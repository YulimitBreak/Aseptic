package io.github.yulimitbreak.aseptic

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.names.TestNameCase
import io.kotest.engine.concurrency.TestExecutionMode
import kotlin.time.Duration.Companion.seconds


class GlobalKotestConfig : AbstractProjectConfig() {

    override val testNameCase: TestNameCase = TestNameCase.InitialLowercase

    override val testExecutionMode: TestExecutionMode = TestExecutionMode.Concurrent

    override val timeout = 20.seconds
}
