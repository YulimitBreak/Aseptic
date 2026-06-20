package io.github.yulimitbreak.aseptic.util

import io.github.yulimitbreak.aseptic.AsepticInternal

/**
 * A map whose values are retrieved without a statically known value type - it unsafely
 * casts data to the required parameter under the hood. Only used in generated code, in order to
 * guarantee safe use
 */
@AsepticInternal
interface UncheckedMap<Key> {
    operator fun <T> get(key: Key): T
}

/**
 * [UncheckedMap] backed by a plain [Map] of [Any?] values.
 */
@AsepticInternal
@Suppress("UNCHECKED_CAST")
internal class UncheckedMapWrapper<Key>(private val source: Map<Key, Any?>) : UncheckedMap<Key> {
    override fun <T> get(key: Key): T = source.getValue(key) as T
}
