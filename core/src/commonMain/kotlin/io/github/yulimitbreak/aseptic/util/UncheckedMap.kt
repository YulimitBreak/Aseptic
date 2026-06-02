package io.github.yulimitbreak.aseptic.util

import io.github.yulimitbreak.aseptic.AsepticInternal

@AsepticInternal
interface UncheckedMap<in Key> {
    operator fun <T> get(key: Key): T
}

@AsepticInternal
@Suppress("UNCHECKED_CAST")
internal class UncheckedMapWrapper<Key>(private val source: Map<Key, Any?>) : UncheckedMap<Key> {
    override fun <T> get(key: Key): T = source[key] as T
}
