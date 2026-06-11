@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.FieldState
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.github.yulimitbreak.aseptic.state.UpdatableFieldState
import kotlinx.coroutines.CoroutineScope

/**
 * Base class for all field declarations in an [io.github.yulimitbreak.aseptic.schema.AsepticSchema].
 *
 * A field declaration is a pure, stateless descriptor created at schema definition time.
 * It carries the information the runtime needs to construct the corresponding
 * [FieldState][io.github.yulimitbreak.aseptic.state.FieldState],
 * but holds no mutable state itself.
 *
 * @param T the type of the field value
 */
abstract class FieldDeclaration<out T> internal constructor() {
    internal abstract fun convert(
        name: String,
        fields: StateContainerBuilder.FieldMap,
        coroutineScope: CoroutineScope
    ): FieldState<T>
}

/**
 * A [FieldDeclaration] that produces an [UpdatableFieldState], and can be linked to other fields via
 * [linkedTo][io.github.yulimitbreak.aseptic.schema.AsepticSchema.linkedTo] and be a target of linking by other fields.
 *
 * @param T the type of the field value.
 * @param Update the type of the write message.
 * @param LinkableUpdate the value propagated to linked fields after each write.
 */
abstract class LinkableFieldDeclaration<out T, in Update, out LinkableUpdate> internal constructor() :
    FieldDeclaration<T>() {

    abstract override fun convert(
        name: String,
        fields: StateContainerBuilder.FieldMap,
        coroutineScope: CoroutineScope
    ): UpdatableFieldState<T, Update, LinkableUpdate>
}
