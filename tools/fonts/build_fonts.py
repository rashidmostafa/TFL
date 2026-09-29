#!/usr/bin/env python3
"""Build TFL's bundled font resources.

Dev-time tool (Python 3 + fontTools). Its outputs are committed, so building the app downloads
no fonts and the app never fetches fonts at runtime.

    python3 tools/fonts/build_fonts.py

Every input is pinned to an exact upstream commit and checked against a SHA-256 before use:
  * Inter, Plus Jakarta Sans, JetBrains Mono -> google/fonts (SIL Open Font License 1.1)
  * Material Symbols Outlined                -> google/material-design-icons (Apache License 2.0)

Outputs:
  core/designsystem/src/main/res/font/*.ttf
  core/designsystem/src/main/kotlin/app/tfl/core/designsystem/icon/MaterialSymbols.kt
  core/designsystem/src/main/assets/licenses/*.txt

To add an icon, append its Material Symbols name to core/designsystem/material-symbols.txt and rerun.
"""

import hashlib
import io
import sys
import urllib.request
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / ".cache"
DESIGNSYSTEM = ROOT / "core" / "designsystem"
FONT_RES = DESIGNSYSTEM / "src" / "main" / "res" / "font"
LICENSES = DESIGNSYSTEM / "src" / "main" / "assets" / "licenses"
ICON_LIST = DESIGNSYSTEM / "material-symbols.txt"
ICON_KOTLIN = (
    DESIGNSYSTEM / "src" / "main" / "kotlin" / "app" / "tfl" / "core" / "designsystem" / "icon" / "MaterialSymbols.kt"
)

_FONTS = "https://raw.githubusercontent.com/google/fonts"
_INTER = f"{_FONTS}/e1d6480102fed30739fead0faee463101f892c8f/ofl/inter"
_JAKARTA = f"{_FONTS}/8cd7d0de182c88592d6852c245fe48f66eef55ee/ofl/plusjakartasans"
_MONO = f"{_FONTS}/2e05c1cf00a6e4f40a4b931600a90881c26e15cd/ofl/jetbrainsmono"
_SYMBOLS = "https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef"

# name -> (url, sha256)
SOURCES = {
    "Inter.ttf": (
        f"{_INTER}/Inter%5Bopsz,wght%5D.ttf",
        "29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031",
    ),
    "Inter-OFL.txt": (
        f"{_INTER}/OFL.txt",
        "5b9321a4298cfeb6b34354164a1c3afc3db114569984c502b9b35d988fd58c57",
    ),
    "PlusJakartaSans.ttf": (
        f"{_JAKARTA}/PlusJakartaSans%5Bwght%5D.ttf",
        "89b3fb38aa0d275d7a731d0d817a4f1622b316b4d7fbdedcf02ee9099ff68bc8",
    ),
    "PlusJakartaSans-OFL.txt": (
        f"{_JAKARTA}/OFL.txt",
        "995c7199cab65954f545996326755daee7b63cc6b42b06c13da1f9502ab08a99",
    ),
    "JetBrainsMono.ttf": (
        f"{_MONO}/JetBrainsMono%5Bwght%5D.ttf",
        "48715a42ec242c21e9f02692891e147d022299a52e48d5e413e1a942193ffeda",
    ),
    "JetBrainsMono-OFL.txt": (
        f"{_MONO}/OFL.txt",
        "b2fe5e8987594e9ffd1d2ca52a2f5d73eb8335243893c5d6254b5ad69269591d",
    ),
    "MaterialSymbolsOutlined.ttf": (
        f"{_SYMBOLS}/variablefont/MaterialSymbolsOutlined%5BFILL,GRAD,opsz,wght%5D.ttf",
        "0128da5981791d2fe09918c337ea6657b0ef2e4e5f8f4af3e1dc5f31889b2311",
    ),
    "MaterialSymbolsOutlined.codepoints": (
        f"{_SYMBOLS}/variablefont/MaterialSymbolsOutlined%5BFILL,GRAD,opsz,wght%5D.codepoints",
        "225bd09137103cb7746bc93dc08d08764c9f0c3bd04f4b958d4a3c3c19432dd6",
    ),
    "MaterialSymbols-LICENSE.txt": (
        f"{_SYMBOLS}/LICENSE",
        "58d1e17ffe5109a7ae296caafcadfdbe6a7d176f0bc4ab01e12a689b0499d8bd",
    ),
}

# (source, output resource name, axis limits). Ranges keep only the weights TFL uses.
TEXT_FONTS = [
    ("Inter.ttf", "inter.ttf", {"opsz": 14, "wght": (400, 700)}),
    ("PlusJakartaSans.ttf", "plus_jakarta_sans.ttf", {"wght": (400, 800)}),
    ("JetBrainsMono.ttf", "jetbrains_mono.ttf", {"wght": (400, 700)}),
]

# Static Material Symbols instances: FILL 0 (outlined) and FILL 1 (filled), all at weight 400.
SYMBOL_FONTS = [
    (0, "material_symbols_outlined.ttf"),
    (1, "material_symbols_outlined_filled.ttf"),
]

