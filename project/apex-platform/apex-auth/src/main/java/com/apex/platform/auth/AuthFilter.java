package com.apex.platform.auth;

/**
 * Extracts and verifies an authentication token from an authorization
 * header. Returns a {@link Principal} on success, throws
 * {@link AuthException} on any failure. The server (S6) wires this
 * into a Spring filter.
 */
public interface AuthFilter {
    Principal authenticate(String authorizationHeader) throws AuthException;
}
