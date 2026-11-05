package com.apex.platform.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RbacPolicyTest {

    RbacPolicy policy;

    @BeforeEach
    void setUp() {
        policy = DefaultRbacPolicy.instance();
    }

    private boolean allows(Role role, Permission permission) {
        return policy.allows(Set.of(role), permission);
    }

    @Test
    void operatorCanChatAndUseTools() {
        assertThat(allows(Role.OPERATOR, Permission.CHAT_USE)).isTrue();
        assertThat(allows(Role.OPERATOR, Permission.TOOL_EXECUTE)).isTrue();
    }

    @Test
    void operatorCannotAudit() {
        assertThat(allows(Role.OPERATOR, Permission.AUDIT_READ)).isFalse();
    }

    @Test
    void operatorCannotApproveProposal() {
        assertThat(allows(Role.OPERATOR, Permission.PROPOSAL_APPROVE)).isFalse();
    }

    @Test
    void reviewerCanApproveProposal() {
        assertThat(allows(Role.REVIEWER, Permission.PROPOSAL_APPROVE)).isTrue();
    }

    @Test
    void reviewerCanReadAudit() {
        assertThat(allows(Role.REVIEWER, Permission.AUDIT_READ)).isTrue();
    }

    @Test
    void reviewerCannotManageConfig() {
        assertThat(allows(Role.REVIEWER, Permission.CONFIG_MANAGE)).isFalse();
    }

    @Test
    void tenantAdminCanManageConfig() {
        assertThat(allows(Role.TENANT_ADMIN, Permission.CONFIG_MANAGE)).isTrue();
        assertThat(allows(Role.TENANT_ADMIN, Permission.USER_MANAGE)).isTrue();
        assertThat(allows(Role.TENANT_ADMIN, Permission.SESSION_READ_ALL))
                .isTrue();
    }

    @Test
    void tenantAdminCannotApproveProposal() {
        assertThat(allows(Role.TENANT_ADMIN, Permission.PROPOSAL_APPROVE))
                .isFalse();
    }

    @Test
    void systemHasEveryPermission() {
        for (Permission permission : Permission.values()) {
            assertThat(allows(Role.SYSTEM, permission))
                    .as("SYSTEM should hold %s", permission)
                    .isTrue();
        }
        assertThat(((DefaultRbacPolicy) policy).permissionsFor(Role.SYSTEM))
                .hasSize(Permission.values().length);
    }

    @Test
    void multipleRolesUnionPermissions() {
        Set<Role> roles = Set.of(Role.OPERATOR, Role.REVIEWER);

        assertThat(policy.allows(roles, Permission.TOOL_EXECUTE)).isTrue();
        assertThat(policy.allows(roles, Permission.PROPOSAL_APPROVE)).isTrue();
        assertThat(policy.allows(roles, Permission.CONFIG_MANAGE)).isFalse();
    }

    @Test
    void emptyRolesDenied() {
        assertThat(policy.allows(Set.of(), Permission.CHAT_USE)).isFalse();
    }
}
