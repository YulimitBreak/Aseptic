@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.FieldState
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

/**
 * Declaration of a read-only field derived from a source using delta (old→new) logic.
 *
 * Unlike [Derived1FieldDeclaration], [mapper] receives both the *previous* and *new* source value
 * along with the *previous result*, enabling incremental computation (e.g. accumulating diffs,
 * tracking transitions). [initial] is the result value before the first source emission.
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
    override fun convert(flows: StateContainerBuilder.FlowMap, coroutineScope: CoroutineScope): FieldState<R> =
        State(flows[source], initial, mapper, coroutineScope)

    private class State<T, R>(
        sourceFlow: StateFlow<T>,
        initial: R,
        mapper: (T, T, R) -> R,
        coroutineScope: CoroutineScope,
    ) : FieldState<R>() {
        override val flow: StateFlow<R> = sourceFlow
            .drop(1)
            .runningFold(sourceFlow.value to initial) { (prevSource, prevResult), newSource ->
                newSource to mapper(prevSource, newSource, prevResult)
            }
            .map { it.second }
            .stateIn(coroutineScope, SharingStarted.Eagerly, initial)
    }
}
