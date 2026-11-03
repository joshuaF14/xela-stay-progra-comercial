# Imagen del backend de Xela Stay (Render, plan Free: 512 MB de RAM y 0.1 CPU).
# Construir desde backend/:  docker build -t xela-stay-api .

# --- Etapa 1: compilación -----------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Primero solo lo que define las dependencias: esta capa se reutiliza mientras pom.xml no cambie.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Después el código. Las pruebas ya corren en el CI (perfil test).
COPY src src
RUN ./mvnw -B -DskipTests package && cp target/*.jar app.jar

# --- Etapa 2: ejecución -------------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Usuario sin privilegios.
RUN groupadd --system xela && useradd --system --gid xela --no-create-home xela
COPY --from=build --chown=xela:xela /workspace/app.jar app.jar
USER xela

# JVM ajustada a 512 MB (ver docs/deploy/DESPLIEGUE.md).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"

# Documentativo: el puerto real lo asigna Render en la variable PORT (server.port=${PORT:...}).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
