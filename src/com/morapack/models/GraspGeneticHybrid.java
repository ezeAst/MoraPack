package com.morapack.models;
import com.morapack.utils.Profiler;
import java.util.*;

/**
 * Algoritmo híbrido GRASP + Algoritmo Genético para MoraPack
 * Combina la construcción heurística de GRASP con la mejora evolutiva del GA
 */
public class GraspGeneticHybrid {

    private List<Pedido> pedidos;
    private List<Vuelo> vuelos;
    private GraspMoraPack grasp;
    private GeneticAlgorithmMoraPack geneticAlgorithm;

    // Parámetros del híbrido
    private int iteracionesGrasp;
    private boolean usarMejorGraspComoSemilla;
    private double porcentajePoblacionGrasp;

    // Resultados
    private List<Solucion> mejoresSolucionesGrasp;
    private Solucion mejorSolucionHibrida;
    private List<Double> historialConvergencia;

    public GraspGeneticHybrid(List<Pedido> pedidos, List<Vuelo> vuelos) {
        this.pedidos = new ArrayList<>(pedidos);
        this.vuelos = new ArrayList<>(vuelos);
        this.grasp = new GraspMoraPack(pedidos, vuelos);
        this.geneticAlgorithm = new GeneticAlgorithmMoraPack(pedidos, vuelos);

        // Parámetros por defecto
        this.iteracionesGrasp = 20;
        this.usarMejorGraspComoSemilla = true;
        this.porcentajePoblacionGrasp = 0.4; // 40% de la población inicial viene de GRASP

        this.mejoresSolucionesGrasp = new ArrayList<>();
        this.historialConvergencia = new ArrayList<>();
    }

    /**
     * Ejecuta el algoritmo híbrido completo
     */
    public Solucion ejecutarHibrido() {
        Profiler.reset();
        Profiler.iniciar("TOTAL_HIBRIDO");

        // Fase 1: GRASP
        Profiler.iniciar("FASE_GRASP");
        ejecutarFaseGrasp();
        Profiler.finalizar("FASE_GRASP");

        // Fase 2: Configurar GA
        Profiler.iniciar("CONFIGURAR_GA");
        configurarGeneticoConGrasp();
        Profiler.finalizar("CONFIGURAR_GA");

        // Fase 3: Ejecutar GA
        Profiler.iniciar("FASE_GA");
        Solucion mejorGA = geneticAlgorithm.ejecutar();
        Profiler.finalizar("FASE_GA");

        // Fase 4: Análisis
        Profiler.iniciar("ANALISIS_RESULTADOS");
        analizarResultados(mejorGA);
        Profiler.finalizar("ANALISIS_RESULTADOS");

        Profiler.finalizar("TOTAL_HIBRIDO");
        Profiler.mostrarReporte();

        return mejorSolucionHibrida;
    }

    /**
     * Fase 1: Ejecuta múltiples iteraciones de GRASP con diferentes parámetros
     */
    private void ejecutarFaseGrasp() {
        double[] valoresAlfa = {0.0, 0.2, 0.4, 0.6, 0.8, 1.0};
        Solucion mejorGrasp = null;
        double mejorFitnessGrasp = Double.NEGATIVE_INFINITY;

        // System.out.printf("Ejecutando %d iteraciones de GRASP...%n", iteracionesGrasp);
        long inicioGA = System.currentTimeMillis();

        for (int iteracion = 0; iteracion < iteracionesGrasp; iteracion++) {
            // Alternar entre diferentes valores de alfa
            double alfa = valoresAlfa[iteracion % valoresAlfa.length];

            // Agregar algo de aleatoriedad al alfa
            if (iteracion >= valoresAlfa.length) {
                alfa += (Math.random() - 0.5) * 0.3; // ±15% de variación
                alfa = Math.max(0.0, Math.min(1.0, alfa));
            }

            grasp.setAlfa(alfa);
            Solucion solucion = grasp.generarSolucion();

            if (solucion != null) {
                mejoresSolucionesGrasp.add(solucion);
                historialConvergencia.add(solucion.getFitness());

                if (solucion.getFitness() > mejorFitnessGrasp) {
                    mejorFitnessGrasp = solucion.getFitness();
                    mejorGrasp = solucion;
                }

                // if ((iteracion + 1) % 5 == 0) {
                //     System.out.printf("Iteración %d/%d - Alfa: %.2f - Fitness: %.2f - Mejor hasta ahora: %.2f%n",
                //             iteracion + 1, iteracionesGrasp, alfa, solucion.getFitness(), mejorFitnessGrasp);
                // }
            }
        }

        long tiempoGA = System.currentTimeMillis() - inicioGA;
        System.out.printf("✓ grasp completado en %.2f segundos%n%n", tiempoGA / 1000.0);

        // System.out.printf("GRASP completado. Mejor fitness: %.2f%n", mejorFitnessGrasp);
        // System.out.printf("Soluciones válidas generadas: %d/%d%n", mejoresSolucionesGrasp.size(), iteracionesGrasp);

        // if (mejorGrasp != null) {
        //     System.out.println("\n" + mejorGrasp.obtenerReporteFitness());
        // }
    }

