package io.github.yulimitbreak.aseptic.handle.fields

import io.github.yulimitbreak.aseptic.state.FieldKey

interface FieldLockProperty {
    val keys: Set<FieldKey>
}
