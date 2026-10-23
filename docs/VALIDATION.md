# Validation evidence

The prepared cloud workspace ran the following checks successfully with Java 21, Maven 3.9.11 and Spring Boot 3.5.16:

| Check | Result |
| --- | --- |
| `./mvnw verify` with default H2 | 19 tests, 0 failures, 0 errors, 0 skipped; executable JAR built |
| `./mvnw test` with disposable PostgreSQL 17 | Same 19 tests passed; migrations applied and schema validated |
| Multi-stage Docker build | Passed; all 19 tests executed in the Maven/JDK build stage |
| Compose configuration validation and production startup | Passed; PostgreSQL health gate and production Flyway migration succeeded |
| Real HTTP demo health / valid transfer / insufficient funds | UP / 204 / sanitized 422 |
| Real HTTP production authentication | Missing credentials 401; correct credentials accepted |
| Real production PostgreSQL transfer | 1000.00 / 500.00 became 974.50 / 525.50; sum remained 1500.00; exactly one SUCCESS ledger row |
| Rejected production transfer | Insufficient funds returned 422; balances and committed ledger count unchanged |
| Runtime container | Non-root banksync user; read-only root filesystem |
| Reusable cloud install script | Previously verified JDK installation and Maven-wrapper setup; its build command now runs the expanded 19-test suite |

The 19 tests comprise 11 transfer integrity tests, 5 dashboard/history/static-resource integration tests and 3 production security tests. Concurrency scenarios each release ten workers behind a shared latch. The suite verifies accepted-request accounting and money conservation without assuming all competing transfers succeed. A stale-version write is also rejected deterministically. A mocked ledger-storage failure verifies rollback after balance updates were flushed.

Cloud Maven downloads required the existing HTTPS egress proxy. The container build additionally needed the host's proxy DNS mapping and trusted Java certificate store through temporary build mounts. TLS, repository-signature, Maven distribution checksum and container layer verification remained enabled.

No remote cloud deployment, GitHub push, sustained load test, disaster-recovery exercise, regulatory assessment or vulnerability scan was performed. GitHub Actions configuration is included but has not run on GitHub. The source is a portfolio reference implementation with the boundaries described in the README.

## Website validation

The website is served directly from the Spring Boot executable JAR. No Node build or separate frontend server is required in production.

- The checked-in browser smoke script was executed with `npm ci` and real Chromium against a fresh disposable H2 demo.
- A transfer was submitted through the review/confirmation form; exact displayed account balances and conserved total funds were verified after the API committed.
- Insufficient funds, server-side account search, ten-row pagination, CSV download contents, account/activity navigation, keyboard dialog dismissal, and a 390-pixel mobile viewport were checked.
- An aborted transfer request produced an unknown-outcome warning, disabled immediate resubmission and sent exactly one request. A separate failed history request produced an explicit error state and disabled export.
- Browser checks completed with no uncaught JavaScript errors. Desktop and mobile screenshots are included under `docs/screenshots/`.
- The rebuilt multi-stage Docker image ran all 19 Java tests successfully.
- A real browser authenticated to the production-profile Docker website backed by PostgreSQL, rendered its private workspace and completed a 1.25 transfer. The source displayed 998.75 and the combined balance remained 1500.00. Anonymous dashboard access returned 401.
- The final 19-test suite also passed independently against PostgreSQL, including the dashboard aggregates, paginated searches and read APIs.

The GitHub Actions workflow now includes the browser checks and screenshot upload. Those workflow changes have been validated locally through the equivalent commands; the workflow itself has not been run on GitHub.

## GitHub Pages browser demo

A separate, explicitly labeled static demo is generated into `docs/` by `scripts/build-pages.cjs`. Chromium verified relative assets under a project subpath, simulated transfers with conserved sample balances, account search, CSV downloads, reload reset, and a 390-pixel mobile layout. No banking API requests or browser exceptions occurred. This simulation is tab-local and makes no database transaction or concurrency guarantees. The Java/PostgreSQL application remains separate. Repository Pages configuration and the public deployment could not be inspected or enabled from the cloud machine because the GitHub settings API returned Forbidden. Hosting requires selecting `main` and `/docs` under repository Settings → Pages.
