FROM eclipse-temurin:21-jdk-alpine@sha256:6ea5548706b60ac0a602eaf48af74792cbab012d90e811ca8db6184b16b5c3d6 AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src src
RUN ./mvnw -DskipTests package

FROM eclipse-temurin:21-jre@sha256:7a65df4b22d2de92d4e04056e884f3b9122d70b21e2847fd66084278bd0ce037

WORKDIR /app

RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --home-dir /app --no-create-home --shell /usr/sbin/nologin app \
    && mkdir -p /app/data \
    && chown -R app:app /app

COPY --from=build --chown=app:app /workspace/target/schedule-system-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

USER app

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080"]

ENTRYPOINT ["java", "-jar", "app.jar"]
