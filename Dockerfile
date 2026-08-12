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
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
