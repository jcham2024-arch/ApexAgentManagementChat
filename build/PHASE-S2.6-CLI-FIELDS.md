# PHASE-S2.6-CLI-FIELDS.md — Session Package, Part 3 of 6

**Version:** 1.0 · **Sub-phase of S2** · **Part 3 of 6:** §4.5, TaskFields.
Parts: SESSION · TASK-TYPES · FIELDS · RUNNER-MAIN · METADATA ·
TESTS-CRITERIA, each `PHASE-S2.6-CLI-<part>.md` (`AGENT_RULES.md` §2.13).

---
### 4.5 `TaskFields.java`

```java
package com.apex.engine.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Field-level parsing and validation for task files. Extracted from
 * {@link TaskLoader} to keep both under the 200-line limit. Package
 * private.
 */
final class TaskFields {

    private TaskFields() {}

    static Map<String, Object> asStringKeyedMap(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    static TaskDefinition fromYaml(Map<String, Object> root) {
        String taskId = requireString(root, "task_id");
        String description = requireString(root, "description");
        String instructions = requireString(root, "instructions");
        int maxSteps = requirePositiveInt(root, "max_steps");
        String modelRoute = optionalString(root, "model_route", "");
        List<String> tools = requireStringList(root, "tools");

        List<String> readFirst = nestedList(root, "inputs", "read_first");
        List<String> outputFiles = nestedList(root, "outputs", "files");
        List<String> assertions = nestedList(root, "outputs", "assertions");

        return new TaskDefinition(taskId, description, instructions,
                maxSteps, modelRoute, tools, readFirst, outputFiles,
                assertions);
    }

    private static String requireString(Map<String, Object> root, String key) {
        Object v = root.get(key);
        if (v == null) {
            throw new TaskLoadException("schema", "missing field: " + key);
        }
        if (!(v instanceof String s) || s.isBlank()) {
            throw new TaskLoadException("schema",
                    "field '" + key + "' must be a non-blank string");
        }
        return s;
    }

    private static String optionalString(Map<String, Object> root,
                                         String key, String fallback) {
        Object v = root.get(key);
        if (v == null) return fallback;
        if (!(v instanceof String s)) {
            throw new TaskLoadException("schema",
                    "field '" + key + "' must be a string");
        }
        return s;
    }

    private static int requirePositiveInt(Map<String, Object> root,
                                          String key) {
        Object v = root.get(key);
        if (v == null) {
            throw new TaskLoadException("schema", "missing field: " + key);
        }
        if (v instanceof Long l) {
            if (l <= 0 || l > Integer.MAX_VALUE) {
                throw new TaskLoadException("schema",
                        "field '" + key + "' must be a positive int");
            }
            return l.intValue();
        }
        if (v instanceof Integer i && i > 0) return i;
        throw new TaskLoadException("schema",
                "field '" + key + "' must be a positive int");
    }

    private static List<String> requireStringList(Map<String, Object> root,
                                                  String key) {
        Object v = root.get(key);
        if (v == null) {
            throw new TaskLoadException("schema", "missing field: " + key);
        }
        return toStringList(v, key);
    }

    private static List<String> nestedList(Map<String, Object> root,
                                           String parent, String child) {
        Object p = root.get(parent);
        if (!(p instanceof Map<?, ?> parentMap)) return List.of();
        Object c = parentMap.get(child);
        if (c == null) return List.of();
        return toStringList(c, parent + "." + child);
    }

    private static List<String> toStringList(Object value, String path) {
        if (!(value instanceof List<?> list)) {
            throw new TaskLoadException("schema",
                    "field '" + path + "' must be a list");
        }
        List<String> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof String s) || s.isBlank()) {
                throw new TaskLoadException("schema",
                        "field '" + path + "' contains a non-string or blank element");
            }
            out.add(s);
        }
        return out;
    }
}
```
