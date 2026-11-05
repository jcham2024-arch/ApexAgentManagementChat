package com.apex.engine.cli;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.ApprovalPort;
import com.apex.engine.core.port.AuditPort;
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

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
            "usage: apex-cli [--real] [--auto-approve] "
            + "[--workspace <path>] [--tenant <id>] <task-file>\n";

    /**
     * Development-only audit key. Production MUST set APEX_AUDIT_SECRET.
     * This 32-byte array is public and provides no tamper-evidence against
     * an attacker with read access to the source.
     */
    private static final byte[] DEV_AUDIT_KEY = (
            "dev-only-do-not-use-in-production!!").getBytes(StandardCharsets.UTF_8);

    private Main() {}

    public static void main(String[] args) {
        int code = run(args, System.getenv(), System.in,
                System.out, System.err);
        System.exit(code);
    }

    static int run(String[] args, Map<String, String> env,
                   InputStream in, PrintStream out, PrintStream err) {
        CliArgs parsed;
        try {
            parsed = CliArgs.parse(args);
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

        TenantId tenant = parsed.tenant() != null
                ? parsed.tenant()
                : new TenantId(env.getOrDefault("APEX_TENANT_ID", "cli-default"));

        ApprovalPort approval = parsed.autoApprove()
                ? new AutoApprove()
                : new TerminalApproval(in, err);

        TaskRunner runner;
        try {
            runner = parsed.real()
                    ? buildRealRunner(task, env, parsed.workspace(), tenant,
                            approval, err)
                    : new StubTaskRunner();
        } catch (TaskLoadException e) {
            err.println("build failed [" + e.category() + "]: "
                    + e.getMessage());
            return 1;
        }

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

    private static Map<String, RiskLevel> defaultRiskRegistry() {
        return Map.of(
                "read_file",   RiskLevel.L0,
                "list_files",  RiskLevel.L0,
                "write_file",  RiskLevel.L1,
                "http_get",    RiskLevel.L2,
                "run_command", RiskLevel.L4);
    }

    private static TaskRunner buildRealRunner(TaskDefinition task,
                                              Map<String, String> env,
                                              Path workspace,
                                              TenantId tenant,
                                              ApprovalPort approval,
                                              PrintStream err) {
        if (task.modelRoute().isBlank()) {
            err.println("real mode requires model_route in the task file");
            throw new TaskLoadException("model_route", "real mode requires model_route in the task file");
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
            byte[] auditKey = env.containsKey("APEX_AUDIT_SECRET")
                    ? env.get("APEX_AUDIT_SECRET").getBytes(StandardCharsets.UTF_8)
                    : DEV_AUDIT_KEY;
            AuditPort audit = new InMemoryAuditLog(auditKey);
            Map<String, RiskLevel> risks = defaultRiskRegistry();
            return new RealTaskRunner(model, tools, storage, audit, approval,
                    risks, tenant, workspace);
        } catch (Exception e) {
            err.println("adapter init failed: " + e.getMessage());
            throw new TaskLoadException("adapter_init", "adapter init failed: " + e.getMessage());
        }
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
}
