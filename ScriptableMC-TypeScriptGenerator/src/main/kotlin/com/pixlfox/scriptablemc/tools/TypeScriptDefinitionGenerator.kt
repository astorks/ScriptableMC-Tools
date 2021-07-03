package com.pixlfox.scriptablemc.tools

import com.beust.klaxon.*
import org.springframework.core.KotlinReflectionParameterNameDiscoverer
import java.io.File
import java.io.InputStream
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod

@Suppress("unused", "MemberVisibilityCanBePrivate")
class TypeScriptDefinitionGenerator(private val rootFolder: File = File("./"), private val configuration: Configuration = Configuration()) {

    val pluginClassLoader = PluginClassLoader(pluginsFolder)
    private val classList = mutableListOf<KClass<*>>()
    private var loggingLevel: EnumSet<LoggingLevel> = EnumSet.of(
//        LoggingLevel.DEBUG,
        LoggingLevel.INFO,
        LoggingLevel.WARNING,
        LoggingLevel.ERROR,
        LoggingLevel.FATAL,
    )

    private val sortedClassList: Array<KClass<*>>
        get() = classList.filter { isClassAllowed(it) }.distinct().sortedBy { it.qualifiedName }.toTypedArray()


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

        if(!pluginsFolder.exists()) {
            pluginsFolder.mkdirs()
        }

