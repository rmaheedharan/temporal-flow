package dev.temporalflow.core.temporal

import io.temporal.activity.ActivityOptions
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import java.time.Duration

/**
 * Thin Temporal workflow that wraps a single step execution.
 * Gives a standalone step invocation its own durable execution history.
 */
@WorkflowInterface
internal interface StepExecutorWorkflow {
    @WorkflowMethod
    fun execute(stepName: String, inputJson: String): String
}

internal class TemporalStepExecutorWorkflow : StepExecutorWorkflow {

    private val activityOptions = ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofSeconds(60))
        .build()

    override fun execute(stepName: String, inputJson: String): String {
        val stub = Workflow.newUntypedActivityStub(activityOptions)
        return stub.execute(stepName, String::class.java, inputJson)
    }
}
