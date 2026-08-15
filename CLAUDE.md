# Expense Tracker Backend — Build Plan & Progress

Source spec: `src/main/resources/design.md`. This file tracks the phased build-out
of the backend and must be kept up to date as work progresses. After each phase
is completed, stop and get explicit user go-ahead before starting the next one.

## Decisions locked in with the user (2026-08-12)

- Entities: fix `id` fields to typed `UUID` with `@GeneratedValue`/`@UuidGenerator`,
  and drop the hardcoded `catalog="expensetracker"` from `@Table` (Postgres catalog
  mismatch risk). Everything else about the generated entities stays as-is unless a
  phase specifically needs a change (call it out when it happens).
- AI provider for Phase 8: `LocalLLMCategorizer` calling a local Ollama HTTP endpoint
  (configurable base URL + model name, no API key). `ExpenseCategorizationAI`
  interface stays provider-agnostic so OpenAI/Gemini implementations can be added
  later without touching business logic.
- Config format: `application.yml` + `application-local.yml` (replacing
  `application.properties`), per design.md section 29.
- Phase granularity: ~12 checkpoints (listed below), pausing for approval after each.
- Account deletion (2026-08-14): `DELETE /api/v1/accounts/{id}` is a hard delete but is
  blocked with 409 if the account has any transactions or statements (checked at the
  app level; the FKs from `statements.account_id` / `transactions.account_id` are
  `ON DELETE RESTRICT`, not `CASCADE`, as a DB-level backstop). Deactivation (PUT with
  `active:false`) is the path for retiring an account that has history.

## Phase status

| # | Phase | Status |
|---|-------|--------|
| 1 | Project setup, config, Flyway baseline, entity fixes | done |
| 2 | Authentication (register/login/refresh/logout, JWT, Spring Security) | done |
| 3 | Account APIs (CRUD, ownership) | done |
| 4 | Category module (list/read APIs, user_category_rules groundwork) | done |
| 5 | Statement upload + file storage abstraction + RabbitMQ publish | done |
| 6 | PDF parsing (Strategy pattern parsers) + merchant normalization + duplicate detection | done (HDFC bank + HDFC credit card + generic; ICICI/SBI pending real samples) |
| 7 | Transaction APIs (manual expense/income, list/filter, update, category patch) | done |
| 8 | Categorization engine + AI abstraction (Ollama) + review APIs | done |
| 9 | Analytics & Dashboard (monthly/yearly/trends, recalculation) | done |
| 10 | Cross-cutting hardening (security review, global exceptions, Swagger, observability) | done |
| 11 | Testing (unit + integration w/ Testcontainers) | skipped (user decision, 2026-08-15) |
| 12 | Docker, docker-compose, README | done |

## Environment notes (Spring Boot 4.1.0 gotchas found during Phase 1)

- This project pins `spring-boot-starter-parent` 4.1.0, which splits what used to be
  bundled in `spring-boot-autoconfigure` into many small modules. Flyway autoconfig
  is NOT pulled in transitively by `flyway-core` alone — you must explicitly add
  `org.springframework.boot:spring-boot-flyway`.
- Testcontainers 2.x renamed artifacts: use `org.testcontainers:testcontainers-postgresql`
  and `org.testcontainers:testcontainers-junit-jupiter` (not the old `postgresql` /
  `junit-jupiter` artifact ids). Java package names (`org.testcontainers.containers.*`,
  `org.testcontainers.junit.jupiter.*`) are unchanged.
- Never put `hibernate.properties` under `src/main/resources` — Hibernate's core
  bootstrap auto-loads any classpath file with that exact name and it will hijack the
  JDBC connection provider away from Spring's configured datasource. It now lives at
  `tooling/hibernate.properties` and is referenced only by the `hibernate-tools-maven`
  plugin's `hbm2java` goal (manual entity regeneration), wired via `<propertyFile>` in
  `pom.xml`.
- Verified end-to-end against a real (throwaway, Docker) Postgres: app boots, Flyway
  runs both migrations, Hibernate schema validation passes against the generated
  entities. `ExpenseTrackerApplicationTests` now spins up its own Postgres via
  Testcontainers (`@ServiceConnection`) rather than requiring a local DB / env vars.
- Per user feedback: do not add unit tests for new classes phase-by-phase; testing is
  concentrated in Phase 11. Only fix tests that block compilation/build.
- A separate app instance is sometimes left running from the IDE (IntelliJ run
  config) on port 8081 — don't kill it; use a different `SERVER_PORT` for any manual
  verification runs.
- This Spring Boot version defaults to Jackson 3.x (`tools.jackson.*`); there is no
  Spring-managed bean of the classic `com.fasterxml.jackson.databind.ObjectMapper`
  type. Anything needing that classic type (e.g. `RestAuthenticationEntryPoint`)
  must construct its own `ObjectMapper` locally rather than `@Autowired`/inject it.
