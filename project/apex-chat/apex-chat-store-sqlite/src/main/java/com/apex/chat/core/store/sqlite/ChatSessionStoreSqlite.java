package com.apex.chat.core.store.sqlite;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import com.apex.chat.core.SessionState;
import com.apex.chat.core.event.*;
import com.apex.chat.core.session.ChatSession;
import com.apex.chat.core.session.ChatSessionStore;
import com.apex.engine.json.Json;
import com.apex.engine.store.StorageException;

import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SQLite implementation of {@link ChatSessionStore}. Each operation opens
 * its own JDBC connection; WAL mode and a busy timeout are applied on
 * connect. Every query filters by {@code tenant_id} — a query for tenant
 * A never returns sessions written by tenant B.
 *
 * <p>Events are serialized as JSON in the {@code event_json} column of
 * the {@code chat_events} table, using {@link com.apex.engine.json.Json}.
 * The {@code event_type} column enables reconstruction of the correct
 * event subtype on read.
 */
public final class ChatSessionStoreSqlite implements ChatSessionStore {

    private static final String CREATE_SESSIONS_TABLE = """
            CREATE TABLE IF NOT EXISTS chat_sessions (
                session_id    TEXT PRIMARY KEY,
                tenant_id     TEXT NOT NULL,
                state         TEXT NOT NULL,
                last_sequence INTEGER NOT NULL
            )
            """;

    private static final String CREATE_EVENTS_TABLE = """
            CREATE TABLE IF NOT EXISTS chat_events (
                session_id  TEXT NOT NULL,
                sequence    INTEGER NOT NULL,
                event_type  TEXT NOT NULL,
                event_json  TEXT NOT NULL,
                PRIMARY KEY (session_id, sequence),
                FOREIGN KEY (session_id) REFERENCES chat_sessions(session_id)
            )
            """;

