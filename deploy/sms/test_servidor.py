"""Pruebas del simulador SMS en un puerto efímero local: python3 -I deploy/sms/test_servidor.py"""
import importlib.util
import json
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.request
from pathlib import Path

ESPEC = importlib.util.spec_from_file_location("servidor", Path(__file__).with_name("servidor.py"))
servidor = importlib.util.module_from_spec(ESPEC)
ESPEC.loader.exec_module(servidor)

SMS = {"numero": "3159876543", "texto": "Su compra fue realizada"}


class PruebaSimuladorSms(unittest.TestCase):
    def setUp(self):
        carpeta = tempfile.TemporaryDirectory()
        self.addCleanup(carpeta.cleanup)
        servidor.ARCHIVO = Path(carpeta.name) / "sms.jsonl"
        servidor.FALLA.update(latencia_ms=0, errores_503=0)
        self.http = servidor.ThreadingHTTPServer(("127.0.0.1", 0), servidor.Pasarela)
        hilo = threading.Thread(target=self.http.serve_forever, daemon=True)
        hilo.start()
        self.addCleanup(hilo.join)
        self.addCleanup(self.http.server_close)
        self.addCleanup(self.http.shutdown)

    def pedir(self, metodo, ruta, cuerpo=None):
        """Devuelve (estado, JSON de la respuesta), también en respuestas de error."""
        datos = None if cuerpo is None else json.dumps(cuerpo).encode()
        peticion = urllib.request.Request(
            f"http://127.0.0.1:{self.http.server_address[1]}{ruta}", data=datos, method=metodo)
        try:
            with urllib.request.urlopen(peticion, timeout=5) as respuesta:
                return respuesta.status, json.load(respuesta)
        except urllib.error.HTTPError as error:
            with error:
                return error.code, json.load(error)

    def guardados(self):
        return self.pedir("GET", "/sms")[1]

    def test_sin_configurar_acepta_guarda_y_lista_sin_falla(self):
        self.assertEqual(self.pedir("GET", "/control/falla"), (200, {"latencia_ms": 0, "errores_503": 0}))

        inicio = time.monotonic()
        estado, respuesta = self.pedir("POST", "/sms", SMS)

        self.assertLess(time.monotonic() - inicio, 1)
        self.assertEqual(estado, 202)
        self.assertEqual(list(respuesta), ["id"])
        estado, lista = self.pedir("GET", "/sms")
        self.assertEqual(estado, 200)
        self.assertEqual(len(lista), 1)
        self.assertEqual(lista[0]["id"], respuesta["id"])
        self.assertEqual((lista[0]["numero"], lista[0]["texto"]), (SMS["numero"], SMS["texto"]))

    def test_lista_primero_el_mas_reciente(self):
        self.pedir("POST", "/sms", {**SMS, "texto": "primero"})
        self.pedir("POST", "/sms", {**SMS, "texto": "segundo"})

        self.assertEqual([s["texto"] for s in self.guardados()], ["segundo", "primero"])

    def test_cuerpo_invalido_sigue_siendo_400_y_no_consume_errores(self):
        servidor.FALLA.update(errores_503=1)

        self.assertEqual(self.pedir("POST", "/sms", {"numero": "3159876543"})[0], 400)
        self.assertEqual(self.pedir("POST", "/sms", {**SMS, "numero": "abc"})[0], 400)
        self.assertEqual(self.pedir("GET", "/control/falla")[1]["errores_503"], 1)

    def test_errores_503_contados_no_guardan_y_luego_se_recupera(self):
        self.assertEqual(self.pedir("PUT", "/control/falla", {"errores_503": 2})[0], 200)

        self.assertEqual(self.pedir("POST", "/sms", SMS)[0], 503)
        self.assertEqual(self.pedir("POST", "/sms", SMS)[0], 503)
        self.assertEqual(self.guardados(), [])
        self.assertEqual(self.pedir("POST", "/sms", SMS)[0], 202)
        self.assertEqual(len(self.guardados()), 1)
        self.assertEqual(self.pedir("GET", "/control/falla")[1]["errores_503"], 0)

    def test_errores_503_indefinidos_hasta_apagar_por_control(self):
        self.pedir("PUT", "/control/falla", {"errores_503": -1})

        for _ in range(3):
            self.assertEqual(self.pedir("POST", "/sms", SMS)[0], 503)
        self.assertEqual(self.pedir("GET", "/control/falla")[1]["errores_503"], -1)

        self.assertEqual(self.pedir("PUT", "/control/falla", {}), (200, {"latencia_ms": 0, "errores_503": 0}))
        self.assertEqual(self.pedir("POST", "/sms", SMS)[0], 202)

    def test_latencia_retrasa_el_post_pero_no_lectura_ni_salud(self):
        self.pedir("PUT", "/control/falla", {"latencia_ms": 400})

        inicio = time.monotonic()
        estado, _ = self.pedir("POST", "/sms", SMS)
        self.assertGreaterEqual(time.monotonic() - inicio, 0.4)
        self.assertEqual(estado, 202)

        inicio = time.monotonic()
        self.assertEqual(self.pedir("GET", "/salud"), (200, {"estado": "UP"}))
        self.assertEqual(len(self.guardados()), 1)
        self.assertLess(time.monotonic() - inicio, 0.4)

    def test_salud_sigue_arriba_durante_errores(self):
        self.pedir("PUT", "/control/falla", {"errores_503": -1})

        self.assertEqual(self.pedir("GET", "/salud"), (200, {"estado": "UP"}))

    def test_control_rechaza_valores_invalidos_sin_cambiar_el_modo(self):
        for invalido in ({"latencia_ms": -1}, {"errores_503": -2}, {"latencia_ms": "5"},
                         {"errores_503": True}, {"otro": 1}, [1]):
            with self.subTest(invalido=invalido):
                self.assertEqual(self.pedir("PUT", "/control/falla", invalido)[0], 400)
        self.assertEqual(self.pedir("GET", "/control/falla")[1], {"latencia_ms": 0, "errores_503": 0})

    def test_entorno_apagado_por_defecto_configurable_y_estricto(self):
        apagado = {"latencia_ms": 0, "errores_503": 0}
        self.assertEqual(servidor.falla_del_entorno({}), apagado)
        self.assertEqual(servidor.falla_del_entorno({"SMS_FALLA_LATENCIA_MS": ""}), apagado)
        self.assertEqual(
            servidor.falla_del_entorno({"SMS_FALLA_LATENCIA_MS": "250", "SMS_FALLA_ERRORES_503": "-1"}),
            {"latencia_ms": 250, "errores_503": -1})
        for invalido in ({"SMS_FALLA_LATENCIA_MS": "rapido"}, {"SMS_FALLA_ERRORES_503": "-2"}):
            with self.subTest(invalido=invalido), self.assertRaises(ValueError):
                servidor.falla_del_entorno(invalido)


if __name__ == "__main__":
    unittest.main()
