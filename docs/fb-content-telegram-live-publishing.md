# Telegram live publishing

This integration uses the official Telegram Bot API over HTTPS. It only sends a publication that already exists in the content publishing workflow and has been explicitly approved.

## Configuration

Set these environment variables in the deployment secret store (never commit real values):

- `TELEGRAM_TOKEN`: bot token from BotFather.
- `TELEGRAM_CHANNEL_ID`: target channel numeric ID or `@channelusername`. The bot must be an administrator with permission to post.
- `CONTENT_PUBLISHING_API_KEY`: a long random secret (at least 32 random bytes recommended) used to authorize the live-publish endpoint.

The endpoint remains disabled with HTTP 503 when `CONTENT_PUBLISHING_API_KEY` is missing. Placeholder values such as `mock` are rejected for Telegram credentials.

## Workflow

1. Create a publication with `POST /api/fb/content/publications` and an `Idempotency-Key`.
2. Complete editorial review and approve it with `POST /api/fb/content/publications/{id}/approve`.
3. Explicitly send it using:

```http
POST /api/fb/content/publications/{id}/publish/telegram
X-Publishing-Key: <CONTENT_PUBLISHING_API_KEY>
X-Publishing-Actor: operator-or-service-name
```

No scheduler automatically sends content. A future `scheduledAt` blocks immediate publishing until due. For text-only posts the adapter calls `sendMessage`; when `mediaReference` is supplied it is passed to `sendPhoto` and must be a Telegram file_id or a URL Telegram can fetch. Photo captions are limited to 1024 characters including the title.

On success, the Telegram `message_id` is stored as `externalPostId`, the state becomes `PUBLISHED`, and an audit attempt is recorded. API failures are recorded as `FAILED`.

## Operational and delivery caveats

- Protect the service with HTTPS and keep the shared publishing key in a secret manager. Rotate it if exposed. Do not use actor labels as identity proof.
- Telegram's Bot API does not provide an idempotency key for `sendMessage` / `sendPhoto`. If Telegram accepts a request but the connection is lost before the response reaches this service, a manual retry from `FAILED` may produce a duplicate. Inspect the target channel before retrying an ambiguous timeout.
- Publishing is an external side effect inside a database transaction; a database failure after Telegram accepted the message can also leave an uncertain outcome. A durable outbox/reconciliation worker is the next hardening step.
- The API key authenticates the live-publish operation only. Existing create/list/approve endpoints are not made authenticated by this integration and must remain behind trusted network/API access until a full application authorization model is implemented.
- Do not call the endpoint against a production channel until the bot, channel permissions, content, and target are verified.
