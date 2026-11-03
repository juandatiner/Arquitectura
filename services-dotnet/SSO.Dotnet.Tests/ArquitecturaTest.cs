using System.Reflection;
using System.Text.Json;
using Xunit;

namespace SSO.Dotnet.Tests;

/// <summary>
/// Regla de CLAUDE.md: una clase del diagrama solo depende de las clases a las que el diagrama «Arquitectura
/// Integrada JavaEE-.NET» le dibuja flecha (pasos de docs/modelo/modelo-canonico.json). Equivale al ArchUnit de Java.
/// </summary>
public sealed class ArquitecturaTest
{
    public static TheoryData<string, string> Componentes => new()
    {
        { "MSPago.dll", "MSPago" },
        { "YoPagoBackEnd.dll", "YoPagoBackEnd" },
        { "YoPagoFrontEnd.dll", "YoPagoFrontEnd" },
    };

    [Theory]
    [MemberData(nameof(Componentes))]
    public void LasClasesDelDiagramaSoloDependenSegunLasFlechas(string componente, string ensamblado)
    {
        var raiz = AppContext.BaseDirectory;
        while (!File.Exists(Path.Combine(raiz, "docs", "modelo", "modelo-canonico.json")))
        {
            raiz = Path.GetDirectoryName(raiz) ?? throw new InvalidOperationException("No se encontró docs/modelo");
        }
        using var modelo = JsonDocument.Parse(File.ReadAllText(Path.Combine(raiz, "docs", "modelo", "modelo-canonico.json")));
        var clases = modelo.RootElement.GetProperty("componentes").GetProperty(componente).GetProperty("clases")
            .EnumerateArray().Select(c => c.GetString()!).ToHashSet();
        var flechas = modelo.RootElement.GetProperty("pasos").EnumerateArray()
            .Select(p => p.GetProperty("de").GetString() + "→" + p.GetProperty("a").GetString()).ToHashSet();

        var tipos = Assembly.Load(ensamblado).GetTypes().Where(t => t.Namespace == ensamblado && clases.Contains(t.Name)).ToList();
        Assert.Equal(clases.Count, tipos.Count);
        var sinFlecha = new List<string>();
        foreach (var tipo in tipos)
        {
            var dependencias = tipo.GetConstructors().SelectMany(c => c.GetParameters()).Select(p => p.ParameterType)
                .Concat(tipo.GetFields(BindingFlags.Instance | BindingFlags.NonPublic).Select(f => f.FieldType))
                .Where(d => d.Namespace == ensamblado && clases.Contains(d.Name) && d != tipo)
                .Select(d => d.Name).Distinct();
            sinFlecha.AddRange(dependencias.Where(d => !flechas.Contains(tipo.Name + "→" + d)).Select(d => tipo.Name + " → " + d));
        }
        Assert.Empty(sinFlecha);
    }
}
