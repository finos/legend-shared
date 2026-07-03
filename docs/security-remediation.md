# Security Remediation Register

**Scope:** all open Dependabot alerts of severity **critical, high, and medium** on `finos/legend-shared`.
**Baseline:** 29 open alerts as of 2026-07-03 (1 critical, 17 high, 11 medium), inventoried from the GitHub Dependabot API.
**Constraint:** the library keeps its Java 8 bytecode target (`maven.compiler.release=8` in the root pom). Fixes that require a Java 11+ baseline (Jetty 10+, pac4j 5.x, Dropwizard 2.1+) are deferred to the Phase 2 track at the bottom of this document, with rationale.

## How to use this document

Each remediation item below is sized to be executed **one at a time**, one PR per item. For each session:

1. Refresh the alert list and confirm the item's alerts are still open:
   ```sh
   gh api 'repos/finos/legend-shared/dependabot/alerts?state=open&severity=critical,high,medium&per_page=100' --paginate \
     --jq '.[] | [.number, .security_advisory.severity, .dependency.package.name, (.security_vulnerability.first_patched_version.identifier // "NONE"), (.security_advisory.cve_id // .security_advisory.ghsa_id)] | @tsv'
   ```
2. Pick the next `☐` item, respecting the wave order (Wave 1 items are order-independent among themselves; Wave 2 before Wave 3).
3. Apply the change described in the item (usually a one-property edit in the root `pom.xml`).
4. Run the item's **Verification** steps. `mvn -B install` runs tests *and* checkstyle for the whole reactor.
5. Confirm the resolved version with `mvn dependency:tree -Dincludes=<groupId>:<artifactId>` — exactly one version must appear, everywhere.
6. Open one PR per item; after merge, Dependabot re-scans and the alerts should auto-close. Update the item's status here (`☐` → `☑` with date/PR#) in the same PR or a follow-up.

Items must not be batched across waves in a single PR: a per-item history is what makes a bad bump bisectable and revertable.

## Register

| Item | Wave | Component | Current → Target | Alerts closed (severity) | Status |
|------|------|-----------|------------------|--------------------------|--------|
| V01 | 0 | `aquasecurity/trivy-action` (CI) | < 0.35.0 → 0.35.0 (SHA-pinned) | CVE-2026-33634 (critical) | ☑ 2026-07-03 |
| V02 | 1 | `com.hazelcast:hazelcast` | 5.3.1 → 5.3.8 | CVE-2023-45859 (high 7.6), CVE-2023-45860 (high 6.5) | ☑ 2026-07-03 |
| V03 | 1 | `net.minidev:json-smart` | 2.4.2 → 2.5.2 | CVE-2021-31684 (high 7.5), CVE-2023-1370 (high 7.5) | ☑ 2026-07-03 |
| V04 | 1 | `org.eclipse.jetty:*` (9 artifacts, one property) | 9.4.44.v20210927 → 9.4.57.v20241219 | CVE-2024-13009 (high 7.2), CVE-2024-8184 (med 5.9), CVE-2023-26048 (med 5.3), CVE-2023-40167 (med 5.3), CVE-2024-9823 (med 5.3) | ☑ 2026-07-03 |
| V05 | 1 | `spring-boot-autoconfigure` / `spring-test` (test scope) | 2.3.3 → 2.7.18 / 4.3.24 → 5.3.39 | CVE-2023-20883 (high 7.5) | ☑ 2026-07-03 |
| V06 | 2 | Jackson family + snakeyaml (coordinated) | jackson 2.10.5/2.10.5.1/2.11.2 → 2.18.8; snakeyaml 1.33 → 2.x | 12 alerts — see item | ☐ |
| V07 | 3 | `com.nimbusds:nimbus-jose-jwt` | 8.0 → ≥ 9.37.4 | CVE-2023-52428 (high 7.5), CVE-2025-53864 (med 5.8) | ☐ |
| D1 | deferred | `org.eclipse.jetty:jetty-http` | no fix in 9.4 line | CVE-2026-2332 (high 7.4) | accepted risk |
| D2 | deferred | `org.pac4j:pac4j-core` | fix is 5.7.10 (Java 11) | CVE-2026-40458 (high 6.5) | accepted risk |
| D3 | deferred | Dropwizard 1.3.29 EOL | 2.0.x is last Java-8 line | (enabler, no direct alert) | Phase 2 |
| D4 | watch | `jackson-databind` | no patched version exists yet | CVE-2026-54515 (med 5.3) | watch |
| D5 | deferred | `org.eclipse.jetty:jetty-http` | fix only in Jetty 12.0.12 | CVE-2024-6763 (med 3.7) | accepted risk |

