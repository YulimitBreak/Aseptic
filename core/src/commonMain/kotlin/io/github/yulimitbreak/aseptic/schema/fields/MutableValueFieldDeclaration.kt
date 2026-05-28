package io.github.yulimitbreak.aseptic.schema.fields

/**
 * Declaration of a mutable field whose value is set directly by operations.
 *
 * At runtime backed by a `MutableStateFlow` initialised with [initial].
 * The generated state handle exposes a typed setter. All writes are serialized under a mutex.
 *
 * @param T the type of the field value.
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.mutable
 */
class MutableValueFieldDeclaration<T> internal constructor(
    /** The value the field holds before any update is applied. */
    internal val initial: T,
) : FieldDeclaration<T>
