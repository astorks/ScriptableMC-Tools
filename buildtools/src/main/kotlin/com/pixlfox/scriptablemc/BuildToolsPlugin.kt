@file:Suppress("unused")

package com.pixlfox.scriptablemc

import com.pixlfox.scriptablemc.tsgenerator.*
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import java.io.File
import java.util.*


fun Project.smc(configure: Action<BuildToolsConfigExtension>): Unit =
    (this as ExtensionAware).extensions.configure("smc_buildtools_config", configure)

class BuildToolsConfigExtension {
    var typescript: TypeScriptGeneratorConfig = TypeScriptGeneratorConfig()
    fun typescript(action: Action<in TypeScriptGeneratorConfig>) {
        action.execute(typescript)
    }
}

class TypeScriptGeneratorConfig {
    var debugCopyArtifacts: Boolean = false
    var debug: Boolean = false
    var commentTypes: Boolean = true

    var includeTypes: MutableList<String> = mutableListOf("*")

    var excludeTypes: MutableList<String> = mutableListOf("*.package-info")

    var functionBlacklist: MutableList<String> = mutableListOf(
        "wait",
        "equals",
        "toString",
        "hashCode",
        "getClass",
        "notify",
        "notifyAll",
        "(.*?)\\\$(.*?)",
    )

    val safeNames: MutableMap<String, String> = mutableMapOf(
        "function" to "_function",
        "yield" to "_yield",
        "arguments" to "_arguments",
        "name" to "_name",
        "<set-?>" to "value",
        "in" to "_in",
        "with" to "_with",
    )

    val safeClassNames: MutableMap<String, String> = mutableMapOf(
        "Array" to "_Array",
    )
}

class BuildToolsPlugin: Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.add("smc_buildtools_config", BuildToolsConfigExtension())
        val extension = project.extensions.getByType(BuildToolsConfigExtension::class.java)
        val typescript = extension.typescript

        extension.typescript {
            it.debug = true
        }


        project.task("generateTypeScriptDefinitions") {
            it.group = "smc buildtools"
            it.dependsOn("shadowJar")

            it.doLast {
                val defaultConfig = project.configurations.getByName("default")
                val compileConfig = project.configurations.getByName("compileClasspath")
                val artifactFileList = mutableListOf<File>()
                val artifactBlacklist = Regex("spigot-api-1\\.8\\.8(.*)\\.jar")

                for (artifact in defaultConfig.artifacts) {
                    if(!artifactBlacklist.matches(artifact.file.name) && !artifactFileList.contains(artifact.file)) {
                        artifactFileList.add(artifact.file)
                    }
                }

                for (artifact in defaultConfig.resolvedConfiguration.resolvedArtifacts) {
                    if(!artifactBlacklist.matches(artifact.file.name) && !artifactFileList.contains(artifact.file)) {
                        artifactFileList.add(artifact.file)
                    }
                }

                for (artifact in compileConfig.resolvedConfiguration.resolvedArtifacts) {
                    if(!artifactBlacklist.matches(artifact.file.name) && !artifactFileList.contains(artifact.file)) {
                        artifactFileList.add(artifact.file)
                    }
                }

                if(typescript.debugCopyArtifacts) {
                    val pluginsDirectory = File(project.buildDir, "plugin-artifacts")

                    if(pluginsDirectory.exists()) {
                        pluginsDirectory.deleteRecursively()
                    }

                    pluginsDirectory.mkdir()

                    println("Artifact List:")
                    for (artifactFile in artifactFileList) {
                        println("-- ${artifactFile.path}")
                        artifactFile.copyTo(File(pluginsDirectory, artifactFile.name))
                    }
                }

                val exportDirectory = File(project.buildDir, "ts-lib")

                if(exportDirectory.exists()) {
                    exportDirectory.deleteRecursively()
                }

                exportDirectory.mkdir()

                val tsGenerator = TypeScriptDefinitionGenerator(
                    exportDirectory.absoluteFile,
                    TypeScriptDefinitionGenerator.Configuration(
                        commentTypes = typescript.commentTypes,
                        includeTypes = typescript.includeTypes.toTypedArray(),
                        excludeTypes = typescript.excludeTypes.toTypedArray(),
                        functionBlacklist = typescript.functionBlacklist.toTypedArray(),
                        safeNames = typescript.safeNames,
                        safeClassNames = typescript.safeClassNames
                    ),
                    exportDirectory.absoluteFile,
                    PluginClassLoader(artifactFileList.toTypedArray())
                )

                if(typescript.debug) {
                    tsGenerator.logging(
                        EnumSet.of(
                            TypeScriptDefinitionGenerator.LoggingLevel.DEBUG,
                            TypeScriptDefinitionGenerator.LoggingLevel.INFO,
                            TypeScriptDefinitionGenerator.LoggingLevel.WARNING,
                            TypeScriptDefinitionGenerator.LoggingLevel.ERROR,
                            TypeScriptDefinitionGenerator.LoggingLevel.FATAL,
                        )
                    )
                }

                tsGenerator.buildClassList()
                tsGenerator.exportClassList()
                tsGenerator.exportTypeScriptDefinitions()
                tsGenerator.exportJavaScript()
            }
        }
    }
}