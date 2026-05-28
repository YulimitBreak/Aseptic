package io.github.yulimitbreak.aseptic.schema.fields

/**
 * Declaration of a read-only field whose value is computed from one source field.
 *
 * At runtime the field recomputes via [mapper] whenever [source1] emits a new value.
 *
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.derived
 */
class Derived1FieldDeclaration<T1, R> internal constructor(
    internal val source1: FieldDeclaration<T1>,
    internal val mapper: (T1) -> R,
) : FieldDeclaration<R>

/**
 * Declaration of a read-only field whose value is computed from two source fields.
 *
 * At runtime the field recomputes via [mapper] whenever either source emits a new value,
 * combining the latest value of each source.
 *
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.derived
 */
class Derived2FieldDeclaration<T1, T2, R> internal constructor(
    internal val source1: FieldDeclaration<T1>,
    internal val source2: FieldDeclaration<T2>,
    internal val mapper: (T1, T2) -> R,
) : FieldDeclaration<R>

/**
 * Declaration of a read-only field whose value is computed from three source fields.
 *
 * At runtime the field recomputes via [mapper] whenever any source emits a new value,
 * combining the latest value of each source.
 *
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.derived
 */
class Derived3FieldDeclaration<T1, T2, T3, R> internal constructor(
    internal val source1: FieldDeclaration<T1>,
    internal val source2: FieldDeclaration<T2>,
    internal val source3: FieldDeclaration<T3>,
    internal val mapper: (T1, T2, T3) -> R,
) : FieldDeclaration<R>

/**
 * Declaration of a read-only field derived from four or more source fields.
 *
 * At runtime the field recomputes via [mapper] whenever any source emits a new value.
 * All current source values are passed as a list.
 *
 * For 1–3 sources with distinct types use [Derived1FieldDeclaration], [Derived2FieldDeclaration],
 * or [Derived3FieldDeclaration] instead.
 *
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.derived
 */
class DerivedNFieldDeclaration<T, R> internal constructor(
    internal val sources: List<FieldDeclaration<T>>,
    internal val mapper: (List<T>) -> R,
) : FieldDeclaration<R>
