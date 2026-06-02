@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.FieldState
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

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
    override fun convert(fields: StateContainerBuilder.FieldMap): FieldState<R> = State(fields[source1], mapper)

    private class State<T1, R>(
        private val sourceState: FieldState<T1>,
        private val mapper: (T1) -> R,
    ) : FieldState<R>() {

        private val flow = MutableStateFlow(mapper(sourceState.value))

        init {
            sourceState.addUpdateCallback { flow.value = mapper(it) }
        }

        override fun produceFlow(): Flow<R> = flow
        override val value: R get() = flow.value

        override fun addUpdateCallback(callback: (R) -> Unit) {
            sourceState.addUpdateCallback { callback(mapper(it)) }
        }
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
    override fun convert(fields: StateContainerBuilder.FieldMap): FieldState<R> =
        State(fields[source1], fields[source2], mapper)

    private class State<T1, T2, R>(
        private val source1: FieldState<T1>,
        private val source2: FieldState<T2>,
        private val mapper: (T1, T2) -> R,
    ) : FieldState<R>() {

        override val value: R get() = mapper(source1.value, source2.value)

        override fun produceFlow(): Flow<R> = combine(source1.produceFlow(), source2.produceFlow(), mapper)

        override fun addUpdateCallback(callback: (R) -> Unit) {
            source1.addUpdateCallback { callback(mapper(source1.value, source2.value)) }
            source2.addUpdateCallback { callback(mapper(source1.value, source2.value)) }
        }
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
    override fun convert(fields: StateContainerBuilder.FieldMap): FieldState<R> =
        State(fields[source1], fields[source2], fields[source3], mapper)

    private class State<T1, T2, T3, R>(
        private val source1: FieldState<T1>,
        private val source2: FieldState<T2>,
        private val source3: FieldState<T3>,
        private val mapper: (T1, T2, T3) -> R,
    ) : FieldState<R>() {

        override val value: R get() = mapper(source1.value, source2.value, source3.value)

        override fun produceFlow(): Flow<R> =
            combine(source1.produceFlow(), source2.produceFlow(), source3.produceFlow(), mapper)

        override fun addUpdateCallback(callback: (R) -> Unit) {
            source1.addUpdateCallback { callback(mapper(source1.value, source2.value, source3.value)) }
            source2.addUpdateCallback { callback(mapper(source1.value, source2.value, source3.value)) }
            source3.addUpdateCallback { callback(mapper(source1.value, source2.value, source3.value)) }
        }
    }
}

/**
 * Declaration of a read-only field derived from four or more source fields of the same type.
 *
 * At runtime the field recomputes via [mapper] whenever any source emits a new value.
 * All current source values are passed as a list.
 *
 * For 1–3 sources with distinct types use [Derived1FieldDeclaration], [Derived2FieldDeclaration],
 * or [Derived3FieldDeclaration] instead.
 *
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.derived
 */
@Suppress("UNCHECKED_CAST")
class DerivedNFieldDeclaration<T, R> internal constructor(
    internal val sources: List<FieldDeclaration<T>>,
    internal val mapper: (List<T>) -> R,
) : FieldDeclaration<R>() {
    override fun convert(fields: StateContainerBuilder.FieldMap): FieldState<R> =
        State(sources.map { fields[it] }, mapper as (List<Any?>) -> R)

    private class State<R>(
        private val sources: List<FieldState<Any?>>,
        private val mapper: (List<Any?>) -> R,
    ) : FieldState<R>() {

        override val value: R get() = mapper(sources.map { it.value })

        @Suppress("UNCHECKED_CAST")
        override fun produceFlow(): Flow<R> =
            combine(sources.map { it.produceFlow() }) { values -> mapper(values.toList()) }

        override fun addUpdateCallback(callback: (R) -> Unit) {
            sources.forEach { source ->
                source.addUpdateCallback { callback(mapper(sources.map { it.value })) }
            }
        }
    }
}
