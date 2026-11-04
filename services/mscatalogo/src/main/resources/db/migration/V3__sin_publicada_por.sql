-- III.9 publicarSiPendienteYEnFecha(idSubasta) no recibe el usuario: viaja en el evento NuevaSubasta.
ALTER TABLE subasta DROP COLUMN publicada_por;
