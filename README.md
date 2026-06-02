# Antigen

> **Research project — interfaces and configuration formats are unstable.**

Antigen is a REST API test quality framework combining two capabilities:

1. **Fault simulation** — intercepts HTTP responses during test execution, injects mutations derived from invariants you define, and measures how many your tests actually catch. Draws from mutation testing (evaluate tests, not code) and property-based testing (mutations are derived from invariant constraints, not code grammar).

2. **AI test generation** — given an API specification, uses an LLM to generate a test suite, then validates it through compilation, execution, and fault simulation. Tests that pass but fail to catch injected faults are revised automatically until they meet a configurable detection threshold.

The two capabilities compose: the simulation loop is the quality gate for generated tests.

---

## Table of Contents

- [How it works](#how-it-works)
- [Fault Simulation](#fault-simulation)
  - [What gets mutated](#what-gets-mutated)
  - [Configuration](#configuration)
  - [Invariants DSL](#invariants-dsl)
  - [Running fault simulation](#running-fault-simulation)
  - [Reports](#reports)
- [AI Test Generation](#ai-test-generation)
  - [Generation loop](#generation-loop)
  - [CLI usage](#cli-usage)
- [Installation](#installation)
- [CI/CD](#cicd)
- [Architecture](#architecture)
- [Troubleshooting](#troubleshooting)

---

## How it works

### Fault simulation alone

You write tests normally. Antigen intercepts HTTP responses through AspectJ bytecode weaving — no changes to your test code needed. When `-DrunWithAntigen=true`, after each test passes its baseline run, Antigen replays it against mutated responses and records whether the test catches each violation.

```
for each test that exercises endpoint E:
    baseline = run test, capture response
    for each contract fault (null_field, missing_field, ...):
        for each field in response:
            inject fault → re-run test → record caught / escaped
    for each invariant defined on E:
        generate value that violates the constraint
        re-run test → record caught / escaped
```

Tests that pass despite injected faults reveal assertion gaps.

### AI generation + simulation

```
for attempt in 1..max_retries:
    1. Claude generates test suite from OpenAPI spec
    2. Build — fix compilation errors with Claude, retry
    3. Run tests — fix test failures with Claude, retry
    4. Run tests with fault simulation
       → if all faults caught: done
       → if faults escaped: feed report back to Claude, retry
```

The generation loop does not stop when tests pass. It stops when tests pass *and* catch all simulated faults.

---

## Fault Simulation

### What gets mutated

**Contract faults** — structural mutations applied to every field in the response:

| Fault type | Mutation | Catches |
|---|---|---|
| `null_field` | Set field to `null` | `assertNotNull`, null checks |
| `missing_field` | Remove field from JSON | field existence checks |
| `empty_list` | Replace array with `[]` | size assertions |
| `empty_string` | Replace string with `""` | non-empty checks |

**Invariant faults** — semantic mutations derived from your invariant definitions:

| Invariant | Generated mutation |
|---|---|
| `price > 0` | inject `0`, `-1` |
| `status in [PENDING, FILLED]` | inject `"DELETED"`, `""` |
| `created_at <= updated_at` | inject `updated_at` one second before `created_at` |
| `filled_order_has_filled_at` (conditional) | inject `filled_at: null` when `status == FILLED` |

The invariant defines the semantic boundary. The mutation crosses it. This is the PBT falsification idea applied to API response data rather than function inputs.

---

### Configuration

All configuration lives in `src/test/resources/antigen/`:

```
src/test/resources/antigen/
├── contract.yml                        # fault types, exclusions, simulation settings
├── antigen.properties                  # API key (optional, for cloud config)
├── coverage_config.yml                 # coverage tracking (optional)
└── features/                           # invariants, one file per domain
    ├── orders.yml
    ├── accounts.yml
    └── auth.yml
```

#### contract.yml

```yaml
version: "1.0"

settings:
  default_quantifier: all       # for array fields: all | any | none
  stop_on_first_catch: true     # skip further simulation once any test catches a fault

contract:
  null_field:
    enabled: true
  missing_field:
    enabled: true
  empty_list:
    enabled: false
  empty_string:
    enabled: false

exclusions:
  urls:
    - '*/health*'
    - '*/actuator/*'
  tests:
    - '*SmokeTest*'

simulation:
  only_success_responses: true
  skip_collections_response: true
  min_response_fields: 1
  skip_if_contains_fields:
    - error
    - message
```

#### Feature files

Feature files define invariants (business rules) grouped by domain. Antigen loads all `.yml` files from `antigen/features/` automatically.

```yaml
# src/test/resources/antigen/features/orders.yml

feature: Order Lifecycle
description: >
  Status transitions, price constraints, and temporal ordering.

invariants:
  /api/v1/orders/{id}:
    GET:
      invariants:
        - name: positive_quantity
          field: quantity
          greater_than: 0

        - name: valid_status
          field: status
          in: [PENDING, FILLED, REJECTED, CANCELLED]

        - name: filled_order_has_timestamp
          if:
            field: status
            equals: FILLED
          then:
            field: filled_at
            is_not_null: true

        - name: created_before_filled
          if:
            field: filled_at
            is_not_null: true
          then:
            field: created_at
            less_than_or_equal: $.filled_at

  /api/v1/orders:
    GET:
      invariants:
        - name: all_orders_positive_quantity
          field: $[*].quantity
          greater_than: 0

    POST:
      invariants:
        - name: new_order_valid_status
          field: status
          in: [PENDING, FILLED, REJECTED]

# Which tests exercise these endpoints.
# Antigen re-runs these during simulation.
tests:
  - class: com.example.OrdersApiTest
    methods:
      - testCreateBuyOrder
      - testGetOrder
      - testListMyOrders
```

Feature files are additive — a test class can match multiple features, and all matching invariants are merged.

#### antigen.properties

Required only when using the cloud API for fault strategies.

```properties
# src/test/resources/antigen/antigen.properties
antigen.api.key=ant_proj_xxxxxxxxxxxxx
antigen.project.id=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
antigen.api.url=http://localhost:8080

# Force local mode even if API key is present
io.antigen.core.config.source=local
```

Config source priority: system property `io.antigen.core.config.source` → env var `ANTIGEN_CONFIG_SOURCE` → `antigen.properties` → auto-detect.

#### coverage_config.yml

```yaml
# src/test/resources/antigen/coverage_config.yml
coverage:
  enabled: true
  output_file: schema_coverage.json
  urls:
    - http://localhost:8080   # empty = track all URLs
  include_request_body: true
  include_response_body: false
  aggregate_by_pattern: true
  gap_analysis:
    enabled: true
    openapi_spec_path: api-spec.yaml
    output_file: gap_analysis.json
```

---

### Invariants DSL

#### Operators

| Operator | Description | Example |
|---|---|---|
| `equals` | Exact match | `equals: "ACTIVE"` |
| `not_equals` | Not equal | `not_equals: "DELETED"` |
| `greater_than` | Numeric `>` | `greater_than: 0` |
| `greater_than_or_equal` | Numeric `>=` | `greater_than_or_equal: 0` |
| `less_than` | Numeric `<` | `less_than: 100` |
| `less_than_or_equal` | Numeric `<=`, or cross-field | `less_than_or_equal: $.updated_at` |
| `in` | Value in set | `in: [BUY, SELL]` |
| `not_in` | Value not in set | `not_in: [DELETED, ARCHIVED]` |
| `is_null` | Must be null | `is_null: true` |
| `is_not_null` | Must not be null | `is_not_null: true` |
| `is_empty` | Must be empty | `is_empty: true` |
| `is_not_empty` | Must not be empty | `is_not_empty: true` |

#### Cross-field references

```yaml
- name: created_before_updated
  field: created_at
  less_than_or_equal: $.updated_at
```

#### Array fields

```yaml
- name: all_prices_positive
  field: $[*].price
  greater_than: 0
```

`default_quantifier` controls evaluation: `all` (default), `any`, or `none`.

#### Conditional invariants

```yaml
- name: shipped_order_has_tracking
  if:
    field: status
    equals: SHIPPED
  then:
    field: tracking_number
    is_not_empty: true
```

The `if` precondition is evaluated first; the `then` clause is only checked (and mutated) when the precondition holds.

#### Per-test overrides

Place a `.antigen.yml` file alongside your test class to override settings for that class or specific methods:

```yaml
# src/test/resources/antigen/com.example.OrdersApiTest.antigen.yml
version: "1.0"

settings:
  stop_on_first_catch: false

contract:
  null_field:
    enabled: false      # disable for this class

tests:
  testGetOrder:
    contract:
      missing_field:
        enabled: true   # re-enable for this method only
    endpoints:
      /api/v1/orders/{id}:
        GET:
          invariants:
            - name: local_only_check
              field: internal_id
              greater_than: 0
```

---

### Running fault simulation

```bash
# normal test run — no simulation
./gradlew test

# with fault simulation
./gradlew test -DrunWithAntigen=true

# specific test classes
./gradlew test --tests "com.example.*" -DrunWithAntigen=true
```

The AspectJ agent is attached automatically when `-DrunWithAntigen=true` (see [Installation](#installation)).

---

### Reports

After a simulation run, three reports are written to the project root:

**`antigen_report.html`** — interactive browser report with tabs:
- **Summary** — overall detection rate, escaped vs caught counts
- **Fault Simulation** — per-endpoint breakdown of contract and invariant faults
- **Test Matrix** — 2D grid of tests × faults
- **Coverage** — endpoint coverage with HTTP call logs
- **Gap Analysis** — OpenAPI spec endpoints not covered by any test

**`fault_simulation_report.json`**:

```json
{
  "/api/v1/orders/{id}": {
    "contractFaultCount": 8,
    "contractFaultsCaught": 6,
    "invariantFaultCount": 4,
    "invariantFaultsCaught": 3,
    "contract_faults": {
      "null_field": {
        "status": {
          "caught_by_any_test": true,
          "tested_by": ["OrdersApiTest.testGetOrder"],
          "caught_by": [{ "test": "OrdersApiTest.testGetOrder", "caught": true }]
        }
      }
    },
    "invariant_faults": {
      "filled_order_has_timestamp": {
        "caught_by_any_test": false,
        "tested_by": ["OrdersApiTest.testGetOrder"],
        "caught_by": []
      }
    }
  }
}
```

`caught_by_any_test: false` is a test quality gap — no test detected that violation.

**Console summary** printed after all simulations complete:

```
============================================================
  Antigen -- Simulation Run Summary
============================================================
  Test                          Caught   Total   Escaped
------------------------------------------------------------
  OrdersApiTest.testGetOrder      8        12      4
  AuthApiTest.testLogin           3         3      0
------------------------------------------------------------
  TOTAL                          11        15      4
============================================================
```

---

## AI Test Generation

### Generation loop

Requires [Claude Code](https://claude.ai/code) (`claude` CLI) on PATH.

```
antigen generate --spec openapi.yaml --project ./my-project
```

Internally:

```
attempt 1..max-retries:
  → Claude generates tests in generated/ package
  → ./gradlew clean compileTestJava
       failure → send compilation errors to Claude, retry
  → ./gradlew test --tests "generated.*"
       failure → send test failures to Claude, retry
  → ./gradlew test --tests "generated.*" -DrunWithAntigen=true
       all faults caught → done
       faults escaped    → send fault_simulation_report.json to Claude, retry
```

Claude reads the fault report and adds or strengthens assertions for each escaped fault. The loop terminates when the generated tests pass and catch all simulated faults, or when `--max-retries` is exhausted.

### CLI usage

```
antigen generate \
  --spec path/to/openapi.yaml \
  --project path/to/java-project \
  [--requirements "must test pagination" --requirements "cover 401 responses"] \
  [--requirements-file requirements.json] \
  [--max-retries 5] \
  [--timeout-antigen 30] \
  [--timeout-build 5] \
  [--timeout-test 10] \
  [--verbose]

antigen version
```

The target project must already have Antigen on its test classpath and `antigen/contract.yml` configured. Generated tests are written to `src/test/java/generated/`.

**Exit codes:** `0` = all faults caught, `1` = faults escaped or max retries reached.

---

## Installation

### 1. Add repository

**settings.gradle.kts:**
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Add dependency

**build.gradle.kts:**
```kotlin
dependencies {
    testImplementation("com.github.your-org:antigen:1.0.0-SNAPSHOT")

    // required
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.rest-assured:rest-assured:5.5.0")
}
```

### 3. Configure the AspectJ agent

**build.gradle.kts:**
```kotlin
tasks.test {
    useJUnitPlatform()

    doFirst {
        val runWithAntigen = System.getProperty("runWithAntigen") == "true"
        jvmArgs("-DrunWithAntigen=$runWithAntigen")

        if (runWithAntigen) {
            val agent = configurations.runtimeClasspath.get()
                .find { it.name.contains("aspectjweaver") }?.absolutePath
            if (agent != null) {
                jvmArgs("-javaagent:$agent")
            }
        }
    }
}
```

### 4. Create configuration

**`src/test/resources/antigen/contract.yml`:**
```yaml
version: "1.0"

settings:
  default_quantifier: all
  stop_on_first_catch: true

contract:
  null_field:
    enabled: true
  missing_field:
    enabled: true
```

**`src/test/resources/antigen/features/my-api.yml`:**
```yaml
feature: My API

invariants:
  /api/users/{id}:
    GET:
      invariants:
        - name: user_has_email
          field: email
          is_not_empty: true

        - name: valid_status
          field: status
          in: [ACTIVE, SUSPENDED, PENDING]

tests:
  - class: com.example.UserApiTest
    methods:
      - testGetUser
```

---

## CI/CD

```yaml
# .github/workflows/antigen.yml
name: Antigen CI

on: [push, pull_request]

jobs:
  unit_tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '18', distribution: 'temurin' }
      - uses: gradle/actions/setup-gradle@v4
      - run: chmod +x gradlew
      - run: ./gradlew test --tests "com.example.unit.*" -DrunWithAntigen=false

  integration_tests:
    needs: unit_tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '18', distribution: 'temurin' }
      - uses: gradle/actions/setup-gradle@v4
      - run: chmod +x gradlew
      - run: ./gradlew test --tests "com.example.integration.*" -DrunWithAntigen=true
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: antigen-reports
          path: |
            fault_simulation_report.json
            antigen_report.html
            schema_coverage.json
```

---

## Architecture

```
src/main/java/io/antigen/
├── core/
│   ├── interceptor/       AspectJ weaving — @Test and HTTP client interception
│   ├── config/            Configuration loading and merging
│   │   ├── LocalConfigurationSource    reads antigen/contract.yml
│   │   ├── ApiConfigurationSource      fetches from cloud API
│   │   ├── FeatureConfigScanner        loads antigen/features/*.yml
│   │   ├── ConfigResolver              merges global + feature + per-class + per-method
│   │   └── TestScopedConfigLoader      loads <ClassName>.antigen.yml
│   ├── injection/         Fault injection strategies (null, missing, empty)
│   ├── invariant/         Invariant evaluation and violation generation
│   ├── simulation/        Simulation runner and report aggregation
│   ├── coverage/          Endpoint coverage tracking
│   ├── analytics/         Gap analysis against OpenAPI spec
│   ├── report/            HTML report generation
│   └── api/               Cloud API client
└── ai/
    ├── Antigen.java        PicoCLI entry point
    ├── orchestrator/       Generation loop (Orchestrator, AntigenConfig)
    ├── llm/                Claude invocation (ClaudeGenerator, PromptBuilder)
    ├── runners/            Gradle subprocess execution
    ├── phases/             Phase result types (BuildPhase, TestPhase, AntigenPhase)
    ├── feedback/           Error parsing for Claude feedback
    ├── model/              Domain types (EscapedFault, GenerationResult)
    └── config/             YAML config for the CLI (antigen.yml)
```

### AspectJ interception

Antigen weaves two pointcuts at load time:

- `@Around("execution(@org.junit.jupiter.api.Test * *(..))")` — wraps each `@Test` method to establish context and trigger simulation after baseline passes
- `@Around("execution(* org.apache.http.impl.client.CloseableHttpClient.execute(..))")` — intercepts Apache HttpClient to capture and replay requests with mutated responses

Weaving requires `-javaagent:aspectjweaver.jar` and `src/main/resources/META-INF/aop.xml` on the classpath.

---

## Performance

Simulation time scales with: `tests × response fields × enabled fault types × invariants per endpoint`.

Practical controls:
- `stop_on_first_catch: true` — skip a fault once any test catches it (faster, less detail)
- `simulation.only_success_responses: true` — skip error responses
- `simulation.skip_collections_response: true` — skip array responses (invariant simulation on arrays is typically not useful)
- `simulation.min_response_fields: N` — skip sparse responses
- `exclusions.tests` — exclude slow or noisy test classes

---

## Troubleshooting

**No simulation output**

Verify `-DrunWithAntigen=true` is passed and the AspectJ agent attached. Look for `[Antigen] Fault simulation enabled — agent: ...` in Gradle output.

**`No contract.yml found`**

File must be at `src/test/resources/antigen/contract.yml`.

**Feature invariants not appearing in report**

Check that `tests[].class` in the feature file matches the fully-qualified class name (`com.example.OrdersApiTest`, not `OrdersApiTest`).

**`ConnectException` to `localhost:8080` on startup**

An API key is present in `antigen.properties` and the config source auto-detected to API mode. Add `io.antigen.core.config.source=local` to force local mode.

**`advice defined in AspectExecutor has not been applied`**

This warning appears during compile-time weaving when no test classes are being woven at that point (they're woven at load time instead). It is not an error.

---

## Requirements

- Java 17+
- Gradle 7.3+
- JUnit 5 (Jupiter)
- Apache HttpClient (via RestAssured or direct)
- Claude Code CLI on PATH (AI generation only)

---

## Building from source

```bash
git clone https://github.com/your-org/antigen.git
cd antigen
./gradlew build
./gradlew publishToMavenLocal

# run unit tests only
./gradlew test --tests "io.antigen.core.unit.*" -DrunWithAntigen=false

# run integration tests with simulation
./gradlew test --tests "io.antigen.core.integration.*" -DrunWithAntigen=true
```