All 29 baseline alerts are accounted for: V01 (1), V02 (2), V03 (2), V04 (5), V05 (1), V06 (12), V07 (2), D1 (1), D2 (1), D4 (1), D5 (1).

---

## Wave 0 — CI-only

### V01 — trivy-action supply-chain pin (CVE-2026-33634, critical)

- **What/why:** `.github/workflows/docker.yml` uses `aquasecurity/trivy-action` < 0.35.0, whose ecosystem was briefly compromised. CI-only; nothing shipped in the library is affected.
- **How:** bump the action reference to ≥ 0.35.0 in `docker.yml`. Prefer pinning by full commit SHA rather than tag (standard mitigation for action supply-chain risk).
- **Blast radius:** the Docker publish workflow only. No library consumer impact.
- **Verification:** workflow runs green on the PR (`docker.yml` triggers) or via a manual `workflow_dispatch` if it doesn't run on PRs.
- **Status:** ☑ 2026-07-03 — pinned to commit `57a97c7e` (tag 0.35.0). Workflow-green check pending on PR.

## Wave 1 — independent version-pin bumps (any order)

### V02 — Hazelcast 5.3.1 → 5.3.8 (CVE-2023-45859, CVE-2023-45860)

- **What/why:** missing permission checks in Hazelcast client protocol / CSV source connector; both fixed in 5.3.5. Target the latest 5.3.x patch (5.3.8) for the rest of the 5.3 fixes.
- **How:** root `pom.xml`: `<hazelcast.version>5.3.1</hazelcast.version>` → `5.3.8`.
- **Dependency path:** direct dependency of `legend-shared-pac4j` (used only by `HazelcastSessionStore`); reaches downstream apps through that module.
- **Blast radius:** small. Patch-level within 5.3.x, wire-format compatible; only affects deployments configured to use the Hazelcast session store. Note the recent `Fix hazelcast store (#257)` commit — the hazelcaststore tests are current and meaningful coverage.
- **Verification:** `mvn -B install` (the `hazelcaststore` tests in `legend-shared-pac4j` exercise the store end-to-end).
- **Status:** ☑ 2026-07-03 — full reactor build + tests green on 5.3.8.

### V03 — json-smart 2.4.2 → 2.5.2 (CVE-2021-31684, CVE-2023-1370)

- **What/why:** out-of-bounds read (fixed 2.4.4) and uncontrolled recursion DoS (fixed 2.4.9) in a JSON parser that sits on the OIDC token-parsing path.
- **How:** root `pom.xml`: `<json-smart.version>2.4.2</json-smart.version>` → `2.5.2`.
- **Dependency path (from `mvn dependency:tree`):** `legend-shared-pac4j-gitlab` / `-ping` → `com.nimbusds:nimbus-jose-jwt:8.0` → `net.minidev:json-smart` (version forced to 2.4.2 by the root dependencyManagement pin).
- **Blast radius:** low. json-smart 2.5.x is API-compatible for nimbus 8.0's usage; parses IdP-supplied JWT/JSON during OIDC login in downstream apps.
- **Note:** after V07 (nimbus 9.x) this dependency may leave the tree entirely — nimbus 9.x no longer declares json-smart. When executing V07, check the tree and retire this pin if it's gone.
- **Verification:** `mvn -B install`; `mvn dependency:tree -Dincludes=net.minidev:json-smart` shows only 2.5.2.
- **Status:** ☑ 2026-07-03 — build green; tree shows json-smart/accessors-smart 2.5.2 only.

