# BankSync

A Java 21 / Spring Boot 3.5 portfolio backend for atomic money transfers, optimistic concurrency control, and a transactional audit ledger. PostgreSQL is the deployment database; the default profile uses an isolated, ephemeral H2 database.

This is a runnable reference implementation, not a certified banking platform. The production profile adds service authentication and operational settings; the deployment responsibilities and functional boundaries below still apply.

## Quick start

Install Java 21. The Maven wrapper downloads verified Maven 3.9.11 automatically.

```bash
./mvnw verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

On Windows PowerShell, use `.\mvnw.cmd verify` and `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"` instead. Run these commands **inside the downloaded BankSync project folder**, never your entire Windows user folder.

The `demo` profile creates `ACC-1001` with 1000.00 and `ACC-1002` with 500.00. It is disabled when `prod` is active. Local data disappears when the process exits. Without `demo`, the database starts empty. The API intentionally has no unauthenticated balance-creation endpoint.

```bash
curl --fail-with-body -i -X POST http://127.0.0.1:8080/api/v1/transfers \
  -H 'Content-Type: application/json' \
  -d '{"fromAccountNumber":"ACC-1001","toAccountNumber":"ACC-1002","amount":25.50}'
```

PowerShell equivalent:

```powershell
$body = @{fromAccountNumber='ACC-1001'; toAccountNumber='ACC-1002'; amount=25.50} | ConvertTo-Json
Invoke-WebRequest -Method Post -Uri http://127.0.0.1:8080/api/v1/transfers -ContentType 'application/json' -Body $body
```

Success returns HTTP **204** with no response body. `/actuator/health` is the readiness probe. The local profile listens on loopback and requires no authentication; never deploy that profile publicly.

## Website

The application now serves a complete responsive website at its root path. Start the demo using the quick-start command above, then open `http://localhost:8080` in your own browser. The website and API run in the same Java process; no separate frontend server or Node build is required.

The website includes a financial overview, account cards, seven-day transfer activity, a two-step transfer review/confirmation dialog, searchable paginated transaction history, and CSV export of the displayed page. Balances, totals and history come from the backend. Navigation, dialogs and the mobile menu support keyboard interaction, and server failures are shown explicitly.

`GET /api/v1/dashboard` returns a consistent read-only snapshot, exact decimal strings, the first 100 accounts and the latest 100 ledger records. Totals include all accounts. `GET /api/v1/transactions?page=0&size=10&search=ACC-1001` provides server-side account search and pagination (maximum page size 50). The chart explicitly represents the latest 100 records within seven UTC days. Dates in the table use the browser's local timezone. The workspace uses USD as its single display currency.

The transfer form preserves decimal precision, checks available funds, asks for confirmation and refreshes the dashboard after completion. The server remains authoritative for funds and concurrency. An interrupted or ambiguous transfer is never automatically retried: the page warns you to check activity and balances first. Empty workspaces, no search results, unavailable history and authentication failures have explicit states. CSV text cells are escaped against spreadsheet formula injection.

Production protects the website and its read APIs with the same internal service credentials as transfers. Your browser uses the HTTP Basic sign-in challenge; credentials are not placed in JavaScript or local storage. An HTTPS ingress is required outside local development. A content security policy restricts scripts and assets to this application. This is an internal operator dashboard, not a customer login or an account-ownership authorization system.

### Optional real-browser checks

Node is needed only for browser automation:

```bash
npm ci
npx playwright install chromium
```

Start a **fresh, disposable** demo server in another terminal (these checks intentionally move demo funds):

```bash
java -jar target/banksync.jar --spring.profiles.active=demo --server.port=18080
```

Then run `npm run test:web`. The script refuses non-local URLs, non-demo workspaces and nonempty ledgers. It tests an actual transfer, exact balances, insufficient funds, search, pagination, CSV downloads, navigation, mobile rendering, keyboard dialogs, interrupted submissions and failed activity loading. Screenshots are written to `target/browser/`. To use an existing Chromium installation, set `BROWSER_EXECUTABLE` to its executable path. `BANKSYNC_WEB_URL` can override the disposable local server address.

## Phase 1 — multi-environment configuration