        return this
    }

    fun clean(): TypeScriptDefinitionGenerator {

        deleteDirectory(exportFolder, true)

        return this
    }

    fun buildClassList(): TypeScriptDefinitionGenerator {
        val classList = mutableListOf<KClass<*>>()

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
            println("- ${baseClass.qualifiedName}")
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

        val classDescriptions = sortedClassList.map { ClassDescription(getPackageName(it), stripPackageName(it)) }.toTypedArray()
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

    private fun generateTypeScriptDefinitions(classes: Array<KClass<*>>) {
        for (baseClass in classes) {
            val file = File(exportFolder, "${getPackageName(baseClass).replace(".", "/")}/${stripPackageName(baseClass)}.d.ts")
            if(file.exists()) file.delete()
            file.parentFile.mkdirs()
            file.createNewFile()

            file.writeText(generateTypeScriptDefinitionSource(baseClass))

            println(LoggingLevel.INFO, "${baseClass.qualifiedName} -> ${file.path}")
        }
    }

    private fun generateJavaScript(classes: Array<KClass<*>>) {
        for (baseClass in classes) {
            val file = File(
                exportFolder, "${getPackageName(baseClass).replace(".", "/")}/" +
                        "${stripPackageName(baseClass)}.js"
            )
            if (file.exists()) file.delete()
            file.parentFile.mkdirs()
            file.createNewFile()

            file.writeText(generateJavaScriptSource(baseClass))

            println(LoggingLevel.INFO, "${baseClass.qualifiedName} -> ${file.path}")
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

    private val exportFolder: File
        get() = File(rootFolder, configuration.exportFolder)

    private val pluginsFolder: File
        get() = File(rootFolder, configuration.pluginsFolder)

    private val systemClassLoader: ClassLoader
        get() = javaClass.classLoader

    private fun loadClass(classDescription: ClassDescription): KClass<*>? {
        val className = classDescription.toString()
        if(!isClassAllowed(classDescription)) return null
        val cachedClass = classList.firstOrNull { it.qualifiedName == className }
        if(cachedClass != null) return cachedClass

        try {
            return pluginClassLoader.loadClass(className).kotlin
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

    @OptIn(ExperimentalStdlibApi::class)
    private fun toTypeScriptType(type: KType): String {
        val isArray = if(type.classifier is KClass<*>) {
            val typeClass = type.classifier as KClass<*>
            typeClass.qualifiedName == "kotlin.Array" ||
                    typeClass.qualifiedName == "kotlin.collections.List" ||
                    typeClass.qualifiedName == "kotlin.collections.Set"
        } else false

        val isClass = if(type.classifier is KClass<*>) {
            val typeClass = type.classifier as KClass<*>
            typeClass.qualifiedName == "java.lang.Class"
        } else false

        return when {
            type == typeOf<Any>() -> "any"
            type == typeOf<Unit>() -> "void"
            type == typeOf<Boolean>() -> "boolean"
            type == typeOf<String>() -> "string"
            type == typeOf<Byte>() -> if(configuration.commentTypes) "number/*(Byte)*/" else "number"
            type == typeOf<Short>() -> if(configuration.commentTypes) "number/*(Short)*/" else "number"
            type == typeOf<Int>() -> if(configuration.commentTypes) "number/*(Int)*/" else "number"
            type == typeOf<Long>() -> if(configuration.commentTypes) "number/*(Long)*/" else "number"
            type == typeOf<Float>() -> if(configuration.commentTypes) "number/*(Float)*/" else "number"
            type == typeOf<Double>() -> if(configuration.commentTypes) "number/*(Double)*/" else "number"
            type == typeOf<IntArray>() -> if(configuration.commentTypes) "Array<number/*(Int)*/>" else "Array<number>"
            type == typeOf<Char>() -> if(configuration.commentTypes) "string/*(Char)*/" else "string"
            isClass -> "{ new (...args: any[]): ${toTypeScriptType(type.arguments.first().type?.classifier)}; }"
            isArray -> "Array<${toTypeScriptType(type.arguments.first().type?.classifier)}>"
            else -> toTypeScriptType(type.classifier)
        }
    }

    private fun toTypeScriptType(classifier: KClassifier?): String {
        return when {
            (classifier is KClass<*>) -> toTypeScriptType(classifier)
//            configuration.commentTypes -> "any/*???: ${classifier?.javaClass?.kotlin?.qualifiedName}*/"
            else -> "any"
        }
    }

    private fun toTypeScriptType(baseClass: KClass<*>): String {
        val className = stripPackageName(baseClass)

        return when {
            baseClass.qualifiedName == "kotlin.Any" -> "any"
            baseClass.qualifiedName == "kotlin.Unit" -> "void"
            baseClass.qualifiedName == "kotlin.Boolean" -> "boolean"
            baseClass.qualifiedName == "kotlin.String" -> "string"
            baseClass.qualifiedName == "kotlin.Byte" -> if(configuration.commentTypes) "number/*(Byte)*/" else "number"
            baseClass.qualifiedName == "kotlin.Short" -> if(configuration.commentTypes) "number/*(Short)*/" else "number"
            baseClass.qualifiedName == "kotlin.Int" -> if(configuration.commentTypes) "number/*(Int)*/" else "number"
            baseClass.qualifiedName == "kotlin.Long" -> if(configuration.commentTypes) "number/*(Long)*/" else "number"
            baseClass.qualifiedName == "kotlin.Float" -> if(configuration.commentTypes) "number/*(Float)*/" else "number"
            baseClass.qualifiedName == "kotlin.Double" -> if(configuration.commentTypes) "number/*(Double)*/" else "number"
            baseClass.qualifiedName == "kotlin.IntArray" -> if(configuration.commentTypes) "Array<number/*(Int)*/>" else "Array<number>"
            baseClass.qualifiedName == "kotlin.LongArray" -> if(configuration.commentTypes) "Array<number/*(Long)*/>" else "Array<number>"
            baseClass.qualifiedName == "kotlin.Char" -> if(configuration.commentTypes) "string/*(Char)*/" else "string"
            isClassAllowed(baseClass) -> safeClassName(className)
            else -> if(configuration.commentTypes)  "any/*(${baseClass.qualifiedName})*/" else "any"
        }
    }

    private fun isClassExcluded(baseClass: KClass<*>): Boolean =
        isClassExcluded("${getPackageName(baseClass)}.${stripPackageName(baseClass)}")

    private fun isClassExcluded(classDef: ClassDescription): Boolean = isClassExcluded(classDef.toString())

    private fun isClassExcluded(qualifiedName: String?): Boolean =
        if(qualifiedName.isNullOrEmpty()) true else qualifiedName.matches(excludeTypesRegex)

    private fun isClassIncluded(baseClass: KClass<*>): Boolean =
        isClassIncluded("${getPackageName(baseClass)}.${stripPackageName(baseClass)}")

    private fun isClassIncluded(classDef: ClassDescription): Boolean = isClassIncluded(classDef.toString())

    private fun isClassIncluded(qualifiedName: String?): Boolean =
        if(qualifiedName.isNullOrEmpty()) true else qualifiedName.matches(includeTypesRegex)

    private fun isClassAllowed(classDef: ClassDescription): Boolean =
        isClassIncluded(classDef) && !isClassExcluded(classDef)

    private fun isClassAllowed(qualifiedName: String?): Boolean =
        isClassIncluded(qualifiedName) && !isClassExcluded(qualifiedName)

    private fun isClassAllowed(baseClass: KClass<*>): Boolean {
        try {
            if (baseClass.isCompanion) return false
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

    private fun isInnerClass(baseClass: KClass<*>): Boolean = baseClass.isInner || baseClass.java.isMemberClass

    private fun stripPackageName(baseClass: KClass<*>): String {
        val qualifiedName = baseClass.java.name ?: return ""
        return safeClassName(qualifiedName.split('.').last())
    }

    private fun getPackageName(baseClass: KClass<*>): String {
        val qualifiedName = baseClass.java.name ?: return ""
        return safePackageName(qualifiedName.split('.').dropLast(1).joinToString("."))
    }

    private fun addAllClasses(baseClasses: List<KClass<*>>) = addAllClasses(baseClasses.toTypedArray())

    private fun addAllClasses(baseClasses: Array<KClass<*>>) {
        for (baseClass in baseClasses)  addAllClasses(baseClass)
    }

    private fun addAllClasses(baseClass: KClass<*>) {
        if(classList.contains(baseClass) || !isClassAllowed(baseClass)) return

        addClasses(baseClass)
        addAllClasses(buildClassList(baseClass))
    }

    private fun addClasses(baseClasses: List<KClass<*>>) = addClasses(baseClasses.toTypedArray())

    private fun addClasses(baseClasses: Array<KClass<*>>) {
        for (baseClass in baseClasses)  addClasses(baseClass)
    }

    private fun addClasses(baseClass: KClass<*>) {
        if(classList.contains(baseClass) || !isClassAllowed(baseClass)) return

        println(LoggingLevel.DEBUG,"Adding ${baseClass.qualifiedName} to the class list.")
        classList.add(baseClass)
    }

    private fun getClassFromType(type: KType?): KClass<*>? {
        if(type == null) return null

        val typeClassifier = type.classifier
        if (typeClassifier is KClass<*>) {
            return typeClassifier
        }

        return null
    }

    private fun getClassesFromType(type: KType?): Array<KClass<*>> {
        val classList = mutableListOf<KClass<*>>()

        if(type != null) {
            for (typeArg in type.arguments) {
                val typeArgClass = getClassFromType(typeArg.type)
                if (typeArgClass != null) classList.add(typeArgClass)
            }

            val mainClass = getClassFromType(type)
            if (mainClass != null) classList.add(mainClass)
        }

        return classList.toTypedArray()
    }

    private fun buildClassList(functions: Collection<KFunction<*>> = listOf(), properties: Collection<KProperty1<*, *>> = listOf(), ignoreWhitelist: Boolean = false): Array<KClass<*>> {
        val classList = mutableListOf<KClass<*>>()

        try {
            for(function in functions) {
                if(function.visibility == KVisibility.PUBLIC && !function.name.matches(functionBlacklistRegex)) {
                    classList.addAll(getClassesFromType(function.returnType))

                    for (_parameter in function.parameters) {
                        classList.addAll(getClassesFromType(_parameter.type))
                    }
                }
            }
        }
        catch (ex: NoClassDefFoundError){
            println(LoggingLevel.WARNING, ex.toString())
        }

        try {
            for(property in properties) {
                if(property.visibility == KVisibility.PUBLIC) {
                    classList.addAll(getClassesFromType(property.returnType))
                }
            }
        }
        catch (ex: NoClassDefFoundError){
            println(LoggingLevel.WARNING, ex.toString())
        }

        return if(ignoreWhitelist) {
            classList.distinct().toTypedArray()
        } else {
            classList.distinct().filter { isClassAllowed(it) }.toTypedArray()
        }
    }

    private fun buildClassList(baseClasses: Array<KClass<*>>, ignoreWhitelist: Boolean = false): Array<KClass<*>> {
        val classList = mutableListOf<KClass<*>>()

        for (baseClass in baseClasses) {
            classList.addAll(buildClassList(baseClass, ignoreWhitelist))
        }

        return classList.distinct().toTypedArray()
    }

    private fun buildClassList(baseClass: KClass<*>, ignoreWhitelist: Boolean = false): Array<KClass<*>> {
        val classList = mutableListOf<KClass<*>>()

        if(!isClassAllowed(baseClass)) return arrayOf()

        try {
            for (_superclass in baseClass.superclasses) {
                if (!classList.contains(_superclass)) {
                    classList.add(_superclass)
                }
            }
        }
        catch (ex: IncompatibleClassChangeError) {
            println(LoggingLevel.WARNING, ex.toString())
        }
        catch (ex: NoClassDefFoundError) {
            println(
                LoggingLevel.WARNING, "NoClassDefFoundError was thrown for a superclass on " +
                    "${baseClass.qualifiedName} while building it's class list")
        }

        try {
            val companionObject = baseClass.companionObject
            if (companionObject != null) {
                classList.addAll(
                    buildClassList(
                        functions = companionObject.memberFunctions
                    )
                )
            }
        }
        catch (ex: IncompatibleClassChangeError) {
            println(LoggingLevel.WARNING, ex.toString())
        }
        catch (ex: NoClassDefFoundError) {
            println(LoggingLevel.WARNING, ex.toString())
        }

        try {
            classList.addAll(
                buildClassList(
                    functions = baseClass.constructors + baseClass.staticFunctions + baseClass.memberFunctions
                )
            )
        }
        catch (ex: IncompatibleClassChangeError) {
            println(LoggingLevel.WARNING, ex.toString())
        }
        catch (ex: NoClassDefFoundError) {
            println(LoggingLevel.WARNING, ex.toString())
        }

        return if(ignoreWhitelist) {
            classList.distinct().toTypedArray()
        } else {
            classList.distinct().filter { isClassAllowed(it) }.toTypedArray()
        }
    }

    private fun generateTypeScriptDefinitionSource(baseClass: KClass<*>): String =
        "${generateTypeScriptImports(baseClass)}\n\n" +
                generateTypeScriptClassDeclaration(baseClass)

    private fun generateJavaScriptSource(baseClass: KClass<*>): String =
        "export default ${stripPackageName(baseClass)} = Java.type('${baseClass.qualifiedName}');"

    private fun generateTypeScriptImports(baseClass: KClass<*>): String =
        generateTypeScriptImports(baseClass, buildClassList(baseClass))

    private fun generateTypeScriptImports(baseClass: KClass<*>?, importClasses: Array<KClass<*>>, keyword: String = "import"): String {
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

    private fun generateTypeScriptParameters(function: KFunction<*>, constructor: Boolean = false): String {
        val parameters = function.parameters
        if(parameters.isEmpty()) return ""

        val parameterList = mutableListOf<String>()
        var parameterNames: Array<String>? = null

        if(constructor) {
            try {
                val jConstructor = function.javaConstructor
                if(jConstructor != null) {
                    parameterNames = KotlinReflectionParameterNameDiscoverer().getParameterNames(jConstructor)
                }
            }
            catch (ex: Exception) {
                println(LoggingLevel.ERROR, ex.toString())
            }
        }
        else {
            try {
                val jMethod = function.javaMethod
                if(jMethod != null) {
                    parameterNames = KotlinReflectionParameterNameDiscoverer().getParameterNames(jMethod)
                }
            }
            catch (ex: Exception) {
                println(LoggingLevel.ERROR, ex.toString())
            }
        }

        for((index, parameter) in parameters.filter { it.name != null }.withIndex()) {
            var parameterName = if(parameterNames != null) parameterNames.getOrNull(index) else parameter.name
            if(parameterName == null) parameterName = parameter.name

            parameterList.add(
                safeName(parameterName.orEmpty()) +
                "${if(parameter.isOptional) "?" else ""}: " +
                toTypeScriptType(parameter.type)
            )
        }

        return parameterList.joinToString(", ")
    }

    private fun generateTypeScriptClassExtends(baseClass: KClass<*>): String {
        val superclasses = baseClass.superclasses.filter { isClassAllowed(it) }
        if(superclasses.isEmpty()) return ""
        return " implements ${superclasses.joinToString(", ") { toTypeScriptType(it) }}"
    }

    private fun generateTypeScriptFunctionDeclaration(function: KFunction<*>, modifiers: String = ""): String {
        return  "${modifiers}${safeName(function.name)}" +
                "(${generateTypeScriptParameters(function)})" +
                ": ${toTypeScriptType(function.returnType)};"
    }

    private fun generateTypeScriptFunctionDeclarations(functions: Collection<KFunction<*>>, linePrefix: String = "", modifiers: String = ""): String {
        var source = ""

        try {
            for (function in functions.filter {
                it.visibility == KVisibility.PUBLIC &&
                        !it.name.matches(functionBlacklistRegex)
            }.sortedWith(compareBy({it.name}, {it.parameters.size}))) {
                source += "${linePrefix}\t${generateTypeScriptFunctionDeclaration(function, modifiers)}\n"
            }
        }
        catch (ex: NoClassDefFoundError) {
            println(LoggingLevel.WARNING, ex.toString())
        }

        return source
    }

    private fun generateTypeScriptClassDeclaration(baseClass: KClass<*>, linePrefix: String = ""): String {
        var tsSource =
            "${linePrefix}export declare class ${stripPackageName(baseClass)}${generateTypeScriptClassExtends(baseClass)} {\n"

        try {
            if (baseClass.java.isEnum) {
                tsSource = "/* Enum */\n$tsSource"

                val enumConstants = baseClass.java.enumConstants

                if(enumConstants is Array<*>) {
                    for (enumConstant in enumConstants) {
                        if(enumConstant is Enum<*>) {
                            tsSource += "${linePrefix}\tpublic static get ${enumConstant.name}(): ${
                                stripPackageName(
                                    baseClass
                                )
                            }\n"
                        }
                    }
                }
            }
            else if(baseClass.java.isInterface) {
                tsSource = "/* Interface */\n$tsSource"
            }
        }
        catch (ex: NullPointerException) {
            println(LoggingLevel.WARNING, "generateTypeScriptClassDeclaration(${baseClass.qualifiedName}): $ex")
        }
        catch (ex: NoSuchMethodError) {
            println(LoggingLevel.WARNING, "generateTypeScriptClassDeclaration(${baseClass.qualifiedName}): $ex")
        }
        catch (ex: Exception) {
            println(LoggingLevel.WARNING, "generateTypeScriptClassDeclaration(${baseClass.qualifiedName}): $ex")
        }


        try {
            for (constructor in baseClass.constructors.sortedWith(compareBy({it.name}, {it.parameters.size}))) {
                if (constructor.visibility == KVisibility.PUBLIC && !constructor.name.matches(functionBlacklistRegex)) {
                    tsSource += "${linePrefix}\tconstructor(${generateTypeScriptParameters(constructor, true)});\n"
                }
            }
        }
        catch (ex: NoClassDefFoundError) {
            println(LoggingLevel.WARNING, "generateTypeScriptClassDeclaration(${baseClass.qualifiedName}): $ex")
        }

        val companionObject = baseClass.companionObject
        if(companionObject != null) {
            tsSource += generateTypeScriptFunctionDeclarations(companionObject.memberFunctions, linePrefix, "public static ")
        }

        tsSource += generateTypeScriptFunctionDeclarations(baseClass.staticFunctions, linePrefix, "public static ")
        tsSource += generateTypeScriptFunctionDeclarations(baseClass.memberFunctions, linePrefix, "public ")


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
            "com.pixlfox.scriptablemc.*",
            "com.smc.*",
            "fr.minuskube.inv.*",
            "com.google.common.io.*",
            "java.sql.*",
            "java.io.*",
            "java.util.logging.*",
            "khttp.*",
            "org.apache.commons.io.*",
            "org.graalvm.*",
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
            "name" to "_name",
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

    class ClassDescription (
        @Json(name = "p", index = 0)
        val packageName: String = "",
        @Json(name = "l", index = 1)
        val className: String = "",
    ) {
        override fun toString(): String =
            if(packageName.isNotEmpty() && className.isNotEmpty()) "$packageName.$className" else ""

        companion object {
            fun fromJsonObject(classDescriptionJson: String): ClassDescription? {
                return Klaxon().parse<ClassDescription>(classDescriptionJson)
            }
        }
    }
}
