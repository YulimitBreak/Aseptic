package io.github.yulimitbreak.aseptic.handle

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.state.StateContainer

@AsepticInternal
abstract class BaseAsepticHandle protected constructor(
    protected val container: StateContainer
)
