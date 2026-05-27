package io.github.yulimitbreak.aseptic.schema.fields

class DerivedDeltaFieldDeclaration<T, R>(
    val source: FieldDeclaration<T>,
    val initial: R,
    val mapper: (oldSource: T, newSource: T, oldResult: R) -> R,
) : FieldDeclaration<R>
