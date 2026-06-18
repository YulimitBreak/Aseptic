@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.handle.fields

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.state.StateContainer

open class ReadableField<T> @AsepticInternal constructor(
    internal val key: FieldKey,
    protected val container: StateContainer
) : FieldLockProperty {

    override val keys: Set<FieldKey> = setOf(key)

    operator fun invoke(): T = container[key]

    fun asFlow() = container.asFlow<T>(key)
}
