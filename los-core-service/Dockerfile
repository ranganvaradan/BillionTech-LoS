FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
ARG GIT_COMMIT=unknown
ARG GIT_COMMIT_ABBREV=unknown
ARG GIT_BRANCH=unknown
ARG GIT_COMMIT_TIME=unknown
ARG GIT_BUILD_TIME=unknown
COPY parent-pom.xml .
COPY pom.xml .
COPY src ./src
# Authoritative build identity for /actuator/info (Docker context has no .git).
RUN printf '%s\n' \
  "git.branch=${GIT_BRANCH}" \
  "git.build.time=${GIT_BUILD_TIME}" \
  "git.commit.id=${GIT_COMMIT}" \
  "git.commit.id.abbrev=${GIT_COMMIT_ABBREV}" \
  "git.commit.id.full=${GIT_COMMIT}" \
  "git.commit.time=${GIT_COMMIT_TIME}" \
  "git.dirty=false" \
  > src/main/resources/git.properties \
  && cat src/main/resources/git.properties
RUN mvn install -N -f parent-pom.xml -q
RUN mvn package -f pom.xml -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
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
