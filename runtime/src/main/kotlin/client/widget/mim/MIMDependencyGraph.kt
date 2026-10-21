package com.algorithmlx.ecr.client.widget.mim

internal class MIMDependencyGraph(parents: List<Int>) {
    val nodes: List<Node>
    val width: Int
    val height: Int

    init {
        val children = List(parents.size) { mutableListOf<Int>() }
        val roots = mutableListOf<Int>()
        val depths = IntArray(parents.size)
        parents.forEachIndexed { index, parent ->
            require(parent in -1 until index) { "Invalid dependency graph parent" }
            if (parent < 0) roots += index
            else {
                children[parent] += index
                depths[index] = depths[parent] + 1
            }
        }
        val spans = IntArray(parents.size) { 1 }
        for (index in parents.indices.reversed())
            if (children[index].isNotEmpty()) spans[index] = children[index].sumOf { spans[it] }
        val columns = IntArray(parents.size)
        var rootColumn = 0
        roots.forEach { index -> columns[index] = rootColumn; rootColumn += spans[index] }
        parents.indices.forEach { index ->
            var column = columns[index]
            children[index].forEach { child -> columns[child] = column; column += spans[child] }
        }
        nodes = parents.indices.map { index ->
            Node(
                columns[index] * MIMDependencyLayout.HORIZONTAL_STEP + (spans[index] - 1) * MIMDependencyLayout.HORIZONTAL_STEP / 2,
                depths[index] * MIMDependencyLayout.VERTICAL_STEP
            )
        }
        width = (nodes.maxOfOrNull { it.x } ?: -MIMDependencyLayout.NODE_WIDTH) + MIMDependencyLayout.NODE_WIDTH
        height = (nodes.maxOfOrNull { it.y } ?: -MIMDependencyLayout.NODE_HEIGHT) + MIMDependencyLayout.NODE_HEIGHT
    }

    class Node(val x: Int, val y: Int) {
        fun contains(x: Double, y: Double): Boolean = x >= this.x && x < this.x + MIMDependencyLayout.NODE_WIDTH &&
            y >= this.y && y < this.y + MIMDependencyLayout.NODE_HEIGHT
    }
}
