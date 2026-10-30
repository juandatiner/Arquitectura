package co.edu.javeriana.sso.mscompra;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Regla de CLAUDE.md: una clase del diagrama solo llama a las clases a las que el diagrama
 * «Arquitectura Integrada JavaEE-.NET» le dibuja flecha (pasos de
 * docs/modelo/modelo-canonico.json).
 */
class ArquitecturaTest {

    private static final String COMPONENTE = "MSCompra.jar";
    private static final String PAQUETE = "co.edu.javeriana.sso.mscompra";

    @Test
    void lasClasesDelDiagramaSoloLlamanSegunLasFlechas() {
        JsonNode modelo =
                JsonMapper.shared()
                        .readTree(Path.of("../../docs/modelo/modelo-canonico.json").toFile());
        Set<String> clases = new HashSet<>();
        modelo.get("componentes")
                .get(COMPONENTE)
                .get("clases")
                .forEach(c -> clases.add(c.asString()));
        Set<String> flechas = new HashSet<>();
        modelo.get("pasos")
                .forEach(p -> flechas.add(p.get("de").asString() + "→" + p.get("a").asString()));

        JavaClasses importadas =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages(PAQUETE);
        List<String> sinFlecha = new ArrayList<>();
        for (JavaClass origen : importadas) {
            if (!origen.getPackageName().equals(PAQUETE)
                    || !clases.contains(origen.getSimpleName())) {
                continue;
            }
            origen.getDirectDependenciesFromSelf().stream()
                    .map(d -> d.getTargetClass())
                    .filter(destino -> destino.getPackageName().equals(PAQUETE))
                    .map(JavaClass::getSimpleName)
                    .filter(clases::contains)
                    .filter(destino -> !destino.equals(origen.getSimpleName()))
                    .distinct()
                    .forEach(
                            destino -> {
                                if (!flechas.contains(origen.getSimpleName() + "→" + destino)) {
                                    sinFlecha.add(origen.getSimpleName() + " → " + destino);
                                }
                            });
        }
        assertThat(sinFlecha).as("Llamadas sin flecha en el diagrama").isEmpty();
    }
}
