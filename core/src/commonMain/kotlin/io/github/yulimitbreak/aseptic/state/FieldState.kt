package io.github.yulimitbreak.aseptic.state

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal abstract class FieldState<T> {

    abstract val flow: StateFlow<T>

    open val value: T get() = flow.value
}

internal abstract class UpdatableFieldState<T, Update> : FieldState<T>() {

    private val mutex = Mutex()

    abstract fun update(value: Update)

    suspend fun lock() = mutex.lock()
    fun unlock() = mutex.unlock()
}
