package io.github.yulimitbreak.aseptic.schema

import io.github.yulimitbreak.aseptic.schema.fields.BackedFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.Derived1FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.Derived2FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.Derived3FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.DerivedDeltaFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.DerivedNFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.MessageFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.MutableValueFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.ReducedFieldDeclaration

abstract class AsepticSchema {

    protected fun <T> mutable(initial: T): MutableValueFieldDeclaration<T> =
        MutableValueFieldDeclaration(initial)

    protected fun <T1, R> derived(
        source1: FieldDeclaration<T1>,
        mapper: (T1) -> R,
    ): Derived1FieldDeclaration<T1, R> =
        Derived1FieldDeclaration(source1, mapper)

    protected fun <T1, T2, R> derived(
        source1: FieldDeclaration<T1>,
        source2: FieldDeclaration<T2>,
        mapper: (T1, T2) -> R,
    ): Derived2FieldDeclaration<T1, T2, R> =
        Derived2FieldDeclaration(source1, source2, mapper)

    protected fun <T1, T2, T3, R> derived(
        source1: FieldDeclaration<T1>,
        source2: FieldDeclaration<T2>,
        source3: FieldDeclaration<T3>,
        mapper: (T1, T2, T3) -> R,
    ): Derived3FieldDeclaration<T1, T2, T3, R> =
        Derived3FieldDeclaration(source1, source2, source3, mapper)

    protected fun <T, R> derived(
        source1: FieldDeclaration<T>,
        source2: FieldDeclaration<T>,
        source3: FieldDeclaration<T>,
        source4: FieldDeclaration<T>,
        vararg moreSources: FieldDeclaration<T>,
        mapper: (List<T>) -> R,
    ): DerivedNFieldDeclaration<T, R> =
        DerivedNFieldDeclaration(listOf(source1, source2, source3, source4) + moreSources, mapper)

    protected fun <T, R> derivedDelta(
        source: FieldDeclaration<T>,
        initial: R,
        mapper: (oldSource: T, newSource: T, oldResult: R) -> R,
    ): DerivedDeltaFieldDeclaration<T, R> =
        DerivedDeltaFieldDeclaration(source, initial, mapper)

    protected fun <T, U> reduced(
        initial: T,
        update: (old: T, update: U) -> T,
    ): ReducedFieldDeclaration<T, U> =
        ReducedFieldDeclaration(initial, update)

    protected fun <T> message(): MessageFieldDeclaration<T> =
        MessageFieldDeclaration()

    protected fun <M, U> backed(initial: M, mapper: (M) -> U): BackedFieldDeclaration<M, U> =
        BackedFieldDeclaration(initial, mapper)
}
