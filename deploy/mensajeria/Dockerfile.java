# Imagen común para los microservicios Java de mensajería (el jar se compila fuera y se copia).
FROM eclipse-temurin:21-jre-alpine
ARG JAR
WORKDIR /app
COPY ${JAR} app.jar
RUN addgroup -S app && adduser -S app -G app
USER app
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
