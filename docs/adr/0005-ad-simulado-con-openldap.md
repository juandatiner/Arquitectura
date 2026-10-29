# 0005 · Active Directory simulado con OpenLDAP

- Estado: Aceptada
- Fecha: 2026-10-29

## Contexto
`IntegraLoginAD` autentica contra Active Directory por LDAP (puerto 389). No hay un AD real disponible.

## Decisión
Contenedor OpenLDAP en Máquina 3 con un LDIF semilla (usuarios clientes y administradores, atributo con la cédula). `IntegraLoginAD` usa Spring LDAP con *bind* del usuario; el cambio a un AD real es solo de configuración (URL, base DN, filtro).
MSAcceso emite un JWT firmado (RS256); el APIGateway valida la firma con la clave pública (`SrvSeguridadToken`).

## Consecuencias
- Login real por LDAP sin dependencia de Windows Server.
- Los roles (`CLIENTE`, `ADMIN`) salen de grupos LDAP.
