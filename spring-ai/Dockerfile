FROM mcr.microsoft.com/openjdk/jdk:25-ubuntu AS build
# The Maven wrapper downloads Maven as a zip
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN chmod +x mvnw && ./mvnw -B -ntp -DskipTests package

FROM mcr.microsoft.com/openjdk/jdk:25-ubuntu
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
# The base image already ships the non-root "app" user (uid 101)
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