    /**
     * Configura el algoritmo genético para usar soluciones de GRASP en la población inicial
     */
    private void configurarGeneticoConGrasp() {
        if (usarMejorGraspComoSemilla && !mejoresSolucionesGrasp.isEmpty()) {
            // Ordenar soluciones de GRASP por fitness
            mejoresSolucionesGrasp.sort((a, b) -> Double.compare(b.getFitness(), a.getFitness()));

            // IMPORTANTE: Actualizar el total de pedidos en todas las soluciones de GRASP
            for (Solucion solucion : mejoresSolucionesGrasp) {
                solucion.setTotalPedidosProblema(pedidos.size());
            }

            // Seleccionar las mejores para sembrar el GA
            int numSemillas = Math.min(mejoresSolucionesGrasp.size(),
                    (int)(geneticAlgorithm.getTamañoPoblacion() * porcentajePoblacionGrasp));

            // System.out.printf("Usando %d mejores soluciones de GRASP como semillas (%.1f%% de la población)%n",
            //         numSemillas, porcentajePoblacionGrasp * 100);

            // Configurar el GA para usar estas semillas
            geneticAlgorithm.setSemillasIniciales(mejoresSolucionesGrasp.subList(0, numSemillas));
        }

        // Ajustar parámetros del GA para el contexto híbrido
        geneticAlgorithm.setNumeroGeneraciones(50); // Más generaciones para mejor convergencia
        geneticAlgorithm.setTasaMutacion(0.12); // Mutación ligeramente menor con buenas semillas
        geneticAlgorithm.setTasaCruzamiento(0.85); // Mayor cruzamiento para explorar combinaciones
    }

    /**
     * Analiza y compara los resultados de ambas fases
     */
    private void analizarResultados(Solucion mejorGA) {
        Solucion mejorGrasp = mejoresSolucionesGrasp.stream()
                .max(Comparator.comparingDouble(Solucion::getFitness))
                .orElse(null);

        // System.out.println("=== ANÁLISIS COMPARATIVO DE RESULTADOS ===");

        // if (mejorGrasp != null) {
        //     System.out.printf("Mejor solución GRASP: %.2f%n", mejorGrasp.getFitness());
        // }

        // if (mejorGA != null) {
        //     System.out.printf("Mejor solución GA: %.2f%n", mejorGA.getFitness());
        // }

        // Determinar la mejor solución híbrida
        if (mejorGrasp == null) {
            mejorSolucionHibrida = mejorGA;
        } else if (mejorGA == null) {
            mejorSolucionHibrida = mejorGrasp;
        } else {
            mejorSolucionHibrida = (mejorGA.getFitness() > mejorGrasp.getFitness()) ? mejorGA : mejorGrasp;
        }

        if (mejorSolucionHibrida != null) {
            double mejora = calcularMejora(mejorGrasp, mejorGA);

            // System.out.printf("Mejor solución HÍBRIDA: %.2f%n", mejorSolucionHibrida.getFitness());

            // if (mejora > 0) {
            //     System.out.printf("✓ GA mejoró GRASP en %.2f puntos (%.2f%% de mejora)%n",
            //             mejora, (mejora / mejorGrasp.getFitness()) * 100);
            // } else if (mejora < 0) {
            //     System.out.printf("- GA no superó a GRASP (diferencia: %.2f)%n", mejora);
            // } else {
            //     System.out.println("= GA alcanzó el mismo resultado que GRASP");
            // }

            // Mostrar estadísticas detalladas
            mostrarEstadisticasDetalladas();

            // Reporte final de la mejor solución
            // System.out.println("\n=== REPORTE DE LA MEJOR SOLUCIÓN ===");
            // System.out.println(mejorSolucionHibrida.obtenerReporteFitness());
        }
    }

