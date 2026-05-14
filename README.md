# trein

Find international train connections from any European city.

```
$ trein Berlin

Berlin → Amsterdam Centraal (ICE 140)
Berlin → Basel SBB (ICE 379)
Berlin → Bruxelles Midi (ES 452)
Berlin → Budapest-Nyugati (EN 40457)
Berlin → Göteborg Central (EN 344)
Berlin → Koebenhavn H (RJ 384)
Berlin → Malmö Central (D 300)
Berlin → Paris Est (ICE 9590)
Berlin → Praha hl.n. (ECE 385)
Berlin → Wien Hbf (RJ 177)
Berlin → Zürich HB (IC 60408)
...
```

## What it does

Given a city name, trein shows all cities in other countries you can reach by **direct train**, based on today's schedule. It queries a full 24 hours of departures to capture daytime services, night trains, and everything in between.

## Prerequisites

- [Clojure CLI](https://clojure.org/guides/install_clojure) (1.12+)
- [Podman](https://podman.io/) (for the local API instance)

## Setup

Start the local DB REST API:

```bash
podman machine init   # first time only
podman machine start
podman run -d --name db-rest -p 3000:3000 derhuerst/db-rest:6
```

## Usage

```bash
clojure -M:cli Berlin
clojure -M:cli Vienna
clojure -M:cli "Zürich"
clojure -M:cli Amsterdam
```

Use `--static` for offline mode (limited to Amsterdam, Berlin, Paris, Brussels):

```bash
clojure -M:cli Berlin --static
```

## Running tests

```bash
clojure -M:test
```

## Building

```bash
clojure -T:build uber
java -jar target/trein-0.1.0.jar Berlin
```

## How it works

trein uses the [db.transport.rest](https://v6.db.transport.rest/) API (self-hosted via podman) to fetch train departures. Country detection uses the UIC prefix of station IDs — no manually maintained lookup tables.

For trains where the API returns a null direction (common for Eurostar and TGV services), the destination is extracted from the tripId field and the station name is fetched via a separate API call.

See [plan.md](plan.md) for the full design document.

## License

MIT
