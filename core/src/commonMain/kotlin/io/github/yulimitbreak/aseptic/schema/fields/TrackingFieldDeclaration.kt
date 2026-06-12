@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.github.yulimitbreak.aseptic.state.UpdatableFieldState

/**
 * A [TrackableFieldDeclaration] that automatically receives updates from a source field.
 *
 * Wraps an [original] field and registers a callback on a source field so that every time
 * the source is written, it updates the wrapped field.
 *
 * @param T the type of the field value.
 * @param TrackedUpdate the value propagated to tracking fields after each write; inherited from the wrapped field.
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.tracking
 */
class TrackingFieldDeclaration<out T, Update, out TrackedUpdate> internal constructor(
    private val original: UpdatableFieldDeclaration<T, Update, TrackedUpdate>,
    private val link: Link<*, Update>
) : TrackableFieldDeclaration<T, Nothing, TrackedUpdate>() {

    override fun convert(
        key: FieldKey,
        fields: StateContainerBuilder.FieldMap,
    ): UpdatableFieldState<T, Nothing, TrackedUpdate> {
        val state = original.convertForTracking(key, fields)
        link.registerUpdate(fields, state)
        return state
    }

    @Suppress("UNCHECKED_CAST")
    internal class Link<SourceUpdate, FieldUpdate>(
        val source: TrackableFieldDeclaration<*, *, SourceUpdate>,
        val mapper: (SourceUpdate) -> FieldUpdate
    ) {
        fun registerUpdate(
            fields: StateContainerBuilder.FieldMap,
            target: UpdatableFieldState<*, FieldUpdate, *>
        ) {
            check(!target.isLockable) { "Inner field should not be lockable" }
            val sourceState = fields[source] as UpdatableFieldState<*, *, SourceUpdate>
            sourceState.addUpdateCallback { output ->
                target.update(mapper(output))
            }
        }
    }
}
