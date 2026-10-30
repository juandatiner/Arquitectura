package co.edu.javeriana.sso.mscompra;

import co.edu.javeriana.sso.mscompra.modelo.Compra;
import co.edu.javeriana.sso.mscompra.modelo.CompraIniciada;
import co.edu.javeriana.sso.mscompra.modelo.SolicitudInicioCompra;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Servicio web de compras. Lo invoca Nginx (upstream msCompra, least_conn) en cualquiera de las dos
 * réplicas.
 */
@RestController
@RequestMapping("/compras")
public class WSCompra {

    private final SrvCompraSaga srvCompraSaga;

    public WSCompra(SrvCompraSaga srvCompraSaga) {
        this.srvCompraSaga = srvCompraSaga;
    }

    /**
     * Pasos VI.8 (Nginx → WSCompra iniciarCompra(numeroCompra, cliente)) y VI.9 (→ SrvCompraSaga).
     */
    @PostMapping("/iniciar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompraIniciada iniciarCompra(@Valid @RequestBody SolicitudInicioCompra solicitud) {
        return srvCompraSaga.iniciarCompra(solicitud.numeroCompra(), solicitud.cliente());
    }

    /** VI.1 consultarMisCompras por la flecha Nginx → WSCompra (observación 6). */
    @GetMapping
    public List<Compra> consultarCompras(@RequestParam String cedula) {
        return srvCompraSaga.consultarCompras(cedula);
    }
}
