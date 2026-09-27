package legaltech;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LegalMatterAgentService {
    public enum Stage { INTAKE_PENDING, DOCUMENT_PENDING, DEADLINE_PENDING, COMPLETE }
    public record Matter(String matterId, String clientName, LocalDate responseDeadline, Stage stage) { }
    public record RunResult(Matter matter, String capturedEventId, String capturedGroupId) { }

    @FunctionalInterface
    public interface SignedDocumentDelivery {
        void deliver(Matter matter) throws Exception;
    }

    private final AgentFailureSink failures;

    public LegalMatterAgentService(AgentFailureSink failures) {
        this.failures = failures;
    }

    public RunResult advance(Matter matter, SignedDocumentDelivery delivery) {
        Matter accepted = new Matter(matter.matterId(), matter.clientName(), matter.responseDeadline(),
                Stage.DOCUMENT_PENDING);
        try {
            delivery.deliver(accepted);
            Matter awaitingDeadline = new Matter(accepted.matterId(), accepted.clientName(),
                    accepted.responseDeadline(), Stage.DEADLINE_PENDING);
            return new RunResult(awaitingDeadline, "", "");
        } catch (Exception failure) {
            Map<String, String> context = new LinkedHashMap<>();
            context.put("matter_id", accepted.matterId());
            context.put("step", "signed-document-delivery");
            context.put("next_action", "retry-delivery-before-deadline-follow-up");
            AgentFailureSink.CaptureReceipt receipt = failures.capture(
                    "Signed document delivery failed",
                    failure.getClass().getSimpleName() + ": " + failure.getMessage(),
                    failure.toString(),
                    stableKey(accepted.matterId(), "signed-document-delivery"),
                    context);
            return new RunResult(accepted, receipt.eventId(), receipt.groupId());
        }
    }

    private static String stableKey(String matterId, String step) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((matterId + ":" + step).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
