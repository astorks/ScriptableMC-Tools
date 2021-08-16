@file:Suppress("unused")

package com.pixlfox.gradle

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.util.*

fun Project.typeScriptDefinition(configure: Action<TypeScriptDefinitionGeneratorExtension>): Unit =
    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("tsgenerator_config", configure)

class TypeScriptDefinitionGeneratorExtension {
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

class TypeScriptDefinitionGeneratorPlugin: Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.add("tsgenerator_config", TypeScriptDefinitionGeneratorExtension())
        val extension = project.extensions.getByType(TypeScriptDefinitionGeneratorExtension::class.java)


        project.task("generateTypeScriptDefinitions") {
            it.group = "build"
            it.dependsOn("shadowJar")

            it.doLast {
                val defaultConfig = project.configurations.getByName("default")
                val compileOnlyConfig = project.configurations.getByName("compileOnly")
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

                for (artifact in compileOnlyConfig.resolvedConfiguration.resolvedArtifacts) {
                    if(!artifactBlacklist.matches(artifact.file.name) && !artifactFileList.contains(artifact.file)) {
                        artifactFileList.add(artifact.file)
                    }
                }

                if(extension.debugCopyArtifacts) {
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
                        commentTypes = extension.commentTypes,
                        includeTypes = extension.includeTypes.toTypedArray(),
                        excludeTypes = extension.excludeTypes.toTypedArray(),
                        functionBlacklist = extension.functionBlacklist.toTypedArray(),
                        safeNames = extension.safeNames,
                        safeClassNames = extension.safeClassNames
                    ),
                    exportDirectory.absoluteFile,
                    PluginClassLoader(artifactFileList.toTypedArray())
                )

                if(extension.debug) {
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