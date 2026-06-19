package io.github.yulimitbreak.aseptic.state

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.util.UncheckedMap
import io.github.yulimitbreak.aseptic.util.UncheckedMapWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Central runtime object that owns all field state for a single schema instance.
 *
 * Created by [StateContainerBuilder] and held by generated `XxxxState` classes.
 */
@Suppress("UNCHECKED_CAST")
@AsepticInternal
class StateContainer internal constructor(
    private val fields: Map<FieldKey, FieldState<*>>,
    private val lockingOrder: List<UpdatableFieldState<*, *, *>>,
    private val uiFields: Set<FieldKey>,
) {

    private val consistencyMutex = Mutex()

    private val controlGate = MutableStateFlow(true)

    private val valueMap = object : UncheckedMap<FieldKey> {
        override fun <T> get(key: FieldKey): T = this@StateContainer[key]
    }

    fun <Snapshot> snapshotFlow(keys: Set<FieldKey>, mapper: (UncheckedMap<FieldKey>) -> Snapshot) =
        fields.filterKeys { key -> keys.contains(key) }.let { fields ->
            val builder = SnapshotFlowBuilder()
            for ((_, field) in fields) {
                field.buildSnapshotFlow(builder)
            }
            builder.build(controlGate)
        }.map(mapper)

    /**
     * Returns a [StateFlow] of UI state mapped from all `@Ui` fields via [uiMapper].
     */
    fun <UI> uiFlow(scope: CoroutineScope, uiMapper: (UncheckedMap<FieldKey>) -> UI): StateFlow<UI> =
        snapshotFlow(uiFields, uiMapper).stateIn(
            scope,
            SharingStarted.Eagerly,
            uiMapper(valueMap)
        )

    /**
     * Returns the current value of the field by key.
     * Multiple [get] calls aren't guaranteed to return fields in mutually consistent state, use [generateSnapshot]
     * for that instead.
     *
     * Performs an unchecked cast, should be used only in generated code.
     */
    operator fun <T> get(key: FieldKey): T = fields.getValue(key).value as T

    /**
     * Returns the underlying [Flow] for the field by key.
     *
     * Performs an unchecked cast, should be used only in generated code.
     */
    fun <T> asFlow(key: FieldKey): Flow<T> = SnapshotFlowBuilder().also {
        fields.getValue(key).buildSnapshotFlow(it)
    }.build(controlGate).map { it[key] }

    /**
     * Applies [update] to a single updatable field under its field mutex.
     *
     * Performs an unchecked cast, should be used only in generated code.
     */
    suspend fun <U> update(key: FieldKey, update: U) {
        (fields[key] as UpdatableFieldState<*, U, *>).let { field ->
            field.lock()
            try {
                field.update(update)
            } finally {
                field.unlock()
            }
        }
    }

    /**
     * First gets a full consistent snapshot of the state using [generateSnapshot], then
     * runs [update] on that snapshot without holding any locks,
     * then obtains locks for changed fields and writes them atomically,
     * ensuring that [generateSnapshot] does not return inconsistent values with only a partial write.
     *
     * Values returned by [update] overwrite current state, even if actual values have changed since then
     */
    suspend fun updateAtomic(update: (UncheckedMap<FieldKey>) -> AtomicUpdate) {
        val writes = update(generateSnapshot { it })
        if (writes.isNotEmpty()) {
            writes.keys.withLock { flushAtomicWrite(writes) }
        }
    }

    /**
     * Locks fields specified in [lockRequest], runs [update] and then writes changed fields
     * atomically, ensuring that [generateSnapshot] and all flows do not return inconsistent values with
     * only a partial write, and that fields from [lockRequest] haven't been changed for
     * the duration of [update] call. [UncheckedMap] passed to [update] is guaranteed to be consistent on
     * [lockRequest] fields but no other guarantees given
     *
     * Changed fields have to be a subset of [lockRequest] (no way to ensure proper locking order otherwise)
     */
    suspend fun updateAtomic(lockRequest: Set<FieldKey>, update: (UncheckedMap<FieldKey>) -> AtomicUpdate) {
        lockRequest.withLock {
            val writes = update(valueMap)
            if (writes.isNotEmpty()) {
                check(lockRequest.containsAll(writes.keys)) {
                    "Lock request must include all written fields: ${writes.keys}"
                }
                flushAtomicWrite(writes)
            }
        }
    }

    private suspend fun flushAtomicWrite(writes: AtomicUpdate) {
        consistencyMutex.withLock {
            controlGate.update { false }
            writes.forEach { (key, updateSequence) ->
                updateSequence.forEach { update ->
                    (fields[key] as UpdatableFieldState<*, Any?, *>).update(update)
                }
            }
            controlGate.update { true }
        }
    }

    /**
     * Generates a snapshot via [snapshotMapper] from current data, ensuring that all
     * atomic writes were completed, and the state is internally consistent.
     */
    suspend fun <Snapshot> generateSnapshot(
        snapshotMapper: (UncheckedMap<FieldKey>) -> Snapshot,
    ): Snapshot = consistencyMutex.withLock { snapshotMapper(valueMap) }

    /**
     * Locks fields specified in [lockRequest] and generates snapshot using [snapshotMapper],
     * fields in [lockRequest] are guaranteed to be internally consistent with atomic writes,
     * no such guarantee for other fields
     */
    suspend fun <Snapshot> generateSnapshot(
        lockRequest: Set<FieldKey>,
        snapshotMapper: (UncheckedMap<FieldKey>) -> Snapshot,
    ): Snapshot = lockRequest.withLock { snapshotMapper(valueMap) }

    /**
     * Generate a frozen UncheckedMap of the whole state
     */
    internal suspend fun frozenSnapshotMap(): UncheckedMap<FieldKey> = consistencyMutex.withLock {
        UncheckedMapWrapper(
            fields.mapValues { (_, v) -> v.value }
        )
    }

    private suspend inline fun <R> Set<FieldKey>.withLock(block: () -> R): R {
        if (this.isEmpty()) {
            return block()
        }
        val fields = this.flatMapTo(mutableSetOf()) {
            fields.getValue(it).getUpdateSources()
        }.let { states ->
            lockingOrder.filter { it in states }
        }
        fields.forEach { it.lock() }
        try {
            return block()
        } finally {
            fields.asReversed().forEach { it.unlock() }
        }
    }
}
