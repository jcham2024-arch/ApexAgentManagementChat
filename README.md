# Apex Agent Management Chat (LLM Architecture & Context Guide)

## Project Overview
Apex Agent Management Chat is a multi-module, Java 21 project designed to orchestrate autonomous AI agents. It strictly adheres to Hexagonal Architecture (Ports and Adapters) and a tiered module structure to ensure the core business logic remains completely isolated from external frameworks and infrastructure. This document is optimized to provide immediate, structured context for LLM agents contributing to or navigating this codebase.

## Build System & Tooling
- Build Tool: Maven
- Java Version: Java 21
- Testing Frameworks: JUnit 5, ArchUnit (for enforcing architectural boundaries)
- Null-Safety: JSpecify (using @NullMarked at the package level)

## The Tiered Architecture (CRITICAL CONSTRAINT)
The project enforces strict boundaries between modules using the Maven Enforcer plugin. When modifying dependencies, you must respect these tiers.

### Tier 0: The Pure Core
- Modules: apex-core, engine-core, engine-planner, engine-tools, engine-store
- Rule: NO third-party infrastructure dependencies. The build will fail if Spring, Jackson, Micrometer, or similar frameworks are introduced here.
- Purpose: Pure, deterministic, highly testable business logic, interfaces (ports), and domain models.

### Tier 2: Platform Services (Adapters)
- Modules: apex-auth, apex-audit
- Rule: Allowed to use external libraries (e.g., jjwt for JWT, standard cryptographic libraries).
- Purpose: Provide concrete implementations (adapters) for the interfaces defined in Tier 0.

## Module Breakdown
- apex-core: Defines fundamental domain models and port interfaces (e.g., AuditPort).
- engine-core: The central loop for agent execution (e.g., PlanLoop) and orchestration.
- engine-planner: Contains the AStarPlanner. This planner is strictly deterministic (uses a sequence counter to break ties in priority queues).
- engine-tools: Implementations of tools the agents can use (e.g., file I/O, HTTP calls).
- apex-auth: JWT authentication and authorization. Uses the modern jjwt (0.12.6) library. Enforces HS256, validates exp, sub, tenant_id, and roles.
- apex-audit: Provides a tamper-evident audit trail using an HMAC-SHA256 HashChain. Requires a minimum key length of 32 bytes.

## Coding Standards & Design Patterns
- Hexagonal Architecture: Always separate ports (interfaces in Tier 0) from adapters (implementations in Tier 2).
- Immutability: Favor Java Records for data carriers (e.g., the Node record in AStarPlanner).
- Determinism: AI agents and planners must be deterministic. Avoid random number generation in the core engine. Ensure collections are ordered or sorted predictably.
- Null Safety: All packages must have a package-info.java annotated with @NullMarked. Do not return null; use Optional or explicit empty collections.
- Resource Management: Long-running tasks must respect the CancellationToken passed through the PlanLoop. Check for cancellation at every step.

## Current State & Areas for Improvement
When asked to write new features or refactor, prioritize these areas:
1. Testing: The architectural rules are enforced by ArchUnit, but unit test coverage for the deterministic planner and the cryptographic audit log is currently sparse. Adding robust tests is a high priority.
2. Documentation: The root README.md is missing. Keep code comments and Javadoc up to date, especially for Port interfaces.
3. Avoid Over-Engineering: The tiered structure is powerful but complex. Do not add new modules unless absolutely necessary. 

## Instructions for LLM Agents Working on this Repo
1. Before adding a dependency to Tier 0, check if it violates the Maven Enforcer constraints in the root pom.xml.
2. If modifying the AStarPlanner, ensure the priority queue logic remains deterministic.
3. If adding a new capability (like a new agent tool), define the interface in Tier 0 (engine-tools) and the implementation in the appropriate adapter module.
4. Ensure all new code compiles under Java 21. 
5. Do not use nested code fences in generated markdown. Use standard indentation for code snippets if necessary, or inline code formatting.