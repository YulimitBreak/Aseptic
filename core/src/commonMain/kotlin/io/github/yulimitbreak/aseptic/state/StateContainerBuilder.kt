package io.github.yulimitbreak.aseptic.state

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.schema.fields.FieldDeclaration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Wires all field declarations in a schema into a [StateContainer].
 *
 * Generated `XxxState` constructors instantiate this builder, builder methods for every
 * schema member in **declaration order**, then call [build] to produce the live [StateContainer].
 *
 * Declaration order matters: [UpdatableFieldState] entries are appended to [lockingOrder]
 * in the order they are added, and [StateContainer] must always acquire field mutexes in that
 * order. This prevents deadlocks when multiple field mutexes are held simultaneously.
 *
 * The [FieldMap] is threaded through every [FieldDeclaration.convert] call so that derived
 * fields can look up the [StateFlow] of their source fields. Because declarations are added in
 * order, a source field's flow is always registered before any field that depends on it.
 */
@AsepticInternal
class StateContainerBuilder(private val coroutineScope: CoroutineScope) {

    private val fields = mutableMapOf<String, FieldState<*>>()

    private val lockingOrder = mutableListOf<String>()

    private val fieldMap = FieldMap()

    private val uiFields = mutableSetOf<String>()

    /**
     * Registers a non-field schema member as a static (never-changing) field.
     *
     * Used for schema constructor parameters and plain `val` members that are referenced by
     * derived fields.
     */
    fun <T> addStaticField(name: String, uiVisible: Boolean, value: T) {
        fields[name] = StaticFieldState(value)
        if (uiVisible) uiFields += name
    }

    /**
     * Converts [field] into a live [FieldState] and registers it.
     *
     * Calls [FieldDeclaration.convert] with the current [FieldMap] (so derived fields can
     * resolve source flows) and the shared [coroutineScope] (for `stateIn` sharing). If the
     * resulting state is [UpdatableFieldState], the field name is appended to [lockingOrder].
     * Setting [uiVisible] as true adds the dependency of UI mapper on this field.
     * Finally, the new flow is added to [FieldMap] so subsequent fields can use it as a source.
     */
    fun <T> addField(name: String, uiVisible: Boolean, field: FieldDeclaration<T>) {
        val state = field.convert(fieldMap, coroutineScope)
        fields[name] = state
        if (state is UpdatableFieldState<*, *, *>) {
            lockingOrder += name
        }
        fieldMap[field] = state
        if (uiVisible) uiFields += name
    }

    /**
     * Creates a [StateContainer] from this builder
     */
    fun build(): StateContainer = StateContainer(
        fields = fields,
        lockingOrder = lockingOrder,
        uiFields = uiFields,
    )

    private class StaticFieldState<T>(override val value: T) : FieldState<T>() {

        private val flow by lazy { MutableStateFlow(value) }

        override fun provideFlow() = flow
    }

    /**
     * Typed index from [FieldDeclaration] instances to their [FieldState]. Populated
     * incrementally as fields are added via [addField] / [addStaticField].
     */
    @Suppress("UNCHECKED_CAST")
    internal class FieldMap {

        private val fields = mutableMapOf<FieldDeclaration<*>, FieldState<*>>()

        operator fun <T> get(declaration: FieldDeclaration<T>): FieldState<T> = fields[declaration] as FieldState<T>

        operator fun <T> set(declaration: FieldDeclaration<T>, field: FieldState<T>) {
            fields[declaration] = field
        }
    }
}
