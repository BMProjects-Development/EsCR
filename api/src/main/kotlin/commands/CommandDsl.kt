@file:Suppress("unused")

package com.algorithmlx.ecr.api.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.RedirectModifier
import com.mojang.brigadier.ResultConsumer
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.ParsedCommandNode
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import com.mojang.brigadier.tree.RootCommandNode
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate
import kotlin.reflect.KProperty

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class BrigadierCommandDsl

interface CommandTarget<S> {
    val node: CommandNode<S>
}

class LiteralReference<S> internal constructor(
    override val node: LiteralCommandNode<S>,
    val aliases: Map<String, LiteralCommandNode<S>>,
) : CommandTarget<S> {
    val name: String
        get() = node.name
}

class CommandArgument<S, T> internal constructor(
    val name: String,
    val parser: ArgumentType<*>,
    private val reader: (CommandContext<S>, String) -> T,
) : CommandTarget<S> {
    private var attachedNode: ArgumentCommandNode<S, *>? = null

    override val node: ArgumentCommandNode<S, *>
        get() = checkNotNull(attachedNode) {
            "Argument '$name' has not been attached to the command tree yet"
        }

    fun read(context: CommandContext<S>): T = reader(context, name)

    internal fun attach(node: ArgumentCommandNode<S, *>) {
        check(attachedNode == null) { "Argument '$name' is already attached to a command tree" }
        attachedNode = node
    }
}

operator fun <S, T> CommandContext<S>.get(argument: CommandArgument<S, T>): T = argument.read(this)

inline fun <S, reified T : Any> CommandContext<S>.argument(name: String): T = getArgument(name, T::class.java)

inline fun <S, reified T : Any> CommandContext<S>.argumentOrNull(name: String): T? =
    runCatching { argument<S, T>(name) }.getOrNull()

class CommandArguments<S> internal constructor(private val context: CommandContext<S>) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(name: String): T = context.getArgument(name, Any::class.java) as T

    operator fun <T> get(argument: CommandArgument<S, T>): T = argument.read(context)

    @Suppress("UNCHECKED_CAST")
    fun <T> getOrNull(name: String): T? =
        runCatching { context.getArgument(name, Any::class.java) as T }.getOrNull()

    operator fun <T> getValue(thisRef: Any?, property: KProperty<*>): T = get(property.name)
}

open class CommandCall<S> internal constructor(val context: CommandContext<S>) {
    val source: S
        get() = context.source

    val input: String
        get() = context.input

    val range: StringRange
        get() = context.range

    val nodes: List<ParsedCommandNode<S>>
        get() = context.nodes

    val rootNode: CommandNode<S>
        get() = context.rootNode

    val arguments: CommandArguments<S> = CommandArguments(context)

    val args: CommandArguments<S>
        get() = arguments

    inline fun <reified T : Any> argument(name: String): T = context.getArgument(name, T::class.java)

    inline fun <reified T : Any> argumentOrNull(name: String): T? =
        runCatching { argument<T>(name) }.getOrNull()

    operator fun <T> CommandArgument<S, T>.invoke(): T = read(context)

    operator fun <T> CommandArgument<S, T>.getValue(thisRef: Any?, property: KProperty<*>): T = read(context)
}

class CommandSuggestions<S> internal constructor(
    context: CommandContext<S>,
    val builder: SuggestionsBuilder,
) : CommandCall<S>(context) {
    val remaining: String
        get() = builder.remaining

    val remainingLowerCase: String
        get() = builder.remainingLowerCase

    val start: Int
        get() = builder.start

    fun suggest(value: String, tooltip: com.mojang.brigadier.Message? = null) {
        if (tooltip == null) builder.suggest(value) else builder.suggest(value, tooltip)
    }

    fun suggest(value: Int, tooltip: com.mojang.brigadier.Message? = null) {
        if (tooltip == null) builder.suggest(value) else builder.suggest(value, tooltip)
    }

    fun suggestAll(values: Iterable<String>) {
        values.forEach(::suggest)
    }

    fun suggestAllInts(values: Iterable<Int>) {
        values.forEach(::suggest)
    }

    fun suggestMatching(values: Iterable<String>, ignoreCase: Boolean = true) {
        val prefix = if (ignoreCase) remainingLowerCase else remaining
        values.asSequence()
            .filter { value ->
                val candidate = if (ignoreCase) value.lowercase(Locale.ROOT) else value
                candidate.startsWith(prefix)
            }
            .forEach(::suggest)
    }

    fun atOffset(offset: Int, block: CommandSuggestions<S>.() -> Unit) {
        val offsetBuilder = builder.createOffset(offset)
        CommandSuggestions(context, offsetBuilder).block()
        builder.add(offsetBuilder)
    }

    operator fun String.unaryPlus() {
        suggest(this)
    }

    fun build(): Suggestions = builder.build()

    fun buildFuture(): CompletableFuture<Suggestions> = builder.buildFuture()
}

