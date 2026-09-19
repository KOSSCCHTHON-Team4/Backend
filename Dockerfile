# syntax=docker/dockerfile:1

FROM eclipse-temurin:21.0.12_8-jdk-noble@sha256:4d271cd5e0624598cf563342f47281b09cb364bc13acbbd7251f49f83470018d AS build

WORKDIR /workspace

COPY gradlew build.gradle.kts settings.gradle.kts lombok.config ./
COPY gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties gradle/wrapper/
COPY src/main/java/ src/main/java/
COPY src/main/resources/application.yml src/main/resources/application-prod.yml src/main/resources/
COPY src/main/resources/db/migration/ src/main/resources/db/migration/

RUN chmod 0755 gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21.0.12_8-jre-noble@sha256:7739f0ffce786528961eea6bf46d9610ee968ac6127c9b2e93494757bdecce9f AS runtime

RUN groupadd --gid 10001 emotionmap \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin emotionmap \
    && install --directory --owner=10001 --group=10001 --mode=0750 /var/lib/emotionmap/uploads

WORKDIR /app

COPY --from=build --chown=0:0 /workspace/build/libs/*.jar /app/app.jar
RUN chmod 0444 /app/app.jar

USER 10001:10001

ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "/app/app.jar"]
