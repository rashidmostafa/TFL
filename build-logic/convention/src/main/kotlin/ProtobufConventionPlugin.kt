import app.tfl.buildlogic.libs
import app.tfl.buildlogic.library
import com.google.protobuf.gradle.ProtobufExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Protocol Buffers for a pure Kotlin module: `.proto` files in `src/main/proto` become lite Java
 * classes plus Kotlin builders (protobuf-kotlin-lite). protoc runs at build time only.
 */
class ProtobufConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.protobuf")

            val protoc = libs.library("protobuf-protoc").get()
            extensions.configure<ProtobufExtension> {
                protoc {
                    artifact = "${protoc.module}:${protoc.versionConstraint.requiredVersion}"
                }
                generateProtoTasks {
                    all().configureEach {
                        builtins {
                            maybeCreate("java").option("lite")
                            maybeCreate("kotlin").option("lite")
                        }
                    }
                }
            }

            dependencies {
                "api"(libs.library("protobuf-kotlin-lite"))
            }
        }
    }
}
