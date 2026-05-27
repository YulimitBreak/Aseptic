package io.github.yulimitbreak.aseptic

import io.github.yulimitbreak.aseptic.runner.DispatchPolicy

annotation class Aseptic(
    val baseName: String = "",
)

annotation class Model(
    val named: String = ""
)

annotation class Ui(
    val named: String = "",
)

annotation class Operation(
    val named: String = "",
    val dispatchPolicy: DispatchPolicy = DispatchPolicy.CONCURRENT,
)
