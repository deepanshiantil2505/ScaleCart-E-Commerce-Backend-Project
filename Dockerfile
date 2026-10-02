# syntax=docker/dockerfile:1
# ---------------------------------------------------------
# Build Stage: Compile and Package the Spring Boot JAR
# ---------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and pom.xml first to leverage Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Pre-fetch project dependencies
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B

# Copy application source code
COPY src ./src

# Build production executable JAR (skipping unit tests during image build)
RUN ./mvnw clean package -DskipTests -B

# ---------------------------------------------------------
# Production Runtime Stage: Ultra-slim JRE container
# ---------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# Install curl for container health check
RUN apk --no-cache add curl && \
    addgroup -S scalecart && adduser -S scalecart -G scalecart

# Copy compiled JAR from builder stage
COPY --from=builder /build/target/scalecart-backend-*.jar app.jar

# Run as non-privileged user for container security
USER scalecart

# Expose standard Spring Boot HTTP port
EXPOSE 8080

# Configure production-tuned JVM options
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Container Healthcheck
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
