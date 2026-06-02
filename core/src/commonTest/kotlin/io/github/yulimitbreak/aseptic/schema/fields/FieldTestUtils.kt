@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.FieldState
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.github.yulimitbreak.aseptic.state.UpdatableFieldState

internal object FieldTestUtils {

    fun <T> MutableValueFieldDeclaration<T>.buildState(): Pair<UpdatableFieldState<T, (T) -> T>, StateContainerBuilder.FieldMap> {
        val state = convert(StateContainerBuilder.FieldMap()).asUpdatable<T, (T) -> T>()
        return state to StateContainerBuilder.FieldMap().also { it[this] = state }
    }

    fun <T> buildStates(
        decls: List<MutableValueFieldDeclaration<T>>,
    ): Pair<List<UpdatableFieldState<T, (T) -> T>>, StateContainerBuilder.FieldMap> {
        val states = decls.map { it.convert(StateContainerBuilder.FieldMap()).asUpdatable<T, (T) -> T>() }
        val map = StateContainerBuilder.FieldMap().also { map ->
            decls.zip(states).forEach { (decl, state) -> map[decl] = state }
        }
        return states to map
    }

    fun <T> buildStates(vararg decls: MutableValueFieldDeclaration<T>) = buildStates(decls.toList())

    @Suppress("UNCHECKED_CAST")
    fun <T, U> FieldState<T>.asUpdatable() = this as UpdatableFieldState<T, U>

    suspend fun <T, U> UpdatableFieldState<T, U>.locked(block: UpdatableFieldState<T, U>.() -> Unit) {
        lock()
        try { block() } finally { unlock() }
    }

    fun <T> UpdatableFieldState<T?, MessageFieldDeclaration.Update<T>>.enqueue(message: T) {
        update(MessageFieldDeclaration.Update.Enqueue(message))
    }

    fun <T> UpdatableFieldState<T?, MessageFieldDeclaration.Update<T>>.dequeue() {
        update(MessageFieldDeclaration.Update.Dequeue)
    }
}
