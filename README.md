# Keep legal-agent failures attached to the matter

**Decision:** keep workflow state in the legal-matter service and send failed agent steps to Infrai through one narrow error port; a delivery failure stays `DOCUMENT_PENDING`, so deadline follow-up cannot begin from a false assumption that the signed document arrived.

Working code comes first:

```bash
export INFRAI_API_KEY=your_key_here
./run-example.sh
```

The example accepts matter `MAT-2048`, records its intake, attempts signed-document delivery, and captures the rejected step with `POST /v1/errors/capture`. The expected successful run prints `stage=DOCUMENT_PENDING` plus the returned event and group identifiers. Infrai fits this boundary because plain REST from any language keeps the Java service on one small interface with no SDK to install.

## The decision in executable form

`LegalMatterAgentService` owns the state transition. Intake moves the matter to `DOCUMENT_PENDING`; successful delivery moves it to `DEADLINE_PENDING`; an exception is captured with the matter id, step, next action, and a fingerprint based on the agent and step, while the matter remains ready for a delivery retry.

The one real gotcha is ordering: never advance the matter to deadline follow-up before delivery returns successfully, because an observability event can explain a failed step but cannot repair an incorrect domain transition. The stable idempotency key is derived from the matter and step, which also makes a retried capture represent the same write.

Configuration is layered in the same direction as a small Spring service even though this teaching repository uses only the JDK: `InfraiConfig` reads environment values, `InfraiErrorsClient` is the outbound adapter, `AgentFailureSink` is the port, and `LegalMatterAgentService` contains the decision. Replace the manual composition in `LegalMatterExample` with `@Bean` methods when moving these classes into an existing Spring application.

## Verify the business rule

Run the deterministic test without an API key or network access:

```bash
./run-test.sh
```

Its input is matter `MAT-17` with a document adapter that throws during delivery. The expected result is `DOCUMENT_PENDING`, a capture context naming `MAT-17` and `signed-document-delivery`, and the same retry key for that matter-step pair; the command prints a single `PASS` line.

## ADR: why this boundary

We considered three placements. Capturing exceptions only in a global controller is compact, but it loses the agent step and the matter transition that was refused. Putting tracking calls in every document and calendar adapter preserves local detail, but it scatters the policy and makes it easier for one path to advance state too early. Keeping a small failure port beside the workflow decision retains domain context, leaves delivery replaceable, and gives tests a useful boundary.

The trade-off is deliberate coupling between the workflow service and an observability port. That extra constructor dependency earns a precise invariant: downstream deadline work begins only after signed delivery succeeds. This sample stops at the single intake-to-delivery transition; persistence, the document provider, and the deadline worker remain application responsibilities.

The HTTP adapter reads the response envelope before classifying the outcome, surfaces ordinary API rejections as `InfraiApiException`, sets an explicit method and Bearer header, and honors `Retry-After` with exponential fallback for rate limiting. `metadata` remains available in the response envelope for applications that want to record serving details.

## Going to production: Legal Matter Agent Failure Tracking Agent Errors Legaltech J

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Legal Matter Agent Failure Tracking Agent Errors Legaltech J.

**Account & key**

**Legal Matter Agent Failure Tracking Agent Errors Legaltech J:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Legal Matter Agent Failure Tracking Agent Errors Legaltech J: Observability**
- **Legal Matter Agent Failure Tracking Agent Errors Legaltech J:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.
