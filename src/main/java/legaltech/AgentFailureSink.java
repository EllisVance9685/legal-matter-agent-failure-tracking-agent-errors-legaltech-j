package legaltech;

import java.util.Map;

public interface AgentFailureSink {
    CaptureReceipt capture(String title, String message, String exception, String idempotencyKey,
                           Map<String, String> context);

    record CaptureReceipt(String eventId, String groupId) { }
}
