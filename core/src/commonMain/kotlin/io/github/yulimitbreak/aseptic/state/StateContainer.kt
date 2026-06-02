package io.github.yulimitbreak.aseptic.state

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.util.UncheckedMap
import io.github.yulimitbreak.aseptic.util.UncheckedMapWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Suppress("UNCHECKED_CAST")
@AsepticInternal
class StateContainer internal constructor(
    private val fields: Map<String, FieldState<*>>,
    private val lockingOrder: List<String>,
    uiFields: Set<String>,
) : UncheckedMap<String> {

    private val consistencyMutex = Mutex()

    private val uiCombined = fields
        .filter { (key, _) -> uiFields.contains(key) }
        .map { (name, field) ->
            field.flow.map { name to it }
        }.let { flows ->
            combine(flows) { entries ->
                UncheckedMapWrapper(entries.toMap())
            }
        }

    fun <UI> uiFlow(scope: CoroutineScope, uiMapper: (UncheckedMap<String>) -> UI): StateFlow<UI> =
        uiCombined.map(uiMapper).stateIn(scope, SharingStarted.Eagerly, uiMapper(this))

    override operator fun <T> get(key: String): T = fields[key]?.value as T

    fun <T> asFlow(name: String): Flow<T> = fields[name]?.flow as Flow<T>

    suspend fun <U> update(name: String, update: U) {
        (fields[name] as UpdatableFieldState<*, U>).let { field ->
            field.lock()
            try {
                field.update(update)
            } finally {
                field.unlock()
            }
        }
    }

    suspend fun updateAtomic(lockRequest: Set<String>, update: () -> Map<String, Any?>) {
        lockRequest.withLock {
            val writes = update()
            if (writes.isNotEmpty()) {
                suspend fun write() {
                    consistencyMutex.withLock {
                        writes.forEach { (key, value) ->
                            (fields[key] as UpdatableFieldState<*, Any?>).update(value)
                        }
                    }
                }

                if (lockRequest.isEmpty()) {
                    writes.keys.withLock { write() }
                } else {
                    check(lockRequest.containsAll(writes.keys)) {
                        "Lock request must be empty or include fields: ${writes.keys}"
                    }
                    write()
                }
            }
        }
    }

    suspend fun <Snapshot> generateSnapshot(
        lockRequest: Set<String>,
        snapshotMapper: (UncheckedMap<String>) -> Snapshot
    ): Snapshot = if (lockRequest.isEmpty()) {
        consistencyMutex.withLock { snapshotMapper(this) }
    } else {
        lockRequest.withLock { snapshotMapper(this) }
    }

    private suspend inline fun <R> Set<String>.withLock(block: () -> R): R {
        if (this.isEmpty()) {
            return block()
        }
        val fields = lockingOrder.filter { it in this }.map { fields[it] as UpdatableFieldState<*, *> }
        fields.forEach { it.lock() }
        try {
            return block()
        } finally {
            fields.asReversed().forEach { it.unlock() }
        }
    }
}
