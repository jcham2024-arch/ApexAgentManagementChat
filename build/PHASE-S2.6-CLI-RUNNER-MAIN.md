# PHASE-S2.6-CLI-RUNNER-MAIN.md — Session Package, Part 4 of 6

**Version:** 1.0 · **Sub-phase of S2** · **Part 4 of 6:** §4.6–§4.9, result, runner interface, stub, Main.
Parts: SESSION · TASK-TYPES · FIELDS · RUNNER-MAIN · METADATA ·
TESTS-CRITERIA, each `PHASE-S2.6-CLI-<part>.md` (`AGENT_RULES.md` §2.13).

---
### 4.6 `TaskRunResult.java`

```java
package com.apex.engine.cli;

import java.util.Objects;

/**
 * Terminal result of a task run. Serializes to JSON via
 * {@link #toJson()} for CLI output.
 */
public record TaskRunResult(String taskId, String status, int steps,
                            long durationMs) {
    public TaskRunResult {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(status, "status");
        if (steps < 0) throw new IllegalArgumentException("steps < 0");
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs < 0");
        }
    }

    public String toJson() {
        return "{\"task_id\":\"" + escape(taskId)
                + "\",\"status\":\"" + escape(status)
                + "\",\"steps\":" + steps
                + ",\"duration_ms\":" + durationMs + "}";
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.toString();
    }
}
```

### 4.7 `TaskRunner.java`

```java
package com.apex.engine.cli;

/**
 * Executes a {@link TaskDefinition} and returns its terminal result.
 * Implementations may perform I/O, call the model, run tools, and
 * persist steps. Implementations must not throw checked exceptions —
 * runtime failures are reported as a {@link TaskRunResult} with a
 * non-COMPLETED status, or via a runtime exception the CLI catches.
 */
public interface TaskRunner {
    TaskRunResult run(TaskDefinition task);
}
```

### 4.8 `StubTaskRunner.java`

```java
package com.apex.engine.cli;

/**
 * Minimal {@link TaskRunner} that returns a fixed result without
 * touching any port. Proves the CLI's load-run-print flow works.
 * Replaced by the real runner in S2.7 E2E.
 */
public final class StubTaskRunner implements TaskRunner {

    @Override
    public TaskRunResult run(TaskDefinition task) {
        return new TaskRunResult(task.taskId(), "STUB", 0, 0L);
    }
}
```

### 4.9 `Main.java`

```java
package com.apex.engine.cli;

import java.io.PrintStream;
import java.nio.file.Path;

/**
 * CLI entrypoint. Usage: {@code java -cp <classpath>
 * com.apex.engine.cli.Main <task-file>}. Loads the task, runs it via
 * {@link StubTaskRunner}, prints the JSON result to stdout. Exit codes:
 * 0 success, 1 task failure, 2 usage error.
 */
public final class Main {

    private static final String USAGE =
            "usage: apex-cli <task-file>\n";

    private Main() {}

    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        System.exit(code);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length != 1) {
            err.print(USAGE);
            return 2;
        }
        Path taskFile = Path.of(args[0]);

        TaskDefinition task;
        try {
            task = new TaskLoader().load(taskFile);
        } catch (TaskLoadException e) {
            err.println("load failed [" + e.category() + "]: "
                    + e.getMessage());
            return 1;
        }

        TaskRunner runner = new StubTaskRunner();
        TaskRunResult result;
        try {
            result = runner.run(task);
        } catch (RuntimeException e) {
            err.println("run failed: " + e.getMessage());
            return 1;
        }

        out.println(result.toJson());
        return 0;
    }
}
```
