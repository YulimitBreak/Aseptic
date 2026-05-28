package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.github.yulimitbreak.aseptic.state.UpdatableFieldState
import kotlinx.coroutines.flow.MutableStateFlow

internal object FieldTestUtils {

    fun <T> flowMapWith(
        decl: FieldDeclaration<T>,
        flow: MutableStateFlow<T>,
    ): StateContainerBuilder.FlowMap = StateContainerBuilder.FlowMap().also { it[decl] = flow }

    fun flowMapWith(
        vararg entries: Pair<FieldDeclaration<*>, MutableStateFlow<*>>,
    ): StateContainerBuilder.FlowMap = StateContainerBuilder.FlowMap().also { map ->
        entries.forEach { (decl, flow) -> map[decl] = flow }
    }

    fun flowMapWith(
        decls: List<FieldDeclaration<*>>,
        flows: List<MutableStateFlow<*>>,
    ): StateContainerBuilder.FlowMap = StateContainerBuilder.FlowMap().also { map ->
        decls.zip(flows).forEach { (decl, flow) -> map[decl] = flow }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T, U> Any.asUpdatable() = this as UpdatableFieldState<T, U>

    suspend fun <T, U> UpdatableFieldState<T, U>.locked(block: UpdatableFieldState<T, U>.() -> Unit) {
        lock()
        try { block() } finally { unlock() }
    }
}
