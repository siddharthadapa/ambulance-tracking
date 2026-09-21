# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml first and download dependencies separately so this layer is
# cached and skipped on rebuilds where only source files changed, not pom.xml.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp package -DskipTests

# --- Runtime stage ---
# JRE only (not the full JDK) - smaller image, nothing here needs a compiler.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render/Railway/Fly.io all inject PORT at runtime and expect the app to bind
# to it - Spring Boot reads server.port from this env var automatically.
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
