package com.algorithmlx.ecr.api.config

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.*
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object ConfigManager {
    val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "

        ignoreUnknownKeys = true
        encodeDefaults = false

        allowComments = true
        allowTrailingComma = true
    }

    private val fullJson = Json(json) {
        encodeDefaults = true
    }

    inline fun <reified T : Any> saveOrLoad(file: File, defaults: T): T = saveOrLoad(file, defaults, serializer())

    inline fun <reified T : Any> saveOrLoad(cache: MutableMap<String, Any>, file: File, defaults: T): T =
        saveOrLoad(cache, file, defaults, serializer())

    fun <T : Any> saveOrLoad(file: File, defaults: T, serializer: KSerializer<T>): T {
        if (!file.exists()) {
            createConfig(file, defaults, defaults, serializer)
            return defaults
        }

        return try {
            json.decodeFromString(serializer, file.readText())
        } catch (e: SerializationException) {
            recoverBrokenConfig(file, defaults, serializer, e)
        } catch (e: IOException) {
            recoverBrokenConfig(file, defaults, serializer, e)
        }
    }

    fun <T : Any> saveOrLoad(cache: MutableMap<String, Any>, file: File, defaults: T, serializer: KSerializer<T>): T {
        val value = saveOrLoad(file, defaults, serializer)

        replaceCache(cache, value, serializer)

        return value
    }

    inline fun <reified T : Any> regenerate(file: File, config: T, defaults: T) {
        regenerate(file, config, defaults, serializer())
    }

    inline fun <reified T : Any> regenerate(cache: MutableMap<String, Any>, file: File, config: T, defaults: T) {
        regenerate(cache, file, config, defaults, serializer())
    }

    fun <T : Any> regenerate(file: File, config: T, defaults: T, serializer: KSerializer<T>) {
        createConfig(file, config, defaults, serializer)
    }

    fun <T : Any> regenerate(cache: MutableMap<String, Any>, file: File, config: T, defaults: T, serializer: KSerializer<T>) {
        createConfig(file, config, defaults, serializer)

        replaceCache(cache, config, serializer)
    }

    private fun <T : Any> createConfig(file: File, config: T, defaults: T, serializer: KSerializer<T>) {
        val currentElement = fullJson.encodeToJsonElement(serializer, config)
        val defaultsElement = fullJson.encodeToJsonElement(serializer, defaults)

        val originalJson = fullJson.encodeToString(JsonElement.serializer(), currentElement)

        val commentedJson = injectComments(originalJson, serializer.descriptor, currentElement, defaultsElement)
        writeAtomically(file, commentedJson)
    }

    private fun <T : Any> recoverBrokenConfig(file: File, defaults: T, serializer: KSerializer<T>, cause: Exception): T {
        cause.printStackTrace()

        val source = file.toPath().toAbsolutePath()
        val backup = source.resolveSibling("${file.name}.broken-${System.currentTimeMillis()}")

        Files.copy(source, backup)

        createConfig(file, defaults, defaults, serializer)

        return defaults
    }

    private fun writeAtomically(file: File, content: String) {
        val target = file.toPath().toAbsolutePath()
        val parent = target.parent

        Files.createDirectories(parent)

        val temporary = Files.createTempFile(parent, ".${file.name}.", ".tmp")

        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8)

            try {
                Files.move(
                    temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun <T> replaceCache(cache: MutableMap<String, Any>, config: T, serializer: KSerializer<T>) {
        val jsonObject = fullJson.encodeToJsonElement(serializer, config)

        cache.clear()
        cache.putAll(flatten(jsonObject))
    }

    @JvmStatic
    fun isEnabled(cache: Map<String, Any>, key: String): Boolean = cache[key] as? Boolean ?: true

    @JvmStatic
    fun getInt(cache: Map<String, Any>, key: String): Int = (cache[key] as? Number)?.toInt() ?: 0

    fun flatten(element: JsonElement, prefix: String = ""): Map<String, Any> {
        val result = mutableMapOf<String, Any>()

        fun visit(current: JsonElement, path: String) {
            when (current) {
                JsonNull -> Unit

                is JsonObject -> current.forEach { (key, value) ->
                    val childPath = if (path.isEmpty()) key else "$path.$key"
                    visit(value, childPath)
                }

                is JsonArray -> current.forEachIndexed { index, value ->
                    val childPath = if (path.isEmpty()) "[$index]" else "$path[$index]"
                    visit(value, childPath)
                }

                is JsonPrimitive -> result[path] = when {
                    current.isString -> current.content
                    current.booleanOrNull != null -> current.boolean
                    current.intOrNull != null -> current.int
                    current.longOrNull != null -> current.long
                    current.doubleOrNull != null -> current.double
                    else -> current.content
                }
            }
        }

        visit(element, prefix)

        return result
    }

    fun injectComments(jsonString: String, rootDescriptor: SerialDescriptor, currentRoot: JsonElement, defaultsRoot: JsonElement): String {
        val commentMap = buildCommentMap(rootDescriptor)

        val defaultMap = buildDefaultMap(rootDescriptor, currentRoot, defaultsRoot)

        val lines = jsonString.lines()
        val pathStack = arrayListOf<String>()

        val keyRegex = Regex("""^(\s*)"((?:\\.|[^"\\])*)"\s*:""")

        val indentUnitLength = json.configuration.prettyPrintIndent.length.coerceAtLeast(1)

        return buildString {
            commentMap[""]?.takeIf(String::isNotBlank)?.let {
                append(it)
                append('\n')
            }

            lines.forEach { line ->
                val match = keyRegex.find(line)

                var currentPath: String? = null
                var openedObjectKey: String? = null

                if (match != null) {
                    val indent = match.groupValues[1]
                    val encodedKey = match.groupValues[2]

                    val key = Json.decodeFromString<String>("\"$encodedKey\"")

                    val indentLevel = indent.length / indentUnitLength

                    while (pathStack.size >= indentLevel) {
                        pathStack.removeAt(pathStack.lastIndex)
                    }

                    currentPath = if (pathStack.isEmpty()) key else "${pathStack.joinToString(".")}.$key"

                    commentMap[currentPath]?.takeIf(String::isNotBlank)?.lineSequence()?.forEach { commentLine ->
                        append(indent)
                        append(commentLine)
                        append('\n')
                    }

                    val valuePart = line.substring(match.range.last + 1).trim().removeSuffix(",").trim()

                    if (valuePart == "{") openedObjectKey = key
                }

                append(line)

                currentPath?.let(defaultMap::get)?.let { defaultValue ->
                    append(" // default: ")
                    append(formatDefaultValue(defaultValue))
                }

                append('\n')

                if (openedObjectKey != null) pathStack += openedObjectKey
            }
        }.trimEnd()
    }

    private fun buildCommentMap(rootDescriptor: SerialDescriptor): Map<String, String> {
        val comments = linkedMapOf<String, MutableList<JsonComment>>()

        fun addAnnotations(path: String, annotations: List<Annotation>) {
            annotations.filterIsInstance<JsonComment>().forEach { annotation ->
                comments.getOrPut(path, ::mutableListOf).add(annotation)
            }
        }

        fun visit(descriptor: SerialDescriptor, prefix: String, recursionStack: MutableSet<String>) {
            addAnnotations(prefix, descriptor.annotations)

            if (!recursionStack.add(descriptor.serialName)) return

            for (index in 0 until descriptor.elementsCount) {
                val elementName = descriptor.getElementName(index)

                val path = if (prefix.isEmpty()) elementName else "$prefix.$elementName"

                addAnnotations(path, descriptor.getElementAnnotations(index))

                val childDescriptor = descriptor.getElementDescriptor(index)

                when (childDescriptor.kind) {
                    StructureKind.CLASS,
                    StructureKind.OBJECT -> visit(childDescriptor, path, recursionStack)
                    else -> Unit
                }
            }

            recursionStack.remove(descriptor.serialName)
        }

        visit(rootDescriptor, "", mutableSetOf())

        return comments.mapValues { (_, annotations) ->
            annotations.distinct().joinToString("\n", transform = ::formatComment)
        }
    }

    private fun buildDefaultMap(
        rootDescriptor: SerialDescriptor, currentRoot: JsonElement, defaultsRoot: JsonElement
    ): Map<String, JsonElement> {
        val result = linkedMapOf<String, JsonElement>()
        val recursionStack = mutableSetOf<String>()

        fun visit(
            descriptor: SerialDescriptor, currentElement: JsonElement, defaultElement: JsonElement,
            prefix: String, inheritedPolicy: JsonDefaults?
        ) {
            val currentObject = currentElement as? JsonObject ?: return
            val defaultObject = defaultElement as? JsonObject ?: return

            val ownPolicy = descriptor.annotations.filterIsInstance<JsonDefaults>().firstOrNull()

            val policy = ownPolicy ?: inheritedPolicy

            if (!recursionStack.add(descriptor.serialName)) return

            try {
                for (index in 0 until descriptor.elementsCount) {
                    val key = descriptor.getElementName(index)

                    val path = if (prefix.isEmpty()) key
                    else "$prefix.$key"

                    val currentValue = currentObject[key]
                    val defaultValue = defaultObject[key]

                    if (policy != null && descriptor.isElementOptional(index) && defaultObject.containsKey(key)) {
                        val shouldAdd = !policy.onlyIfChanged || currentValue != defaultValue

                        if (shouldAdd && defaultValue is JsonPrimitive) result[path] = defaultValue
                    }

                    if (currentValue != null && defaultValue != null) {
                        val childDescriptor = descriptor.getElementDescriptor(index)

                        val inheritedChildPolicy = policy?.takeIf { it.recursive }

                        visit(
                            childDescriptor, currentValue,
                            defaultValue, path, inheritedChildPolicy
                        )
                    }
                }
            } finally {
                recursionStack.remove(descriptor.serialName)
            }
        }

        visit(rootDescriptor, currentRoot, defaultsRoot, "", null)

        return result
    }

    private fun formatDefaultValue(value: JsonElement): String = value.toString()

    private fun formatComment(annotation: JsonComment): String {
        val lines = annotation.comments.flatMap(String::lines)

        return if (annotation.multiline)
            buildString {
                append("/*").append('\n')

                lines.forEach { append(" * ").append(it).append('\n') }

                append(" */")
            }
        else
            lines.joinToString("\n") {
                "// $it"
            }
    }
}
