# Architecture

## Problem

Temporal is a powerful durable execution engine but its Java/Kotlin SDK requires a lot of boilerplate: manually defining `@WorkflowInterface`, `@ActivityInterface`, worker setup, stubs, and serialization for every operation. There is also no built-in way to expose registered workflows as a machine-readable catalog — a gap that matters when AI agents need to discover and invoke capabilities.

This framework fills both gaps:
- A typed DSL that turns step and flow definitions into a declared data graph, eliminating boilerplate
- An automatic MCP tool catalog derived from the same registrations, with no extra work from the developer

---

## Module structure

```
temporal-flow/
├── temporal-flow-core          # No framework dependency — pure Kotlin
│   ├── step/                   # StepDefinition, registerStep, @FlowStep
│   ├── flow/                   # FlowDefinition, FlowNode graph model, registerFlow
│   ├── engine/                 # WorkflowEngine interface, SynchronousWorkflowEngine
│   ├── registry/               # StepRegistry, FlowRegistry (global, thread-safe)
│   └── temporal/               # Temporal wiring: worker, workflows, dynamic activity
│
├── temporal-flow-micronaut     # Micronaut integration layer
│   ├── config/                 # @Factory: WorkflowClient, TemporalWorkflowEngine, TemporalWorker
│   ├── lifecycle/              # TemporalWorkerLifecycle: start/stop on server events
│   ├── api/                    # WorkflowController: REST catalog + run endpoints
│   └── mcp/                    # McpToolController: MCP tools/list + tools/call
│
└── sample-app                  # Reference implementation
    ├── steps/                  # RegisteredStep declarations (@FlowStep + registerStep)
    ├── flows/                  # RegisteredFlow declarations (registerFlow + graph DSL)
    ├── domain/                 # Input/output data classes
    └── startup/                # SampleFlowRegistrar: forces lazy val initialization
```

`temporal-flow-core` has zero framework dependencies — it can be tested and used independently of Micronaut. `temporal-flow-micronaut` wires the core into the Micronaut DI container and HTTP server. Applications depend only on `temporal-flow-micronaut`.

---

## Core design: flows as data graphs

The central architectural decision is that a flow's structure is **data, not executable code**.

### The problem with opaque lambdas

A naive approach stores composition as a lambda:
```kotlin
val body: FlowScope.() -> O  // opaque — can't inspect without running
```

This makes it impossible to know execution order, data dependencies, or graph structure without running the flow. The catalog API, MCP descriptors, and visual tooling all need this information at rest.

### The solution: FlowNode graph

Every step and sub-flow inside a `registerFlow { }` block is materialized as a `FlowNode` with explicit edges:

```
FlowDefinition
  └── nodes: List<FlowNode>
        ├── StepNode(id, step, dependsOn, inputFn)
        └── SubFlowNode(id, subFlow, dependsOn, inputFn)
```

`dependsOn` declares predecessor nodes as a set of references to other nodes in the same graph. The graph is fully inspectable without execution:

```kotlin
OrderPricingFlow.definition.nodes.map { it.displayName }
// → ["validate-order", "apply-discounts", "calculate-shipping"]

OrderPricingFlow.definition.edges
// → [("validate-order" → "apply-discounts"), ("apply-discounts" → "calculate-shipping")]
```

**Parallel execution is declared by omission** — nodes with no `dependsOn` edges (or whose predecessors are all already completed) form a ready wave and run concurrently. No special `parallel { }` API is needed.

---

## Registration model: compile-time enforcement

### The problem

Nothing prevents a developer from creating a raw `StepDefinition` and wiring it into a flow, bypassing the registry entirely. The framework should make unregistered composition a compile error, not a runtime failure.

### RegisteredStep / RegisteredFlow wrappers

```kotlin
class RegisteredStep<SI, SO> @PublishedApi internal constructor(
    internal val definition: StepDefinition<SI, SO>
)
```

The constructor is `internal` — the only way to obtain a `RegisteredStep` is through `registerStep`, which simultaneously creates the definition, adds it to `StepRegistry`, and returns the wrapper. `addStep` and `addSubFlow` accept only `RegisteredStep` / `RegisteredFlow`, not raw definitions:

```kotlin
// Compile error — cannot pass a raw StepDefinition:
addStep(StepDefinition(...)) { ... }

// Only this compiles:
addStep(ValidateOrderStep) { ... }   // ValidateOrderStep: RegisteredStep
```

The same pattern applies to flows via `RegisteredFlow` and `registerFlow`.

### @FlowStep annotation

`@FlowStep` is a `SOURCE`-retention annotation — it survives only in source, not bytecode. Its purpose is tooling and discoverability (KSP processors, IDE inspections). Registration is unconditional: `registerStep` always adds to the registry regardless of the annotation.

---

## Execution engines

The `WorkflowEngine` interface has two implementations sharing the same graph traversal logic:

### SynchronousWorkflowEngine

Used in tests and local development (no Temporal required). Performs topological traversal in-process:

```
remaining = all nodes
while remaining is not empty:
    ready = nodes whose dependsOn set is fully outside remaining
    for each ready node:
        output = execute(node)
        record(output)
        remove from remaining
return outputFn(ctx)
```

