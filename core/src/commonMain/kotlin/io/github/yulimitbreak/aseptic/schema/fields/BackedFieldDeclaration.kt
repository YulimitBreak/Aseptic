package io.github.yulimitbreak.aseptic.schema.fields

class BackedFieldDeclaration<T, R>(initial: T, mapper: (T) -> R) {

    val model = MutableValueFieldDeclaration(initial)
    val ui = DerivedFieldDeclaration.Derived1(model, mapper)
}