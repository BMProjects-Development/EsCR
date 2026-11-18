package com.algorithmlx.ecr.api.dsl

import com.algorithmlx.ecr.api.ECRAPIScope
import com.algorithmlx.ecr.api.molang.compiler.MolangFunctions
import org.objectweb.asm.Type

class MolangRegistrationScope(namespace: String): ECRAPIScope(namespace) {
    fun function(name: String, owner: Class<*>, method: String, arguments: Int, returnType: Class<*> = Float::class.javaPrimitiveType!!, isStatic: Boolean = true) {
        require(arguments >= 0) { "Molang argument count cannot be negative" }
        MolangFunctions.register(name, Type.getInternalName(owner), method, Type.getDescriptor(returnType), arguments, isStatic)
    }
}
