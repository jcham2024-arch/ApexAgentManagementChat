# engine-cli

**Tier:** 1 · **Purpose:** Task-file loader, ReAct runner, and CLI entrypoint.

## Allowed dependencies
java.base, org.yaml:snakeyaml, jspecify, and the three Tier 1 adapters it
wires: engine-llm-http, engine-tools-impl, engine-store-sqlite.

## Ports consumed
`ChatModelPort` (engine-llm-http) · `ToolExecutorPort` (engine-tools-impl) ·
`StoragePort` (engine-store-sqlite) — wired only in `--real` mode.

## Ports implemented
none

## Usage
```
mvn -q -pl engine-cli package
java -cp engine-cli/target/engine-cli-0.1.0-SNAPSHOT.jar:<deps> \
  com.apex.engine.cli.Main [--real] [--workspace <path>] path/to/task.yaml
```

- Default mode loads and validates the task, then runs `StubTaskRunner`.
- `--real` runs the ReAct loop through the real adapters. It requires a
  non-empty `model_route` and provider configuration in the environment.
- `--workspace` sets the jail root, the tool base, and the SQLite
  location (`.apex/steps.db`). Defaults to the current directory.
- Exit codes: 0 success, 1 load or run failure, 2 usage error. Only the
  JSON result goes to stdout; usage and errors go to stderr.

## Tenant

Pass `--tenant <id>` or set `APEX_TENANT_ID`. Defaults to
`cli-default`. Every `StepLogEntry` written by the runner carries this
tenant, so cross-tenant queries cannot surface this run's steps.

## Audit

Every tool call is recorded in a hash-chained audit log. Production
deployments MUST set `APEX_AUDIT_SECRET` to a value at least 32 bytes
long. Without it, the CLI uses a hardcoded development key — the chain
verifies, but against a public secret, so tampering cannot be
detected. The audit log is in-memory only in S3.3; file persistence
lands in S6.

## Task file schema (YAML)

| Field | Type | Required | Notes |
|---|---|---|---|
| `task_id` | string | yes | identifier |
| `description` | string | yes | one line |
| `instructions` | string | yes | multi-line allowed |
| `max_steps` | int | yes | > 0 |
| `model_route` | string | no | empty → provider default (stub mode ignores it) |
| `tools` | list of string | yes | non-empty |
| `inputs.read_first` | list of string | no | paths to read before starting |
| `outputs.files` | list of string | no | files the task must produce |
| `outputs.assertions` | list of string | no | checks before success |

Supported assertion prefixes in S2.7: `file_exists:<path>` and
`file_contains:<path>:<substring>`. Any other prefix warns instead of
failing; S3 hardens this into a real assertion interpreter.

Example:
```yaml
task_id: bootstrap-verify
description: Verify the kernel boots and completes a trivial task
max_steps: 20
model_route: ""
tools: [read_file, write_file, list_files, run_command]
inputs:
  read_first: [ARCHITECTURE.md]
instructions: |
  Read ARCHITECTURE.md, summarize it in 100 words, write the summary
  to BOOTSTRAP_REPORT.md.
outputs:
  files: [BOOTSTRAP_REPORT.md]
  assertions: ["file_exists:BOOTSTRAP_REPORT.md"]
```

## Tests
`TaskLoaderTest` · `TaskLoaderValidationTest` · `TaskRunnerTest` ·
`MainTest` · `DecisionTest` · `LlmResponseParserTest` ·
`PromptBuilderTest` · `AssertionCheckerTest` · `RealTaskRunnerTest` ·
`E2ETest`
