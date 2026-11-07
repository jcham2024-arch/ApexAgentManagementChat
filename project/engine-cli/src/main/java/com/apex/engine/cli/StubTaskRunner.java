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
