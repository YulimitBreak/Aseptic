package io.github.yulimitbreak.aseptic

import io.github.yulimitbreak.aseptic.runner.DispatchPolicy

/**
 * Marks an [AsepticSchema][io.github.yulimitbreak.aseptic.schema.AsepticSchema] subclass for processing by the
 * Aseptic KSP processor.
 *
 * Based on the schema, the processor will generate the following classes:
 * - [XxxxState][io.github.yulimitbreak.aseptic.state.BaseAsepticState] - a state manager,
 * an entry point for state interactions. It generates methods for every operation, exposes ui flow and provides
 * handles for observing and consuming messages
 * - [XxxxContext][io.github.yulimitbreak.aseptic.context.BaseAsepticContext] - an access point to the state for
 * operations - it has accessors for all `@Model` annotated properties
 * - `XxxxContext.Snapshot` - a snapshot data class containing all readable `@Model` annotated fields returned with
 * [io.github.yulimitbreak.aseptic.context.BaseAsepticContext.snapshot]
 * - `XxxxContext.AtomicScope` - a receiver for
 * [XxxxContext.atomic][io.github.yulimitbreak.aseptic.context.BaseAsepticContext.atomic]. The scope generates
 * mutable properties for mutable fields, collects the updates and applies them atomically
 * - `XxxxContext.Xxxx` for every [lens][io.github.yulimitbreak.aseptic.schema.AsepticSchema.lens] declared in
 * the schema for partial snapshot reads
 * - `XxxxContext.XxxxScope` for every lens that depends on any
 * [mutable value][io.github.yulimitbreak.aseptic.schema.AsepticSchema.mutable] fields for atomic writes to
 * specified field
 * - `XxxxUi` - a snapshot data class containing all `@Ui` annotated fields observed
 * through [XxxxState.ui][io.github.yulimitbreak.aseptic.state.BaseAsepticState.ui]
 *
 * ```kotlin
 *
 * @Aseptic(baseName = "Profile")
 * class ProfileSchema(
 *      @Model
 *      val userId: String
 * ): AsepticSchema() {
 *
 *      @Model
 *      val userData = mutable<UserData?>(null)
 *
 *      @Model
 *      @Ui(named = "showLoading")
 *      val userLoading = mutable(true)
 *
 *      // ...
 * }
 * ```
 *
 * @param baseName overrides the common prefix used for all generated classes - by default,
 * the generated class names are derived from the schema class name
 * @param stateClassSuffix determines suffix name for a generated state manager class - default value is "State"
 * @param contextClassSuffix determines the suffix for the name of a generated context class - default value is "Context"
 * @param uiClassSuffix determines suffix name for a generated UI class - default value is "Ui"
 *
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema
 */
annotation class Aseptic(
    val baseName: String = "",
    val stateClassSuffix: String = "State",
    val contextClassSuffix: String = "Context",
    val uiClassSuffix: String = "Ui"
)

/**
 * Exposes a schema field or member to the generated state context, making it accessible inside operations.
 *
 * Applied to properties declared in an [io.github.yulimitbreak.aseptic.schema.AsepticSchema].
 * For mutable fields this generates a setter; for read-only fields it generates a getter.
 * Can be combined with [@Ui][Ui] to expose the same field to both operations and the UI.
 *
 * @param named overrides the property name in the generated state context. If empty, the schema
 * property name is used.
 * @param kDoc determines documentation to put on a field accessor in context, snapshot and
 * atomic scope classes. If left empty, it will copy the documentation on the field declaration
 * in the schema
 */
annotation class Model(
    val named: String = "",
    val kDoc: String = "",
)

/**
 * Exposes a schema field or member to the generated UI model class, making it observable by the UI.
 *
 * Applied to properties declared in an [io.github.yulimitbreak.aseptic.schema.AsepticSchema].
 * The generated UI model is a data class containing each field annotated with `@Ui`.
 * Can be combined with [@Model][Model] to expose the same field to both operations and the UI.
 *
 * @param named overrides the property name in the generated UI model. If empty, the schema
 * property name is used.
 * @param kDoc determines documentation to put on a field accessor in context, snapshot and
 * atomic scope classes. If left empty, it will copy the documentation on the field declaration
 * in the schema
 */
annotation class Ui(
    val named: String = "",
    val kDoc: String = "",
)

/**
 * Marks a function in the generated state context as an Aseptic operation.
 *
 * Operations are top-level or `object`-scoped suspending extension functions on a related
 * [`XxxxContext`][io.github.yulimitbreak.aseptic.context.BaseAsepticContext] receiver. It allows them to
 * read and update the state in a safe and isolated way. Operations cannot call other operations directly (but
 * it's okay to pass dispatch calls through lambda parameters), and should only depend on the state
 * accessed through Context and function parameters.
 *
 * For each annotated operation, the processor generates a corresponding method in `XxxxState` class. This method
 * has the same parameters as the operation function, and also two parameters for overriding dispatch policy and
 * operation key (that is used for grouping operations for dispatch policy purposes).
 *
 * ```kotlin
 *
 * @Operation(named = "loadNews", dispatchPolicy = DispatchPolicy.CANCEL)
 * suspend fun NewsContext.loadNewsArticles(
 *      categoryId: String,
 *      loadArticlesUseCase: suspend (String) -> List<Article>,
 *      onSuccess: () -> Unit,
 * ) {
 *      loading.set(true)
 *      try {
 *          val loaded = loadArticlesUseCase(categoryId)
 *          atomic(articles, categoryStatus) {
 *              articles = loaded
 *              categoryStatus = CategoryStatus.READ
 *          }
 *          onSuccess()
 *      } catch (e: IOException) {
 *          errors.emit(NewsErrorEvents.NetworkError)
 *      } finally {
 *          loading.set(false)
 *      }
 * }
 * ```
 * ```kotlin
 * fun showCategory(category: Category) {
 *      newsState.loadNews(
 *          categoryId = category.id,
 *          loadArticlesUseCase = loadArticlesUseCase,
 *          onSuccess = { newsState.updateSuggestionsWidget() },
 *          dispatchPolicy = DispatchPolicy.DROP
 *      )
 * }
 *
 * ```
 *
 * @param named overrides the function name in the generated state class. If empty, the annotated
 * function name is used.
 * @param dispatchPolicy defines the default [policy][DispatchPolicy] on how this operation behaves when another
 * instance of the same operation is already running. Defaults to [DispatchPolicy.CONCURRENT]
 */
annotation class Operation(
    val named: String = "",
    val dispatchPolicy: DispatchPolicy = DispatchPolicy.CONCURRENT,
)

/**
 * Marks code to be used only from generated code (that declares OptIn),
 * users should not use these directly, as their functioning hinges on internal conventions
 * that are guaranteed with generated code.
 */
@RequiresOptIn(
    message = "Internal Aseptic API. Do not use directly - for generated code only.",
    level = RequiresOptIn.Level.ERROR
)
annotation class AsepticInternal
