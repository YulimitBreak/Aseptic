package io.github.yulimitbreak.aseptic.schema.fields

class BackedFieldDeclaration<T, R> internal constructor(initial: T, mapper: (T) -> R) {
    val model = MutableValueFieldDeclaration(initial)
    val ui = Derived1FieldDeclaration(model, mapper)
}