    private static final String CREATE_EVENTS_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_chat_events_session_seq
            ON chat_events(session_id, sequence)
            """;

    private static final String INSERT_SESSION = """
            INSERT OR REPLACE INTO chat_sessions (session_id, tenant_id, state, last_sequence)
            VALUES (?, ?, ?, ?)
            """;

    private static final String INSERT_EVENT = """
            INSERT INTO chat_events (session_id, sequence, event_type, event_json)
            VALUES (?, ?, ?, ?)
            """;

    private static final String SELECT_SESSION = """
            SELECT session_id, tenant_id, state, last_sequence
            FROM chat_sessions
            WHERE session_id = ? AND tenant_id = ?
            """;

    private static final String SELECT_EVENTS = """
            SELECT e.sequence, e.event_type, e.event_json
            FROM chat_events e
            JOIN chat_sessions s ON e.session_id = s.session_id
            WHERE e.session_id = ? AND s.tenant_id = ?
            ORDER BY e.sequence
            """;

    private static final String SELECT_TENANT_SESSIONS = """
            SELECT session_id
            FROM chat_sessions
            WHERE tenant_id = ?
            ORDER BY session_id
            """;

    private final Path dbPath;

    public ChatSessionStoreSqlite(Path dbPath) {
        this.dbPath = Objects.requireNonNull(dbPath, "dbPath");
        initSchema();
    }

    private Connection open() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("PRAGMA foreign_keys=ON");
        }
        return conn;
    }

    private void initSchema() {
        try (Connection conn = open();
             Statement st = conn.createStatement()) {
            st.execute(CREATE_SESSIONS_TABLE);
            st.execute(CREATE_EVENTS_TABLE);
            st.execute(CREATE_EVENTS_INDEX);
        } catch (SQLException e) {
            throw new StorageException("init", "failed to initialize schema", e);
        }
    }

    @Override
    public void save(ChatSession session) {
        Objects.requireNonNull(session, "session");

        try (Connection conn = open()) {
            conn.setAutoCommit(false);
            try {
                // Upsert session row
                try (PreparedStatement ps = conn.prepareStatement(INSERT_SESSION)) {
                    ps.setString(1, session.id().value());
                    ps.setString(2, session.tenantId().value());
                    ps.setString(3, session.state().name());
                    ps.setLong(4, session.lastSequence());
                    ps.executeUpdate();
                }

                // Delete existing events for this session, then re-insert all
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM chat_events WHERE session_id = ?")) {
                    ps.setString(1, session.id().value());
                    ps.executeUpdate();
                }

                // Insert all events
                List<com.apex.chat.core.event.ChatLifecycleEvent> events = session.events();
                for (int i = 0; i < events.size(); i++) {
                    com.apex.chat.core.event.ChatLifecycleEvent event = events.get(i);
                    try (PreparedStatement ps = conn.prepareStatement(INSERT_EVENT)) {
                        ps.setString(1, session.id().value());
                        ps.setLong(2, event.sequence());
                        ps.setString(3, event.type().name());
                        ps.setString(4, eventToJson(event));
                        ps.executeUpdate();
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    // ignore - original exception is more important
                }
                throw new StorageException("append", "failed to persist session " + session.id(), e);
            }
        } catch (SQLException e) {
            throw new StorageException("init", "failed to open connection", e);
        }
    }

    @Override
    public java.util.Optional<ChatSession> load(TenantId tenantId, ChatSessionId id) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(id, "id");

        try (Connection conn = open()) {
            // Load session row, tenant-scoped
            SessionState state = null;
            long lastSequence = 0;
            try (PreparedStatement ps = conn.prepareStatement(SELECT_SESSION)) {
                ps.setString(1, id.value());
                ps.setString(2, tenantId.value());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return java.util.Optional.empty();
                    }
                    state = SessionState.valueOf(rs.getString("state"));
                    lastSequence = rs.getLong("last_sequence");
                }
            }

            // Load events, tenant-scoped via join
            List<com.apex.chat.core.event.ChatLifecycleEvent> events = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(SELECT_EVENTS)) {
                ps.setString(1, id.value());
                ps.setString(2, tenantId.value());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int sequence = rs.getInt("sequence");
                        String eventType = rs.getString("event_type");
                        String eventJson = rs.getString("event_json");
                        com.apex.chat.core.event.ChatLifecycleEvent event = eventFromJson(
                                eventType, sequence, eventJson);
                        events.add(event);
                    }
                }
            }

            return java.util.Optional.of(new ChatSession(id, tenantId, events, state, lastSequence));
        } catch (SQLException e) {
            throw new StorageException("query", "failed to load session " + id, e);
        }
    }

    @Override
    public List<ChatSessionId> listByTenant(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");

        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(SELECT_TENANT_SESSIONS)) {
            ps.setString(1, tenantId.value());
            try (ResultSet rs = ps.executeQuery()) {
                List<ChatSessionId> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(new ChatSessionId(rs.getString("session_id")));
                }
                return ids;
            }
        } catch (SQLException e) {
            throw new StorageException("query", "failed to list sessions for tenant " + tenantId, e);
        }
    }

    /* ---------------------------------------------------------------- *
     *  Event serialization                                              *
     * ---------------------------------------------------------------- */

    private String eventToJson(com.apex.chat.core.event.ChatLifecycleEvent event) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("eventType", event.type().name());
        json.put("sessionId", event.sessionId().value());
        json.put("tenantId", event.tenantId().value());
        json.put("sequence", event.sequence());
        json.put("occurredAt", event.occurredAt().toEpochMilli());

        switch (event.type()) {
            case SESSION_OPENED -> {
                // No additional fields beyond common ones
            }
            case PLAN_STEP_STARTED -> {
                PlanStepStarted e = (PlanStepStarted) event;
                json.put("stepId", e.stepId());
                json.put("actionId", e.actionId());
            }
            case PLAN_STEP_COMPLETED -> {
                PlanStepCompleted e = (PlanStepCompleted) event;
                json.put("stepId", e.stepId());
                json.put("durationMs", e.durationMs());
            }
            case TOOL_INVOKED -> {
                ToolInvoked e = (ToolInvoked) event;
                json.put("toolName", e.toolName());
                json.put("argumentsJson", e.argumentsJson());
            }
            case APPROVAL_REQUESTED -> {
                ApprovalRequested e = (ApprovalRequested) event;
                json.put("requestId", e.requestId());
                json.put("toolName", e.toolName());
                json.put("riskLevel", e.riskLevel());
            }
            case APPROVAL_RESOLVED -> {
                ApprovalResolved e = (ApprovalResolved) event;
                json.put("requestId", e.requestId());
                json.put("toolName", e.toolName());
                json.put("approved", e.approved());
            }
            case SESSION_CLOSED -> {
                // No additional fields
            }
            case SESSION_FAILED -> {
                SessionFailed e = (SessionFailed) event;
                json.put("failureReason", e.failureReason());
            }
        }

        return Json.stringify(json);
    }

    @SuppressWarnings("unchecked")
    private com.apex.chat.core.event.ChatLifecycleEvent eventFromJson(
            String eventType, int sequence, String eventJson) {

        Map<String, Object> json = Json.parseObject(eventJson);
        ChatSessionId sessionId = new ChatSessionId((String) json.get("sessionId"));
        TenantId tenantId = new TenantId((String) json.get("tenantId"));
        Instant occurredAt = Instant.ofEpochMilli((Long) json.get("occurredAt"));

        switch (ChatEventType.valueOf(eventType)) {
            case SESSION_OPENED:
                return new SessionOpened(sessionId, tenantId, occurredAt, sequence);
            case PLAN_STEP_STARTED:
                return new PlanStepStarted(
                        sessionId, tenantId, occurredAt, sequence,
                        (String) json.get("stepId"), (String) json.get("actionId"));
            case PLAN_STEP_COMPLETED:
                return new PlanStepCompleted(
                        sessionId, tenantId, occurredAt, sequence,
                        (String) json.get("stepId"), ((Number) json.get("durationMs")).longValue());
            case TOOL_INVOKED:
                return new ToolInvoked(
                        sessionId, tenantId, occurredAt, sequence,
                        (String) json.get("toolName"), (String) json.get("argumentsJson"));
            case APPROVAL_REQUESTED:
                return new ApprovalRequested(
                        sessionId, tenantId, occurredAt, sequence,
                        (String) json.get("requestId"), (String) json.get("toolName"),
                        (String) json.get("riskLevel"));
            case APPROVAL_RESOLVED:
                return new ApprovalResolved(
                        sessionId, tenantId, occurredAt, sequence,
                        (String) json.get("requestId"), (String) json.get("toolName"),
                        (Boolean) json.get("approved"));
            case SESSION_CLOSED:
                return new SessionClosed(sessionId, tenantId, occurredAt, sequence);
            case SESSION_FAILED:
                return new SessionFailed(
                        sessionId, tenantId, occurredAt, sequence,
                        (String) json.get("failureReason"));
            default:
                throw new StorageException("query", "unknown event type: " + eventType);
        }
    }
}
