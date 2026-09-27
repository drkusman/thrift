# Stage 1: build the frontend's static export (see frontend/next.config.ts - output: "export", no Node
# server needed at runtime). Built here, before the backend, so the jar can embed it directly.
FROM node:20-alpine AS frontend-build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend .
RUN npm run build:prod

# Stage 2: compiles the backend jar, with the frontend's static export baked into
# src/main/resources/static so Spring Boot serves it directly (see SpaResourceConfig) - one jar, one
# process, no separate frontend container or nginx routing between the two.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN chmod +x mvnw
# Warms the local Maven repo layer separately so it's cached across builds unless pom.xml changes.
RUN ./mvnw -q -B dependency:go-offline
COPY backend/src src
COPY --from=frontend-build /app/out src/main/resources/static
# application.yml itself is gitignored (holds a developer's local Postgres password) and so isn't in
# this build context at all - application.yml.example is the checked-in template for exactly this,
# and every value it hardcodes is overridden at runtime by the SPRING_* / THRIFT_* env vars docker-compose
# sets, so packaging it as-is is safe.
RUN cp src/main/resources/application.yml.example src/main/resources/application.yml
RUN ./mvnw -q -B package -DskipTests

# Runtime stage: just a JRE and the built jar - no Maven, no Node, no source, no build cache.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
