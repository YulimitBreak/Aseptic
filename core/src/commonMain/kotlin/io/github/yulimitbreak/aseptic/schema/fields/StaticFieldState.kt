package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.FieldState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A FieldState specifically to handle non-field schema members as fields
 */
internal class StaticFieldState<T>(override val value: T) : FieldState<T>() {
    override val flow by lazy { MutableStateFlow(value) }
}

// TODO builder extension specifically for it