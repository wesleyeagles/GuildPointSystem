# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
# Cache do Maven no servidor de build: as dependências não são baixadas de novo a cada deploy,
# mesmo quando o cache de camadas do Docker se perde.
RUN --mount=type=cache,target=/root/.m2 chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl su-exec \
    && addgroup -S guild \
    && adduser -S guild -G guild \
    && mkdir -p /data/uploads \
    && chown -R guild:guild /data/uploads

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# O Coolify espera o start-period inteiro antes da 1ª checagem: curto, com checagens frequentes.
# 40 s + 20 x 10 s = 4 min de margem para o Spring e o Flyway subirem.
HEALTHCHECK --interval=10s --timeout=10s --start-period=40s --retries=20 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"
ENV UPLOAD_PATH=/data/uploads

COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]