- `GlobalExceptionHandler`'s catch-all `Exception` handler will swallow Spring MVC's
  own `NoResourceFoundException` (thrown for unmatched routes) and turn it into a
  500 unless handled explicitly first — added a dedicated `@ExceptionHandler` for it
  returning 404. Watch for other standard MVC exceptions (`HttpRequestMethodNotSupportedException`,
  etc.) needing the same treatment as they come up.
- Verified end-to-end against a real (throwaway, Docker) Postgres: register, duplicate-email
  409, wrong-password 401, login, unauthenticated/invalid/expired JWT all correctly
  401, valid JWT passes through to a 404 on an unmatched route, refresh-token rotation
  (old token rejected after use), and logout revocation all behave correctly over curl.
- A `@RequestParam` bound to an enum with an invalid value throws
  `MethodArgumentTypeMismatchException`, NOT `HttpMessageNotReadableException` (that one's
  only for request-body deserialization) -- needed its own handler for a clean 400 instead
  of falling through to the generic 500. Watch for this pattern anywhere else enums are
  bound from query params/path variables.
- Categories module verified end-to-end: type filter (EXPENSE/INCOME/TRANSFER), 404 on
  unknown id, and hierarchical tree building (parent/child nesting via `parent_id`) all
  confirmed over curl, including reparenting a category via direct SQL to prove the tree
  builder nests correctly (no seeded hierarchy exists by default -- all 24 categories are
  top-level until a parent_id is set).
- Statement upload's event publish happens in the caller (`StatementService.upload`)
  *after* `persistStatement` returns, not inside a shared `@Transactional` spanning both
  the DB write and the publish -- avoids both the dual-write hazard (event published for
  a row that then rolls back) and the Spring AOP self-invocation trap (an internal
  `@Transactional` method called via `this.` from the same class is not intercepted by
  the proxy). `persistStatement` needs no explicit `@Transactional` since a single
  `repository.save()` is already transactional via `SimpleJpaRepository`.
  Same reasoning will apply to Phase 6's consumer and Phase 9's analytics recalculation
  triggers -- watch for the same trap there.
  Verified end-to-end against real (throwaway, Docker) Postgres + RabbitMQ: full upload
  validation chain (missing auth 401, missing file part 400, missing accountId 400, wrong
  extension 400, corrupt PDF bytes 400, unknown account 404, success 202), file landed on
  disk under a per-user directory, DB row correct, `GET` list/by-id correct, cross-user
  404 on someone else's statement, and the RabbitMQ message itself inspected on the wire
  (`statement.processing.queue`, correct JSON: `statementId`/`accountId`/`userId`/
  `storageKey`, nothing sensitive).
