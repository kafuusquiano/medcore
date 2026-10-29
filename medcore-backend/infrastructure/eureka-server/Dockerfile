# ============================================================
# eureka-server — build multi-stage.
#
# El contexto de build es medcore-backend/, no esta carpeta, porque
# el modulo necesita el POM padre y shared/. Desde la raiz del repo:
#   docker build -f medcore-backend/infrastructure/eureka-server/Dockerfile \
#                -t medcore/eureka-server medcore-backend
# ============================================================

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Primero los POM: si no cambian, Docker reutiliza la capa de dependencias.
COPY pom.xml .
COPY shared/medcore-commons/pom.xml shared/medcore-commons/
COPY infrastructure/eureka-server/pom.xml infrastructure/eureka-server/

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl infrastructure/eureka-server -am dependency:go-offline

COPY shared/medcore-commons/src shared/medcore-commons/src
COPY infrastructure/eureka-server/src infrastructure/eureka-server/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl infrastructure/eureka-server -am package -DskipTests


# ------------------------------------------------------------
# eclipse-temurin:21-jre trae curl, que es lo que usa el
# healthcheck del docker-compose (MC2).
# ------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# No correr como root.
RUN groupadd --system medcore && useradd --system --gid medcore medcore

COPY --from=build /app/infrastructure/eureka-server/target/*.jar app.jar
RUN chown medcore:medcore app.jar
USER medcore

EXPOSE 8761

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=90s \
  CMD curl -f http://localhost:8761/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
