package com.pixlfox.scriptablemc.tsgenerator

import com.beust.klaxon.*
import org.springframework.core.KotlinReflectionParameterNameDiscoverer
import java.io.File
import java.io.InputStream
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter
import java.util.*

@Suppress("unused", "MemberVisibilityCanBePrivate")
class TypeScriptDefinitionGenerator(
    private val rootFolder: File = File("./"),
    private val configuration: Configuration = Configuration(),
    private val exportFolder: File = File(rootFolder, configuration.exportFolder),
    private val pluginClassLoader: PluginClassLoader = PluginClassLoader.fromPluginsFolder(
        File(rootFolder, configuration.pluginsFolder)
    )
) {

    private val classList = mutableListOf<Class<*>>()
    var loggingLevel: EnumSet<LoggingLevel> = EnumSet.of(
//        LoggingLevel.DEBUG,
        LoggingLevel.INFO,
        LoggingLevel.WARNING,
        LoggingLevel.ERROR,
        LoggingLevel.FATAL,
    )

    private val sortedClassList: Array<Class<*>>
        get() = classList.filter { isClassAllowed(it) }.distinct().sortedBy { it.name }.toTypedArray()

    fun logging(level: EnumSet<LoggingLevel>): TypeScriptDefinitionGenerator {
        loggingLevel = level
        return this
    }

    fun mkdirs(): TypeScriptDefinitionGenerator {
        if(!rootFolder.exists()) {
            rootFolder.mkdirs()
        }

        if(!exportFolder.exists()) {
            exportFolder.mkdirs()
        }

        return this
    }

    fun clean(): TypeScriptDefinitionGenerator {

        deleteDirectory(exportFolder, true)

        return this
    }

    fun buildClassList(): TypeScriptDefinitionGenerator {
        val classList = mutableListOf<Class<*>>()

        for (classDescription in pluginClassLoader.buildClassDescriptionList { isClassAllowed(it) }) {
            val loadedClass = loadClass(classDescription)

            if(loadedClass != null) {
                classList.add(loadedClass)
            }
        }

        addAllClasses(classList)

        return this
    }

    fun debugClassList(): TypeScriptDefinitionGenerator {
        for (baseClass in sortedClassList) {
            println("- ${baseClass.name}")
        }

        println("ClassList Size: ${sortedClassList.size}")

        return this
    }

    fun exportAll(): TypeScriptDefinitionGenerator {
        exportClassList()
        exportTypeScriptDefinitions()
        exportJavaScript()

        println(LoggingLevel.INFO, "Successfully generated ${sortedClassList.size} classes.")

        return this
    }

    fun exportClassList(): TypeScriptDefinitionGenerator {
        val file = File(rootFolder, "class-list.json")
        if(file.exists()) file.delete()
        file.parentFile.mkdirs()
        file.createNewFile()

        val classDescriptions = sortedClassList.map { PluginClassLoader.ClassDescription(getPackageName(it), stripPackageName(it)) }.toTypedArray()
        file.writeText(classDescriptions.jsonToString(true))

        return this
    }

    fun exportTypeScriptDefinitions(): TypeScriptDefinitionGenerator {
        generateTypeScriptDefinitions(sortedClassList)
        return this
    }

    fun exportJavaScript(): TypeScriptDefinitionGenerator {
        generateJavaScript(sortedClassList)
        return this
    }

    private fun generateTypeScriptDefinitions(classes: Array<Class<*>>) {
        for (baseClass in classes) {
            val file = File(exportFolder, "${getPackageName(baseClass).replace(".", "/")}/${stripPackageName(baseClass)}.d.ts")
            println(LoggingLevel.INFO, "${baseClass.name} -> ${file.path}")

            if(file.exists()) file.delete()
            file.parentFile.mkdirs()
            file.createNewFile()

            file.writeText(generateTypeScriptDefinitionSource(baseClass))
        }
    }

    private fun generateJavaScript(classes: Array<Class<*>>) {
        for (baseClass in classes) {
            val file = File(
                exportFolder, "${getPackageName(baseClass).replace(".", "/")}/" +
                        "${stripPackageName(baseClass)}.js"
            )
            if (file.exists()) file.delete()
            file.parentFile.mkdirs()
            file.createNewFile()

            file.writeText(generateJavaScriptSource(baseClass))

            println(LoggingLevel.INFO, "${baseClass.name} -> ${file.path}")
        }
    }

    val includeTypesRegex: Regex
        get() = Regex(
            "(${configuration.includeTypes.joinToString("|") {
                it.replace(".", "\\.")
                    .replace("$", "\\\$")
                    .replace("*", "(.*)?")
            }})"
        )

    val excludeTypesRegex: Regex
        get() = Regex(
            "(${configuration.excludeTypes.joinToString("|") {
                it.replace(".", "\\.")
                    .replace("$", "\\\$")
                    .replace("*", "(.*)?")
            }})"
        )

    private val functionBlacklistRegex: Regex
        get() = Regex("(${configuration.functionBlacklist.joinToString("|")})")

    private val systemClassLoader: ClassLoader
        get() = javaClass.classLoader

    private fun loadClass(classDescription: PluginClassLoader.ClassDescription): Class<*>? {
        val className = classDescription.toString()
        if(!isClassAllowed(classDescription)) return null
        val cachedClass = classList.firstOrNull { it.name == className }
        if(cachedClass != null) return cachedClass

        try {
            return pluginClassLoader.loadClass(className)
        }
        catch (ex: ClassNotFoundException) {
            println(LoggingLevel.WARNING, ex.toString())
        }
        catch (ex: NoClassDefFoundError) {
            println(LoggingLevel.WARNING, ex.toString())
        }
        catch(ex: IncompatibleClassChangeError) {
            println(LoggingLevel.WARNING, ex.toString())
        }
        catch (ex: Exception) {
            println(LoggingLevel.ERROR, ex.toString())
        }

        return null
    }



    private fun safeName(name: String): String = when {
        configuration.safeNames.containsKey(name) -> configuration.safeNames[name]!!
        else -> name
    }

    private fun safeClassName(name: String): String = when {
        configuration.safeClassNames.containsKey(name) -> configuration.safeClassNames[name]!!
        else -> name
    }

    private fun safePackageName(name: String): String = name

    private fun toTypeScriptType(baseClass: Class<*>): String {
        val className = stripPackageName(baseClass)

        return when {
            baseClass.name == "java.lang.Object" -> "any"
            baseClass.name == "void" -> "void"
            baseClass.name == "boolean" -> "boolean"
            baseClass.name == "java.lang.String" -> "string"
            baseClass.name == "byte" -> if(configuration.commentTypes) "number/*(Byte)*/" else "number"
            baseClass.name == "short" -> if(configuration.commentTypes) "number/*(Short)*/" else "number"
            baseClass.name == "int" -> if(configuration.commentTypes) "number/*(Int)*/" else "number"
            baseClass.name == "long" -> if(configuration.commentTypes) "number/*(Long)*/" else "number"
            baseClass.name == "float" -> if(configuration.commentTypes) "number/*(Float)*/" else "number"
            baseClass.name == "double" -> if(configuration.commentTypes) "number/*(Double)*/" else "number"
            baseClass.name == "kotlin.IntArray" -> if(configuration.commentTypes) "Array<number/*(Int)*/>" else "Array<number>"
            baseClass.name == "kotlin.LongArray" -> if(configuration.commentTypes) "Array<number/*(Long)*/>" else "Array<number>"
            baseClass.name == "kotlin.Char" -> if(configuration.commentTypes) "string/*(Char)*/" else "string"
            isClassAllowed(baseClass) -> safeClassName(className)
            else -> if(configuration.commentTypes)  "any/*(${baseClass.name})*/" else "any"
        }
    }

    private fun isClassExcluded(baseClass: Class<*>): Boolean =
        isClassExcluded("${getPackageName(baseClass)}.${stripPackageName(baseClass)}")

    private fun isClassExcluded(classDef: PluginClassLoader.ClassDescription): Boolean = isClassExcluded(classDef.toString())

    private fun isClassExcluded(qualifiedName: String?): Boolean =
        if(qualifiedName.isNullOrEmpty()) true else qualifiedName.matches(excludeTypesRegex)

    private fun isClassIncluded(baseClass: Class<*>): Boolean =
        isClassIncluded("${getPackageName(baseClass)}.${stripPackageName(baseClass)}")

    private fun isClassIncluded(classDef: PluginClassLoader.ClassDescription): Boolean = isClassIncluded(classDef.toString())

    private fun isClassIncluded(qualifiedName: String?): Boolean =
        if(qualifiedName.isNullOrEmpty()) true else qualifiedName.matches(includeTypesRegex)

    private fun isClassAllowed(classDef: PluginClassLoader.ClassDescription): Boolean =
        isClassIncluded(classDef) && !isClassExcluded(classDef)

    private fun isClassAllowed(qualifiedName: String?): Boolean =
        isClassIncluded(qualifiedName) && !isClassExcluded(qualifiedName)

    private fun isClassAllowed(baseClass: Class<*>): Boolean {
        try {
            if (!isClassIncluded(baseClass) || isClassExcluded(baseClass)) return false
            return true
        }
        catch (ex: IncompatibleClassChangeError) {
            return false
        }
        catch (ex: UnsupportedOperationException) {
            return false
        }
    }

    private fun isInnerClass(baseClass: Class<*>): Boolean = baseClass.isMemberClass

    private fun stripPackageName(baseClass: Class<*>): String {
        val qualifiedName = baseClass.name ?: return ""
        return safeClassName(qualifiedName.split('.').last())
    }

    private fun getPackageName(baseClass: Class<*>): String {
        val qualifiedName = baseClass.name ?: return ""
        return safePackageName(qualifiedName.split('.').dropLast(1).joinToString("."))
    }

    private fun addAllClasses(baseClasses: List<Class<*>>) = addAllClasses(baseClasses.toTypedArray())

    private fun addAllClasses(baseClasses: Array<Class<*>>) {
        for (baseClass in baseClasses)  addAllClasses(baseClass)
    }

    private fun addAllClasses(baseClass: Class<*>) {
        if(classList.contains(baseClass) || !isClassAllowed(baseClass)) return

        addClasses(baseClass)
        addAllClasses(buildClassList(baseClass))
    }

    private fun addClasses(baseClasses: List<Class<*>>) = addClasses(baseClasses.toTypedArray())

    private fun addClasses(baseClasses: Array<Class<*>>) {
        for (baseClass in baseClasses)  addClasses(baseClass)
    }

    private fun addClasses(baseClass: Class<*>) {
        if(classList.contains(baseClass) || !isClassAllowed(baseClass)) return

        println(LoggingLevel.DEBUG,"Adding ${baseClass.name} to the class list.")
        classList.add(baseClass)
    }

    private fun buildClassList(baseClasses: Array<Class<*>>, ignoreWhitelist: Boolean = false): Array<Class<*>> {
        val classList = mutableListOf<Class<*>>()

        for (baseClass in baseClasses) {

            if(baseClass.superclass != null) {
                classList.add(baseClass.superclass)
            }

            for(constructor in baseClass.constructors) {
                classList.addAll(constructor.parameterTypes)
            }

            for(method in baseClass.methods) {
                classList.add(method.returnType)
                classList.addAll(method.parameterTypes)
            }

            for(field in baseClass.fields) {
                classList.add(field.type)
            }
        }

        return if(ignoreWhitelist) {
            classList.distinct().toTypedArray()
        } else {
            classList.distinct().filter { isClassAllowed(it) }.toTypedArray()
        }
    }

    private fun buildClassList(baseClass: Class<*>, ignoreWhitelist: Boolean = false): Array<Class<*>> {
        val classList = mutableListOf<Class<*>>()

        if(!isClassAllowed(baseClass)) return arrayOf()

        classList.add(baseClass)

        if(baseClass.superclass != null) {
            classList.add(baseClass.superclass)
        }

        for(baseInterface in baseClass.interfaces) {
            classList.add(baseInterface)
        }

        for(constructor in baseClass.constructors) {
            classList.addAll(constructor.parameterTypes)
        }

        for(method in baseClass.methods) {
            classList.add(method.returnType)
            classList.addAll(method.parameterTypes)
        }

        for(field in baseClass.fields) {
            classList.add(field.type)
        }

        return if(ignoreWhitelist) {
            classList.distinct().toTypedArray()
        } else {
            classList.distinct().filter { isClassAllowed(it) }.toTypedArray()
        }
    }

