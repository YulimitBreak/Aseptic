package io.github.yulimitbreak.aseptic.runner

import io.github.yulimitbreak.aseptic.AsepticInternal

/**
 * A way for generated code to mark operation keys made by the generated code.
 * The set of them is limited so it's generally okay to keep their [OperationGroup]
 * in the runner even when it gets empty, so new instance is not created unnecessarily
 * if the operation is run again.
 *
 * Groups keyed to non-standard keys on the other hand get removed from the runner,
 * once they get empty
 */
@AsepticInternal
data class StandardOpKey(val operationName: String)
