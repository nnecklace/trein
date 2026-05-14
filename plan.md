# Trein — Design Plan

## What This Is

A terminal application that answers: **"Which cities in other countries can I reach by direct train from `<city>`?"**

```
$ trein Berlin

Berlin → Amsterdam Centraal (ICE 140)
Berlin → Basel SBB (ICE 379)
Berlin → Bruxelles Midi (ES 452)
Berlin → Budapest-Nyugati (EN 40457)
Berlin → Chur (ICE 277)
Berlin → Göteborg Central (EN 344)
Berlin → Koebenhavn H (RJ 384)
Berlin → Malmö Central (D 300)
Berlin → Paris Est (ICE 9590)
Berlin → Praha hl.n. (ECE 385)
Berlin → Wien Hbf (RJ 177)
Berlin → Zürich HB (IC 60408)
...
```

Works with any European city — not limited to a fixed set.

---

## Behavioral Semantics

### What the output means

`trein <city>` shows all international destinations reachable by **direct train** from `<city>`, based on **today's schedule** (next 24 hours).

- **Direct only** — no transfers. The passenger boards and alights without changing trains.
- **Cross-border** — the train's terminus is in a different country than the departure city.
- **Terminus-based** — only the train's final stop is shown. Intermediate international stops are invisible (e.g., ICE Berlin→Amsterdam does not show intermediate Dutch stops).
- **Route discovery, not schedule lookup** — one entry per destination city, no departure times. The question is "where can I go?" not "when does it leave?"
- **Night trains included** — EuroNight, Nightjet services count.

### How it works

The DB REST API provides **departures**, not routes. We infer routes by:

1. Querying departures in 24 hourly time slices (the API caps each response to ~1 hour)
2. For each departure, extracting the destination station ID from the `tripId` field
3. Deriving the destination country from the **UIC station ID prefix** (first 2 digits of the 7-digit IBNR code)
4. Getting the station name from the `direction` field, or via `/stops/:id` API lookup when direction is null
5. Filtering out domestic connections (same country as origin)
6. Deduplicating by destination city

### What varies

- Results change by **day** (weekday vs weekend schedule)
- Results change by **season** (summer-only services)
- Results change by **weekly pattern** (a train running only on Fridays won't appear on Wednesday)

### Known limitations

- Only the terminus is shown, not intermediate stops
- The API's station search determines which station is queried — may not always pick the optimal one
- Cancelled trains may still appear in results

---

## Architecture

Clojure, hexagonal / ports-and-adapters pattern.

```
┌─────────────────────────────────────────────────────────┐
│  CLI Layer                                               │
│  trein.cli.core        (tools.cli entry point)          │
│  trein.cli.formatter   (print to stdout)                │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│  Use-Case Layer                                          │
│  trein.routes          (find-connections — pure fn)      │
│  Filters to international only, sorts, deduplicates     │
└────────────────────────┬────────────────────────────────┘
                         │ RouteRepository protocol
┌────────────────────────▼────────────────────────────────┐
│  Domain Layer                                            │
│  trein.domain          (City, Connection — Malli)       │
│  trein.ports           (RouteRepository protocol)       │
└─────────────────────────────────────────────────────────┘
                         ▲
┌────────────────────────┴────────────────────────────────┐
│  Infrastructure Layer                                    │
│  trein.adapters.db-rest   (db.transport.rest v6 API)    │
│  trein.adapters.static    (hardcoded fallback data)     │
│  trein.http               (HTTP client wrapper)         │
│  trein.cache              (file-based TTL cache)        │
└─────────────────────────────────────────────────────────┘
```

**Invariant**: domain and use-case layers have zero infrastructure imports.

---

## Technology Stack

| Concern | Choice |
|---------|--------|
| Language | Clojure 1.12 (JVM) |
| Build | deps.edn + tools.build |
| CLI parsing | clojure.tools.cli |
| HTTP client | babashka.http-client |
| Validation | Malli |
| JSON | jsonista |
| Testing | clojure.test + Kaocha |

---

## Data Provider

### API: db.transport.rest v6

Self-hosted locally via podman to avoid rate limiting on the public instance:

```bash
podman machine start
podman run -d --name db-rest -p 3000:3000 derhuerst/db-rest:6
```

The app defaults to `http://localhost:3000`. Override with `DB_REST_URL` env var to point at the public instance (`https://v6.db.transport.rest`) or another host.

### Key endpoints

```
GET /locations?query={city}          → resolve city name to station ID
GET /stops/{id}/departures           → departures from a station
GET /stops/{id}                      → station name lookup (for null-direction trains)
```

### Country resolution

Country is derived from the **UIC station ID prefix** — the first 2 digits of a 7-digit IBNR code encode the country:

| Prefix | Country | Prefix | Country |
|--------|---------|--------|---------|
| 80 | DE | 84 | NL |
| 81 | AT | 85 | CH |
| 82 | LU | 86 | DK |
| 83 | IT | 87 | FR |
| 88 | BE | 74 | SE |
| 51 | PL | 70 | GB |
| 54 | CZ | 76 | NO |
| 55 | HU | 71 | ES |

No manually maintained country lookup maps. Any train to any station with a valid 7-digit IBNR is automatically resolved.

### Null-direction trains

Many Eurostar (EUR), TGV, and some night trains have `direction: null` in the API response. The destination station ID is extracted from the `tripId` field (`TO#STATION_ID#TT#TIME`). The station name is fetched via `/stops/:id` and cached in memory.

### 24-hour coverage

The API caps each departures response to ~1 hour of data. To cover a full day, we query in 24 hourly time slices. Results are cached for 24 hours.

---

## Running

```bash
# Run with live API (requires local db-rest instance)
clojure -M:cli Berlin
clojure -M:cli Vienna
clojure -M:cli "Zürich"

# Run with static fallback data (offline, 4 cities only)
clojure -M:cli Berlin --static

# Run tests
clojure -M:test

# Build uberjar
clojure -T:build uber
java -jar target/trein-0.1.0.jar Berlin
```

---

## Project Structure

```
src/trein/
  domain.clj              City/Connection Malli schemas
  ports.clj               RouteRepository protocol
  routes.clj              find-connections use-case (pure)
  http.clj                HTTP client wrapper
  cache.clj               File-based TTL cache (~/.trein/cache/)
  adapters/
    db_rest.clj            DB REST v6 adapter (UIC prefix, tripId parsing, 24h slices)
    static.clj             Hardcoded fallback data
  cli/
    core.clj               CLI entry point
    formatter.clj           Output formatting

test/trein/
  routes_test.clj          Use-case unit tests
  formatter_test.clj       Formatter tests
  adapters/
    db_rest_test.clj        Adapter + fixture tests
```

---

## Future Work

- **Web API** — add Ring + Reitit layer at `src/trein/web/`, reusing the same `RouteRepository` adapters
- **Intermediate stops** — use `/trips/:id` to show all international stops, not just the terminus
- **Departure times** — enrich output with schedule data via `/journeys` endpoint
- **Multiple adapters** — iRail for Belgium, NS for Netherlands, SNCF for France
- **Station name normalization** — clean up raw API names like "Bruxelles Midi" → "Brussels"
