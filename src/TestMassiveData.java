import com.morapack.models.*;
import com.morapack.utils.CSVDataLoader;
import com.morapack.utils.CSVDataLoader.DatosMoraPack;

import java.util.Comparator;
import java.util.Map;

/**
 * Prueba limpia con datos masivos - Solo información esencial
 */
public class TestMassiveData {

    public static void main(String[] args) {
        System.out.println("🚀 MORAPACK - ANÁLISIS LIMPIO CON DATOS MASIVOS");
        System.out.println("=".repeat(60));

        // Rutas de archivos
        String rutaAeropuertos = "data/aeropuertos.csv";
        String rutaVuelos = "data/vuelos.csv";
        String rutaPedidos = "data/pedidos.csv";

        try {
            // ==========================================
            // 1. CARGAR DATOS (SILENCIOSO)
            // ==========================================
            DatosMoraPack datos = cargarDatosSilencioso(rutaAeropuertos, rutaVuelos, rutaPedidos);

            // ==========================================
            // 2. VERIFICACIÓN COMPACTA DE DATOS
            // ==========================================
            mostrarVerificacionCompacta(datos);

            // ==========================================
            // 3. EJECUTAR SOLO GRASP vs HÍBRIDO
            // ==========================================
            ejecutarComparacionLimpia(datos);

        } catch (Exception e) {
            System.err.printf("❌ Error: %s%n", e.getMessage());
            mostrarInstrucciones();
        }
    }

