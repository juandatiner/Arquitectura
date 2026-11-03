import { provideHttpClient, withFetch } from '@angular/common/http';
import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeEsCo from '@angular/common/locales/es-CO';
import { bootstrapApplication } from '@angular/platform-browser';
import { PantallaWebSubasta } from './app/pantalla-web-subasta';

// Componente SSOWeb.zip · nodo SSOWeb SERVER :80 (Nginx) · Máquina 1 · 10.43.99.2 (DMZ)
registerLocaleData(localeEsCo);

bootstrapApplication(PantallaWebSubasta, {
  providers: [provideHttpClient(withFetch()), { provide: LOCALE_ID, useValue: 'es-CO' }],
}).catch((err) => console.error(err));