Sequential within a wave (declaration order). Same result as the Temporal engine for correct flows.

### TemporalWorkflowEngine

Routes every execution through Temporal for durable, retryable, observable execution:

- `runFlow(flow, input)` → starts a `FlowExecutorWorkflow` Temporal workflow
- `runStep(step, input)` → starts a `StepExecutorWorkflow` Temporal workflow (wraps a single activity)

`FlowExecutorWorkflow` traverses the same graph model. For a wave of one node it calls the activity synchronously; for a wave of multiple nodes it dispatches each with `Async.function`, letting Temporal schedule them concurrently:

```kotlin
// Parallel wave — inputs computed eagerly before async dispatch
val scheduled = ready.map { node ->
    val encodedInput = encode(node.inputFn(ctx))
    val stub = Workflow.newUntypedActivityStub(activityOptions)
    Pair(node, Async.function { stub.execute(node.stepName, String::class.java, encodedInput) })
}
scheduled.forEach { (node, promise) -> ctx.record(node, decode(promise.get())) }
```

### DynamicActivity for named observability

Steps are executed via Temporal's `DynamicActivity` interface. The activity type name is the step name (`"validate-order"`, `"apply-discounts"`, etc.) rather than a generic dispatcher class name. This makes individual step executions visible by name in the Temporal Web UI's workflow event history.

```kotlin
class DynamicStepExecutorActivity : DynamicActivity {
    override fun execute(args: EncodedValues): Any {
        val stepName = Activity.getExecutionContext().info.activityType  // e.g. "validate-order"
        // dispatch to StepRegistry.require(stepName)
    }
}
```

The workflow uses an untyped activity stub so the activity type name is set per-call:
```kotlin
Workflow.newUntypedActivityStub(options).execute("validate-order", String::class.java, inputJson)
```

---

## Global registries

`StepRegistry` and `FlowRegistry` are global, thread-safe maps keyed by name. They are populated at initialization time — `registerStep` and `registerFlow` each call `registry.register(definition)` as a side effect. The REST and MCP controllers read from these registries directly.

Kotlin top-level `val` declarations are lazily initialized by the JVM. Applications must touch each declaration at startup (e.g., in a `StartupEvent` listener) to ensure all steps and flows appear in the catalog before any request arrives.

---

## API surfaces

Two parallel API surfaces serve different consumers:

### REST API — for humans and HTTP clients

```
GET  /catalog              → names, descriptions, graph structure (no schemas)
GET  /steps/{name}         → full detail: input schema + output schema
GET  /flows/{name}         → full detail: input/output schemas + graph nodes/edges
POST /steps/{name}/run     → execute step; returns output as JSON
POST /flows/{name}/run     → execute flow; returns output as JSON
```

### MCP API — for AI agents

```
GET  /mcp/tools            → MCP tool descriptors with inputSchema (spec-compliant)
POST /mcp/tools/call       → execute any step or flow by name
```

MCP descriptors include only `inputSchema` (per the MCP spec). Output schema is available via `GET /steps/{name}` and `GET /flows/{name}` on the REST surface.

Both surfaces derive all information from `StepRegistry` and `FlowRegistry` — no separate configuration required.

---

## Temporal topology

```
HTTP request
    │
    ▼
WorkflowController / McpToolController
    │  TemporalWorkflowEngine.runFlow(definition, input)
    ▼
Temporal WorkflowClient
    │  starts FlowExecutorWorkflow on task queue
    ▼
TemporalFlowExecutorWorkflow          (Temporal workflow — durable)
    │  graph traversal; per step:
    │  Workflow.newUntypedActivityStub.execute("step-name", ...)
    ▼
DynamicStepExecutorActivity           (Temporal activity — retryable)
    │  StepRegistry.require(activityType) → handler(input)
    ▼
Step handler (pure Kotlin function)
```

Sub-flows become child workflows (`Workflow.newChildWorkflowStub`), giving each sub-flow its own durable history. Parallel waves are dispatched with `Async.function` before `.get()` is called, so Temporal schedules them concurrently within a single workflow execution.

---

## Key principles

**Graph as data, not code.** A flow's structure is a list of nodes with explicit edges. Any tool can inspect nodes, edges, input/output types, and dependency order without executing the flow.

**Registration is the only path to composition.** `addStep` and `addSubFlow` accept only `RegisteredStep` / `RegisteredFlow`. The compiler enforces this — bypassing the registry is a type error.

**One engine interface, two implementations.** `SynchronousWorkflowEngine` and `TemporalWorkflowEngine` share the same interface and traverse the same graph model. Tests use the synchronous engine; production uses Temporal. Switching is a dependency injection concern, not a code change.

**Parallel by absence.** Concurrency is declared by omitting `dependsOn`, not by calling a `parallel { }` API. The engine computes ready waves from the graph; the caller declares intent, not scheduling.

**Named activities for observability.** Each step appears as its own named activity type in Temporal (e.g., `validate-order`) via `DynamicActivity`, rather than all steps collapsing into a single generic dispatcher name.
