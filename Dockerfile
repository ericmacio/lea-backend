# syntax=docker/dockerfile:1.7

# ============================================
# Stage 1 - Build
# ============================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests \
    && mv target/*.jar target/app.jar

# ============================================
# Stage 2 - Runtime
# ============================================
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build /build/target/app.jar app.jar

USER spring:spring

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"
ENV SERVER_PORT=8080

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-$SERVER_PORT} -jar app.jar"]
