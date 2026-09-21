package com.algorithmlx.ecr.api.mru.balance

import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import java.util.IdentityHashMap

class MRUBalanceContainer(
    override val maxUpperBalance: Double = MAX_BALANCE,
    override val maxLowerBalance: Double = MAX_BALANCE,
    override val minUpperBalance: Double = MIN_BALANCE,
    override val minLowerBalance: Double = MIN_BALANCE,
    initialUpperBalance: Double = minUpperBalance,
    initialLowerBalance: Double = minLowerBalance,
    private val onChange: (MRUBalance) -> Unit = {}
) : MutableMRUBalance {
    private var mutableUpperBalance = 0.0
    private var mutableLowerBalance = 0.0
    private var sourceTick = Long.MIN_VALUE
    private val sources = IdentityHashMap<MRUBalance, Unit>()
    private var sourceUpperAverage = 0.0
    private var sourceLowerAverage = 0.0

    init {
        require(!minUpperBalance.isNaN() && !maxUpperBalance.isNaN() && minUpperBalance <= maxUpperBalance) {
            "Upper MRU balance bounds are invalid: $minUpperBalance..$maxUpperBalance"
        }
        require(!minLowerBalance.isNaN() && !maxLowerBalance.isNaN() && minLowerBalance <= maxLowerBalance) {
            "Lower MRU balance bounds are invalid: $minLowerBalance..$maxLowerBalance"
        }
        mutableUpperBalance = clamp(initialUpperBalance, minUpperBalance, maxUpperBalance)
        mutableLowerBalance = clamp(initialLowerBalance, minLowerBalance, maxLowerBalance)
    }

    override val upperBalance: Double get() = mutableUpperBalance
    override val lowerBalance: Double get() = mutableLowerBalance

    override fun setUpperBalance(newBalance: Double) {
        resetSourceSamples()
        applyBalance(newBalance, mutableLowerBalance)
    }

    override fun setLowerBalance(newBalance: Double) {
        resetSourceSamples()
        applyBalance(mutableUpperBalance, newBalance)
    }

    override fun setBalance(
        upperBalance: Double,
        lowerBalance: Double
    ) {
        resetSourceSamples()
        applyBalance(upperBalance, lowerBalance)
    }

    override fun updateUpperBalance(changeOn: Double): Double {
        val oldBalance = mutableUpperBalance
        setUpperBalance(oldBalance + changeOn)
        return mutableUpperBalance - oldBalance
    }

    override fun updateLowerBalance(changeOn: Double): Double {
        val oldBalance = mutableLowerBalance
        setLowerBalance(oldBalance + changeOn)
        return mutableLowerBalance - oldBalance
    }

    override fun includeSource(
        source: MRUBalance,
        gameTime: Long
    ) {
        if (sourceTick != gameTime) {
            sourceTick = gameTime
            sources.clear()
            sourceUpperAverage = 0.0
            sourceLowerAverage = 0.0
        }
        if (sources.put(source, Unit) != null) return

        val sourceCount = sources.size.toDouble()
        val sourceUpper = clamp(source.upperBalance, minUpperBalance, maxUpperBalance)
        val sourceLower = clamp(source.lowerBalance, minLowerBalance, maxLowerBalance)
        sourceUpperAverage += (sourceUpper - sourceUpperAverage) / sourceCount
        sourceLowerAverage += (sourceLower - sourceLowerAverage) / sourceCount
        applyBalance(sourceUpperAverage, sourceLowerAverage)
    }

    override fun toImmutable(): MRUBalance = ImmutableMRUBalance(this.upperBalance, this.lowerBalance)

    override fun save(output: ValueOutput) {
        output.putDouble("upper_balance", this.mutableUpperBalance)
        output.putDouble("lower_balance", this.mutableLowerBalance)
    }

    override fun load(input: ValueInput) {
        resetSourceSamples()
        applyBalance(
            input.getDoubleOr("upper_balance", minUpperBalance),
            input.getDoubleOr("lower_balance", minLowerBalance),
            notify = false
        )
    }

    private fun applyBalance(
        upperBalance: Double,
        lowerBalance: Double,
        notify: Boolean = true
    ) {
        val nextUpper = clamp(upperBalance, minUpperBalance, maxUpperBalance)
        val nextLower = clamp(lowerBalance, minLowerBalance, maxLowerBalance)
        if (mutableUpperBalance == nextUpper && mutableLowerBalance == nextLower) return

        val previous = toImmutable()
        mutableUpperBalance = nextUpper
        mutableLowerBalance = nextLower
        if (notify) onChange(previous)
    }

    private fun resetSourceSamples() {
        sourceTick = Long.MIN_VALUE
        sources.clear()
        sourceUpperAverage = 0.0
        sourceLowerAverage = 0.0
    }

    companion object {
        const val MIN_BALANCE = 0.0
        const val MAX_BALANCE = 2.0

        private fun clamp(
            value: Double,
            min: Double,
            max: Double
        ): Double = if (value.isNaN()) min else value.coerceIn(min, max)
    }
}

data class ImmutableMRUBalance(
    override val upperBalance: Double,
    override val lowerBalance: Double
) : MRUBalance
