# Build stage: compiles the jar. Uses the project's own mvnw so the exact Maven version is pinned,
# same as local development.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw
# Warms the local Maven repo layer separately so it's cached across builds unless pom.xml changes.
RUN ./mvnw -q -B dependency:go-offline
COPY src src
# application.yml itself is gitignored (holds a developer's local Postgres password) and so isn't in
# this build context at all - application.yml.example is the checked-in template for exactly this,
# and every value it hardcodes is overridden at runtime by the SPRING_* / THRIFT_* env vars docker-compose
# sets, so packaging it as-is is safe.
RUN cp src/main/resources/application.yml.example src/main/resources/application.yml
RUN ./mvnw -q -B package -DskipTests

# Runtime stage: just a JRE and the built jar - no Maven, no source, no build cache.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
