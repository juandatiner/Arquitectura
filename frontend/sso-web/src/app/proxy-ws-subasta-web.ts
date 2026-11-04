import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, firstValueFrom, throwError } from 'rxjs';
import {
  Compra,
  CompraIniciada,
  CriterioSuscripcion,
  ErrorSso,
  Filtros,
  OfertaRegistrada,
  PaginaCatalogo,
  RespuestaLogin,
  SubastaCatalogo,
  Suscripcion,
} from './modelo/modelo';

/**
 * Proxy HTTP de SSOWeb hacia Nginx (BALANCEADOR DE CARGAS :80). No toma decisiones: solo llama y
 * devuelve. Las rutas son relativas: el navegador las envía al mismo Nginx que sirve la página.
 */
@Injectable({ providedIn: 'root' })
export class ProxyWSSubastaWeb {
  private readonly http = inject(HttpClient);

  /** Pasos I.2 (PantallaWebSubasta → ProxyWSSubastaWeb) e I.3 (→ Nginx POST /api/acceso/login). */
  autenticar(usuario: string, clave: string): Promise<RespuestaLogin> {
    return this.enviar(this.http.post<RespuestaLogin>('/api/acceso/login', { usuario, clave }));
  }

  /** Pasos II.2 e II.3 (→ Nginx GET /api/catalogo). */
  consultarCatalogo(filtros: Filtros, token: string): Promise<PaginaCatalogo> {
    let params = new HttpParams();
    for (const [campo, valor] of Object.entries(filtros)) {
      if (valor !== undefined && valor !== '') {
        params = params.set(campo, String(valor));
      }
    }
    return this.enviar(
      this.http.get<PaginaCatalogo>('/api/catalogo', { params, headers: this.autorizacion(token) }),
    );
  }

  /** Pasos II.2 e II.3 (→ Nginx POST /api/suscripciones). */
  suscribirse(criterio: CriterioSuscripcion, token: string): Promise<Suscripcion> {
    return this.enviar(
      this.http.post<Suscripcion>('/api/suscripciones', criterio, {
        headers: this.autorizacion(token),
      }),
    );
  }

  /** Pasos II.2 e II.3, camino b de II.7b (→ Nginx DELETE /api/suscripciones/{id}). */
  cancelarSuscripcion(idSuscripcion: string, token: string): Promise<Suscripcion> {
    return this.enviar(
      this.http.delete<Suscripcion>(`/api/suscripciones/${encodeURIComponent(idSuscripcion)}`, {
        headers: this.autorizacion(token),
      }),
    );
  }

  /** Pasos III.2 (publicarNuevaSubasta(idSubasta, token)) e III.3 (→ Nginx POST /api/catalogo/publicar). */
  publicarNuevaSubasta(idSubasta: string, token: string): Promise<SubastaCatalogo> {
    return this.enviar(
      this.http.post<SubastaCatalogo>(
        '/api/catalogo/publicar',
        { idSubasta },
        { headers: this.autorizacion(token) },
      ),
    );
  }

  /** Pasos IV.2 (registrarOferta(idSubasta, valor, token)) e IV.3 (→ Nginx POST /api/subastas/{id}/ofertas). */
  registrarOferta(idSubasta: string, valor: number, token: string): Promise<OfertaRegistrada> {
    return this.enviar(
      this.http.post<OfertaRegistrada>(
        `/api/subastas/${encodeURIComponent(idSubasta)}/ofertas`,
        { valor },
        { headers: this.autorizacion(token) },
      ),
    );
  }

  /** Paso VI.1 consultarMisCompras sobre el camino de VI.3 (→ Nginx GET /api/compras, observación 6). */
  consultarMisCompras(token: string): Promise<Compra[]> {
    return this.enviar(
      this.http.get<Compra[]>('/api/compras', { headers: this.autorizacion(token) }),
    );
  }

  /** Pasos VI.2 (iniciarCompra(numeroCompra, token)) y VI.3 (→ Nginx POST /api/compras/{numero}/iniciar). */
  iniciarCompra(numeroCompra: string, token: string): Promise<CompraIniciada> {
    return this.enviar(
      this.http.post<CompraIniciada>(
        `/api/compras/${encodeURIComponent(numeroCompra)}/iniciar`,
        null,
        { headers: this.autorizacion(token) },
      ),
    );
  }

  private autorizacion(token: string): HttpHeaders {
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }

  /** Traduce la respuesta de error (Problem Details del APIGateway) a un ErrorSso con su texto. */
  private enviar<T>(peticion: Observable<T>): Promise<T> {
    return firstValueFrom(
      peticion.pipe(
        catchError((e: HttpErrorResponse) => {
          const detalle = (e.error as { detail?: unknown } | null)?.detail;
          const mensaje =
            typeof detalle === 'string'
              ? detalle
              : e.status === 0 || e.status >= 502
                ? 'El servicio no está disponible en este momento, intente de nuevo'
                : 'No fue posible completar la solicitud';
          return throwError(() => new ErrorSso(e.status, mensaje));
        }),
      ),
    );
  }
}
