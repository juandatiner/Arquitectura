package co.edu.javeriana.sso.mscompra.modelo;

/** Respuesta de iniciarCompra: el cliente no espera la validación, se le avisa al celular. */
public record CompraIniciada(String numeroCompra, EstadoCompra estado, String mensaje) {

    public static final String MENSAJE =
            "Estamos validando su compra. Le llegará un mensaje al celular con el valor a pagar y el enlace de pago.";
}
