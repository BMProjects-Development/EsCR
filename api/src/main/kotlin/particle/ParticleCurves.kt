package com.algorithmlx.ecr.api.particle

import com.algorithmlx.ecr.api.molang.compiler.eval
import com.algorithmlx.ecr.api.molang.runtime.MolangContext
import com.algorithmlx.ecr.api.molang.runtime.Variables
import com.algorithmlx.ecr.api.particle.file.BedrockParticleFile

class CurveVariables(
    private val context: () -> MolangContext,
    private val curves: Map<String, BedrockParticleFile.Curve>
) : Variables {
    private var frame = 0
    private val variables = mutableMapOf<String, Variable?>()

    fun update() {
        frame++
    }

    override fun getOrNull(name: String): Variables.Variable? = variables.getOrPut(name) {
        curves["variable.$name"]?.let(::Variable)
    }

    override fun getOrPut(name: String, initialValue: Float): Variables.Variable =
        getOrNull(name) ?: throw UnsupportedOperationException("Unknown curve variable: $name")

    private inner class Variable(private val curve: BedrockParticleFile.Curve) : Variables.Variable {
        private var cachedFrame = -1
        private var cachedValue = 0f

        override fun get(): Float {
            if (cachedFrame != frame) {
                cachedValue = curve.eval(context())
                cachedFrame = frame
            }
            return cachedValue
        }

        override fun set(value: Float) = Unit
    }
}

private fun BedrockParticleFile.Curve.eval(context: MolangContext): Float {
    val evaluatedRange = range.eval(context)
    val inputValue = if (evaluatedRange == 0f) 0f else input.eval(context) / evaluatedRange
    if (nodes.isEmpty()) return 0f

    return when (type) {
        BedrockParticleFile.Curve.Type.Linear -> {
            val position = inputValue * nodes.lastIndex
            val index = position.toInt()
            when {
                index < 0 -> nodes.first().eval(context)
                index >= nodes.lastIndex -> nodes.last().eval(context)
                else -> nodes[index].eval(context).lerp(nodes[index + 1].eval(context), position - index)
            }
        }

        BedrockParticleFile.Curve.Type.Bezier -> {
            require(nodes.size >= 4) { "Bezier particle curves require four nodes" }
            bezier(inputValue, nodes[0].eval(context), nodes[1].eval(context), nodes[2].eval(context), nodes[3].eval(context))
        }

        BedrockParticleFile.Curve.Type.CatmullRom -> {
            if (nodes.size < 4) return nodes.first().eval(context)
            val position = 1 + inputValue * (nodes.lastIndex - 2)
            val index = position.toInt()
            when {
                index < 1 -> nodes[1].eval(context)
                index >= nodes.lastIndex - 1 -> nodes[nodes.lastIndex - 1].eval(context)
                else -> catmullRom(
                    position - index,
                    nodes[index - 1].eval(context),
                    nodes[index].eval(context),
                    nodes[index + 1].eval(context),
                    nodes[index + 2].eval(context)
                )
            }
        }

        BedrockParticleFile.Curve.Type.BezierChain -> {
            val segmentCount = (nodes.size - 1) / 3
            if (segmentCount <= 0) return nodes.first().eval(context)
            val position = inputValue * segmentCount
            val segmentIndex = position.toInt()
            when {
                segmentIndex < 0 -> nodes.first().eval(context)
                segmentIndex >= segmentCount -> nodes.last().eval(context)
                else -> {
                    val i = segmentIndex * 3
                    bezier(
                        position - segmentIndex,
                        nodes[i].eval(context),
                        nodes[i + 1].eval(context),
                        nodes[i + 2].eval(context),
                        nodes[i + 3].eval(context)
                    )
                }
            }
        }
    }
}
