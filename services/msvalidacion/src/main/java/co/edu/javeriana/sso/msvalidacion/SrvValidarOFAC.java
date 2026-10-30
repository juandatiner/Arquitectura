package co.edu.javeriana.sso.msvalidacion;

import co.edu.javeriana.sso.msvalidacion.modelo.EstadoOFAC;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Valida al comprador ante la OFAC sin repetir consultas ya resueltas. */
@Service
public class SrvValidarOFAC {

    private final RepoValidar repoValidar;
    private final IntegraOFAC integraOFAC;

    public SrvValidarOFAC(RepoValidar repoValidar, IntegraOFAC integraOFAC) {
        this.repoValidar = repoValidar;
        this.integraOFAC = integraOFAC;
    }

    /**
     * Pasos VI.15 (WSValidacion → SrvValidarOFAC), VI.16 (→ RepoValidar buscarResultado), VI.17 (→
     * IntegraOFAC consultar) y VI.21 (→ RepoValidar guardarResultado). Un resultado definitivo
     * (APROBADO o RECHAZADO) no se vuelve a consultar; ENPROCESO sí.
     */
    public EstadoOFAC validar(String numeroCompra, String cedula) {
        Optional<EstadoOFAC> anterior = repoValidar.buscarResultado(numeroCompra);
        if (anterior.isPresent() && anterior.get().definitivo()) {
            return anterior.get();
        }
        EstadoOFAC estado = integraOFAC.consultar(cedula);
        repoValidar.guardarResultado(numeroCompra, estado);
        return estado;
    }
}
