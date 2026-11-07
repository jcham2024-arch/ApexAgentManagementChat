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
