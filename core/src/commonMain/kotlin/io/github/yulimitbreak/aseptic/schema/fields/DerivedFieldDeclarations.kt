@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.FieldState
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Declaration of a read-only field whose value is computed from one source field.
 *
 * At runtime the field recomputes via [mapper] whenever [source1] emits a new value.
 *
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.derived
 */
class Derived1FieldDeclaration<T1, R> internal constructor(
    internal val source1: FieldDeclaration<T1>,
    internal val mapper: (T1) -> R,
) : FieldDeclaration<R>() {
    override fun convert(fields: StateContainerBuilder.FieldMap, coroutineScope: CoroutineScope): FieldState<R> =
        State(fields[source1].flow, mapper, coroutineScope)

    private class State<T1, R>(
        sourceFlow: StateFlow<T1>,
        mapper: (T1) -> R,
        coroutineScope: CoroutineScope,
    ) : FieldState<R>() {
        override val flow: StateFlow<R> = sourceFlow
            .map(mapper)
            .stateIn(coroutineScope, SharingStarted.Eagerly, mapper(sourceFlow.value))
    }
}

/**
 * Declaration of a read-only field whose value is computed from two source fields.
 *
 * At runtime the field recomputes via [mapper] whenever either source emits a new value,
 * combining the latest value of each source.
 *
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.derived
 */
class Derived2FieldDeclaration<T1, T2, R> internal constructor(
    internal val source1: FieldDeclaration<T1>,
    internal val source2: FieldDeclaration<T2>,
    internal val mapper: (T1, T2) -> R,
) : FieldDeclaration<R>() {
    override fun convert(fields: StateContainerBuilder.FieldMap, coroutineScope: CoroutineScope): FieldState<R> =
        State(fields[source1].flow, fields[source2].flow, mapper, coroutineScope)

    private class State<T1, T2, R>(
        flow1: StateFlow<T1>,
        flow2: StateFlow<T2>,
        mapper: (T1, T2) -> R,
        coroutineScope: CoroutineScope,
    ) : FieldState<R>() {
        override val flow: StateFlow<R> = combine(flow1, flow2, mapper)
            .stateIn(coroutineScope, SharingStarted.Eagerly, mapper(flow1.value, flow2.value))
    }
}

/**
 * Declaration of a read-only field whose value is computed from three source fields.
 *
 * At runtime the field recomputes via [mapper] whenever any source emits a new value,
 * combining the latest value of each source.
 *
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.derived
 */
class Derived3FieldDeclaration<T1, T2, T3, R> internal constructor(
    internal val source1: FieldDeclaration<T1>,
    internal val source2: FieldDeclaration<T2>,
    internal val source3: FieldDeclaration<T3>,
    internal val mapper: (T1, T2, T3) -> R,
) : FieldDeclaration<R>() {
    override fun convert(fields: StateContainerBuilder.FieldMap, coroutineScope: CoroutineScope): FieldState<R> =
        State(fields[source1].flow, fields[source2].flow, fields[source3].flow, mapper, coroutineScope)

    private class State<T1, T2, T3, R>(
        flow1: StateFlow<T1>,
        flow2: StateFlow<T2>,
        flow3: StateFlow<T3>,
        mapper: (T1, T2, T3) -> R,
        coroutineScope: CoroutineScope,
    ) : FieldState<R>() {
        override val flow: StateFlow<R> = combine(flow1, flow2, flow3, mapper)
            .stateIn(coroutineScope, SharingStarted.Eagerly, mapper(flow1.value, flow2.value, flow3.value))
    }
}

/**
 * Declaration of a read-only field derived from four or more source fields.
 *
 * At runtime the field recomputes via [mapper] whenever any source emits a new value.
 * All current source values are passed as a list.
 *
 * For 1–3 sources with distinct types use [Derived1FieldDeclaration], [Derived2FieldDeclaration],
 * or [Derived3FieldDeclaration] instead.
 *
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.derived
 */
class DerivedNFieldDeclaration<T, R> internal constructor(
    internal val sources: List<FieldDeclaration<T>>,
    internal val mapper: (List<T>) -> R,
) : FieldDeclaration<R>() {
    @Suppress("UNCHECKED_CAST")
    override fun convert(fields: StateContainerBuilder.FieldMap, coroutineScope: CoroutineScope): FieldState<R> =
        State(
            sourceFlows = sources.map { fields[it] } as List<Flow<Any?>>,
            mapper = mapper as (List<Any?>) -> R,
            coroutineScope = coroutineScope,
        )

    private class State<R>(
        sourceFlows: List<Flow<Any?>>,
        mapper: (List<Any?>) -> R,
        coroutineScope: CoroutineScope,
    ) : FieldState<R>() {
        override val flow: StateFlow<R> = combine(sourceFlows) { values -> mapper(values.toList()) }
            .stateIn(coroutineScope, SharingStarted.Eagerly, mapper(sourceFlows.map { (it as StateFlow<Any?>).value }))
    }
}
