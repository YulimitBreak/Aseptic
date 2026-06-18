@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.context.fields

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.context.FieldLockProperty
import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.state.StateContainer

/**
 * An accessor to a field declared in schema
 */
open class ReadableField<T> @AsepticInternal constructor(
    internal val key: FieldKey,
    protected val container: StateContainer
) : FieldLockProperty {

    override val keys: Set<FieldKey> = setOf(key)

    /**
     * Get the current value of the field
     */
    operator fun invoke(): T = container[key]

    /**
     * Observe the value of this field as a flow
     */
    fun asFlow() = container.asFlow<T>(key)
}
