@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.FieldState
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.github.yulimitbreak.aseptic.state.UpdatableFieldState
import kotlinx.coroutines.CoroutineScope

/**
 * Marker interface for all field declarations in an [io.github.yulimitbreak.aseptic.schema.AsepticSchema].
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
        fields: StateContainerBuilder.FieldMap,
        coroutineScope: CoroutineScope
    ): FieldState<T>
}

/**
 * A [FieldDeclaration] that produces an [UpdatableFieldState] — a field that accepts writes
 * and can serve as a source for [LinkedFieldDeclaration].
 *
 * The [Update] type is the write message accepted by the field. [LinkableUpdate] is the value
 * emitted to linked fields after each write; it is the bridge between this field's update
 * output and the update input of any field chained via
 * [linkedTo][io.github.yulimitbreak.aseptic.schema.AsepticSchema.linkedTo].
 *
 * @param T the type of the field value.
 * @param Update the type of the write message.
 * @param LinkableUpdate the value propagated to linked fields after each write.
 */
abstract class LinkableFieldDeclaration<out T, in Update, out LinkableUpdate> internal constructor() :
    FieldDeclaration<T>() {

    abstract override fun convert(
        fields: StateContainerBuilder.FieldMap,
        coroutineScope: CoroutineScope
    ): UpdatableFieldState<T, Update, LinkableUpdate>
}
