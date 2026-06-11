package io.github.yulimitbreak.aseptic.state

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.schema.fields.FieldDeclaration

/**
 * Wires all field declarations in a schema into a [StateContainer].
 *
 * Generated `XxxState` constructors instantiate this builder, builder methods for every
 * schema member in **declaration order**, then call [build] to produce the live [StateContainer].
 */
@AsepticInternal
class StateContainerBuilder {

    private val fields = mutableMapOf<String, FieldState<*>>()

    private val lockingOrder = mutableListOf<UpdatableFieldState<*, *, *>>()

    private val fieldMap = FieldMap()

    private val uiFields = mutableSetOf<String>()

    /**
     * Registers a non-field schema member as a static (never-changing) field.
     *
     * Used for schema constructor parameters and plain `val` members that are referenced by
     * derived fields.
     */
    fun <T> addStaticField(name: String, uiVisible: Boolean, value: T) {
        fields[name] = StaticFieldState(name, value)
        if (uiVisible) uiFields += name
    }

    /**
     * Converts [field] into a live [FieldState] and registers it using the [name].
     * Setting [uiVisible] as true adds the dependency of UI mapper on this field.
     *
     * Updatable fields added create a canonical locking order, to have a consistent
     * order to lock fields without deadlocks
     */
    fun <T> addField(name: String, uiVisible: Boolean, field: FieldDeclaration<T>) {
        val state = field.convert(name, fieldMap)
        fields[name] = state
        if (state is UpdatableFieldState<*, *, *> && state.isLockable) {
            lockingOrder += state
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

    /**
     * A trivial [FieldState] to be able to handle static values as fields
     */
    private class StaticFieldState<T>(name: String, override val value: T) : FieldState<T>(name) {

        override fun buildSnapshotFlow(snapshotFlowBuilder: SnapshotFlowBuilder) {
            snapshotFlowBuilder.addMapper(name) { value }
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal class FieldMap {

        private val fields = mutableMapOf<FieldDeclaration<*>, FieldState<*>>()

        operator fun <T> get(declaration: FieldDeclaration<T>): FieldState<T> = fields[declaration] as FieldState<T>

        operator fun <T> set(declaration: FieldDeclaration<T>, field: FieldState<T>) {
            fields[declaration] = field
        }
    }
}
