package io.github.yulimitbreak.aseptic.handle

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.schema.fields.MessageFieldDeclaration
import io.github.yulimitbreak.aseptic.state.AtomicUpdate
import io.github.yulimitbreak.aseptic.state.FieldKey
import kotlin.collections.mutableListOf

@AsepticInternal
class AtomicUpdateBuilder {

    private val mutableValues = mutableMapOf<FieldKey, MutableValue<*>>()
    private val updates = mutableMapOf<FieldKey, MutableList<Any?>>()

    @Suppress("UNCHECKED_CAST")
    internal fun <T> getMutable(key: FieldKey, default: T) = if (key in mutableValues) {
        mutableValues.getValue(key).value as T
    } else {
        default
    }

    internal fun <T> setMutable(key: FieldKey, value: T) {
        mutableValues[key] = MutableValue(value)
    }

    internal fun <U> enqueueUpdate(key: FieldKey, update: U) {
        updates.getOrPut(key, { mutableListOf() }).add(update)
    }

    internal fun <T : Any> enqueueMessage(key: FieldKey, message: T) {
        enqueueUpdate(key, MessageFieldDeclaration.Update.Enqueue(message))
    }

    fun build(): AtomicUpdate = mutableValues.mapValues { (_, v) -> listOf(v.mapper) } + updates

    private data class MutableValue<T>(val value: T) {
        val mapper: (T) -> T get() = { _ -> value }
    }
}
