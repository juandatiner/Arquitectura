package co.edu.javeriana.sso.mscompra;

import co.edu.javeriana.sso.mscompra.modelo.PagoConfirmado;
import co.edu.javeriana.sso.mscompra.modelo.ReferenciaPago;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Consumidor de q.pagoConfirmado (lo publica MSPago en .NET). Compiten las dos réplicas. */
@Component
public class ListenerPago {

    private final SrvCompraSaga srvCompraSaga;
    private final JsonMapper json;

    public ListenerPago(SrvCompraSaga srvCompraSaga, JsonMapper json) {
        this.srvCompraSaga = srvCompraSaga;
        this.json = json;
    }

    /**
     * Pasos VII.30 (q.pagoConfirmado → entregar(PagoConfirmado)) y VII.31 (→ SrvCompraSaga
     * procesarPago).
     */
    @RabbitListener(queues = "q.pagoConfirmado")
    public void entregar(String pagoConfirmado) {
        PagoConfirmado pago = json.readValue(pagoConfirmado, PagoConfirmado.class);
        srvCompraSaga.procesarPago(
                pago.numeroCompra(),
                new ReferenciaPago(pago.referenciaPago(), pago.resultado(), pago.valorPagado()));
    }
}
