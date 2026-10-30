package co.edu.javeriana.sso.mscompra.modelo;

/** Estados de la saga de compra (literales exactos del diagrama). */
public enum EstadoCompra {
    CREADA,
    EN_VALIDACION,
    ESPERANDO_PAGO,
    PAGO_CONFIRMADO,
    TERMINADA,
    CANCELADA,
    EN_REVISION
}
