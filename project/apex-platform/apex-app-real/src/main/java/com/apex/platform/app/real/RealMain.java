package com.apex.platform.app.real;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.cli.AutoApprove;
import com.apex.engine.cli.RealTaskRunner;
import com.apex.engine.cli.TaskDefinition;
import com.apex.engine.cli.TaskLoader;
import com.apex.engine.cli.TaskLoadException;
import com.apex.engine.cli.TaskRunResult;
import com.apex.engine.cli.TerminalApproval;
import com.apex.engine.core.port.ApprovalPort;
import com.apex.engine.core.port.AuditPort;
import com.apex.engine.core.port.ChatEventSink;
import com.apex.engine.core.port.ChatModelPort;
import com.apex.engine.core.port.StoragePort;
import com.apex.engine.core.port.ToolExecutorPort;
import com.apex.engine.llm.http.OpenAiCompatibleProvider;
import com.apex.engine.llm.http.ProviderConfig;
import com.apex.engine.llm.http.ProviderConfigLoader;
import com.apex.engine.planner.RiskLevel;
import com.apex.engine.store.sqlite.SqliteStoragePort;
import com.apex.engine.tools.impl.KernelToolExecutor;
import com.apex.engine.tools.impl.RealPathJail;
import com.apex.platform.audit.InMemoryAuditLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Real-mode entrypoint. Wires Tier 1 adapters behind RealTaskRunner.
 * Lives in Tier 2 so engine-cli never depends on another Tier 1 module.
 * Flags: --auto-approve, --workspace <path>, --tenant <id> <task-file>.
 */
public final class RealMain {

    private static final String USAGE =
            "usage: apex-real [--auto-approve] "
            + "[--workspace <path>] [--tenant <id>] <task-file>\n";

    private RealMain() {}

    public static void main(String[] args) {
        int code = run(args, System.getenv(), System.in,
                System.out, System.err);
        System.exit(code);
    }

    static int run(String[] args, Map<String, String> env,
                   InputStream in, PrintStream out, PrintStream err) {
        CliArgs parsed;
        try {
            parsed = parseArgs(args);
        } catch (IllegalArgumentException e) {
            err.print(USAGE);
            err.println(e.getMessage());
            return 2;
        }

        TaskDefinition task;
        try {
            task = new TaskLoader().load(parsed.taskFile);
        } catch (TaskLoadException e) {
            err.println("load failed [" + e.category() + "]: " + e.getMessage());
            return 1;
        }

        TenantId tenant = parsed.tenant != null
                ? parsed.tenant
                : new TenantId(env.getOrDefault("APEX_TENANT_ID", "cli-default"));

        ApprovalPort approval = parsed.autoApprove
                ? new AutoApprove()
                : new TerminalApproval(in, err);

        RealTaskRunner runner;
        try {
            runner = buildRealRunner(task, env, parsed.workspace, tenant,
                    approval, err);
        } catch (Exception e) {
            err.println("adapter init failed: " + e.getMessage());
            return 1;
        }

        TaskRunResult result;
        try {
            result = runner.run(task);
        } catch (RuntimeException e) {
            err.println("run failed: " + e.getMessage());
            return 1;
        }

        out.println(result.toJson());
        return result.status().equals("COMPLETED") ? 0 : 1;
    }

    private static RealTaskRunner buildRealRunner(TaskDefinition task,
                                                  Map<String, String> env,
                                                  Path workspace,
                                                  TenantId tenant,
                                                  ApprovalPort approval,
                                                  PrintStream err) {
        if (task.modelRoute().isEmpty()) {
            err.println("real mode requires model_route");
            throw new IllegalArgumentException("model_route required");
        }
        ProviderConfig cfg = ProviderConfigLoader.fromMap(env);
        ChatModelPort model = new OpenAiCompatibleProvider(cfg);
        RealPathJail jail;
        try {
            jail = new RealPathJail(workspace);
            Files.createDirectories(workspace.resolve(".apex"));
        } catch (IOException e) {
            err.println("workspace setup failed: " + e.getMessage());
            throw new IllegalArgumentException("workspace setup failed", e);
        }
        Map<String, Path> registry = buildCommandRegistry();
        ToolExecutorPort tools = new KernelToolExecutor(jail, registry);
        StoragePort storage = new SqliteStoragePort(
                workspace.resolve(".apex/steps.db"));
        byte[] auditKey = requireEnv(env, "APEX_AUDIT_SECRET")
                .getBytes(StandardCharsets.UTF_8);
        AuditPort audit = new InMemoryAuditLog(auditKey);
        Map<String, RiskLevel> risks = defaultRiskRegistry();
        return new RealTaskRunner(model, tools, storage, audit, approval,
                ChatEventSink.NOOP, risks, tenant, workspace);
    }

    private static Map<String, Path> buildCommandRegistry() {
        Map<String, Path> registry = new HashMap<>();
        for (String name : new String[] {"echo", "cat", "bash", "sh"}) {
            for (String dir : new String[] {"/bin/", "/usr/bin/"}) {
                Path p = Path.of(dir + name);
                if (Files.isExecutable(p)) {
                    registry.putIfAbsent(name, p);
                    break;
                }
            }
        }
        return registry;
    }

    private static Map<String, RiskLevel> defaultRiskRegistry() {
        Map<String, RiskLevel> risks = new HashMap<>();
        risks.put("read_file", RiskLevel.L0);
        risks.put("list_files", RiskLevel.L0);
        risks.put("write_file", RiskLevel.L1);
        risks.put("http_get", RiskLevel.L2);
        risks.put("run_command", RiskLevel.L4);
        return risks;
    }

    private static String requireEnv(Map<String, String> env, String key) {
        String v = env.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("missing required env: " + key);
        }
        return v;
    }

    record CliArgs(boolean autoApprove, Path workspace, TenantId tenant,
                   Path taskFile) {}

    private static CliArgs parseArgs(String[] args) {
        boolean autoApprove = false;
        Path workspace = Path.of(".");
        TenantId tenant = null;
        Path taskFile = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--auto-approve" -> autoApprove = true;
                case "--workspace" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--workspace requires a value");
                    }
                    workspace = Path.of(args[++i]);
                }
                case "--tenant" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--tenant requires a value");
                    }
                    tenant = new TenantId(args[++i]);
                }
                default -> {
                    if (taskFile != null) {
                        throw new IllegalArgumentException(
                                "unexpected argument: " + args[i]);
                    }
                    taskFile = Path.of(args[i]);
                }
            }
        }

        if (taskFile == null) {
            throw new IllegalArgumentException("missing task file");
        }
        return new CliArgs(autoApprove, workspace, tenant, taskFile);
    }
}
