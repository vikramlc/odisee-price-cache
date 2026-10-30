# odisee-price-cache

A small, original practice repository for **Odisee** AI-assisted coding mocks.
It is a slice of a pricing service: a read-through, time-limited cache in
front of a slow catalog lookup, and a quoting service that uses it.
Java 21+, Gradle, JUnit 5. No other dependencies.

Licence: MIT (see `LICENSE`).

## Setup

Requires a JDK 21 or newer.

```bash
./gradlew test
```

All tests pass on `main`.

## Layout

| Path | What it is |
|---|---|
| `src/main/java/app/odisee/pricecache/Price.java` | Price record (sku, cents, loadedAt) |
| `src/main/java/app/odisee/pricecache/PriceCache.java` | Read-through cache with a TTL, backed by a `ConcurrentHashMap`; called from many request threads |
| `src/main/java/app/odisee/pricecache/PricingService.java` | Quotes order lines; 5% bulk discount from 10 units |
| `src/test/java/...` | Visible tests; `TestClock` is a hand-moved clock |

## Tasks

Each task is graded on its own, starting from `main`. Use any AI assistant
you like, but you own the result: read every edit, run the tests, and do not
change existing tests to make them pass. Keep the public API as it is unless a
task says otherwise.

### Task 1 — one load per SKU under concurrency (`t1-single-flight`)

Production metrics show the catalog service receiving bursts of identical
lookups: when a popular SKU is missing or has just expired, every request
thread that calls `PriceCache.get` at that moment calls the loader.

* `get` must call the loader **at most once per SKU per expiry**, however many
  threads ask at the same time. All of those callers get the same `Price`.
* A loader exception still propagates to the caller and is not cached; the
  next `get` tries again.
* TTL behaviour, `invalidate` and `size` stay as they are.

Add a concurrent test that fails before your change and passes after it.

### Task 2 — bound the cache size (`t2-lru-limit`)

The cache grows without limit as SKUs are added to the catalog. Add a
constructor `PriceCache(loader, clock, ttl, maxEntries)`.

* When an insert would make the cache hold more than `maxEntries` entries,
  evict the **least recently used** entry. A successful `get` (hit or load) counts as a use.
* `maxEntries < 1` throws `IllegalArgumentException`.
* The existing three-argument constructor keeps today's behaviour (no limit).
* TTL expiry, `invalidate` and `size` keep working exactly as before.
* Say in a comment what the cost of `get` is.

Add tests for eviction order and for "a `get` refreshes recency".

## For graders

* Golden solutions: branches `solution/t1-single-flight` and `solution/t2-lru-limit`.
* Hidden verifier tests: branch `verifier`, directory `src/verifier/java/...`
  (`T1SingleFlightVerifierTest`, `T2LruVerifierTest`). Copy them into
  `src/test/java` to run them.
  * fail-to-pass: the verifier class for the task.
  * pass-to-pass: `PriceCacheTest` and `PricingServiceTest` **as they are on `main`**.

```bash
git checkout <candidate>
git checkout main -- src/test/
git checkout verifier -- src/verifier/ && cp -r src/verifier/java src/test/
./gradlew test --tests '*T1SingleFlightVerifierTest'               # fail-to-pass
./gradlew test --tests '*PriceCacheTest' --tests '*PricingServiceTest'  # pass-to-pass
```

Please solve the tasks before looking at those branches.
