package com.apex.platform.auth;

import java.util.Set;

/**
 * Answers: does any of these roles grant this permission? A principal
 * with multiple roles has the union of their permissions. No role
 * inherits from another.
 */
public interface RbacPolicy {
    boolean allows(Set<Role> roles, Permission permission);
}
