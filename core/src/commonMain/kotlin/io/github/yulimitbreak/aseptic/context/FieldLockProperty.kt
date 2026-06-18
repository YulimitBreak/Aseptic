package io.github.yulimitbreak.aseptic.context

import io.github.yulimitbreak.aseptic.state.FieldKey

/**
 * Marks properties and fields that can be used for fine-grained locks in
 * [XxxxContext.atomic][BaseAsepticContext.atomic].
 *
 * Most fields and lenses implement this, except
 * [MessageField][io.github.yulimitbreak.aseptic.context.fields.MessageField]
 */
interface FieldLockProperty {
    val keys: Set<FieldKey>
}
