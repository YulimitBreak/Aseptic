package io.github.yulimitbreak.aseptic

import io.github.yulimitbreak.aseptic.runner.DispatchPolicy

/**
 * Marks an [AsepticSchema][io.github.yulimitbreak.aseptic.schema.AsepticSchema] subclass for processing by the
 * Aseptic KSP processor.
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
 * @param contextClassSuffix determines the suffix for the name of a generated context class
 * that is used as a receiver of Operations - default value is "Context"
 * @param stateClassSuffix overrides suffix name for a generated state manager class that is used as an entry
 * point for the execution of Operations - default value is "State"
 */
annotation class Aseptic(
    val baseName: String = "",
    val contextClassSuffix: String = "Context",
    val stateClassSuffix: String = "State",
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
 */
annotation class Model(
    val named: String = "",
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
 */
annotation class Ui(
    val named: String = "",
)

/**
 * Marks a function in the generated state context as an Aseptic operation.
 *
 * Operations are fire-and-forget `suspend` extension functions on the generated handle class
 * (a subclass of [BaseAsepticContext][io.github.yulimitbreak.aseptic.context.BaseAsepticContext]). Inside an operation,
 * `this` is the context - giving access to all `@Model`-annotated fields for reading and writing,
 * as well as `snapshot()` and `atomic {}` for consistent multi-field reads and writes.
 *
 * Each operation is dispatched through an [OperationRunner][io.github.yulimitbreak.aseptic.runner.OperationRunner]
 * which enforces the chosen [dispatchPolicy] relative to other running instances of the same operation.
 *
 *
 *
 * ```kotlin
 *
 * @Operation(named = "loadNews", dispatchPolicy = DispatchPolicy.CANCEL)
 * suspend fun NewsContext.loadNewsArticles(categoryId: String, loadArticlesUseCase: suspend (String) -> List<Article>) {
 *      loading.set(true)
 *      try {
 *          val loaded = loadArticlesUseCase(categoryId)
 *          atomic(articles, categoryStatus) {
 *              articles = loaded
 *              categoryStatus = CategoryStatus.READ
 *          }
 *      } catch (e: IOException) {
 *          errors.emit(NewsErrorEvents.NetworkError)
 *      } finally {
 *          loading.set(false)
 *      }
 * }
 * ```
 * ```kotlin
 * fun showCategory(category: Category) {
 *      newsState.loadNews(category.id, loadArticlesUseCase, dispatchPolicy = DispatchPolicy.DROP)
 * }
 *
 * ```
 *
 * @param named overrides the function name in the generated state context. If empty, the annotated
 * function name is used.
 * @param dispatchPolicy controls how this operation behaves when another instance of the same
 * operation is already running. Defaults to [DispatchPolicy.CONCURRENT].
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
