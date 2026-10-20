# API documentation

- OpenAPI 3 spec: [openapi.yaml](openapi.yaml)
- Postman collection: [MMO-Football-API.postman_collection.json](../../postman/MMO-Football-API.postman_collection.json)

## Import
1. Open Postman and select Import.
2. Import `postman/MMO-Football-API.postman_collection.json`.
3. Set `baseUrl` to `http://localhost:8188` or your deployed app URL.
4. Start the app and try `GET /api/admin/leagues`.

You may also import `openapi.yaml` into Postman to generate requests from the OpenAPI spec.

## Covered endpoints
- Predictions: upcoming, by match ID, premium, and UTC date.
- Admin read APIs: matches, match by ID, teams, leagues.
- Admin mutation: update match status.
- Quartz job controls: start, pause, resume, stop.

Spring Data REST may expose additional repository endpoints that are not listed in this initial spec. See the corresponding controllers for exact behavior.

Security note: the current code does not declare authentication/authorization for admin mutation or Quartz job endpoints. Do not expose them publicly until access control is configured. `leagueId` on `GET /api/admin/teams` is accepted but not currently used as a filter.
