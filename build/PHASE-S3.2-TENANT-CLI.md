# PHASE-S3.2-TENANT-CLI.md — Session Package, Part 5 of 7

**Version:** 1.0 · **Part 5 of 7:** §4.6–§4.8, Main --tenant, cli pom, README.
Parts: SESSION · CORE-PORT · SQLITE · POMS-RUNNER · CLI · TESTS ·
ISOLATION-CRITERIA, each `PHASE-S3.2-TENANT-<part>.md` (`AGENT_RULES.md`
§2.13).

---
### 4.6 `engine-cli/src/main/java/com/apex/engine/cli/Main.java`

Three changes to the existing file. Do not replace the whole file — make these edits.

**Edit 1:** Add to the imports block:

```java
import com.apex.chat.common.identity.TenantId;
```

**Edit 2:** In the `Args` record and its `parse` method, add a `tenant` field:

```java
record Args(boolean real, Path workspace, TenantId tenant, Path taskFile) {
    static Args parse(String[] args) {
        boolean real = false;
        Path workspace = Path.of(".").toAbsolutePath().normalize();
        String tenantStr = null;
        List<String> positional = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--real")) { real = true; }
            else if (a.equals("--workspace")) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--workspace needs a path");
                }
                workspace = Path.of(args[++i]).toAbsolutePath().normalize();
            } else if (a.equals("--tenant")) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--tenant needs an id");
                }
                tenantStr = args[++i];
            } else if (a.startsWith("--")) {
                throw new IllegalArgumentException("unknown flag: " + a);
            } else {
                positional.add(a);
            }
        }
        if (positional.size() != 1) {
            throw new IllegalArgumentException(
                    "expected 1 task file, got " + positional.size());
        }
        TenantId tenant = tenantStr != null
                ? new TenantId(tenantStr)
                : null;
        return new Args(real, workspace, tenant,
                Path.of(positional.get(0)));
    }
}
```

**Edit 3:** In `run(...)`, after `Args parsed = Args.parse(args);`, resolve the tenant from `parsed.tenant()` or env, then pass it to `buildRealRunner`:

```java
TenantId tenant = parsed.tenant() != null
        ? parsed.tenant()
        : new TenantId(env.getOrDefault("APEX_TENANT_ID", "cli-default"));
```

And in `buildRealRunner`, add a `TenantId tenant` parameter and pass it to `RealTaskRunner`:

```java
return new RealTaskRunner(model, tools, storage, tenant, workspace);
```

The `buildRealRunner` signature becomes:

```java
private static TaskRunner buildRealRunner(TaskDefinition task,
                                          Map<String, String> env,
                                          Path workspace,
                                          TenantId tenant,
                                          PrintStream err) {
```

And the call site passes `tenant` as the fourth argument.

### 4.7 `engine-cli/pom.xml` — add `apex-chat-common`

Insert inside `<dependencies>`, after the `engine-core` dependency:

```xml
<dependency>
    <groupId>com.apex</groupId>
    <artifactId>apex-chat-common</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 4.8 `engine-cli/README.md`

Add a new section after "## Usage":

```markdown
## Tenant

Pass `--tenant <id>` or set `APEX_TENANT_ID`. Defaults to
`cli-default`. Every `StepLogEntry` written by the runner carries this
tenant, so cross-tenant queries cannot surface this run's steps.
```
