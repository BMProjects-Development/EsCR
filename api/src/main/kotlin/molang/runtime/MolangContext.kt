package com.algorithmlx.ecr.api.molang.runtime

data class MolangContext(val query: Query, val variables: Variables = VariablesMap()) {
    companion object {
        val EMPTY = MolangContext(Query.EMPTY)
    }
}
