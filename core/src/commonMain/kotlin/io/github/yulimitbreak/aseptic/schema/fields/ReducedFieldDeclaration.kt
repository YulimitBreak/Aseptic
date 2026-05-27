package io.github.yulimitbreak.aseptic.schema.fields

class ReducedFieldDeclaration<T, U>(
    val initial: T,
    val update: (old: T, update: U) -> T,
) : FieldDeclaration<T>
