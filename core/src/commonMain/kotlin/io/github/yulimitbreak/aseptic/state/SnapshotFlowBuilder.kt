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

/**
 * Builds a single [Flow] emitting a consistent snapshot of all registered fields.
 *
 * Source fields register via [addSource]; derived fields register via [addMapper], computing their
 * value from already-emitted sources. [build] creates a flow combining source flows and calculating
 * all relevant derived fields.
 *
 * A control gate flow can suppress emissions (e.g. during an atomic multi-field write): while it
 * emits `false`, no snapshot is produced, collapsing intermediate states into a single emission.
 */
internal class SnapshotFlowBuilder {

    private val sources = mutableListOf<SourceEntry>()

    private val mappers = mutableListOf<MappingEntry>()

    fun <T> addSource(key: FieldKey, flow: Flow<T>) {
        sources.add(SourceEntry(key, flow))
    }

    fun <T> addMapper(key: FieldKey, mapper: (UncheckedMap<FieldKey>) -> T) {
        mappers.add(MappingEntry(key, mapper))
    }

    /**
     * Combines all source flows and uses mappers to calculate all requested values.
     *
     * [controlGate] allows to stop emitting new values when it's `false`, it allows to do
     * atomic writes without recalculating a snapshot for every intermediate state
     */
    fun build(controlGate: Flow<Boolean> = flowOf(true)): Flow<UncheckedMap<FieldKey>> {
        val mappers = this.mappers.distinctBy { it.key }

        val sourceFlows = this.sources.map { (key, flow) ->
            flow.map { SourceOrGate.Source(key, it) }
        } + controlGate.map { SourceOrGate.Gate(it) }

        return combine(sourceFlows) { sources ->
            if (!(sources.last() as SourceOrGate.Gate).open) return@combine null

            val resultMap = sources.dropLast(1).associateTo(mutableMapOf()) {
                it as SourceOrGate.Source
                it.key to it.value
            }
            val wrapper = UncheckedMapWrapper(resultMap)

            mappers.forEach { (key, mapper) ->
                if (resultMap.containsKey(key)) return@forEach
                resultMap[key] = mapper.invoke(wrapper)
            }

            wrapper
        }.filterNotNull()
    }

    private data class SourceEntry(val key: FieldKey, val flow: Flow<Any?>)

    private data class MappingEntry(val key: FieldKey, val mapper: (UncheckedMap<FieldKey>) -> Any?)

    private sealed interface SourceOrGate {

        class Source(val key: FieldKey, val value: Any?) : SourceOrGate

        class Gate(val open: Boolean) : SourceOrGate
    }
}
