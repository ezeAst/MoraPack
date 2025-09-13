import com.morapack.models.*;
import com.morapack.utils.CSVDataLoader;
import com.morapack.utils.CSVDataLoader.DatosMoraPack;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * Prueba del sistema con datos masivos cargados desde CSV
 * Maneja 100+ aeropuertos, 150+ vuelos, 150+ pedidos
 */
public class TestMassiveData {

    public static void main(String[] args) {
        System.out.println("🚀 SISTEMA MORAPACK - PRUEBA CON DATOS MASIVOS");
        System.out.println("=".repeat(60));

        // Rutas de los archivos CSV (ajusta según tu ubicación)
        String rutaAeropuertos = "data/aeropuertos.csv";
        String rutaVuelos = "data/vuelos.csv";
        String rutaPedidos = "data/pedidos.csv";

        try {
            // Cargar datos masivos
            DatosMoraPack datos = CSVDataLoader.cargarDatosCompletos(
                    rutaAeropuertos, rutaVuelos, rutaPedidos
            );

            // Mostrar resumen
            System.out.println("\n" + datos.getResumenEstadisticas());

            // Mostrar estadísticas detalladas
            CSVDataLoader.mostrarEstadisticas(datos);

            // Ejecutar algoritmos con datos masivos
            ejecutarAlgoritmosConDatosMasivos(datos);

        } catch (Exception e) {
            System.err.printf("❌ Error en la ejecución: %s%n", e.getMessage());
            System.out.println("\n📝 INSTRUCCIONES:");
            System.out.println("1. Crea una carpeta 'data' en tu proyecto");
            System.out.println("2. Guarda los 3 archivos CSV en esa carpeta:");
            System.out.println("   - aeropuertos.csv");
            System.out.println("   - vuelos.csv");
            System.out.println("   - pedidos.csv");
            System.out.println("3. Ajusta las rutas en el código si es necesario");
        }
    }

    /**
     * Ejecuta los algoritmos con el dataset masivo
     */
    private static void ejecutarAlgoritmosConDatosMasivos(DatosMoraPack datos) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("EJECUTANDO ALGORITMOS CON DATOS MASIVOS");
        System.out.println("=".repeat(60));

        // Configurar algoritmos para datos masivos
        long inicioTotal = System.currentTimeMillis();

        // GRASP con datos masivos
        System.out.println("\n🔵 GRASP - DATOS MASIVOS:");
        long inicioGrasp = System.currentTimeMillis();

        GraspMoraPack graspMasivo = new GraspMoraPack(datos.getPedidos(), datos.getVuelos());
        Solucion solucionGraspMasiva = ejecutarGraspMasivo(graspMasivo, 5); // Menos iteraciones por tiempo

        long tiempoGrasp = System.currentTimeMillis() - inicioGrasp;

        // GA con datos masivos
        System.out.println("\n🟢 ALGORITMO GENÉTICO - DATOS MASIVOS:");
        long inicioGA = System.currentTimeMillis();

        GeneticAlgorithmMoraPack gaMasivo = new GeneticAlgorithmMoraPack(datos.getPedidos(), datos.getVuelos());
        gaMasivo.setTamañoPoblacion(20); // Población menor para datos masivos
        gaMasivo.setNumeroGeneraciones(30); // Menos generaciones
        gaMasivo.setTasaMutacion(0.20); // Mayor mutación para explorar
        gaMasivo.setTasaCruzamiento(0.80);

        Solucion solucionGAMasiva = gaMasivo.ejecutar();

        long tiempoGA = System.currentTimeMillis() - inicioGA;

        // Híbrido con datos masivos
        System.out.println("\n🟡 HÍBRIDO - DATOS MASIVOS:");
        long inicioHibrido = System.currentTimeMillis();

        GraspGeneticHybrid hibridoMasivo = new GraspGeneticHybrid(datos.getPedidos(), datos.getVuelos());
        hibridoMasivo.setIteracionesGrasp(5);
        hibridoMasivo.setPorcentajePoblacionGrasp(0.3);
        hibridoMasivo.configurarParametrosGA(20, 30, 0.20, 0.80);

        Solucion solucionHibridaMasiva = hibridoMasivo.ejecutarHibrido();

        long tiempoHibrido = System.currentTimeMillis() - inicioHibrido;
        long tiempoTotal = System.currentTimeMillis() - inicioTotal;

        // Comparación de resultados masivos
        System.out.println("\n" + "=".repeat(60));
        System.out.println("RESULTADOS CON DATOS MASIVOS");
        System.out.println("=".repeat(60));

        mostrarComparacionMasiva(solucionGraspMasiva, solucionGAMasiva, solucionHibridaMasiva,
                tiempoGrasp, tiempoGA, tiempoHibrido, datos.getTotalPedidos());

        System.out.printf("\n⏱️ TIEMPO TOTAL DE EXPERIMENTACIÓN: %.2f segundos%n", tiempoTotal / 1000.0);

