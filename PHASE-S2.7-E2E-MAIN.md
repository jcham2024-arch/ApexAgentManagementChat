# PHASE-S2.7-E2E-MAIN.md — Session Package, Part 5 of 6

**Version:** 1.0 · **Part 5 of 6:** §4.6, Main with --real and --workspace.
Parts: SESSION · DECISION-PROMPT · ASSERTIONS · RUNNER · MAIN ·
TESTS-CRITERIA, each `PHASE-S2.7-E2E-<part>.md` (`AGENT_RULES.md` §2.13).

---
### 4.6 Update `Main.java`

Replace the entire file with:

```java
package com.apex.engine.cli;

import com.apex.engine.core.port.ChatModelPort;
import com.apex.engine.core.port.StoragePort;
import com.apex.engine.core.port.ToolExecutorPort;
import com.apex.engine.llm.http.OpenAiCompatibleProvider;
import com.apex.engine.llm.http.ProviderConfig;
import com.apex.engine.llm.http.ProviderConfigLoader;
import com.apex.engine.store.sqlite.SqliteStoragePort;
import com.apex.engine.tools.impl.KernelToolExecutor;
import com.apex.engine.tools.impl.RealPathJail;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CLI entrypoint. Modes:
 * <ul>
 *   <li>Default (no flags): load + validate + stub run.</li>
 *   <li>{@code --real}: load + validate + real ReAct run through all
 *       four Tier 1 adapters.</li>
 * </ul>
 *
 * Flags: {@code --real}, {@code --workspace <path>}.
 */
public final class Main {

    private static final String USAGE =
            "usage: apex-cli [--real] [--workspace <path>] <task-file>\n";

    private Main() {}

    public static void main(String[] args) {
        int code = run(args, System.getenv(), System.out, System.err);
        System.exit(code);
    }

    static int run(String[] args, Map<String, String> env,
                   PrintStream out, PrintStream err) {
        Args parsed;
        try {
            parsed = Args.parse(args);
        } catch (IllegalArgumentException e) {
            err.print(USAGE);
            err.println(e.getMessage());
            return 2;
        }

        TaskDefinition task;
        try {
            task = new TaskLoader().load(parsed.taskFile());
        } catch (TaskLoadException e) {
            err.println("load failed [" + e.category() + "]: " + e.getMessage());
            return 1;
        }

        TaskRunner runner = parsed.real()
                ? buildRealRunner(task, env, parsed.workspace(), err)
                : new StubTaskRunner();

        if (runner == null) return 1;

        TaskRunResult result;
        try {
            result = runner.run(task);
        } catch (RuntimeException e) {
            err.println("run failed: " + e.getMessage());
            return 1;
        }

        out.println(result.toJson());
        return result.status().equals("COMPLETED")
                || result.status().equals("STUB") ? 0 : 1;
    }

    private static TaskRunner buildRealRunner(TaskDefinition task,
                                              Map<String, String> env,
                                              Path workspace,
                                              PrintStream err) {
        if (task.modelRoute().isBlank()) {
            err.println("real mode requires model_route in the task file");
            return null;
        }
        try {
            ProviderConfig cfg = ProviderConfigLoader.fromMap(env);
            ChatModelPort model = new OpenAiCompatibleProvider(cfg);
            RealPathJail jail = new RealPathJail(workspace);
            Map<String, Path> registry = buildCommandRegistry();
            ToolExecutorPort tools = new KernelToolExecutor(jail, registry);
            Files.createDirectories(workspace.resolve(".apex"));
            StoragePort storage = new SqliteStoragePort(
                    workspace.resolve(".apex/steps.db"));
            return new RealTaskRunner(model, tools, storage, workspace);
        } catch (Exception e) {
            err.println("adapter init failed: " + e.getMessage());
            return null;
        }
    }

    private static Map<String, Path> buildCommandRegistry() {
        Map<String, Path> registry = new HashMap<>();
        for (String name : new String[]{"echo", "cat", "bash", "sh"}) {
            for (String dir : new String[]{"/bin/", "/usr/bin/"}) {
                Path p = Path.of(dir + name);
                if (Files.isExecutable(p)) {
                    registry.putIfAbsent(name, p);
                    break;
                }
            }
        }
        return registry;
    }

    /** Parsed CLI arguments. */
    record Args(boolean real, Path workspace, Path taskFile) {
        static Args parse(String[] args) {
            boolean real = false;
            Path workspace = Path.of(".").toAbsolutePath().normalize();
            Path taskFile = null;
            List<String> positional = new ArrayList<>();

            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if (a.equals("--real")) { real = true; }
                else if (a.equals("--workspace")) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--workspace needs a path");
                    }
                    workspace = Path.of(args[++i]).toAbsolutePath().normalize();
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
            taskFile = Path.of(positional.get(0));
            return new Args(real, workspace, taskFile);
        }
    }
}
```

**Note:** `TaskRunResult.status()` returns a `String` in S2.6. If it
returns an enum, adapt the `run` method accordingly (compare against
`TaskRunResult.Status.COMPLETED`). Do not modify `TaskRunResult`;
adjust `Main` to match the actual return type.
