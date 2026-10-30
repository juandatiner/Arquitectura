package co.edu.javeriana.sso.ofacbackend;

import co.edu.javeriana.sso.ofacbackend.modelo.EstadoOFAC;
import org.springframework.stereotype.Service;

/** Decide si una cédula está habilitada para hacer compras. */
@Service
public class SrvValidacionOFAC {

    private final RepoOFAC repoOFAC;

    public SrvValidacionOFAC(RepoOFAC repoOFAC) {
        this.repoOFAC = repoOFAC;
    }

    /**
     * Pasos VI.19 (WSOfac → SrvValidacionOFAC) y VI.20 (→ RepoOFAC consultarReporte). Sin reporte →
     * APROBADO; REPORTADO → RECHAZADO; EN_ESTUDIO → ENPROCESO (aún no aprobado ni rechazado).
     */
    public EstadoOFAC validarCedula(String cedula) {
        return repoOFAC.consultarReporte(cedula)
                .map(
                        reporte ->
                                switch (reporte) {
                                    case REPORTADO -> EstadoOFAC.RECHAZADO;
                                    case EN_ESTUDIO -> EstadoOFAC.ENPROCESO;
                                })
                .orElse(EstadoOFAC.APROBADO);
    }
}
