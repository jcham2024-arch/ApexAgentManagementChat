/**
 * Default ChatModelPort implementation for OpenAI-compatible providers.
 * Tier 1. Implements: ChatModelPort. Consumes: java.base,
 * java.net.http. Handles streaming, error classification, and
 * cancellation between chunks.
 */
@NullMarked
package com.apex.engine.llm.http;

import org.jspecify.annotations.NullMarked;
