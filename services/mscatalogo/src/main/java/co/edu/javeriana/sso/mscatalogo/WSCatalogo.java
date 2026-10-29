package co.edu.javeriana.sso.mscatalogo;

import co.edu.javeriana.sso.mscatalogo.modelo.Filtros;
import co.edu.javeriana.sso.mscatalogo.modelo.PaginaCatalogo;
import co.edu.javeriana.sso.mscatalogo.modelo.SolicitudPublicacion;
import co.edu.javeriana.sso.mscatalogo.modelo.SubastaPublicada;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Servicio web del catálogo. Lo invoca SrvRuteo (APIGateway) por HTTP. */
@RestController
@RequestMapping("/catalogo")
public class WSCatalogo {

    private final SrvCatalogo srvCatalogo;

    public WSCatalogo(SrvCatalogo srvCatalogo) {
        this.srvCatalogo = srvCatalogo;
    }

    /** Pasos II.7a (SrvRuteo → WSCatalogo) y II.8a (WSCatalogo → SrvCatalogo). */
    @GetMapping
    public PaginaCatalogo consultarCatalogo(Filtros filtros) {
        return srvCatalogo.consultarCatalogo(filtros);
    }

    /** Pasos III.7 (SrvRuteo → WSCatalogo) y III.8 (WSCatalogo → SrvCatalogo). */
    @PostMapping("/publicar")
    public SubastaPublicada publicarNuevaSubasta(
            @Valid @RequestBody SolicitudPublicacion solicitud) {
        return srvCatalogo.publicarNuevaSubasta(solicitud.idSubasta(), solicitud.admin());
    }
}
