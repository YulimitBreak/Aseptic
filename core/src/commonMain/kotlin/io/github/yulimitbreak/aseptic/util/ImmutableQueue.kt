package io.github.yulimitbreak.aseptic.util

/**
 * Immutable queue, based on Scala implementation
 *
 * Use [plus] to enqueue a new value (returns a new queue), use [next] to read the first value
 * without popping, and use [drop] to get a new queue without the first value
 */
internal class ImmutableQueue<T> private constructor(
    // A linked list of elements added to the queue that haven't been pivoted yet
    private val inNode: Node<T>? = null,
    // A linked list of elements that can be taken out of queue, if it's empty, the queue is empty
    private val outNode: Node<T>? = null,
) : Iterable<T> {

    init {
        require(outNode != null || inNode == null) {
            "Invalid queue (inNode $inNode, outNode $outNode)"
        }
    }

    constructor() : this(null, null)

    val next: T? = outNode?.head

    val size: Int by lazy {
        (outNode?.size ?: 0) + (inNode?.size ?: 0)
    }

    private val pivotedInNode by lazy {
        var result: Node<T>? = null
        var current = inNode
        while (current != null) {
            result = Node(current.head, result)
            current = current.tail
        }
        result
    }

    /**
     * Return a new [ImmutableQueue] with [value] enqueued
     */
    operator fun plus(value: T): ImmutableQueue<T> = if (outNode != null) {
        ImmutableQueue(
            Node(value, inNode),
            outNode
        )
    } else {
        ImmutableQueue(
            null,
            Node(value, null)
        )
    }

    /**
     * Return a new [ImmutableQueue] with first element dequeued
     */
    fun drop(): ImmutableQueue<T> = when {
        outNode == null -> this
        outNode.size == 1 -> ImmutableQueue(inNode = null, outNode = pivotedInNode)
        else -> ImmutableQueue(inNode = inNode, outNode = outNode.tail)
    }

    private data class Node<T>(val head: T, val tail: Node<T>?) : Iterable<T> {

        val size: Int by lazy { 1 + (tail?.size ?: 0) }

        override fun iterator(): Iterator<T> = iterator {
            yield(head)
            tail?.let { yieldAll(it) }
        }
    }

    override fun iterator(): Iterator<T> = iterator {
        outNode?.let { yieldAll(it) }
        pivotedInNode?.let { yieldAll(it) }
    }

    override fun toString(): String = this.toList().joinToString(
        prefix = "ImmutableQueue[",
        postfix = "]",
        separator = ", "
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImmutableQueue<*>) return false
        val thisIterator = this.iterator()
        val otherIterator = other.iterator()
        while (thisIterator.hasNext() || otherIterator.hasNext()) {
            if (thisIterator.hasNext() != otherIterator.hasNext()) return false
            if (thisIterator.next() != otherIterator.next()) return false
        }
        return true
    }

    override fun hashCode(): Int =
        this.fold(17) { acc, value ->
            acc * 31 + value.hashCode()
        }
}
