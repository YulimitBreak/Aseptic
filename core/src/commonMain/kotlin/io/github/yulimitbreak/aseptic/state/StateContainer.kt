package io.github.yulimitbreak.aseptic.state

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.util.UncheckedMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Central runtime object that owns all field state for a single schema instance.
 *
 * Created by [StateContainerBuilder] and held by generated `XxxState` classes.
 *
 * TODO improve documentation
 */
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
        .let {
            SnapshotFlowBuilder.of(it.map { (_, value) -> value }).build()
        }

    /**
     * Returns a [StateFlow] of UI state mapped from all `@Ui` fields via [uiMapper].
     */
    fun <UI> uiFlow(scope: CoroutineScope, uiMapper: (UncheckedMap<String>) -> UI): StateFlow<UI> =
        TODO("(uiMapper)").stateIn(scope, SharingStarted.Eagerly, uiMapper(this))

    /**
     * Returns the current value of the field by name.
     * Multiple [get] calls aren't guaranteed to return fields in mutually consistent state, use [generateSnapshot]
     * for that instead.
     *
     * Performs an unchecked cast, should be used only in generated code.
     */
    override operator fun <T> get(key: String): T = fields[key]?.value as T

    /**
     * Returns the underlying [Flow] for the field by name.
     *
     * Performs an unchecked cast, should be used only in generated code.
     */
    fun <T> asFlow(name: String): Flow<T> = TODO("fields[name]?.provideFlow() as Flow<T>")

    /**
     * Applies [update] to a single updatable field under its field mutex.
     *
     * Performs an unchecked cast, should be used only in generated code.
     */
    suspend fun <U> update(name: String, update: U) {
        (fields[name] as UpdatableFieldState<*, U, *>).let { field ->
            field.lock()
            try {
                field.update(update)
            } finally {
                field.unlock()
            }
        }
    }

    /**
     * Applies a multi-field atomic write in one of two modes, determined by [lockRequest]:
     *
     * - **Deferred**: if [lockRequest] is empty [update] block runs with no locks held.
     *   After the block, field mutexes for all written fields are acquired in declaration order,
     *   then [consistencyMutex], then writes are flushed to [StateFlow]s.
     *
     * - **Pre-locked**: if [lockRequest] is not empty, the provided fields have
     *   their mutexes acquired upfront before [update] runs, providing read stability during
     *   the block. Writes returned by [update] must be a subset of [lockRequest] (otherwise no way to guarantee
     *   the correct locking order). After the block, [consistencyMutex] is acquired and writes are flushed.
     *
     * In both modes [consistencyMutex] is held during the flush, so a concurrent
     * [generateSnapshot] will never observe a partial write.
     *
     * Performs an unchecked cast, should be used only in generated code.
     */
    suspend fun updateAtomic(lockRequest: Set<String>, update: () -> Map<String, Any?>) {
        lockRequest.withLock {
            val writes = update()
            if (writes.isNotEmpty()) {
                suspend fun write() {
                    consistencyMutex.withLock {
                        writes.forEach { (key, value) ->
                            (fields[key] as UpdatableFieldState<*, Any?, *>).update(value)
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

    /**
     * Reads a consistent snapshot of field values via [snapshotMapper] in one of two modes,
     * determined by [lockRequest]:
     *
     * - **Full**: if [lockRequest] is empty, acquires [consistencyMutex] before invoking
     *   [snapshotMapper]. Guarantees that the updates made by [updateAtomic] are written
     *   completely, all fields are in a consistent state
     *
     * - **Fine-grained**: if [lockRequest] is not empty, acquires only the field mutexes for the
     *   specified fields in declaration order. Guarantees that listed fields specifically
     *   are consistent, no guarantees on other fields
     *
     * In both modes, locks are released before returning the snapshot to the caller.
     */
    suspend fun <Snapshot> generateSnapshot(
        lockRequest: Set<String>,
        snapshotMapper: (UncheckedMap<String>) -> Snapshot
    ): Snapshot = if (lockRequest.isEmpty()) {
        consistencyMutex.withLock { snapshotMapper(this) }
    } else {
        lockRequest.withLock { snapshotMapper(this) }
    }

    /**
     * Acquires the field mutexes for all names in this set, in [lockingOrder] (declaration
     * order), executes [block], then releases in reverse order.
     */
    private suspend inline fun <R> Set<String>.withLock(block: () -> R): R {
        if (this.isEmpty()) {
            return block()
        }
        val fields = lockingOrder.filter { it in this }.map { fields[it] as UpdatableFieldState<*, *, *> }
        fields.forEach { it.lock() }
        try {
            return block()
        } finally {
            fields.asReversed().forEach { it.unlock() }
        }
    }
}
