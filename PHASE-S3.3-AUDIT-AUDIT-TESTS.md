# PHASE-S3.3-AUDIT-AUDIT-TESTS.md — Session Package, Part 6 of 8

**Version:** 1.0 · **Part 6 of 8:** §7.1–§7.3, hash chain, redactor, in-memory log tests.
Parts: SESSION · CORE-PORT · AUDIT-CORE · AUDIT-LOG · CLI · AUDIT-TESTS ·
RUNNER-TESTS · CRITERIA, each `PHASE-S3.3-AUDIT-<part>.md`
(`AGENT_RULES.md` §2.13).

---
## 7. Test files

### 7.1 `apex-audit/src/test/java/com/apex/platform/audit/HashChainTest.java`

Five tests. Fixed key: `new byte[32]` filled with `0x42`.

1. `computesDeterministicHash` — same inputs twice → same hash
2. `differentInputsProduceDifferentHashes` — vary id
3. `previousHashAffectsResult` — same id/action/at, different previousHash → different hash
4. `actionChangeAltersHash`
5. `shortKeyRejected` — `new HashChain(new byte[16])` throws `AuditLogException("config")`

### 7.2 `apex-audit/src/test/java/com/apex/platform/audit/PiiRedactorTest.java`

Six tests:

1. `redactsEmail` — `"contact alice@example.com"` → `"contact [EMAIL]"`
2. `redactsSsn` — `"123-45-6789"` → `"[SSN]"`
3. `redactsAwsKey` — `"AKIAIOSFODNN7EXAMPLE"` → `"[AWS_KEY]"`
4. `redactsGithubToken` — `"ghp_" + 36 chars` → `"[GITHUB_TOKEN]"`
5. `leavesCleanTextUnchanged` — `"user logged in"` unchanged
6. `containsPiiDetects` — `containsPii("alice@x.com")` true; `containsPii("hello")` false

### 7.3 `apex-audit/src/test/java/com/apex/platform/audit/InMemoryAuditLogTest.java`

Ten tests. Fixed key: `new byte[32]` filled with `0x42`. Use a fixed
base instant `Instant.parse("2026-01-01T00:00:00Z")`.

1. `appendReturnsEntryWithComputedHash` — entry has non-blank hash; first entry has `previousHash == ""`
2. `secondEntryChainsToFirst` — `second.previousHash == first.hash`
3. `allReturnsImmutableSnapshot` — mutating the returned list throws
4. `allReturnsInAppendOrder`
5. `verifyChainOnEmptyLogReturnsTrue`
6. `verifyChainOnValidChainReturnsTrue` — three appends
7. `verifyChainOnTamperedActionReturnsFalse` — append one, then
   manually construct an `AuditEntry` with a different action and
   different hash but the same `previousHash`, feed it into a second
   `InMemoryAuditLog` that shares the key — cannot test tampering
   directly because fields are immutable. **Alternative test:**
   verify that recomputing the hash with different inputs produces a
   different result. This proves the tamper-detection mechanism, not
   the tamper itself. Use `HashChain` directly for this test.
8. `appendRedactsPii` — append `"login alice@example.com"`; query
   `all()`; assert `entry.action() == "login [EMAIL]"`
9. `identicalRedactedActionsProduceIdenticalHashes` — append
   `"a@b.com"` and `"c@d.com"` at the same instant; assert both
   entries have different hashes (different id) but the same redacted
   action
10. `verifyChainAfterManyAppends` — append 50 entries; assert
    `verifyChain()` returns true

Test #7 note: since `AuditEntry` is a record and `InMemoryAuditLog`
does not expose a mutation method, direct tampering is impossible
through the public API. The tamper-detection proof must construct a
`HashChain`, compute a hash for a specific entry, mutate the input,
and show the hash changes. That is a `HashChain` test, not an
`InMemoryAuditLog` test. Keep test #7 in `HashChainTest`, not here.
Replace test #7 with:

7. `chainCannotBeForgedWithoutSecret` — build a second
   `InMemoryAuditLog` with a different key; append the same action and
   instant to both; assert entries have different hashes
