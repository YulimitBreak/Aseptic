@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.FieldState
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.github.yulimitbreak.aseptic.state.UpdatableFieldState

internal object FieldTestUtils {

    @Suppress("UNCHECKED_CAST")
    fun fieldMapWith(
        vararg entries: Pair<FieldDeclaration<*>, FieldState<*>>,
    ): StateContainerBuilder.FieldMap = StateContainerBuilder.FieldMap().also { map ->
        entries.forEach { (decl, state) ->
            map[decl] = state
        }
    }

    fun fieldMapWith(
        decls: List<FieldDeclaration<*>>,
        states: List<FieldState<*>>,
    ): StateContainerBuilder.FieldMap = fieldMapWith(*decls.zip(states).toTypedArray())

    fun <T, U, LU> UpdatableFieldState<T, U, LU>.withTryLock(block: UpdatableFieldState<T, U, LU>.() -> Unit) {
        check(tryLock()) { "withTryLock() on a locked field" }
        try { block() } finally { unlock() }
    }

    fun <T : Any> UpdatableFieldState<T?, MessageFieldDeclaration.Update<T>, Unit>.enqueue(message: T) {
        update(MessageFieldDeclaration.Update.Enqueue(message))
    }

    fun <T : Any> UpdatableFieldState<T?, MessageFieldDeclaration.Update<T>, Unit>.dequeue() {
        update(MessageFieldDeclaration.Update.Dequeue)
    }
}
