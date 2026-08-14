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
| 8 | Categorization engine + AI abstraction (Ollama) + review APIs | not started |
| 9 | Analytics & Dashboard (monthly/yearly/trends, recalculation) | not started |
| 10 | Cross-cutting hardening (security review, global exceptions, Swagger, observability) | not started |
| 11 | Testing (unit + integration w/ Testcontainers) | not started |
| 12 | Docker, docker-compose, README | not started |

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
Unit tests: MerchantNormalizer, RuleEngine, CategorizationService,
ConfidenceEvaluator, financial calculations, duplicate detection.
Integration tests (Testcontainers Postgres): auth, accounts, transactions,
statement upload, dashboard.

### Phase 12 — Docker & docs
Dockerfile, docker-compose.yml (postgres + rabbitmq + backend), README.md.
