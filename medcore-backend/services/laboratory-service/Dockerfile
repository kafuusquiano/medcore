# ============================================================
# laboratory-service — build multi-stage.
#
# Contexto de build: medcore-backend/. Desde la raiz del repo:
#   docker build -f medcore-backend/services/laboratory-service/Dockerfile \
#                -t medcore/laboratory-service medcore-backend
#
# Publica el 8085 porque los equipos del laboratorio escriben la trama HL7
# directamente al servicio, no al gateway. En produccion ese puerto solo debe
# ser alcanzable desde la VLAN del laboratorio: es la unica proteccion de
# /api/hl7/oru, que no puede exigir token porque los analizadores no lo envian.
# ============================================================

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY shared/medcore-commons/pom.xml shared/medcore-commons/
COPY services/laboratory-service/pom.xml services/laboratory-service/

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl services/laboratory-service -am dependency:go-offline

COPY shared/medcore-commons/src shared/medcore-commons/src
COPY services/laboratory-service/src services/laboratory-service/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl services/laboratory-service -am package -DskipTests


FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system medcore && useradd --system --gid medcore medcore

COPY --from=build /app/services/laboratory-service/target/*.jar app.jar
RUN chown medcore:medcore app.jar
USER medcore

EXPOSE 8085

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=90s \
  CMD curl -f http://localhost:8085/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
