# ============================================================
# api-gateway — build multi-stage.
#
# Contexto de build: medcore-backend/. Desde la raiz del repo:
#   docker build -f medcore-backend/infrastructure/api-gateway/Dockerfile \
#                -t medcore/api-gateway medcore-backend
#
# Es el unico servicio cuyo puerto se publica al host (MC4). Los ocho
# microservicios quedan solo en la red interna de Compose.
# ============================================================

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY shared/medcore-commons/pom.xml shared/medcore-commons/
COPY infrastructure/api-gateway/pom.xml infrastructure/api-gateway/

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl infrastructure/api-gateway -am dependency:go-offline

COPY shared/medcore-commons/src shared/medcore-commons/src
COPY infrastructure/api-gateway/src infrastructure/api-gateway/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl infrastructure/api-gateway -am package -DskipTests


FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system medcore && useradd --system --gid medcore medcore

COPY --from=build /app/infrastructure/api-gateway/target/*.jar app.jar
RUN chown medcore:medcore app.jar
USER medcore

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=90s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