- `pom.xml`: Spring Web, JPA, PostgreSQL, H2, Validation, Lombok, Flyway, Security, Actuator and JUnit/MockMvc testing. Spring Boot manages compatible dependency versions.
- `application.properties`: local H2, schema validation, closed persistence sessions, no H2 console, UTC times, strict JSON binding, ordered updates.
- `application-prod.properties`: PostgreSQL with required `DB_URL`, `DB_USER`, `DB_PASSWORD`; Hikari maximum 20, minimum idle 5, connection timeout 30 seconds by default. Tune total pool connections across all replicas against the database connection budget. PostgreSQL lock timeout is 5 seconds and statement timeout is 15 seconds. SQL and bind-value logging are disabled.
- `BankSyncApplication.java`: executable application entry point.
- `db/migration/V1__create_ledger.sql`: versioned Flyway migration shared by H2 and PostgreSQL. Hibernate validates rather than mutates deployed schemas.

## Phase 2 — data integrity

`Account` stores money as `NUMERIC(19,2)` / `BigDecimal`, a unique immutable account number, and an optimistic `@Version` token. Database constraints reject negative balances. Never use floating point for money.

Hibernate updates the account only if its stored version still matches the read version. A stale write raises an exception; the transaction rolls back rather than overwriting another transfer. Both source and destination carry version checks, protecting shared destinations as well as sources.

`Transaction` is a separate, immutable JPA entity mapped to `transfer_transactions`, avoiding SQL reserved names. It records source, destination, amount, UTC timestamp, and `SUCCESS` / `FAILED` status. Only committed SUCCESS transfers are written by this workflow. A rejected operation rolls back and does **not** leave a FAILED row in the same transaction. Durable rejected-attempt auditing would require a deliberately separate audit transaction or external event pipeline.

The ledger has no mutation API. Hibernate immutability is not protection against database administrators, direct SQL, or repository deletion. Production permissions, retention and tamper-evident archives remain operational concerns.

## Phase 3 — atomic transfer engine

`TransferServiceImpl.executeTransfer` runs under `@Transactional(rollbackFor = Exception.class)`:

1. Validate account numbers, positive amount, decimal precision, distinct accounts and maximum balances.
2. Read both accounts and confirm funds are available.
3. Debit and credit with exact decimal arithmetic.
4. Save and flush the versioned account updates, with Hibernate ordering updates by ID.
5. Insert and flush the SUCCESS ledger row; commit the whole unit.

A flush is **not** a commit. If either update or the ledger write fails, the debit, credit and ledger all roll back. An optimistic conflict is translated to a safe domain conflict and rethrown, never swallowed inside a rollback-only transaction. Database concurrency failures also return a sanitized conflict response. No automatic transfer retry occurs.

## Phase 4 — validated HTTP API

`POST /api/v1/transfers` accepts only:

```json
{"fromAccountNumber":"ACC-1001","toAccountNumber":"ACC-1002","amount":25.50}
```

Account numbers accept 1–64 ASCII letters, digits or hyphens. Amounts require a non-null positive value, at most 17 integer digits and two decimal digits. Unknown JSON fields and trailing tokens are rejected. The service independently validates callers that bypass HTTP.

| Status | Meaning |
| --- | --- |
| 204 | Debit, credit and SUCCESS ledger committed |
| 400 | Invalid transfer, malformed JSON, precision, or destination overflow |
| 401 | Missing or incorrect production service credentials |
| 404 | An account was not found |
| 409 | Concurrent update or database concurrency conflict; this transfer rolled back |
| 422 | Insufficient funds; no money moved |
| 500 | Sanitized unexpected error; details stay in server logs |

Example application error (timestamps vary):

```json
{"timestamp":"2026-01-01T00:00:00Z","status":422,"code":"INSUFFICIENT_FUNDS","message":"Insufficient funds","path":"/api/v1/transfers","fieldErrors":{}}
```

Validation errors include field-specific messages, without echoing rejected financial data or Java stack traces. Authentication failures use Spring Security's HTTP Basic challenge.

## Phase 5 — concurrency and rollback tests

```bash
./mvnw verify
```

`TransferControllerConcurrencyTest` releases **exactly 10 executor workers** using a ready latch and a shared start latch. Operating-system scheduling cannot guarantee simultaneous execution within one millisecond. The assertions instead verify observable correctness: accepted requests match ledger rows and exact final balances, total money is conserved, funds cannot be spent twice, and balances never become negative.

