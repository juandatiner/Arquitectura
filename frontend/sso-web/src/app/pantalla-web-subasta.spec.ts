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
    consultarMisCompras: vi.fn(),
    publicarNuevaSubasta: vi.fn(),
  };

  beforeEach(() => {
    vi.resetAllMocks();
    sessionStorage.clear();
    localStorage.clear();
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

  it('muestra en rojo la oferta rechazada, no como aviso de éxito', async () => {
    proxy.autenticar.mockResolvedValue({ token: TOKEN });
    proxy.consultarCatalogo.mockResolvedValue({ contenido: [], pagina: 0, tamano: 10, total: 0 });
    proxy.registrarOferta.mockRejectedValue(new ErrorSso(422, 'Oferta bajo el mínimo'));
    const fixture = TestBed.createComponent(PantallaWebSubasta);
    const pantalla = fixture.componentInstance;
    await pantalla.ingresarCredenciales('ana', 'secreta');
    pantalla['agregarAlCarrito']({
      idSubasta: 'SUB-1',
      idBien: 'B-1',
      descripcion: 'Bien',
      categoria: 'ARTE',
      valorMinimo: 100,
      fechaInicio: '2026-11-01T00:00:00Z',
      fechaCierre: '2026-11-30T00:00:00Z',
      estado: 'PUBLICADA',
    });
    pantalla['carrito']()[0].valor = 1;
    pantalla['irA']('carrito');

    await pantalla['ofertarCarrito']();
    fixture.detectChanges();

    const elemento: HTMLElement = fixture.nativeElement;
    expect(elemento.querySelector('.seleccion .error')?.textContent).toContain(
      'Oferta bajo el mínimo',
    );
    expect(elemento.querySelector('.seleccion .aviso')).toBeNull();
  });

  it('recupera la sesión al recargar la página', async () => {
    proxy.autenticar.mockResolvedValue({ token: TOKEN });
    proxy.consultarCatalogo.mockResolvedValue({ contenido: [], pagina: 0, tamano: 10, total: 0 });
    proxy.consultarMisCompras.mockResolvedValue([]);
    await TestBed.createComponent(PantallaWebSubasta).componentInstance.ingresarCredenciales(
      'ana',
      'secreta',
    );

    const recargada = TestBed.createComponent(PantallaWebSubasta);
    await recargada.whenStable();
    recargada.detectChanges();

    expect(recargada.nativeElement.textContent).toContain('Ana');
    expect(recargada.nativeElement.querySelector('input[name=clave]')).toBeNull();
  });

  it('recuerda las ofertas aceptadas tras salir y volver a entrar', async () => {
    proxy.autenticar.mockResolvedValue({ token: TOKEN });
    proxy.consultarCatalogo.mockResolvedValue({ contenido: [], pagina: 0, tamano: 10, total: 0 });
    proxy.consultarMisCompras.mockResolvedValue([]);
    proxy.registrarOferta.mockResolvedValue({ mensaje: 'Oferta registrada' });
    const fixture = TestBed.createComponent(PantallaWebSubasta);
    const pantalla = fixture.componentInstance;
    await pantalla.ingresarCredenciales('ana', 'secreta');
    pantalla['agregarAlCarrito']({
      idSubasta: 'SUB-9',
      idBien: 'B-9',
      descripcion: 'Bien recordado',
      categoria: 'ARTE',
      valorMinimo: 100,
      fechaInicio: '2026-11-01T00:00:00Z',
      fechaCierre: '2999-01-01T00:00:00Z',
      estado: 'PUBLICADA',
    });
    pantalla['carrito']()[0].valor = 150;
    await pantalla['ofertarCarrito']();
    pantalla['salir']();
    await pantalla.ingresarCredenciales('ana', 'secreta');
    pantalla['irA']('ofertas');
    fixture.detectChanges();

    const texto: string = fixture.nativeElement.textContent;
    expect(texto).toContain('Bien recordado');
    expect(texto).toContain('Abiertas');
  });

  it('al rechazar por oferta mayor muestra el monto legible y sugiere superarlo', async () => {
    proxy.autenticar.mockResolvedValue({ token: TOKEN });
    proxy.consultarCatalogo.mockResolvedValue({ contenido: [], pagina: 0, tamano: 10, total: 0 });
    proxy.consultarMisCompras.mockResolvedValue([]);
    proxy.registrarOferta.mockRejectedValue(
      new ErrorSso(422, 'La oferta debe superar la oferta mayor actual de 300000000.00'),
    );
    const fixture = TestBed.createComponent(PantallaWebSubasta);
    const pantalla = fixture.componentInstance;
    await pantalla.ingresarCredenciales('ana', 'secreta');
    const subasta = {
      idSubasta: 'SUB-7',
      idBien: 'B-7',
      descripcion: 'Apartamento',
      categoria: 'INMUEBLES',
      valorMinimo: 280000000,
      fechaInicio: '2026-11-01T00:00:00Z',
      fechaCierre: '2999-01-01T00:00:00Z',
      estado: 'PUBLICADA',
    };
    pantalla['agregarAlCarrito'](subasta);
    pantalla['carrito']()[0].valor = 280000000;
    pantalla['irA']('carrito');

    await pantalla['ofertarCarrito']();
    fixture.detectChanges();

    const elemento: HTMLElement = fixture.nativeElement;
    expect(elemento.querySelector('.seleccion .error')?.textContent).toContain('$ 300.000.000');
    expect(elemento.querySelector('p.superar')?.textContent?.replace(/\D/g, '')).toBe('300000000');
    expect(pantalla['sugerencias'](subasta)[0].valor).toBeGreaterThan(300000000);
  });

  it('busca la categoría en mayúsculas aunque se escriba en minúsculas', async () => {
    proxy.autenticar.mockResolvedValue({ token: TOKEN });
    proxy.consultarCatalogo.mockResolvedValue({ contenido: [], pagina: 0, tamano: 10, total: 0 });
    proxy.consultarMisCompras.mockResolvedValue([]);
    const pantalla = TestBed.createComponent(PantallaWebSubasta).componentInstance;
    await pantalla.ingresarCredenciales('ana', 'secreta');
    pantalla['filtros'].categoria = ' demo ';

    pantalla['buscar']();

    expect(proxy.consultarCatalogo).toHaveBeenLastCalledWith(
      expect.objectContaining({ categoria: 'DEMO' }),
      TOKEN,
    );
  });

  it('no envía ofertas sin valor', async () => {
    proxy.autenticar.mockResolvedValue({ token: TOKEN });
    proxy.consultarCatalogo.mockResolvedValue({ contenido: [], pagina: 0, tamano: 10, total: 0 });
    proxy.consultarMisCompras.mockResolvedValue([]);
    const pantalla = TestBed.createComponent(PantallaWebSubasta).componentInstance;
    await pantalla.ingresarCredenciales('ana', 'secreta');
    pantalla['agregarAlCarrito']({
      idSubasta: 'SUB-8',
      idBien: 'B-8',
      descripcion: 'Bien',
      categoria: 'ARTE',
      valorMinimo: 100,
      fechaInicio: '2026-11-01T00:00:00Z',
      fechaCierre: '2999-01-01T00:00:00Z',
      estado: 'PUBLICADA',
    });

    await pantalla['ofertarCarrito']();

    expect(proxy.registrarOferta).not.toHaveBeenCalled();
  });

  it('al administrador solo le muestra Catálogo y Publicar', async () => {
    const admin = btoa(JSON.stringify({ sub: 'admin', nombre: 'Admin', rol: 'ADMIN' }));
    proxy.autenticar.mockResolvedValue({ token: `x.${admin}.y` });
    proxy.consultarCatalogo.mockResolvedValue({ contenido: [], pagina: 0, tamano: 10, total: 0 });
    const fixture = TestBed.createComponent(PantallaWebSubasta);
    await fixture.componentInstance.ingresarCredenciales('admin', 'secreta');
    fixture.detectChanges();

    const pestanas = [...fixture.nativeElement.querySelectorAll('nav button')].map((b) =>
      (b as HTMLElement).textContent?.trim(),
    );
    expect(pestanas).toEqual(['Catálogo', 'Publicar']);
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
