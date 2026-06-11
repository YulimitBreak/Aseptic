@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.state

import io.github.yulimitbreak.aseptic.schema.fields.Derived1FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.Derived2FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.MutableValueFieldDeclaration
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class FieldStateGetUpdateSourcesTest : BehaviorSpec() {

    init {
        Given("a mutable field") {
            val decl = MutableValueFieldDeclaration(0)
            val state = decl.convert("m", StateContainerBuilder.FieldMap())

            Then("getUpdateSources returns itself") {
                state.getUpdateSources() shouldBe setOf(state)
            }
        }

        Given("a derived field over a single mutable") {
            val mDecl = MutableValueFieldDeclaration(0)
            val fieldMap = StateContainerBuilder.FieldMap()
            val mState = mDecl.convert("m", fieldMap).also { fieldMap[mDecl] = it }
            val dState = Derived1FieldDeclaration(mDecl) { it * 2 }.convert("d", fieldMap)

            Then("getUpdateSources returns the mutable, not the derived") {
                dState.getUpdateSources() shouldBe setOf(mState)
            }
        }

        Given("a two-level derived chain: mutable -> derived -> derived") {
            val mDecl = MutableValueFieldDeclaration(0)
            val fieldMap = StateContainerBuilder.FieldMap()
            val mState = mDecl.convert("m", fieldMap).also { fieldMap[mDecl] = it }
            val d1Decl = Derived1FieldDeclaration(mDecl) { it }
            val d1State = d1Decl.convert("d1", fieldMap).also { fieldMap[d1Decl] = it }
            val d2State = Derived1FieldDeclaration(d1Decl) { it }.convert("d2", fieldMap)

            Then("getUpdateSources on intermediate derived returns the root mutable") {
                d1State.getUpdateSources() shouldBe setOf(mState)
            }

            Then("getUpdateSources on leaf derived returns the root mutable") {
                d2State.getUpdateSources() shouldBe setOf(mState)
            }
        }

        Given("a derived field over two independent mutables") {
            val m1Decl = MutableValueFieldDeclaration(0)
            val m2Decl = MutableValueFieldDeclaration(0)
            val fieldMap = StateContainerBuilder.FieldMap()
            val m1State = m1Decl.convert("m1", fieldMap).also { fieldMap[m1Decl] = it }
            val m2State = m2Decl.convert("m2", fieldMap).also { fieldMap[m2Decl] = it }
            val dState = Derived2FieldDeclaration(m1Decl, m2Decl) { a, b -> a + b }.convert("d", fieldMap)

            Then("getUpdateSources returns both mutables") {
                dState.getUpdateSources() shouldBe setOf(m1State, m2State)
            }
        }

        Given("a diamond dependency: two deriveds from the same mutable, combined into a third") {
            val mDecl = MutableValueFieldDeclaration(0)
            val fieldMap = StateContainerBuilder.FieldMap()
            val mState = mDecl.convert("m", fieldMap).also { fieldMap[mDecl] = it }
            val d1Decl = Derived1FieldDeclaration(mDecl) { it }
            d1Decl.convert("d1", fieldMap).also { fieldMap[d1Decl] = it }
            val d2Decl = Derived1FieldDeclaration(mDecl) { it * 2 }
            d2Decl.convert("d2", fieldMap).also { fieldMap[d2Decl] = it }
            val dState = Derived2FieldDeclaration(d1Decl, d2Decl) { a, b -> a + b }.convert("d", fieldMap)

            Then("getUpdateSources returns the shared mutable exactly once") {
                dState.getUpdateSources() shouldBe setOf(mState)
            }
        }
    }
}
