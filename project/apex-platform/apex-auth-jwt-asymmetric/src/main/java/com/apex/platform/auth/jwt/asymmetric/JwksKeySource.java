package com.apex.platform.auth.jwt.asymmetric;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Fetches, caches, and refreshes JWKS public keys.
 *
 * <p>The cache is keyed by {@code kid}. On a cache miss for a given
 * {@code kid}, the source attempts exactly one refresh of the JWKS
 * endpoint, rate-limited so that on-demand refreshes happen no more
 * frequently than once per {@code refreshCooldown}. A background-style
 * refresh is not performed; callers must call
 * {@link #refresh()} periodically if they want proactive cache warming.
 *
 * <p>Thread-safe: multiple threads may call {@link #getKey(String)}
 * concurrently.
 */
public final class JwksKeySource {

    private final String jwksUri;
    private final Duration refreshCooldown;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();
    private volatile Instant lastRefresh = Instant.EPOCH;

    public JwksKeySource(String jwksUri, Duration refreshCooldown) {
        this.jwksUri = Objects.requireNonNull(jwksUri, "jwksUri");
        this.refreshCooldown = Objects.requireNonNull(
                refreshCooldown, "refreshCooldown");
    }

    /**
     * Returns the public key for the given {@code kid}, or {@link
     * Optional#empty()} if not available.
     *
     * <p>If the key is not in the cache, attempts one rate-limited
     * refresh of the JWKS endpoint. If the key is still absent after
     * refresh, returns empty.
     */
    public Optional<PublicKey> getKey(String kid) {
        Objects.requireNonNull(kid, "kid");

        PublicKey cached = keyCache.get(kid);
        if (cached != null) {
            return Optional.of(cached);
        }

        // Try one rate-limited refresh, then check again.
        maybeRefresh();
        PublicKey refreshed = keyCache.get(kid);
        return Optional.ofNullable(refreshed);
    }

    /**
     * Inserts a key into the cache, primarily for testing. Replaces any
     * existing key for the same {@code kid}.
     */
    public void putKey(String kid, PublicKey key) {
        Objects.requireNonNull(kid, "kid");
        Objects.requireNonNull(key, "key");
        lock.writeLock().lock();
        try {
            keyCache.put(kid, key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Refreshes the JWKS cache from the endpoint, but only if the
     * refresh cooldown has elapsed since the last refresh. If the
     * cooldown has not elapsed, this is a no-op.
     */
    public synchronized void maybeRefresh() {
        Instant now = Instant.now();
        if (Duration.between(lastRefresh, now).compareTo(refreshCooldown) < 0) {
            return; // rate-limited: skip
        }

        try {
            Map<String, PublicKey> fetched = fetchJwks();
            lock.writeLock().lock();
            try {
                keyCache.clear();
                keyCache.putAll(fetched);
                lastRefresh = now;
            } finally {
                lock.writeLock().unlock();
            }
        } catch (Exception e) {
            // JWKS fetch failed; keep the old cache (possibly empty).
            // Last refresh time is NOT updated so the next call retries.
        }
    }

    /**
     * Forces an immediate refresh regardless of cooldown. Used when a
     * caller has strong reason to believe the JWKS has changed (e.g.
     * a known key rotation event).
     */
    public synchronized void forceRefresh() {
        try {
            Map<String, PublicKey> fetched = fetchJwks();
            lock.writeLock().lock();
            try {
                keyCache.clear();
                keyCache.putAll(fetched);
                lastRefresh = Instant.now();
            } finally {
                lock.writeLock().unlock();
            }
        } catch (Exception e) {
            // Keep the old cache.
        }
    }

    private Map<String, PublicKey> fetchJwks() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jwksUri))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return Collections.emptyMap();
            }

            JWKSet jwkSet = JWKSet.parse(response.body());
            Map<String, PublicKey> keys = new HashMap<>();
            for (JWK jwk : jwkSet.getKeys()) {
                if (jwk instanceof RSAKey rsaKey) {
                    String kid = jwk.getKeyID();
                    if (kid != null && !kid.isBlank()) {
                        keys.put(kid, rsaKey.toRSAPublicKey());
                    }
                }
            }
            return keys;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