//    private fun buildClassList(baseClass: KClass<*>, ignoreWhitelist: Boolean = false): Array<KClass<*>> {
//        val classList = mutableListOf<KClass<*>>()
//
//        if(!isClassAllowed(baseClass)) return arrayOf()
//
//        try {
//            for (_superclass in baseClass.superclasses) {
//                if (!classList.contains(_superclass)) {
//                    classList.add(_superclass)
//                }
//            }
//        }
//        catch (ex: IncompatibleClassChangeError) {
//            println(LoggingLevel.WARNING, ex.toString())
//        }
//        catch (ex: NoClassDefFoundError) {
//            println(
//                LoggingLevel.WARNING, "NoClassDefFoundError was thrown for a superclass on " +
//                        "${baseClass.qualifiedName} while building it's class list")
//        }
//        catch (ex: ClassNotFoundException) {
//            println(
//                LoggingLevel.WARNING, "ClassNotFoundException was thrown for a superclass on " +
//                        "${baseClass.qualifiedName} while building it's class list")
//        }
//
//        try {
//            val companionObject = baseClass.companionObject
//            if (companionObject != null) {
//                classList.addAll(
//                    buildClassList(
//                        functions = companionObject.memberFunctions
//                    )
//                )
//            }
//        }
//        catch (ex: IncompatibleClassChangeError) {
//            println(LoggingLevel.WARNING, ex.toString())
//        }
//        catch (ex: NoClassDefFoundError) {
//            println(LoggingLevel.WARNING, ex.toString())
//        }
//        catch (ex: ClassNotFoundException) {
//            println(LoggingLevel.WARNING, ex.toString())
//        }
//
//        try {
//            classList.addAll(
//                buildClassList(
//                    functions = baseClass.constructors + baseClass.staticFunctions + baseClass.memberFunctions
//                )
//            )
//        }
//        catch (ex: IncompatibleClassChangeError) {
//            println(LoggingLevel.WARNING, ex.toString())
//        }
//        catch (ex: NoClassDefFoundError) {
//            println(LoggingLevel.WARNING, ex.toString())
//        }
//        catch (ex: ClassNotFoundException) {
//            println(LoggingLevel.WARNING, ex.toString())
//        }
//
//        return if(ignoreWhitelist) {
//            classList.distinct().toTypedArray()
//        } else {
//            classList.distinct().filter { isClassAllowed(it) }.toTypedArray()
//        }
//    }

    private fun generateTypeScriptDefinitionSource(baseClass: Class<*>): String =
        "${generateTypeScriptImports(baseClass)}\n\n" +
                generateTypeScriptClassDeclaration(baseClass)

    private fun generateJavaScriptSource(baseClass: Class<*>): String =
        "export default ${stripPackageName(baseClass)} = Java.type('${baseClass.name}');"

    private fun generateTypeScriptImports(baseClass: Class<*>): String =
        generateTypeScriptImports(baseClass, buildClassList(baseClass))

    private fun generateTypeScriptImports(baseClass: Class<*>?, importClasses: Array<Class<*>>, keyword: String = "import"): String {
        if(baseClass == null && importClasses.isEmpty()) return ""
        val basePackageName = getPackageName(baseClass ?: importClasses.first())
        val baseClassFolderUri = File(exportFolder, basePackageName.replace('.', '/')).absoluteFile.toURI()
        var tsSource = ""

        for (requiredClass in importClasses.filter { it != baseClass }.sortedBy { getPackageName(it) }) {
            val requiredPackageName = getPackageName(requiredClass)
            val requiredClassName = stripPackageName(requiredClass)
            val importClassFolderUri = File(exportFolder, requiredPackageName.replace('.','/')).absoluteFile.toURI()
            val relativeImportClassFolderUri = baseClassFolderUri.relativize(importClassFolderUri)

            tsSource += "$keyword { $requiredClassName } from '"
            if(!relativeImportClassFolderUri.isAbsolute) {
                tsSource += if (
                    relativeImportClassFolderUri.path != "" &&
                    !relativeImportClassFolderUri.path.startsWith("/")
                ) "./" else "."

                tsSource += relativeImportClassFolderUri.path
                tsSource += if (!relativeImportClassFolderUri.path.endsWith("/")) "/" else ""
                tsSource += requiredClassName
            }
            else {
                tsSource += "../".repeat(basePackageName.split('.').count())
                tsSource += "${requiredPackageName.replace('.', '/')}/"
                tsSource += requiredClassName
            }
            tsSource += "';\n"
        }

        return tsSource
    }

    private fun generateTypeScriptParameters(parameters: Array<Parameter>, constructor: Boolean = false): String {
        if(parameters.isEmpty()) return ""

        val parameterList = mutableListOf<String>()
        var parameterNames: Array<String>? = null

        for((index, parameter) in parameters.filter { it.name != null }.withIndex()) {
            var parameterName = if(parameterNames != null) parameterNames.getOrNull(index) else parameter.name
            if(parameterName == null) parameterName = parameter.name

            parameterList.add(
                safeName(parameterName.orEmpty()) + ": " + toTypeScriptType(parameter.type)
            )
        }

        return parameterList.joinToString(", ")
    }

    private fun generateTypeScriptClassExtends(baseClass: Class<*>): String {
        if(baseClass.superclass == null || !isClassAllowed(baseClass.superclass)) return ""
        return " implements ${toTypeScriptType(baseClass.superclass) }"
    }

    private fun generateTypeScriptFunctionDeclaration(method: Method, modifiers: String = ""): String {
        return  "${modifiers}${safeName(method.name)}" +
                "(${generateTypeScriptParameters(method.parameters)})" +
                ": ${toTypeScriptType(method.returnType)};"
    }

    private fun generateTypeScriptFunctionDeclarations(baseClass: Class<*>, linePrefix: String = ""): String {
        var source = ""


        for (method in baseClass.methods.sortedWith(compareBy({it.name}, {it.parameters.size}))) {
            var modifiers = ""

            if(Modifier.isPublic(method.modifiers)) {
                modifiers += "public "
            }
            if(Modifier.isStatic(method.modifiers)) {
                modifiers += "static "
            }

            source += "${linePrefix}\t${modifiers}${generateTypeScriptFunctionDeclaration(method)}\n"
        }

        return source
    }

    private fun generateTypeScriptClassDeclaration(baseClass: Class<*>, linePrefix: String = ""): String {
        var tsSource =
            "${linePrefix}export declare class ${stripPackageName(baseClass)}${generateTypeScriptClassExtends(baseClass)} {\n"

        try {
            if (baseClass.isEnum) {
                tsSource = "/* Enum */\n$tsSource"

                val enumConstants = baseClass.enumConstants

                for (enumConstant in enumConstants) {
                    try {
                        tsSource += "${linePrefix}\tpublic static get ${(enumConstant as Enum<*>).name}(): ${
                            stripPackageName(
                                baseClass
                            )
                        }\n"
                    }
                    catch (ex: java.lang.Exception) {
                        println(LoggingLevel.WARNING, "generateTypeScriptClassDeclaration_Enum(${baseClass.name}): $ex")
                    }
                }

            }
            else if(baseClass.isInterface) {
                tsSource = "/* Interface */\n$tsSource"
            }
        }
        catch (ex: NullPointerException) {
            println(LoggingLevel.WARNING, "generateTypeScriptClassDeclaration(${baseClass.name}): $ex")
        }
        catch (ex: NoSuchMethodError) {
            println(LoggingLevel.WARNING, "generateTypeScriptClassDeclaration(${baseClass.name}): $ex")
        }
        catch (ex: NoClassDefFoundError) {
            println(LoggingLevel.WARNING, "generateTypeScriptClassDeclaration(${baseClass.name}): $ex")
        }
        catch (ex: Exception) {
            println(LoggingLevel.WARNING, "generateTypeScriptClassDeclaration(${baseClass.name}): $ex")
        }


        try {
            for (constructor in baseClass.constructors.sortedWith(compareBy({it.name}, {it.parameters.size}))) {
                if (!constructor.name.matches(functionBlacklistRegex)) {
                    tsSource += "${linePrefix}\tconstructor(${generateTypeScriptParameters(constructor.parameters, true)});\n"
                }
            }
        }
        catch (ex: NoClassDefFoundError) {
            println(LoggingLevel.WARNING, "generateTypeScriptClassDeclaration(${baseClass.name}): $ex")
        }

