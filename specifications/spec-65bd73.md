# Additional Code: Security and Functionality — Implementation Guide

---

## Purpose

This document is an instructional guide for implementing a focused set of code revisions and additions that close the foundation gaps identified in the prior reviews and deliver the minimum capability required for the product loop (chat → sandbox → proposal → export).

It contains exactly eight pieces. Each piece either:

- Fixes a foundation bug that blocks productization, or
- Delivers a required product capability.

Nothing else is included. No scaffolding, no speculative abstractions, no framework adoption.

---

## How to Use This Document

1. Read the piece table to understand scope.
2. Implement pieces in the listed order. Pieces 1–5 are foundations; pieces 6–8 build on them.
3. For each piece, use the provided code as-is or as the review baseline.
4. Add the stated test for each piece before merging.
5. Verify against the acceptance checklist at the end.
6. Do not add dependencies, abstractions, or features beyond what is listed.

---

## Pieces at a Glance

| # | Piece | Type | Replaces / Adds | Why It Is Minimal |
|---|---|---|---|---|
| 1 | `CanonicalJson` | Revision | Replaces `arguments.toString()` in `AuditLogManager` | One utility; fixes audit determinism |
| 2 | `SessionBudget` | Revision | Replaces global `AtomicInteger` in `SecurityInterlock` | Scope state; fixes cross-conversation denial of service |
| 3 | `SafePaths` | New | — | Prevents path traversal |
| 4 | `SandboxFs` | New | — | The sandbox; quotas in the same file |
| 5 | `parseToolCalls` | Revision | Adds missing parsing in `OpenAiCompatibleClient` | Without it, sandbox tools cannot be invoked |
| 6 | `SecureKeyStore` | New | Keystore-backed key storage | Keys never touch preferences in plaintext |
| 7 | `ProposalStore` + `ExportPackager` | New | Proposal flow and export zip | Delivers the upgrade loop |
| 8 | `AgentOrchestrator` fix | Deletion | Removes prompt-substring tool hack | The fix is a delete |

---

## Piece 1 — `CanonicalJson` (deterministic audit hashing)

**Fixes:** the audit hash chain is non-deterministic because it hashes `arguments.toString()`.

**File:** `core-engine/src/main/kotlin/com/apex/core/json/CanonicalJson.kt`

```kotlin
package com.apex.core.json

import java.security.MessageDigest

/**
 * Deterministic serialization used only for audit hashing.
 * Handles the types tool arguments actually carry: null, string,
 * number, boolean, map, list. Rejects anything else explicitly so
 * a caller cannot silently introduce non-determinism.
 */
object CanonicalJson {

    fun sha256Hex(value: Any?): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(encode(value).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun encode(value: Any?): String = buildString { append(this, value) }

    private fun append(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> quote(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long, is Short, is Byte -> sb.append(v)
            is Double -> sb.append(formatDouble(v))
            is Float -> sb.append(formatDouble(v.toDouble()))
            is Map<*, *> -> {
                sb.append('{')
                v.entries
                    .map { it.key.toString() to it.value }
                    .sortedBy { it.first }
                    .forEachIndexed { i, (k, vv) ->
                        if (i > 0) sb.append(',')
                        quote(sb, k); sb.append(':'); append(sb, vv)
                    }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                v.forEachIndexed { i, item ->
                    if (i > 0) sb.append(',')
                    append(sb, item)
                }
                sb.append(']')
            }
            is Array<*> -> append(sb, v.toList())
            else -> throw IllegalArgumentException(
                "Non-canonical type for audit: ${v.javaClass.name}"
            )
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else ->
                if (c < ' ') sb.append("\\u%04x".format(c.code))
                else sb.append(c)
        }
        sb.append('"')
    }

    private fun formatDouble(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}
```

**Revision in `AuditLogManager`** — one line:

```kotlin
// before
val argsHash = sha256(arguments.toString())

// after
val argsHash = CanonicalJson.sha256Hex(toolCall.arguments)
```

**Required test:** hash the same logical arguments with keys inserted in three different orders; assert all three hashes match.

---

## Piece 2 — `SessionBudget` (per-session iteration counter)

