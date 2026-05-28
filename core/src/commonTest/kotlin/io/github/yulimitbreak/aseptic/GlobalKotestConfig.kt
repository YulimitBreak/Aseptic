package io.github.yulimitbreak.aseptic

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.names.TestNameCase
import io.kotest.core.spec.IsolationMode
import io.kotest.engine.concurrency.SpecExecutionMode
import kotlin.time.Duration.Companion.seconds

class GlobalKotestConfig : AbstractProjectConfig() {

    override val testNameCase: TestNameCase = TestNameCase.InitialLowercase

    override val specExecutionMode: SpecExecutionMode =
        SpecExecutionMode.LimitedConcurrency(Runtime.getRuntime().availableProcessors())

    override val timeout = 20.seconds

    override val isolationMode: IsolationMode = IsolationMode.InstancePerRoot
}
