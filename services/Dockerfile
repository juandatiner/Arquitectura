# Imagen de cualquier componente Java del diagrama (Tomcat embebido).
#   docker buildx build --platform linux/amd64 -f services/Dockerfile \
#     --build-arg MODULO=msacceso --build-arg ARTEFACTO=MSAcceso --build-arg PUERTO=8081 -t sso-msacceso services
# Compila dentro del contenedor: la VM Ubuntu solo necesita Docker.

FROM maven:3.9-eclipse-temurin-21 AS construccion
ARG MODULO
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ${MODULO} -am package -DskipTests -Dspotless.check.skip=true -Djacoco.skip=true

FROM eclipse-temurin:21-jre-alpine
ARG MODULO
ARG ARTEFACTO
# Puerto del nodo en el diagrama (el mismo de server.port del componente)
ARG PUERTO
ENV PUERTO=${PUERTO}
EXPOSE ${PUERTO}
RUN addgroup -S sso && adduser -S sso -G sso && apk add --no-cache wget
WORKDIR /app
COPY --from=construccion /src/${MODULO}/target/${ARTEFACTO}.jar /app/app.jar
USER sso
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:${PUERTO}/actuator/health/readiness | grep -q UP || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
