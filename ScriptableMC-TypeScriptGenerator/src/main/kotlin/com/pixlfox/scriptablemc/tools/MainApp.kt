package com.pixlfox.scriptablemc.tools

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import java.util.*

class MainApp {
    class Args(parser: ArgParser) {
        val debug by parser.flagging(
            "-d", "--debug",
            help = "Additional debug output.")

        val skipClassListBuild by parser.flagging(
            "-b", "--skip-build-class-list",
            help = "Skip building class list.")

        val skipClassListExport by parser.flagging(
            "-c", "--skip-export-class-list",
            help = "Skip exporting the built class list.")

        val skipTypeScriptExport by parser.flagging(
            "-t", "--skip-export-type-script",
            help = "Skip exporting the TypeScript definitions.")

        val skipJavaScriptExport by parser.flagging(
            "-j", "--skip-export-java-script",
            help = "Skip exporting the JavaScript sources.")

        val configFilePath by parser.positional(
            "CFG_FILE",
            help = "Configuration file path")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>): Unit = mainBody {
            ArgParser(args).parseInto(MainApp::Args).run {

                val tsGenerator = TypeScriptDefinitionGenerator.fromConfigFile(configFilePath)

                if(debug) {
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

                if (!skipClassListBuild) {
                    tsGenerator.buildClassList()
                }

                if(debug) {
                    tsGenerator.debugClassList()
                }

                if(!skipClassListExport) {
                    tsGenerator.exportClassList()
                }

                if(!skipTypeScriptExport) {
                    tsGenerator.exportTypeScriptDefinitions()
                }

                if(!skipJavaScriptExport) {
                    tsGenerator.exportJavaScript()
                }
            }
        }
    }
}