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
# Todos los pom del monorepo, no solo el de este servicio: el pom padre
# declara los once modulos y Maven exige que todos existan, aunque -pl
# construya uno solo. Se copian los pom y NO los src, para que la capa de
# dependencias siga cacheada y un cambio de codigo en otro servicio no
# invalide este build.
COPY shared/medcore-commons/pom.xml shared/medcore-commons/
COPY infrastructure/eureka-server/pom.xml infrastructure/eureka-server/
COPY infrastructure/config-server/pom.xml infrastructure/config-server/
COPY infrastructure/api-gateway/pom.xml infrastructure/api-gateway/
COPY services/auth-service/pom.xml services/auth-service/
COPY services/patient-service/pom.xml services/patient-service/
COPY services/appointment-service/pom.xml services/appointment-service/
COPY services/medical-service/pom.xml services/medical-service/
COPY services/laboratory-service/pom.xml services/laboratory-service/
COPY services/inventory-service/pom.xml services/inventory-service/
COPY services/analytics-service/pom.xml services/analytics-service/
COPY services/notification-service/pom.xml services/notification-service/

# Precarga de la cache de dependencias, no una verificacion: con
# -DfailOnError=false un corte de red descargando de Maven Central no
# tumba la imagen. Si falta algo de verdad, el package siguiente falla
# con el detalle real.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl infrastructure/api-gateway -am dependency:go-offline -DfailOnError=false

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