        // Análisis de escalabilidad
        analizarEscalabilidad(datos, tiempoTotal);
    }

    /**
     * Ejecuta GRASP optimizado para datos masivos
     */
    private static Solucion ejecutarGraspMasivo(GraspMoraPack grasp, int iteraciones) {
        double[] alphas = {0.0, 0.3, 0.7, 1.0}; // Menos valores para optimizar tiempo
        Solucion mejorSolucion = null;
        double mejorFitness = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < iteraciones; i++) {
            double alfa = alphas[i % alphas.length];
            grasp.setAlfa(alfa);

            Solucion solucion = grasp.generarSolucion();

            if (solucion != null && solucion.getFitness() > mejorFitness) {
                mejorFitness = solucion.getFitness();
                mejorSolucion = solucion;
                System.out.printf("  ⚡ Iteración %d (α=%.1f): %.2f%n", i+1, alfa, mejorFitness);
            }
        }

        System.out.printf("GRASP masivo completado: %.2f%n", mejorFitness);
        return mejorSolucion;
    }

    /**
     * Muestra comparación optimizada para datos masivos
     */
    private static void mostrarComparacionMasiva(Solucion grasp, Solucion ga, Solucion hibrido,
                                                 long tiempoGrasp, long tiempoGA, long tiempoHibrido,
                                                 int totalPedidos) {

        System.out.printf("%-15s | %-12s | %-12s | %-12s | %-12s%n",
                "ALGORITMO", "FITNESS", "PEDIDOS", "COBERTURA", "TIEMPO (s)");
        System.out.println("-".repeat(75));

        if (grasp != null) {
            int pedidosGrasp = grasp.getSolucionLogistica().getAsignacionPedidos().size();
            double coberturaGrasp = (double) pedidosGrasp / totalPedidos * 100;
            System.out.printf("%-15s | %-12.2f | %-12d | %-12.1f%% | %-12.2f%n",
                    "GRASP", grasp.getFitness(), pedidosGrasp, coberturaGrasp, tiempoGrasp/1000.0);
        }

        if (ga != null) {
            int pedidosGA = ga.getSolucionLogistica().getAsignacionPedidos().size();
            double coberturaGA = (double) pedidosGA / totalPedidos * 100;
            System.out.printf("%-15s | %-12.2f | %-12d | %-12.1f%% | %-12.2f%n",
                    "GA", ga.getFitness(), pedidosGA, coberturaGA, tiempoGA/1000.0);
        }

        if (hibrido != null) {
            int pedidosHibrido = hibrido.getSolucionLogistica().getAsignacionPedidos().size();
            double coberturaHibrido = (double) pedidosHibrido / totalPedidos * 100;
            System.out.printf("%-15s | %-12.2f | %-12d | %-12.1f%% | %-12.2f%n",
                    "HÍBRIDO", hibrido.getFitness(), pedidosHibrido, coberturaHibrido, tiempoHibrido/1000.0);
        }

        // Determinar ganador en datos masivos
        Solucion mejor = determinarMejor(grasp, ga, hibrido);
        if (mejor != null) {
            String nombreMejor = (mejor == grasp) ? "GRASP" :
                    (mejor == ga) ? "GA" : "HÍBRIDO";
            System.out.printf("\n🏆 GANADOR EN DATOS MASIVOS: %s (%.2f fitness)%n",
                    nombreMejor, mejor.getFitness());
        }
    }

    /**
     * Analiza la escalabilidad del sistema
     */
    private static void analizarEscalabilidad(DatosMoraPack datos, long tiempoTotal) {
        System.out.println("\n📊 ANÁLISIS DE ESCALABILIDAD:");
        System.out.println("-".repeat(40));

        int totalElementos = datos.getTotalAeropuertos() + datos.getTotalVuelos() + datos.getTotalPedidos();
        double tiempoPorElemento = (tiempoTotal / 1000.0) / totalElementos;

        System.out.printf("• Total elementos procesados: %d%n", totalElementos);
        System.out.printf("• Tiempo por elemento: %.4f segundos%n", tiempoPorElemento);
        System.out.printf("• Throughput: %.0f elementos/segundo%n", 1.0 / tiempoPorElemento);

        // Proyecciones
        System.out.println("\n🔮 PROYECCIONES PARA DATASETS MAYORES:");
        int[] tamaños = {500, 1000, 2000, 5000};
        for (int tamaño : tamaños) {
            double tiempoEstimado = tamaño * tiempoPorElemento;
            System.out.printf("• %d pedidos: ~%.1f segundos (%.1f minutos)%n",
                    tamaño, tiempoEstimado, tiempoEstimado / 60);
        }

        // Recomendaciones de optimización
        System.out.println("\n💡 RECOMENDACIONES:");
        if (tiempoPorElemento > 0.1) {
            System.out.println("• Considerar paralelización para datasets >1000 elementos");
            System.out.println("• Implementar índices para búsquedas más rápidas");
        }
        if (datos.getTotalPedidos() > 100) {
            System.out.println("• Usar menos iteraciones GRASP para datasets masivos");
            System.out.println("• Reducir población GA para mantener tiempos razonables");
        }
    }

    /**
     * Determina la mejor solución entre las tres
     */
    private static Solucion determinarMejor(Solucion grasp, Solucion ga, Solucion hibrido) {
        return Arrays.asList(grasp, ga, hibrido).stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparingDouble(Solucion::getFitness))
                .orElse(null);
    }
}