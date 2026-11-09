import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
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

type Vista = 'catalogo' | 'carrito' | 'ofertas' | 'compras' | 'publicar';

/** Elemento del carrito de subastas: estado solo de la pantalla. */
interface Seleccion {
  subasta: SubastaCatalogo;
  valor: number | null;
  resultado?: string;
  fallida?: boolean;
}

/** Oferta aceptada por el backend, recordada en este navegador para mostrarla al volver. */
interface OfertaHecha {
  idSubasta: string;
  descripcion: string;
  categoria: string;
  valorMinimo: number;
  fechaCierre: string;
  valor: number;
  enviadaEn: string;
}

/** Cómo va una oferta: se deduce de la fecha de cierre y de las compras que devuelve el backend. */
type Situacion = 'ABIERTA' | 'POR_DEFINIR' | 'GANADA' | 'NO_GANADA';

const SEGUNDO = 1000;
const MINUTO = 60 * SEGUNDO;
/** Tiempo que la pantalla espera tras el cierre antes de dar una oferta como no ganada. */
const ESPERA_RESULTADO = 3 * MINUTO;
/** Una subasta se resalta cuando le queda menos de este tiempo. */
const CIERRE_PRONTO = 15 * MINUTO;
const REFRESCO = 15 * SEGUNDO;
const CLAVE_SESION = 'sso.sesion';

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
  protected readonly ahora = signal(Date.now());

  protected readonly filtros: Filtros = { texto: '', categoria: '', pagina: 0, tamano: 10 };
  protected readonly catalogo = signal<PaginaCatalogo | null>(null);
  protected readonly carrito = signal<Seleccion[]>([]);
  protected readonly suscripciones = signal<Suscripcion[]>([]);
  protected readonly compras = signal<Compra[]>([]);
  protected readonly compraSeleccionada = signal<Compra | null>(null);
  protected readonly ofertasHechas = signal<OfertaHecha[]>([]);
  protected readonly esAdministrador = computed(() => this.sesion()?.rol === 'ADMIN');
  protected readonly paginas = computed(() => {
    const c = this.catalogo();
    return c ? Math.max(1, Math.ceil(c.total / c.tamano)) : 1;
  });
  protected readonly totalOfertas = computed(() =>
    this.carrito().reduce((total, s) => total + (s.valor ?? 0), 0),
  );
  protected readonly faltanValores = computed(() => this.carrito().some((s) => s.valor === null));
  protected readonly grupos = computed(() => {
    const grupos: Record<Situacion, OfertaHecha[]> = {
      ABIERTA: [],
      POR_DEFINIR: [],
      GANADA: [],
      NO_GANADA: [],
    };
    for (const o of this.ofertasHechas()) {
      grupos[this.situacion(o)].push(o);
    }
    return grupos;
  });

  protected readonly gruposVista: { clave: Situacion; titulo: string }[] = [
    { clave: 'ABIERTA', titulo: 'Abiertas' },
    { clave: 'POR_DEFINIR', titulo: 'Cerradas, esperando resultado' },
    { clave: 'GANADA', titulo: 'Ganadas' },
    { clave: 'NO_GANADA', titulo: 'No ganadas' },
  ];

  protected usuario = '';
  protected clave = '';
  protected idSubastaPublicar = '';

  constructor() {
    const reloj = setInterval(() => this.ahora.set(Date.now()), SEGUNDO);
    const refresco = setInterval(() => void this.refrescar(), REFRESCO);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(reloj);
      clearInterval(refresco);
    });
    this.restaurarSesion();
  }

  /** Paso I.1 (Browser → PantallaWebSubasta ingresarCredenciales(usuario, clave)) e I.2. */
  async ingresarCredenciales(usuario: string, clave: string): Promise<void> {
    await this.ejecutar(async () => {
      const { token } = await this.proxyWSSubastaWeb.autenticar(usuario, clave);
      this.iniciarSesion(leerSesion(token));
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
      this.guardar('suscripciones', this.suscripciones());
      this.aviso.set(`Suscripción activa: ${descripcionCriterio(criterio)}`);
    });
  }

  /** Paso II.1 (camino b de II.7b, cancelarSuscripcion) e II.2. */
  async cancelarSuscripcion(idSuscripcion: string): Promise<void> {
    await this.ejecutar(async () => {
      await this.proxyWSSubastaWeb.cancelarSuscripcion(idSuscripcion, this.token());
      this.suscripciones.update((s) => s.filter((x) => x.idSuscripcion !== idSuscripcion));
      this.guardar('suscripciones', this.suscripciones());
      this.aviso.set('Suscripción cancelada');
    });
  }

  /** Paso III.1 (Browser → PantallaWebSubasta solicitarPublicacion(idSubasta), administrador) e III.2. */
  async solicitarPublicacion(idSubasta: string): Promise<void> {
    await this.ejecutar(async () => {
      const subasta = await this.proxyWSSubastaWeb.publicarNuevaSubasta(
        idSubasta.trim(),
        this.token(),
      );
      this.aviso.set(`Subasta ${subasta.idSubasta} publicada. Ya aparece en el catálogo.`);
      this.idSubastaPublicar = '';
      this.catalogo.set(await this.proxyWSSubastaWeb.consultarCatalogo(this.filtros, this.token()));
    });
  }

  /** Paso IV.1 (registrarOferta(idSubasta, valor)) e IV.2. */
  async registrarOferta(idSubasta: string, valor: number): Promise<void> {
    try {
      const oferta = await this.proxyWSSubastaWeb.registrarOferta(idSubasta, valor, this.token());
      this.recordarOferta(idSubasta, valor);
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
    if (vista === 'compras' || vista === 'ofertas') {
      void this.consultarMisCompras();
    }
    if (vista === 'catalogo') {
      void this.consultarCatalogo(this.filtros);
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

  protected miOferta(idSubasta: string): OfertaHecha | undefined {
    return this.ofertasHechas().find((o) => o.idSubasta === idSubasta);
  }

  protected agregarAlCarrito(subasta: SubastaCatalogo): void {
    if (!this.enCarrito(subasta.idSubasta)) {
      this.carrito.update((c) => [...c, { subasta, valor: null }]);
    }
  }

  protected quitarDelCarrito(idSubasta: string): void {
    this.carrito.update((c) => c.filter((s) => s.subasta.idSubasta !== idSubasta));
  }

  /** Escribe el valor con separador de miles mientras el cliente teclea. */
  protected escribirValor(seleccion: Seleccion, evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const digitos = campo.value.replace(/\D/g, '').slice(0, 15);
    this.fijarValor(seleccion, digitos ? Number(digitos) : null);
    campo.value = formatoMiles(this.valorDe(seleccion));
  }

  protected fijarValor(seleccion: Seleccion, valor: number | null): void {
    this.carrito.update((c) =>
      c.map((s) =>
        s === seleccion ? { ...s, valor, resultado: undefined, fallida: undefined } : s,
      ),
    );
  }

  protected valorDe(seleccion: Seleccion): number | null {
    return this.carrito().find((s) => s.subasta === seleccion.subasta)?.valor ?? null;
  }

  protected sugerencias(subasta: SubastaCatalogo): { texto: string; valor: number }[] {
    const base = this.miOferta(subasta.idSubasta)?.valor ?? subasta.valorMinimo;
    const redondear = (v: number) => Math.ceil(v / 10000) * 10000;
    const primera = this.miOferta(subasta.idSubasta)
      ? { texto: 'Mi oferta +5 %', valor: redondear(base * 1.05) }
      : { texto: 'Mínimo', valor: subasta.valorMinimo };
    return [
      primera,
      { texto: '+10 %', valor: redondear(base * 1.1) },
      { texto: '+25 %', valor: redondear(base * 1.25) },
    ];
  }

  protected formatoMiles = formatoMiles;

  /** Ofertar por uno o varios bienes: un registrarOferta por cada subasta del carrito con valor. */
  protected async ofertarCarrito(): Promise<void> {
    if (this.faltanValores()) {
      return;
    }
    this.ocupado.set(true);
    this.error.set(null);
    try {
      for (const s of this.carrito()) {
        await this.registrarOferta(s.subasta.idSubasta, s.valor as number);
      }
      const aceptadas = this.carrito().filter((s) => !s.fallida);
      this.carrito.update((c) => c.filter((s) => s.fallida));
      if (aceptadas.length) {
        this.aviso.set(
          `${aceptadas.length === 1 ? 'Oferta registrada' : `${aceptadas.length} ofertas registradas`}. ` +
            'Le llegará un mensaje al celular cuando sea posible continuar la compra.',
        );
        if (!this.carrito().length) {
          this.vista.set('ofertas');
        }
      }
    } finally {
      this.ocupado.set(false);
    }
  }

  protected restante(fecha: string): number {
    return new Date(fecha).getTime() - this.ahora();
  }

  protected cierraPronto(fecha: string): boolean {
    const r = this.restante(fecha);
    return r > 0 && r <= CIERRE_PRONTO;
  }

  protected textoRestante(fecha: string): string {
    const r = this.restante(fecha);
    if (r <= 0) {
      return 'Cerrada';
    }
    const s = Math.floor(r / SEGUNDO);
    const d = Math.floor(s / 86400);
    const h = Math.floor((s % 86400) / 3600);
    const m = Math.floor((s % 3600) / 60);
    if (d > 0) {
      return `${d} d ${h} h`;
    }
    if (h > 0) {
      return `${h} h ${m} min`;
    }
    return `${m}:${String(s % 60).padStart(2, '0')}`;
  }

  protected situacion(o: OfertaHecha): Situacion {
    if (this.compraDe(o.idSubasta)) {
      return 'GANADA';
    }
    const r = this.restante(o.fechaCierre);
    if (r > 0) {
      return 'ABIERTA';
    }
    return r > -ESPERA_RESULTADO ? 'POR_DEFINIR' : 'NO_GANADA';
  }

  protected compraDe(idSubasta: string): Compra | undefined {
    return this.compras().find((c) => c.idSubasta === idSubasta);
  }

  protected olvidarOferta(idSubasta: string): void {
    this.ofertasHechas.update((o) => o.filter((x) => x.idSubasta !== idSubasta));
    this.guardar('ofertas', this.ofertasHechas());
  }

  protected salir(): void {
    try {
      sessionStorage.removeItem(CLAVE_SESION);
    } catch {
      /* sin almacenamiento: no hay nada que borrar */
    }
    this.sesion.set(null);
    this.catalogo.set(null);
    this.carrito.set([]);
    this.suscripciones.set([]);
    this.compras.set([]);
    this.compraSeleccionada.set(null);
    this.ofertasHechas.set([]);
    this.aviso.set(null);
    this.vista.set('catalogo');
  }

  protected descripcionCriterio = descripcionCriterio;
  protected textoEstado = textoEstado;

  private token(): string {
    return this.sesion()?.token ?? '';
  }

  private iniciarSesion(sesion: Sesion): void {
    this.sesion.set(sesion);
    try {
      sessionStorage.setItem(CLAVE_SESION, sesion.token);
    } catch {
      /* sin almacenamiento: la sesión dura hasta recargar */
    }
    this.ofertasHechas.set(this.leer<OfertaHecha[]>('ofertas') ?? []);
    this.suscripciones.set(this.leer<Suscripcion[]>('suscripciones') ?? []);
    if (sesion.rol !== 'ADMIN') {
      void this.consultarMisCompras();
    }
  }

  /** Al recargar la página se recupera la sesión de esta pestaña mientras el token siga vigente. */
  private restaurarSesion(): void {
    let token: string | null;
    try {
      token = sessionStorage.getItem(CLAVE_SESION);
    } catch {
      return;
    }
    if (!token) {
      return;
    }
    try {
      const sesion = leerSesion(token);
      if (sesion.expira && sesion.expira <= Date.now()) {
        sessionStorage.removeItem(CLAVE_SESION);
        return;
      }
      this.iniciarSesion(sesion);
      void this.consultarCatalogo(this.filtros);
    } catch {
      sessionStorage.removeItem(CLAVE_SESION);
    }
  }

  /** Actualiza sola la vista abierta: catálogo, ofertas o compras. Sin mensajes si falla. */
  private async refrescar(): Promise<void> {
    if (!this.sesion() || this.ocupado()) {
      return;
    }
    try {
      if (this.vista() === 'catalogo') {
        this.catalogo.set(
          await this.proxyWSSubastaWeb.consultarCatalogo(this.filtros, this.token()),
        );
      } else if (this.vista() === 'ofertas' || this.vista() === 'compras') {
        this.compras.set(await this.proxyWSSubastaWeb.consultarMisCompras(this.token()));
      }
    } catch (e) {
      if (e instanceof ErrorSso && e.estado === 401) {
        this.salir();
        this.error.set('Su sesión venció. Ingrese de nuevo.');
      }
    }
  }

  private recordarOferta(idSubasta: string, valor: number): void {
    const s = this.carrito().find((x) => x.subasta.idSubasta === idSubasta)?.subasta;
    if (!s) {
      return;
    }
    const hecha: OfertaHecha = {
      idSubasta,
      descripcion: s.descripcion,
      categoria: s.categoria,
      valorMinimo: s.valorMinimo,
      fechaCierre: s.fechaCierre,
      valor,
      enviadaEn: new Date().toISOString(),
    };
    this.ofertasHechas.update((o) => [hecha, ...o.filter((x) => x.idSubasta !== idSubasta)]);
    this.guardar('ofertas', this.ofertasHechas());
  }

  private claveAlmacen(nombre: string): string {
    return `sso.${nombre}.${this.sesion()?.usuario ?? ''}`;
  }

  private guardar(nombre: string, datos: unknown): void {
    try {
      localStorage.setItem(this.claveAlmacen(nombre), JSON.stringify(datos));
    } catch {
      /* sin almacenamiento: se pierde al recargar */
    }
  }

  private leer<T>(nombre: string): T | null {
    try {
      const texto = localStorage.getItem(this.claveAlmacen(nombre));
      return texto ? (JSON.parse(texto) as T) : null;
    } catch {
      return null;
    }
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

function formatoMiles(valor: number | null): string {
  return valor === null ? '' : valor.toLocaleString('es-CO', { maximumFractionDigits: 0 });
}

const TEXTO_ESTADO: Record<string, string> = {
  PENDIENTE: 'Pendiente',
  PUBLICADA: 'Publicada',
  LIBERADA: 'Liberada',
  CREADA: 'Lista para comprar',
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
  const datos = JSON.parse(new TextDecoder().decode(bytes)) as Record<string, string | number>;
  return {
    token,
    usuario: String(datos['sub']),
    nombre: String(datos['nombre']),
    rol: String(datos['rol']),
    expira: typeof datos['exp'] === 'number' ? datos['exp'] * SEGUNDO : undefined,
  };
}
