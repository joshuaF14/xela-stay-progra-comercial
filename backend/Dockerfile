# Imagen del backend de Xela Stay. Se construye desde la carpeta backend/:
#   docker build -t xelastay-backend backend
# La configuración llega por variables de entorno (las mismas del .env); la imagen no lleva secretos.

# 1) Compilación con el Maven Wrapper del proyecto (sin pruebas: corren en local y en CI).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

# 2) Ejecución solo con el JRE y un usuario sin privilegios.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 xelastay
COPY --from=build /app/target/*.jar app.jar
USER xelastay
# Memoria acotada para planes pequeños (p. ej. el gratuito de Render).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
