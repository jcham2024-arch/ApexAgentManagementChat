# PHASE-S3.4-HITL.md

**Version:** 1.0 · **Sub-phase of S3** · **Ends with human-in-the-loop approval gating for L3+ tool calls.**

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S3-SECURITY.md` (repo — router)
4. **This file**
    }

    private TaskDefinition task(String id, String tool, int maxSteps) {
        return new TaskDefinition(id, "hitl test", "do it", maxSteps,
                "m", List.of(tool), List.of(), List.of(), List.of());
    }

    private static Path findEcho() {
        for (String c : new String[]{"/bin/echo", "/usr/bin/echo"}) {
            Path p = Path.of(c);
            if (Files.isExecutable(p)) return p;
        }
        throw new IllegalStateException("echo not found");
    }

    private record Scripted(List<String> responses) implements ChatModelPort {
        private static final AtomicInteger NEXT = new AtomicInteger();
        @Override
        public ChatResponse chat(ChatRequest req) {
            return new ChatResponse(responses.get(
                    Math.min(NEXT.getAndIncrement(), responses.size() - 1)));
        }
    }
}