# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependency downloads before copying sources
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY mvnw mvnw.cmd .mvn ./
RUN chmod +x mvnw
COPY src ./src

RUN mvn -B -q -DskipTests package

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Payment screenshots are stored locally -> keep them on a persistent volume.
RUN mkdir -p /data/screenshots && chmod 755 /data/screenshots

ENV TZ=UTC \
    SCREENSHOTS_DIR=/data/screenshots \
    JAVA_TOOL_OPTIONS="-Xms256m -Xmx1024m"

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]