    /**
     * Carga datos sin imprimir logs innecesarios
     */
    private static DatosMoraPack cargarDatosSilencioso(String aeropuertos, String vuelos, String pedidos) {
        // Redirigir System.out temporalmente para silenciar logs
        java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
            public void write(int b) {} // No hacer nada - silenciar
        }));

        try {
            DatosMoraPack datos = CSVDataLoader.cargarDatosCompletos(aeropuertos, vuelos, pedidos);
            return datos;
        } finally {
            // Restaurar System.out
            System.setOut(originalOut);
        }
    }

    /**
     * Muestra verificación muy compacta de los datos cargados
     */
    private static void mostrarVerificacionCompacta(DatosMoraPack datos) {
        System.out.println("\n📋 VERIFICACIÓN DE DATOS CARGADOS:");
        System.out.println("-".repeat(40));

        // Resumen básico
        System.out.printf("✅ Aeropuertos: %d | Vuelos: %d | Pedidos: %d%n",
                datos.getTotalAeropuertos(), datos.getTotalVuelos(), datos.getTotalPedidos());

        // Verificar fábricas
        long fabricasValidas = datos.getAeropuertos().stream()
                .filter(a -> Solucion.FABRICAS.contains(a.getCodigo()))
                .count();
        System.out.printf("✅ Fábricas válidas: %d/3 (%s)%n",
                fabricasValidas, Solucion.FABRICAS.toString());

        // Capacidad vs demanda
        int capacidadTotal = datos.getVuelos().stream().mapToInt(Vuelo::getCapacidadMaxima).sum();
        int demandaTotal = datos.getPedidos().stream().mapToInt(Pedido::getCantidad).sum();
        double ratio = (double) capacidadTotal / demandaTotal;
        System.out.printf("✅ Capacidad: %d | Demanda: %d | Ratio: %.2f %s%n",
                capacidadTotal, demandaTotal, ratio,
                ratio >= 1.0 ? "(Factible)" : "(Sobrecarga)");

        System.out.println("-".repeat(40));
    }

    /**
     * Ejecuta comparación limpia entre GRASP y HÍBRIDO
     */
    private static void ejecutarComparacionLimpia(DatosMoraPack datos) {
        System.out.println("\n🔄 EJECUTANDO COMPARACIÓN: GRASP vs HÍBRIDO");
        System.out.println("=".repeat(60));

        // ==========================================
        // EJECUTAR GRASP (SILENCIOSO)
        // ==========================================
        System.out.print("🔵 Ejecutando GRASP... ");
        Solucion solucionGrasp = ejecutarGraspSilencioso(datos);
        System.out.println("✅ Completado");

        // ==========================================
        // EJECUTAR HÍBRIDO (SILENCIOSO)
        // ==========================================
        System.out.print("🟡 Ejecutando HÍBRIDO... ");
        Solucion solucionHibrida = ejecutarHibridoSilencioso(datos);
        System.out.println("✅ Completado");

        // ==========================================
        // COMPARACIÓN DETALLADA
        // ==========================================
        mostrarComparacionDetallada(solucionGrasp, solucionHibrida, datos.getTotalPedidos());
    }

    /**
     * Ejecuta GRASP sin logs
     */
    private static Solucion ejecutarGraspSilencioso(DatosMoraPack datos) {
        // Silenciar output
        java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
            public void write(int b) {}
        }));

        try {
            GraspMoraPack grasp = new GraspMoraPack(datos.getPedidos(), datos.getVuelos());

            // Ejecutar múltiples iteraciones para encontrar la mejor
            Solucion mejorSolucion = null;
            double mejorFitness = Double.NEGATIVE_INFINITY;
            double[] alphas = {0.0, 0.3, 0.5, 0.7, 1.0};

            for (int i = 0; i < 10; i++) {
                double alfa = alphas[i % alphas.length];
                grasp.setAlfa(alfa);
                Solucion solucion = grasp.generarSolucion();

                if (solucion != null && solucion.getFitness() > mejorFitness) {
                    mejorFitness = solucion.getFitness();
                    mejorSolucion = solucion;
                }
            }

            return mejorSolucion;
        } finally {
            System.setOut(originalOut);
        }
    }

    /**
     * Ejecuta HÍBRIDO sin logs
     */
    private static Solucion ejecutarHibridoSilencioso(DatosMoraPack datos) {
        // Silenciar output
        java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
            public void write(int b) {}
        }));

        try {
            GraspGeneticHybrid hibrido = new GraspGeneticHybrid(datos.getPedidos(), datos.getVuelos());
            hibrido.setIteracionesGrasp(5);
            hibrido.setPorcentajePoblacionGrasp(0.3);
            hibrido.configurarParametrosGA(20, 50, 0.15, 0.8);

            return hibrido.ejecutarHibrido();
        } finally {
            System.setOut(originalOut);
        }
    }

    /**
     * Muestra comparación detallada y limpia
     */
    private static void mostrarComparacionDetallada(Solucion grasp, Solucion hibrido, int totalPedidos) {
        System.out.println("\n🏆 RESULTADOS DE LA COMPARACIÓN");
        System.out.println("=".repeat(60));

        // ==========================================
        // TABLA COMPARATIVA
        // ==========================================
        System.out.printf("%-15s | %-10s | %-12s | %-12s%n", "ALGORITMO", "FITNESS", "PEDIDOS", "COBERTURA");
        System.out.println("-".repeat(55));

        if (grasp != null) {
            int pedidosGrasp = grasp.getSolucionLogistica().getAsignacionPedidos().size();
            double coberturaGrasp = (double) pedidosGrasp / totalPedidos * 100;
            System.out.printf("%-15s | %-10.2f | %-12d | %-12.1f%%%n",
                    "GRASP", grasp.getFitness(), pedidosGrasp, coberturaGrasp);
        }

        if (hibrido != null) {
            int pedidosHibrido = hibrido.getSolucionLogistica().getAsignacionPedidos().size();
            double coberturaHibrido = (double) pedidosHibrido / totalPedidos * 100;
            System.out.printf("%-15s | %-10.2f | %-12d | %-12.1f%%%n",
                    "HÍBRIDO", hibrido.getFitness(), pedidosHibrido, coberturaHibrido);
        }

        // ==========================================
        // GANADOR
        // ==========================================
        determinarGanador(grasp, hibrido);

        // ==========================================
        // ANÁLISIS DE COMPONENTES DE FITNESS
        // ==========================================
        mostrarAnalisisComponentes(grasp, hibrido);

        // ==========================================
        // MUESTRA DE RUTAS
        // ==========================================
        mostrarMuestraRutas(grasp, hibrido);
    }

    /**
     * Determina y muestra el ganador
     */
    private static void determinarGanador(Solucion grasp, Solucion hibrido) {
        System.out.println("-".repeat(55));

        if (grasp != null && hibrido != null) {
            if (hibrido.getFitness() > grasp.getFitness()) {
                double mejora = hibrido.getFitness() - grasp.getFitness();
                System.out.printf("🏆 GANADOR: HÍBRIDO (+%.2f puntos de mejora)%n", mejora);
            } else if (grasp.getFitness() > hibrido.getFitness()) {
                double mejora = grasp.getFitness() - hibrido.getFitness();
                System.out.printf("🏆 GANADOR: GRASP (+%.2f puntos de mejora)%n", mejora);
            } else {
                System.out.println("🤝 EMPATE");
            }
        }
    }

    /**
     * Muestra análisis detallado de componentes de fitness
     */
    private static void mostrarAnalisisComponentes(Solucion grasp, Solucion hibrido) {
        System.out.println("\n🔬 ANÁLISIS DE COMPONENTES DE FITNESS:");
        System.out.println("-".repeat(60));

        if (grasp != null) {
            System.out.println("\n📊 GRASP - COMPONENTES:");
            mostrarComponentesIndividuales(grasp, "GRASP");
        }

        if (hibrido != null) {
            System.out.println("\n📊 HÍBRIDO - COMPONENTES:");
            mostrarComponentesIndividuales(hibrido, "HÍBRIDO");
        }
    }

    /**
     * Muestra componentes individuales de una solución
     */
    private static void mostrarComponentesIndividuales(Solucion solucion, String nombre) {
        String reporte = solucion.obtenerReporteFitness();
        String[] lineas = reporte.split("\n");

        System.out.printf("   FITNESS TOTAL: %.2f%n", solucion.getFitness());

        for (String linea : lineas) {
            if (linea.contains("PRIORIDAD #") || linea.contains("PENALIZACIÓN")) {
                System.out.println("   " + linea.trim());
            } else if (linea.contains("Cobertura total:") ||
                    linea.contains("Pedidos a tiempo:") ||
                    linea.contains("Vuelos utilizados:")) {
                System.out.println("   " + linea.trim());
            }
        }
    }

    /**
     * Muestra muestra representativa de rutas
     */
    private static void mostrarMuestraRutas(Solucion grasp, Solucion hibrido) {
        System.out.println("\n🗺️ MUESTRA DE RUTAS GENERADAS:");
        System.out.println("=".repeat(60));

        if (grasp != null) {
            System.out.println("\n🔵 RUTAS DE GRASP (Primeras 10):");
            mostrarRutasLimitadas(grasp, 10);
        }

        if (hibrido != null) {
            System.out.println("\n🟡 RUTAS DE HÍBRIDO (Primeras 10):");
            mostrarRutasLimitadas(hibrido, 10);
        }
    }

    /**
     * Muestra un número limitado de rutas
     */
    private static void mostrarRutasLimitadas(Solucion solucion, int limite) {
        Map<Pedido, RutaPedido> rutas = solucion.getSolucionLogistica().getAsignacionPedidos();

        if (rutas.isEmpty()) {
            System.out.println("   Sin rutas asignadas");
            return;
        }

        System.out.printf("%-8s | %-25s | %-8s | %-15s%n", "PEDIDO", "RUTA", "VUELOS", "DESTINO");
        System.out.println("-".repeat(65));

        rutas.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Pedido::getId)))
                .limit(limite)
                .forEach(entry -> {
                    Pedido pedido = entry.getKey();
                    RutaPedido ruta = entry.getValue();

                    String rutaStr = construirStringRuta(ruta);
                    String destino = pedido.getLugarDestino().getCodigo();
                    int numVuelos = ruta.getSecuenciaVuelos().size();

                    System.out.printf("%-8s | %-25s | %-8d | %-15s%n",
                            pedido.getId(),
                            truncar(rutaStr, 25),
                            numVuelos,
                            destino);
                });

        if (rutas.size() > limite) {
            System.out.printf("   ... y %d rutas más%n", rutas.size() - limite);
        }
    }

    /**
     * Construye string de ruta
     */
    private static String construirStringRuta(RutaPedido ruta) {
        if (ruta.getSecuenciaVuelos().isEmpty()) {
            return "Sin vuelos";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ruta.getSecuenciaVuelos().size(); i++) {
            if (i > 0) sb.append("→");
            sb.append(ruta.getSecuenciaVuelos().get(i).getOrigen().getCodigo());
        }
        sb.append("→").append(ruta.getSecuenciaVuelos().get(ruta.getSecuenciaVuelos().size() - 1)
                .getDestino().getCodigo());

        return sb.toString();
    }

    /**
     * Trunca texto para mostrar en tabla
     */
    private static String truncar(String texto, int maxLongitud) {
        if (texto.length() <= maxLongitud) {
            return texto;
        }
        return texto.substring(0, maxLongitud - 3) + "...";
    }

    /**
     * Muestra instrucciones si hay error
     */
    private static void mostrarInstrucciones() {
        System.out.println("\n📝 INSTRUCCIONES:");
        System.out.println("1. Verifica que existe la carpeta 'data' en tu proyecto");
        System.out.println("2. Asegúrate de tener los archivos:");
        System.out.println("   - data/aeropuertos.csv");
        System.out.println("   - data/vuelos.csv");
        System.out.println("   - data/pedidos.csv");
    }
}