import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.io.File
import java.net.URI

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    id("org.jetbrains.intellij.platform") // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/version_catalogs.html
dependencies {
    testImplementation(libs.junitJupiter)
    testImplementation(libs.opentest4j)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // The platform's JUnit5 test framework still resolves a few JUnit4 classes at runtime;
    // see https://plugins.jetbrains.com/docs/intellij/testing-faq.html#junit5-test-framework-refers-to-junit4
    testRuntimeOnly(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        // Module Dependencies. Uses `platformBundledModules` property from the gradle.properties file for bundled IntelliJ Platform modules.
        bundledModules(providers.gradleProperty("platformBundledModules").map { it.split(',') })

        testFramework(TestFrameworkType.JUnit5)
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html#specifying-a-release-channel
        channels = providers.gradleProperty("pluginVersion").map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
    versionPrefix = ""
}

// Configure Gradle Kover Plugin - read more: https://kotlin.github.io/kotlinx-kover/gradle-plugin/#configuration-details
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

// Bundle the kensa-development agent skill files into the plugin jar so the
// "Install Kensa Agent Skills" action can write them into a user's project.
// The skill ref defaults to the Kensa version pinned in ./version.txt; override
// with -PkensaSkillsRef=<tag> or -PkensaSkillsLocalPath=<dir> for development.
val kensaSkillsRef = providers.gradleProperty("kensaSkillsRef")
    .orElse(providers.fileContents(layout.projectDirectory.file("version.txt")).asText.map { it.trim() })
val kensaSkillsLocalPath = providers.gradleProperty("kensaSkillsLocalPath").orNull
val kensaSkillsOutputDir = layout.buildDirectory.dir("generated-resources/agent-skills/skills/kensa-development")
val kensaSkillsRelativePaths = listOf(
    "SKILL.md",
    "references/captured-outputs.md",
    "references/fixtures.md",
    "references/interactions.md",
    "references/rendered-value.md",
    "references/setup-steps.md",
)

val fetchKensaSkills by tasks.registering {
    inputs.property("ref", kensaSkillsRef)
    inputs.property("localPath", kensaSkillsLocalPath ?: "")
    outputs.dir(kensaSkillsOutputDir)
    val refValue = kensaSkillsRef
    val localPath = kensaSkillsLocalPath
    val outDirProvider = kensaSkillsOutputDir
    val relativePaths = kensaSkillsRelativePaths
    doLast {
        val outDir = outDirProvider.get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()
        File(outDir, "references").mkdirs()
        if (localPath != null) {
            val sourceRoot = File(localPath, "plugins/kensa/skills/kensa-development")
            require(sourceRoot.isDirectory) { "kensaSkillsLocalPath does not contain plugins/kensa/skills/kensa-development: $localPath" }
            relativePaths.forEach { relative ->
                val source = File(sourceRoot, relative)
                require(source.isFile) { "Skill file missing: ${source.absolutePath}" }
                source.copyTo(File(outDir, relative), overwrite = true)
            }
        } else {
            val ref = refValue.get()
            val baseUrl = "https://raw.githubusercontent.com/kensa-dev/agent-skills/$ref/plugins/kensa/skills/kensa-development"
            relativePaths.forEach { relative ->
                val text = URI("$baseUrl/$relative").toURL().readText()
                File(outDir, relative).writeText(text)
            }
        }
    }
}

sourceSets["main"].resources.srcDir(layout.buildDirectory.dir("generated-resources/agent-skills"))

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    test {
        useJUnitPlatform()
    }

    processResources {
        dependsOn(fetchKensaSkills)
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }
}

intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:6666",
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
                        "-Didea.debugger.dispatch.port=6666",
                    )
                }
            }

            plugins {
                robotServerPlugin()
            }
        }
    }
}