- Phase 6 (2026-08-14): `StatementParser` strategy interface + `GenericStatementParser`
  (fallback, requires an explicit Dr/Cr marker per line -- won't guess direction) +
  `HdfcBankStatementParser`, `MerchantNormalizer` (`transaction.util`, first-token-after-
  skipping-corporate-suffixes-and-payment-rail-prefixes: PVT/LTD/.../UPI/NEFT/IMPS/...),
  `TransactionHasher` (SHA-256 fingerprint per design.md section 10), RabbitMQ consumer
  (`StatementProcessingListener` -> `StatementProcessingService` -> `StatementTransactionPersister`,
  the last one a separate bean so `@Transactional` actually applies -- same self-invocation
  trap as Phase 5, and the fix generalizes: `StatementProcessingService.process()` also had
  to switch to `StatementRepository.findByIdWithAccountAndUser` (a JOIN FETCH query) instead
  of plain `findById`, because the async listener has no open Hibernate session by the time
  it reads `statement.getAccounts()`/`getUsers()` -- LazyInitializationException otherwise).
  ICICI/SBI parsers are not built yet (no real samples available) -- statements from those
  institutions currently fall through to `GenericStatementParser`.
  Real-statement findings (from a genuine, password-protected HDFC statement, redacted in
  all our testing/logs): (1) upload validation had no path for encrypted PDFs at all --
  `StatementFileValidator` now accepts an optional `password` param, decrypts once via
  PDFBox (`Loader.loadPDF(bytes, password)` + `setAllSecurityToBeRemoved(true)`), and only
  the decrypted bytes are stored/processed from then on -- the password itself is never
  persisted, logged, or put on the RabbitMQ message. `FileStorageService.store` therefore
  takes an `InputStream` now, not a `MultipartFile`. (2) HDFC's real export does NOT come
  out of PDFBox as one line per transaction -- narration wraps across 2-4 physical lines
  and the numeric row (ref no/value date/amount/balance) can land anywhere within that
  wrapped block, so `HdfcBankStatementParser` scans the whole text for the numeric-row
  pattern and uses the *running-balance delta* between consecutive rows as the source of
  truth for amount + DEBIT/CREDIT direction, seeded from the statement's own "Opening
  Balance" summary line -- this reconciled exactly (12 debits/42,887.00, 1 credit/4,600.00)
  against the bank's own self-reported Dr Count/Cr Count/Debits/Credits. Known limitation:
  a narration fragment that lands *after* its own numeric row (and before the next
  transaction's date) is dropped rather than reattached; in the sample seen this is always
  a trailing bank-reference-code fragment, never merchant-identifying text. (3) real
  narrations blew past `transactions.description VARCHAR(255)` (and `statements.error_message`
  had the same latent risk) -- both changed to `TEXT` in `V1__init_schema.sql` plus
  `columnDefinition = "TEXT"` on the corresponding entity getters, since this hadn't been
  run against any real persisted environment yet.
- HDFC credit card ("Billed Statement") export is structurally unrelated to the savings
  account one -- not encrypted, mostly single-line transactions ("dd/mm/yyyy| hh:mm
  <description>  C<amount> l", "C" being this document's font rendering the Rupee glyph
  as literal ASCII 'C' -- confirmed from raw bytes, may not hold for a differently-generated
  card statement), direction read from a leading "+" (credit) rather than a running-balance
  delta (no balance column here). `HdfcCreditCardStatementParser` added alongside
  `HdfcBankStatementParser`; both are HDFC but for different `accountType`s, which exposed
  a real bug -- `HdfcBankStatementParser.supports()` only checked institution, so it would
  have wrongly also claimed credit-card statements. Fixed by adding an `accountType` check
  to both parsers' `supports()`. A "+" credit is typed PAYMENT if its description mentions
  "PAYMENT" (e.g. a "CC PAYMENT ... PayZapp" line), else REFUND, so Phase 9's analytics can
  exclude credit-card bill payments from totals per design.md section 21/4. Verified against
  the real statement: 29 transactions (28 debit + 1 payment), and both totals reconcile
  exactly against the statement's own summary line (Purchases/Debit 4,186.16, Payments/
  Credits Received 11,098.00). Parser-selection disambiguation (bank vs. credit-card
  `supports()`) verified directly; did not re-run the full RabbitMQ round-trip for this one
  since that plumbing was already proven correct against the bank-statement case and
  RabbitMQ was locally flaky (Docker Desktop `.erlang.cookie` permission errors, unrelated
  to the app) during this session.
- Manual transactions ALSO need `transaction_hash` populated (the unique constraint applies
  regardless of source), but must never collide with each other or with real PDF-derived
  hashes just because two legitimate manual entries happen to share the same date/amount/
  description (e.g. two separate same-day coffees) -- `TransactionHasher` gained an
  optional `salt` param; manual creation and any subsequent edit of a MANUAL-sourced
  transaction pass a fresh random UUID as salt, while PDF-sourced hashing (Phase 6) stays
  unsalted so re-upload dedup keeps working. Verified two identical manual expenses both
  succeed (201 twice), unlike a PDF re-upload which correctly dedupes.
- Editing a transaction (PUT) recomputes `normalized_merchant` + `transaction_hash` since
  amount/date/description/type can all change -- added a `DataIntegrityViolationException`
  -> 409 handler in `GlobalExceptionHandler` for the rare case an edit's new hash collides
  with an existing row.
- Found and fixed a real bug hit on the very first `GET /api/v1/transactions` call:
  Hibernate returns `java.sql.Date` for `@Temporal(TemporalType.DATE)` columns (transaction_
  date, statement_start/end_date), and `java.sql.Date.toInstant()` throws
  `UnsupportedOperationException` by design (a DATE has no time-of-day component) -- the
  `.toInstant().atZone(...)` pattern used in `TransactionMapper`, `StatementMapper`, and
  `TransactionService.recomputeFingerprint` all had this latent bug (silent until a
  non-null value actually got mapped). Fixed with a shared `common.util.JpaDateUtils
  .toLocalDate()` that re-wraps as `java.sql.Date` before converting, used everywhere a
  DATE-typed column needs to become a `LocalDate`. Watch for the same pattern if any future
  code reads `statementStartDate`/`statementEndDate` (currently always null, untested).
- Verified end-to-end against a throwaway Postgres: manual expense/income creation
  (category-type validated -- EXPENSE-only for /expenses, INCOME-only for /income, 400
  otherwise), list with every filter (date range, account, category, type, source) and
  pagination, get/update/delete, PATCH category with `createRule` correctly creating (and,
  on a second call, updating rather than duplicating) a `user_category_rules` row, and
  cross-user 404 on the ownership check.
- "Credit-card purchase/payment double-count avoidance" (design.md section 21) is only
  partially this phase's concern: Phase 7 just needs to correctly model and allow PAYMENT/
  TRANSFER/REFUND as transaction types (already done, exercised by the HDFC credit-card
  parser in Phase 6). The actual *exclusion* of those types from expense/credit totals is
  Phase 9's `AnalyticsService`'s job (design.md section 4's SUM rules) -- not implemented
  yet, noted here so it isn't missed.
- Design.md section 20's recalculation trigger ("Monthly summaries must be recalculated
  whenever transactions are added/deleted/updated/recategorized") is explicitly Phase 9's
  `AnalyticsService`. Phase 7's create/update/delete/patch-category methods do NOT call
  into any recalculation hook yet -- intentional, not an oversight, since that service
  doesn't exist until Phase 9.
- Phase 8 (2026-08-15): `ExpenseCategorizationService` orchestrates design.md section
  11's order (user rule -> global `RuleEngine` -> AI -> confidence) and is wired into
  `StatementTransactionPersister` right after each newly-saved (non-duplicate) PDF
  transaction is persisted, wrapped in its own try-catch there so a categorization bug
  never rolls back an otherwise-successful statement's transaction batch. Only DEBIT
  transactions lacking a category are considered -- manual expense/income always arrive
  with a user-picked category (Phase 7) and never reach this service.
  `UserCategoryRuleService` was extracted from `TransactionService` (which used it
  inline for the Phase 7 category-PATCH endpoint) into `categorization.service` so both
  it and the new categorization-review endpoints share one upsert path.
  There's no DB table for "global merchant rules" in the schema (only user-scoped
  `user_category_rules`), so `RuleEngine` is a small, explicitly non-exhaustive in-code
  merchant->category map -- easy to extend as more merchants are observed.
  Confidence routing reads design.md section 13 literally: both the 0.60-0.85 band and
  the below-0.60 band map to NEEDS_REVIEW per its own three bullet points (only >=0.85
  differs), so effectively only the auto-approve threshold branches anything today; the
  "review" threshold is kept in config as a documented no-op. FAILED is reserved
  separately for genuinely unusable AI output (parse failure, unknown category,
  out-of-range confidence) -- never for a low-but-valid confidence score.
  `transactions.source` (PDF/MANUAL/...) is deliberately left untouched by this whole
  engine -- it's the transaction's origin, not how its category was decided;
  provenance lives in `ai_categorization.status` + `confidence_score` +
  `is_category_modified` instead.
  `LocalLLMCategorizer` calls Ollama once per transaction rather than asking for one
  JSON array covering a whole batch -- a small local model reliably honoring an
  exact-array-length instruction is a real risk, and one bad response must not take
  down the rest of the batch. Each call is independently try-caught; genuine AI
  failures fall back to the OTHER category and a FAILED `ai_categorization` row, never
  a crash.
  No Ollama installation was available in this environment, so real-LLM behavior was
  not verified. Instead, built a small local HTTP stub (Python `http.server`) simulating
  Ollama's `/api/generate` response shape, driving every path deliberately: valid
  high-confidence, valid low-confidence, malformed JSON, a category name outside the
  valid list, and an out-of-range confidence value -- arguably a *more* thorough test of
  the resilience logic than a real (non-deterministic) model would give. Building the
  stub itself surfaced two unrelated infra gotchas worth remembering if this pattern
  gets reused: (1) Python's bare `http.server` declares HTTP/1.0 by default
  (`protocol_version` must be set to `"HTTP/1.1"` explicitly, or a client speaking
  proper HTTP/1.1 semantics gets confused mid-response); (2) Java's HTTP client sends
  the request body chunked (`Transfer-Encoding: chunked`) rather than with a
  `Content-Length` header, so a from-scratch Python handler must decode chunked
  request bodies, not just read `Content-Length` bytes.
  Verified end-to-end against a throwaway Postgres + RabbitMQ + the stub: user-rule
  match (confidence 1.0, no AI call), global-rule match (SWIGGY -> FOOD, confidence
  1.0), AI auto-approve, AI needs-review (correctly appears in `GET .../review` and
  nowhere else), AI failure paths (malformed JSON / bad category / out-of-range
  confidence, all correctly falling back to OTHER with a FAILED audit row), approve
  (flips status, category unchanged, drops out of the review list), and patch (category
  changed, `is_category_modified` set, audit row flipped to USER_CORRECTED, rule
  created) -- then re-uploaded a new transaction from the same corrected merchant and
  confirmed the newly-created rule caught it on the next pass (confidence 1.0, no AI
  call needed), closing the design.md section 14 feedback loop end-to-end.
- Phase 9 (2026-08-15): `AnalyticsService.recalculateMonth(userId, year, month)` recomputes
  and upserts `monthly_summary` + `category_monthly_summary` from raw transactions; wired
  into every mutation site design.md section 20 calls out -- `TransactionService`'s
  create/update/delete/patchCategory, `CategorizationReviewService.patch`, and
  `StatementTransactionPersister`'s batch import (once per distinct affected month, not
  once per transaction). An update that moves a transaction to a different month
  recalculates *both* the old and new month. A category that no longer has any matching
  transactions for a month (deleted/recategorized away) has its stale
  `category_monthly_summary` row deleted, not left behind with a stale amount.
  Synchronous paths (`TransactionService`, `CategorizationReviewService`) let a
  recalculation failure propagate and roll back -- design.md explicitly says "do not
  allow stale analytics," which here means fail loudly rather than silently accept
  staleness. The async PDF-batch path wraps it in try-catch instead (a future mutation
  for that user/month self-heals it; the transactions themselves already saved fine and
  a transient analytics hiccup shouldn't retroactively fail the whole statement).
  There's no account_id column on monthly_summary/category_monthly_summary (schema
  section 3), but the dashboard API accepts an optional accountId filter (section 19) --
  reconciled by having `DashboardService` read from those maintained tables only when
  accountId is absent (fast path); when present, it computes live from raw transactions
  via the same `TransactionAggregationRepository` queries (never persisted, since the
  summary tables can't represent a single-account view).
  The category breakdown deliberately filters to `categories.category_type = 'EXPENSE'`
  (not "any transaction type"), matching design.md section 19's own example exactly --
  an income category like SALARY mixed into the same list would show a nonsensical
  percentageOfExpenses over 100%.
  Verified end-to-end against a throwaway Postgres (RabbitMQ was unusually flaky this
  session -- see below): monthly dashboard reproduces design.md's exact example shape;
  multi-category percentage recomputation; delete correctly removing a category from the
  breakdown rather than leaving it stale; an update moving a transaction to a different
  month correctly recalculating both months (old month's expenses drop to zero, new
  month picks them up, remaining goes negative with expense_percentage correctly staying
  0 since total_credited is 0 for that month -- exactly per the section 4 zero-credit
  rule); yearly correctly summing monthly_summary rows and re-deriving percentages
  against yearly totals (not by summing monthly percentages, which wouldn't be valid);
  trends listing chronologically; account-filtered live query matching the persisted
  values; an unowned/nonexistent accountId correctly 404ing; and a second user with no
  data getting all-zeros/empty rather than an error. Did not re-verify the PDF-upload
  batch-recalculation path specifically in this session -- RabbitMQ repeatedly hit the
  same Docker Desktop `.erlang.cookie` flakiness seen in Phases 5/6/8, and the batching
  logic it would exercise (collect distinct months, call the same already-verified
  `recalculateMonth` once per month) is simple enough that code review plus the
  extensive manual-transaction verification above stands in for it; worth a real
  end-to-end pass in Phase 11 once Testcontainers-backed RabbitMQ testing exists.
- Phase 10 (2026-08-15): a dedicated ownership/authorization + sensitive-data-in-logs
  audit across all controllers/services came back clean -- every entity lookup used by
  a controller already filtered by the authenticated user's id (404, not 403, on
  cross-user access, consistent with the pattern established in Phases 3/5/7), and no
  passwords/tokens/PII are logged anywhere. Added the two remaining standard MVC
  exception mappings not yet handled: `HttpRequestMethodNotSupportedException` -> 405
  and `HttpMediaTypeNotSupportedException` -> 415 (both previously fell through the
  catch-all to a bare 500). Added `CorrelationIdFilter` (reads/generates
  `X-Correlation-Id`, puts it in MDC, echoes it back as a response header, registered
  at `Ordered.HIGHEST_PRECEDENCE` via `FilterConfig`) and wired `%X{correlationId}`
  into the console log pattern. Verified the header round-trip directly (custom header
  echoed back, absent header gets one generated) and the log pattern's MDC read
  (empty brackets `[]` outside request scope, proving the placeholder resolves rather
  than printing literally). Note for Phase 11: every current `log.*` call in the
  codebase lives in the async RabbitMQ-consumer path (`StatementProcessingService`,
  `StatementTransactionPersister`, `LocalLLMCategorizer`, `ExpenseCategorizationService`)
  -- MDC is thread-local and does NOT cross the listener-container thread boundary, so
  the correlation ID set by the HTTP filter does not currently propagate into those
  async log lines (the original HTTP request's ID would need to ride along on the
  RabbitMQ message and be re-seeded into MDC by the listener to close that gap -- not
  done here, flagging as a known limitation rather than in scope for this phase).
  Consequently there is not yet a synchronous, log-producing endpoint to directly
  observe a *populated* correlation-ID bracket in a log line -- the mechanism is
  verified via the response-header round trip and the log-pattern MDC-read behavior
  instead, both of which are individually sufficient to confirm correct wiring.
  Added Swagger `@Tag`/`@Operation` annotations to all 7 controllers; verified
  `/swagger-ui/index.html` (200) and `/v3/api-docs` (valid OpenAPI JSON, 22 paths, all
  7 tags present, `bearerAuth` scheme registered, `/auth/login` correctly has an empty
  `security` array while authenticated endpoints inherit the global requirement).
  Design.md section 27's statement-processing-duration and categorization-success/
  failure metrics were not built -- no metrics/observability requirement beyond
  correlation-ID logging and Actuator's default endpoints was explicitly requested
  this phase, and Actuator was already added in Phase 1; revisit only if the user
  asks for dedicated metrics later.

## Frontend-integration gaps (2026-08-15)

Backend was otherwise done (Phases 1-12); the pre-built React frontend surfaced 5
concrete gaps closed in this session, all within `expense-tracker-backend` only.

- **CORS**: was entirely absent (no bean, no `.cors(...)`, no `@CrossOrigin`
  anywhere) -- `SecurityConfig` gained a `corsConfigurationSource()` bean wired via
  `.cors(cors -> cors.configurationSource(...))` ahead of `.sessionManagement(...)`
  in the filter chain. Origins come from a new `CorsProperties`
  (`auth.security`, prefix `app.cors`, alongside `JwtProperties` since it's only
  ever consumed by `SecurityConfig`) bound to `app.cors.allowed-origins:
  ${CORS_ALLOWED_ORIGINS:http://localhost:5173}` in `application.yml` (5173 is the
  frontend's Vite dev port; no other origin was configured anywhere so no other
  default was needed). Methods GET/POST/PUT/PATCH/DELETE/OPTIONS, headers `*`,
  `allowCredentials(false)` (bearer-token auth via header, not cookies). Could not
  add `CORS_ALLOWED_ORIGINS` to `.env.example` -- that file is blocked by a
  blanket deny-on-`.env*` permission rule in this sandboxed session; the property
  and its default are fully documented in `application.yml` instead. Verified with
  a real preflight `OPTIONS` request: `http://localhost:5173` gets
  `Access-Control-Allow-Origin`/`-Methods`/`-Headers` back correctly, an
  unconfigured origin (`http://evil.example.com`) gets a 403 "Invalid CORS
  request" from Spring Security's own CORS filter.
- **`GET /api/v1/auth/me`**: added, `@AuthenticationPrincipal AuthenticatedUser`
  -> new `AuthService.getCurrentUser(UUID userId)` -> existing `UserResponse`
  (`{id, name, email}`, unchanged). `SecurityConfig` needed a matcher *more
  specific than and ordered before* the existing `/api/v1/auth/**` permitAll
  rule (`authorizeHttpRequests` matches in declaration order): added
  `.requestMatchers("/api/v1/auth/me").authenticated()` immediately above it.
  The class-level `@SecurityRequirements` (which told Swagger the *whole*
  controller was unauthenticated) was moved off the class and onto each of
  register/login/refresh/logout individually so `/me` correctly inherits the
  global bearer-auth requirement instead of also being marked no-auth.
  Verified: unauthenticated `/me` -> 401; authenticated -> 200 with the exact
  logged-in user's `{id,name,email}`; register/login/refresh/logout all still
  reachable with no token; `/v3/api-docs` confirms `/auth/me`'s `security` is
  absent (inherits global bearerAuth) while `/auth/login`'s is an explicit empty
  array, matching the Phase 10-verified pattern for the other three.
- **Category CRUD**: `CategoryController` was read-only (2 GETs, tagged
  "(read-only)"); added POST/PATCH/POST-deactivate. Categories have no
  `user_id` (confirmed in `V1__init_schema.sql`) so writes need only
  authentication, no ownership check -- already covered by
  `SecurityConfig`'s default `anyRequest().authenticated()` since
  `/api/v1/categories/**` isn't in any permitAll list.
  `V3__add_category_color.sql` adds `categories.color VARCHAR(7) NOT NULL
  DEFAULT '#64748b'` and backfills existing seeded rows with a rotating
  7-color palette keyed off insertion order (a `ROW_NUMBER() OVER (ORDER BY
  created_at, id) % 7` `CASE`); note the actual seed count in `V2` is 24 rows,
  not the 28 the frontend spec assumed -- doesn't matter, the backfill is
  count-agnostic. New rows always get an explicit caller-supplied `color`; the
  `DEFAULT` only protects the backfill.
  `CategoryResponse` gained `color` (String) and `transactionCount` (long,
  `TransactionRepository.countByCategoryId`, N+1 per tree node -- fine at this
  scale) -- both populated in `CategoryMapper.toResponse` for every node,
  root and nested children alike (it already recurses via `buildNode`).
  New DTOs `CategoryCreateRequest{name, parentId?, color, categoryType}`,
  `CategoryUpdateRequest{name?, parentId?, color?}` (PATCH semantics -- only
  non-null fields applied), `CategoryDeactivateRequest{replacementCategoryId?}`.
  `CategoryService.deactivate`: 0 transactions -> immediate `active=false`;
  >0 transactions with no `replacementCategoryId` -> 409 (same `ConflictException`
  pattern as account-deletion-blocked-by-use); `replacementCategoryId` equal to
  the category being deactivated -> 400; replacement not found/inactive -> 400;
  otherwise bulk-reassigns via a native `UPDATE transactions SET category_id =
  ... WHERE category_id = ...` (`TransactionRepository.reassignCategory`) and
  recalculates analytics. One correctness point beyond the original spec's
  literal wording: categories are *global*, shared across users, so a single
  deactivation can reassign transactions belonging to *several different
  users* -- unlike `TransactionService.patchCategory`'s single-user
  recalculation, this collects distinct `(userId, year, month)` triples (a
  private `UserMonth` record) across all reassigned transactions and calls
  `AnalyticsService.recalculateMonth` once per triple, not once per month.
  Verified end-to-end: created a category (color/type persisted), PATCHed its
  name+color, deactivated a zero-transaction category (204 immediate),
  attempted to deactivate a category with one transaction and no replacement
  (409), retried with the same category as its own replacement (400), then
  with a valid different active replacement (204) -- confirmed afterward via
  `GET` that the source category is `active:false`/`transactionCount:0`, the
  replacement shows `transactionCount:1`, the transaction's `categoryId`
  moved, and the affected month's dashboard breakdown recomputed to show the
  amount under the replacement category.
- **`DELETE /api/v1/statements/{id}`**: added to `StatementController` +
  `StatementService`, same `findByIdAndUsers_Id` 404-on-not-found-or-not-owned
  pattern as the existing GET. Checked `V1__init_schema.sql` before assuming
  anything about cascades: `transactions.statement_id` is `ON DELETE SET
  NULL` (so the DB alone would only orphan transactions, not remove them --
  wrong behavior here, transactions must actually be deleted), while
  `ai_categorization.transaction_id` is `ON DELETE CASCADE` (so deleting the
  transaction rows is sufficient; no explicit `ai_categorization` delete
  needed). One `@Transactional` service method (not self-invoked --
  called directly from the controller): load owned statement -> load its
  transactions (`TransactionRepository.findByStatements_Id`, new) -> collect
  distinct `YearMonth`s from their dates -> `deleteAll` the transactions (DB
  cascade removes their `ai_categorization` rows) -> call
  `AnalyticsService.recalculateMonth` once per distinct month (statements are
  user-scoped so, unlike category deactivation, every affected transaction
  belongs to the same user) -> delete the stored file via the already-existing
  `FileStorageService.delete` -> delete the statement row. Returns 204.
  Verified end-to-end: seeded a statement with a real transaction row and an
  `ai_categorization` row (direct SQL, simulating what the real PDF pipeline
  would have produced) in a month whose dashboard total was confirmed
  non-zero beforehand; after `DELETE`, the statement 404s, the transaction and
  its `ai_categorization` row are both gone from the DB, the month's dashboard
  total dropped back to zero, and the file was removed from disk. Also
  verified cross-user 404 on both this and the download endpoint below.
- **`GET /api/v1/statements/{id}/download`**: added; same ownership check.
  `FileStorageService` already had a symmetric `retrieve(String storageKey):
  InputStream` alongside `store`/`delete` -- no interface change needed. New
  `StatementService.downloadFile` reads it fully into a byte array (small
  `StatementFile(fileName, content)` DTO in `statement.dto`) and the
  controller returns `ResponseEntity<byte[]>` with
  `Content-Type: application/pdf` and `Content-Disposition: attachment;
  filename="<original fileName>"`. Verified: downloaded bytes are
  byte-for-byte identical to the originally-uploaded file, headers correct,
  cross-user access 404s.

Verified for real against a throwaway (Docker) Postgres + RabbitMQ, same
pattern as every other phase: registered two users, exercised every item
above end-to-end via curl (plus a couple of direct `psql` inserts to simulate
PDF-derived transaction/`ai_categorization` rows without needing a real
parseable bank statement), confirmed `./mvnw compile` and `./mvnw package
-DskipTests` both succeed, then tore the throwaway containers down.

## Phase details

### Phase 1 — Project setup, config, Flyway baseline, entity fixes
- pom.xml: add Spring Security, Validation, Actuator, AMQP (RabbitMQ), Flyway
  (+ postgres flyway module), PDFBox, springdoc-openapi, JJWT, Testcontainers, JUnit5,
  Mockito.
- Convert `application.properties` -> `application.yml` + `application-local.yml`.
- Flyway migrations: full schema (users, accounts, statements, transactions,
  categories, user_category_rules, ai_categorization, monthly_summary,
  category_monthly_summary, refresh_tokens) + seed initial categories.
- Fix entity `id` types to `UUID`, drop `catalog` attribute.
- Create empty package skeleton: auth, user, account, statement, transaction,
  category, categorization, ai, analytics, dashboard, common, config.
- `common`: ApiError response DTO + base domain exception + `@RestControllerAdvice`
  skeleton.
- `config`: base OpenAPI config (bearer JWT scheme wired in Phase 2).

### Phase 2 — Authentication
POST /api/v1/auth/register, /login, /refresh, /logout. BCrypt password hashing,
JWT access + refresh tokens (refresh persisted hashed in `refresh_tokens`),
Spring Security stateless filter chain, ownership-aware `@AuthenticationPrincipal`
plumbing used by every later module.

### Phase 3 — Accounts
POST/GET/GET-by-id/PUT/DELETE `/api/v1/accounts`, user-scoped.

### Phase 4 — Categories
Read APIs for hierarchical categories; repository groundwork for
`user_category_rules` (service logic lands in Phase 8).

### Phase 5 — Statement upload
`FileStorageService` interface + `LocalFileStorageService`, multipart upload
validation (extension/MIME/size/PDF validity), statement metadata persistence,
status UPLOADED -> PROCESSING, RabbitMQ event publish, immediate response.

### Phase 6 — PDF processing
`StatementParser` strategy interface + Hdfc/Icici/Sbi/Generic implementations,
RabbitMQ consumer driving parse -> normalize -> hash -> persist, `MerchantNormalizer`,
SHA-256 transaction fingerprinting + unique constraint enforcement, FAILED status
handling on parse errors.

### Phase 7 — Transactions
Manual expense/income creation, list with filters (date/account/category/type/
source/pagination), get/update/delete, PATCH category (with optional
user-rule creation), credit-card purchase/payment double-count avoidance.

### Phase 8 — Categorization engine + AI
`ExpenseCategorizationService` orchestrating `UserCategoryRuleService` ->
`RuleEngine` -> `ExpenseCategorizationAI` -> `ConfidenceEvaluator`; `LocalLLMCategorizer`
(Ollama); structured JSON output validation; confidence thresholds from config;
`ai_categorization` persistence; review APIs (GET review, approve, patch).

### Phase 9 — Analytics & Dashboard
`AnalyticsService` recalculating `monthly_summary` / `category_monthly_summary` on
every relevant transaction mutation; GET dashboard monthly/yearly/trends.

### Phase 10 — Cross-cutting hardening
Full ownership-validation audit across all controllers, refined global exception
mapping, complete Swagger/OpenAPI docs with bearer auth, correlation-ID logging
filter, no-sensitive-data-in-logs audit.

### Phase 11 — Testing
Skipped per explicit user decision (2026-08-15): no unit tests, no integration
tests. All verification for Phases 1-10 was done manually against real throwaway
Postgres/RabbitMQ instances via curl (see "Environment notes" above for what was
exercised each phase). Only the pre-existing `ExpenseTrackerApplicationTests`
context-load test remains, kept solely because it must pass for the build itself
to succeed.

### Phase 12 — Docker & docs
Dockerfile, docker-compose.yml (postgres + rabbitmq + backend), README.md.

Done (2026-08-15). `Dockerfile` is a two-stage build (`eclipse-temurin:21-jdk` for
`./mvnw package -DskipTests`, `eclipse-temurin:21-jre` for runtime, non-root
`appuser`, `COPY --from=build /build/target/*.jar` — safe against the
`-jar.original` file spring-boot-maven-plugin leaves behind since that has a
`.original` extension, not `.jar`). `docker-compose.yml` runs postgres:16-alpine +
rabbitmq:4-management-alpine (both with healthchecks; backend's `depends_on` waits
on `service_healthy` for both, not just container-started) + the backend, wired via
service DNS names, with named volumes for Postgres data and statement file storage.
`EXPENSE_AI_ENABLED` defaults to `false` in compose since no Ollama container is
bundled -- confirmed in code (`ExpenseCategorizationService`) that a disabled AI
flag cleanly skips straight to the OTHER-category fallback rather than erroring, so
this is a safe default rather than a hidden gap. `.env.example` documents every
override; `JWT_SECRET`'s compose fallback is the same non-secret placeholder
already committed in `application.yml` for local dev, explicitly called out in both
`.env.example` and `README.md` as something to change beyond a quick local trial.
Added `.env` and `storage/` to `.gitignore`.
Verified for real: built the full stack via `docker compose ... up --build` (a
separate `-p expense-tracker-verify` project name + all-remapped host ports, to
avoid clashing with this machine's other already-running Postgres/RabbitMQ
containers from unrelated projects), confirmed all three containers reached
`healthy`/`started`, `/actuator/health` returned UP (implicitly proving Flyway
migrations ran and the RabbitMQ connection came up, both required for a clean
boot), then exercised register -> login -> an authenticated `GET /categories` call
and `/swagger-ui/index.html` end-to-end through the compose network before tearing
the whole verification stack down (`down -v`, plus the temp env file removed).
README.md covers the docker-compose quickstart, the local (non-Docker) run path,
an API endpoint table, and how to turn on Ollama-backed categorization.
