package io.github.yulimitbreak.aseptic.schema.fields

/**
 * Declaration of a read-only field derived from a source using delta (old→new) logic.
 *
 * Unlike [Derived1FieldDeclaration], [mapper] receives both the *previous* and *new* source value
 * along with the *previous result*, enabling incremental computation (e.g. accumulating diffs,
 * tracking transitions). [initial] is the result value before the first source emission.
 *
 * @param T the type of the source field value.
 * @param R the type of the derived field value.
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.derivedDelta
 */
class DerivedDeltaFieldDeclaration<T, R> internal constructor(
    internal val source: FieldDeclaration<T>,
    /** The value of the derived field before the first source emission. */
    internal val initial: R,
    internal val mapper: (oldSource: T, newSource: T, oldResult: R) -> R,
) : FieldDeclaration<R>
