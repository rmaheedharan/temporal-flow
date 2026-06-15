package dev.temporalflow.micronaut.api

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
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import jakarta.inject.Singleton

data class CatalogEntry(
    val name: String,
    val description: String,
    val kind: String,
    val nodes: List<String> = emptyList(),
    val edges: List<List<String>> = emptyList()
)

data class CatalogResponse(val steps: List<CatalogEntry>, val flows: List<CatalogEntry>)

data class StepDetail(
    val name: String,
    val description: String,
    val kind: String = "step",
    val inputSchema: Any,
    val outputSchema: Any
)

data class FlowDetail(
    val name: String,
    val description: String,
    val kind: String = "flow",
    val inputSchema: Any,
    val outputSchema: Any,
    val nodes: List<String>,
    val edges: List<List<String>>
)

/**
 * REST API surface for human callers — symmetrical to the MCP surface for agents.
 *
 * GET  /catalog              — all registered steps and flows with graph metadata
 * GET  /steps/{name}         — step detail with input and output schemas
 * GET  /flows/{name}         — flow detail with input/output schemas and graph
 * POST /steps/{name}/run     — execute a named step with a JSON body
 * POST /flows/{name}/run     — execute a named flow with a JSON body
 */
@Controller
@Singleton
class WorkflowController(
    private val engine: WorkflowEngine,
    private val objectMapper: ObjectMapper
) {
    private val schemaGenerator = JsonSchemaGenerator(objectMapper)

    @Get("/catalog")
    fun catalog(): CatalogResponse = CatalogResponse(
        steps = StepRegistry.all().map { CatalogEntry(it.name, it.description, "step") },
        flows = FlowRegistry.all().map { flow ->
            CatalogEntry(
                name        = flow.name,
                description = flow.description,
                kind        = "flow",
                nodes       = flow.nodes.map { it.displayName },
                edges       = flow.edges.map { (from, to) -> listOf(from, to) }
            )
        }
    )

    @Get("/steps/{name}")
    fun stepDetail(@PathVariable name: String): StepDetail {
        val definition = StepRegistry.require(name)
        return StepDetail(
            name         = definition.name,
            description  = definition.description,
            inputSchema  = schemaGenerator.generateSchema(definition.inputClass.java),
            outputSchema = schemaGenerator.generateSchema(definition.outputClass.java)
        )
    }

    @Get("/flows/{name}")
    fun flowDetail(@PathVariable name: String): FlowDetail {
        val definition = FlowRegistry.require(name)
        return FlowDetail(
            name         = definition.name,
            description  = definition.description,
            inputSchema  = schemaGenerator.generateSchema(definition.inputClass.java),
            outputSchema = schemaGenerator.generateSchema(definition.outputClass.java),
            nodes        = definition.nodes.map { it.displayName },
            edges        = definition.edges.map { (from, to) -> listOf(from, to) }
        )
    }

    @Post("/steps/{name}/run")
    fun runStep(@PathVariable name: String, @Body body: JsonNode): Any {
        val definition = StepRegistry.require(name)
        return invokeStep(definition, objectMapper.writeValueAsString(body))
    }

    @Post("/flows/{name}/run")
    fun runFlow(@PathVariable name: String, @Body body: JsonNode): Any {
        val definition = FlowRegistry.require(name)
        return invokeFlow(definition, objectMapper.writeValueAsString(body))
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeStep(definition: StepDefinition<*, *>, json: String): Any {
        val typed = definition as StepDefinition<Any, Any>
        val input = objectMapper.readValue(json, typed.inputClass.java)
        return engine.runStep(typed, input)
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeFlow(definition: FlowDefinition<*, *>, json: String): Any {
        val typed = definition as FlowDefinition<Any, Any>
        val input = objectMapper.readValue(json, typed.inputClass.java)
        return engine.runFlow(typed, input)
    }
}
