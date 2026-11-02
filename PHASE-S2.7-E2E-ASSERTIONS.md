# PHASE-S2.7-E2E-ASSERTIONS.md — Session Package, Part 3 of 6

**Version:** 1.0 · **Part 3 of 6:** §4.4, AssertionChecker.
Parts: SESSION · DECISION-PROMPT · ASSERTIONS · RUNNER · MAIN ·
TESTS-CRITERIA, each `PHASE-S2.7-E2E-<part>.md` (`AGENT_RULES.md` §2.13).

---
### 4.4 `AssertionChecker.java`

```java
package com.apex.engine.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates a task's output files and assertions after a successful
 * run. Two assertion prefixes are supported in S2.7:
 *
 * <pre>
 *   file_exists:&lt;path&gt;
 *   file_contains:&lt;path&gt;:&lt;substring&gt;
 * </pre>
 *
 * Any other prefix produces a warning, not a failure. All paths are
 * relative to the workspace root.
 */
public final class AssertionChecker {

    public sealed interface CheckResult {
        record Pass(String assertion) implements CheckResult {}
        record Fail(String assertion, String reason) implements CheckResult {}
        record Warn(String assertion, String reason) implements CheckResult {}
    }

    public List<CheckResult> check(Path workspace, TaskDefinition task) {
        List<CheckResult> results = new ArrayList<>();
        for (String f : task.outputFiles()) {
            Path p = resolve(workspace, f);
            if (Files.isRegularFile(p)) {
                results.add(new CheckResult.Pass("file_exists:" + f));
            } else {
                results.add(new CheckResult.Fail("file_exists:" + f,
                        "not a regular file: " + p));
            }
        }
        for (String a : task.assertions()) {
            results.add(checkOne(workspace, a));
        }
        return results;
    }

    public boolean allPassed(List<CheckResult> results) {
        return results.stream().noneMatch(r -> r instanceof CheckResult.Fail);
    }

    private CheckResult checkOne(Path workspace, String assertion) {
        if (assertion.startsWith("file_exists:")) {
            String path = assertion.substring("file_exists:".length());
            Path p = resolve(workspace, path);
            return Files.isRegularFile(p)
                    ? new CheckResult.Pass(assertion)
                    : new CheckResult.Fail(assertion, "not a file: " + p);
        }
        if (assertion.startsWith("file_contains:")) {
            String rest = assertion.substring("file_contains:".length());
            int colon = rest.indexOf(':');
            if (colon < 0) {
                return new CheckResult.Warn(assertion,
                        "format: file_contains:<path>:<substring>");
            }
            Path p = resolve(workspace, rest.substring(0, colon));
            String needle = rest.substring(colon + 1);
            try {
                if (!Files.isRegularFile(p)) {
                    return new CheckResult.Fail(assertion, "not a file: " + p);
                }
                return Files.readString(p).contains(needle)
                        ? new CheckResult.Pass(assertion)
                        : new CheckResult.Fail(assertion,
                                "does not contain: " + needle);
            } catch (IOException e) {
                return new CheckResult.Fail(assertion, e.getMessage());
            }
        }
        return new CheckResult.Warn(assertion, "unknown assertion prefix");
    }

    private static Path resolve(Path workspace, String rel) {
        Path p = Path.of(rel);
        return p.isAbsolute() ? p : workspace.resolve(p);
    }
}
```
