# engine-cli

**Tier:** 1 · **Purpose:** Task-file loader and CLI entrypoint for the Apex kernel.

## Allowed dependencies
java.base, org.yaml:snakeyaml, jspecify

## Ports consumed
none (S2.6) — real runner wiring lands in S2.7

## Ports implemented
none

## Usage
```
mvn -q -pl engine-cli package
java -cp engine-cli/target/engine-cli-0.1.0-SNAPSHOT.jar:<deps> \
  com.apex.engine.cli.Main path/to/task.yaml
```

## Task file schema (YAML)

| Field | Type | Required | Notes |
|---|---|---|---|
| `task_id` | string | yes | identifier |
| `description` | string | yes | one line |
| `instructions` | string | yes | multi-line allowed |
| `max_steps` | int | yes | > 0 |
| `model_route` | string | no | empty → provider default |
| `tools` | list of string | yes | non-empty |
| `inputs.read_first` | list of string | no | paths to read before starting |
| `outputs.files` | list of string | no | files the task must produce |
| `outputs.assertions` | list of string | no | checks before success |

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
  assertions: ["BOOTSTRAP_REPORT.md exists"]
```

## Tests
`TaskLoaderTest` · `TaskRunnerTest` · `MainTest`
