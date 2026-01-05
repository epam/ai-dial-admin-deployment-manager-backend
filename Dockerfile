# Prepare runtime.
FROM amazoncorretto:21-alpine AS runtime
WORKDIR /app
EXPOSE 8080 9464

# SDK
FROM gradle:8.13-jdk21-alpine AS sdk
WORKDIR /build-workspace
COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .
COPY src/ src/

# Build.
FROM sdk AS build
WORKDIR /build-workspace
RUN gradle --no-daemon clean bootJar

# Final image.
FROM runtime AS final
COPY --from=build /build-workspace/build/libs/ai-dial-admin-deployment-manager-backend*.jar ./app.jar
ENV DEBUG_OPTS=""
ENTRYPOINT ["sh", "-c", "java ${DEBUG_OPTS} -jar app.jar"]