//        val companionObject = baseClass.companionObject
//        if(companionObject != null) {
//            tsSource += generateTypeScriptFunctionDeclarations(companionObject.memberFunctions, linePrefix, "public static ")
//        }

        tsSource += generateTypeScriptFunctionDeclarations(baseClass, linePrefix)


        tsSource += "${linePrefix}}\n\n"
        return tsSource
    }

    private fun println(logLevel: LoggingLevel, message: String) {
        if(loggingLevel.contains(logLevel)) {
            println("$logLevel: $message")
        }
    }

    companion object {
        fun fromConfigFile(configFilePath: String): TypeScriptDefinitionGenerator =
            fromConfigFile(File(configFilePath))

        fun fromConfigFile(configFile: File): TypeScriptDefinitionGenerator =
            fromConfigStream(configFile.parent, configFile.inputStream())

        fun fromConfigStream(rootFolderPath: String, inputStream: InputStream): TypeScriptDefinitionGenerator {
            val rootFolder = File(rootFolderPath)
            val configuration = Klaxon().parse<Configuration>(inputStream)!!
            return TypeScriptDefinitionGenerator(rootFolder, configuration)
        }

        fun Any.jsonToString(prettyPrint: Boolean = true): String {
            val thisJsonString = Klaxon().toJsonString(this)
            var result = thisJsonString
            if(prettyPrint) {
                result = if(thisJsonString.startsWith("[")){
                    Klaxon().parseJsonArray(thisJsonString.reader()).toJsonString(true)
                } else {
                    Klaxon().parseJsonObject(thisJsonString.reader()).toJsonString(true)
                }
            }
            return result
        }

        private fun deleteDirectory(directoryToBeDeleted: File, skipThisDir: Boolean = false): Boolean {
            val allContents = directoryToBeDeleted.listFiles()
            if (allContents != null) {
                for (file in allContents) {
                    deleteDirectory(file)
                }
            }

            return if(skipThisDir) {
                true
            }
            else {
                directoryToBeDeleted.delete()
            }
        }
    }

    enum class LoggingLevel {
        DEBUG,
        INFO,
        WARNING,
        ERROR,
        FATAL
    }

    class Configuration (
        val exportFolder: String = "dist",
        val pluginsFolder: String = "plugins",
        val commentTypes: Boolean = true,
        val includeTypes: Array<String> = arrayOf(
            "org.bukkit.*",
            "net.md_5.bungee.api.*",
            "com.destroystokyo.paper.*",
            "io.papermc.paper.*",
            "com.mojang.*",
            "net.kyori.adventure.*",
            "com.pixlfox.scriptablemc.*",
            "com.smc.*",
            "fr.minuskube.inv.*",
            "com.google.common.io.*",
            "java.sql.*",
            "java.io.*",
            "java.util.logging.*",
            "khttp.*",
            "org.apache.commons.io.*",
            "org.graalvm.*"
        ),
        val excludeTypes: Array<String> = arrayOf(
            "org.bukkit.plugin.java.LibraryLoader",
            "net.md_5.bungee.api.ChatColor",
            "com.smc.nbtapi.utils.nmsmappings.*",
            "*.package-info",
        ),
        val functionBlacklist: Array<String> = arrayOf(
            "wait",
            "equals",
            "toString",
            "hashCode",
            "getClass",
            "notify",
            "notifyAll",
            "(.*?)\\\$(.*?)",
        ),
        val safeNames: Map<String, String> = mapOf(
            "function" to "_function",
            "yield" to "_yield",
            "arguments" to "_arguments",
            "name" to "'name'",
            "<set-?>" to "value",
            "in" to "_in",
            "with" to "_with",
        ),
        val safeClassNames: Map<String, String> = mapOf(
            "Array" to "_Array",
        ),
    ) {
        companion object {
            fun fromJsonObject(configJson: String): Configuration? {
                return Klaxon().parse<Configuration>(configJson)
            }
        }
    }
}
