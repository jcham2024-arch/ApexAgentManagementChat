# PHASE-S2.6-CLI-SESSION.md — Session Package, Part 1 of 6

**Version:** 1.0 · **Sub-phase of S2** · **Part 1 of 6:** reading protocol, context deltas, prerequisite, scope, pom change.
Parts: SESSION · TASK-TYPES · FIELDS · RUNNER-MAIN · METADATA ·
TESTS-CRITERIA, each `PHASE-S2.6-CLI-<part>.md` (`AGENT_RULES.md` §2.13).

---

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S2-KERNEL.md` (repo — router)
4. **This file**

## 1. Context notes — deltas for S2.6

Authoritative where they conflict with any prior file. Do not stop over
them.

### 1.1 Spec not required

Router §4 says S2.6 does not need the spec. Holds. The CLI consumes
`TaskDefinition` (defined here) and stubs the runner. Real runner
wiring that touches the ports lands in S2.7 E2E.

### 1.2 SnakeYAML **is** added in this phase

Task files are YAML: they carry multi-line `instructions` and nested
`inputs`/`outputs` blocks that are unreadable in JSON. `engine-cli` is
Tier 1 and SnakeYAML is Apache 2.0, so the Enforcer permits it. Add
`org.yaml:snakeyaml` to `engine-cli/pom.xml`.

**Use the safe constructor.** `new Yaml(new SafeConstructor(new
LoaderOptions()))`. Do not use `new Yaml()`. Task files will eventually
be produced by the flywheel (S5) and are therefore untrusted input;
`safe` mode forbids arbitrary class instantiation.

### 1.3 `engine-cli` gains an unused `engine-core` reference for now

`engine-cli` already depends on `engine-core` (S2.0 §1.7). S2.6 does
not use any `engine-core` types — the stub runner avoids them. That is
intentional. S2.7 replaces the stub with a real runner that consumes
the ports. The dependency stays declared; no imports yet.

### 1.4 Exit codes

`Main` returns:
- `0` — task loaded, ran, result printed to stdout
- `1` — task load failed, or runner threw
- `2` — usage error (no args, extra args)

Usage and errors go to **stderr**. Only the JSON result goes to stdout.

### 1.5 `Main` is testable without `System.exit`

`Main.main(String[])` delegates to `Main.run(String[], PrintStream out,
PrintStream err) -> int`. Tests call `run()` directly with
`ByteArrayOutputStream`-backed streams. `main()` calls
`System.exit(run(...))`.

### 1.6 The stub runner is intentional

`StubTaskRunner` returns a fixed status without calling any port. It
proves the CLI wiring (load → run → print) works end to end. The real
runner, which calls `PlannerPort`, `ChatModelPort`, `ToolExecutorPort`,
and `StoragePort`, is deferred to S2.7 where the E2E test wires them
together. Do not remove the stub; replace it in S2.7.

## 2. Prerequisite

S2.5 merged to `main`. `mvn -B test` green on `main`. If not, stop and
report.

## 3. Scope

Deliver:

1. One change to `engine-cli/pom.xml` (add SnakeYAML)
2. Eight source files in `engine-cli`
3. Three test classes
4. Updated `package-info.java` and `README.md`
5. `docs/INVARIANTS.md`: no changes (S2.6 proves nothing named)

## 4. Files

### 4.1 `engine-cli/pom.xml` — add SnakeYAML

Insert inside `<dependencies>`, after the `engine-core` dependency:

```xml
<dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
    <version>2.3</version>
</dependency>
```

If `mvn -B dependency:resolve` fails on that version, use the latest
2.x release and note the version in the commit body. Do not add
snakeyaml to any other module.