**Fixes:** a single global counter disables all future conversations once exhausted.

**File:** `security-interface/src/main/kotlin/com/apex/security/interlock/SessionBudget.kt`

```kotlin
package com.apex.security.interlock

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class SessionBudget(private val maxIterations: Int) {

    private val counters = ConcurrentHashMap<String, AtomicInteger>()

    @Throws(BudgetExhaustedException::class)
    fun acquire(sessionId: String) {
        val c = counters.computeIfAbsent(sessionId) { AtomicInteger(0) }
        if (c.incrementAndGet() > maxIterations) {
            throw BudgetExhaustedException(
                "Session $sessionId exceeded $maxIterations tool iterations"
            )
        }
    }

    fun remaining(sessionId: String): Int =
        (maxIterations - (counters[sessionId]?.get() ?: 0)).coerceAtLeast(0)

    fun reset(sessionId: String) {
        counters.remove(sessionId)
    }
}
```

**Revision in `SecurityInterlock`** — signature change:

```kotlin
// before
fun execute(toolCall: ToolCall): ToolResult

// after
fun execute(sessionId: String, toolCall: ToolCall): ToolResult {
    budget.acquire(sessionId)
    // ... existing validation ...
}
```

**Required test:** run five turns on session `A` with `maxIterations=3`; assert turn four throws. Run session `B` immediately after; assert `B` succeeds. This is the regression the old code would fail.

---

## Piece 3 — `SafePaths` (path traversal guard)

**Adds:** sandbox root confinement. Without this, `write_file("../../databases/app.db", ...)` overwrites app internals.

**File:** `core-engine/src/main/kotlin/com/apex/core/sandbox/SafePaths.kt`

```kotlin
package com.apex.core.sandbox

import java.io.File
import java.nio.file.Files

class UnsafePathException(message: String) : RuntimeException(message)

object SafePaths {

    fun resolve(root: File, relative: String): File {
        if (relative.isBlank()) throw UnsafePathException("empty path")
        if (relative.startsWith("/") || relative.startsWith("\\"))
            throw UnsafePathException("absolute path: $relative")
        if (relative.contains('\u0000'))
            throw UnsafePathException("null byte in path")

        val rootPath = root.canonicalFile.toPath()
        val target = File(rootPath.toFile(), relative).canonicalFile
        val targetPath = target.toPath()

        if (!targetPath.startsWith(rootPath))
            throw UnsafePathException("escapes sandbox: $relative")

        if (Files.exists(targetPath) && Files.isSymbolicLink(targetPath)) {
            val real = targetPath.toRealPath()
            if (!real.startsWith(rootPath))
                throw UnsafePathException("symlink escapes: $relative")
        }
        return target
    }
}
```

**Required test:** reject `"../x"`, `"/etc/passwd"`, `"a/../../b"`, and a symlink placed inside the sandbox pointing outside it.

---

## Piece 4 — `SandboxFs` (the sandbox with quotas)

**Adds:** the sandbox filesystem. Quotas live in the same file to keep the tool surface honest.

**File:** `core-engine/src/main/kotlin/com/apex/core/sandbox/SandboxFs.kt`

```kotlin
package com.apex.core.sandbox

import java.io.File

class QuotaExceededException(message: String) : RuntimeException(message)

data class SandboxLimits(
    val maxFileBytes: Long = 1L * 1024 * 1024,
    val maxTotalBytes: Long = 50L * 1024 * 1024,
    val maxFiles: Int = 500
)

class SandboxFs(
    private val root: File,
    private val limits: SandboxLimits = SandboxLimits()
) {
    init { root.mkdirs() }

    fun read(relative: String): String =
        SafePaths.resolve(root, relative).readText(Charsets.UTF_8)

    fun write(relative: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size > limits.maxFileBytes)
            throw QuotaExceededException("file too large: ${bytes.size}")

        val target = SafePaths.resolve(root, relative)
        target.parentFile?.mkdirs()

        val existingSize = target.takeIf { it.exists() }?.length() ?: 0L
        if (totalBytes() - existingSize + bytes.size > limits.maxTotalBytes)
            throw QuotaExceededException("total size limit")

        if (!target.exists() && fileCount() >= limits.maxFiles)
            throw QuotaExceededException("file count limit")

        target.writeBytes(bytes)
    }

    fun append(relative: String, content: String) {
        val current = if (SafePaths.resolve(root, relative).exists())
            read(relative) else ""
        write(relative, current + content)
    }

    fun list(relative: String = ""): List<String> {
        val dir = SafePaths.resolve(root, relative)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.sortedBy { it.name }
            ?.map { (if (it.isDirectory) it.name + "/" else it.name) }
            ?: emptyList()
    }

    private fun fileCount(): Int =
        root.walkTopDown().count { it.isFile }

    private fun totalBytes(): Long =
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
```

