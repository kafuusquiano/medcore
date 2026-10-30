# ============================================================
# analytics-service — build multi-stage.
#
# Contexto de build: medcore-backend/. Desde la raiz del repo:
#   docker build -f medcore-backend/services/analytics-service/Dockerfile \
#                -t medcore/analytics-service medcore-backend
#
# No publica puerto al host: solo el gateway lo alcanza (MC4).
# ============================================================

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY shared/medcore-commons/pom.xml shared/medcore-commons/
COPY services/analytics-service/pom.xml services/analytics-service/

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl services/analytics-service -am dependency:go-offline

COPY shared/medcore-commons/src shared/medcore-commons/src
COPY services/analytics-service/src services/analytics-service/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl services/analytics-service -am package -DskipTests


FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system medcore && useradd --system --gid medcore medcore

COPY --from=build /app/services/analytics-service/target/*.jar app.jar
RUN chown medcore:medcore app.jar
USER medcore

EXPOSE 8087

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=90s \
  CMD curl -f http://localhost:8087/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
