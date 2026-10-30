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
| `notification-service` | 8088 | Correo y avisos in-app | `notification_db` |

Los once están implementados, con 216 pruebas. `laboratory-service` es el único que usa dos bases a la vez, y `analytics-service` el único sin Postgres: sus datos son series y agregados, no entidades relacionadas.

En `notification-service`, los canales de push y SMS están declarados pero **no implementados**: Firebase necesita un proyecto con credenciales y el SMS una pasarela con contrato. Un envío por esos canales se marca como omitido con su motivo, en lugar de simular que salió.

### Tópicos Kafka

Nueve tópicos, todos con productor y consumidores reales a los dos lados:

| Tópico | Productor | Consumidores |
|---|---|---|
| `patient.created` | patient-service | notification-service, appointment-service |
| `appointment.created` | appointment-service | notification-service |
| `appointment.cancelled` | appointment-service | notification-service, medical-service |
| `lab-order.created` | medical-service | laboratory-service, notification-service |
| `sample.registered` | laboratory-service | notification-service |
| `result.registered` | laboratory-service | analytics-service, notification-service |
| `result.critical` | laboratory-service | analytics-service, notification-service |
| `stock.low` | inventory-service | notification-service |
| `stock.expired` | inventory-service | notification-service |

`notification-service` consume los nueve y no publica ninguno: es el extremo de todas las cadenas. `sample.registered` lleva dos desenlaces distinguidos por `eventType` — la muestra aceptada avisa al paciente y la rechazada al médico.

Reglas que valen para todos los consumidores del sistema:

- **Idempotencia obligatoria.** Kafka entrega al menos una vez, así que cada consumidor necesita una clave natural con índice único que descarte el reenvío: `idResultado` en analytics, `(event_id, tipo, canal)` en notification, el identificador de la orden en la cola del laboratorio. Sin eso, un reenvío cuenta dos veces un caso epidemiológico o manda dos veces el mismo correo.
- **El consumidor atrapa toda `RuntimeException`.** Un mensaje problemático no debe bloquear la partición y dejar sin procesar los siguientes, que pueden ser un valor crítico.
- **Los eventos se publican después del commit**, con `TransactionSynchronization`, nunca dentro de la transacción: anunciar algo que un rollback posterior deja sin existir es peor que no anunciarlo.

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
├── parser/Hl7ParsedResult.java     # formato normalizado, comun a HL7 y ASTM
├── builder/AckBuilder.java
├── mapper/Hl7ResultMapper.java
└── adapter/EquipmentAdapter.java
```

Endpoint de entrada: `POST /api/hl7/oru`, que consume `text/plain` y responde `text/plain`.

**Responde siempre 200 con un ACK, nunca un error HTTP.** Un equipo que no recibe ACK reintenta y acaba bloqueando su cola de salida, así que devolver un 422 ante una trama mala pararía el laboratorio. El código del fallo va en el segmento `MSA`, que es donde el equipo lo busca:

| Código | Significado | Cuándo |
|---|---|---|
| `AA` | Application Accept | se procesó |
| `AE` | Application Error | no se pudo interpretar, o falló MedCore; reintentar tiene sentido |
| `AR` | Application Reject | la muestra no existe o ya se cerró; reintentar lo mismo no funcionará |

Esa ruta es pública en `SecurityConfig`: los analizadores no saben enviar un JWT. **Se protege por red, no por token:** en producción solo la VLAN del laboratorio debe alcanzar el puerto 8085, y el gateway no publica `/api/hl7/**` al exterior. La exportación (`GET /api/hl7/oru/muestra/{codigo}`) sí exige rol, porque saca datos clínicos del sistema.

La validación estricta de HAPI está desactivada a propósito: los equipos reales incumplen el estándar en detalles menores (campos opcionales vacíos, fechas sin segundos) y con ella activa se perdería un resultado perfectamente legible. `Hl7MessageValidator` comprueba lo que el proceso necesita de verdad.

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

### MC8 — Trampas de Spring Boot 4 que no dan error de compilación

Seis trampas que **no dan error de compilación**: se ignoran en silencio, o fallan solo en ejecución y lejos de su causa. Todas se encontraron levantando el stack real.

**1. Autoconfiguraciones partidas en módulos.** Hay que declarar explícitamente lo que antes venía en el starter:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-flyway</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-kafka</artifactId>
</dependency>
```

Sin la primera, Hibernate falla con `Schema validation: missing table`. Sin la segunda, `A component required a bean named 'kafkaListenerContainerFactory'`.

**2. La conexión a MongoDB se movió a `spring.mongodb.*`.** La clave antigua no da error: se ignora y el driver se conecta a `localhost:27017` **sin credenciales**.

```yaml
spring:
  mongodb:                      # ← la conexión va aquí
    uri: mongodb://${MONGO_USER}:${MONGO_PASSWORD}@${MONGO_HOST:localhost}:27017/lab_results_db?authSource=admin
  data:
    mongodb:
      auto-index-creation: false   # ← esta sí sigue en spring.data: es de Spring Data
```

Redis va al contrario: `spring.data.redis.*` es la clave **actual** y `spring.redis.*` la deprecada.

**3. Jackson 3 rechaza un cuerpo que omita un campo primitivo.** Jackson 2 lo dejaba en `false`; Jackson 3 responde 400. Un formulario que no envía una casilla sin marcar es lo normal, así que en los DTOs de request los indicadores se declaran como envoltorio:

```java
public record SampleRequest(Long idOrden, String tipoMuestra, Boolean urgente) {

    /** Un indicador ausente en el JSON vale lo mismo que false. */
    public boolean esUrgente() {
        return Boolean.TRUE.equals(urgente);
    }
}
```

Jackson 3 también sacó `WRITE_DATES_AS_TIMESTAMPS` de `SerializationFeature`: configurarla rompe el arranque, y no hace falta porque su formato por omisión ya es ISO-8601.

**4. Los consumidores de Kafka.** Dos reglas que valen para todos:

- **No declarar un parámetro `Acknowledgment`.** La configuración usa `ack-mode: record`, y ese parámetro solo se inyecta con `ack-mode: MANUAL`. Declararlo lanza `No Acknowledgment available as an argument`.
- **Registrar el bean `ByteArrayJsonMessageConverter`** en el `KafkaConfig` de cada servicio. Con `JsonDeserializer` el consumidor exige la cabecera `__TypeId__` y falla con `No type information in headers`; el converter deduce el tipo de la firma del `@KafkaListener`, de modo que acepta eventos de cualquier productor, incluido un reenvío manual.

```java
@Bean
public ByteArrayJsonMessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
    return new ByteArrayJsonMessageConverter(objectMapper);
}
```

**5. Tras añadir una clase a `medcore-commons`, reinstalarlo.** `mvn compile -am` construye el módulo desde el fuente, pero `mvn test -pl services/x` sin `-am` usa el JAR del repositorio local. Si está viejo, el fallo aparece lejos de la causa: un `MockitoException` sobre una clase del propio servicio, porque Byte Buddy no puede instrumentar una clase cuya dependencia falta en runtime.

```bash
mvn install -pl shared/medcore-commons -DskipTests
```

Y si un arreglo parece no surtir efecto, comprueba que el JAR en ejecución lo tiene: un `mvn package` lanzado mientras el servicio mantiene el JAR abierto no siempre lo reemplaza ni falla de forma visible.

```bash
# Parar el servicio, borrar el jar y reempaquetar
rm -f services/x-service/target/x-service-1.0.0-SNAPSHOT.jar
mvn -q package -pl services/x-service -DskipTests
```

**6. En Spring Data MongoDB, `Between` es EXCLUSIVO.** El mismo nombre de método derivado se comporta distinto según el módulo: en JPA genera un `BETWEEN` de SQL, que incluye los límites; en Mongo genera `$gt` y `$lt`, que los dejan fuera.

Con rangos de fechas eso descarta el primer y el último día de cada ventana, sin ningún error. Se detectó en `analytics-service`: la detección de brotes contaba 0 casos donde la agregación contaba 6, porque todos eran de hoy, que es justo el límite superior. Afectaba también a todos los recuentos de los informes.

```java
// MAL en Mongo: pierde los extremos del rango
long countByFechaBetween(LocalDate desde, LocalDate hasta);

