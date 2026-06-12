@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.state.FieldState
import io.github.yulimitbreak.aseptic.state.SnapshotFlowBuilder
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder

/**
 * Declaration of a read-only field whose value is computed from one source field.
 * Caches last calculation
 *
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.derived
 */
class Derived1FieldDeclaration<T1, R> internal constructor(
    internal val source1: FieldDeclaration<T1>,
    internal val mapper: (T1) -> R,
) : FieldDeclaration<R>() {
    override fun convert(
        key: FieldKey,
        fields: StateContainerBuilder.FieldMap,
    ): FieldState<R> =
        State(key, fields[source1], mapper)

    private class State<T1, R>(
        key: FieldKey,
        private val source: FieldState<T1>,
        private val mapper: (T1) -> R,
    ) : FieldState<R>(key) {

        override val dependencies: Set<FieldState<*>> = setOf(source)

        @Volatile
        private var cache: Pair<T1, R> = source.value.let { it to mapper(it) }

        private fun compute(s1: T1): R {
            val c = cache
            if (c.first == s1) return c.second
            return mapper(s1).also { cache = s1 to it }
        }

        override val value: R get() = compute(source.value)

        override fun buildSnapshotFlow(snapshotFlowBuilder: SnapshotFlowBuilder) {
            source.buildSnapshotFlow(snapshotFlowBuilder)
            snapshotFlowBuilder.addMapper(key) { map -> compute(map[source.key]) }
        }
    }
}

/**
 * Declaration of a read-only field whose value is computed from two source fields.
 * Caches last calculation
 *
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.derived
 */
class Derived2FieldDeclaration<T1, T2, R> internal constructor(
    internal val source1: FieldDeclaration<T1>,
    internal val source2: FieldDeclaration<T2>,
    internal val mapper: (T1, T2) -> R,
) : FieldDeclaration<R>() {
    override fun convert(
        key: FieldKey,
        fields: StateContainerBuilder.FieldMap,
    ): FieldState<R> =
        State(key, fields[source1], fields[source2], mapper)

    private class State<T1, T2, R>(
        key: FieldKey,
        private val source1: FieldState<T1>,
        private val source2: FieldState<T2>,
        private val mapper: (T1, T2) -> R,
    ) : FieldState<R>(key) {

        override val dependencies = setOf(source1, source2)

        @Volatile
        private var cache: Triple<T1, T2, R> =
            Triple(source1.value, source2.value, mapper(source1.value, source2.value))

        private fun compute(s1: T1, s2: T2): R {
            val c = cache
            if (c.first == s1 && c.second == s2) return c.third
            return mapper(s1, s2).also { cache = Triple(s1, s2, it) }
        }

        override val value: R get() = compute(source1.value, source2.value)

        override fun buildSnapshotFlow(snapshotFlowBuilder: SnapshotFlowBuilder) {
            source1.buildSnapshotFlow(snapshotFlowBuilder)
            source2.buildSnapshotFlow(snapshotFlowBuilder)
            snapshotFlowBuilder.addMapper(key) { map -> compute(map[source1.key], map[source2.key]) }
        }
    }
}

/**
 * Declaration of a read-only field whose value is computed from three source fields.
 * Caches last calculation
 *
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.derived
 */
class Derived3FieldDeclaration<T1, T2, T3, R> internal constructor(
    internal val source1: FieldDeclaration<T1>,
    internal val source2: FieldDeclaration<T2>,
    internal val source3: FieldDeclaration<T3>,
    internal val mapper: (T1, T2, T3) -> R,
) : FieldDeclaration<R>() {

    override fun convert(
        key: FieldKey,
        fields: StateContainerBuilder.FieldMap,
    ): FieldState<R> =
        State(key, fields[source1], fields[source2], fields[source3], mapper)

    private class State<T1, T2, T3, R>(
        key: FieldKey,
        private val source1: FieldState<T1>,
        private val source2: FieldState<T2>,
        private val source3: FieldState<T3>,
        private val mapper: (T1, T2, T3) -> R,
    ) : FieldState<R>(key) {

        override val dependencies = setOf(source1, source2, source3)

        private data class Cache<T1, T2, T3, R>(val s1: T1, val s2: T2, val s3: T3, val result: R)

        @Volatile
        private var cache = Cache(
            source1.value,
            source2.value,
            source3.value,
            mapper(source1.value, source2.value, source3.value)
        )

        private fun compute(s1: T1, s2: T2, s3: T3): R {
            val c = cache
            if (c.s1 == s1 && c.s2 == s2 && c.s3 == s3) return c.result
            return mapper(s1, s2, s3).also { cache = Cache(s1, s2, s3, it) }
        }

        override val value: R get() = compute(source1.value, source2.value, source3.value)

        override fun buildSnapshotFlow(snapshotFlowBuilder: SnapshotFlowBuilder) {
            source1.buildSnapshotFlow(snapshotFlowBuilder)
            source2.buildSnapshotFlow(snapshotFlowBuilder)
            source3.buildSnapshotFlow(snapshotFlowBuilder)
            snapshotFlowBuilder.addMapper(
                key
            ) { map -> compute(map[source1.key], map[source2.key], map[source3.key]) }
        }
    }
}

/**
 * Declaration of a read-only field derived from four or more source fields.
 * Caches last calculation
 *
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.derived
 */
class DerivedNFieldDeclaration<T, R> internal constructor(
    internal val sources: List<FieldDeclaration<T>>,
    internal val mapper: (List<T>) -> R,
) : FieldDeclaration<R>() {
    @Suppress("UNCHECKED_CAST")
    override fun convert(
        key: FieldKey,
        fields: StateContainerBuilder.FieldMap,
    ): FieldState<R> =
        State(
            key = key,
            sources = sources.map { fields[it] } as List<FieldState<Any?>>,
            mapper = mapper as (List<Any?>) -> R,
        )

    private class State<R>(
        key: FieldKey,
        private val sources: List<FieldState<Any?>>,
        private val mapper: (List<Any?>) -> R,
    ) : FieldState<R>(key) {

        override val dependencies = sources.toSet()

        @Volatile
        private var cache: Pair<List<Any?>, R> = sources.map { it.value }.let { it to mapper(it) }

        private fun compute(inputProvider: (FieldState<Any?>) -> Any?): R {
            val (cacheKey, cacheValue) = cache
            sources.forEachIndexed { index, state ->
                if (cacheKey[index] != inputProvider(state)) {
                    val inputs = sources.map(inputProvider)
                    return@compute mapper(inputs).also { cache = inputs to it }
                }
            }
            return cacheValue
        }

        override val value: R get() = compute { it.value }

        override fun buildSnapshotFlow(snapshotFlowBuilder: SnapshotFlowBuilder) {
            sources.forEach { it.buildSnapshotFlow(snapshotFlowBuilder) }
            snapshotFlowBuilder.addMapper(key) { map -> compute { map[it.key] } }
        }
    }
}
