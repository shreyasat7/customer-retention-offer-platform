# =============================================================================
#  Customer Retention Offer Platform — application image
#
#  Runtime-only by design. The WAR is produced once by the Maven build in the
#  pipeline and shipped unchanged into the image, so the artefact that was
#  tested is byte-for-byte the artefact that runs. Rebuilding the application
#  inside the image would mean testing one binary and shipping another.
#
#  eclipse-temurin:17-jre rather than :17-jre-alpine, because Temurin publishes
#  no linux/arm64 Alpine variant and this host is Apple silicon.
# =============================================================================

FROM eclipse-temurin:17-jre

LABEL org.opencontainers.image.title="Customer Retention Offer Platform" \
      org.opencontainers.image.description="Role-based platform for raising, approving and tracking customer retention offers" \
      org.opencontainers.image.source="https://github.com/shreyasat7/customer-retention-offer-platform" \
      org.opencontainers.image.licenses="MIT" \
      maintainer="shreyasat7"

# curl is required by HEALTHCHECK below.
# hadolint ignore=DL3008
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

ENV TZ=Asia/Kolkata \
    APP_PORT=8080 \
    CROP_DATA_DIR=/app/data \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

# Run as an unprivileged user: a container that does not need root should not have it.
RUN groupadd --system crop \
    && useradd --system --gid crop --home-dir /app --shell /usr/sbin/nologin crop

WORKDIR /app
COPY --chown=crop:crop target/crop.war /app/crop.war

# The H2 file database lives here. Declaring it a volume means a container can be
# replaced without losing the data, which is what the Task 14 rollback relies on.
RUN mkdir -p /app/data && chown -R crop:crop /app
VOLUME ["/app/data"]

USER crop
EXPOSE 8080

# Docker itself reports whether the application is up, so `docker ps` shows a health
# state rather than merely "running".
# Shell form is required here: the probe pipes curl into grep, which JSON form cannot do.
# hadolint ignore=DL3025
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -fsS "http://localhost:${APP_PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1

# JSON form, with an explicit shell, because $JAVA_OPTS must be word-split at runtime.
ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /app/crop.war"]
