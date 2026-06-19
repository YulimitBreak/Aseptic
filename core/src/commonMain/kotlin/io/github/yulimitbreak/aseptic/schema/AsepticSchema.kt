package io.github.yulimitbreak.aseptic.schema

import io.github.yulimitbreak.aseptic.schema.fields.Derived1FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.Derived2FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.Derived3FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.DerivedNFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.MessageFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.MutableValueFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.ReducedFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.TrackableFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.TrackingFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.UpdatableFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.properties.BackedPropertyDeclaration
import io.github.yulimitbreak.aseptic.schema.properties.LensPropertyDeclaration

/**
 * Base class for Aseptic schema definitions.
 *
 * A schema declares the fields that make up a piece of state. Each field is a property
 * created via one of the protected factory methods in this class. The Aseptic KSP processor reads
 * the schema at compile time and generates an operation context (for operations to read and write state)
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
 *
 * @see io.github.yulimitbreak.aseptic.Aseptic
 */

abstract class AsepticSchema {

    /**
     * Declares a mutable value field with [initial] as its starting value.
     *
     * `@Model` annotated mutable value fields:
     * - Generate an [UpdatableField][io.github.yulimitbreak.aseptic.context.fields.UpdatableField] accessor in
     * the context class
     * - Generate a member in the snapshot
     * - Generate a mutable property in atomic scope
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
     * `@Model` annotated derived fields:
     * - Generate a [ReadableField][io.github.yulimitbreak.aseptic.context.fields.ReadableField] accessor in
     * the context class
     * - Generate a member in the snapshot
     * - Generate an immutable property in atomic scope
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
     * `@Model` annotated derived fields:
     * - Generate a [ReadableField][io.github.yulimitbreak.aseptic.context.fields.ReadableField] accessor in
     * the context class
     * - Generate a member in the snapshot
     * - Generate an immutable property in atomic scope
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
     * `@Model` annotated derived fields:
     * - Generate a [ReadableField][io.github.yulimitbreak.aseptic.context.fields.ReadableField] accessor in
     * the context class
     * - Generate a member in the snapshot
     * - Generate an immutable property in atomic scope
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
     * `@Model` annotated derived fields:
     * - Generate a [ReadableField][io.github.yulimitbreak.aseptic.context.fields.ReadableField] accessor in
     * the context class
     * - Generate a member in the snapshot
     * - Generate an immutable property in atomic scope
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
     * The generated operation context exposes the `update` method that takes an update of type [U], and uses
     * [update] to apply it to the current state (starting with [initial]) to produce a new value
     *
     * `@Model` annotated reduced fields:
     * - Generate an [UpdatableField][io.github.yulimitbreak.aseptic.context.fields.UpdatableField] accessor in
     * the context class
     * - Generate a member in the snapshot
     * - Generate a special accessor in atomic scope
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
     * **Must be annotated with both `@Model` and `@Ui`**
     *
     * Message fields are always accessible from operations, and they have their own
     * special way of being read on UI. Message fields cannot be read in the model,
     * and cannot be depended on by other fields
     *
     * Message fields:
     * - Generate a [MessageField][io.github.yulimitbreak.aseptic.context.fields.MessageField] accessor in
     * the context class
     * - Do *not* generate anything in snapshot
     * - Do *not* generate anything in atomic scope
     *
     * ```kotlin
     *
     * @Model
     * @Ui(named = "navigationEvents")
     * val navigation = message<NavigationEvent>()
     *
     * ```
     * ```kotlin
     * val navigation = state.navigation.flow
     *
     * fun onNavigationHandled() {
     *      state.navigation.consume()
     * }
     * ```
     */
    protected fun <T : Any> message(): MessageFieldDeclaration<T> = MessageFieldDeclaration()

    /**
     * Shorthand for declaring a `@Model`-annotated [mutable] value field paired with a `@Ui`-annotated
     * [derived] field with the same name that transforms it.
     *
     * **Must be annotated with both `@Model` and `@Ui`**
     *
     * The resulting mutable value field generates the same accessors as a regular mutable value field
     *
     * ```kotlin
     *
     *  @Model
     *  @Ui(named="passwordText")
     *  val password = backed("") { "*".repeat(it.length) }
     *  ```
     */
    protected fun <M, U> backed(initial: M, mapper: (M) -> U): BackedPropertyDeclaration<M, U> =
        BackedPropertyDeclaration(initial, mapper)

