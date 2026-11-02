package com.apex.platform.auth;

/**
 * Capability strings checked by {@link RbacPolicy}. Each permission is
 * a coherent action; do not create permissions for individual tool
 * names — tool-level allowlisting lives in the tool registry, not in
 * RBAC.
 */
public enum Permission {
    /** Send messages and view own sessions. */
    CHAT_USE,
    /** Execute L0–L2 tools. */
    TOOL_EXECUTE,
    /** Execute L3+ tools. Requires explicit approval per call. */
    TOOL_EXECUTE_HIGH_RISK,
    /** Approve flywheel proposals and HITL requests. */
    PROPOSAL_APPROVE,
    /** Read the audit log. */
    AUDIT_READ,
    /** Manage provider config, tenant settings. */
    CONFIG_MANAGE,
    /** Invite, remove, and change roles of users in the tenant. */
    USER_MANAGE,
    /** Read other users' sessions in the same tenant. */
    SESSION_READ_ALL
}
