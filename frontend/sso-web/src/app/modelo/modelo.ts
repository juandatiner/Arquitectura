/** Tipos de los cuerpos que viajan entre ProxyWSSubastaWeb y el APIGateway (sin lógica de negocio). */

/** Respuesta de autenticar(usuario, clave) — paso I.3. */
export interface RespuestaLogin {
  token: string;
}

/** Datos del cliente que trae el token firmado por MSAcceso (solo para mostrarlos). */
export interface Sesion {
  token: string;
  usuario: string;
  nombre: string;
  rol: string;
}

/** filtros de consultarCatalogo(filtros) — paso II.1. Todos opcionales. */
export interface Filtros {
  texto?: string;
  categoria?: string;
  pagina?: number;
  tamano?: number;
}

export interface SubastaCatalogo {
  idSubasta: string;
  idBien: string;
  descripcion: string;
  categoria: string;
  valorMinimo: number;
  fechaInicio: string;
  fechaCierre: string;
  estado: string;
}

export interface PaginaCatalogo {
  contenido: SubastaCatalogo[];
  pagina: number;
  tamano: number;
  total: number;
}

export type TipoSuscripcion = 'NUEVAS_SUBASTAS' | 'CAMBIOS_OFERTA';

/** criterio de suscribirse(criterio) — paso II.1. */
export interface CriterioSuscripcion {
  tipo: TipoSuscripcion;
  valor: string;
}

export interface Suscripcion {
  idSuscripcion: string;
  tipo: TipoSuscripcion;
  valor: string;
  estado: string;
}

export interface Compra {
  numeroCompra: string;
  idSubasta: string;
  idBien: string;
  descripcion: string;
  valorOfertado: number;
  estado: string;
  motivo: string | null;
  creadaEn: string;
}

/** Respuesta de iniciarCompra(numeroCompra, token) — paso VI.3. */
export interface CompraIniciada {
  numeroCompra: string;
  estado: string;
  mensaje: string;
}

/** Error que devuelve el APIGateway (Problem Details) traducido a un texto para la pantalla. */
export class ErrorSso extends Error {
  constructor(
    readonly estado: number,
    mensaje: string,
  ) {
    super(mensaje);
  }
}

/** Respuesta de registrarOferta(idSubasta, valor, token) — paso IV.3. */
export interface OfertaRegistrada {
  idOferta: number;
  idSubasta: string;
  valor: number;
  estado: string;
  mensaje: string;
}
