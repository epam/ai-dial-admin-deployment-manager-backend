# Build stage
FROM gradle:8.14-jdk21-alpine AS builder
WORKDIR /build-workspace

COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .
COPY src/ src/

RUN gradle --no-daemon clean bootJar

# Runtime stage
FROM amazoncorretto:21-alpine AS runtime
WORKDIR /app

RUN adduser -u 1001 --disabled-password --gecos "" appuser && \
    mkdir -p /app/data && \
    chown -R appuser:appuser /app

RUN apk add --no-cache bash

COPY --from=builder --chown=appuser:appuser /build-workspace/build/libs/ai-dial-admin-deployment-manager-backend*.jar ./app.jar
COPY --chown=appuser:appuser docker-entrypoint.sh /usr/local/bin/

RUN chmod +x /usr/local/bin/docker-entrypoint.sh

RUN apk add --no-cache zlib=1.3.2-r0 libcrypto3=3.5.6-r0 libssl3=3.5.6-r0

USER appuser

EXPOSE 8080 9464

HEALTHCHECK --start-period=30s --interval=1m --timeout=3s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/v1/health || exit 1

ENTRYPOINT ["docker-entrypoint.sh"]
