package com.pixlfox.scriptablemc.tsgenerator

import java.io.File
import java.io.FileInputStream
import java.net.URLClassLoader
import java.security.CodeSource
import java.security.PermissionCollection
import java.security.Permissions
import java.util.zip.ZipInputStream


class PluginClassLoader(private val artifactFiles: Array<File>) :
    URLClassLoader(artifactFiles.map { it.canonicalFile.toURI().toURL() }.toTypedArray()) {

    companion object {
        fun fromPluginsFolder(pluginFolder: File): PluginClassLoader {
            val artifactFiles = pluginFolder.listFiles()?.filter {
                it.extension.equals("jar", true)
            }?.toTypedArray()

            return if(artifactFiles != null) {
                PluginClassLoader(artifactFiles)
            } else {
                PluginClassLoader(arrayOf())
            }
        }
    }

    private val classIgnoreRegex = Regex("(.*?)\\\$[0-9]+(.*?)")

    override fun getPermissions(codesource: CodeSource?): PermissionCollection = Permissions()

    fun buildClassDescriptionList(isClassAllowed: (TypeScriptDefinitionGenerator.ClassDescription) -> Boolean): Array<TypeScriptDefinitionGenerator.ClassDescription> {
        val classList: MutableList<TypeScriptDefinitionGenerator.ClassDescription> = mutableListOf()

        for(artifactFile in artifactFiles) {
            val zipStream = ZipInputStream(FileInputStream(artifactFile.canonicalPath))
            var entry = zipStream.nextEntry

            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".class")) {
                    val rawEntryName = entry.name.replace('/', '.')
                    val rawClassName = rawEntryName.substring(0, rawEntryName.length - ".class".length).split('.')

                    val className = rawClassName.last()
                    val packageName = rawClassName.take(rawClassName.size - 1).joinToString(".")

                    if(!className.matches(classIgnoreRegex)) {
                        classList.add(TypeScriptDefinitionGenerator.ClassDescription(packageName, className))
                    }
                }

                entry = zipStream.nextEntry
            }
        }

        return classList.filter { isClassAllowed(it) }.distinct().toTypedArray()
    }
}