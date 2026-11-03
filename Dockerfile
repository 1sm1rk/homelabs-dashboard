# syntax=docker/dockerfile:1

# Multi-stage build for the dashboard. BUILD CONTEXT = this repository's root:
#   docker build -t homelabs-dashboard .
#
# Single Maven module: the build compiles the app directly, no sibling checkout
# and no extra build step (there is no frontend build chain — the templates,
# htmx file and CSS ship as static resources inside the jar).
#
# The runtime stage ships ONLY the packaged fat jar. There are no native
# libraries in this service, so no `--enable-native-access` flag and no model
# mount are involved — same as `java -jar target/…jar` on the host. The app
# holds no database and no filesystem state: sessions live in memory, so
# nothing needs a volume. Tests run on the host via `mvn verify`, not inside
# the build (they need no Docker either).

# ---- Stage 1: build the jar (JDK 25, same Maven line as the host) ----
# Needs BuildKit/buildx (Docker's default builder since 23.x): the .m2 cache
# mount survives pom.xml changes, so dependency re-downloads stay rare.
FROM maven:3.10.0-eclipse-temurin-25-noble AS build
WORKDIR /build

# The pom first, so the dependency layer is cached across source changes.
COPY pom.xml ./pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp package -DskipTests

# ---- Stage 2: runtime (JRE 25, glibc-based) ----
FROM eclipse-temurin:25-jre-noble

# Non-root service user: the noble base image already ships an `ubuntu` user
# (uid/gid 1000) — reuse it instead of creating a duplicate-uid user.
WORKDIR /opt/dashboard

COPY --from=build --chown=ubuntu:ubuntu \
     /build/target/homelabs-dashboard-0.0.1-SNAPSHOT.jar app.jar

USER ubuntu
EXPOSE 8094

# The deployment data (issuer, client id/secret, projects API base URL) comes
# from the environment; the mappings live in src/main/resources/application.yml.
# JAVA_TOOL_OPTIONS is picked up by the JVM automatically (heap sizing etc.).
ENTRYPOINT ["java", "-jar", "/opt/dashboard/app.jar"]
