@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.context.fields

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.state.StateContainer

/**
 * Accessor to readable and updatable fields -
 * [mutable value][io.github.yulimitbreak.aseptic.schema.AsepticSchema.mutable]
 * and [reduced][io.github.yulimitbreak.aseptic.schema.AsepticSchema.reduced]
 */
open class UpdatableField<T, U> @AsepticInternal constructor(
    key: FieldKey,
    container: StateContainer,
) : ReadableField<T>(key, container) {

    /**
     * Apply the update to the field
     */
    suspend fun update(update: U) {
        container.update(key, update)
    }
}

/**
 * A convenience method just for [mutable value][io.github.yulimitbreak.aseptic.schema.AsepticSchema.mutable] fields,
 * that sets the value ignoring the previous one
 */
suspend fun <T> UpdatableField<T, (T) -> T>.set(value: T) {
    update(update = { _: T -> value })
}