**Required test:** fill to quota, assert next write throws. Write via `"../escape"`, assert `UnsafePathException`. Assert `list("")` returns sorted entries.

---

## Piece 5 — `parseToolCalls` (the missing piece in `OpenAiCompatibleClient`)

**Fixes:** `tool_calls` are silently dropped, so the model can never invoke the sandbox.

**File:** `core-engine/src/main/kotlin/com/apex/core/port/chat/OpenAiCompatibleClient.kt` (revision)

```kotlin
// before generate(): only extracted content
// after:

@Override
public ChatResponse generate(ChatRequest request) {
    HttpResponse<String> response = send(request);
    String body = response.getBody();

    String content = MiniJson.extractField(body, "content"); // to be replaced
    List<ToolCall> toolCalls = parseToolCalls(body);

    return new ChatResponse(content == null ? "" : content, toolCalls);
}

private List<ToolCall> parseToolCalls(String body) {
    if (body == null || body.isEmpty()) return List.of();
    try {
        JSONObject root = new JSONObject(body);
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.length() == 0) return List.of();

        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        if (message == null) return List.of();

        JSONArray calls = message.optJSONArray("tool_calls");
        if (calls == null) return List.of();

        List<ToolCall> out = new ArrayList<>(calls.length());
        for (int i = 0; i < calls.length(); i++) {
            JSONObject c = calls.getJSONObject(i);
            JSONObject fn = c.getJSONObject("function");
            Map<String, Object> args = toMap(new JSONObject(
                    fn.optString("arguments", "{}")));
            out.add(new ToolCall(
                    c.optString("id"),
                    fn.getString("name"),
                    args));
        }
        return out;
    } catch (JSONException e) {
        return List.of(); // malformed body: no tool calls, caller decides
    }
}

private static Map<String, Object> toMap(JSONObject obj) {
    Map<String, Object> out = new LinkedHashMap<>();
    Iterator<String> it = obj.keys();
    while (it.hasNext()) {
        String k = it.next();
        Object v = obj.opt(k);
        if (v instanceof JSONObject) v = toMap((JSONObject) v);
        else if (v instanceof JSONArray) v = toList((JSONArray) v);
        out.put(k, v);
    }
    return out;
}

private static List<Object> toList(JSONArray arr) {
    List<Object> out = new ArrayList<>(arr.length());
    for (int i = 0; i < arr.length(); i++) {
        Object v = arr.opt(i);
        if (v instanceof JSONObject) v = toMap((JSONObject) v);
        else if (v instanceof JSONArray) v = toList((JSONArray) v);
        out.add(v);
    }
    return out;
}
```

**Required test:** feed a fixture body with one `tool_call`; assert `response.getToolCalls()` has one entry with the right name and arguments map. Feed a content-only body; assert empty tool list.

---

## Piece 6 — `SecureKeyStore` (Keystore-backed key storage)

**Adds:** encrypted-at-rest API keys. A plaintext key in SharedPreferences on a device that lets the model write files is a material leak.

**File:** `app/src/main/java/com/apex/app/security/SecureKeyStore.kt`

```kotlin
package com.apex.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureKeyStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("apex_secure_keys", Context.MODE_PRIVATE)
    private val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun put(providerId: String, apiKey: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val blob = cipher.iv + cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(providerId, Base64.encodeToString(blob, Base64.NO_WRAP))
            .apply()
    }

    fun get(providerId: String): String? {
        val encoded = prefs.getString(providerId, null) ?: return null
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        if (raw.size < 13) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            masterKey(),
            GCMParameterSpec(128, raw.copyOfRange(0, 12))
        )
        return String(
            cipher.doFinal(raw.copyOfRange(12, raw.size)),
            Charsets.UTF_8
        )
    }

    fun delete(providerId: String) {
        prefs.edit().remove(providerId).apply()
    }

    private fun masterKey(): SecretKey {
        val alias = "apex_master_key"
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)
            ?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        gen.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }
}
```

