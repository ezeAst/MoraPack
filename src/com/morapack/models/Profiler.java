package com.morapack.utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Profiler {
    private static final Map<String, Long> tiempos = new ConcurrentHashMap<>();
    private static final Map<String, Integer> contadores = new ConcurrentHashMap<>();
    private static final Map<String, Long> tiempoInicio = new ConcurrentHashMap<>();

    public static void iniciar(String seccion) {
        tiempoInicio.put(seccion, System.currentTimeMillis());
    }

    public static void finalizar(String seccion) {
        Long inicio = tiempoInicio.remove(seccion);
        if (inicio != null) {
            long duracion = System.currentTimeMillis() - inicio;
            tiempos.merge(seccion, duracion, Long::sum);
            contadores.merge(seccion, 1, Integer::sum);
        }
    }

    public static void mostrarReporte() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("PROFILING - TIEMPOS DE EJECUCIÓN");
        System.out.println("=".repeat(80));

        List<Map.Entry<String, Long>> ordenado = new ArrayList<>(tiempos.entrySet());
        ordenado.sort(Map.Entry.<String, Long>comparingByValue().reversed());

        long tiempoTotal = tiempos.values().stream().mapToLong(Long::longValue).sum();

        System.out.printf("%-50s %10s %10s %10s %8s%n",
                "SECCION", "TIEMPO(s)", "LLAMADAS", "PROM(ms)", "%");
        System.out.println("-".repeat(80));

        for (Map.Entry<String, Long> entry : ordenado) {
            String seccion = entry.getKey();
            long tiempo = entry.getValue();
            int llamadas = contadores.get(seccion);
            double promedio = (double) tiempo / llamadas;
            double porcentaje = (double) tiempo / tiempoTotal * 100;

            System.out.printf("%-50s %10.2f %10d %10.2f %7.1f%%%n",
                    truncar(seccion, 50),
                    tiempo / 1000.0,
                    llamadas,
                    promedio,
                    porcentaje);
        }

        System.out.println("-".repeat(80));
        System.out.printf("TIEMPO TOTAL: %.2f segundos%n", tiempoTotal / 1000.0);
        System.out.println("=".repeat(80));
    }

    public static void reset() {
        tiempos.clear();
        contadores.clear();
        tiempoInicio.clear();
    }

    private static String truncar(String texto, int max) {
        return texto.length() <= max ? texto : texto.substring(0, max - 3) + "...";
    }
}