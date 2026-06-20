@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.state.FieldState
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.github.yulimitbreak.aseptic.state.UpdatableFieldState

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
        key: FieldKey,
        fields: StateContainerBuilder.FieldMap,
    ): FieldState<T>
}

/**
 * A [FieldDeclaration] that produces an [UpdatableFieldState], and can be a source for tracking fields
 * via [tracking][io.github.yulimitbreak.aseptic.schema.AsepticSchema.tracking]
 *
 * @param T the type of the field value.
 * @param Update the type of the write message.
 * @param TrackedUpdate the value propagated to tracking fields after each write.
 */
abstract class TrackableFieldDeclaration<out T, in Update, out TrackedUpdate> internal constructor() :
    FieldDeclaration<T>() {

    abstract override fun convert(
        key: FieldKey,
        fields: StateContainerBuilder.FieldMap,
    ): UpdatableFieldState<T, Update, TrackedUpdate>
}

/**
 * A [FieldDeclaration] that can be updated manually.
 * It is also a [TrackableFieldDeclaration], so other fields can track it
 * with [tracking][io.github.yulimitbreak.aseptic.schema.AsepticSchema.tracking], and
 * can also receive tracking updates from other trackable fields
 *
 * @param T the type of the field value.
 * @param Update the type of the write message.
 * @param TrackedUpdate the value propagated to tracking fields after each write.
 */
abstract class UpdatableFieldDeclaration<out T, in Update, out TrackedUpdate> internal constructor() :
    TrackableFieldDeclaration<T, Update, TrackedUpdate>() {

    internal abstract fun convertForTracking(
        key: FieldKey,
        fields: StateContainerBuilder.FieldMap,
        source: FieldState<*>,
    ): UpdatableFieldState<T, Update, TrackedUpdate>
}
