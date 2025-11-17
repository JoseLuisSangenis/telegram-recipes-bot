FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copiar pom y descargar dependencias primero (para aprovechar caché)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar el código fuente y construir
COPY src ./src
RUN mvn clean package -DskipTests -B

# Etapa 2: Imagen final súper liviana
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copiar el JAR generado
COPY --from=build /app/target/telegram-recipes-bot-*.jar app.jar

# Puerto que usa tu bot
EXPOSE 9090

# Variables de entorno (Render las sobrescribe, pero las dejo por si corrés local)
ENV TELEGRAM_BOT_TOKEN=""
ENV OPENROUTER_API_KEY=""

# Arrancar la app
ENTRYPOINT ["java", "-jar", "app.jar"]
