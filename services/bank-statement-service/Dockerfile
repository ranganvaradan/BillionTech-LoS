FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY services/bank-statement-service/target/*.jar app.jar

ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -Xms256m -Xmx512m"

EXPOSE 8086

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
