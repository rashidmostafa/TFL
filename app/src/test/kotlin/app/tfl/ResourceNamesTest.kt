package app.tfl

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Android merges every module's resources into one namespace, so when two modules define the same
 * name, one silently replaces the other in the app. (Onboarding once showed Settings' wording this
 * way; each module's own screenshot tests couldn't see it.)
 */
class ResourceNamesTest {

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun noTwoModulesDefineTheSameResource() {
        val definedBy = mutableMapOf<String, MutableSet<String>>()
        mainResourceDirectories().forEach { (module, res) ->
            resourceNames(res).forEach { name -> definedBy.getOrPut(name) { sortedSetOf() } += module }
        }
        val collisions = definedBy.filterValues { it.size > 1 }
        assertTrue("Checked no resources: is the project root right? ($root)", definedBy.isNotEmpty())
        assertTrue(
            "Resource names defined in more than one module (prefix them with the module's name):\n" +
                collisions.entries.joinToString("\n") { (name, modules) -> "  $name: $modules" },
            collisions.isEmpty(),
        )
    }

    /** `src/main/res` of every module, keyed by the module's path. */
    private fun mainResourceDirectories(): List<Pair<String, File>> =
        root.walkTopDown()
            .onEnter { it.name != "build" && it.name != "build-logic" && !it.name.startsWith(".") }
            .filter { it.isDirectory && it.name == "res" && it.parentFile?.name == "main" && it.parentFile?.parentFile?.name == "src" }
            .map { res -> checkNotNull(res.parentFile?.parentFile?.parentFile).relativeTo(root).path to res }
            .toList()

    /** "string/app_name", "drawable/ic_launcher_foreground", … for one resource directory. */
    private fun resourceNames(res: File): Set<String> = buildSet {
        res.listFiles().orEmpty().filter { it.isDirectory }.forEach { directory ->
            val type = directory.name.substringBefore('-')
            directory.listFiles().orEmpty().forEach { file ->
                if (type == "values") addAll(valueNames(file)) else add("$type/${file.nameWithoutExtension}")
            }
        }
    }

    private fun valueNames(file: File): List<String> {
        val resources = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val children = resources.childNodes
        return (0 until children.length).mapNotNull { index ->
            (children.item(index) as? Element)?.takeIf { it.hasAttribute("name") }?.let { element ->
                val type = if (element.tagName == "item") element.getAttribute("type") else element.tagName
                "$type/${element.getAttribute("name")}"
            }
        }
    }
}
