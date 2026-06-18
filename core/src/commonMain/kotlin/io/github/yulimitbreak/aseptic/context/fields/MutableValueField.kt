@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.context.fields

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.state.StateContainer

class MutableValueField<T> @AsepticInternal constructor(
    key: FieldKey,
    container: StateContainer
) : UpdatableField<T, (T) -> T>(key, container) {

    suspend fun set(value: T) {
        container.update(key, update = { _: T -> value })
    }
}
