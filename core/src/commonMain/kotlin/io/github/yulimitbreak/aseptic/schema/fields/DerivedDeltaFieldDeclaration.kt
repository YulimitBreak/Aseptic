package io.github.yulimitbreak.aseptic.schema.fields

class DerivedDeltaFieldDeclaration<T, R> internal constructor(
    internal val source: FieldDeclaration<T>,
    internal val initial: R,
    internal val mapper: (oldSource: T, newSource: T, oldResult: R) -> R,
) : FieldDeclaration<R>
