package co.tz.kikomo.audit;

/** Audit event types written to {@code audit_log.event_type}. */
public final class AuditEvent {
    public static final String CALL_EVALUATED = "call_evaluated";
    public static final String CALL_EXECUTED = "call_executed";
    public static final String CALL_FAILED = "call_failed";
    public static final String CALL_RECORDING_FAILED = "call_recording_failed";
    public static final String APPROVAL_APPROVED = "approval_approved";
    public static final String APPROVAL_DENIED = "approval_denied";
    public static final String APPROVAL_EXPIRED = "approval_expired";
    public static final String AGENT_SUSPENDED = "agent_suspended";
    public static final String AGENT_ACTIVATED = "agent_activated";
    public static final String POLICY_ACTIVATED = "policy_activated";

    private AuditEvent() {
        /* constants */
    }
}
