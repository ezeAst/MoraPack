import com.morapack.models.*;
import com.morapack.utils.CSVDataLoader;
import com.morapack.utils.CSVDataLoader.DatosMoraPack;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Ejecución del algoritmo híbrido GRASP + Genético
 */
public class TestHybridGA {

    public static void main(String[] args) {
        System.out.println("🚀 MORAPACK - ALGORITMO HÍBRIDO GRASP + GA");
        System.out.println("=".repeat(70));

        // Rutas de archivos
        String rutaAeropuertos = "data/aeropuertos.csv";
        String rutaVuelos = "data/vuelos.txt";
        String rutaPedidos = "data/pedidos.txt";

        try {
            // Cargar datos
            DatosMoraPack datos = CSVDataLoader.cargarDatosCompletos(
                    rutaAeropuertos, rutaVuelos, rutaPedidos
            );

            System.out.printf("📋 Dataset: %d aeropuertos, %d vuelos, %d pedidos%n%n",
                    datos.getTotalAeropuertos(), datos.getTotalVuelos(), datos.getTotalPedidos());

            // Ejecutar algoritmo híbrido
            ejecutarHibrido(datos);

        } catch (Exception e) {
            System.err.printf("❌ Error: %s%n", e.getMessage());
            e.printStackTrace();
        }
    }

    private static void ejecutarHibrido(DatosMoraPack datos) {
        System.out.println("=".repeat(70));
        System.out.println("EJECUTANDO ALGORITMO HÍBRIDO GRASP + GA");
        System.out.println("=".repeat(70));

        long inicio = System.currentTimeMillis();

        // Crear y ejecutar el híbrido
        GraspGeneticHybrid ga = new GraspGeneticHybrid(datos.getPedidos(), datos.getVuelos());
        Solucion solucion = ga.ejecutarHibrido();

        long tiempo = System.currentTimeMillis() - inicio;

        // Mostrar resultados
        mostrarResultados(solucion, tiempo, datos);

        // Exportar resultados
        exportarResultados(solucion, datos);
    }

    private static void mostrarResultados(Solucion solucion, long tiempo, DatosMoraPack datos) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("🏆 RESULTADOS FINALES");
        System.out.println("=".repeat(70));

        if (solucion == null || solucion.getSolucionLogistica() == null) {
            System.out.println("⚠️ No se generó solución");
            return;
        }

        int pedidosAsignados = solucion.getSolucionLogistica().getAsignacionPedidos().size();
        int totalPedidos = datos.getTotalPedidos();
        double cobertura = (double) pedidosAsignados / totalPedidos * 100;

        System.out.println("\n📊 MÉTRICAS PRINCIPALES:");
        System.out.println("-".repeat(70));
        System.out.printf("Fitness: %.2f%n", solucion.getFitness());
        System.out.printf("Pedidos asignados: %d/%d (%.1f%%)%n",
                pedidosAsignados, totalPedidos, cobertura);
        System.out.printf("Tiempo de ejecución: %.2f segundos%n", tiempo / 1000.0);
        System.out.printf("Solución factible: %s%n", solucion.esSolucionFactible() ? "Sí" : "No");
        System.out.println("-".repeat(70));

        // Reporte detallado de fitness
        System.out.println("\n" + solucion.obtenerReporteFitness());

