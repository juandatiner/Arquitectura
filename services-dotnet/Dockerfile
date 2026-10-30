# Imagen de cualquier componente .NET del diagrama (Kestrel .NET 8).
#   docker buildx build --platform linux/amd64 -f services-dotnet/Dockerfile \
#     --build-arg PROYECTO=MSPago --build-arg PUERTO=5086 -t sso-mspago services-dotnet
# Compila dentro del contenedor: la VM Ubuntu solo necesita Docker.

FROM mcr.microsoft.com/dotnet/sdk:8.0 AS construccion
ARG PROYECTO
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.nuget/packages \
    dotnet publish ${PROYECTO}/${PROYECTO}.csproj -c Release -o /app

FROM mcr.microsoft.com/dotnet/aspnet:8.0-alpine
ARG PROYECTO
# Puerto del nodo en el diagrama (el mismo de Urls en appsettings.json)
ARG PUERTO
# ICU: los avisos muestran pesos colombianos (es-CO)
RUN apk add --no-cache icu-libs icu-data-full && addgroup -S sso && adduser -S sso -G sso
ENV DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=false \
    PUERTO=${PUERTO} \
    ENSAMBLADO=${PROYECTO}.dll
WORKDIR /app
COPY --from=construccion /app .
USER sso
EXPOSE ${PUERTO}
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://127.0.0.1:${PUERTO}/health | grep -q UP || exit 1
ENTRYPOINT ["sh", "-c", "exec dotnet $ENSAMBLADO"]
