/**
 * Chained audit log with PII redaction. Tier 2. Implements AuditPort.
 * Hash chain uses HMAC-SHA256 over
 * previousHash|id|action|atMillis. In-memory only in S3.3;
 * file-backed persistence lands in S6.
 */
@NullMarked
package com.apex.platform.audit;

import org.jspecify.annotations.NullMarked;