    /**
     * Calcula la mejora del GA sobre GRASP
     */
    private double calcularMejora(Solucion mejorGrasp, Solucion mejorGA) {
        if (mejorGrasp == null || mejorGA == null) return 0.0;
        return mejorGA.getFitness() - mejorGrasp.getFitness();
    }

    /**
     * Muestra estadísticas detalladas del proceso híbrido
     */
    private void mostrarEstadisticasDetalladas() {
        // System.out.println("\n=== ESTADÍSTICAS DETALLADAS ===");

        // Estadísticas de GRASP
        if (!mejoresSolucionesGrasp.isEmpty()) {
            double promedioGrasp = mejoresSolucionesGrasp.stream()
                    .mapToDouble(Solucion::getFitness)
                    .average().orElse(0.0);

            double desviacionGrasp = calcularDesviacionEstandar(
                    mejoresSolucionesGrasp.stream()
                            .mapToDouble(Solucion::getFitness)
                            .toArray()
            );

            // System.out.printf("GRASP - Promedio: %.2f, Desviación: %.2f%n", promedioGrasp, desviacionGrasp);
        }

        // Estadísticas de GA
        List<Double> historialGA = geneticAlgorithm.getHistorialFitness();
        if (!historialGA.isEmpty()) {
            double mejorInicialGA = historialGA.get(0);
            double mejorFinalGA = historialGA.get(historialGA.size() - 1);
            double mejoraGA = mejorFinalGA - mejorInicialGA;

            // System.out.printf("GA - Inicial: %.2f, Final: %.2f, Mejora interna: %.2f%n",
            //         mejorInicialGA, mejorFinalGA, mejoraGA);

            // Analizar convergencia
            int generacionConvergencia = analizarConvergencia(historialGA);
            // System.out.printf("GA - Convergencia aproximada en generación: %d%n", generacionConvergencia);
        }

        // Estadísticas de factibilidad
        analizarFactibilidad();
    }

    /**
     * Analiza la factibilidad de las soluciones generadas
     */
    private void analizarFactibilidad() {
        int solucionesFactiblesGrasp = 0;
        int solucionesTotalesGrasp = mejoresSolucionesGrasp.size();

        for (Solucion solucion : mejoresSolucionesGrasp) {
            if (solucion.esSolucionFactible()) {
                solucionesFactiblesGrasp++;
            }
        }

        double porcentajeFactibilidadGrasp = (solucionesTotalesGrasp > 0) ?
                (double) solucionesFactiblesGrasp / solucionesTotalesGrasp * 100 : 0;

        // System.out.printf("Factibilidad GRASP: %d/%d (%.1f%%)%n",
        //         solucionesFactiblesGrasp, solucionesTotalesGrasp, porcentajeFactibilidadGrasp);

        // if (mejorSolucionHibrida != null) {
        //     System.out.printf("Mejor solución híbrida es factible: %s%n",
        //             mejorSolucionHibrida.esSolucionFactible() ? "SÍ" : "NO");
        // }
    }

    /**
     * Analiza en qué generación aproximadamente convergió el GA
     */
    private int analizarConvergencia(List<Double> historial) {
        if (historial.size() < 10) return historial.size();

        double mejorFitness = historial.get(historial.size() - 1);
        double umbralConvergencia = mejorFitness * 0.98; // 98% del fitness final

        for (int i = 0; i < historial.size(); i++) {
            if (historial.get(i) >= umbralConvergencia) {
                return i;
            }
        }

        return historial.size();
    }

