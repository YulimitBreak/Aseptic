@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.context

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.util.UncheckedMap
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Base class for the receiver used inside [XxxxContext.atomic][BaseAsepticContext.atomic],
 * providing delegates for accessing and updating data
 */
@AsepticInternal
abstract class BaseAtomicScope protected constructor(
    private val source: UncheckedMap<FieldKey>,
    internal val updateBuilder: AtomicUpdateBuilder = AtomicUpdateBuilder()
) {

    protected fun <T> readOnlyValue(key: FieldKey): T = source[key]

    protected fun <Lens> readOnlyLensValue(lensGenerator: (UncheckedMap<FieldKey>) -> Lens) = lensGenerator(source)
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

    inner class ReducedFieldAccessor<T, U>(private val key: FieldKey) {
        /**
         * Value that the reduced field had when snapshot was taken.
         * Does not update inside atomic scope
         */
        val previous: T = source[key]

        /**
         * Enqueue an update for the reduced field. Multiple calls enqueue
         * all the updates, which will be applied at once after atomic
         * section completes
         */
        fun enqueue(update: U) {
            this@BaseAtomicScope.updateBuilder.enqueueUpdate(key, update)
        }
    }

    protected fun <LensScope : BaseAtomicScope> mutableLensProperty(
        generator: (UncheckedMap<FieldKey>, AtomicUpdateBuilder) -> LensScope
    ) = generator(this.source, this.updateBuilder)
}
