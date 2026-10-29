# MedCore

Sistema Integral de Gestión Clínica y Analítica — Centro de Salud Acapulco / DIRESA Callao.

Arquitectura de microservicios con Spring Cloud, frontend web en React y aplicación móvil en React Native. Monorepo único.

---

## 1. Arquitectura

```
                   CLIENTES
      [React Web] [React Native] [DIRESA]
                      │ HTTPS
                ┌─────▼─────┐
                │ API GATEWAY│ :8080
                │ JWT + Rate │
                │ Limit+CORS │
                └─────┬─────┘
          ┌───────────┼───────────┐
          │      Spring Cloud     │
          │  Eureka :8761         │
          │  Config Server :8888  │
          └───────────────────────┘
   ┌──────┬──────┬──────┬──────┬──────┬──────┬──────┐
  auth  patient appoint medical  lab  invent analyt notif
  8081   8082    8083    8084   8085   8086   8087  8088

   [PostgreSQL] [MongoDB] [Redis] [Kafka KRaft]
```

### Servicios

| Servicio | Puerto | Dominio | Base de datos |
|---|---|---|---|
| `api-gateway` | 8080 | Enrutamiento, JWT, rate limit | Redis |
| `eureka-server` | 8761 | Service discovery | — |
| `config-server` | 8888 | Configuración centralizada | `infra/config-repo/` |
| `auth-service` | 8081 | Autenticación, JWT, roles | `auth_db` + Redis |
| `patient-service` | 8082 | Pacientes, historial | `patient_db` |
| `appointment-service` | 8083 | Citas, horarios, slots | `appointment_db` + Redis |
| `medical-service` | 8084 | Atenciones, órdenes, CIE-10 | `medical_db` |
| `laboratory-service` | 8085 | Muestras, resultados, HL7 | `lab_db` + MongoDB |
| `inventory-service` | 8086 | Reactivos, stock, alertas | `inventory_db` |
| `analytics-service` | 8087 | Epidemiología, brotes | MongoDB |
| `notification-service` | 8088 | Email, push, in-app | `notification_db` |

### Tópicos Kafka

| Tópico | Productor | Consumidores |
|---|---|---|
| `patient.created` | patient-service | notification-service, appointment-service |
| `appointment.created` | appointment-service | notification-service |
| `appointment.cancelled` | appointment-service | notification-service, medical-service |
| `lab-order.created` | medical-service | laboratory-service, notification-service |
| `sample.registered` | laboratory-service | log interno |
| `result.registered` | laboratory-service | analytics-service, notification-service |
| `result.critical` | laboratory-service | notification-service, analytics-service |
| `stock.low` | inventory-service | notification-service |
| `stock.expired` | inventory-service | notification-service |

---

## 2. Estructura del monorepo

```
medcore/
├── .github/workflows/            # ci, cd-backend, cd-frontend, code-quality
├── medcore-backend/
│   ├── pom.xml                   # POM padre multi-módulo
│   ├── infrastructure/           # api-gateway, eureka-server, config-server
│   ├── services/                 # los 8 microservicios
│   └── shared/medcore-commons/   # librería compartida (jar)
├── medcore-frontend/             # React 18 + Vite
├── medcore-mobile/               # React Native
├── infra/
│   ├── postgres/{init,seeds}/
│   ├── mongo/init/
│   ├── kafka/
│   └── config-repo/              # configuración que sirve el Config Server
├── docs/
├── docker-compose.yml            # stack completo
├── docker-compose.infra.yml      # solo bases de datos + Kafka
├── .env.example
└── Makefile
```

---

## 3. Convenciones

### G1 — Idioma y nomenclatura
Comentarios, nombres de variables de negocio y mensajes de log en español. Clases, métodos e identificadores técnicos en inglés: PascalCase para clases, camelCase para métodos, PascalCase para componentes React y camelCase con prefijo `use` para hooks.

### G2 — Respuesta estándar de la API
Toda respuesta REST usa el wrapper `ApiResponse` de `medcore-commons`:

```json
{
  "success": true,
  "message": "Paciente registrado correctamente",
  "data": { },
  "timestamp": "2026-10-29T10:30:00Z"
}
```

### G3 — Manejo de excepciones
Cada servicio tiene su `GlobalExceptionHandler`, que extiende el handler base de `medcore-commons`. Nunca se devuelve el stack trace al cliente. Siempre el código HTTP correcto (400, 401, 403, 404, 409, 422, 500) y un mensaje en español.

### G4 — Seguridad
Nunca se escriben credenciales en el código: se leen de variables de entorno o del Config Server con `${NOMBRE_VARIABLE}`. El archivo `.env` no se versiona; solo `.env.example` con valores de ejemplo.

