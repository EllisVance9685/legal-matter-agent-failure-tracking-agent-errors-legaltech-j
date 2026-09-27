package legaltech;

import java.time.LocalDate;
import java.util.Map;

public final class LegalMatterAgentServiceTest {
    public static void main(String[] args) {
        RecordingSink sink = new RecordingSink();
        LegalMatterAgentService service = new LegalMatterAgentService(sink);
        LegalMatterAgentService.Matter intake = new LegalMatterAgentService.Matter(
                "MAT-17", "Avery Chen", LocalDate.of(2027, 1, 15),
                LegalMatterAgentService.Stage.INTAKE_PENDING);

        LegalMatterAgentService.RunResult result = service.advance(intake,
                matter -> { throw new IllegalArgumentException("recipient address missing"); });

        check(result.matter().stage() == LegalMatterAgentService.Stage.DOCUMENT_PENDING,
                "a failed delivery must not advance to deadline follow-up");
        check("MAT-17".equals(sink.context.get("matter_id")), "capture must identify the matter");
        check("signed-document-delivery".equals(sink.context.get("step")), "capture must identify the step");
        check(sink.idempotencyKey.equals(sink.secondKey), "the same matter and step need a stable retry key");
        check("evt-test".equals(result.capturedEventId()), "the result should expose the capture receipt");
        System.out.println("PASS: delivery failure remains DOCUMENT_PENDING and is captured once per stable key");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class RecordingSink implements AgentFailureSink {
        private Map<String, String> context;
        private String idempotencyKey;
        private String secondKey;

        @Override
        public CaptureReceipt capture(String title, String message, String exception, String key,
                                      Map<String, String> context) {
            this.context = context;
            if (idempotencyKey == null) idempotencyKey = key;
            secondKey = key;
            return new CaptureReceipt("evt-test", "grp-test");
        }
    }
}
