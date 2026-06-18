@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.handle

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.util.UncheckedMap
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@AsepticInternal
abstract class BaseAtomicScope protected constructor(
    private val source: UncheckedMap<FieldKey>,
    internal val updateBuilder: AtomicUpdateBuilder = AtomicUpdateBuilder()
) {

    protected fun <T> readOnlyFieldDelegate(key: FieldKey) = ReadOnlyProperty<BaseAtomicScope, T> { _, _ ->
        source[key]
    }

    protected fun <T> mutableFieldDelegate(key: FieldKey) = object : ReadWriteProperty<BaseAtomicScope, T> {

        @Suppress("UNCHECKED_CAST")
        override fun getValue(
            thisRef: BaseAtomicScope,
            property: KProperty<*>
        ): T = updateBuilder.getMutable(key, source[key])

        override fun setValue(
            thisRef: BaseAtomicScope,
            property: KProperty<*>,
            value: T
        ) {
            updateBuilder.setMutable(key, value)
        }
    }

    protected inner class ReducedFieldHandle<T, U>(private val key: FieldKey) {
        val previous: T = source[key]

        fun enqueue(update: U) {
            this@BaseAtomicScope.updateBuilder.enqueueUpdate(key, update)
        }
    }

    protected inner class MessageFieldHandle<T : Any>(private val key: FieldKey) {
        fun enqueue(message: T) {
            this@BaseAtomicScope.updateBuilder.enqueueMessage(key, message)
        }
    }

    protected fun <LensScope : BaseAtomicScope> lensProperty(
        generator: (UncheckedMap<FieldKey>, AtomicUpdateBuilder) -> LensScope
    ) = generator(this.source, this.updateBuilder)
}
