# Goal

Build Apex, an enterprise-grade self-evolving multi-agent platform.
The kernel is an immutable Java 21 microkernel; the evolution plane is a
Content-Addressable Storage Git-tree. Agents self-improve on sandbox
branches, and nothing reaches `main` without automated validation and
human sign-off. Success is a working P1–P10 pipeline with green CI at
every phase gate.

## In Scope
- Java 21 microkernel (records, sealed types, virtual threads)
- CAS workspace with branch CAS and rollback
- Sandboxed process executor with no shell interpolation
- Six-category validation sentinel
- LangChain4j orchestration and MCP host
- Tauri 2.0 client with SSE
- SBC profile (Raspberry Pi 400 / Kali)

## Out of Scope
- Multi-region LLM routing
- Cross-tenant skill marketplace
- TLA+ formal verification
- Android deployment

## Non-Negotiables
- No dynamic JVM classloading (K1)
- No shell interpolation (K2)
- Kernel invariants are compiled constants (K3)
- Every agent self-modification passes the sentinel (E1)
- Capability escalation requires a signed `PermissionGrant` (E2)
- TypeScript types are isomorphic to Java OpenAPI (C1)