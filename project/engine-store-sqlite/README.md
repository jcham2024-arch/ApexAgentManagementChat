# engine-store-sqlite

**Tier:** 1 · **Purpose:** SQLite-backed StoragePort for step and
session logs.

## Allowed dependencies
java.base, java.sql, org.xerial:sqlite-jdbc, jspecify

## Ports consumed
none

## Ports implemented
StoragePort (via SqliteStoragePort)

## Design notes
- Each operation opens its own JDBC connection; WAL mode + 5s busy
  timeout applied on connect.
- Schema is minimal (five columns matching StepLogEntry). The richer
  step_log schema lands in S5.
- Duplicate step_id throws StorageException("duplicate").
- This adapter is for server and SBC. Android uses Room (S9) against
  the same StoragePort interface.

## Tests
SqliteStoragePortTest · SqliteStoragePortConcurrencyTest