Scenarios cover the same recipient, different recipients sharing a source, and opposite-direction transfers. Conflicts may return 409 and depleted balances may return 422; the test does not incorrectly require every racing request to succeed. All futures have timeouts and unexpected 500 responses fail the tests. A deterministic stale-version test also demonstrates that optimistic concurrency rejects an obsolete account update.

Additional tests cover malformed JSON, zero/negative/null/over-precision amounts, missing accounts, destination overflow, and a forced ledger failure **after both balances were flushed**. Production security tests check missing, wrong and correct credentials.

To run the same integration suite against a **disposable** PostgreSQL database, set `TEST_DB_URL`, `TEST_DB_USER`, and `TEST_DB_PASSWORD` before `./mvnw test`. The suite deletes account and ledger data before each test. Never point it at a real database. `.github/workflows/ci.yml` runs H2 tests, PostgreSQL tests, and a container build on GitHub Actions.

## Phase 6 — Docker and deployment

Requirements: Docker with Compose v2. Copy `.env.example` to `.env` and fill in your own database username/password and service username/password. Use a randomly generated API password of 16–72 ASCII characters. `.env` is ignored by Git and Docker build context.

```bash
cp .env.example .env
# Edit .env before proceeding.
docker compose up --build -d
docker compose logs -f app
```

PowerShell: `Copy-Item .env.example .env`, then edit `.env` and run the same Docker commands.

The multi-stage Dockerfile runs the tests during compilation, then copies only the executable JAR into a Java 21 JRE image. The runtime runs as a non-root user. Compose adds a read-only filesystem, writable temporary directory, dropped Linux capabilities, no-new-privileges, an internal database network, a persistent PostgreSQL volume and database health gating. The database has no host port. The app's host port is loopback-only by default; put it behind an HTTPS ingress for remote access.

Check `http://127.0.0.1:8080/actuator/health` using your local HTTP client. For transfers, send HTTP Basic credentials from your environment settings. Avoid typing real credentials into shared terminal histories.

Production creates **no funded accounts**. To seed a private demonstration after startup, open:

```bash
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Then execute this once on a fresh demonstration database:

```sql
INSERT INTO accounts (account_number, balance, version)
VALUES ('ACC-1001', 1000.00, 0), ('ACC-1002', 500.00, 0);
```

Stop with `docker compose down`. Data survives in the volume. `docker compose down -v` permanently deletes that local database volume.

For Render, Railway or AWS, deploy the Dockerfile, set `SPRING_PROFILES_ACTIVE=prod`, supply all five required database/API variables through the provider's secret manager, and configure `/actuator/health`. `DB_URL` must be a **JDBC** URL, e.g. `jdbc:postgresql://database-host:5432/banksync?sslmode=verify-full`; supply a trusted database certificate chain for verification. `PORT` defaults to 8080. The production profile listens on all interfaces for the platform ingress. No provider deployment is performed by this repository.

Restrictive build networks can pass a Maven proxy/mirror settings file through a BuildKit secret without baking it into the image:

```bash
docker build --secret id=maven_settings,src=/path/to/settings.xml -t banksync .
```

If a corporate proxy uses a private certificate authority, also pass a trusted Java certificate store with `--secret id=maven_truststore,src=/path/to/cacerts`. A proxy hostname known only through the host’s hosts file may require Docker’s `--add-host` option. TLS verification remains enabled. These build-only files are not copied into the runtime image.

## Production boundaries

- This is a **single-currency, internal service**. All callers share a privileged service identity; there is no customer ownership model, customer authorization, account-opening workflow, or payment-provider settlement.
- Requests have **no idempotency key**. Never blindly replay an HTTP timeout or ambiguous connection failure: the first request might have committed. A production payment API needs durable idempotency and reconciliation before safe automated retries.
- The ledger is a transfer journal, not a full double-entry general ledger. There are no fees, FX conversions, holds, outbox/events, balance reconciliation jobs, or tamper-proof audit storage.
- Run only the production profile outside private development. Require HTTPS, network controls, rate limits, managed secrets, backups with restore drills, monitoring/alerting, separate migration and least-privilege application roles, vulnerability scanning and load tests against your deployment's actual resource limits.
- Database and container versions are pinned to version lines; update them routinely and pin image digests for controlled releases. CI verifies correctness, not a guarantee of regulatory compliance or absence of vulnerabilities.

See `docs/VALIDATION.md` for the checks performed in the prepared cloud environment.
