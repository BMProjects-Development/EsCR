package com.algorithmlx.ecr.registry

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.registries.ECRegistries
import com.algorithmlx.ecr.api.registries.RegistrationHandler
import com.algorithmlx.ecr.api.research.ResearchTaskSerializer
import com.algorithmlx.ecr.api.research.content.BookElementSerializer
import com.algorithmlx.ecr.api.research.serializer.ResearchSerializers

object ResearchSerializerRegistry : RegistrationHandler(ModId) {
    init {
        listOf(
            ResearchSerializers.SPACE_ELEMENT,
            ResearchSerializers.VERTICAL_SPACE_ELEMENT,
            ResearchSerializers.TEXT_ELEMENT,
            ResearchSerializers.ITEM_ELEMENT,
            ResearchSerializers.BLOCK_ELEMENT,
            ResearchSerializers.GROUP_ELEMENT,
            ResearchSerializers.MULTIBLOCK_ELEMENT,
            ResearchSerializers.BOOK_MULTIBLOCK_ELEMENT,
            ResearchSerializers.ASSEMBLED_MULTIBLOCK_ELEMENT,
            ResearchSerializers.CRAFTING_ELEMENT
        ).forEach(::registerElement)

        listOf(
            ResearchSerializers.ITEM_TASK,
            ResearchSerializers.EXPERIENCE_TASK,
            ResearchSerializers.CRAFTING_TASK,
            ResearchSerializers.OPEN_TASK,
            ResearchSerializers.TRAVEL_TO_DIMENSION,
            ResearchSerializers.TRAVEL_TO_STRUCTURE
        ).forEach(::registerTask)
    }

    private fun registerElement(serializer: BookElementSerializer<*>) {
        serializer.registerNoEntry(serializer.type, ECRegistries.BOOK_ELEMENT_SERIALIZER)
    }

    private fun registerTask(serializer: ResearchTaskSerializer<*>) {
        serializer.registerNoEntry(serializer.type, ECRegistries.RESEARCH_TASK_SERIALIZER)
    }
}
