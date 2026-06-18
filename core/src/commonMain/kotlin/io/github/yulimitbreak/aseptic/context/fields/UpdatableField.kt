@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.context.fields

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.state.StateContainer

open class UpdatableField<T, U> @AsepticInternal constructor(
    key: FieldKey,
    container: StateContainer
) : ReadableField<T>(key, container) {

    suspend fun update(update: U) {
        container.update(key, update)
    }
}
