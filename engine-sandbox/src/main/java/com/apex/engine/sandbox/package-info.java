/**
 * Sandboxed tool execution across three isolation tiers. Tier 1.
 * Defines the sandbox port and command/result types. Process tier
 * lands in S4.6; container and Firecracker stubs land in S4.6;
 * production isolation lands in S6.
 */
@NullMarked
package com.apex.engine.sandbox;

import org.jspecify.annotations.NullMarked;
