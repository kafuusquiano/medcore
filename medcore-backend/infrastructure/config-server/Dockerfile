# ============================================================
# config-server — build multi-stage.
#
# Contexto de build: medcore-backend/. Desde la raiz del repo:
#   docker build -f medcore-backend/infrastructure/config-server/Dockerfile \
#                -t medcore/config-server medcore-backend
#
# La configuracion NO se copia dentro de la imagen: infra/config-repo
# se monta como volumen de solo lectura en /config-repo, de modo que
# un cambio de configuracion no obliga a reconstruir la imagen.
# ============================================================

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY shared/medcore-commons/pom.xml shared/medcore-commons/
COPY infrastructure/config-server/pom.xml infrastructure/config-server/

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl infrastructure/config-server -am dependency:go-offline

COPY shared/medcore-commons/src shared/medcore-commons/src
COPY infrastructure/config-server/src infrastructure/config-server/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl infrastructure/config-server -am package -DskipTests


FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system medcore && useradd --system --gid medcore medcore

COPY --from=build /app/infrastructure/config-server/target/*.jar app.jar
RUN chown medcore:medcore app.jar
USER medcore

EXPOSE 8888

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75" \
    CONFIG_REPO_LOCATION="file:/config-repo"

HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=90s \
  CMD curl -f http://localhost:8888/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
