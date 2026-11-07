/**
 * SQLite-backed StoragePort for step and session logs. Tier 1.
 * Implements: StoragePort. Consumes: java.sql, org.xerial:sqlite-jdbc.
 * Server and SBC deployment; Android uses Room (S9).
 */
@NullMarked
package com.apex.engine.store.sqlite;

import org.jspecify.annotations.NullMarked;
