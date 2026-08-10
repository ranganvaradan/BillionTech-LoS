FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY parent-pom.xml .
COPY pom.xml .
COPY src ./src
RUN mvn install -N -f parent-pom.xml -q
RUN mvn package -f pom.xml -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
# RUN apt-get add --no-cache wget \
#     && addgroup -S app && adduser -S app -G app \
#     && mkdir -p /data/documents && chown -R app:app /data /app

RUN apt-get update && \
    apt-get install -y wget && \
    groupadd -r app && \
    useradd -r -g app app && \
    mkdir -p /data/documents && \
    chown -R app:app /data /app && \
    rm -rf /var/lib/apt/lists/*
COPY --from=build --chown=app:app /build/target/*.jar app.jar
USER app
EXPOSE 8083
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.net.preferIPv4Stack=true -jar /app/app.jar"]
