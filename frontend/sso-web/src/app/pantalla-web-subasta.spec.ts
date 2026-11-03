import { TestBed } from '@angular/core/testing';
import { ErrorSso } from './modelo/modelo';
import { PantallaWebSubasta } from './pantalla-web-subasta';
import { ProxyWSSubastaWeb } from './proxy-ws-subasta-web';

/** Token sin firma real: la pantalla solo lee nombre y rol para mostrarlos. */
const carga = btoa(JSON.stringify({ sub: 'ana', nombre: 'Ana', rol: 'CLIENTE' }));
const TOKEN = `x.${carga}.y`;

describe('PantallaWebSubasta', () => {
  const proxy = {
    autenticar: vi.fn(),
    consultarCatalogo: vi.fn(),
    registrarOferta: vi.fn(),
  };

  beforeEach(() => {
    vi.resetAllMocks();
    TestBed.configureTestingModule({
      imports: [PantallaWebSubasta],
      providers: [{ provide: ProxyWSSubastaWeb, useValue: proxy }],
    });
  });

  it('ingresa, guarda la sesión y consulta el catálogo con el token', async () => {
    proxy.autenticar.mockResolvedValue({ token: TOKEN });
    proxy.consultarCatalogo.mockResolvedValue({ contenido: [], pagina: 0, tamano: 10, total: 0 });
    const fixture = TestBed.createComponent(PantallaWebSubasta);

    await fixture.componentInstance.ingresarCredenciales('ana', 'secreta');
    fixture.detectChanges();

    expect(proxy.autenticar).toHaveBeenCalledWith('ana', 'secreta');
    expect(proxy.consultarCatalogo).toHaveBeenCalledWith(expect.anything(), TOKEN);
    expect(fixture.nativeElement.textContent).toContain('Ana');
  });

  it('muestra el error del backend sin decidir nada', async () => {
    proxy.autenticar.mockRejectedValue(new ErrorSso(401, 'Usuario o clave incorrectos'));
    const fixture = TestBed.createComponent(PantallaWebSubasta);

    await fixture.componentInstance.ingresarCredenciales('ana', 'mala');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain(
      'Usuario o clave incorrectos',
    );
  });
});
