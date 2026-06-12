package io.github.yulimitbreak.aseptic.schema

import io.github.yulimitbreak.aseptic.schema.fields.BackedFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.Derived1FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.Derived2FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.Derived3FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.DerivedNFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.MessageFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.MutableValueFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.ReducedFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.TrackableFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.TrackingCapableFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.TrackingFieldDeclaration

/**
 * Base class for Aseptic schema definitions.
 *
 * A schema declares the fields that make up a piece of state. Each field is a property
 * created via one of the protected factory methods below. The Aseptic KSP processor reads
 * the schema at compile time and generates a state handle (for operations to write state)
 * and a UI model class (for the UI to observe it).
 *
 * Schema instances are created once by the generated code to build the runtime state container,
 * then discarded - they carry no mutable state themselves.
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
 *     // Accessible inside operations and exposed to UI model
 *     @Model @Ui val count = mutable(0)
 *
 *     // Only exposed to UI model
 *     @Ui val doubled = derived(count) { it * 2 }
 *
 *     // unannotated - used internally only
 *     val countPlusStep = derived(count) { it + step }
 * }
 * ```
 */

abstract class AsepticSchema {

    /**
     * Declares a mutable field with [initial] as its starting value.
     *
     * The generated state handle exposes a typed setter for this field.
     *
     * ```kotlin
     * @Model
     * val isLoading = mutable(false)
     * ```
     */
    protected fun <T> mutable(initial: T): MutableValueFieldDeclaration<T> = MutableValueFieldDeclaration(initial)

    /**
     * Declares a read-only field computed from the value of one field.
     * Last computation is cached. Computation shouldn't have side effects and must depend only on
     * the source values or static values in the schema.
     *
     * ```kotlin
     *
     * @Model
     * val password = mutable("")
     *
     * @Ui(named="password")
     * val passwordText = derived(password) { "*".repeat(it.length) }
     * ```
     */
    protected fun <T1, R> derived(
        source1: FieldDeclaration<T1>,
        mapper: (T1) -> R,
    ): Derived1FieldDeclaration<T1, R> = Derived1FieldDeclaration(source1, mapper)

    /**
     * Declares a read-only field computed from two source fields.
     * Last computation is cached. Computation shouldn't have side effects and must depend only on
     * the source values or static values in the schema.
     *
     * ```kotlin
     * @Model
     * @Ui
     * val username = mutable("")
     *
     * @Model
     * val password = mutable("")
     *
     * @Ui
     * val loginButtonEnabled = derived(username, password) { username, password ->
     *      username.isNotBlank() && password.isNotBlank()
     * }
     * ```
     */
    protected fun <T1, T2, R> derived(
        source1: FieldDeclaration<T1>,
        source2: FieldDeclaration<T2>,
        mapper: (T1, T2) -> R,
    ): Derived2FieldDeclaration<T1, T2, R> = Derived2FieldDeclaration(source1, source2, mapper)

    /**
     * Declares a read-only field computed from three source fields.
     * Last computation is cached. Computation shouldn't have side effects and must depend only on
     * the source values or static values in the schema.
     *
     * ```kotlin
     * @Ui
     * val userCard = derived(firstName, lastName, age) { firstName, lastName, age ->
     *      UserCardUi(firstName, lastName, age)
     * }
     * ```
     */
    protected fun <T1, T2, T3, R> derived(
        source1: FieldDeclaration<T1>,
        source2: FieldDeclaration<T2>,
        source3: FieldDeclaration<T3>,
        mapper: (T1, T2, T3) -> R,
    ): Derived3FieldDeclaration<T1, T2, T3, R> = Derived3FieldDeclaration(source1, source2, source3, mapper)

    /**
     * Declares a read-only field computed from four or more source fields.
     * Last computation is cached. Computation shouldn't have side effects and must depend only on
     * the source values or static values in the schema.
     *
     * ```kotlin
     *
     * @Ui
     * val isLoadingShown = derived(userLoading, contentLoading, messagesLoading, bannerLoading) { loading ->
     *      loading.any { it }
     * }
     * ```
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
     * The generated state handle exposes the `update` method that takes an update of type [U], and uses
     * [update] to apply it to a current state (starting with [initial]) to produce a new value
     *
     * ```kotlin
     *
     * @Model
     * val messages = reduced<List<Message>, MessageUpdate>(emptyList()) { current, update ->
     *     when (update) {
     *          is MessageUpdate.New -> current + update.message
     *       // ...
     *     }
     * }
     * ```
     */
    protected fun <T, U> reduced(
        initial: T,
        update: (old: T, update: U) -> T,
    ): ReducedFieldDeclaration<T, U> = ReducedFieldDeclaration(initial, update)

