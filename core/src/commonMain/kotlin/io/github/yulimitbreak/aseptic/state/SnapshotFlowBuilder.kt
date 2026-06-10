@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.state

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.util.UncheckedMap
import io.github.yulimitbreak.aseptic.util.UncheckedMapWrapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class SnapshotFlowBuilder {

    private val sources = mutableListOf<SourceEntry<*>>()

    private val mappers = mutableListOf<MappingEntry<*>>()

    fun <T> addSource(source: FieldState<T>, flow: Flow<T>) {
        sources.add(SourceEntry(source, flow))
    }

    fun <T> addMapper(field: FieldState<T>, mapper: (UncheckedMap<FieldState<*>>) -> T) {
        mappers.add(MappingEntry(field, mapper))
    }

    fun build(controlGate: Flow<Boolean> = flowOf(true)): Flow<UncheckedMap<FieldState<*>>> {
        val mappers = this.mappers.distinctBy { it.fieldState }

        val sourceFlows = this.sources.map { (state, flow) ->
            flow.map { SourceOrGate.Source(state, it) }
        } + controlGate.map { SourceOrGate.Gate(it) }

        return combine(sourceFlows) { sources ->
            if (!(sources.last() as SourceOrGate.Gate).open) return@combine null

            val resultMap = sources.dropLast(1).associateTo(mutableMapOf()) {
                it as SourceOrGate.Source
                it.state to it.value
            }
            val wrapper = UncheckedMapWrapper(resultMap)

            mappers.forEach { (field, mapper) ->
                if (resultMap.containsKey(field)) return@forEach
                resultMap[field] = mapper.invoke(wrapper)
            }

            wrapper
        }.filterNotNull()
    }

    private data class SourceEntry<T>(val fieldState: FieldState<T>, val flow: Flow<T>)

    private data class MappingEntry<T>(val fieldState: FieldState<T>, val mapper: (UncheckedMap<FieldState<*>>) -> T)

    private sealed interface SourceOrGate {

        class Source(val state: FieldState<*>, val value: Any?) : SourceOrGate

        class Gate(val open: Boolean) : SourceOrGate
    }
}
