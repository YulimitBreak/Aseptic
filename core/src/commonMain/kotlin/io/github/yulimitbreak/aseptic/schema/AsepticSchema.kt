package io.github.yulimitbreak.aseptic.schema

import io.github.yulimitbreak.aseptic.schema.fields.BackedFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.Derived1FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.Derived2FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.Derived3FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.DerivedNFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.LinkableFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.LinkedFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.MessageFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.MutableValueFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.ReducedFieldDeclaration

/**
 * Base class for Aseptic schema definitions.
 *
 * A schema declares the fields that make up a piece of state. Each field is a property
 * created via one of the protected factory methods below. The Aseptic KSP processor reads
 * the schema at compile time and generates a state handle (for operations to write state)
 * and a UI model class (for the UI to observe it).
 *
 * Schema instances are created once by the generated code to build the runtime state container,
 * then discarded — they carry no mutable state themselves.
 *
 * ## Constructor parameters
 * Constructor parameters are forwarded to the generated state manager constructor, allowing
 * external data or dependencies (e.g. mappers) to be injected into the state.
 *
 * ## Annotations
 * Fields must be annotated with [@Model][io.github.yulimitbreak.aseptic.Model]
 * to be accessible inside operations, and/or [@Ui][io.github.yulimitbreak.aseptic.Ui]
 * to be exposed in the generated UI model class. A field with neither annotation is internal
 * to the schema and can still be referenced by other field declarations.
 *
 * Non-field `val` members (helper values, constants) can also be declared and used in field
 * definitions. These can carry `@Model` or `@Ui` as well.
 *
 * ```kotlin
 * class CounterSchema(private val step: Int) : AsepticSchema() {
 *     @Model @Ui val count = mutable(0)
 *     @Ui val doubled = derived(count) { it * 2 }
 *     val countPlusStep = derived(count) { it + step } // unannotated — used internally only
 * }
 * ```
 */

abstract class AsepticSchema {

    /**
     * Declares a mutable field with [initial] as its starting value.
     *
     * The generated state handle exposes a typed setter for this field.
     */
    protected fun <T> mutable(initial: T): MutableValueFieldDeclaration<T> = MutableValueFieldDeclaration(initial)

    /**
     * Declares a read-only field computed from one source field.
     *
     * Recomputes via [mapper] whenever [source1] changes. Has no setter.
     */
    protected fun <T1, R> derived(
        source1: FieldDeclaration<T1>,
        mapper: (T1) -> R,
    ): Derived1FieldDeclaration<T1, R> = Derived1FieldDeclaration(source1, mapper)

    /**
     * Declares a read-only field computed from two source fields.
     *
     * Recomputes via [mapper] whenever either source changes, combining their latest values.
     */
    protected fun <T1, T2, R> derived(
        source1: FieldDeclaration<T1>,
        source2: FieldDeclaration<T2>,
        mapper: (T1, T2) -> R,
    ): Derived2FieldDeclaration<T1, T2, R> = Derived2FieldDeclaration(source1, source2, mapper)

    /**
     * Declares a read-only field computed from three source fields.
     *
     * Recomputes via [mapper] whenever any source changes, combining their latest values.
     */
    protected fun <T1, T2, T3, R> derived(
        source1: FieldDeclaration<T1>,
        source2: FieldDeclaration<T2>,
        source3: FieldDeclaration<T3>,
        mapper: (T1, T2, T3) -> R,
    ): Derived3FieldDeclaration<T1, T2, T3, R> = Derived3FieldDeclaration(source1, source2, source3, mapper)

    /**
     * Declares a read-only field computed from four or more source fields.
     *
     * Recomputes via [mapper] whenever any source changes, passing all current source values as a list.
     * For distinct-typed sources with up to three inputs prefer the typed overloads above.
     */
    protected fun <T, R> derived(
        source1: FieldDeclaration<T>,
        source2: FieldDeclaration<T>,
        source3: FieldDeclaration<T>,
        source4: FieldDeclaration<T>,
        vararg moreSources: FieldDeclaration<T>,
        mapper: (List<T>) -> R,
    ): DerivedNFieldDeclaration<T, R> =
        DerivedNFieldDeclaration(listOf(source1, source2, source3, source4) + moreSources, mapper)