    /**
     * Declares a one-way message field for fire-and-forget events sent from state to UI.
     *
     * Messages are stored as a queue under the hood, and need to be explicitly consumed one
     * by one
     *
     * ** Must be annotated with both `@Model` and `@Ui`** - message fields are always accessible
     * from operations, and they have their own special way of being read on UI
     *
     * Message fields cannot be read in the model, and cannot be depended on by other fields
     *
     * ```kotlin
     *
     * @Model
     * @Ui(named = "navigationEvents")
     * val navigation = message<NavigationEvent>()
     *
     * ```
     *
     * TODO update KDoc when we finalize a way to access them properly once processor is done
     */
    protected fun <T : Any> message(): MessageFieldDeclaration<T> = MessageFieldDeclaration()

    /**
     * Declares a composite field with separate internal and UI-facing representations.
     *
     * Shorthand for declaring a `@Model`-annotated [mutable] field paired with a `@Ui`-annotated
     * [derived] field with the same name that transforms it.
     *
     * **Must be annotated with both `@Model` and `@Ui`**
     *
     * ```kotlin
     *
     *  @Model
     *  @Ui(named="passwordText")
     *  val password = backed("") { "*".repeat(it.length) }
     *  ```
     */
    protected fun <M, U> backed(initial: M, mapper: (M) -> U): BackedFieldDeclaration<M, U> =
        BackedFieldDeclaration(initial, mapper)

    /**
     * Wires this field to automatically receive updates from [source].
     *
     * Whenever [source] is written, [updateMapper] is called with the source's output value and
     * the result is applied as a write to this field.
     *
     * ```kotlin
     * @Model
     * val articles = reduced<List<Article>, List<Article>>(emptyList()) { all, new -> all + new }
     *
     * @Ui
     * val articleCards = reduced<List<ArticleCard>, List<ArticleCard>>(emptyList()) { cards, new -> cards + new }
     *     .tracking(articles) { newArticles -> newArticles.map { it.toCard() } }
     * ```
     */
    @Suppress("MaximumLineLength")
    protected fun <T, SourceUpdate, Update, TrackedUpdate> TrackingCapableFieldDeclaration<T, Update, TrackedUpdate>.tracking(
        source: TrackableFieldDeclaration<*, *, SourceUpdate>,
        updateMapper: (SourceUpdate) -> Update,
    ) = TrackingFieldDeclaration(
        this,
        link = TrackingFieldDeclaration.Link(
            source,
            updateMapper
        )
    )

    /**
     * Wires this field to automatically receive updates from [source], using the source output
     * directly as the update message (no mapping needed). Requires that the source's output type
     * matches this field's update type.
     *
     * ```kotlin
     * @Model
     * val score = reduced<Int, Int>(0) { total, delta -> total + delta }
     *
     * @Model
     * val scoreHistory = reduced<List<Int>, Int>(emptyList()) { history, delta -> history + delta }
     *     .tracking(score)
     * ```
     */
    @Suppress("MaximumLineLength")
    protected fun <T, SourceUpdate, TrackedUpdate> TrackingCapableFieldDeclaration<T, SourceUpdate, TrackedUpdate>.tracking(
        source: TrackableFieldDeclaration<*, *, SourceUpdate>
    ) = TrackingFieldDeclaration(
        this,
        link = TrackingFieldDeclaration.Link(source) { it }
    )

    /**
     * Wires this [MutableValueFieldDeclaration] to automatically update its value whenever
     * [source] is written.
     *
     * [updateMapper] receives the current field value and the source's output, and returns the
     * new field value. This is a convenience overload that wraps the result in the transform
     * `(T) -> T` expected by [MutableValueFieldDeclaration].
     *
     * ```kotlin
     * @Model
     * val cartItems = reduced<List<Item>, Item>(emptyList()) { items, added -> items + added }
     *
     * @Model
     * val cartTotal = mutable(0.0)
     *     .tracking(cartItems) { currentTotal, addedItem -> currentTotal + addedItem.price }
     * ```
     */
    protected inline fun <T, SourceUpdate> MutableValueFieldDeclaration<T>.tracking(
        source: TrackableFieldDeclaration<*, *, SourceUpdate>,
        crossinline updateMapper: (previous: T, update: SourceUpdate) -> T,
    ) = this.tracking(source) { update ->
        {
            updateMapper(it, update)
        }
    }
}
