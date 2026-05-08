# ─── Stage 1: Build ─────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

COPY pom.xml ./pom.xml
COPY services/discovery-service/pom.xml ./services/discovery-service/pom.xml
COPY services/api-gateway/pom.xml ./services/api-gateway/pom.xml
COPY services/iam-service/pom.xml ./services/iam-service/pom.xml
COPY services/enrollment-service/pom.xml ./services/enrollment-service/pom.xml
COPY services/los-core-service/pom.xml ./services/los-core-service/pom.xml
COPY services/notification-service/pom.xml ./services/notification-service/pom.xml
COPY services/lms-adapter-service/pom.xml ./services/lms-adapter-service/pom.xml

RUN mvn dependency:go-offline -pl services/api-gateway -am -q 2>/dev/null || true

COPY services/discovery-service/src ./services/discovery-service/src
COPY services/api-gateway/src ./services/api-gateway/src
RUN mvn package -pl services/api-gateway -am -DskipTests -q

# ─── Stage 2: Runtime ───────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
LABEL maintainer="BillionTech LOS"
WORKDIR /app
COPY --from=builder /build/services/api-gateway/target/*.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
