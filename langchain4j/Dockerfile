# ── Build stage ──────────────────────────────────────────────
FROM mcr.microsoft.com/openjdk/jdk:25-ubuntu AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    apt-get update && apt-get install -y maven && \
    mvn package -DskipTests -q

# ── Runtime stage ───────────────────────────────────────────
FROM mcr.microsoft.com/openjdk/jdk:25-distroless

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
