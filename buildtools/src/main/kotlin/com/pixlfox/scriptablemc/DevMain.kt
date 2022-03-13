package com.pixlfox.scriptablemc

import com.pixlfox.scriptablemc.tsgenerator.PluginClassLoader
import com.pixlfox.scriptablemc.tsgenerator.TypeScriptDefinitionGenerator
import java.io.File
import java.util.*

class DevMain {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val pluginClassLoader = PluginClassLoader(
                arrayOf(
                    File("/Users/ashton/Downloads/spigot-api-1.18.2-R0.1-20220312.205511-17-shaded.jar")
                )
            )
            val tsdGenerator = TypeScriptDefinitionGenerator(
                rootFolder = File("/Users/ashton/Downloads/dev/"),
                configuration = TypeScriptDefinitionGenerator.Configuration(),
                pluginClassLoader = pluginClassLoader
            )

            tsdGenerator.logging(
                EnumSet.of(
                    TypeScriptDefinitionGenerator.LoggingLevel.DEBUG,
                    TypeScriptDefinitionGenerator.LoggingLevel.INFO,
                    TypeScriptDefinitionGenerator.LoggingLevel.WARNING,
                    TypeScriptDefinitionGenerator.LoggingLevel.ERROR,
                    TypeScriptDefinitionGenerator.LoggingLevel.FATAL,
                )
            )

            tsdGenerator.buildClassList()
            tsdGenerator.exportClassList()
            tsdGenerator.exportTypeScriptDefinitions()
            tsdGenerator.exportJavaScript()
        }
    }
}