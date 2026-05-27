package io.github.yulimitbreak.aseptic.schema.fields

interface DerivedFieldDeclaration<R> : FieldDeclaration<R> {

    class Derived1<T1, R>(
        val source1: FieldDeclaration<T1>,
        val mapper: (T1) -> R,
    ) : DerivedFieldDeclaration<R>

    class Derived2<T1, T2, R>(
        val source1: FieldDeclaration<T1>,
        val source2: FieldDeclaration<T2>,
        val mapper: (T1, T2) -> R,
    ) : DerivedFieldDeclaration<R>

    class Derived3<T1, T2, T3, R>(
        val source1: FieldDeclaration<T1>,
        val source2: FieldDeclaration<T2>,
        val source3: FieldDeclaration<T3>,
        val mapper: (T1, T2, T3) -> R,
    ) : DerivedFieldDeclaration<R>

    class DerivedN<T, R>(
        val sources: List<FieldDeclaration<T>>,
        val mapper: (List<T>) -> R,
    ) : DerivedFieldDeclaration<R>
}
