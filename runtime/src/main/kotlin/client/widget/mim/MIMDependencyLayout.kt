package com.algorithmlx.ecr.client.widget.mim

import kotlin.math.ceil

internal object MIMDependencyLayout {
    const val WIDTH = 256
    const val HEIGHT = 256
    const val DISPLAY_SCALE = 0.75f
    const val TITLE_HEIGHT = 28
    const val RESULT_X = 13
    const val RESULT_Y = 34
    const val RESULT_TEXT_X = 39
    const val PAN_STEP = 18.0
    const val ZOOM_STEP = 0.25f
    const val BODY_X = 9
    const val BODY_Y = 66
    const val BODY_WIDTH = 238
    const val BODY_HEIGHT = 149
    const val PADDING = 4
    const val NODE_HEIGHT = 32
    const val NODE_WIDTH = 96
    const val NODE_GAP = 24
    const val HORIZONTAL_STEP = NODE_WIDTH + NODE_GAP
    const val VERTICAL_STEP = NODE_HEIGHT + NODE_GAP
    const val SCROLL_X = 234
    const val ZOOM_Y = 194
    const val ZOOM_X = 226
    const val ZOOM_SCROLL_X = 213
    const val ZOOM_GAP = 21
    const val BUTTON_SIZE = 17
    const val CLOSE_X = 232
    const val CLOSE_Y = 7
    const val CONTROL_GAP = 4
    const val QUANTITY_WIDTH = 32
    const val INCREASE_X = CLOSE_X - CONTROL_GAP - BUTTON_SIZE
    const val QUANTITY_X = INCREASE_X - CONTROL_GAP - QUANTITY_WIDTH
    const val DECREASE_X = QUANTITY_X - CONTROL_GAP - BUTTON_SIZE
    const val TITLE_X = 11
    const val TITLE_WIDTH = DECREASE_X - CONTROL_GAP - TITLE_X
    const val CRAFT_X = 149
    const val STOP_X = 217
    const val ACTION_Y = 222
    const val CONTENT_X = BODY_X + PADDING
    const val CONTENT_Y = BODY_Y + PADDING
    const val CONTENT_HEIGHT = BODY_HEIGHT - PADDING * 2
}

internal class MIMDependencyViewport {
    var zoom = 1f
        private set
    var horizontal = 0.0
        private set
    var vertical = 0.0
        private set
    var graph = MIMDependencyGraph(emptyList())
        private set
    private val contentWidth: Int get() = graph.width
    private val contentHeight: Int get() = graph.height
    val maxVertical: Double get() = (ceil(contentHeight * zoom.toDouble()) - MIMDependencyLayout.CONTENT_HEIGHT).coerceAtLeast(0.0)
    val hasScrollbar: Boolean get() = maxVertical > 0
    val width: Int get() = (if (hasScrollbar) MIMDependencyLayout.SCROLL_X else MIMDependencyLayout.BODY_X + MIMDependencyLayout.BODY_WIDTH) -
        MIMDependencyLayout.PADDING - MIMDependencyLayout.CONTENT_X
    val horizontalPadding: Double get() = ((width - contentWidth * zoom.toDouble()) / 2).coerceAtLeast(0.0)
    val plusX: Int get() = if (hasScrollbar) MIMDependencyLayout.ZOOM_SCROLL_X else MIMDependencyLayout.ZOOM_X
    val step: Double get() = MIMDependencyLayout.VERTICAL_STEP * zoom.toDouble()

    fun update(parents: List<Int>) {
        val wasEmpty = graph.nodes.isEmpty()
        graph = MIMDependencyGraph(parents)
        if (wasEmpty && graph.nodes.isNotEmpty())
            horizontal = (graph.nodes.first().x + MIMDependencyLayout.NODE_WIDTH / 2.0) * zoom - width / 2.0 + horizontalPadding
        constrain()
    }

    fun reset() {
        horizontal = 0.0
        vertical = 0.0
        update(emptyList())
    }

    fun zoom(delta: Float) {
        val previous = zoom
        val centerX = (horizontal + width / 2.0 - horizontalPadding) / previous
        val centerY = (vertical + MIMDependencyLayout.CONTENT_HEIGHT / 2.0) / previous
        zoom = (zoom + delta).coerceIn(0.5f, 1.5f)
        horizontal = centerX * zoom - width / 2.0 + horizontalPadding
        vertical = centerY * zoom - MIMDependencyLayout.CONTENT_HEIGHT / 2.0
        constrain()
    }

    fun scroll(horizontal: Double, vertical: Double) {
        this.horizontal += horizontal
        this.vertical += vertical
        constrain()
    }

    fun selectVertical(value: Double) {
        vertical = value.coerceIn(0.0, maxVertical)
    }

    fun visibleNodes(): List<Int> = graph.nodes.indices.filter { index ->
        val node = graph.nodes[index]
        (node.x + MIMDependencyLayout.NODE_WIDTH) * zoom + horizontalPadding > horizontal &&
            node.x * zoom + horizontalPadding < horizontal + width &&
            (node.y + MIMDependencyLayout.NODE_HEIGHT) * zoom > vertical &&
            node.y * zoom < vertical + MIMDependencyLayout.CONTENT_HEIGHT
    }

    fun contains(x: Double, y: Double): Boolean = x >= MIMDependencyLayout.CONTENT_X && x < MIMDependencyLayout.CONTENT_X + width &&
        y >= MIMDependencyLayout.CONTENT_Y && y < MIMDependencyLayout.CONTENT_Y + MIMDependencyLayout.CONTENT_HEIGHT && !overZoom(x, y)

    fun nodeAt(x: Double, y: Double): Int? {
        if (!contains(x, y)) return null
        val localY = (y - MIMDependencyLayout.CONTENT_Y + vertical) / zoom
        val localX = (x - MIMDependencyLayout.CONTENT_X + horizontal - horizontalPadding) / zoom
        return graph.nodes.indexOfFirst { it.contains(localX, localY) }.takeIf { it >= 0 }
    }

    fun overZoom(x: Double, y: Double): Boolean = x >= plusX - MIMDependencyLayout.ZOOM_GAP - MIMDependencyLayout.PADDING &&
        x < plusX + MIMDependencyLayout.BUTTON_SIZE + MIMDependencyLayout.PADDING &&
        y >= MIMDependencyLayout.ZOOM_Y - MIMDependencyLayout.PADDING &&
        y < MIMDependencyLayout.ZOOM_Y + MIMDependencyLayout.BUTTON_SIZE + MIMDependencyLayout.PADDING

    private fun constrain() {
        vertical = vertical.coerceIn(0.0, maxVertical)
    }
}
