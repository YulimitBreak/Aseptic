package io.github.yulimitbreak.aseptic.context

import io.github.yulimitbreak.aseptic.state.FieldKey

interface FieldLockProperty {
    val keys: Set<FieldKey>
}
