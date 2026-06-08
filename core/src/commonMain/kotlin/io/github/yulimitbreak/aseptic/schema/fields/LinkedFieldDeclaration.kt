@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.github.yulimitbreak.aseptic.state.UpdatableFieldState
import kotlinx.coroutines.CoroutineScope

class LinkedFieldDeclaration<out T, Update, out LinkableUpdate> internal constructor(
    private val original: LinkableFieldDeclaration<T, Update, LinkableUpdate>,
    private val link: Link<*, Update>
) : LinkableFieldDeclaration<T, Nothing, LinkableUpdate>() {
    // Using Nothing for update, in order to prevent field to be linked to multiple sources or updated manually

    override fun convert(
        fields: StateContainerBuilder.FieldMap,
        coroutineScope: CoroutineScope
    ): UpdatableFieldState<T, Update, LinkableUpdate> {
        val state = original.convert(fields, coroutineScope)
        link.registerUpdate(
            fields,
            state
        )
        return state
    }

    @Suppress("UNCHECKED_CAST")
    internal class Link<SourceUpdate, FieldUpdate>(
        val source: LinkableFieldDeclaration<*, *, SourceUpdate>,
        val mapper: (SourceUpdate) -> FieldUpdate
    ) {
        fun registerUpdate(
            fields: StateContainerBuilder.FieldMap,
            target: UpdatableFieldState<*, FieldUpdate, *>
        ) {
            val sourceState = fields[source] as UpdatableFieldState<*, *, SourceUpdate>
            sourceState.addUpdateCallback { output ->
                with(target) {
                    check(tryLock()) { "Field was accessed outside of linking context" }
                    // field should never be updated manually, so it shouldn't happen
                    try {
                        update(mapper(output))
                    } finally {
                        unlock()
                    }
                }
            }
        }
    }
}
