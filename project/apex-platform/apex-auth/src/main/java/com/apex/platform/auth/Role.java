package com.apex.platform.auth;

/**
 * Authorisation roles. Each role is a distinct capability bundle; no
 * role inherits from another. A principal may carry multiple roles;
 * effective permissions are the union.
 */
public enum Role {
    /** Normal user: chats, runs tools, sees own sessions. */
    OPERATOR,
    /** HITL approver: reviews proposals, reads audit, approves L3+. */
    REVIEWER,
    /** Tenant administrator: manages config, users, sees all sessions. */
    TENANT_ADMIN,
    /** Internal system principal: all permissions. */
    SYSTEM
}
