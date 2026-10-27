# Reliable workflow and monetization measurement

## Operating principle

Do not add more scheduled jobs to solve reliability problems. Keep the existing FBCronjobInitial registry and use its current schedules. Improve job semantics, idempotency boundaries, and reporting.

## Existing schedule policy

All expressions below are interpreted in UTC by JobScheduleCreator.

| Existing job | Cadence | Why |
| --- | --- | --- |
| FootballWorkflowOrchestrationJob | Daily 11:30 | Main daily data-to-content cycle |
| FootballDataBootstrapJob | Monthly | Expensive configured backfill/bootstrap |
| LeagueSyncJob | Monthly | League/season metadata changes infrequently |
| TeamSyncJob | Days 1 and 15 | Team metadata refresh |
| MatchResultSyncJob | Every 4 hours | Recent scores/status freshness |
| MatchFixtureSyncJob | Every 6 hours | Upcoming fixture freshness |
| MatchStatisticsSyncJob | Every 6 hours | Enrich finished matches with stats/xG |
| MatchProcessPredictionJob | Every 3 hours | Upcoming match analysis |
| AdvancedMetricsJob | Every 6 hours | Rebuild form and H2H aggregates |
| PredictionEvaluationJob | Every 30 minutes | Evaluate newly completed predictions |
| WalkForwardBackTestJob | Sunday 06:15 | Expensive model evaluation, outside content critical path |
| SubscriptionExpiryCheckJob | Daily midnight | Subscription expiry maintenance |
| PublicationWorkerQuartzJob | Every minute | Drain due, approved publication queue |

The cadence should be changed only when provider rate limits, kickoff distribution, content output volume, and production run-time metrics justify it. Bootstrap/backfill should not be used as a frequent refresh mechanism.

## Recovery and duplicate prevention

### Quartz

- startJobNow no longer holds an outer transaction open while triggering Quartz. RUNNING is committed before Quartz can finish and write COMPLETE, preventing a late commit from overwriting the execution result.
- A failed child stage stops the orchestrator rather than allowing content generation to proceed with stale upstream data. The next scheduled run retries the failed stage; existing content workflow checkpoints support resuming failed/partial runs with the same idempotency key.
- Keep the database job registry and Quartz trigger definitions aligned through FBCronjobInitial; no second scheduler or new job family is introduced.

### Content

- Scheduled workflow retries with the same idempotency key resume failed or partial runs instead of returning a permanently failed run.
- Draft persistence is checkpointed by workflow run, match, and format; the orchestration layer skips matches that already have a draft for the requested format.

### Telegram publication

- A unique (draft_id, platform) constraint plus the existing (platform, idempotency_key) constraint prevents a draft being published twice to the same platform through different request keys.
- The PUBLISHING claim is committed before making the Telegram network call. The network request is not inside a database transaction.
- Telegram's Bot API does not offer a client-supplied idempotency key for sendMessage. If the request times out or the process crashes after Telegram accepts the message but before the database stores the message ID, an automatic retry can duplicate the post. Such outcomes are therefore quarantined as DELIVERY_UNKNOWN, not blindly retried.
- The existing minute worker moves stale PUBLISHING rows older than 10 minutes to DELIVERY_UNKNOWN, in bounded batches. An operator must check the target channel and call the authenticated reconciliation endpoint before marking the post as delivered or permitting a retry.
- Definitive local validation/configuration failures are recorded as FAILED; retries remain bounded by the configured maximum attempt count and delay.

Before applying the new unique publication constraint to an existing production database, check for duplicate rows:

```sql
SELECT draft_id, platform, COUNT(*) AS duplicates
FROM fb_content_publications
GROUP BY draft_id, platform
HAVING COUNT(*) > 1;
```

Resolve duplicates intentionally before deploying the schema update; do not delete published history automatically.

## Conversion and revenue measurement

The new fb_conversion_events ledger records idempotent observations: CLICK, LEAD, SUBSCRIPTION_CONFIRMED, PAYMENT_CONFIRMED, and REFUND_CONFIRMED.

Each event has an eventSource, provider/external event ID, idempotency key, optional publication/subscriber/campaign references, and event timestamp. Duplicate event IDs do not increase counts twice. The report calculates gross confirmed payments, refunds, and net confirmed revenue by currency and optionally by campaign code. Clicks/leads are observations, not revenue; refunds are subtracted from confirmed payments.

Enable ingestion by setting FB_MONETIZATION_EVENTS_API_KEY. Send X-Monetization-Event-Key on ingestion/report requests. Payment/refund events must be emitted only after the upstream payment provider's webhook signature and payment state have been verified. Never accept a browser redirect or client claim as proof of payment.

Campaign click attribution is supported by a controlled redirect link. Click counts represent raw redirect requests, not deduplicated human visitors; messaging-app previews, bots, and repeated visits may be included, and no invasive fingerprinting is collected. Create a campaign link with the authenticated campaign endpoint, put the resulting /r/{campaignCode} URL in the content CTA, and the redirect records a deduplicated CLICK event before returning a 302 to the configured HTTPS destination. Only authenticated operators can create or disable links; arbitrary HTTP destinations and embedded URL credentials are rejected.

Endpoints:

- POST /api/fb/monetization/events — append an event with Idempotency-Key.
- GET /api/fb/monetization/events/revenue?from=<ISO-8601>&to=<ISO-8601>&currency=USD&campaignCode=<optional> — funnel counts and confirmed net revenue for a time window.
- POST /api/fb/monetization/campaigns — create an authenticated campaign link with campaignCode, destinationUrl (HTTPS), and optional publicationId.
- GET /r/{campaignCode} — record a click and redirect to the configured destination.
- PATCH /api/fb/monetization/campaigns/{campaignCode}?active=false — disable a campaign link.

### Remaining integration gate

This PR adds the durable event ledger, campaign-filtered report, and signature-verified Stripe webhook for payment_intent.succeeded, invoice.paid (initial paid subscriptions), and refund.created. It does not add a social-platform click analytics importer or change the existing payment checkout flow. Reports show only verified events actually received; they must not be presented as complete business revenue until Stripe checkout emits campaign metadata and tracked CTA links/analytics importers emit click and lead events. Next integration should connect outbound content campaign links to the ledger, then compare campaign clicks → leads → confirmed payments → refunds.

## Validation

Run mvn -B -ntp clean verify in CI. Before production:
1. Check existing duplicate (draft_id, platform) publication rows before schema update.
2. Run one controlled approved Telegram publication and verify the stored external message ID.
3. Simulate a remote timeout and verify the state becomes DELIVERY_UNKNOWN and is not resent.
4. Replay the same conversion event twice and verify it is counted once.
5. Compare reported confirmed payments/refunds against the verified payment provider ledger for the same period and currency.
