# Supplements — Package & Module Structure

Loaded from P1 onward and stays loaded for every subsequent phase.

## Maven Module Tree

```

apex/
├── pom.xml                          # parent, packaging=pom
├── kernel/
│   ├── pom.xml                      # packaging=pom
│   ├── kernel-core/                 # P1
│   ├── kernel-rules/                # P6
│   └── kernel-agent/                # P6
├── evolution/
│   ├── pom.xml                      # packaging=pom
│   ├── evolution-cas/               # P2
│   └── evolution-sentinel/          # P5
├── plugin-host/
│   ├── pom.xml                      # packaging=pom
│   ├── plugin-process/              # P4
│   └── plugin-mcp/                  # P6
├── ipc-omnibus/                     # P9
├── os-automation/                   # P9
└── client/                          # P7–P8 (pnpm workspace, not Maven)

```

**Rule:** create only the modules required for the current phase. Do not
scaffold `kernel-rules/` during P1.

## Java Package Map

```

com.agentplatform
├── kernel.core
│   ├── id/               TenantId, SessionId, CommitId, BlobId
│   ├── security/         CapabilityClass, PermissionGrant
│   ├── context/          ExecutionContext, ContextResolver
│   ├── concurrent/       CancellationToken
│   ├── message/          DomainMessage + 4 subtypes + ToolCallTrace
│   ├── stream/           AgentStreamEvent + 9 subtypes
│   └── workspace/        CompiledAgentWorkspace, AgentProfileView,
│                         LoadedSkill, ToolDescriptor (P3)
├── kernel.rules          (P6) AgentRuleValidator, rules
├── kernel.agent          (P6) DynamicAgentFactory, SupervisorTools,
│                              ContextCompactor, TokenEstimator
├── kernel.web            (P6) SpringContextResolver, AgentStreamController
├── evolution.cas
│   ├── domain/           WorkspaceBlob, Tree, Commit, Branch
│   ├── port/             WorkspaceStoragePort
│   ├── adapter/          PostgresWorkspaceStorageAdapter,
│   │                     SqliteWorkspaceStorageAdapter
│   └── materializer/     WorkspaceMaterializer (P3)
├── evolution.sentinel    (P5) SandboxValidationSentinel + 6 validators
├── plugin.process        (P4) SandboxedProcessExecutor, RealPathJail,
│                              ProcessTreeTerminator, BinaryAllowList,
│                              ToolExecutionResult
├── plugin.mcp            (P6) McpRegistry, McpClientFactory
├── ipc.osc               (P9) OscBridge
├── ipc.engine            (P9) GodotHeadlessBridge, BlenderBridge
└── automation            (P9) AccessibilityReader, InputInjectionGuard

```

## Test Mirror

- `src/test/java/...` mirrors `src/main/java/...` exactly.
- Property tests live in the same package as the class under test.
- Integration tests (`*IT.java`) in the same package but tagged
  `@Tag("integration")` and excluded from the default surefire run.

  ## Rule of Adherence

  If you need a package that isn't in this tree, **stop and report**. Do
  not invent packages silently — a new package is a structural decision
  that affects imports across modules.
  ```
  