# syntax=docker/dockerfile:1

# --- 1. Compilar el jar (los tests se ejecutan en la CI, no aquí) ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Descarga las dependencias en una capa propia: solo se repite si cambia el pom.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && cp target/api-*.jar application.jar

# --- 2. Separar el jar en capas (dependencias / código) para aprovechar la caché de Docker ---
FROM eclipse-temurin:25-jre AS extract
WORKDIR /extract
COPY --from=build /build/application.jar .
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# --- 3. Imagen final: solo JRE, sin herramientas de compilación y sin root ---
FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=extract /extract/extracted/dependencies/ ./
COPY --from=extract /extract/extracted/spring-boot-loader/ ./
COPY --from=extract /extract/extracted/snapshot-dependencies/ ./
COPY --from=extract /extract/extracted/application/ ./
USER app

# Cloud Run fija PORT; en local se usa 8080 por defecto.
ENV PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

# MaxRAMPercentage: la JVM ajusta el heap a la memoria del contenedor (512 MiB en Cloud Run).
# TieredStopAtLevel=1: arranca antes, importante porque la API escala a cero.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:TieredStopAtLevel=1", "-jar", "application.jar"]
