# Carga de datos al desplegar

El código no trae datos. Los usuarios y las subastas se cargan desde archivos CSV que viven **en la VM**
(nunca en el repositorio), una vez levantados los contenedores.

| Script | Destino | Plantilla |
|---|---|---|
| `cargar_usuarios.py` | AD (OpenLDAP, Máquina 3) + DB ACCESO (Máquina 2) | `plantillas/usuarios.csv` |
| `cargar_subastas.py` | DB CATALOGO + DB SUBASTA (Máquina 2) | `plantillas/subastas.csv` |
| `cargar_ofac.py` | DB OFAC (Máquina 3, sistema externo): personas REPORTADAS o EN_ESTUDIO | `plantillas/ofac.csv` |

- Validan cada fila antes de cargar; si una falla, no cargan nada.
- Son idempotentes: se pueden correr de nuevo con el mismo archivo.
- Las claves de usuario se guardan en AD con hash `{SSHA}`; DB ACCESO no guarda claves.
- Los contenedores se indican con `AD_CONTENEDOR`, `DB_ACCESO_CONTENEDOR`, `DB_CATALOGO_CONTENEDOR` y `DB_SUBASTA_CONTENEDOR`.
- AD (Máquina 3) y DB ACCESO (Máquina 2) están en máquinas distintas: `cargar_usuarios.py` se corre en cada
  una con `--destino ad` o `--destino acceso` (en desarrollo, `--destino todo`).
