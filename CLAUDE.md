# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

Shared server-side infrastructure used across FINOS Legend applications: pac4j-based authentication, OpenTracing instrumentation, and a Dropwizard static-content server. Published to Maven Central as `org.finos.legend.shared:*`. Two tenets govern changes (from README): code must be genuinely used by the majority of Legend applications, and new dependencies are heavily scrutinized.

## Build and test

Maven multi-module build (parent pom at repo root, version `0.35.x`).

- Build everything: `mvn install`
- Build one module (with its deps): `mvn install -pl legend-shared-pac4j -am`
- Run all tests in a module: `mvn test -pl legend-shared-pac4j`
- Run a single test class: `mvn test -pl legend-shared-pac4j -Dtest=TestMongoDbSessionStore`
- CI runs: `mvn install javadoc:javadoc`

JDK: the enforcer plugin requires JDK 11, 17, 21, or 25 to *build* (CI uses 25), but `maven.compiler.release` is **8** — source code must stay Java 8 compatible (no `var`, no newer language features or APIs).

Surefire test reports from all modules aggregate into `legend-shared-test-reports/surefire-reports-aggregate`. Tests use JUnit 4 (plus Mockito; Mongo tests use an in-memory `mongo-java-server`).

## Checkstyle

`mvn verify` runs checkstyle with `checkstyle_legend.xml` and **fails on warnings**. Notable enforced style:

- Allman braces: opening brace on its own line (`LeftCurly` = `nl`), closing brace alone.
- No star imports; custom import order; no tabs; 4-space indentation.
- Every file needs the Apache 2.0 license header (see any existing `.java` file).

Match the existing brace/format style exactly when editing — checkstyle will block the build otherwise.

## Module architecture

**Authentication (pac4j):**
- `legend-shared-pac4j` — the core. `LegendPac4jBundle` is a Dropwizard bundle (extends `Pac4jBundle`) that Legend apps install to get authentication; it wires pac4j `Client`s from YAML config (`LegendPac4jConfiguration`), sets up security filters (`internal/SecurityFilterHandler`, `UsernameFilter`), and selects a session store. Session state can live in MongoDB (`mongostore/MongoDbSessionStore`, encrypted via `SessionCrypt`) or Hazelcast (`hazelcaststore/HazelcastSessionStore`), fronted by `internal/HttpSessionStore`; `sessionutil/SessionToken` manages the session cookie. `mongoauthorizer/MongoDbAuthorizer` authorizes against a Mongo collection.
- `legend-shared-pac4j-kerberos`, `-gitlab`, `-ping` — pluggable pac4j clients/authenticators for each identity provider, discovered via Jackson subtypes in app YAML config. The GitLab module includes personal-access-token auth and group-based authorization.

**Tracing (OpenTracing/Zipkin):**
- `legend-shared-opentracing-base` — core `OpenTracing` setup, span reporters, pluggable `AuthenticationProvider`s for the trace collector.
- `legend-shared-opentracing-jersey` — `JerseyClientSender` for shipping spans over Jersey.
- `legend-shared-opentracing-servlet-filter` — `OpenTracingFilter` and span decorators for inbound requests.
- `legend-shared-opentracing-test` — shared test helper (`ClientSenderTest`).

**Server:**
- `legend-shared-server` — Dropwizard static-content server (`staticserver/Server` is the main class) plus reusable bundles Legend apps install: `StaticServerBundle`, `LocalAssetBundle`/`LocalAssetServlet`, CORS (`CorsBundleWrapper`), `OpenTracingBundle`, `HtmlRouterRedirectBundle`, `HostnameHeaderBundle`. Has a `Dockerfile`; the docker image is built by the `docker.yml` workflow.

The dependency direction: `legend-shared-server` consumes the pac4j and opentracing modules; provider modules (`-kerberos`, `-gitlab`, `-ping`) depend on `legend-shared-pac4j`.

## Releases

Releases are cut via maven-release-plugin through GitHub workflows (`release.yml`); commits containing `[maven-release-plugin]` are skipped by CI. Don't bump versions manually.
