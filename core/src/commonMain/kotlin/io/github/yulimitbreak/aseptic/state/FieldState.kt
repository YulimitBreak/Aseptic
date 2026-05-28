package io.github.yulimitbreak.aseptic.state

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex

internal abstract class FieldState<out T> {

    abstract val flow: StateFlow<T>

    open val value: T get() = flow.value
}

internal abstract class UpdatableFieldState<out T, in Update> : FieldState<T>() {

    private val mutex = Mutex()

    fun update(update: Update) {
        check(mutex.isLocked) { "update() must only be called while holding the field mutex" }
        doUpdate(update)
    }

    protected abstract fun doUpdate(update: Update)

    suspend fun lock() = mutex.lock()
    fun unlock() = mutex.unlock()
}
