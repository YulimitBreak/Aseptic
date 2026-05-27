package io.github.yulimitbreak.aseptic.schema.fields

class ReducedFieldDeclaration<T, U> internal constructor(
    internal val initial: T,
    internal val update: (old: T, update: U) -> T,
) : FieldDeclaration<T>
