# temporal-flow

A Kotlin framework that combines [Temporal](https://temporal.io) durable execution with an [MCP](https://spec.modelcontextprotocol.io) tool catalog. Define typed, composable workflow steps once — run them durably via Temporal and expose them to AI agents via MCP automatically.

**Stack:** Kotlin 2.4.0 · JDK 25 · Micronaut 4.7.3 · Temporal Java SDK 1.27.0 · KSP 2.3.9 · Gradle

---

## Modules

| Module | Purpose |
|---|---|
| `temporal-flow-core` | DSL, graph model, registries, engine abstraction, Temporal wiring |
| `temporal-flow-micronaut` | Micronaut DI, REST API, MCP endpoint, Temporal worker lifecycle |
| `sample-app` | Reference implementation: order fulfillment domain |

---

## Running locally

### 1. Start Temporal

```bash
docker compose up -d
```

This starts the Temporal dev server (in-memory SQLite, no external database needed):
- gRPC on `localhost:7233` — the app connects here
- Web UI on `http://localhost:8233` — browse workflow executions

### 2. Start the app

```bash
./gradlew :sample-app:run
```

HTTP server starts on `http://localhost:8080`.

### 3. Verify

```bash
curl http://localhost:8080/catalog
```

---

## REST API

### Discover what's available

| Endpoint | Description |
|---|---|
| `GET /catalog` | All registered steps and flows (names, descriptions, graph edges) |
| `GET /steps/{name}` | Step detail: description + input and output JSON schemas |
| `GET /flows/{name}` | Flow detail: description + input/output schemas + graph nodes and edges |

### Execute

| Endpoint | Description |
|---|---|
| `POST /steps/{name}/run` | Run a single step; body = step input as JSON |
| `POST /flows/{name}/run` | Run a flow; body = flow input as JSON |

Every execution goes through Temporal — each run gets its own durable workflow execution visible in the Temporal Web UI at `http://localhost:8233`.

### Example: run a flow

```bash
curl -X POST http://localhost:8080/flows/order-pricing/run \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-001",
    "shippingAddress": { "street": "1 Main St", "city": "Austin", "country": "US" },
    "promoCode": "SAVE10"
  }'
```

### Example: run a single step

```bash
curl -X POST http://localhost:8080/steps/validate-order/run \
  -H "Content-Type: application/json" \
  -d '{ "orderId": "ORD-001" }'
```

Use `GET /steps/{name}` or `GET /flows/{name}` to see the exact input and output shape before calling.

---

## MCP API

Agents and tools that speak the [Model Context Protocol](https://spec.modelcontextprotocol.io) can discover and invoke all registered steps and flows.

| Endpoint | Description |
|---|---|
| `GET /mcp/tools` | All steps and flows as MCP tool descriptors with `inputSchema` |
| `POST /mcp/tools/call` | Execute any step or flow by name |

```bash
# List tools
curl http://localhost:8080/mcp/tools

# Call a tool
curl -X POST http://localhost:8080/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{ "name": "validate-order", "arguments": { "orderId": "ORD-001" } }'
```

---

## Writing steps

A step is a pure function with typed input and output. Register it with `registerStep` — this creates the step, adds it to the global registry, and returns a `RegisteredStep` handle that can be used in flow definitions.

```kotlin
// steps/OrderPricingSteps.kt
@FlowStep
val ValidateOrderStep = registerStep<ValidateOrderInput, ValidateOrderOutput>(
    name        = "validate-order",
    description = "Validates the order exists and returns its items and base total"
) { input ->
    ValidateOrderOutput(
        items     = fetchItems(input.orderId),
        baseTotal = calculateTotal(items)
    )
}
```

- `@FlowStep` is a source-retention annotation for tooling and discoverability; registration happens inside `registerStep`.
- The type parameters `<I, O>` are inferred from the lambda and enforced at compile time.
- Only a `RegisteredStep` (obtained from `registerStep`) can be passed to `addStep` inside a flow.

---

## Writing flows

A flow is a directed acyclic graph of steps and sub-flows. Declare each node with `addStep` or `addSubFlow`, state its dependencies with `dependsOn`, and declare its input with `input { }`.

```kotlin
// flows/OrderPricingFlow.kt
val OrderPricingFlow = registerFlow<OrderPricingInput, OrderPricingOutput>(
    name        = "order-pricing",
    description = "Validates order, applies discounts, and calculates shipping"
) {
    val validated = addStep(ValidateOrderStep) {
        input { ValidateOrderInput(flowInput.orderId) }
    }

    val discounted = addStep(ApplyDiscountsStep) {
        dependsOn = setOf(validated)
        input {
            ApplyDiscountsInput(
                items     = outputOf(validated).items,
                baseTotal = outputOf(validated).baseTotal,
                promoCode = flowInput.promoCode
            )
        }
    }

    val shipping = addStep(CalculateShippingStep) {
        dependsOn = setOf(discounted)
        input {
            CalculateShippingInput(
                discountedTotal = outputOf(discounted).discountedTotal,
                shippingAddress = flowInput.shippingAddress
            )
        }
    }

    output {
        OrderPricingOutput(
            grandTotal   = outputOf(shipping).grandTotal,
            carrier      = outputOf(shipping).carrier,
            shippingCost = outputOf(shipping).shippingCost
        )
    }
}
```

**Parallel execution** — nodes with no `dependsOn` (or with `dependsOn` pointing to already-completed nodes in the same wave) run concurrently via `Async.function` in the Temporal engine:

```kotlin
val OrderFulfillmentPipeline = registerFlow<...>(...) {
    // pricing and inventory have no dependsOn → Temporal runs them in parallel
    val pricing   = addSubFlow(OrderPricingFlow)   { input { ... } }
    val inventory = addSubFlow(InventoryManagementFlow) { input { ... } }

    val fulfillment = addSubFlow(FulfillmentFlow) {
        dependsOn = setOf(pricing)   // waits for pricing; inventory runs independently
        input { FulfillmentInput(outputOf(pricing).grandTotal, flowInput.paymentMethod) }
    }

    output { ... }
}
```

---

## Registering steps and flows at startup

Kotlin top-level `val` declarations are lazy — they initialize on first access. Touch each registered step and flow explicitly in a startup listener so they appear in the catalog before any request arrives.

```kotlin
@Singleton
class SampleFlowRegistrar : ApplicationEventListener<StartupEvent> {
    override fun onApplicationEvent(event: StartupEvent) {
        // Steps first — flows reference them
        ValidateOrderStep; ApplyDiscountsStep; CalculateShippingStep
        // ...

        // Then flows
        OrderPricingFlow; InventoryManagementFlow; OrderFulfillmentPipeline
    }
}
```

---

## Configuration

`sample-app/src/main/resources/application.yml`:

```yaml
micronaut:
  application:
    name: temporal-flow-sample

temporal:
  server-url: 127.0.0.1:7233   # Temporal gRPC endpoint
  task-queue: order-fulfillment  # Worker task queue name
```

---

## Testing

Tests use `SynchronousWorkflowEngine` — no Temporal server needed. The engine traverses the same graph model as the Temporal engine, so test behavior matches production behavior exactly.

```kotlin
class OrderPricingFlowTest : DescribeSpec({
    val engine = SynchronousWorkflowEngine()

    it("applies promo code discount") {
        val result = engine.runFlow(OrderPricingFlow, OrderPricingInput("ORD-001", usAddress, "SAVE10"))
        result.grandTotal shouldBe BigDecimal("113.963")
    }
})
```

Run all tests:

```bash
./gradlew test
```