### V04 — Jetty 9.4.44 → latest 9.4.x (CVE-2024-13009 + 4 mediums)

- **What/why:** closes CVE-2024-13009 (GzipHandler request-body leak, high), CVE-2024-8184 (ThreadLimitHandler DoS), CVE-2023-26048 (multipart OOM), CVE-2023-40167 ("+"-prefixed Content-Length smuggling vector), CVE-2024-9823 (DosFilter DoS) — plus already-published 9.4.x highs not currently alerting (CVE-2023-36478 HPACK, CVE-2023-44487 rapid reset, both fixed by 9.4.53).
- **How:** root `pom.xml`: `<jetty.version>9.4.44.v20210927</jetty.version>` → the newest `9.4.x` on Maven Central at execution time (≥ `9.4.57.v20241219`). One property drives all 9 pinned jetty artifacts.
- **Dependency path:** jetty artifacts arrive transitively via `dropwizard-jetty`/`dropwizard-jersey` (Dropwizard 1.3.29 wants 9.4.z of its own) and are overridden by the root dependencyManagement pins. `jetty-io` and `jetty-xml` are *not* pinned — they follow the pinned artifacts' own requirements, so confirm they land on the same version.
- **Blast radius:** the widest of Wave 1 — this is the HTTP engine of every Legend server. Mitigating: Dropwizard 1.3 is API-compatible with later 9.4.x patches (drop-in), and 9.4.x maintained strict patch-compat.
- **Explicitly NOT fixed:** CVE-2026-2332 (D1) and CVE-2024-6763 (D5) have no 9.4 fix. Merging this must not be read as "jetty is clean".
- **Verification:** `mvn -B install`; `mvn dependency:tree -Dincludes=org.eclipse.jetty:*` shows a single version for all artifacts including jetty-io/jetty-xml; boot the shaded server as a smoke test:
  ```sh
  java -cp legend-shared-server/target/legend-shared-server-*-shaded.jar \
    org.finos.legend.server.shared.staticserver.Server server legend-shared-server/src/test/resources/testConfig.json
  ```
  and confirm it serves the static root and `/admin/healthcheck`.
- **Status:** ☑ 2026-07-03 — build + tests green; tree shows all jetty artifacts (incl. unpinned jetty-io/jetty-xml) at 9.4.57.v20241219; shaded-jar smoke test served `/` (200) and a healthy `/admin/healthcheck`. Note: invoke the smoke test as `Server server <config-path>` — with a missing path it falls back to the bundled default config (AnonymousClient, port 8080).

### V05 — test-scope Spring bumps (CVE-2023-20883)

- **What/why:** `spring-boot-autoconfigure` 2.3.3.RELEASE (welcome-page DoS, fixed 2.5.15) and EOL `spring-test` 4.3.24. **Test scope only in `legend-shared-pac4j-gitlab` and others — never shipped to consumers.** Fixed for scanner hygiene, not real exposure.
- **How:** root `pom.xml`: `<spring.boot.autoconfigure.version>` → `2.7.18` (last 2.x line), `<spring.test.version>` → `5.3.39`. spring-test 4.3 → 5.3 may need small test adjustments (JUnit 4 runner API is stable; imports unchanged for `spring-test` basics).
- **Blast radius:** zero at runtime (test scope). Risk is limited to compile errors in test code; fix forward within the PR.
- **Verification:** `mvn -B install` (all module test suites).
- **Status:** ☑ 2026-07-03 — done. Gotcha found and fixed: spring-test 5.x `MockHttpServletResponse.addCookie` needs `spring-web` on the classpath (`NoClassDefFoundError: org/springframework/http/HttpHeaders`); added `org.springframework:spring-web` (same version property) as a test dependency in the root DM and in `legend-shared-pac4j`, `-kerberos`, `-ping`. Build green after.

