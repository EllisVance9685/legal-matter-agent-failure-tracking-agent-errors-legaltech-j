package legaltech;

import java.time.LocalDate;

public final class LegalMatterExample {
    private LegalMatterExample() { }

    public static void main(String[] args) {
        InfraiErrorsClient errors = new InfraiErrorsClient(InfraiConfig.fromEnvironment());
        LegalMatterAgentService service = new LegalMatterAgentService(errors);
        LegalMatterAgentService.Matter intake = new LegalMatterAgentService.Matter(
                "MAT-2048", "Jordan Lee", LocalDate.now().plusDays(14),
                LegalMatterAgentService.Stage.INTAKE_PENDING);

        LegalMatterAgentService.RunResult result = service.advance(intake, matter -> {
            throw new IllegalStateException("signature packet was not accepted by the delivery adapter");
        });

        System.out.println("matter=" + result.matter().matterId());
        System.out.println("stage=" + result.matter().stage());
        System.out.println("capturedEvent=" + result.capturedEventId());
        System.out.println("errorGroup=" + result.capturedGroupId());
    }
}
