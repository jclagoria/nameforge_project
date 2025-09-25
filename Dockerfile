# Build stage
FROM openjdk:21-jdk-slim AS build

# Install build dependencies
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy build files
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./
COPY src/ src/

# Build application
RUN chmod +x gradlew && \
    ./gradlew clean build -x test --no-daemon

# Runtime stage
FROM openjdk:21-jre-slim AS runtime

# Add non-root user for security
RUN groupadd -r nameforge && useradd -r -g nameforge nameforge

# Install runtime dependencies
RUN apt-get update && apt-get install -y \
    curl \
    dumb-init \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy built application
COPY --from=build /app/build/libs/nameforge-*.jar app.jar

# Application configuration
EXPOSE 8080 8081
USER nameforge

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["/usr/bin/dumb-init", "--"]
CMD ["java", "-jar", "app.jar"]
