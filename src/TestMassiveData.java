import com.morapack.models.*;
import com.morapack.utils.CSVDataLoader;
import com.morapack.utils.CSVDataLoader.DatosMoraPack;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * Prueba del sistema con datos masivos - REPORTE LIMPIO
 * Comparación enfocada entre HÍBRIDO vs GRASP vs GA
 */
public class TestMassiveData {

    public static void main(String[] args) {
        System.out.println("🚀 MORAPACK - COMPARACIÓN HÍBRIDO vs GRASP vs GA");
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

            System.out.printf("📋 Dataset cargado: %d aeropuertos, %d vuelos, %d pedidos%n",
                    datos.getTotalAeropuertos(), datos.getTotalVuelos(), datos.getTotalPedidos());

            // Ejecutar comparación limpia
            ejecutarComparacionLimpia(datos);

        } catch (Exception e) {
            System.err.printf("❌ Error: %s%n", e.getMessage());
            mostrarInstrucciones();
        }
    }

    /**
     * Ejecuta la comparación limpia entre los 3 algoritmos
     */
    private static void ejecutarComparacionLimpia(DatosMoraPack datos) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("EJECUTANDO ALGORITMOS DE OPTIMIZACIÓN");
        System.out.println("=".repeat(70));

        long inicioTotal = System.currentTimeMillis();

        // 1. GRASP PURO
        //System.out.println("\n🔵 EJECUTANDO GRASP...");
        long inicioGrasp = System.currentTimeMillis();
        GraspMoraPack grasp = new GraspMoraPack(datos.getPedidos(), datos.getVuelos());
        Solucion solucionGrasp = ejecutarGraspOptimizado(grasp);
        long tiempoGrasp = System.currentTimeMillis() - inicioGrasp;

        // 2. ALGORITMO GENÉTICO PURO
        //System.out.println("\n🟢 EJECUTANDO ALGORITMO GENÉTICO...");
        long inicioGA = System.currentTimeMillis();
        GeneticAlgorithmMoraPack ga = new GeneticAlgorithmMoraPack(datos.getPedidos(), datos.getVuelos());
        configurarGAParaDatosMasivos(ga);
        Solucion solucionGA = ga.ejecutar();
        long tiempoGA = System.currentTimeMillis() - inicioGA;

        // 3. HÍBRIDO GRASP + GA
        System.out.println("\n🟡 EJECUTANDO ALGORITMO GENÉTICO...");
        long inicioHibrido = System.currentTimeMillis();
        GraspGeneticHybrid hibrido = new GraspGeneticHybrid(datos.getPedidos(), datos.getVuelos());
        configurarHibridoParaDatosMasivos(hibrido);
        Solucion solucionHibrida = hibrido.ejecutarHibrido();
        long tiempoHibrido = System.currentTimeMillis() - inicioHibrido;
        escribirSolucionTXT("GENÉTICO", solucionHibrida, "SolucionesGenetico.txt");

        //4. GRASP + ACS
        System.out.println("\n🟣 EJECUTANDO ACS ...");
        long inicioACS = System.currentTimeMillis();
        Solucion solucionACS = ejecutarACSGrasp(datos, 10); // k=12 (ajusta si quieres)
        long tiempoACS = System.currentTimeMillis() - inicioACS;


        long tiempoTotal = System.currentTimeMillis() - inicioTotal;

        // REPORTE FINAL LIMPIO
        mostrarReporteFinalLimpio(solucionGrasp, solucionGA, solucionHibrida,solucionACS,
                tiempoGrasp, tiempoGA, tiempoHibrido,tiempoACS,
                datos.getTotalPedidos(), tiempoTotal);
    }

    /**
     * Ejecuta GRASP con configuración optimizada
     */
    private static Solucion ejecutarGraspOptimizado(GraspMoraPack grasp) {
        double[] alphas = {0.1, 0.2, 0.5, 0.8, 0.9};
        Solucion mejorSolucion = null;
        double mejorFitness = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < 20; i++) { // 8 iteraciones para datos masivos
            double alfa = alphas[i % alphas.length];
            grasp.setAlfa(alfa);
            Solucion solucion = grasp.generarSolucion();

            if (solucion != null && solucion.getFitness() > mejorFitness) {
                mejorFitness = solucion.getFitness();
                mejorSolucion = solucion;
            }
        }

        System.out.printf("  GRASP completado: Fitness = %.2f%n", mejorFitness);
        return mejorSolucion;
    }

    /**
     * Configura GA para datos masivos
     */
    private static void configurarGAParaDatosMasivos(GeneticAlgorithmMoraPack ga) {
        ga.setTamañoPoblacion(25);
        ga.setNumeroGeneraciones(40);
        ga.setTasaMutacion(0.18);
        ga.setTasaCruzamiento(0.82);
    }

    /**
     * Configura híbrido para datos masivos
     */
    private static void configurarHibridoParaDatosMasivos(GraspGeneticHybrid hibrido) {
        hibrido.setIteracionesGrasp(8);
        hibrido.setPorcentajePoblacionGrasp(0.35);
        hibrido.configurarParametrosGA(25, 40, 0.18, 0.82);
    }

    private static void escribirSolucionTXT(String nombreAlgoritmo, Solucion solucion, String rutaArchivo) {
        if (solucion == null || solucion.getSolucionLogistica() == null) {
            System.out.println("[AVISO] No hay solución para exportar: " + nombreAlgoritmo);
            return;
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(rutaArchivo))) {
            pw.println("MORAPACK - REPORTE SIMPLE");
            pw.println("Algoritmo: " + nombreAlgoritmo);
            pw.println("Fitness: " + solucion.getFitness());

            // Datos básicos
            int asignados = 0;
            if (solucion.getSolucionLogistica().getAsignacionPedidos() != null) {
                asignados = solucion.getSolucionLogistica().getAsignacionPedidos().size();
            }
            int total = solucion.getTotalPedidosProblema() > 0 ? solucion.getTotalPedidosProblema() : asignados;
            pw.println("Pedidos asignados: " + asignados + "/" + total);

            // Intentar imprimir métricas de tiempo si existen
            try {
                pw.println("Pedidos a tiempo: " + solucion.getSolucionLogistica().getCantidadAtiempo());
                pw.println("Pedidos en retraso: " + solucion.getSolucionLogistica().getCantidadRetraso());
            } catch (Throwable t) {
                // si tu clase no tiene estos métodos, simplemente se ignora
            }

            pw.println();
            pw.println("RUTAS (por pedido)");
            pw.println("----------------------------------------");

            // Recorrer las asignaciones
            var asignaciones = solucion.getSolucionLogistica().getAsignacionPedidos();
            if (asignaciones != null) {
                for (java.util.Map.Entry<Pedido, RutaPedido> e : asignaciones.entrySet()) {
                    Pedido p = e.getKey();
                    RutaPedido r = e.getValue();

                    pw.print("Pedido ");
                    pw.print(p != null ? p.getId() : "-");
                    pw.print(" | Cant: ");
                    pw.print(p != null ? p.getCantidad() : "-");
                    pw.print(" | Dest: ");
                    String destCode = "-";
                    try { destCode = p.getLugarDestino().getCodigo(); } catch (Throwable t) {}
                    pw.println(destCode);

                    pw.println("  Ruta: " + getRutaSimple(r));
                    pw.println();
                }
            } else {
                pw.println("(sin asignaciones)");
            }

            System.out.println("✔ TXT simple creado en: " + rutaArchivo);
        } catch (IOException e) {
            System.out.println("[ERROR] No se pudo escribir el TXT: " + e.getMessage());
        }
    }

    private static String getRutaSimple(RutaPedido r) {
        if (r == null || r.getSecuenciaVuelos() == null || r.getSecuenciaVuelos().isEmpty())
            return "(sin vuelos)";
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < r.getSecuenciaVuelos().size(); i++) {
            Vuelo v = r.getSecuenciaVuelos().get(i);
            try {
                if (i == 0) s.append(v.getOrigen().getCodigo());
                s.append("→").append(v.getDestino().getCodigo());
            } catch (Throwable t) {
                if (i > 0) s.append("→");
                s.append("?");
            }
        }
        return s.toString();
    }

    // Genera k soluciones con GRASP, configura ACS con esas semillas y ejecuta ACS.
    private static Solucion ejecutarACSGrasp(CSVDataLoader.DatosMoraPack datos, int kSemillas) {
        // 1) Generar k semillas con GRASP (simples y diversas por “firma de arcos”)
        java.util.List<Solucion> semillas = new java.util.ArrayList<>();
        java.util.Set<String> firmas = new java.util.HashSet<>();
        double[] alphas = new double[]{0.3, 0.4, 0.5, 0.6}; // variar alfa para diversidad

        int intentos = 0;
        int maxIntentos = Math.max(5 * kSemillas, 60); // margen para diversidad
        while (semillas.size() < kSemillas && intentos < maxIntentos) {
            GraspMoraPack grasp = new GraspMoraPack(datos.getPedidos(), datos.getVuelos());
            try {
                grasp.setAlfa(alphas[intentos % alphas.length]);
            } catch (Throwable ignored) { /* si no existe setAlfa, seguimos igual */ }

            Solucion s = grasp.generarSolucion(); // tu GRASP estándar (una solución)
            if (s != null) {
                String firma = firmaPorArcos(s);
                if (firmas.add(firma)) { // solo agrega si es distinta
                    semillas.add(s);
                }
            }
            intentos++;
        }

        System.out.printf("Semillas: %d/%d generadas%n", semillas.size(), kSemillas);

        // 2) Configurar y ejecutar ACS con esas semillas
        ACSMoraPack acs = new ACSMoraPack(datos.getPedidos(), datos.getVuelos());
        try {
            acs.establecerSemillas(semillas);
            acs.configurarParametrosACS(40, 100, 1.0, 3.0, 0.10, 0.10); // (hormigas, iters, α, β, ρ, ξ)
        } catch (Throwable ignored) { /* por si la firma difiere en tu versión */ }

        return acs.ejecutar();
    }

    // Firma compacta basada en arcos (IDs de vuelos) para medir diversidad
    private static String firmaPorArcos(Solucion s) {
        if (s == null || s.getSolucionLogistica() == null) return "";
        var asig = s.getSolucionLogistica().getAsignacionPedidos();
        java.util.Set<String> arcos = new java.util.HashSet<>();
        for (RutaPedido r : asig.values()) {
            for (Vuelo v : r.getSecuenciaVuelos()) {
                arcos.add(v.getId());
            }
        }
        java.util.List<String> lista = new java.util.ArrayList<>(arcos);
        java.util.Collections.sort(lista);
        return String.join("|", lista);
    }




    /**
     * REPORTE FINAL LIMPIO Y ENFOCADO
     */
    private static void mostrarReporteFinalLimpio(Solucion grasp, Solucion ga, Solucion hibrido,Solucion acs,
                                                  long tiempoGrasp, long tiempoGA, long tiempoHibrido, long tiempoAcs,
                                                  int totalPedidos, long tiempoTotal) {

        System.out.println("\n" + "=".repeat(70));
        System.out.println("🏆 RESULTADOS FINALES - COMPARACIÓN DE ALGORITMOS");
        System.out.println("=".repeat(70));

        // Tabla de resultados generales
        mostrarTablaResultados(grasp, ga, hibrido,acs, tiempoGrasp, tiempoGA, tiempoHibrido,tiempoAcs, totalPedidos);

        // Análisis detallado de fitness para los 3 algoritmos
        mostrarAnalisisDetalladoFitness(grasp, ga, hibrido,acs);

        // Comparación de rutas generadas
        mostrarComparacionRutas(grasp, ga, hibrido,acs, totalPedidos);

        // Determinación del ganador
        determinarGanador(grasp, ga, hibrido,acs);

        // Conclusiones
        //mostrarConclusiones(tiempoTotal);
    }

    /**
     * Tabla de resultados principal
     */
    private static void mostrarTablaResultados(Solucion grasp, Solucion ga, Solucion hibrido,Solucion acs,
                                               long tiempoGrasp, long tiempoGA, long tiempoHibrido,long tiempoAcs ,
                                               int totalPedidos) {

        System.out.println("\n📊 TABLA DE RESULTADOS PRINCIPALES:");
        System.out.println("-".repeat(70));
        System.out.printf("%-12s | %-10s | %-10s | %-12s | %-10s%n",
                "ALGORITMO", "FITNESS", "PEDIDOS", "COBERTURA", "TIEMPO(s)");
        System.out.println("-".repeat(70));
    /*
        if (grasp != null) {
            int pedidosGrasp = grasp.getSolucionLogistica().getAsignacionPedidos().size();
            double coberturaGrasp = (double) pedidosGrasp / totalPedidos * 100;
            System.out.printf("%-12s | %-10.2f | %-10d | %-11.1f%% | %-10.2f%n",
                    "GRASP", grasp.getFitness(), pedidosGrasp, coberturaGrasp, tiempoGrasp/1000.0);
        }*/
/*
        if (ga != null) {
            int pedidosGA = ga.getSolucionLogistica().getAsignacionPedidos().size();
            double coberturaGA = (double) pedidosGA / totalPedidos * 100;
            System.out.printf("%-12s | %-10.2f | %-10d | %-11.1f%% | %-10.2f%n",
                    "GENÉTICO", ga.getFitness(), pedidosGA, coberturaGA, tiempoGA/1000.0);
        }*/

        if (hibrido != null) {
            int pedidosHibrido = hibrido.getSolucionLogistica().getAsignacionPedidos().size();
            double coberturaHibrido = (double) pedidosHibrido / totalPedidos * 100;
            System.out.printf("%-12s | %-10.2f | %-10d | %-11.1f%% | %-10.2f%n",
                    "GENÉTICO", hibrido.getFitness(), pedidosHibrido, coberturaHibrido, tiempoHibrido/1000.0);
        }
        if (acs != null) {
            int pedidosACS = (acs.getSolucionLogistica() != null)
                    ? acs.getSolucionLogistica().getAsignacionPedidos().size() : 0;
            double coberturaACS = totalPedidos > 0 ? (double) pedidosACS / totalPedidos * 100 : 0.0;

            // Imprimir solo la fila ACS (sin reimprimir cabecera para no duplicar)
            System.out.printf("%-12s | %-10.2f | %-10d | %-11.1f%% | %-10.2f%n",
                    "ACS", acs.getFitness(), pedidosACS, coberturaACS, tiempoAcs / 1000.0);
        }

        System.out.println("-".repeat(70));
    }

    /**
     * Análisis detallado de componentes de fitness para los 3 algoritmos
     */
    private static void mostrarAnalisisDetalladoFitness(Solucion grasp, Solucion ga, Solucion hibrido,Solucion acs) {
        System.out.println("\n🔬 ANÁLISIS DETALLADO DE COMPONENTES DE FITNESS:");
        System.out.println("=".repeat(70));

       /* if (grasp != null) {
            System.out.println("\n🔵 GRASP - COMPONENTES DE FITNESS:");
            System.out.println(extraerComponentesFitness(grasp.obtenerReporteFitness()));
        }*/

        /*if (ga != null) {
            System.out.println("\n🟢 GENÉTICO - COMPONENTES DE FITNESS:");
            System.out.println(extraerComponentesFitness(ga.obtenerReporteFitness()));
        }*/

        if (hibrido != null) {
            System.out.println("\n🟡 GENÉTICO - COMPONENTES DE FITNESS:");
            System.out.println(extraerComponentesFitness(hibrido.obtenerReporteFitness()));
        }

        if (acs != null) {
            System.out.println("\n🟣 ACS - COMPONENTES DE FITNESS:");
            System.out.println(extraerComponentesFitness(acs.obtenerReporteFitness()));
        }
    }

    /**
     * Extrae solo las líneas relevantes del reporte de fitness
     */
    private static String extraerComponentesFitness(String reporteCompleto) {
        StringBuilder resultado = new StringBuilder();
        String[] lineas = reporteCompleto.split("\n");

        for (String linea : lineas) {
            if (linea.contains("FITNESS TOTAL:") ||
                    linea.contains("PRIORIDAD #") ||
                    linea.contains("PENALIZACIÓN") ||
                    linea.contains("- Cobertura total:") ||
                    linea.contains("- Pedidos a tiempo:") ||
                    linea.contains("- Factible:")) {
                resultado.append("   ").append(linea.trim()).append("\n");
            }
        }

        return resultado.toString();
    }

    /**
     * Comparación de rutas generadas entre los algoritmos
     */
    private static void mostrarComparacionRutas(Solucion grasp, Solucion ga, Solucion hibrido,Solucion acs, int totalPedidos) {
        System.out.println("\n🗺 COMPARACIÓN DE RUTAS GENERADAS:");
        System.out.println("=".repeat(70));

        // Contar tipos de rutas para cada algoritmo
       /* if (grasp != null) {
            System.out.println("\n🔵 GRASP - ANÁLISIS DE RUTAS:");
            analizarRutasSolucion(grasp, "GRASP");
        }*/

        /*if (ga != null) {
            System.out.println("\n🟢 GENÉTICO - ANÁLISIS DE RUTAS:");
            analizarRutasSolucion(ga, "GENÉTICO");
        }*/

        if (hibrido != null) {
            System.out.println("\n🟡 GENÉTICO - ANÁLISIS DE RUTAS:");
            analizarRutasSolucion(hibrido, "GENÉTICO");
        }
        if (acs != null) {
            System.out.println("\n🟡 ACS - ANÁLISIS DE RUTAS:");
            analizarRutasSolucion(acs, "ACS");
        }

        // Mostrar ejemplos de rutas side-by-side
        mostrarEjemplosRutasComparacion(grasp, ga, hibrido,acs);
    }

    /**
     * Analiza las rutas de una solución específica
     */
    private static void analizarRutasSolucion(Solucion solucion, String nombreAlgoritmo) {
        var rutas = solucion.getSolucionLogistica().getAsignacionPedidos();

        int rutasDirectas = 0;
        int rutasConEscala = 0;
        int rutasInternacionales = 0;
        int rutasDesdefabricas = 0;

        for (var ruta : rutas.values()) {
            // Contar escalas
            if (ruta.getSecuenciaVuelos().size() == 1) {
                rutasDirectas++;
            } else {
                rutasConEscala++;
            }

            // Contar internacionales
            if (ruta.getEsInternacional()) {
                rutasInternacionales++;
            }

            // Verificar origen desde fábrica
            if (!ruta.getSecuenciaVuelos().isEmpty()) {
                String origen = ruta.getSecuenciaVuelos().get(0).getOrigen().getCodigo();
                if (Solucion.FABRICAS.contains(origen)) {
                    rutasDesdefabricas++;
                }
            }
        }

        System.out.printf("   • Total de rutas: %d%n", rutas.size());
        System.out.printf("   • Rutas directas: %d (%.1f%%)%n",
                rutasDirectas, (double)rutasDirectas/rutas.size()*100);
        System.out.printf("   • Rutas con escala: %d (%.1f%%)%n",
                rutasConEscala, (double)rutasConEscala/rutas.size()*100);
        System.out.printf("   • Rutas internacionales: %d (%.1f%%)%n",
                rutasInternacionales, (double)rutasInternacionales/rutas.size()*100);
        System.out.printf("   • Rutas desde fábricas: %d (%.1f%%)%n",
                rutasDesdefabricas, (double)rutasDesdefabricas/rutas.size()*100);
    }

    /**
     * Muestra ejemplos de rutas lado a lado
     */
    private static void mostrarEjemplosRutasComparacion(Solucion grasp, Solucion ga, Solucion hibrido,Solucion acs) {
        System.out.println("\n📋 EJEMPLOS DE RUTAS (PRIMEROS 8 PEDIDOS):");
        System.out.println("-".repeat(50));
        System.out.printf("%-13s %-20s %-18s%n", "PEDIDO", "GENÉTICO" , "ACS" );
        System.out.println("-".repeat(50));

        // Obtener rutas de cada algoritmo
        var rutasGrasp = grasp != null ? grasp.getSolucionLogistica().getAsignacionPedidos() : null;
        var rutasGA = ga != null ? ga.getSolucionLogistica().getAsignacionPedidos() : null;
        var rutasHibrido = hibrido != null ? hibrido.getSolucionLogistica().getAsignacionPedidos() : null;
        var rutasAcs= acs != null ? acs.getSolucionLogistica().getAsignacionPedidos() : null;

        // Obtener primeros 8 pedidos para comparar
        int contador = 0;
        if (rutasGrasp != null) {
            for (var pedido : rutasGrasp.keySet()) {
                if (contador >= 8) break;

                String rutaGraspStr = obtenerRutaString(rutasGrasp.get(pedido));
                String rutaGAStr = rutasGA != null && rutasGA.containsKey(pedido) ?
                        obtenerRutaString(rutasGA.get(pedido)) : "NO ASIGNADO";
                String rutaHibridoStr = rutasHibrido != null && rutasHibrido.containsKey(pedido) ?
                        obtenerRutaString(rutasHibrido.get(pedido)) : "NO ASIGNADO";
                String rutaAcsStr = rutasAcs != null && rutasAcs.containsKey(pedido) ?
                        obtenerRutaString(rutasAcs.get(pedido)) : "NO ASIGNADO";
                System.out.printf("%-8s |   %-13s |   %-18s%n",
                        pedido.getId(),
                        truncar(rutaHibridoStr, 18),
                        truncar(rutaAcsStr, 18));
                contador++;
            }
        }
        System.out.println("-".repeat(70));
    }

    /**
     * Convierte una ruta a string compacto
     */
    private static String obtenerRutaString(RutaPedido ruta) {
        if (ruta == null || ruta.getSecuenciaVuelos().isEmpty()) {
            return "NO ASIGNADO";
        }

        var vuelos = ruta.getSecuenciaVuelos();
        if (vuelos.size() == 1) {
            return String.format("%s→%s",
                    vuelos.get(0).getOrigen().getCodigo(),
                    vuelos.get(0).getDestino().getCodigo());
        } else {
            return String.format("%s→%s→%s",
                    vuelos.get(0).getOrigen().getCodigo(),
                    vuelos.get(0).getDestino().getCodigo(),
                    vuelos.get(vuelos.size()-1).getDestino().getCodigo());
        }
    }

    /**
     * Trunca texto si es muy largo
     */
    private static String truncar(String texto, int maxLongitud) {
        if (texto.length() <= maxLongitud) {
            return texto;
        }
        return texto.substring(0, maxLongitud - 3) + "...";
    }

    /**
     * Determina y anuncia el ganador
     */
    private static void determinarGanador(Solucion grasp, Solucion ga, Solucion hibrido,Solucion acs) {
        System.out.println("\n🏆 DETERMINACIÓN DEL GANADOR:");
        System.out.println("=".repeat(50));

        Solucion ganador = Arrays.asList(hibrido,acs).stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparingDouble(Solucion::getFitness))
                .orElse(null);

        if (ganador != null) {
            String nombreGanador = "DESCONOCIDO";
            if (ganador == hibrido) nombreGanador = "GENÉTICO";
            else if (ganador == acs) nombreGanador = "ACS";


            System.out.printf("🥇 GANADOR: %s con fitness %.2f%n", nombreGanador, ganador.getFitness());

            // Calcular diferencias
            if (grasp != null && ga != null && hibrido != null) {
                double difHibridoVsGrasp = hibrido.getFitness() - grasp.getFitness();
                double difHibridoVsGA = hibrido.getFitness() - ga.getFitness();
                double difGAVsGrasp = ga.getFitness() - grasp.getFitness();
                double difGAVsAcs = ga.getFitness() - acs.getFitness();

                System.out.println("\n📊 DIFERENCIAS DE RENDIMIENTO:");
                /*System.out.printf("   • Híbrido vs GRASP: %+.2f puntos%n", difHibridoVsGrasp);
                System.out.printf("   • Híbrido vs Genético: %+.2f puntos%n", difHibridoVsGA);
                System.out.printf("   • Genético vs GRASP: %+.2f puntos%n", difGAVsGrasp);*/
                System.out.printf("   • Genético vs ACS: %+.2f puntos%n", difGAVsAcs);
                // Porcentajes de mejora
                if (grasp.getFitness() > 0) {
                    double mejoraPorcentualHibrido = (difGAVsAcs / Math.abs(grasp.getFitness())) * 100;
                    System.out.printf("   • Mejora porcentual GENÉTICO sobre ACS: %+.2f%%%n", mejoraPorcentualHibrido);
                }
            }
        }
    }

    /**
     * Conclusiones finales del experimento
     */
    private static void mostrarConclusiones(long tiempoTotal) {
        System.out.println("\n📝 CONCLUSIONES:");
        System.out.println("=".repeat(50));

        System.out.printf("⏱ Tiempo total de experimentación: %.2f segundos%n", tiempoTotal / 1000.0);

        System.out.println("\n💡 OBSERVACIONES:");
        System.out.println("   • GRASP: Rápido, buenas soluciones iniciales");
        System.out.println("   • Genético: Exploración amplia, puede encontrar soluciones inesperadas");
        System.out.println("   • Híbrido: Combina fortalezas de ambos enfoques");

        System.out.println("\n✅ Experimento completado exitosamente.");
    }

    /**
     * Muestra instrucciones si hay error
     */
    private static void mostrarInstrucciones() {
        System.out.println("\n📝 INSTRUCCIONES:");
        System.out.println("1. Crea una carpeta 'data' en tu proyecto");
        System.out.println("2. Guarda los archivos CSV en esa carpeta:");
        System.out.println("   - aeropuertos.csv");
        System.out.println("   - vuelos.txt");
        System.out.println("   - pedidos.csv");
        System.out.println("3. Ajusta las rutas en el código si es necesario");
    }
}