// BIEN: límites explícitos
@Query(value = "{ fecha: { $gte: ?0, $lte: ?1 } }", count = true)
long contarEntre(LocalDate desde, LocalDate hasta);
```

Las `@Aggregation` escritas a mano no tienen el problema, porque en ellas los operadores se escriben uno a uno.

---

## 5. Entorno de desarrollo

Requisitos: JDK 21, Maven 3.9, Node 20+, Docker Desktop con backend WSL2.

Antes del primer arranque, copia `.env.example` a `.env` y rellena `JWT_SECRET` con una clave de al menos 32 bytes (jjwt rechaza menos para HS256) y `ADMIN_INITIAL_PASSWORD` con la contraseña del administrador que crea `AdminBootstrap`. El `.env` está en `.gitignore` y no se sube.

```bash
# Generar una clave de firma de 64 bytes
openssl rand -base64 64
```

```bash
# Solo infraestructura: Postgres, MongoDB, Redis, Kafka, Mailpit
docker compose -f docker-compose.infra.yml up -d

# Stack completo: la infraestructura más los 11 servicios
docker compose up -d --build

# Un servicio suelto, desde medcore-backend/
./mvnw -pl services/patient-service -am spring-boot:run
```

El orden de arranque lo impone `depends_on` con `condition: service_healthy` (MC2): primero las bases y Kafka, luego Eureka, después el Config Server y por último los microservicios. Un `up -d` tarda unos minutos la primera vez, porque cada servicio espera a que el anterior esté sano.

Puntos de entrada locales:

| Recurso | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Eureka | http://localhost:8761 |
| Kafka UI | http://localhost:8090 |
| Mailpit (correos de desarrollo) | http://localhost:8025 |
| Interfaz HL7 de los equipos | http://localhost:8085/api/hl7/oru |
| Frontend | http://localhost:3000 |

Al host se publican solo cuatro puertos: el 8080 del gateway, el 8085 de la interfaz HL7, y el 8090 y 8025 de las dos utilidades de desarrollo. Los demás servicios viven en la red interna de Compose y solo el gateway los alcanza (MC4).

El 8085 es la excepción deliberada: los analizadores de laboratorio escriben la trama directamente al servicio y no saben enviar un JWT, así que esa ruta se protege por red. En producción, solo la VLAN del laboratorio debe llegar a ese puerto.

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