**Required test (instrumented):** put then get returns the same string. Delete then get returns null. Inspect the on-disk preferences XML; assert the value is not plaintext.

---

## Piece 7 — `ProposalStore` + `ExportPackager` (the upgrade loop)

**Adds:** proposal creation, approval with diff, and export zip with rebuild instructions. This is the deliverable the whole product exists for.

**File:** `core-engine/src/main/kotlin/com/apex/core/proposal/ProposalStore.kt`

```kotlin
package com.apex.core.proposal

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class Proposal(
    val id: String,
    val targetPath: String,
    val newContent: String,
    val rationale: String,
    val targetVersion: String
)

class ProposalStore(private val root: File) {
    init { root.mkdirs() }

    fun create(
        targetPath: String,
        newContent: String,
        rationale: String,
        targetVersion: String
    ): Proposal {
        val id = MessageDigest.getInstance("SHA-256")
            .digest("$targetPath|$newContent|${System.currentTimeMillis()}"
                .toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)

        val dir = File(root, id).apply { mkdirs() }
        File(dir, "content.txt").writeText(newContent, Charsets.UTF_8)
        File(dir, "meta.json").writeText(
            """{"targetPath":${json(targetPath)},""" +
            """"rationale":${json(rationale)},""" +
            """"targetVersion":${json(targetVersion)}}"""
        )
        return Proposal(id, targetPath, newContent, rationale, targetVersion)
    }

    fun approve(id: String, baseline: String) {
        val dir = File(root, id)
        require(dir.isDirectory) { "unknown proposal $id" }
        val proposed = File(dir, "content.txt").readText()
        File(dir, "diff.patch").writeText(unifiedDiff(baseline, proposed, id))
        File(dir, "approved").createNewFile()
    }

    fun reject(id: String, reason: String) {
        val dir = File(root, id)
        require(dir.isDirectory) { "unknown proposal $id" }
        File(dir, "rejected").writeText(reason)
    }

    fun approved(): List<File> =
        root.listFiles()
            ?.filter {
                File(it, "approved").exists() && File(it, "diff.patch").exists()
            }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun exportZip(out: File, changelog: String) {
        ZipOutputStream(out.outputStream()).use { zip ->
            approved().forEach { dir ->
                listOf("meta.json", "diff.patch", "content.txt").forEach { name ->
                    zip.putNextEntry(ZipEntry("proposals/${dir.name}/$name"))
                    zip.write(File(dir, name).readBytes())
                    zip.closeEntry()
                }
            }
            zip.putNextEntry(ZipEntry("CHANGELOG.md"))
            zip.write(changelog.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("README_APPLY.md"))
            zip.write(APPLY_INSTRUCTIONS.toByteArray())
            zip.closeEntry()
        }
    }

    private fun json(s: String) =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun unifiedDiff(a: String, b: String, label: String): String {
        val la = a.lines(); val lb = b.lines()
        return buildString {
            append("--- a/$label\n+++ b/$label\n")
            append("@@ -1,${la.size} +1,${lb.size} @@\n")
            la.forEach { append('-').append(it).append('\n') }
            lb.forEach { append('+').append(it).append('\n') }
        }
    }

    companion object {
        private val APPLY_INSTRUCTIONS = """
            # How to apply this upgrade

            1. Extract the archive.
            2. For each folder in `proposals/`, open `meta.json` to see
               `targetPath` (the file in the source tree to modify).
            3. Apply `content.txt` to that path, or review `diff.patch`.
            4. Rebuild: `./gradlew assembleDebug` (or `assembleRelease`).
            5. Install the rebuilt APK.

            The app did not compile this bundle. You are the build.
        """.trimIndent()
    }
}
```

