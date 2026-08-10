FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY parent-pom.xml .
COPY pom.xml .
COPY src ./src
RUN mvn install -N -f parent-pom.xml -q
RUN mvn dependency:go-offline -f pom.xml -q 2>/dev/null || true
RUN mvn package -f pom.xml -DskipTests -q

FROM eclipse-temurin:17-jre
LABEL maintainer="BillionTech LOS"
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8761
ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