### G5 — Convención de commits

| Prefijo | Uso | Ejemplo |
|---|---|---|
| `feat:` | Nueva funcionalidad | `feat(lab): parser ORU^R01` |
| `fix:` | Corrección de bug | `fix(appointment): doble reserva de slot` |
| `docs:` | Documentación | `docs: guía de deploy` |
| `test:` | Pruebas | `test(auth): JwtServiceImpl` |
| `refactor:` | Refactorización sin cambio funcional | `refactor(commons): ApiResponse` |
| `chore:` | Mantenimiento | `chore: actualizar dependencias` |
| `style:` | Formato de código | `style: formato de imports` |

### G6 — Tests
Toda clase de servicio tiene su test unitario con JUnit 5 y Mockito. Nombrado: `metodoBajoTest_escenario_resultadoEsperado()`, por ejemplo `registrarResultado_valorCritico_publicaEventoResultCritical()`.

### G7 — Documentación de la API
Todo controller lleva anotaciones de Springdoc OpenAPI: `@Tag` en la clase, `@Operation` y `@ApiResponse` en cada endpoint. El gateway agrega la documentación de todos los servicios en un único Swagger UI.

---

## 4. Reglas de microservicios

### MC1 — Independencia total
Cada microservicio tiene su propia base de datos y nunca accede a la de otro. La comunicación es por Feign Client o por evento Kafka. No se comparten tablas ni DataSources.

### MC2 — Orden de arranque
1. `postgres` + `mongodb` + `redis` + `kafka`
2. `eureka-server`
3. `config-server` (depende de Eureka healthy)
4. `api-gateway` (depende de Config healthy)
5. los 8 microservicios, en paralelo
6. `medcore-frontend`

En `docker-compose.yml` se declara con `depends_on` y `condition: service_healthy`. Los servicios Spring Boot necesitan `start_period: 90s` porque la JVM tarda en arrancar:

```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
  interval: 15s
  timeout: 5s
  retries: 5
  start_period: 90s
```

### MC3 — Configuración centralizada

| Archivo | Dónde vive | Qué contiene |
|---|---|---|
| `application.yml` | dentro del JAR | nombre del servicio, puerto, `spring.config.import` |
| `{servicio}.yml` | `infra/config-repo/` | URLs de BD, timeouts, features, con `${VARIABLES}` |
| `.env` | fuera del código | contraseñas, secretos, API keys |

El `application.yml` mínimo:

```yaml
spring:
  application:
    name: patient-service
  config:
    import: optional:configserver:${SPRING_CLOUD_CONFIG_URI:http://localhost:8888}
server:
  port: 8082
```

**Decisión del proyecto:** el Config Server corre siempre en modo `native`, leyendo `infra/config-repo/` desde disco. No existe repositorio `medcore-config` separado, y por lo tanto no hace falta token de GitHub ni perfil que clone configuración remota.

### MC4 — Headers propagados por el gateway
El gateway valida el JWT y propaga a los microservicios:

```
X-User-Id     userId extraído del JWT
X-User-Role   rol extraído del JWT
X-Request-Id  UUID generado por el gateway
```

Los microservicios confían en estos headers y no revalidan el JWT. Se leen con `@RequestHeader` o con el `HeaderAuthenticationFilter` de `medcore-commons` para poder usar `@PreAuthorize`.

Esa confianza solo es segura si los microservicios no son alcanzables desde internet. En Docker Compose se expone al host únicamente el puerto 8080; los demás viven en la red interna de Compose.

### MC5 — Kafka en modo KRaft
Imagen `apache/kafka:3.7.1`, sin Zookeeper. Variables obligatorias en `docker-compose.yml`:

```
KAFKA_NODE_ID=1
KAFKA_PROCESS_ROLES=broker,controller
KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:9093
KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093
KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092
KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1
KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1
KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1
KAFKA_AUTO_CREATE_TOPICS_ENABLE=true
```

Los tres factores de replicación en 1 son obligatorios porque hay un solo broker. `KAFKA_CONTROLLER_LISTENER_NAMES` y `KAFKA_LISTENER_SECURITY_PROTOCOL_MAP` también: sin ellas el broker no arranca.

### MC6 — HL7 en laboratory-service
HAPI HL7 con estructura v2.5.1, parseo de mensajes `ORU^R01`. `EquipmentAdapter` detecta si la trama es HL7 o ASTM y delega al parser correspondiente.