    /**
     * Declares a field updated by folding incoming update messages into its current value.
     *
     * Unlike [mutable] where operations write a value directly, this field accepts *update messages*
     * of type [U] and applies [update] to produce the next value. Useful for append-only or
     * event-driven state (e.g. lists, counters).
     */
    protected fun <T, U> reduced(
        initial: T,
        update: (old: T, update: U) -> T,
    ): ReducedFieldDeclaration<T, U> = ReducedFieldDeclaration(initial, update)

    /**
     * Declares a one-way message field for fire-and-forget events sent from state to UI.
     *
     * Backed at runtime by a queue — operations enqueue messages under a mutex, the UI dequeues
     * and consumes them. No message is lost even if the UI is not currently collecting.
     *
     * **Note:** `@Model` and `@Ui` annotations are not applicable to message fields.
     * Message fields are always accessible from operations via the model (no `@Model` needed).
     * The UI accesses them through a dedicated consumption API, not via standard `@Ui` flow
     * observation — annotating with `@Ui` has no effect.
     *
     * TODO update KDoc when we finalize a way to access them properly once processor is done
     */
    protected fun <T : Any> message(): MessageFieldDeclaration<T> = MessageFieldDeclaration()

    /**
     * Declares a composite field with separate internal and UI-facing representations.
     *
     * Shorthand for declaring a `@Model`-annotated [mutable] field paired with a `@Ui`-annotated
     * [derived] field with the same name that transforms it. Operations write the internal model value of type [M];
     * the UI observes the transformed view of type [U] produced by [mapper].
     *
     * **Must be annotated with both `@Model` and `@Ui`** — `@Model` wires the setter for [M],
     * `@Ui` wires the observable for [U].
     */
    protected fun <M, U> backed(initial: M, mapper: (M) -> U): BackedFieldDeclaration<M, U> =
        BackedFieldDeclaration(initial, mapper)

    /**
     * Wires this field to automatically receive updates from [source].
     *
     * Whenever [source] is written, [updateMapper] is called with the source's output value and
     * the result is applied as a write to this field.
     *
     * @param source the field whose writes trigger updates on this field.
     * @param updateMapper converts the source's update to an update for this field.
     */
    @Suppress("MaximumLineLength")
    protected fun <T, SourceUpdate, Update, LinkableUpdate> LinkableFieldDeclaration<T, Update, LinkableUpdate>.linkedTo(
        source: LinkableFieldDeclaration<*, *, SourceUpdate>,
        updateMapper: (SourceUpdate) -> Update,
    ) = LinkedFieldDeclaration(
        this,
        link = LinkedFieldDeclaration.Link(
            source,
            updateMapper
        )
    )

    /**
     * Wires this field to automatically receive updates from [source], using the source output
     * directly as the update message (no mapping needed). Requires that the source's output type
     * matches this field's update type.
     *
     * @param source the field whose writes trigger updates on this field.
     */
    protected fun <T, SourceUpdate, LinkableUpdate> LinkableFieldDeclaration<T, SourceUpdate, LinkableUpdate>.linkedTo(
        source: LinkableFieldDeclaration<*, *, SourceUpdate>
    ) = LinkedFieldDeclaration(
        this,
        link = LinkedFieldDeclaration.Link(source) { it }
    )

    /**
     * Wires this [MutableValueFieldDeclaration] to automatically update its value whenever
     * [source] is written.
     *
     * [updateMapper] receives the current field value and the source's output, and returns the
     * new field value. This is a convenience overload that wraps the result in the transform
     * `(T) -> T` expected by [MutableValueFieldDeclaration].
     *
     * @param source the field whose writes trigger updates on this field.
     * @param updateMapper converts the current value and the source's update to an update for this field.
     */
    protected inline fun <T, SourceUpdate> MutableValueFieldDeclaration<T>.linkedTo(
        source: LinkableFieldDeclaration<*, *, SourceUpdate>,
        crossinline updateMapper: (previous: T, update: SourceUpdate) -> T,
    ) = this.linkedTo(source) { update ->
        {
            updateMapper(it, update)
        }
    }
}
