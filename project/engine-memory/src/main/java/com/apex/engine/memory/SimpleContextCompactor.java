package com.apex.engine.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Default {@link ContextCompactor} implementation. Compacts a
 * conversation history to fit within a token budget while preserving
 * invariant C4:
 *
 * <ul>
 *   <li>The SYSTEM message at index 0 is never dropped and does not
 *       count against the token budget.</li>
 *   <li>Any tool-call/result pair (an {@code "ASSISTANT: "} turn
 *       immediately followed by a {@code "TOOL_RESULT: "} turn) is
 *       preserved or dropped as a unit, never split.</li>
 * </ul>
 *
 * <p>The history convention is: index 0 is the system prompt, index 1
 * begins with {@code "TASK: "}, and indices 2+ alternate
 * {@code "ASSISTANT: "} and {@code "TOOL_RESULT: "} turns.
 *
 * <p>Tokens are approximated by {@link String#length()}. When the
 * history exceeds the budget, older units are dropped first, keeping
 * the most recent context intact. Tool pairs are given priority over
 * standalone entries.
 */
public final class SimpleContextCompactor implements ContextCompactor {

    private static final String ASSISTANT_PREFIX = "ASSISTANT: ";
    private static final String TOOL_RESULT_PREFIX = "TOOL_RESULT: ";

    @Override
    public List<String> compact(List<String> history, int maxTokens) {
        Objects.requireNonNull(history, "history");
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }

        int total = tokenSize(history);
        int systemSize = history.isEmpty() ? 0 : history.get(0).length();
        // SYSTEM (index 0) never counts against the token budget.
        if (total - systemSize <= maxTokens) {
            return List.copyOf(history);
        }

        // SYSTEM (index 0) is always kept and does not count against
        // the token budget.
        List<String> kept = new ArrayList<>();
        kept.add(history.get(0));
        int budget = maxTokens;

        // Build atomic units from the most recent entry backward, with
        // their original start index. A unit is either a tool-call/
        // result pair (2 entries) or a standalone entry (1 entry).
        List<Unit> units = new ArrayList<>();
        int i = history.size() - 1;
        while (i >= 1) {
            String entry = history.get(i);
            if (entry.startsWith(TOOL_RESULT_PREFIX) && i - 1 >= 1
                    && history.get(i - 1).startsWith(ASSISTANT_PREFIX)) {
                List<String> pair = List.of(history.get(i - 1), entry);
                units.add(new Unit(i - 1, pair));
                i -= 2;
            } else {
                units.add(new Unit(i, List.of(entry)));
                i -= 1;
            }
        }

        // Select units from most recent to oldest. Tool pairs are
        // processed in pass 1 so they get priority over standalone
        // entries; standalone entries are processed in pass 2 with the
        // remaining budget.
        List<Unit> selected = new ArrayList<>();
        for (Unit unit : units) { // units are most-recent-first
            if (unit.entries.size() == 2) {
                int unitSize = tokenSize(unit.entries);
                if (unitSize <= budget) {
                    selected.add(unit);
                    budget -= unitSize;
                }
            }
        }
        for (Unit unit : units) {
            if (unit.entries.size() == 1) {
                int unitSize = tokenSize(unit.entries);
                if (unitSize <= budget) {
                    selected.add(unit);
                    budget -= unitSize;
                }
            }
        }

        // Add selected units oldest-first (by start index) so entries
        // after SYSTEM are in chronological order.
        selected.sort((a, b) -> Integer.compare(a.start, b.start));
        for (Unit unit : selected) {
            kept.addAll(unit.entries);
        }

        return List.copyOf(kept);
    }

    private static int tokenSize(List<String> history) {
        int total = 0;
        for (String entry : history) {
            total += entry.length();
        }
        return total;
    }

    private static int tokenSize(String entry) {
        return entry == null ? 0 : entry.length();
    }

    /** A unit of one or two consecutive history entries. */
    private static final class Unit {
        final int start;
        final List<String> entries;

        Unit(int start, List<String> entries) {
            this.start = start;
            this.entries = entries;
        }
    }
}
