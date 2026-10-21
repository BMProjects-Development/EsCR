package com.algorithmlx.ecr.common.menu

class MIMConfigurationLayout(
    val texture: String,
    val width: Int,
    val height: Int,
    val gridX: Int,
    val gridY: Int,
    val rows: Int,
    val playerY: Int,
    val scrollBar: ScrollBar? = null,
    val expanding: Boolean = false,
    val columns: Int = MIMTerminalLayout.COLUMNS,
    val fixedSlots: List<Position> = emptyList(),
    val mruBar: Bounds? = null
) {
    init {
        require(width > 0 && height > 0 && rows > 0 && columns > 0) { "Invalid MIM configuration geometry" }
    }

    fun totalRows(slots: Int): Int = (slots - fixedSlots.size - 1).coerceAtLeast(0) / columns + 1
    fun maxScrollRow(slots: Int): Int = (totalRows(slots) - rows).coerceAtLeast(0)
    fun availableSlots(slots: Int, occupied: (Int) -> Boolean): Int {
        if (!expanding) return slots
        val lastOccupied = (slots - 1 downTo fixedSlots.size).firstOrNull(occupied) ?: fixedSlots.size - 1
        var available = fixedSlots.size + maxOf(rows, totalRows(lastOccupied + 1)) * columns
        if ((fixedSlots.size until available).all { it < slots && occupied(it) }) available += columns
        return available
    }
    fun isVisible(index: Int, scrollRow: Int): Boolean = index < fixedSlots.size ||
        (index - fixedSlots.size) / columns in scrollRow until scrollRow + rows
    fun slotX(index: Int): Int = fixedSlots.getOrNull(index)?.x
        ?: (gridX + (index - fixedSlots.size) % columns * MIMTerminalLayout.ROW_HEIGHT)
    fun slotY(index: Int, scrollRow: Int): Int = fixedSlots.getOrNull(index)?.y
        ?: (gridY + ((index - fixedSlots.size) / columns - scrollRow) * MIMTerminalLayout.ROW_HEIGHT)

    data class Position(val x: Int, val y: Int)
    data class Bounds(val x: Int, val y: Int, val width: Int, val height: Int)
    data class ScrollBar(val x: Int, val y: Int, val height: Int, val thumbU: Int = 194, val disabledThumbU: Int = 206)

    companion object {
        val CORE = MIMConfigurationLayout(
            "mim/core", 194, 194, 8, 14, 4, 112, ScrollBar(174, 14, 172), true,
            fixedSlots = listOf(Position(8, 90)), mruBar = Bounds(30, 94, 138, 8)
        )
        val STORAGE = MIMConfigurationLayout("mim/node", 176, 166, 8, 14, 3, 84)
        val CRAFTING = MIMConfigurationLayout("mim/frames", 176, 166, 8, 35, 1, 84)
        val NODE = MIMConfigurationLayout("single_slot_gui", 176, 166, 80, 35, 1, 84)
    }
}