LICENSE_FILES = [
    ("Inter-OFL.txt", "inter-OFL.txt"),
    ("PlusJakartaSans-OFL.txt", "plus_jakarta_sans-OFL.txt"),
    ("JetBrainsMono-OFL.txt", "jetbrains_mono-OFL.txt"),
    ("MaterialSymbols-LICENSE.txt", "material_symbols-LICENSE.txt"),
]


def fetch(name: str) -> Path:
    """Returns the cached source file, downloading it first if needed. Exits on a hash mismatch."""
    url, expected = SOURCES[name]
    path = CACHE / name
    if not path.exists():
        CACHE.mkdir(parents=True, exist_ok=True)
        print(f"  downloading {name}")
        with urllib.request.urlopen(url, timeout=300) as response:
            path.write_bytes(response.read())
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual != expected:
        path.unlink()
        sys.exit(f"SHA-256 mismatch for {name}: expected {expected}, got {actual}")
    return path


def unhinted_options() -> subset.Options:
    options = subset.Options()
    options.hinting = False
    options.notdef_outline = True
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.drop_tables += ["DSIG"]
    return options


def reloaded(font: TTFont) -> TTFont:
    """Round-trips a font through bytes so every table is fully compiled before further processing."""
    buffer = io.BytesIO()
    font.save(buffer)
    buffer.seek(0)
    return TTFont(buffer)


def build_text_font(source: str, output: str, limits: dict) -> None:
    font = reloaded(instancer.instantiateVariableFont(TTFont(fetch(source)), limits))
    # Keep every glyph and OpenType feature; only drop TrueType hinting, which phones don't need.
    options = unhinted_options()
    options.layout_features = ["*"]
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=font.getBestCmap().keys())
    subsetter.subset(font)
    font.save(FONT_RES / output)


def read_icon_names() -> list[str]:
    names = []
    for line in ICON_LIST.read_text().splitlines():
        name = line.split("#", 1)[0].strip()
        if name:
            names.append(name)
    return sorted(set(names))


def kotlin_name(icon: str) -> str:
    name = "".join(part[:1].upper() + part[1:] for part in icon.split("_"))
    return f"Ic{name}" if name[:1].isdigit() else name


def kotlin_literal(codepoint: int) -> str:
    if codepoint <= 0xFFFF:
        return f"\\u{codepoint:04X}"
    offset = codepoint - 0x10000
    return f"\\u{0xD800 + (offset >> 10):04X}\\u{0xDC00 + (offset & 0x3FF):04X}"


def build_symbols() -> int:
    codepoints = {}
    for line in fetch("MaterialSymbolsOutlined.codepoints").read_text().splitlines():
        if line.strip():
            name, hex_value = line.split()
            codepoints[name] = int(hex_value, 16)

    names = read_icon_names()
    missing = [name for name in names if name not in codepoints]
    if missing:
        sys.exit(f"Not in Material Symbols Outlined: {', '.join(missing)}")

    constants = {}
    for name in names:
        constant = kotlin_name(name)
        if constant in constants and constants[constant] != name:
            sys.exit(f"Both {constants[constant]} and {name} map to Kotlin name {constant}")
        constants[constant] = name

    variable = TTFont(fetch("MaterialSymbolsOutlined.ttf"))
    options = unhinted_options()
    options.layout_features = []  # glyphs are addressed by codepoint, so the ligature table can go
    options.drop_tables += ["GSUB"]
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes={codepoints[name] for name in names})
    subsetter.subset(variable)

    for fill, output in SYMBOL_FONTS:
        static = instancer.instantiateVariableFont(variable, {"FILL": fill, "GRAD": 0, "opsz": 24, "wght": 400})
        static.save(FONT_RES / output)

    lines = [
        "// GENERATED by tools/fonts/build_fonts.py from core/designsystem/material-symbols.txt. Do not edit.",
        "package app.tfl.core.designsystem.icon",
        "",
        "/**",
        " * Codepoints of the Material Symbols Outlined glyphs bundled with TFL. Render them with [TflIcon].",
        " *",
        " * To add an icon, list its name in `core/designsystem/material-symbols.txt` and rerun",
        " * `python3 tools/fonts/build_fonts.py`.",
        " */",
        "object MaterialSymbols {",
    ]
    for constant in sorted(constants):
        lines.append(f'    const val {constant}: String = "{kotlin_literal(codepoints[constants[constant]])}"')
    lines.append("}")
    ICON_KOTLIN.parent.mkdir(parents=True, exist_ok=True)
    ICON_KOTLIN.write_text("\n".join(lines) + "\n")
    return len(names)


def main() -> None:
    FONT_RES.mkdir(parents=True, exist_ok=True)
    LICENSES.mkdir(parents=True, exist_ok=True)

    print("Text fonts")
    for source, output, limits in TEXT_FONTS:
        build_text_font(source, output, limits)

    print("Material Symbols")
    icon_count = build_symbols()

    for source, output in LICENSE_FILES:
        (LICENSES / output).write_bytes(fetch(source).read_bytes())

    print(f"\n{icon_count} icons -> {ICON_KOTLIN.relative_to(ROOT)}")
    for font in sorted(FONT_RES.glob("*.ttf")):
        print(f"{font.stat().st_size / 1024:8.1f} KB  {font.relative_to(ROOT)}")


if __name__ == "__main__":
    if sys.version_info < (3, 10):
        sys.exit("Python 3.10+ is required")
    main()
