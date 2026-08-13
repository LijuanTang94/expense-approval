# syntax=docker/dockerfile:1

# ---- Stage 1: build the React SPA ----
FROM node:22-alpine AS frontend
WORKDIR /fe
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build   # -> /fe/dist (VITE_API_URL="" from .env.production => same-origin /api)

# ---- Stage 2: build the Spring Boot jar, embedding the SPA ----
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Serve the built SPA from the app itself (classpath:/static).
COPY --from=frontend /fe/dist ./src/main/resources/static
RUN mvn -q -B clean package -DskipTests

# ---- Stage 3: slim runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar

# Run as an unprivileged user: nothing here needs root, and a container process that starts as
# root keeps root's capabilities if it's ever compromised.
RUN addgroup -S app && adduser -S -G app app && chown -R app:app /app
USER app

EXPOSE 8081
# Lets Docker/compose distinguish "container started" from "app actually serving".
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
