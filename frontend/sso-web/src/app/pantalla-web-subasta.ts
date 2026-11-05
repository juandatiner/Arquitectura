import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  Compra,
  CriterioSuscripcion,
  ErrorSso,
  Filtros,
  PaginaCatalogo,
  Sesion,
  SubastaCatalogo,
  Suscripcion,
} from './modelo/modelo';
import { ProxyWSSubastaWeb } from './proxy-ws-subasta-web';

type Vista = 'catalogo' | 'carrito' | 'compras' | 'publicar';

/** Elemento del carrito de subastas: estado solo de la pantalla. */
interface Seleccion {
  subasta: SubastaCatalogo;
  valor: number | null;
  resultado?: string;
  fallida?: boolean;
}

/**
 * Pantalla web de SSO (SSOWeb SERVER :80, Nginx). Solo presentación: toda decisión la toma el
 * backend a través de ProxyWSSubastaWeb.
 */
@Component({
  selector: 'sso-pantalla-web-subasta',
  imports: [FormsModule, CurrencyPipe, DatePipe],
  templateUrl: './pantalla-web-subasta.html',
})
export class PantallaWebSubasta {
  private readonly proxyWSSubastaWeb = inject(ProxyWSSubastaWeb);

  protected readonly sesion = signal<Sesion | null>(null);
  protected readonly vista = signal<Vista>('catalogo');
  protected readonly ocupado = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  protected readonly filtros: Filtros = { texto: '', categoria: '', pagina: 0, tamano: 10 };
  protected readonly catalogo = signal<PaginaCatalogo | null>(null);
  protected readonly carrito = signal<Seleccion[]>([]);
  protected readonly suscripciones = signal<Suscripcion[]>([]);
  protected readonly compras = signal<Compra[]>([]);
  protected readonly compraSeleccionada = signal<Compra | null>(null);
  protected readonly esAdministrador = computed(() => this.sesion()?.rol === 'ADMIN');
  protected readonly paginas = computed(() => {
    const c = this.catalogo();
    return c ? Math.max(1, Math.ceil(c.total / c.tamano)) : 1;
  });

  protected usuario = '';
  protected clave = '';
  protected idSubastaPublicar = '';

  /** Paso I.1 (Browser → PantallaWebSubasta ingresarCredenciales(usuario, clave)) e I.2. */
  async ingresarCredenciales(usuario: string, clave: string): Promise<void> {
    await this.ejecutar(async () => {
      const { token } = await this.proxyWSSubastaWeb.autenticar(usuario, clave);
      this.sesion.set(leerSesion(token));
      this.clave = '';
      await this.consultarCatalogo(this.filtros);
    });
  }

  /** Paso II.1 (consultarCatalogo(filtros)) e II.2. */
  async consultarCatalogo(filtros: Filtros): Promise<void> {
    await this.ejecutar(async () => {
      this.catalogo.set(await this.proxyWSSubastaWeb.consultarCatalogo(filtros, this.token()));
    });
  }

  /** Paso II.1 (suscribirse(criterio)) e II.2. */
  async suscribirse(criterio: CriterioSuscripcion): Promise<void> {
    await this.ejecutar(async () => {
      const suscripcion = await this.proxyWSSubastaWeb.suscribirse(criterio, this.token());
      this.suscripciones.update((s) => [...s, suscripcion]);
      this.aviso.set(`Suscripción activa: ${descripcionCriterio(criterio)}`);
    });
  }

  /** Paso II.1 (camino b de II.7b, cancelarSuscripcion) e II.2. */
  async cancelarSuscripcion(idSuscripcion: string): Promise<void> {
    await this.ejecutar(async () => {
      await this.proxyWSSubastaWeb.cancelarSuscripcion(idSuscripcion, this.token());
      this.suscripciones.update((s) => s.filter((x) => x.idSuscripcion !== idSuscripcion));
      this.aviso.set('Suscripción cancelada');
    });
  }

  /** Paso III.1 (Browser → PantallaWebSubasta solicitarPublicacion(idSubasta), administrador) e III.2. */
  async solicitarPublicacion(idSubasta: string): Promise<void> {
    await this.ejecutar(async () => {
      const subasta = await this.proxyWSSubastaWeb.publicarNuevaSubasta(idSubasta, this.token());
      this.aviso.set(`Subasta ${subasta.idSubasta} publicada (${subasta.estado})`);
      this.idSubastaPublicar = '';
    });
  }

  /** Paso IV.1 (registrarOferta(idSubasta, valor)) e IV.2. */
  async registrarOferta(idSubasta: string, valor: number): Promise<void> {
    try {
      const oferta = await this.proxyWSSubastaWeb.registrarOferta(idSubasta, valor, this.token());
      this.marcarResultado(idSubasta, oferta.mensaje);
    } catch (e) {
      this.marcarResultado(idSubasta, textoError(e), true);
    }
  }