        // Estadísticas de rutas
        mostrarEstadisticasRutas(solucion);
    }

    private static void mostrarEstadisticasRutas(Solucion solucion) {
        System.out.println("\n🗺️ ANÁLISIS DE RUTAS:");
        System.out.println("=".repeat(70));

        var rutas = solucion.getSolucionLogistica().getAsignacionPedidos();
        int directas = 0, conEscala = 0, internacionales = 0, desdeFabricas = 0;

        for (var ruta : rutas.values()) {
            if (ruta.getSecuenciaVuelos().size() == 1) directas++;
            else conEscala++;

            if (ruta.getEsInternacional()) internacionales++;

            if (!ruta.getSecuenciaVuelos().isEmpty()) {
                String origen = ruta.getSecuenciaVuelos().get(0).getOrigen().getCodigo();
                if (Solucion.FABRICAS.contains(origen)) desdeFabricas++;
            }
        }

        int total = rutas.size();
        System.out.printf("• Total rutas: %d%n", total);
        System.out.printf("• Directas: %d (%.1f%%)%n", directas,
                total > 0 ? directas * 100.0 / total : 0);
        System.out.printf("• Con escalas: %d (%.1f%%)%n", conEscala,
                total > 0 ? conEscala * 100.0 / total : 0);
        System.out.printf("• Internacionales: %d (%.1f%%)%n", internacionales,
                total > 0 ? internacionales * 100.0 / total : 0);
        System.out.printf("• Desde fábricas: %d (%.1f%%)%n", desdeFabricas,
                total > 0 ? desdeFabricas * 100.0 / total : 0);
    }

    private static void exportarResultados(Solucion solucion, DatosMoraPack datos) {
        System.out.println("\n📄 EXPORTANDO RESULTADOS:");

        // Exportar solución completa
        exportarSolucion(solucion, "Solucion_GA.txt");

        // Exportar pedidos no enviados
        exportarNoEnviados(datos.getPedidos(), solucion, "No_Enviados_GA.txt");
    }

    private static void exportarSolucion(Solucion solucion, String archivo) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(archivo))) {
            pw.println("MORAPACK - SOLUCIÓN HÍBRIDO GRASP + GA");
            pw.println("Fitness: " + solucion.getFitness());
            pw.println("Pedidos asignados: " +
                    solucion.getSolucionLogistica().getAsignacionPedidos().size());
            pw.println();
            pw.println("RUTAS:");
            pw.println("-".repeat(60));

            for (var entry : solucion.getSolucionLogistica().getAsignacionPedidos().entrySet()) {
                Pedido p = entry.getKey();
                RutaPedido r = entry.getValue();

                pw.printf("Pedido %s | Cant: %d | Destino: %s%n",
                        p.getId(), p.getCantidad(), p.getLugarDestino().getCodigo());
                pw.println("  Ruta: " + formatearRuta(r));
                pw.println();
            }

            System.out.println("  ✓ " + archivo);
        } catch (IOException e) {
            System.out.println("  ✗ Error escribiendo " + archivo);
        }
    }

    private static void exportarNoEnviados(java.util.List<Pedido> todos,
                                           Solucion solucion, String archivo) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(archivo))) {
            pw.println("MORAPACK - PEDIDOS NO ENVIADOS (HÍBRIDO GA)");
            pw.println();

            var asignados = solucion.getSolucionLogistica().getAsignacionPedidos();
            java.util.Set<String> idsEnviados = new java.util.HashSet<>();

            for (var entry : asignados.entrySet()) {
                if (entry.getValue().getSecuenciaVuelos() != null &&
                        !entry.getValue().getSecuenciaVuelos().isEmpty()) {
                    idsEnviados.add(String.valueOf(entry.getKey().getId()));
                }
            }

            pw.println("ID | Cantidad | Destino");
            pw.println("-".repeat(40));

            int noEnviados = 0;
            for (Pedido p : todos) {
                if (!idsEnviados.contains(String.valueOf(p.getId()))) {
                    pw.printf("%s | %d | %s%n", p.getId(), p.getCantidad(),
                            p.getLugarDestino().getCodigo());
                    noEnviados++;
                }
            }

            pw.println();
            pw.println("Total no enviados: " + noEnviados + "/" + todos.size());
            System.out.println("  ✓ " + archivo);
        } catch (IOException e) {
            System.out.println("  ✗ Error escribiendo " + archivo);
        }
    }

    private static String formatearRuta(RutaPedido ruta) {
        if (ruta == null || ruta.getSecuenciaVuelos().isEmpty()) {
            return "NO ASIGNADO";
        }

        var vuelos = ruta.getSecuenciaVuelos();
        StringBuilder sb = new StringBuilder();
        sb.append(vuelos.get(0).getOrigen().getCodigo());

        for (Vuelo v : vuelos) {
            sb.append("→").append(v.getDestino().getCodigo());
        }

        return sb.toString();
    }
}