# CLAUDE.md

## What this project is

Clojure CLI that shows international train connections from any European city. `clojure -M:cli Berlin` returns all cities in other countries reachable by direct train today.

## Commands

```bash
clojure -M:test              # run all unit/fixture tests (no network needed)
clojure -M:cli Berlin        # live query (requires local db-rest running)
clojure -M:cli Berlin --static  # offline mode with hardcoded data
clojure -T:build uber        # build uberjar
```

## Local API setup

The app needs a local db-rest instance. Without it, live queries fail with connection errors.

```bash
podman machine start
podman run -d --name db-rest -p 3000:3000 derhuerst/db-rest:6
```

Check if it's running: `podman ps | grep db-rest`

Override the API URL with `DB_REST_URL` env var. Defaults to `http://localhost:3000`.

## Architecture

Hexagonal / ports-and-adapters. The key boundary is `trein.ports/RouteRepository` protocol.

- `src/trein/routes.clj` — pure use-case, no I/O. Filters to international connections only.
- `src/trein/adapters/db_rest.clj` — the main adapter. Queries departures in 24 hourly time slices.
- `src/trein/adapters/static.clj` — hardcoded fallback (4 cities only).
- Domain and use-case layers have zero infrastructure imports.

## Key design decisions (do not undo without understanding why)

- **UIC prefix for country detection.** The first 2 digits of a 7-digit IBNR station ID encode the country (80=DE, 84=NL, 87=FR, etc.). This replaced a manually maintained lookup map. See `station-id->country` and `uic-country` in `db_rest.clj`.

- **TripId parsing for null-direction trains.** Many Eurostar/TGV/night trains have `direction: null` in the API. The destination station ID is extracted from the `tripId` field (`TO#STATION_ID#TT#TIME`). See `parse-trip-destination-id` and `resolve-destination` in `db_rest.clj`.

- **24 hourly time slices.** The API caps each response to ~1 hour of departures regardless of the `duration` parameter. We make 24 requests offset by 1 hour each to get full-day coverage. See `fetch-departures` in `db_rest.clj`.

- **No product filter on API requests.** We fetch all departure types (including tram, bus, subway) because some international trains use unexpected product types (e.g., D 300 Berlin→Malmö is classified as `regionalExpress`). Non-train departures are discarded by `resolve-destination` because their station IDs are 6 digits (not valid 7-digit IBNR codes).

- **24-hour cache TTL.** This is route discovery (which cities can I reach today), not real-time schedule lookup. One API fetch per city per day is sufficient. Cache is at `~/.trein/cache/`.

- **Self-hosted db-rest via podman.** The public `v6.db.transport.rest` has aggressive IP-based rate limiting (100 req/min, but persistent throttling after moderate use). Self-hosting eliminates this.

## Testing

Tests use fixture data from `resources/fixtures/db-rest-berlin.json` — a real API response with proper tripIds. No network calls in tests.

The `^:integration` tagged test in `db_rest_test.clj` hits the real API and is skipped by default.

## Common pitfalls

- If tests fail with connection errors, the test runner might be trying to reach the API. Check that you're not accidentally running integration tests.
- The cache at `~/.trein/cache/` can hold stale data. Delete it (`rm -rf ~/.trein/cache`) when debugging unexpected results.
- The `station-name-cache` atom in `db_rest.clj` caches `/stops/:id` lookups in memory for the session. It resets on restart.
