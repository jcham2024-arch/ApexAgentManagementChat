@NullMarked
package com.apex.engine.core.guard;

/**
 * Policy gates for OS-level side effects. Tier 0. The dead-man switch,
 * rate limit, and password block required by K7. Injection
 * implementations (S6+) call the guard; the guard makes no OS calls
 * itself.
 */
@NullMarked
package com.apex.engine.core.guard;

import org.jspecify.annotations.NullMarked;
