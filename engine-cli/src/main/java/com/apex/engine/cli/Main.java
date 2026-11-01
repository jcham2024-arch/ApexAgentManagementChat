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
