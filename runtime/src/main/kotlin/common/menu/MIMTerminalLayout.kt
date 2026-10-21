package com.algorithmlx.ecr.common.menu

object MIMTerminalLayout {
    const val COLUMNS = 9
    const val MIN_ROWS = 4
    const val MAX_ROWS = 9
    const val MAX_SLOTS = COLUMNS * MAX_ROWS
    const val WIDTH = 194
    const val BASE_ROWS = 4
    const val BASE_HEIGHT = 190
    const val GRID_X = 8
    const val GRID_Y = 32
    const val STORAGE_X = GRID_X - 1
    const val STORAGE_WIDTH = 180
    const val ROW_HEIGHT = 18
    const val PLAYER_X = 17
    const val SEARCH_X = 10
    const val SEARCH_Y = 10
    const val SEARCH_WIDTH = 174
    const val SEARCH_HEIGHT = 14
    const val SCROLL_X = 174
    const val CRAFT_BUTTON_X = 194
    const val CRAFT_BUTTON_Y = 7
    const val CRAFT_BUTTON_WIDTH = 21
    const val CRAFT_BUTTON_HEIGHT = 24
    const val MODE_BUTTON_Y = CRAFT_BUTTON_Y + CRAFT_BUTTON_HEIGHT + 4
    const val PATCH_HEIGHT = 54
    private const val WINDOW_MARGIN = 8

    fun rowsForHeight(height: Int): Int =
        (BASE_ROWS + (height.toLong() - BASE_HEIGHT - WINDOW_MARGIN).floorDiv(ROW_HEIGHT.toLong()))
            .coerceIn(1L, MAX_ROWS.toLong()).toInt()

    fun height(rows: Int): Int = BASE_HEIGHT + rowOffset(rows)
    fun playerY(rows: Int): Int = 108 + rowOffset(rows)
    fun scrollHeight(rows: Int): Int = rows.coerceIn(1, MAX_ROWS) * ROW_HEIGHT - 2
    private fun rowOffset(rows: Int): Int = (rows.coerceIn(1, MAX_ROWS) - BASE_ROWS) * ROW_HEIGHT

    fun window(itemCount: Int, maxRows: Int, requestedRow: Int): Window {
        val totalRows = ((itemCount.coerceAtLeast(0).toLong() + COLUMNS - 1) / COLUMNS).toInt().coerceAtLeast(1)
        val visibleRows = totalRows.coerceAtLeast(MIN_ROWS).coerceAtMost(maxRows.coerceIn(1, MAX_ROWS))
        return Window(totalRows, visibleRows, requestedRow.coerceIn(0, (totalRows - visibleRows).coerceAtLeast(0)))
    }

    data class Window(val totalRows: Int, val visibleRows: Int, val scrollRow: Int) {
        val maxScrollRow: Int get() = (totalRows - visibleRows).coerceAtLeast(0)
        val firstItem: Int get() = scrollRow * COLUMNS
        val visibleSlots: Int get() = visibleRows * COLUMNS
    }
}
