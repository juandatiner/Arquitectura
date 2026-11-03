import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ErrorSso } from './modelo/modelo';
import { ProxyWSSubastaWeb } from './proxy-ws-subasta-web';

describe('ProxyWSSubastaWeb', () => {
  let proxy: ProxyWSSubastaWeb;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    proxy = TestBed.inject(ProxyWSSubastaWeb);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('autentica con un cuerpo {usuario, clave} en ese orden (paso I.3)', async () => {
    const respuesta = proxy.autenticar('ana', 'secreta');
    const peticion = http.expectOne('/api/acceso/login');
    expect(peticion.request.method).toBe('POST');
    expect(Object.keys(peticion.request.body)).toEqual(['usuario', 'clave']);
    peticion.flush({ token: 't' });
    expect(await respuesta).toEqual({ token: 't' });
  });

  it('envía solo los filtros con valor y el token (paso II.3)', () => {
    void proxy.consultarCatalogo({ texto: '', categoria: 'ARTE', pagina: 0 }, 'tk');
    const peticion = http.expectOne((r) => r.url === '/api/catalogo');
    expect(peticion.request.params.keys()).toEqual(['categoria', 'pagina']);
    expect(peticion.request.headers.get('Authorization')).toBe('Bearer tk');
    peticion.flush({ contenido: [], pagina: 0, tamano: 10, total: 0 });
  });

  it('registra la oferta en la ruta de la subasta (paso IV.3)', () => {
    void proxy.registrarOferta('SUB-1', 1500, 'tk');
    const peticion = http.expectOne('/api/subastas/SUB-1/ofertas');
    expect(peticion.request.body).toEqual({ valor: 1500 });
    peticion.flush({
      idOferta: 1,
      idSubasta: 'SUB-1',
      valor: 1500,
      estado: 'VIGENTE',
      mensaje: 'ok',
    });
  });

  it('inicia la compra (paso VI.3)', () => {
    void proxy.iniciarCompra('C-1', 'tk');
    const peticion = http.expectOne('/api/compras/C-1/iniciar');
    expect(peticion.request.method).toBe('POST');
    peticion.flush({ numeroCompra: 'C-1', estado: 'EN_VALIDACION', mensaje: 'ok' });
  });

  it('devuelve el detail del APIGateway como ErrorSso', async () => {
    const respuesta = proxy.publicarNuevaSubasta('SUB-1', 'tk');
    http
      .expectOne('/api/catalogo/publicar')
      .flush({ detail: 'Solo el administrador' }, { status: 403, statusText: 'Forbidden' });
    await expect(respuesta).rejects.toEqual(new ErrorSso(403, 'Solo el administrador'));
  });

  it('informa que el servicio no está disponible si el backend está caído', async () => {
    const respuesta = proxy.consultarMisCompras('tk');
    http.expectOne('/api/compras').flush(null, { status: 503, statusText: 'Unavailable' });
    await expect(respuesta).rejects.toMatchObject({
      estado: 503,
      message: expect.stringContaining('no está disponible'),
    });
  });
});
