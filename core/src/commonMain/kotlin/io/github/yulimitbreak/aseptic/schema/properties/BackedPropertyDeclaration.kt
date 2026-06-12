package io.github.yulimitbreak.aseptic.schema.properties

import io.github.yulimitbreak.aseptic.schema.fields.Derived1FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.MutableValueFieldDeclaration

/**
 * Declaration of a composite field with separate internal model and UI-facing representations.
 *
 * Decomposes into two runtime fields: [model] (a [io.github.yulimitbreak.aseptic.schema.fields.MutableValueFieldDeclaration]) holds the
 * internal value written by operations, and [ui] (a [io.github.yulimitbreak.aseptic.schema.fields.Derived1FieldDeclaration]) exposes a
 * transformed view of it to the UI. Useful when the data structure used internally differs
 * from what the UI should observe (e.g. storing a list internally, exposing a sorted view).
 *
 * @param M the type of the internal model value.
 * @param U the type of the UI-facing derived value.
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.backed
 */
class BackedPropertyDeclaration<M, U> internal constructor(initial: M, mapper: (M) -> U) {
    /** The mutable model field written to by operations. */
    val model = MutableValueFieldDeclaration(initial)

    /** The read-only UI field derived from [model] via the mapper. */
    val ui = Derived1FieldDeclaration(model, mapper)
}