    /**
     * Calcula la desviación estándar de un array de valores
     */
    private double calcularDesviacionEstandar(double[] valores) {
        if (valores.length == 0) return 0.0;

        double promedio = Arrays.stream(valores).average().orElse(0.0);
        double sumaCuadrados = Arrays.stream(valores)
                .map(x -> Math.pow(x - promedio, 2))
                .sum();

        return Math.sqrt(sumaCuadrados / valores.length);
    }

    /**
     * Ejecuta solo la fase GRASP (útil para comparaciones)
     */
    public Solucion ejecutarSoloGrasp() {
        // System.out.println("=== EJECUTANDO SOLO GRASP ===");
        ejecutarFaseGrasp();

        return mejoresSolucionesGrasp.stream()
                .max(Comparator.comparingDouble(Solucion::getFitness))
                .orElse(null);
    }

    /**
     * Ejecuta solo el algoritmo genético (útil para comparaciones)
     */
    public Solucion ejecutarSoloGA() {
        // System.out.println("=== EJECUTANDO SOLO GA ===");
        return geneticAlgorithm.ejecutar();
    }

    /**
     * Genera un reporte completo del proceso híbrido
     */
    public String generarReporteCompleto() {
        StringBuilder reporte = new StringBuilder();
        reporte.append("=== REPORTE COMPLETO HÍBRIDO GRASP + GA ===\n\n");

        // Configuración
        reporte.append("CONFIGURACIÓN:\n");
        reporte.append(String.format("- Iteraciones GRASP: %d\n", iteracionesGrasp));
        reporte.append(String.format("- Población GA: %d\n", geneticAlgorithm.getTamañoPoblacion()));
        reporte.append(String.format("- Generaciones GA: %d\n", geneticAlgorithm.getNumeroGeneraciones()));
        reporte.append(String.format("- Semillas GRASP en GA: %.1f%%\n", porcentajePoblacionGrasp * 100));

        // Resultados
        if (!mejoresSolucionesGrasp.isEmpty()) {
            Solucion mejorGrasp = mejoresSolucionesGrasp.stream()
                    .max(Comparator.comparingDouble(Solucion::getFitness))
                    .orElse(null);

            reporte.append(String.format("\nMEJOR GRASP: %.2f\n", mejorGrasp.getFitness()));
        }

        if (geneticAlgorithm.getMejorSolucionGlobal() != null) {
            reporte.append(String.format("MEJOR GA: %.2f\n",
                    geneticAlgorithm.getMejorSolucionGlobal().getFitness()));
        }

        if (mejorSolucionHibrida != null) {
            reporte.append(String.format("MEJOR HÍBRIDA: %.2f\n", mejorSolucionHibrida.getFitness()));
            reporte.append("\n").append(mejorSolucionHibrida.obtenerReporteFitness());
        }

        return reporte.toString();
    }

    // Getters y setters
    public void setIteracionesGrasp(int iteracionesGrasp) {
        this.iteracionesGrasp = Math.max(1, iteracionesGrasp);
    }

    public void setUsarMejorGraspComoSemilla(boolean usar) {
        this.usarMejorGraspComoSemilla = usar;
    }

    public void setPorcentajePoblacionGrasp(double porcentaje) {
        this.porcentajePoblacionGrasp = Math.max(0.0, Math.min(1.0, porcentaje));
    }

    public void configurarParametrosGA(int poblacion, int generaciones, double mutacion, double cruzamiento) {
        geneticAlgorithm.setTamañoPoblacion(poblacion);
        geneticAlgorithm.setNumeroGeneraciones(generaciones);
        geneticAlgorithm.setTasaMutacion(mutacion);
        geneticAlgorithm.setTasaCruzamiento(cruzamiento);
    }

    public Solucion getMejorSolucionHibrida() {
        return mejorSolucionHibrida;
    }

    public List<Solucion> getMejoresSolucionesGrasp() {
        return new ArrayList<>(mejoresSolucionesGrasp);
    }

    public List<Double> getHistorialConvergencia() {
        return new ArrayList<>(historialConvergencia);
    }

    public GeneticAlgorithmMoraPack getGeneticAlgorithm() {
        return geneticAlgorithm;
    }
}