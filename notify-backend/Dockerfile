# ─────────────────────────────────────────────
#  Stage 1 — Build
# ─────────────────────────────────────────────
FROM maven:3.9.7-eclipse-temurin-17 AS builder

WORKDIR /build

# Cache dependency layer — only re-downloads when pom.xml changes
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build (skip tests — tests run in CI, not in image build)
COPY src ./src
RUN mvn clean package -DskipTests -q

# ─────────────────────────────────────────────
#  Stage 2 — Runtime (minimal JRE image)
# ─────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Non-root user for security best practice
RUN addgroup -S notifyflow && adduser -S notifyflow -G notifyflow
USER notifyflow

COPY --from=builder /build/target/notifyflow-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "app.jar"]