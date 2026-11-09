# ============================================================
#  Balance General (Spring Boot) - imagen para Render
#  Etapa 1 compila con Maven; etapa 2 solo trae Java y el .jar
# ============================================================

# ---------- 1. Compilar ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Primero solo el pom: así las dependencias se quedan en caché y no se bajan en cada deploy
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Sin pruebas: FinanzasApplicationTests necesita una base de datos que aquí no existe
RUN mvn -B -q -DskipTests package

# ---------- 2. Correr ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Memoria ajustada a instancias chicas (512 MB) y hora de México para "Elaborado el ..."
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Duser.timezone=America/Mexico_City"

# Render avisa en qué puerto escuchar con la variable PORT; en tu compu sigue siendo 8081
EXPOSE 8081
CMD ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8081} -jar app.jar"]