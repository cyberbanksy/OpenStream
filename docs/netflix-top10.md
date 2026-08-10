# Netflix regional Top 10 feed

OpenStream provides an account-free, server-cached regional Top 10 feed. It does not sign in to Netflix, use a profile cookie, call a member API, or attempt to recreate personalized rows.

## How the daily list is produced

1. Fetch the unauthenticated Netflix regional page for `OPENSTREAM_NETFLIX_TOP10_COUNTRY` (default `US`).
2. Locate the public `Trending Now` section.
3. Preserve its displayed order and accept only the first ten unique, validated `https://www.netflix.com/title/{id}` links.
4. Refresh every four hours. This is a polling interval; Netflix describes the list itself as updating daily.
5. Write the last successful snapshot atomically to `logs/netflix-top10-cache.json` with owner-only permissions.
6. If the regional markup is unavailable or changes, parse Netflix's official `all-weeks-countries.tsv`, select the latest week for the country, and return separate movie and series Top 10 lists.
7. If both sources fail, retain the last known-good snapshot and mark it stale after 30 hours instead of replacing it with an empty rail.

The primary feed is the public regional **overall Trending Now** list. It is not guaranteed to match the separate Movies and TV rows inside a signed-in Netflix app. The weekly fallback has separate `movies` and `series` lists because Netflix's official dataset publishes those categories.

Implementation: [`server/netflix-top10.mjs`](../server/netflix-top10.mjs). Parser, fallback, caching, and stale-data tests: [`server/netflix-top10.node-test.mjs`](../server/netflix-top10.node-test.mjs).

## Run and emulate

Run a live one-shot refresh:

```sh
pnpm netflix:top10
pnpm netflix:top10 -- --country=CA
```

Exercise the complete parser and cache response without network access:

```sh
pnpm netflix:top10:emulate
node --test server/netflix-top10.node-test.mjs
```

Start the gateway with a private high-entropy session token:

```sh
export OPENSTREAM_GATEWAY_TOKEN="replace-with-a-32-or-more-character-base64url-token"
export OPENSTREAM_NETFLIX_TOP10_COUNTRY="US"
pnpm gateway
```

Optional configuration:

```text
OPENSTREAM_NETFLIX_TOP10_REFRESH_MS=14400000
OPENSTREAM_NETFLIX_TOP10_CACHE_FILE=logs/netflix-top10-cache.json
```

The refresh interval has a 30-minute minimum. Clients read the authenticated endpoint:

```text
GET /api/catalog/netflix/top10
Origin: http://127.0.0.1:5173
X-OpenStream-Session: <gateway session token>
```

The browser normally supplies the allowed app `Origin` automatically. A direct integration must supply its configured allowed origin (or use the trusted loopback proxy marker) as well as the session token. The response identifies `source`, `cadence`, `refreshedAt`, `lastAttemptAt`, `fallbackWeek`, `stale`, and the available `overall`, `movies`, and `series` lists.

## Sources and prior art

- [Netflix Help](https://help.netflix.com/en/node/116472) documents that country Top 10 lists in the app update daily.
- [Netflix Top 10](https://www.netflix.com/tudum/top10/) and its [official country TSV](https://www.netflix.com/tudum/top10/data/all-weeks-countries.tsv) are the weekly fallback and historical source.
- The daily parser reads the unauthenticated regional page template `https://embed.beta.netflix.com/{country}/title/80057281` and only emits validated public `netflix.com/title` links.
- [`thitirat-mnc/Netflix-top10-dataset`](https://github.com/thitirat-mnc/Netflix-top10-dataset) mirrors official weekly history and is useful for analysis. OpenStream does not copy its code or data and does not use it as the live daily source.

Source markup is treated as an unstable external interface. Complete-list validation, bounded responses, a weekly fallback, and a last-good cache keep a markup change or temporary 403 from emptying the app.
