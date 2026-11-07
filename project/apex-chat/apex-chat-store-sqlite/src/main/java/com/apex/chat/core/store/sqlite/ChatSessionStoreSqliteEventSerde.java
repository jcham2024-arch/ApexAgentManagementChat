package com.apex.chat.core.store.sqlite;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import com.apex.chat.core.event.*;
import com.apex.engine.json.Json;
import com.apex.engine.json.JsonSerializer;
import com.apex.engine.store.StorageException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Event serialization for {@link ChatSessionStoreSqlite}. Converts
 * {@link ChatLifecycleEvent} subtypes to and from their JSON map form
 * used by the {@code chat_events} table.
 */
final class ChatSessionStoreSqliteEventSerde {

    private ChatSessionStoreSqliteEventSerde() {}

    static String eventToJson(ChatLifecycleEvent event) {
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

        return JsonSerializer.stringify(json);
    }

    @SuppressWarnings("unchecked")
    static ChatLifecycleEvent eventFromJson(
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
