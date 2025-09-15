import com.morapack.models.*;
import com.morapack.utils.CSVDataLoader;
import com.morapack.utils.CSVDataLoader.DatosMoraPack;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main para visualizar la distribución de vuelos por días
 * Muestra cómo se duplican las plantillas diarias
 */
public class VisualizarVuelosPorDias {

    public static void main(String[] args) {
        System.out.println("🔍 VISUALIZADOR DE VUELOS POR DÍAS");
        System.out.println("=".repeat(60));

        // Rutas de archivos
        String rutaAeropuertos = "data/aeropuertos.csv";
        String rutaVuelos = "data/vuelos.txt";
        String rutaPedidos = "data/pedidos.csv";

        try {
            // Cargar datos con duplicación dinámica
            DatosMoraPack datos = CSVDataLoader.cargarDatosCompletos(
                    rutaAeropuertos, rutaVuelos, rutaPedidos
            );

            // Analizar distribución por días
            analizarDistribucionPorDias(datos.getVuelos());

            // Mostrar ejemplos específicos
            mostrarEjemplosVuelosEspecificos(datos.getVuelos());

            // Analizar rutas únicas
            analizarRutasUnicas(datos.getVuelos());

            // Verificar consistencia temporal
            verificarConsistenciaTemporal(datos.getVuelos());

        } catch (Exception e) {
            System.err.printf("❌ Error: %s%n", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Analiza cuántos vuelos hay por cada día
     */
    private static void analizarDistribucionPorDias(List<Vuelo> vuelos) {
        System.out.println("\n📅 DISTRIBUCIÓN DE VUELOS POR DÍA:");
        System.out.println("-".repeat(50));

        // Agrupar vuelos por fecha
        Map<LocalDate, List<Vuelo>> vuelosPorFecha = vuelos.stream()
                .collect(Collectors.groupingBy(v -> v.getHoraSalida().toLocalDate()));

        // Mostrar estadísticas por día
        vuelosPorFecha.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    LocalDate fecha = entry.getKey();
                    List<Vuelo> vuelosDelDia = entry.getValue();

                    System.out.printf("📆 %s (%s): %,d vuelos%n",
                            fecha,
                            fecha.getDayOfWeek().name(),
                            vuelosDelDia.size()
                    );
                });

        // Resumen general
        System.out.println("-".repeat(50));
        System.out.printf("📊 RESUMEN: %,d vuelos distribuidos en %d días%n",
                vuelos.size(), vuelosPorFecha.size());

        if (!vuelosPorFecha.isEmpty()) {
            int vuelosPorDiaPromedio = vuelos.size() / vuelosPorFecha.size();
            System.out.printf("📈 Promedio: %,d vuelos por día%n", vuelosPorDiaPromedio);
        }
    }

    /**
     * Muestra ejemplos de vuelos específicos para ver la duplicación
     */
    private static void mostrarEjemplosVuelosEspecificos(List<Vuelo> vuelos) {
        System.out.println("\n🔍 EJEMPLOS DE DUPLICACIÓN DE VUELOS:");
        System.out.println("-".repeat(70));

        // Tomar una ruta específica como ejemplo
        String rutaEjemplo = null;
        Map<String, List<Vuelo>> vuelosPorRuta = new HashMap<>();

        // Agrupar por ruta (origen-destino-hora)
        for (Vuelo vuelo : vuelos) {
            String claveRuta = String.format("%s-%s-%02d:%02d",
                    vuelo.getOrigen().getCodigo(),
                    vuelo.getDestino().getCodigo(),
                    vuelo.getHoraSalida().getHour(),
                    vuelo.getHoraSalida().getMinute()
            );

            vuelosPorRuta.computeIfAbsent(claveRuta, k -> new ArrayList<>()).add(vuelo);

            if (rutaEjemplo == null) {
                rutaEjemplo = claveRuta;
            }
        }

        // Mostrar ejemplo de duplicación
        if (rutaEjemplo != null && vuelosPorRuta.containsKey(rutaEjemplo)) {
            List<Vuelo> vuelosEjemplo = vuelosPorRuta.get(rutaEjemplo);

            System.out.printf("🛫 EJEMPLO - Ruta: %s%n", rutaEjemplo);
            System.out.printf("   Duplicaciones encontradas: %d%n", vuelosEjemplo.size());
            System.out.println("   Fechas y horarios:");

            vuelosEjemplo.stream()
                    .sorted(Comparator.comparing(Vuelo::getHoraSalida))
                    .forEach(vuelo -> {
                        System.out.printf("     • %s | %s → %s | Cap: %d%n",
                                vuelo.getHoraSalida().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                                vuelo.getOrigen().getCodigo(),
                                vuelo.getDestino().getCodigo(),
                                vuelo.getCapacidadMaxima()
                        );
                    });
        }

        // Mostrar estadísticas de duplicación
        System.out.println("\n📊 ESTADÍSTICAS DE DUPLICACIÓN:");
        Map<Integer, Long> distribucionDuplicaciones = vuelosPorRuta.values().stream()
                .collect(Collectors.groupingBy(List::size, Collectors.counting()));

        distribucionDuplicaciones.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    System.out.printf("   %d días: %,d rutas%n", entry.getKey(), entry.getValue());
                });
    }

    /**
     * Analiza las rutas únicas disponibles
     */
    private static void analizarRutasUnicas(List<Vuelo> vuelos) {
        System.out.println("\n🗺️ ANÁLISIS DE RUTAS ÚNICAS:");
        System.out.println("-".repeat(50));

        // Agrupar por ruta origen-destino (sin considerar hora)
        Map<String, List<Vuelo>> rutasUnicas = vuelos.stream()
                .collect(Collectors.groupingBy(v ->
                        v.getOrigen().getCodigo() + " → " + v.getDestino().getCodigo()
                ));

        System.out.printf("🛣️ Total de rutas únicas: %,d%n", rutasUnicas.size());

        // Mostrar las 10 rutas con más vuelos
        System.out.println("\n🔝 TOP 10 RUTAS CON MÁS VUELOS:");
        rutasUnicas.entrySet().stream()
                .sorted(Map.Entry.<String, List<Vuelo>>comparingByValue(
                        Comparator.comparingInt(List::size)).reversed())
                .limit(10)
                .forEach(entry -> {
                    System.out.printf("   %-20s: %,d vuelos%n",
                            entry.getKey(), entry.getValue().size());
                });

        // Analizar rutas desde fábricas
        System.out.println("\n🏭 RUTAS DESDE FÁBRICAS:");
        List<String> fabricas = Arrays.asList("SPIM","EBCI","UBBB");

        for (String fabrica : fabricas) {
            long vuelosDesdefabrica = vuelos.stream()
                    .filter(v -> v.getOrigen().getCodigo().equals(fabrica))
                    .count();

            Set<String> destinosDesdefabrica = vuelos.stream()
                    .filter(v -> v.getOrigen().getCodigo().equals(fabrica))
                    .map(v -> v.getDestino().getCodigo())
                    .collect(Collectors.toSet());

            System.out.printf("   %s: %,d vuelos → %d destinos únicos%n",
                    fabrica, vuelosDesdefabrica, destinosDesdefabrica.size());
        }
    }

    /**
     * Verifica que todos los vuelos tengan fechas consistentes
     */
    private static void verificarConsistenciaTemporal(List<Vuelo> vuelos) {
        System.out.println("\n⏰ VERIFICACIÓN DE CONSISTENCIA TEMPORAL:");
        System.out.println("-".repeat(50));

        // Verificar rango de fechas
        LocalDate fechaMinima = vuelos.stream()
                .map(v -> v.getHoraSalida().toLocalDate())
                .min(LocalDate::compareTo)
                .orElse(null);

        LocalDate fechaMaxima = vuelos.stream()
                .map(v -> v.getHoraSalida().toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(null);

        if (fechaMinima != null && fechaMaxima != null) {
            System.out.printf("📅 Rango de fechas: %s a %s%n", fechaMinima, fechaMaxima);
            System.out.printf("📆 Días cubiertos: %d días%n",
                    fechaMaxima.toEpochDay() - fechaMinima.toEpochDay() + 1);
        }

        // Verificar que no hay vuelos en el pasado
        LocalDate hoy = LocalDate.now();
        long vuelosEnPasado = vuelos.stream()
                .filter(v -> v.getHoraSalida().toLocalDate().isBefore(hoy))
                .count();

        if (vuelosEnPasado > 0) {
            System.out.printf("⚠️ ADVERTENCIA: %,d vuelos están en el pasado%n", vuelosEnPasado);
        } else {
            System.out.println("✅ Todos los vuelos están en fechas futuras");
        }

        // Verificar vuelos por hora del día
        System.out.println("\n🕐 DISTRIBUCIÓN POR HORA DEL DÍA:");
        Map<Integer, Long> vuelosPorHora = vuelos.stream()
                .collect(Collectors.groupingBy(
                        v -> v.getHoraSalida().getHour(),
                        Collectors.counting()
                ));

        // Mostrar distribución en formato compacto
        for (int hora = 0; hora < 24; hora += 6) {
            System.out.printf("   %02d:00-%02d:59: ", hora, hora + 5);
            long vuelosEnBloque = 0;
            for (int h = hora; h < hora + 6 && h < 24; h++) {
                vuelosEnBloque += vuelosPorHora.getOrDefault(h, 0L);
            }
            System.out.printf("%,d vuelos%n", vuelosEnBloque);
        }
    }
}