data class CommandResult<S>(
    val context: CommandContext<S>,
    val success: Boolean,
    val result: Int,
) {
    val source: S
        get() = context.source
}

@BrigadierCommandDsl
abstract class CommandContainer<S> internal constructor() {
    protected abstract fun attach(node: CommandNode<S>): CommandNode<S>

    fun then(node: CommandNode<S>): CommandNode<S> = attach(node)

    fun then(builder: ArgumentBuilder<S, *>): CommandNode<S> = attach(builder.build())

    fun literal(
        name: String,
        vararg aliases: String,
        block: LiteralCommandScope<S>.() -> Unit = {},
    ): LiteralReference<S> = literal(name, aliases.asIterable(), block)

    fun literal(
        name: String,
        aliases: Iterable<String>,
        block: LiteralCommandScope<S>.() -> Unit = {},
    ): LiteralReference<S> {
        require(name.isNotBlank()) { "A command literal cannot be blank" }

        val nativeBuilder = LiteralArgumentBuilder.literal<S>(name)
        val scope = LiteralCommandScope(nativeBuilder)
        scope.block()

        val attached = attach(nativeBuilder.build())
        require(attached is LiteralCommandNode<S>) {
            "A non-literal node named '$name' already exists at this level"
        }

        val aliasNames = (aliases + scope.declaredAliases)
            .onEach { require(it.isNotBlank()) { "A command alias cannot be blank" } }
            .filterNot { it == name }
            .distinct()

        val aliasNodes = LinkedHashMap<String, LiteralCommandNode<S>>(aliasNames.size)
        aliasNames.forEach { alias ->
            val aliasBuilder = LiteralArgumentBuilder.literal<S>(alias)
                .requires(attached.requirement)
            attached.command?.let(aliasBuilder::executes)
            val aliasNode = aliasBuilder.redirect(attached).build()
            val attachedAlias = attach(aliasNode)
            require(attachedAlias is LiteralCommandNode<S>) {
                "A non-literal node named '$alias' already exists at this level"
            }
            aliasNodes[alias] = attachedAlias
        }

        return LiteralReference(attached, aliasNodes)
    }

    operator fun String.invoke(block: LiteralCommandScope<S>.() -> Unit): LiteralReference<S> =
        literal(this, block = block)

    fun <T> argument(
        name: String,
        type: ArgumentType<T>,
        block: ArgumentCommandScope<S, T>.(CommandArgument<S, T>) -> Unit = {},
    ): CommandArgument<S, T> = argument(name, type, ::readParsedArgument, block)

