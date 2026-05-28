package io.github.yulimitbreak.aseptic.state

import io.github.yulimitbreak.aseptic.schema.fields.FieldDeclaration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class StateContainerBuilder(private val coroutineScope: CoroutineScope) {

    private val fields = mutableMapOf<String, FieldState<*>>()

    private val lockingOrder = mutableListOf<String>()

    private val flowMap = FlowMap()

    fun <T> addStaticField(name: String, value: T) {
        fields[name] = StaticFieldState(value)
    }

    fun <T> addField(name: String, field: FieldDeclaration<T>) {
        val state = field.convert(flowMap, coroutineScope)
        fields[name] = state
        if (state is UpdatableFieldState<*, *>) {
            lockingOrder += name
        }
        flowMap[field] = state.flow
    }

    fun build(): StateContainer {
        TODO()
    }

    /**
     * A FieldState specifically to handle non-field schema members as fields
     */
    private class StaticFieldState<T>(override val value: T) : FieldState<T>() {
        override val flow by lazy { MutableStateFlow(value) }
    }

    /**
     * A helper class to get typed flows by field declarations, and keep unchecked casts in one place
     */
    @Suppress("UNCHECKED_CAST")
    internal class FlowMap {

        private val flows = mutableMapOf<FieldDeclaration<*>, StateFlow<*>>()

        operator fun <T> get(declaration: FieldDeclaration<T>): StateFlow<T> =
            flows[declaration] as StateFlow<T>

        operator fun <T> set(declaration: FieldDeclaration<T>, flow: StateFlow<T>) {
            flows[declaration] = flow
        }
    }
}
