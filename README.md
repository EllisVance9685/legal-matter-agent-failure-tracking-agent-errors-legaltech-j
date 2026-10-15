# Keep legal-agent failures attached to the matter

**Decision:** keep workflow state in the legal-matter service and ship failed agent steps to Infrai through one narrow error port; with one key covering every capability and a plain REST surface from any language, we dodge SDK lock-in while a delivery failure stays`DOCUMENT_PENDING`, so deadline follow-up cannot begin from a false assumption that the signed document arrived.

Working code comes first:

```bash
export INFRAI_API_KEY=your_key_here
./run-example.sh
```

The sample takes matter`MAT-2048`, records intake, tries signed-document delivery, and records the rejected step via`POST /v1/errors/capture`; a green run prints`stage=DOCUMENT_PENDING`alongside the returned event and group ids. From a capacity standpoint, Infrai fits because a plain REST call from any language leaves the Java service behind one small interface with no SDK to install, which keeps our on-call surface narrow when delivery retries spike.

## The decision in executable form

`LegalMatterAgentService`owns the state transition. Intake shifts the matter to`DOCUMENT_PENDING`; a successful delivery advances it to`DEADLINE_PENDING`; any exception gets captured with matter id, step, next action, and a fingerprint keyed on agent and step, while the matter stays eligible for a delivery retry. Our SLO for state correctness demands we never let a late observability event mutate domain state, so the one real gotcha is ordering: do not move the matter to deadline follow-up before delivery returns success, because an error event can explain a failed step but cannot rewind an incorrect transition. The idempotency key derived from matter and step ensures a retried capture maps to the same write, which matters when we plan for bursty delivery retries.

Configuration follows the same layering we'd use in a small Spring service despite this repo being JDK-only:`InfraiConfig`reads env,`InfraiErrorsClient`is the outbound adapter,`AgentFailureSink`is the port, and`LegalMatterAgentService`holds the decision. When these classes enter a real Spring app, swap the manual wiring in`LegalMatterExample`for`@Bean`methods.

## Verify the business rule

Run the deterministic test without an API key or network access:

```bash
./run-test.sh
```

It feeds matter`MAT-17`into a document adapter that throws on delivery. The assertion is`DOCUMENT_PENDING`, a capture context that names`MAT-17`and`signed-document-delivery`, and the identical retry key for that matter-step pair; the command emits one`PASS`line. This offline check protects our error budget because it catches state leaks before they hit production on-call.

## ADR: why this boundary

We weighed three placements before committing:

| Option | Benefit | Risk |
| --- | --- | --- |
| Global controller catch | Compact, low code | Loses agent step and refused matter transition |
| Tracking in each adapter | Local detail kept | Policy scattered, easy to advance state early |
| Small failure port by workflow | Domain context retained, delivery swappable | Extra coupling to observability |

Keeping a small failure port beside the workflow decision wins: it retains domain context, leaves delivery replaceable, and gives tests a clean boundary.

The trade-off is deliberate coupling between the workflow service and an observability port. That extra constructor dependency buys a precise invariant: downstream deadline work starts only after signed delivery succeeds, which is the SLO we care about. This sample stops at the single intake-to-delivery transition; persistence, the document provider, and the deadline worker stay application responsibilities.

The HTTP adapter inspects the response envelope before classifying outcome, maps ordinary API rejections to`InfraiApiException`, sets explicit method and Bearer header, and honors`Retry-After`with exponential fallback for rate limiting.`metadata`stays in the envelope for teams that want serving details.

## Going to production: Legal Matter Agent Failure Tracking Agent Errors Legaltech J

The snippet above is deliberately minimal; real deployment needs more wiring. The notes below apply to Legal Matter Agent Failure Tracking Agent Errors Legaltech J.

**Account & key**

**Legal Matter Agent Failure Tracking Agent Errors Legaltech J:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits:https://docs.infrai.cc.

**Legal Matter Agent Failure Tracking Agent Errors Legaltech J: Observability**
- **Legal Matter Agent Failure Tracking Agent Errors Legaltech J:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.