    fun <Parsed, T> argument(
        name: String,
        type: ArgumentType<Parsed>,
        read: (CommandContext<S>, String) -> T,
        block: ArgumentCommandScope<S, Parsed>.(CommandArgument<S, T>) -> Unit,
    ): CommandArgument<S, T> {
        require(name.isNotBlank()) { "An argument name cannot be blank" }

        val reference = CommandArgument(name, type, read)
        val nativeBuilder = RequiredArgumentBuilder.argument<S, Parsed>(name, type)
        ArgumentCommandScope(nativeBuilder).block(reference)

        val attached = attach(nativeBuilder.build())
        require(attached is ArgumentCommandNode<S, *>) {
            "A literal node named '$name' already exists at this level"
        }
        reference.attach(attached)
        return reference
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> readParsedArgument(context: CommandContext<S>, name: String): T =
        context.getArgument(name, Any::class.java) as T
}

@BrigadierCommandDsl
abstract class CommandNodeScope<S, B : ArgumentBuilder<S, B>> internal constructor(
    val brigadier: B,
) : CommandContainer<S>() {
    override fun attach(node: CommandNode<S>): CommandNode<S> {
        brigadier.then(node)
        return brigadier.arguments.first { it.name == node.name }
    }

    fun requires(requirement: Predicate<S>) {
        brigadier.requires(requirement)
    }

    fun requires(requirement: S.() -> Boolean) {
        brigadier.requires { source -> source.requirement() }
    }

    fun executes(command: CommandCall<S>.() -> Int) {
        brigadier.executes { context -> CommandCall(context).command() }
    }

    fun executesRaw(command: Command<S>) {
        brigadier.executes(command)
    }

    fun runs(result: Int = Command.SINGLE_SUCCESS, command: CommandCall<S>.() -> Unit) {
        brigadier.executes { context ->
            CommandCall(context).command()
            result
        }
    }

    fun redirect(target: CommandNode<S>) {
        brigadier.redirect(target)
    }

    fun redirect(target: CommandTarget<S>) {
        redirect(target.node)
    }

    fun redirect(target: CommandNode<S>, modifier: CommandCall<S>.() -> S) {
        brigadier.redirect(target) { context -> CommandCall(context).modifier() }
    }

    fun redirect(target: CommandTarget<S>, modifier: CommandCall<S>.() -> S) {
        redirect(target.node, modifier)
    }

    fun fork(target: CommandNode<S>, modifier: CommandCall<S>.() -> Collection<S>) {
        brigadier.fork(target) { context -> CommandCall(context).modifier() }
    }

    fun fork(target: CommandTarget<S>, modifier: CommandCall<S>.() -> Collection<S>) {
        fork(target.node, modifier)
    }

    fun forward(
        target: CommandNode<S>,
        forks: Boolean,
        modifier: (CommandCall<S>.() -> Collection<S>)? = null,
    ) {
        val nativeModifier = modifier?.let { operation ->
            RedirectModifier<S> { context -> CommandCall(context).operation() }
        }
        brigadier.forward(target, nativeModifier, forks)
    }

    fun forward(
        target: CommandTarget<S>,
        forks: Boolean,
        modifier: (CommandCall<S>.() -> Collection<S>)? = null,
    ) {
        forward(target.node, forks, modifier)
    }

    fun brigadier(block: B.() -> Unit) {
        brigadier.block()
    }
}

@BrigadierCommandDsl
class LiteralCommandScope<S> internal constructor(
    brigadier: LiteralArgumentBuilder<S>,
) : CommandNodeScope<S, LiteralArgumentBuilder<S>>(brigadier) {
    internal val declaredAliases = linkedSetOf<String>()

    fun alias(name: String) {
        declaredAliases += name
    }

    fun aliases(vararg names: String) {
        declaredAliases += names
    }
}

@BrigadierCommandDsl
class ArgumentCommandScope<S, T> internal constructor(
    brigadier: RequiredArgumentBuilder<S, T>,
) : CommandNodeScope<S, RequiredArgumentBuilder<S, T>>(brigadier) {
    fun suggests(provider: SuggestionProvider<S>) {
        brigadier.suggests(provider)
    }

    fun suggests(block: CommandSuggestions<S>.() -> Unit) {
        brigadier.suggests { context, builder ->
            CommandSuggestions(context, builder).block()
            builder.buildFuture()
        }
    }

    fun suggestsAsync(block: CommandSuggestions<S>.() -> CompletableFuture<Suggestions>) {
        brigadier.suggests { context, builder -> CommandSuggestions(context, builder).block() }
    }

    fun suggests(values: Iterable<String>) {
        suggests { suggestMatching(values) }
    }

    fun suggests(vararg values: String) {
        suggests(values.asIterable())
    }
}

@BrigadierCommandDsl
class CommandTreeScope<S> internal constructor(
    val dispatcher: CommandDispatcher<S>,
) : CommandContainer<S>() {
    val root: RootCommandNode<S>
        get() = dispatcher.root

    override fun attach(node: CommandNode<S>): CommandNode<S> {
        root.addChild(node)
        return checkNotNull(root.getChild(node.name))
    }

    fun resultConsumer(consumer: ResultConsumer<S>) {
        dispatcher.setConsumer(consumer)
    }

    fun resultConsumer(block: CommandResult<S>.() -> Unit) {
        dispatcher.setConsumer { context, success, result ->
            CommandResult(context, success, result).block()
        }
    }

    fun find(vararg path: String): CommandNode<S>? = dispatcher.findNode(path.asList())
}

fun <S> CommandDispatcher<S>.commands(block: CommandTreeScope<S>.() -> Unit): CommandDispatcher<S> = apply {
    CommandTreeScope(this).block()
}

fun <S> commandDispatcher(block: CommandTreeScope<S>.() -> Unit): CommandDispatcher<S> =
    CommandDispatcher<S>().commands(block)

fun <S> CommandDispatcher<S>.completionSuggestions(
    input: String,
    source: S,
    cursor: Int = input.length,
): CompletableFuture<Suggestions> = getCompletionSuggestions(parse(input, source), cursor)