## Wave 2 — Jackson family + snakeyaml, one coordinated PR

### V06 — Jackson 2.10.x → 2.18.8 with snakeyaml 2.x (12 alerts)

Closes, per Dependabot:

| CVE | Artifact | Severity | Fixed in |
|-----|----------|----------|----------|
| CVE-2026-54512 | jackson-databind | high 8.1 | 2.18.8 |
| CVE-2026-54513 | jackson-databind | high 8.1 | 2.18.8 |
| CVE-2022-42003 | jackson-databind | high 7.5 | 2.12.7.1 |
| CVE-2022-42004 | jackson-databind | high 7.5 | 2.12.7.1 |
| CVE-2020-36518 | jackson-databind | high 7.5 | 2.12.6.1 |
| CVE-2021-46877 | jackson-databind | high 7.5 | 2.12.6 |
| CVE-2025-52999 | jackson-core | high | 2.15.0 |
| CVE-2026-50193 | jackson-databind | medium | 2.14.0 |
| CVE-2026-54514 | jackson-databind | medium 5.3 | 2.18.8 |
| CVE-2025-49128 | jackson-core | medium 4.0 | 2.13.0 |
| GHSA-72hv-8253-57qq | jackson-core | medium | 2.18.6 |
| CVE-2022-1471 | snakeyaml | **high 8.3 (RCE)** | 2.0 |

- **Why one PR:** these cannot move independently. `jackson-dataformat-yaml` < 2.15 is hard-incompatible with snakeyaml 2.x, so the snakeyaml RCE fix *requires* the jackson bump; and mixed jackson module versions on one classpath is the classic runtime breakage. This is the transitive-ordering pivot of the whole register.
- **How:**
  1. Root pom properties: `jackson.version` 2.10.5 → `2.18.8`, `jackson.databind.version` 2.10.5.1 → `2.18.8`, `jackson.dataformat.yaml.version` 2.11.2 → `2.18.8`, `snakeyaml.version` 1.33 → the version `jackson-dataformat-yaml:2.18.8` declares (2.3 line; confirm from its pom at execution).
  2. **Align Dropwizard's transitive jackson modules.** From the real tree, `dropwizard-jackson:1.3.29` pulls at **2.9.10**: `jackson-datatype-guava`, `jackson-datatype-jsr310`, `jackson-datatype-jdk8`, `jackson-datatype-joda`, `jackson-module-parameter-names`, `jackson-module-afterburner`; `dropwizard-jersey` additionally pulls `jackson-jaxrs-json-provider`/`jackson-jaxrs-base`/`jackson-module-jaxb-annotations` at 2.9.10. Cleanest fix: import the Jackson BOM in `dependencyManagement` **before** other entries:
     ```xml
     <dependency>
         <groupId>com.fasterxml.jackson</groupId>
         <artifactId>jackson-bom</artifactId>
         <version>2.18.8</version>
         <type>pom</type>
         <scope>import</scope>
     </dependency>
     ```
     then verify no `com.fasterxml.jackson*` artifact resolves below 2.18.8 anywhere in the reactor.
- **Blast radius:** high-touch but well-tested territory:
  - Dropwizard 1.3 config parsing (`dropwizard-configuration` + dataformat-yaml + snakeyaml) is how **every downstream Legend app boots**. Dropwizard 1.3 was built against jackson 2.9; jackson's public API has been stable, but `jackson-module-afterburner` (bytecode generation) is the most likely runtime breakage — if the full suite or server boot fails on afterburner, exclude it from `dropwizard-jackson` in the root DM (Dropwizard falls back to plain reflection; minor perf cost, no functional change).
  - pac4j profile JSON round-tripping in `legend-shared-pac4j` (`SerializableProfile`, `StringOrArrayDeserializer` and its tests).
  - snakeyaml 2.x removes unsafe global-tag construction (the CVE). Dropwizard/jackson-dataformat-yaml don't rely on it; custom YAML anywhere in downstream apps that used snakeyaml 1.x `Constructor` semantics would — flag in the release notes of the next legend-shared release.