**Required test:** create, approve with a baseline, export, unzip, and assert `proposals/<id>/diff.patch`, `content.txt`, `meta.json`, `CHANGELOG.md`, and `README_APPLY.md` are all present. Reject path: `approved()` returns empty.

---

## Piece 8 — `AgentOrchestrator` fix (delete, do not replace)

**Fixes:** prompt-substring tool execution.

**File:** `app/src/main/kotlin/com/apex/app/agent/AgentOrchestrator.kt` (revision)

```kotlin
// DELETE these lines:
//   if (prompt.contains("http_get", ignoreCase = true) ||
//       prompt.contains("fetch ", ignoreCase = true)) {
//       ... executeToolCall(toolCall) ...
//   }

// REPLACE with: tool execution driven only by model output.
private fun orchestrateTurn(sessionId: String, prompt: String): ChatResponse {
    val response = chatModel.generate(ChatRequest(prompt))
    for (call in response.toolCalls) {
        val result = interlock.execute(sessionId, call)
        // Append as a tool message and loop; the model decides what to do next.
        conversation.appendToolResult(call.id, result)
    }
    return response
}
```

**Required test:** send `"fetch http://evil.example/x"` as a user prompt; assert no HTTP request is issued and no `ToolCall` is constructed. This is the regression the old code would fail.

---

## What Is Deliberately Not Added

- No dependency injection framework, no coroutine or flow scaffolding, no new port interfaces.
- No JSON library beyond `org.json` (already on Android) and a hand-rolled canonical formatter. A general-purpose library will not guarantee deterministic key ordering, so the canonical formatter is required for the audit hash.
- No multi-file patch format. A unified diff per file is enough for review.
- No versioning scheme beyond a hex ID. The user reads `meta.json`.
- No self-source tool yet. It depends on decision D3 (curated bundle versus SAF), which is a product decision, not code.
- No consent UI. It is a screen, not a service, and it needs interaction design first.

---

## Implementation Order

1. Piece 1 — `CanonicalJson` and the `AuditLogManager` revision.
2. Piece 2 — `SessionBudget` and the `SecurityInterlock` signature change.
3. Piece 3 — `SafePaths`.
4. Piece 4 — `SandboxFs`.
5. Piece 5 — `parseToolCalls` in `OpenAiCompatibleClient`.
6. Piece 6 — `SecureKeyStore`.
7. Piece 7 — `ProposalStore` and export.
8. Piece 8 — delete the prompt-substring tool hack.

Pieces 1–5 restore the safety kernel to a state where the sandbox can be built on top of it. Pieces 6–8 deliver the user-facing foundation for the product loop.

---

## Acceptance Checklist

Mark the additional code work complete when all are true:

- [ ] Audit hashes are identical for logically identical tool arguments inserted in different key orders.
- [ ] The per-session budget rejects the fourth turn on a session configured for three, and a second session succeeds immediately after.
- [ ] `SafePaths.resolve` rejects `..`, absolute paths, and escaping symlinks.
- [ ] `SandboxFs` enforces file size, total size, and file count quotas, and rejects traversal attempts.
- [ ] `OpenAiCompatibleClient` returns populated `toolCalls` for a response body containing `tool_calls`.
- [ ] `SecureKeyStore` round-trips a key, deletes a key, and the on-disk value is not plaintext.
- [ ] `ProposalStore` exports a zip containing per-proposal `diff.patch`, `content.txt`, `meta.json`, plus `CHANGELOG.md` and `README_APPLY.md`.
- [ ] A prompt containing `"fetch http://..."` produces no HTTP request and no `ToolCall`.
- [ ] Every piece above has a corresponding test that fails before and passes after the change.

---

## Next Concrete Steps

The next small, well-scoped pieces are:

1. `list_own_source` and `read_own_source` tools, gated by the curated-bundle product decision (decision D3). These are read-only and small.
2. A consent-tier policy object consumed by `SecurityInterlock`, mapping L0–L3 to auto-allow, allow-with-audit, or ask. Small, and unblocks the consent UI.
3. The Settings and Test Connection screen, which consumes `SecureKeyStore` from Piece 6.

All three are blocked on product decisions already listed in the completeness document. Resolve those decisions before writing more code.