    /**
     * Declares a lens property that generates a data class (named after the schema member, unless
     * [className] is specified) and an accessor for it on the generated `XxxxContext` that returns an
     * internally consistent instance.
     *
     * If any fields are [mutable], also creates a method to update the mutable fields atomically
     *
     * It's a convenience way of reading/writing partial snapshots, and the only way of observing partial
     * snapshots as a flow
     *
     * **Must be annotated with `@Model`, cannot be annotated with `@Ui`**
     *
     * Lenses without mutable value field dependencies:
     * - Generate a [LensProperty][io.github.yulimitbreak.aseptic.context.properties.LensProperty] accessor in
     * the context class
     * - Generate a member of the lens class in the snapshot
     * - Generate an instance of the lens in atomic scope
     *
     * Lenses with mutable value field dependencies:
     * - Generate a [MutableLensProperty][io.github.yulimitbreak.aseptic.context.properties.MutableLensProperty]
     * accessor in the context class
     * - Generate a member of the lens class in the snapshot
     * - Generate a nested atomic scope associated with this lens in global atomic scope
     *
     * ```kotlin
     *
     * val firstName = mutable("")
     * val lastName = mutable("")
     *
     * @Model
     * val fullName = lens(firstName, lastName)
     * ```
     * ```kotlin
     * @Operation
     * suspend fun ProfileContext.rename(first: String, last: String) {
     *      // atomic read of both sources at one moment
     *      val current = fullName()
     *
     *      // atomic write (both sources are mutable)
     *      fullName.update {
     *          firstName = first
     *          lastName = last
     *      }
     * }
     * ```
     */
    @Suppress("unused")
    protected fun lens(
        first: FieldDeclaration<*>,
        second: FieldDeclaration<*>,
        vararg other: FieldDeclaration<*>,
        className: String? = null
    ) =
        LensPropertyDeclaration

    /**
     * Wires this field to automatically receive updates from [source].
     *
     * Whenever [source] is written, [updateMapper] is called with the source's output value and
     * the result is applied as a write to this field.
     *
     * `@Model` annotated tracking fields:
     * - Generate a [ReadableField][io.github.yulimitbreak.aseptic.context.fields.ReadableField] accessor in
     * the context class
     * - Generate a member in the snapshot
     * - Generate an immutable property in atomic scope
     *
     * ```kotlin
     * @Model
     * val articles = reduced<List<Article>, List<Article>>(emptyList()) { all, new -> all + new }
     *
     * @Ui
     * val articleCards = reduced<List<ArticleCard>, List<ArticleCard>>(emptyList()) { cards, new -> cards + new }
     *     .tracking(articles) { newArticles -> newArticles.map { it.toCard() } }
     * ```
     *
     * **The tracking update is non-atomic**. After a non-atomic write to the source,
     * snapshots and snapshot flows may briefly still show the tracking field's old value until
     * the write completes
     */
    @Suppress("MaximumLineLength")
    protected fun <T, SourceUpdate, Update, TrackedUpdate> UpdatableFieldDeclaration<T, Update, TrackedUpdate>.tracking(
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
     * `@Model` annotated tracking fields:
     * - Generate a [ReadableField][io.github.yulimitbreak.aseptic.context.fields.ReadableField] accessor in
     * the context class
     * - Generate a member in the snapshot
     * - Generate an immutable property in atomic scope
     *
     * ```kotlin
     * @Model
     * val score = reduced<Int, Int>(0) { total, delta -> total + delta }
     *
     * @Model
     * val scoreHistory = reduced<List<Int>, Int>(emptyList()) { history, delta -> history + delta }
     *     .tracking(score)
     * ```
     *
     * **The tracking update is non-atomic**. After a non-atomic write to the source,
     * snapshots and snapshot flows may briefly still show the tracking field's old value until
     * the write completes
     */
    @Suppress("MaximumLineLength")
    protected fun <T, SourceUpdate, TrackedUpdate> UpdatableFieldDeclaration<T, SourceUpdate, TrackedUpdate>.tracking(
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
     * `@Model` annotated tracking fields:
     * - Generate a [ReadableField][io.github.yulimitbreak.aseptic.context.fields.ReadableField] accessor in
     * the context class
     * - Generate a member in the snapshot
     * - Generate an immutable property in atomic scope
     *
     * ```kotlin
     * @Model
     * val cartItems = reduced<List<Item>, Item>(emptyList()) { items, added -> items + added }
     *
     * @Model
     * val cartTotal = mutable(0.0)
     *     .tracking(cartItems) { currentTotal, addedItem -> currentTotal + addedItem.price }
     * ```
     *
     * **The tracking update is non-atomic**. After a non-atomic write to the source,
     * snapshots and snapshot flows may briefly still show the tracking field's old value until
     * the write completes
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