- **Exposure context (why this is urgent-but-not-fire):** the two 8.1 CVEs (2026-54512/13) require polymorphic default typing, which this repo does not use (verified: no `enableDefaultTyping`/`activateDefaultTyping` in source). CVE-2022-1471 requires attacker-controlled YAML; here snakeyaml parses trusted server config files. Both worth fixing regardless — downstream apps share this classpath and may have other exposure.
- **Verification:**
  - `mvn -B install` — full reactor, all tests, checkstyle.
  - `mvn dependency:tree | grep -E 'jackson|snakeyaml'` — every jackson artifact at 2.18.8, snakeyaml at its 2.x target, nothing left at 2.9.10/2.10.x/1.33.
  - Boot the shaded static server against a real YAML/JSON config (same smoke test as V04) — this exercises the Dropwizard config-parsing path end-to-end.
  - Deserialization round-trip tests in `legend-shared-pac4j` (`deserializer`, `mongostore`, `hazelcaststore` test packages) all green.
- **Status:** ☐

## Wave 3 — after Wave 2, needs runtime auth verification

### V07 — nimbus-jose-jwt 8.0 → ≥ 9.37.4 (CVE-2023-52428, CVE-2025-53864)

- **What/why:** DoS via expensive PBES2 `p2c` values (fixed 9.37.2) and deeply-nested JSON (fixed 9.37.4). Parses IdP-supplied tokens — genuinely attacker-reachable in OIDC deployments.
- **How:** root `pom.xml`: `<nimbus.jwt.version>8.0</nimbus.jwt.version>` → `9.37.4` (or latest 9.37.x/10.x compatible at execution time).
- **Dependency path:** `legend-shared-pac4j-gitlab` and `-ping` depend on nimbus **directly** (pinned 8.0) and exclude the copy from `pac4j-oidc:4.5.8`; `pac4j-oidc` also pulls `com.nimbusds:oauth2-oidc-sdk:8.22`, which was built against nimbus 8.x.
- **Blast radius / why last:** the OIDC login flow of every Legend app that uses `PingIndirectClient`/OIDC. The compile-time API used by this repo is small, but `oauth2-oidc-sdk:8.22` ↔ nimbus 9.x runtime compatibility is the real risk (`NoSuchMethodError` at login time, not at build time). Two-step approach:
  1. Bump nimbus alone; run the full build; then **manually verify a real OIDC login** against a test IdP (or at minimum a downstream Legend app's auth integration test) — unit tests here do not cover the flow.
  2. If incompatible, additionally pin `com.nimbusds:oauth2-oidc-sdk` to a 9.x version compatible with both nimbus 9.37.x and pac4j-oidc 4.5.8's API usage, and re-verify.
- **Cleanup:** check `mvn dependency:tree -Dincludes=net.minidev:json-smart` — nimbus 9.x drops json-smart; if absent, retire the V03 pin (leave a note here).
- **Verification:** full build; manual OIDC flow sign-in reaching a profile-authenticated endpoint; sequencing after V06 keeps jackson noise out of any failure triage.
- **Status:** ☐

---

## Deferred — documented risk, blocked by the Java 8 baseline

### D1 — Jetty CVE-2026-2332 (request smuggling via chunked-extension parsing, high 7.4)

Every 9.4.x release is affected and **the 9.4 line is EOL — no fix exists or is coming**. The fixed lines require Jetty 10/11/12 → Dropwizard ≥ 2.1/3.x → Java 11 baseline (Phase 2).
**Interim mitigation (operators):** request smuggling requires a parsing disagreement between a front proxy and Jetty. Legend deployments front these servers with a reverse proxy/LB; ensure it normalizes or rejects chunked-extension quoted strings and does not reuse backend connections across clients. Document in deployment guidance.
**Revisit:** Phase 2, or immediately if a 9.4 backport ever appears (check the alert).

### D2 — pac4j-core CVE-2026-40458 (CSRF, high 6.5; fixed in 5.7.10)

pac4j 5.x requires Java 11, and the upgrade is invasive far beyond a version pin:

- this repo subclasses/implements pac4j internals that changed across 4.x → 5.x: `LegendSecurityLogic` (extends `DefaultSecurityLogic`), `HttpSessionStore` (implements `SessionStore`), `JEEContext` usage, `LegendClientFinder`, custom matchers;
- session state is Java-serialized into MongoDB/Hazelcast via pac4j's `JavaSerializationHelper` (`MongoDbSessionStore`, `HazelcastSessionStore`) — pac4j profile classes changed shape across majors, so an upgrade **invalidates or errors on live sessions** during rolling deploys of downstream apps (users forced to re-login; mixed-version fleets may throw on deserialization).

**Exposure assessment:** legend-shared does not wire pac4j's CSRF authorizer/token machinery at all — authorizers come solely from app config (`LegendPac4jConfiguration.authorizers`, default empty). The vulnerable pac4j CSRF code paths are therefore not exercised by legend-shared's default wiring; a downstream app would have to opt into pac4j's `CsrfAuthorizer` in its own config to be exposed. Confirm per-app before treating this as urgent.
**Revisit:** Phase 2.

### D3 — Dropwizard 1.3.29 EOL (enabler, no direct alert)

Dropwizard 1.3 has been EOL since 2020 and is the structural blocker behind D1/D2. The last Java-8-compatible line is 2.0.x, which still ships Jetty 9.4 — so a 2.0 hop alone buys none of the deferred CVEs and is not worth its migration cost. The real path is Phase 2 below.

### D4 — jackson-databind CVE-2026-54515 (medium 5.3) — WATCH

Case-insensitive deserialization bypass; **no patched version exists yet** (vulnerable range `< 2.18.9`, fix expected in 2.18.9). Nothing to upgrade to; V06 lands us on 2.18.8. **Each session: re-run the alert query; when a patched version appears, fold it into a one-line patch bump.**

### D5 — Jetty CVE-2024-6763 (URI authority parsing, medium 3.7)

Fixed only in **Jetty 12.0.12** — even Jetty 10/11 are affected, so this outlives a Phase-2 Dropwizard 3.x migration unless that lands on Jetty 12 (Dropwizard 4.x/5.x territory). Low CVSS, parsing-hygiene issue. Accepted risk alongside D1.

## Phase 2 track (separate effort, FINOS/Legend-wide decision)

Unlocking D1/D2/D3 (and improving D5) requires raising the compiled bytecode target from Java 8 — a breaking change for any downstream consumer still building on Java 8, so it needs coordination across the Legend project (legend-engine, legend-sdlc, etc. already build on 11+; the constraint is published-artifact consumers). Sequence when approved:

1. Raise `maven.compiler.release` to 11 (major version bump of legend-shared).
2. Dropwizard 1.3 → 3.x (javax namespace retained, Jetty 10) or 4.x (jakarta namespace, Jetty 11) — decide against downstream servlet-API expectations; verify the `dropwizard-pac4j` compatibility matrix at that time.
3. pac4j 4.5.8 → 5.7.10+ (closes CVE-2026-40458): rewrite the subclassed internals listed in D2, plan a session-store invalidation/migration for Mongo/Hazelcast-persisted profiles, coordinate downstream deploys.
4. Jetty follows Dropwizard (closes CVE-2026-2332; CVE-2024-6763 only if the target line is Jetty 12).

## Out-of-scope hygiene noted during analysis (no open alert)

- `legend-shared-server/Dockerfile` base image `eclipse-temurin:11.0.17_8-jdk-jammy` is a 2022 JDK patch level — bump to a current temurin tag (and prefer `-jre`) next time the image is touched.
- The OpenTracing/Brave stack (opentracing deprecated upstream) and Jersey 2.23.2 are old but carry no open high/medium alerts; revisit in Phase 2.
