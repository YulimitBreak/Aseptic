@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.FieldState
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Declaration of a read-only field derived from a source using delta (old→new) logic.
 *
 * Unlike [Derived1FieldDeclaration], [mapper] receives both the *previous* and *new* source value
 * along with the *previous result*, enabling incremental computation (e.g. accumulating diffs,
 * tracking transitions). [initial] is the result value before the first source emission.
 *
 * The mapper is called synchronously inside the source field's update, under the source's mutex.
 * Every source update is processed — no intermediate values are skipped.
 *
 * @param T the type of the source field value.
 * @param R the type of the derived field value.
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.derivedDelta
 */
class DerivedDeltaFieldDeclaration<T, R> internal constructor(
    internal val source: FieldDeclaration<T>,
    /** The value of the derived field before the first source emission. */
    internal val initial: R,
    internal val mapper: (oldSource: T, newSource: T, oldResult: R) -> R,
) : FieldDeclaration<R>() {
    override fun convert(fields: StateContainerBuilder.FieldMap): FieldState<R> =
        State(fields[source], initial, mapper)

    private class State<T, R>(
        sourceState: FieldState<T>,
        initial: R,
        private val mapper: (T, T, R) -> R,
    ) : FieldState<R>() {

        private val flow = MutableStateFlow(sourceState.value to initial)
        private val callbacks = mutableListOf<(R) -> Unit>()

        init {
            sourceState.addUpdateCallback { newSource ->
                flow.update { (prevSource, prevValue) ->
                    newSource to mapper(prevSource, newSource, prevValue)
                }
                callbacks.forEach { it(flow.value.second) }
            }
        }

        override fun produceFlow(): Flow<R> = flow.map { it.second }
        override val value: R get() = flow.value.second

        override fun addUpdateCallback(callback: (R) -> Unit) {
            callbacks.add(callback)
        }
    }
}
