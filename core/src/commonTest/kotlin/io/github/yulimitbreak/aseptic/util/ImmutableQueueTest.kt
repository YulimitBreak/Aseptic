package io.github.yulimitbreak.aseptic.util

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldEndWith
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldHaveSameHashCodeAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

class ImmutableQueueTest : BehaviorSpec() {

    init {

        Given("An empty queue") {
            val empty = ImmutableQueue<Int>()

            Then("next should be null") {
                empty.next.shouldBeNull()
            }
            Then("drop() returns empty queue") {
                empty.drop() shouldBeEqual empty
            }
            Then("size should be 0") {
                empty.size shouldBeEqual 0
            }

            When("Items are added to it") {
                val addedItems = listOf(1, 2, 3, 4, 5)
                val newQueue = addedItems.toImmutableQueue()

                Then("The queue has only these items") {
                    newQueue shouldContainExactly addedItems
                }
                Then("size reflects number of added items") {
                    newQueue.size shouldBeEqual addedItems.size
                }
                Then("They are dropped in the same order they are added") {
                    newQueue.popAll() shouldContainExactly addedItems
                }
            }
        }

        Given("A single-element queue") {
            val single = ImmutableQueue<Int>() + 42

            Then("next should be that element") {
                single.next shouldBe 42
            }
            Then("size should be 1") {
                single.size shouldBe 1
            }

            When("The element is dropped") {
                val afterDrop = single.drop()

                Then("Queue should be empty") {
                    afterDrop.size shouldBe 0
                }
                Then("next should be null") {
                    afterDrop.next.shouldBeNull()
                }
            }
        }

        Given("A non-empty list of items") {
            val source = listOf(1, 2, 3, 4, 5)

            When("A new queue is created from it") {
                val queue = source.toImmutableQueue()

                Then("Its contents should be equal to original list") {
                    queue shouldContainExactly source
                }
                Then("next should return first element") {
                    queue.next shouldBe source.first()
                }
                Then("size should equal list size") {
                    queue.size shouldBeEqual source.size
                }
            }
        }

        Given("A queue built by enqueuing, partially draining, then enqueuing again") {
            // Draining the last outNode element forces inNode to pivot into outNode
            val initial = ImmutableQueue<Int>() + 1 + 2 + 3
            val afterPivot = initial.drop() // Causes a pivot

            When("Remaining elements are dequeued") {
                Then("Order respects the original enqueue sequence") {
                    afterPivot.popAll() shouldContainExactly listOf(2, 3)
                }
            }

            When("New elements are enqueued after the pivot") {
                val extended = afterPivot + 4 + 5

                Then("New elements appear after existing ones") {
                    extended.popAll() shouldContainExactly listOf(2, 3, 4, 5)
                }
            }
        }

        Given("An arbitrary queue") {
            val queueGen = Arb.list(Arb.int()).map { it.toImmutableQueue() }

            When("Items are dropped from it") {
                Then("Items are dropped in the same order they would be iterated") {
                    checkAll(queueGen) { queue ->
                        queue.popAll() shouldContainExactly queue.toList()
                    }
                }
            }

            When("A value is pushed into it") {
                Then("It should be the last in the queue") {
                    checkAll(queueGen) { queue ->
                        val element = Arb.int().bind()
                        (queue + element).shouldEndWith(element)
                    }
                }
            }

            When("Elements are dropped one by one") {
                Then("Result matches constructing from the tail of the original list") {
                    checkAll(queueGen, Arb.int(1..5)) { source, dropCount ->
                        val queueWithLess =
                            source.toList().drop(dropCount).toImmutableQueue()
                        val queueWithDropped = run {
                            var queue = source
                            repeat(dropCount) { queue = queue.drop() }
                            queue
                        }
                        assertSoftly {
                            queueWithDropped shouldBeEqual queueWithLess
                            queueWithDropped shouldHaveSameHashCodeAs queueWithLess
                            queueWithDropped shouldContainExactly queueWithLess
                        }
                    }
                }
            }
        }
    }

    private fun <T> Iterable<T>.toImmutableQueue(): ImmutableQueue<T> =
        fold(ImmutableQueue()) { acc, i -> acc + i }

    private fun <T> ImmutableQueue<T>.popAll(): List<T> {
        val popped = mutableListOf<T>()
        var queue = this
        while (queue.next != null) {
            @Suppress("UNCHECKED_CAST")
            popped.add(queue.next as T)
            queue = queue.drop()
        }
        return popped
    }
}
