# Shared provider HTTP client

MMO REST integrations use a shared transport under
com.mmo.module.fb.ingest.provider.client.

## Responsibilities

- ProviderHttpRequest describes one request: HTTP method, absolute URI, headers, query parameters,
  optional body, Jackson TypeReference, timeout, and maximum in-memory response size.
- ProviderHttpClient executes the request with WebClient, applies the configured response-size
  limit, decodes JSON, and normalizes transport failures into ProviderHttpException or
  ProviderTransportException.
- AbstractProviderClient resolves provider configuration from app.ingestion.provider-config,
  validates that the source is enabled, combines its base URI with an endpoint, and builds
  the shared request.
- Each source-specific client only defines its endpoint, authentication headers, parameters,
  response DTO and provider-specific error policy.

## Adding a REST provider

Inject ProviderHttpClient into a client extending AbstractProviderClient. Call exchange with the
provider enum, HTTP method, endpoint path, request headers, query parameters, optional body,
response TypeReference, timeout and response-size limit. The base URI and enabled state are
resolved from AppProperties. No WebClient construction or JSON decoding is needed in a provider.

Provider-specific authentication and semantics stay local: for example, FootballDataClient keeps
its quota pacing, Retry-After handling, retry policy and typed 401/403/429 classification.
API-Football keeps plan-restriction interpretation. Understat keeps its endpoint-specific Referer,
XHR and User-Agent headers. Shared transport does not silently retry requests or decide whether a
resource should be skipped.

SofaScore's current data collection is browser-driven through Playwright page navigation and
network-response interception. It is intentionally not forced through the REST WebClient
transport. Direct REST endpoints can use ProviderHttpClient when the provider access method
does not require the browser session.
