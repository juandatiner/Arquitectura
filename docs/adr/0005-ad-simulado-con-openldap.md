# 0005 · Active Directory simulado con OpenLDAP

- Estado: Aceptada
- Fecha: 2026-10-29

## Contexto
`IntegraLoginAD` autentica contra Active Directory por LDAP (puerto 389). No hay un AD real disponible.

## Decisión
El estereotipo del nodo AD SERVER :389 en el diagrama es `OpenLDAP`. Contenedor `osixia/openldap` en Máquina 3 con el LDIF `deploy/ad/ldif/01-usuarios.ldif`. `IntegraLoginAD` hace *bind* LDAP simple con el DN del usuario (JNDI del JDK, sin dependencias); el cambio a un AD real es solo de configuración (`AD_URL`, `AD_PATRON_DN`). Se rechaza la clave vacía (evita el bind anónimo) y se valida el usuario antes de armar el DN (evita inyección LDAP).
MSAcceso emite un JWT firmado (RS256, 30 min); `SrvSeguridadToken` en el APIGateway valida la firma con la clave pública (`tools/scripts/generar-claves-token.sh`).

## Consecuencias
- Login real por LDAP sin dependencia de Windows Server.
- Los datos del cliente y su rol (`CLIENTE`, `ADMIN`) salen de DB ACCESO (`RepoLogin.obtenerDatosCliente`, paso I.11).
- El APIGateway valida el token con la clave pública configurada; no llama a MSAcceso (el diagrama no tiene esa flecha).
