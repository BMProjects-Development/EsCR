package com.algorithmlx.ecr.api

import com.algorithmlx.ecr.api.alloy.AlloyRegistrationScope
import com.algorithmlx.ecr.api.alloy.MagicalAlloys
import com.algorithmlx.ecr.api.dsl.BoundGemRegistrationScope
import com.algorithmlx.ecr.api.dsl.ClientRegistrationScope
import com.algorithmlx.ecr.api.dsl.ECRRegistries
import com.algorithmlx.ecr.api.dsl.MIMRegistrationScope
import com.algorithmlx.ecr.api.dsl.MolangRegistrationScope
import com.algorithmlx.ecr.api.dsl.ResearchRegistrationScope
import com.algorithmlx.ecr.api.recipe.dsl.RecipeCompiler
import com.algorithmlx.ecr.api.recipe.dsl.RecipesScope
import com.algorithmlx.ecr.api.event.engine.EventBuses
import net.minecraft.resources.Identifier

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class ECRDsl

@ECRDsl
open class ECRAPIScope(val namespace: String) {
    init { require(Identifier.tryBuild(namespace, "entry") != null) { "Invalid API namespace: $namespace" } }

    fun id(path: String): Identifier = if (':' in path) Identifier.parse(path) else Identifier.fromNamespaceAndPath(namespace, path)
}

class ECRAPI(namespace: String): ECRAPIScope(namespace) {
    fun <T> alloys(block: AlloyRegistrationScope.() -> T): T {
        val scope = AlloyRegistrationScope(namespace)
        val result = scope.block()
        MagicalAlloys.registerAll(scope.declarations, scope.providers)
        return result
    }

    fun recipes(block: RecipesScope.() -> Unit) = RecipeCompiler.register(namespace, block)
    fun <T> mim(block: MIMRegistrationScope.() -> T): T = MIMRegistrationScope(namespace).block()
    fun <T> boundGems(block: BoundGemRegistrationScope.() -> T): T = BoundGemRegistrationScope(namespace).block()
    fun <T> research(block: ResearchRegistrationScope.() -> T): T = ResearchRegistrationScope(namespace).block()
    fun <T> molang(block: MolangRegistrationScope.() -> T): T = MolangRegistrationScope(namespace).block()
    fun <T> client(block: ClientRegistrationScope.() -> T): T = ClientRegistrationScope(namespace).block()
    inline fun <T> events(block: EventBuses.() -> T): T = EventBuses.block()

    fun <T> registries(block: ECRRegistries.() -> T): T {
        val scope = ECRRegistries(namespace)
        val result = scope.block()
        scope.init()
        return result
    }
}

inline fun <T> escrAPI(namespace: String = ModId, block: ECRAPI.() -> T): T = ECRAPI(namespace).block()
