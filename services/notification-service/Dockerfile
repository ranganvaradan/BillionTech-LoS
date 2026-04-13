# ─── Stage 1: Build ─────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY pom.xml ./pom.xml
COPY services/discovery-service/pom.xml ./services/discovery-service/pom.xml
COPY services/api-gateway/pom.xml ./services/api-gateway/pom.xml
COPY services/iam-service/pom.xml ./services/iam-service/pom.xml
COPY services/enrollment-service/pom.xml ./services/enrollment-service/pom.xml
COPY services/los-core-service/pom.xml ./services/los-core-service/pom.xml
COPY services/notification-service/pom.xml ./services/notification-service/pom.xml
COPY services/lms-adapter-service/pom.xml ./services/lms-adapter-service/pom.xml

RUN apk add --no-cache maven
RUN mvn dependency:go-offline -pl services/notification-service -am -q 2>/dev/null || true

COPY services/discovery-service/src ./services/discovery-service/src
COPY services/notification-service/src ./services/notification-service/src
RUN mvn package -pl services/notification-service -am -DskipTests -q

# ─── Stage 2: Runtime ───────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
LABEL maintainer="BillionTech LOS"
WORKDIR /app
COPY --from=builder /build/services/notification-service/target/*.jar app.jar
EXPOSE 8084
ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