  /** Paso VI.1 (consultarMisCompras()). */
  async consultarMisCompras(): Promise<void> {
    await this.ejecutar(async () => {
      this.compras.set(await this.proxyWSSubastaWeb.consultarMisCompras(this.token()));
    });
  }

  /** Paso VI.1 (seleccionarCompra(numeroCompra)) y VI.2 (→ ProxyWSSubastaWeb iniciarCompra). */
  async seleccionarCompra(numeroCompra: string): Promise<void> {
    await this.ejecutar(async () => {
      const iniciada = await this.proxyWSSubastaWeb.iniciarCompra(numeroCompra, this.token());
      this.aviso.set(`Compra ${iniciada.numeroCompra}: ${iniciada.mensaje}`);
      this.compras.set(await this.proxyWSSubastaWeb.consultarMisCompras(this.token()));
      this.compraSeleccionada.set(
        this.compras().find((c) => c.numeroCompra === numeroCompra) ?? null,
      );
    });
  }

  protected irA(vista: Vista): void {
    this.vista.set(vista);
    this.aviso.set(null);
    this.error.set(null);
    if (vista === 'compras') {
      void this.consultarMisCompras();
    }
  }

  protected cambiarPagina(pagina: number): void {
    this.filtros.pagina = pagina;
    void this.consultarCatalogo(this.filtros);
  }

  protected buscar(): void {
    this.filtros.pagina = 0;
    void this.consultarCatalogo(this.filtros);
  }

  protected enCarrito(idSubasta: string): boolean {
    return this.carrito().some((s) => s.subasta.idSubasta === idSubasta);
  }

  protected agregarAlCarrito(subasta: SubastaCatalogo): void {
    if (!this.enCarrito(subasta.idSubasta)) {
      this.carrito.update((c) => [...c, { subasta, valor: null }]);
    }
  }

  protected quitarDelCarrito(idSubasta: string): void {
    this.carrito.update((c) => c.filter((s) => s.subasta.idSubasta !== idSubasta));
  }

  /** Ofertar por uno o varios bienes: un registrarOferta por cada subasta del carrito con valor. */
  protected async ofertarCarrito(): Promise<void> {
    this.ocupado.set(true);
    try {
      for (const s of this.carrito()) {
        if (s.valor !== null) {
          await this.registrarOferta(s.subasta.idSubasta, s.valor);
        }
      }
    } finally {
      this.ocupado.set(false);
    }
  }

  protected salir(): void {
    this.sesion.set(null);
    this.catalogo.set(null);
    this.carrito.set([]);
    this.suscripciones.set([]);
    this.compras.set([]);
    this.compraSeleccionada.set(null);
    this.vista.set('catalogo');
  }

  protected descripcionCriterio = descripcionCriterio;
  protected textoEstado = textoEstado;

  protected totalOfertas(): number {
    return this.carrito().reduce((total, s) => total + (s.valor ?? 0), 0);
  }

  private token(): string {
    return this.sesion()?.token ?? '';
  }

  private marcarResultado(idSubasta: string, resultado: string, fallida = false): void {
    this.carrito.update((c) =>
      c.map((s) => (s.subasta.idSubasta === idSubasta ? { ...s, resultado, fallida } : s)),
    );
  }

  private async ejecutar(accion: () => Promise<void>): Promise<void> {
    this.ocupado.set(true);
    this.error.set(null);
    try {
      await accion();
    } catch (e) {
      if (e instanceof ErrorSso && e.estado === 401 && this.sesion()) {
        this.salir();
      }
      this.error.set(textoError(e));
    } finally {
      this.ocupado.set(false);
    }
  }
}

function textoError(e: unknown): string {
  return e instanceof ErrorSso ? e.message : 'Ocurrió un error inesperado';
}

const TEXTO_ESTADO: Record<string, string> = {
  PENDIENTE: 'Pendiente',
  PUBLICADA: 'Publicada',
  LIBERADA: 'Liberada',
  CREADA: 'Creada',
  EN_VALIDACION: 'En validación',
  ESPERANDO_PAGO: 'Esperando pago',
  PAGO_CONFIRMADO: 'Pago confirmado',
  TERMINADA: 'Terminada',
  CANCELADA: 'Cancelada',
  EN_REVISION: 'En revisión',
};

function textoEstado(estado: string): string {
  return TEXTO_ESTADO[estado] ?? estado;
}

function descripcionCriterio(c: CriterioSuscripcion): string {
  return c.tipo === 'NUEVAS_SUBASTAS'
    ? `nuevas subastas de ${c.valor}`
    : `cambios de oferta de la subasta ${c.valor}`;
}

/** Lee (sin verificar: lo verifica el APIGateway) los datos del cliente que trae el token. */
function leerSesion(token: string): Sesion {
  const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
  const datos = JSON.parse(new TextDecoder().decode(bytes)) as Record<string, string>;
  return { token, usuario: datos['sub'], nombre: datos['nombre'], rol: datos['rol'] };
}