```
laboratory-service/src/main/java/com/medcore/laboratory/hl7/
├── config/HapiConfig.java
├── parser/OruR01Parser.java
├── parser/AstmMessageParser.java
├── parser/Hl7MessageValidator.java
├── builder/AckBuilder.java
├── mapper/Hl7ResultMapper.java
└── adapter/EquipmentAdapter.java
```

Endpoint de entrada: `POST /api/lab/hl7`. Si el parseo falla, se registra el error y se responde con un ACK de error (código `AE` o `AR` en el segmento `MSA`), generado por `AckBuilder`.

### MC7 — Plantilla de cada microservicio

```
{service}-service/
├── pom.xml
├── Dockerfile                    # multi-stage obligatorio
└── src/
    ├── main/
    │   ├── java/com/medcore/{service}/
    │   │   ├── {Service}Application.java
    │   │   ├── config/           # SecurityConfig, KafkaConfig, RedisConfig, OpenApiConfig
    │   │   ├── controller/
    │   │   ├── service/impl/
    │   │   ├── repository/
    │   │   ├── domain/
    │   │   │   ├── entity/
    │   │   │   ├── document/     # si usa MongoDB
    │   │   │   ├── dto/request/ + response/
    │   │   │   └── enums/
    │   │   ├── mapper/
    │   │   ├── event/producer/ + consumer/
    │   │   └── exception/
    │   └── resources/
    │       ├── application.yml
    │       ├── application-local.yml
    │       ├── application-prod.yml
    │       └── db/migration/     # Flyway
    └── test/
```

---

## 5. Entorno de desarrollo

Requisitos: JDK 21, Maven 3.9, Node 20+, Docker Desktop con backend WSL2.

```bash
# Solo infraestructura: Postgres, MongoDB, Redis, Kafka
docker compose -f docker-compose.infra.yml up -d

# Stack completo
docker compose up -d

# Un servicio suelto, desde medcore-backend/
./mvnw -pl services/patient-service -am spring-boot:run
```

Puntos de entrada locales:

| Recurso | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Eureka | http://localhost:8761 |
| Kafka UI | http://localhost:8090 |
| Mailpit | http://localhost:8025 |
| Frontend | http://localhost:3000 |

---

## 6. Despliegue

Modelo híbrido: el backend corre en el equipo de desarrollo y se publica por un túnel HTTPS; el frontend vive en Vercel.

```
[Navegador] ──HTTPS──> [Vercel: medcore-frontend]
                              │
                              │ VITE_API_BASE_URL
                              ▼
                    [ngrok: dominio estático]
                              │
                              ▼
            [laptop: docker compose, solo :8080 expuesto]
```

Reglas del despliegue:

- Por el túnel se expone **únicamente el puerto 8080** del gateway. Nunca 8761, 5432, 27017 ni 9092.
- El dominio de ngrok debe ser estático y reservado: la URL se compila dentro del build de Vercel y dentro del APK, así que no puede cambiar en cada arranque.
- En dominios gratuitos, ngrok responde una página intersticial a las peticiones con user-agent de navegador. El cliente HTTP del frontend y del APK deben enviar la cabecera `ngrok-skip-browser-warning: true`, o reciben HTML en lugar de JSON.
- CORS en el gateway con `allowed-origins: ${FRONTEND_ORIGIN}` y `allowed-headers: "*"`.
- `JWT_SECRET` de producción generado con `openssl rand -base64 64`. Nunca el valor de desarrollo.

Orden al montar una demostración: levantar el stack, verificar los contenedores healthy y los registros en Eureka, abrir el túnel, probar `https://<dominio>/actuator/health` desde fuera de la red local, y recién entonces compartir el enlace de Vercel.

---

## 7. Ramas

`main` es producción y solo recibe merges probados desde `develop`. `develop` es la rama de integración. Las funcionalidades se trabajan en ramas `feature/*` que vuelven a `develop` por Pull Request.

---

## 8. Documentación

| Documento | Contenido |
|---|---|
| `docs/arquitectura.md` | Diagramas y decisiones de arquitectura |
| `docs/microservicios.md` | Detalle de cada servicio |
| `docs/kafka-topics.md` | Contratos de los eventos |
| `docs/roles-permisos.md` | Matriz de roles y permisos |
| `docs/flujos.md` | Flujos de negocio extremo a extremo |
| `docs/hl7.md` | Integración HL7 y ASTM |
| `docs/api-reference.md` | Referencia de endpoints |
| `docs/deploy-local.md` | Levantar el entorno local |
| `docs/deploy-cloud.md` | Despliegue híbrido con túnel y Vercel |
| `docs/variables-entorno.md` | Catálogo de variables |

---

## Licencia

MIT. Ver [LICENSE](LICENSE).
