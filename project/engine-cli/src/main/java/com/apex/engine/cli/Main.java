package com.apex.engine.cli;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.ApprovalPort;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;

/**
 * CLI entrypoint. Stub-only mode: load + validate + stub run.
 *
 * Flags: {@code --auto-approve}, {@code --workspace <path>},
 * {@code --tenant <id>} <task-file>.
 */
public final class Main {

    private static final String USAGE =
            "usage: apex-cli [--auto-approve] "
            + "[--workspace <path>] [--tenant <id>] <task-file>\n";

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

        TaskRunner runner = new StubTaskRunner();

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
}
