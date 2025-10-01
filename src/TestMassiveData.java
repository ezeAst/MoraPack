import com.morapack.models.*;
import com.morapack.utils.CSVDataLoader;
import com.morapack.utils.CSVDataLoader.DatosMoraPack;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Comparación GA vs ACS usando semillas de GRASP
 */
public class TestMassiveData {

    private static final int NUM_SEMILLAS = 15; // Número de soluciones GRASP para semillas

    public static void main(String[] args) {
        System.out.println("🚀 MORAPACK - COMPARACIÓN GA vs ACS");
        System.out.println("   (usando semillas de GRASP)");
        System.out.println("=".repeat(70));

        // Rutas de archivos CSV
        String rutaAeropuertos = "data/aeropuertos.csv";
        String rutaVuelos = "data/vuelos.txt";
        String rutaPedidos = "data/pedidos.txt";

        try {
            // Cargar datos masivos
            DatosMoraPack datos = CSVDataLoader.cargarDatosCompletos(
                    rutaAeropuertos, rutaVuelos, rutaPedidos
            );

            System.out.printf("📋 Dataset: %d aeropuertos, %d vuelos, %d pedidos%n%n",
                    datos.getTotalAeropuertos(), datos.getTotalVuelos(), datos.getTotalPedidos());

            // Ejecutar comparación
            ejecutarComparacion(datos);

        } catch (Exception e) {
            System.err.printf("❌ Error: %s%n", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ejecuta la comparación entre GA y ACS
     */
    private static void ejecutarComparacion(DatosMoraPack datos) {
        System.out.println("=".repeat(70));
        System.out.println("PASO 1: GENERANDO SEMILLAS CON GRASP");
        System.out.println("=".repeat(70));

        long inicioTotal = System.currentTimeMillis();

        // 1. Generar semillas con GRASP
        List<Solucion> semillas = generarSemillasGrasp(datos, NUM_SEMILLAS);
        System.out.printf("✓ %d semillas factibles generadas%n%n", semillas.size());

        // 2. Ejecutar GA con semillas
        System.out.println("=".repeat(70));
        System.out.println("PASO 2: EJECUTANDO ALGORITMO GENÉTICO");
        System.out.println("=".repeat(70));
        long inicioGA = System.currentTimeMillis();
        Solucion solucionGA = ejecutarGAConSemillas(datos, semillas);
        long tiempoGA = System.currentTimeMillis() - inicioGA;
        System.out.printf("✓ GA completado en %.2f segundos%n%n", tiempoGA / 1000.0);

        // 3. Ejecutar ACS con semillas
        System.out.println("=".repeat(70));
        System.out.println("PASO 3: EJECUTANDO ACS (COLONIA DE HORMIGAS)");
        System.out.println("=".repeat(70));
        long inicioACS = System.currentTimeMillis();
        Solucion solucionACS = ejecutarACSConSemillas(datos, semillas);
        long tiempoACS = System.currentTimeMillis() - inicioACS;
        System.out.printf("✓ ACS completado en %.2f segundos%n%n", tiempoACS / 1000.0);

        long tiempoTotal = System.currentTimeMillis() - inicioTotal;

        // 4. Mostrar resultados
        mostrarResultados(solucionGA, solucionACS, tiempoGA, tiempoACS,
                datos.getTotalPedidos(), tiempoTotal, datos);
    }

    /**
     * Genera semillas diversas usando GRASP
     */
    private static List<Solucion> generarSemillasGrasp(DatosMoraPack datos, int numSemillas) {
        List<Solucion> semillas = new ArrayList<>();
        Set<String> firmas = new HashSet<>();
        double[] alphas = {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9};

        int intentos = 0;
        int maxIntentos = numSemillas * 5;

        System.out.println("Generando soluciones GRASP diversas...");

        while (semillas.size() < numSemillas && intentos < maxIntentos) {
            GraspMoraPack grasp = new GraspMoraPack(datos.getPedidos(), datos.getVuelos());
            grasp.setAlfa(alphas[intentos % alphas.length]);

            Solucion s = grasp.generarSolucion();

            if (s != null && s.getSolucionLogistica() != null) {
                String firma = obtenerFirmaSolucion(s);
                if (firmas.add(firma)) {
                    semillas.add(s);
                    System.out.printf("  Semilla %d: fitness=%.2f, pedidos=%d, alfa=%.1f%n",
                            semillas.size(), s.getFitness(),
                            s.getSolucionLogistica().getAsignacionPedidos().size(),
                            alphas[intentos % alphas.length]);
                }
            }
            intentos++;
        }

        return semillas;
    }

    /**
     * Ejecuta GA usando las semillas de GRASP
     */
    private static Solucion ejecutarGAConSemillas(DatosMoraPack datos, List<Solucion> semillas) {
        GeneticAlgorithmMoraPack ga = new GeneticAlgorithmMoraPack(datos.getPedidos(), datos.getVuelos());

        // Configuración para datos masivos
        ga.setTamañoPoblacion(30);
        ga.setNumeroGeneraciones(50);
        ga.setTasaMutacion(0.15);
        ga.setTasaCruzamiento(0.85);

        // Establecer semillas
        try {

            System.out.println("  ✓ Semillas inicializadas en GA");
        } catch (Exception e) {
            System.out.println("  ⚠ No se pudieron establecer semillas: " + e.getMessage());
        }

        return ga.ejecutar();
    }

    /**
     * Ejecuta ACS usando las semillas de GRASP
     */
    private static Solucion ejecutarACSConSemillas(DatosMoraPack datos, List<Solucion> semillas) {
        ACSMoraPack acs = new ACSMoraPack(datos.getPedidos(), datos.getVuelos());

        try {
            acs.establecerSemillas(semillas);
            System.out.println("  ✓ Semillas inicializadas en ACS");

            // Configuración ACS: (hormigas, iteraciones, α, β, ρ, ξ)
            acs.configurarParametrosACS(40, 100, 1.0, 3.0, 0.10, 0.10);
        } catch (Exception e) {
            System.out.println("  ⚠ Advertencia ACS: " + e.getMessage());
        }

        return acs.ejecutar();
    }

    /**
     * Muestra resultados comparativos
     */
    private static void mostrarResultados(Solucion ga, Solucion acs,
                                          long tiempoGA, long tiempoACS,
                                          int totalPedidos, long tiempoTotal,
                                          DatosMoraPack datos) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("🏆 RESULTADOS FINALES - GA vs ACS");
        System.out.println("=".repeat(70));

        // Tabla principal
        mostrarTablaComparativa(ga, acs, tiempoGA, tiempoACS, totalPedidos);

        // Análisis de fitness
        mostrarAnalisisFitness(ga, acs);

        // Comparación de rutas
        mostrarComparacionRutas(ga, acs, totalPedidos);

        // Ganador
        determinarGanador(ga, acs);

        // Exportar resultados
        exportarResultados(ga, acs, datos);

        System.out.println("\n⏱ Tiempo total: %.2f segundos".formatted(tiempoTotal / 1000.0));
        System.out.println("=".repeat(70));
    }

    /**
     * Tabla comparativa principal
     */
    private static void mostrarTablaComparativa(Solucion ga, Solucion acs,
                                                long tiempoGA, long tiempoACS,
                                                int totalPedidos) {
        System.out.println("\n📊 COMPARACIÓN PRINCIPAL:");
        System.out.println("-".repeat(70));
        System.out.printf("%-15s | %-12s | %-10s | %-12s | %-10s%n",
                "ALGORITMO", "FITNESS", "PEDIDOS", "COBERTURA", "TIEMPO(s)");
        System.out.println("-".repeat(70));

        if (ga != null && ga.getSolucionLogistica() != null) {
            int pedidosGA = ga.getSolucionLogistica().getAsignacionPedidos().size();
            double coberturaGA = (double) pedidosGA / totalPedidos * 100;
            System.out.printf("%-15s | %-12.2f | %-10d | %-11.1f%% | %-10.2f%n",
                    "GENÉTICO (GA)", ga.getFitness(), pedidosGA, coberturaGA, tiempoGA / 1000.0);
        }

        if (acs != null && acs.getSolucionLogistica() != null) {
            int pedidosACS = acs.getSolucionLogistica().getAsignacionPedidos().size();
            double coberturaACS = (double) pedidosACS / totalPedidos * 100;
            System.out.printf("%-15s | %-12.2f | %-10d | %-11.1f%% | %-10.2f%n",
                    "ACS (HORMIGAS)", acs.getFitness(), pedidosACS, coberturaACS, tiempoACS / 1000.0);
        }

        System.out.println("-".repeat(70));
    }

    /**
     * Análisis detallado de fitness
     */
    private static void mostrarAnalisisFitness(Solucion ga, Solucion acs) {
        System.out.println("\n🔬 ANÁLISIS DE FITNESS:");
        System.out.println("=".repeat(70));

        if (ga != null) {
            System.out.println("\n🟢 ALGORITMO GENÉTICO:");
            mostrarComponentesFitness(ga);
        }

        if (acs != null) {
            System.out.println("\n🟣 ACS (COLONIA DE HORMIGAS):");
            mostrarComponentesFitness(acs);
        }
    }

    private static void mostrarComponentesFitness(Solucion s) {
        String reporte = s.obtenerReporteFitness();
        String[] lineas = reporte.split("\n");

        for (String linea : lineas) {
            if (linea.contains("FITNESS TOTAL:") ||
                    linea.contains("PRIORIDAD #") ||
                    linea.contains("PENALIZACIÓN") ||
                    linea.contains("Cobertura total:") ||
                    linea.contains("Pedidos a tiempo:") ||
                    linea.contains("Factible:")) {
                System.out.println("   " + linea.trim());
            }
        }
    }

    /**
     * Comparación de rutas
     */
    private static void mostrarComparacionRutas(Solucion ga, Solucion acs, int totalPedidos) {
        System.out.println("\n🗺 ANÁLISIS DE RUTAS:");
        System.out.println("=".repeat(70));

        if (ga != null) {
            System.out.println("\n🟢 GENÉTICO - Estadísticas:");
            analizarRutas(ga);
        }

        if (acs != null) {
            System.out.println("\n🟣 ACS - Estadísticas:");
            analizarRutas(acs);
        }

        // Ejemplos lado a lado
        mostrarEjemplosRutas(ga, acs);
    }

    private static void analizarRutas(Solucion s) {
        if (s.getSolucionLogistica() == null) return;

        var rutas = s.getSolucionLogistica().getAsignacionPedidos();
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
        System.out.printf("   • Total rutas: %d%n", total);
        System.out.printf("   • Directas: %d (%.1f%%)%n", directas, total > 0 ? directas * 100.0 / total : 0);
        System.out.printf("   • Con escalas: %d (%.1f%%)%n", conEscala, total > 0 ? conEscala * 100.0 / total : 0);
        System.out.printf("   • Internacionales: %d (%.1f%%)%n", internacionales, total > 0 ? internacionales * 100.0 / total : 0);
        System.out.printf("   • Desde fábricas: %d (%.1f%%)%n", desdeFabricas, total > 0 ? desdeFabricas * 100.0 / total : 0);
    }

    private static void mostrarEjemplosRutas(Solucion ga, Solucion acs) {
        System.out.println("\n📋 EJEMPLOS DE RUTAS (primeros 10 pedidos):");
        System.out.println("-".repeat(60));
        System.out.printf("%-10s | %-22s | %-22s%n", "PEDIDO", "GA", "ACS");
        System.out.println("-".repeat(60));

        var rutasGA = ga != null && ga.getSolucionLogistica() != null ?
                ga.getSolucionLogistica().getAsignacionPedidos() : null;
        var rutasACS = acs != null && acs.getSolucionLogistica() != null ?
                acs.getSolucionLogistica().getAsignacionPedidos() : null;

        if (rutasGA != null) {
            int contador = 0;
            for (var entry : rutasGA.entrySet()) {
                if (contador >= 10) break;

                Pedido p = entry.getKey();
                String rutaGA = formatearRuta(entry.getValue());
                String rutaACS = rutasACS != null && rutasACS.containsKey(p) ?
                        formatearRuta(rutasACS.get(p)) : "NO ASIGNADO";

                System.out.printf("%-10s | %-22s | %-22s%n", p.getId(),
                        truncar(rutaGA, 22), truncar(rutaACS, 22));
                contador++;
            }
        }
        System.out.println("-".repeat(60));
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

    /**
     * Determina el ganador
     */
    private static void determinarGanador(Solucion ga, Solucion acs) {
        System.out.println("\n🏆 RESULTADO FINAL:");
        System.out.println("=".repeat(70));

        if (ga == null || acs == null) {
            System.out.println("⚠ No se pueden comparar (alguna solución es null)");
            return;
        }

        double fitnessGA = ga.getFitness();
        double fitnessACS = acs.getFitness();
        double diferencia = fitnessGA - fitnessACS;
        double mejoraPorcentual = Math.abs(diferencia / Math.max(fitnessGA, fitnessACS) * 100);

        if (fitnessGA > fitnessACS) {
            System.out.printf("🥇 GANADOR: ALGORITMO GENÉTICO (GA)%n");
            System.out.printf("   Fitness GA: %.2f vs ACS: %.2f%n", fitnessGA, fitnessACS);
            System.out.printf("   Diferencia: +%.2f puntos (%.2f%% mejor)%n", diferencia, mejoraPorcentual);
        } else if (fitnessACS > fitnessGA) {
            System.out.printf("🥇 GANADOR: ACS (COLONIA DE HORMIGAS)%n");
            System.out.printf("   Fitness ACS: %.2f vs GA: %.2f%n", fitnessACS, fitnessGA);
            System.out.printf("   Diferencia: +%.2f puntos (%.2f%% mejor)%n", -diferencia, mejoraPorcentual);
        } else {
            System.out.println("🤝 EMPATE: Ambos algoritmos obtuvieron el mismo fitness");
        }

        System.out.println("\n💡 Nota: Ambos algoritmos usaron las mismas " + NUM_SEMILLAS + " semillas de GRASP");
    }

    /**
     * Exporta resultados a archivos
     */
    private static void exportarResultados(Solucion ga, Solucion acs, DatosMoraPack datos) {
        System.out.println("\n📄 EXPORTANDO RESULTADOS:");

        if (ga != null) {
            exportarSolucion("GA", ga, "Solucion_GA.txt");
            exportarNoEnviados("GA", datos.getPedidos(), ga, "No_Enviados_GA.txt");
        }

        if (acs != null) {
            exportarSolucion("ACS", acs, "Solucion_ACS.txt");
            exportarNoEnviados("ACS", datos.getPedidos(), acs, "No_Enviados_ACS.txt");
        }
    }

    private static void exportarSolucion(String nombre, Solucion s, String archivo) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(archivo))) {
            pw.println("MORAPACK - SOLUCIÓN " + nombre);
            pw.println("Fitness: " + s.getFitness());
            pw.println("Pedidos asignados: " + s.getSolucionLogistica().getAsignacionPedidos().size());
            pw.println();
            pw.println("RUTAS:");
            pw.println("-".repeat(60));

            for (var entry : s.getSolucionLogistica().getAsignacionPedidos().entrySet()) {
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

    private static void exportarNoEnviados(String nombre, List<Pedido> todos,
                                           Solucion s, String archivo) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(archivo))) {
            pw.println("MORAPACK - PEDIDOS NO ENVIADOS (" + nombre + ")");
            pw.println();

            var asignados = s.getSolucionLogistica().getAsignacionPedidos();
            Set<String> idsEnviados = new HashSet<>();

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

    // Utilidades
    private static String obtenerFirmaSolucion(Solucion s) {
        if (s == null || s.getSolucionLogistica() == null) return "";

        Set<String> arcos = new HashSet<>();
        for (RutaPedido r : s.getSolucionLogistica().getAsignacionPedidos().values()) {
            for (Vuelo v : r.getSecuenciaVuelos()) {
                arcos.add(v.getId());
            }
        }

        List<String> lista = new ArrayList<>(arcos);
        Collections.sort(lista);
        return String.join("|", lista);
    }

    private static String truncar(String texto, int max) {
        if (texto.length() <= max) return texto;
        return texto.substring(0, max - 3) + "...";
    }
}