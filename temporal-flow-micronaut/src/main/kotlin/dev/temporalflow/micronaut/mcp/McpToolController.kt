package dev.temporalflow.micronaut.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator
import dev.temporalflow.core.engine.WorkflowEngine
import dev.temporalflow.core.flow.FlowDefinition
import dev.temporalflow.core.flow.FlowRegistry
import dev.temporalflow.core.step.StepDefinition
import dev.temporalflow.core.step.StepRegistry
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import jakarta.inject.Singleton

data class McpToolDescriptor(
    val name: String,
    val description: String,
    val inputSchema: Any,
    val kind: String,
    val nodes: List<String> = emptyList(),
    val edges: List<List<String>> = emptyList()
)

data class McpToolCallRequest(
    val name: String,
    val arguments: JsonNode
)

/**
 * MCP-compatible endpoint surface.
 *
 * tools/list  — returns every registered step and flow as an MCP tool descriptor,
 *               with JSON Schema derived from the Kotlin input type and graph metadata for flows.
 *
 * tools/call  — executes any named step or flow via [WorkflowEngine].
 */
@Controller("/mcp")
@Singleton
class McpToolController(
    private val engine: WorkflowEngine,
    private val objectMapper: ObjectMapper
) {
    private val schemaGenerator = JsonSchemaGenerator(objectMapper)

    @Get("/tools")
    fun listTools(): List<McpToolDescriptor> =
        StepRegistry.all().map { it.toDescriptor("step") } +
        FlowRegistry.all().map { it.toDescriptor("flow") }

    @Post("/tools/call")
    fun callTool(@Body request: McpToolCallRequest): Any {
        val argumentJson = objectMapper.writeValueAsString(request.arguments)

        StepRegistry.find(request.name)?.let { definition ->
            return invokeStep(definition, argumentJson)
        }

        FlowRegistry.find(request.name)?.let { definition ->
            return invokeFlow(definition, argumentJson)
        }

        error("No step or flow registered with name '${request.name}'")
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeStep(definition: StepDefinition<*, *>, argumentJson: String): Any {
        val typed = definition as StepDefinition<Any, Any>
        val input = objectMapper.readValue(argumentJson, typed.inputClass.java)
        return engine.runStep(typed, input)
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeFlow(definition: FlowDefinition<*, *>, argumentJson: String): Any {
        val typed = definition as FlowDefinition<Any, Any>
        val input = objectMapper.readValue(argumentJson, typed.inputClass.java)
        return engine.runFlow(typed, input)
    }

    private fun StepDefinition<*, *>.toDescriptor(kind: String) =
        McpToolDescriptor(
            name        = name,
            description = description,
            inputSchema = schemaGenerator.generateSchema(inputClass.java),
            kind        = kind
        )

    private fun FlowDefinition<*, *>.toDescriptor(kind: String) =
        McpToolDescriptor(
            name        = name,
            description = description,
            inputSchema = schemaGenerator.generateSchema(inputClass.java),
            kind        = kind,
            nodes       = nodes.map { it.displayName },
            edges       = edges.map { (from, to) -> listOf(from, to) }
        )
}
