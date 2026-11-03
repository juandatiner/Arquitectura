# Comandos del proyecto SSO. `make` o `make help` lista los objetivos.
# Sin valores quemados: registro, etiqueta e IP llegan por variable (`make images IMAGE_REGISTRY=... IMAGE_TAG=...`).

SHELL := /bin/bash
.DEFAULT_GOAL := help

DIST ?= dist
COMPOSE_DEV := docker compose -f deploy/dev/docker-compose.yml
SECRETOS_DEV := deploy/secretos/token_privada.pem deploy/secretos/token_publica.pem \
	deploy/secretos/balanceador.crt deploy/secretos/balanceador.key deploy/secretos/mom/usuarios.json

# Sin el SDK de .NET instalado se usa el contenedor oficial (la versión la fija services-dotnet/global.json).
DOTNET ?= $(shell command -v dotnet >/dev/null 2>&1 && echo dotnet || echo "docker run --rm -v $(CURDIR):/src -w /src/services-dotnet mcr.microsoft.com/dotnet/sdk:8.0 dotnet")

.PHONY: help verificar build build-java build-dotnet build-web test test-java test-dotnet test-web \
	up down logs images images-local export smoke zip

help: ## Lista los objetivos
	@grep -hE '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-14s %s\n", $$1, $$2}'

verificar: ## Comprueba que el código sea idéntico al diagrama
	python3 -I tools/vpp/verificar_codigo.py

build: build-java build-dotnet build-web ## Compila Java, .NET y Angular (sin pruebas)

build-java:
	cd services && ./mvnw -B -DskipTests package

build-dotnet:
	cd services-dotnet && $(DOTNET) build --configuration Release

build-web:
	cd frontend/sso-web && npm ci && npm run build

test: test-java test-dotnet test-web ## Ejecuta todas las pruebas (Java usa Testcontainers: requiere Docker)

test-java:
	cd services && ./mvnw -B verify

test-dotnet:
	cd services-dotnet && $(DOTNET) test --configuration Release

test-web:
	cd frontend/sso-web && npm ci && npm run lint && npm test -- --watch=false

up: ## Levanta el entorno de desarrollo (deploy/dev) en esta máquina
	@test -f deploy/dev/.env || { echo "Falta deploy/dev/.env: cp deploy/dev/.env.example deploy/dev/.env y llenarlo" >&2; exit 1; }
	@for f in $(SECRETOS_DEV); do test -f "$$f" || { echo "Falta $$f: ver la cabecera de deploy/dev/docker-compose.yml" >&2; exit 1; }; done
	$(COMPOSE_DEV) up -d --build

down: ## Detiene el entorno de desarrollo (conserva los volúmenes)
	$(COMPOSE_DEV) down

logs: ## Sigue los logs del entorno de desarrollo: make logs s=<servicio>
	$(COMPOSE_DEV) logs -f $(s)

images: ## Construye linux/amd64 y PUBLICA: make images IMAGE_REGISTRY=... IMAGE_TAG=... [s=<componente>]
	tools/scripts/imagenes.sh push $(s)

images-local: ## Construye linux/amd64 sin publicar: make images-local IMAGE_REGISTRY=... IMAGE_TAG=... [s=<componente>]
	tools/scripts/imagenes.sh load $(s)

export: ## Genera dist/images-maquina-N.tar para copiar por scp: make export IMAGE_REGISTRY=... IMAGE_TAG=...
	tools/scripts/exportar-imagenes.sh

smoke: ## Prueba de humo de solo lectura contra Máquina 1: make smoke MAQUINA1_IP=...
	tools/scripts/smoke-test.sh

zip: ## Genera $(DIST)/SSO-codigo.zip desde el último commit y lo verifica
	@mkdir -p $(DIST)
	@test -z "$$(git status --porcelain --untracked-files=no)" || echo "AVISO: hay cambios sin commit; el zip solo lleva el último commit" >&2
	git archive --format=zip --prefix=SSO-codigo/ -o $(DIST)/SSO-codigo.zip HEAD -- . ':(exclude)frontend/sso-mobile'
	python3 -I tools/scripts/verificar-zip.py $(DIST)/SSO-codigo.zip
