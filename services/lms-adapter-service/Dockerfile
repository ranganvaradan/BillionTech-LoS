FROM eclipse-temurin:21-jre-alpine
LABEL maintainer="BillionTech LOS"
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8085
ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
