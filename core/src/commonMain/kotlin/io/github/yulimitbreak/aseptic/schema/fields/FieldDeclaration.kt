@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.FieldState
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder

/**
 * Marker interface for all field declarations in an [io.github.yulimitbreak.aseptic.schema.AsepticSchema].
 *
 * A field declaration is a pure, stateless descriptor created at schema definition time.
 * It carries the information the runtime needs to construct the corresponding [FieldState][io.github.yulimitbreak.aseptic.state.FieldState],
 * but holds no mutable state itself.
 *
 * @param T the type of the field value
 */
abstract class FieldDeclaration<out T> internal constructor() {
    internal abstract fun convert(
        fields: StateContainerBuilder.FieldMap,
    ): FieldState<T>
}
