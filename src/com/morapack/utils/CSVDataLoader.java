package com.morapack.utils;

import com.morapack.models.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Cargador de datos masivos desde archivos CSV
 * Diseñado para manejar grandes volúmenes de datos con concordancia temporal
 */
public class CSVDataLoader {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Carga aeropuertos desde archivo CSV
     */
    public static List<Aeropuerto> cargarAeropuertos(String rutaArchivo) {
        List<Aeropuerto> aeropuertos = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(rutaArchivo))) {
            String linea;
            boolean primeraLinea = true;

            while ((linea = br.readLine()) != null) {
                if (primeraLinea) {
                    primeraLinea = false; // Saltar header
                    continue;
                }

                String[] campos = linea.split(",");
                if (campos.length >= 7) {
                    String codigo = campos[0].trim();
                    String nombre = campos[1].trim();
                    String pais = campos[2].trim();
                    int capacidad = Integer.parseInt(campos[3].trim());
                    int capacidadAct = Integer.parseInt(campos[4].trim());
                    int husoHorario = Integer.parseInt(campos[5].trim());
                    String continente = campos[6].trim();

                    aeropuertos.add(new Aeropuerto(codigo, nombre, pais, capacidad,
                            capacidadAct, husoHorario, continente));
                }
            }

            System.out.printf("✅ Cargados %d aeropuertos desde %s%n", aeropuertos.size(), rutaArchivo);

        } catch (IOException e) {
            System.err.printf("❌ Error al cargar aeropuertos: %s%n", e.getMessage());
        } catch (NumberFormatException e) {
            System.err.printf("❌ Error de formato en aeropuertos: %s%n", e.getMessage());
        }

        return aeropuertos;
    }

    /**
     * Carga vuelos desde archivo CSV
     */
    public static List<Vuelo> cargarVuelos(String rutaArchivo, Map<String, Aeropuerto> aeropuertoMap) {
        List<Vuelo> vuelos = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(rutaArchivo))) {
            String linea;
            boolean primeraLinea = true;

            while ((linea = br.readLine()) != null) {
                if (primeraLinea) {
                    primeraLinea = false; // Saltar header
                    continue;
                }

                String[] campos = linea.split(",");
                if (campos.length >= 7) {
                    String id = campos[0].trim();
                    String codigoOrigen = campos[1].trim();
                    String codigoDestino = campos[2].trim();
                    LocalDateTime horaSalida = LocalDateTime.parse(campos[3].trim(), FORMATTER);
                    LocalDateTime horaLlegada = LocalDateTime.parse(campos[4].trim(), FORMATTER);
                    int capacidadMaxima = Integer.parseInt(campos[5].trim());
                    double duracionHoras = Double.parseDouble(campos[6].trim());

                    Aeropuerto origen = aeropuertoMap.get(codigoOrigen);
                    Aeropuerto destino = aeropuertoMap.get(codigoDestino);

                    if (origen != null && destino != null) {
                        vuelos.add(new Vuelo(id, origen, destino, horaSalida, horaLlegada,
                                capacidadMaxima, duracionHoras));
                    } else {
                        System.err.printf("⚠️ Vuelo %s: Aeropuerto no encontrado (%s→%s)%n",
                                id, codigoOrigen, codigoDestino);
                    }
                }
            }

            System.out.printf("✅ Cargados %d vuelos desde %s%n", vuelos.size(), rutaArchivo);

        } catch (IOException e) {
            System.err.printf("❌ Error al cargar vuelos: %s%n", e.getMessage());
        } catch (Exception e) {
            System.err.printf("❌ Error de formato en vuelos: %s%n", e.getMessage());
        }

        return vuelos;
    }

    /**
     * Carga pedidos desde archivo CSV
     */
    public static List<Pedido> cargarPedidos(String rutaArchivo, Map<String, Aeropuerto> aeropuertoMap) {
        List<Pedido> pedidos = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(rutaArchivo))) {
            String linea;
            boolean primeraLinea = true;

            while ((linea = br.readLine()) != null) {
                if (primeraLinea) {
                    primeraLinea = false; // Saltar header
                    continue;
                }

                String[] campos = linea.split(",");
                if (campos.length >= 6) {
                    String id = campos[0].trim();
                    String clienteId = campos[1].trim();
                    int cantidad = Integer.parseInt(campos[2].trim());
                    LocalDateTime fechaRegistro = LocalDateTime.parse(campos[3].trim(), FORMATTER);
                    String codigoDestino = campos[4].trim();
                    LocalDateTime fechaLimite = LocalDateTime.parse(campos[5].trim(), FORMATTER);

                    Aeropuerto destino = aeropuertoMap.get(codigoDestino);

                    if (destino != null) {
                        pedidos.add(new Pedido(id, clienteId, cantidad, fechaRegistro,
                                destino, fechaLimite));
                    } else {
                        System.err.printf("⚠️ Pedido %s: Destino no encontrado (%s)%n",
                                id, codigoDestino);
                    }
                }
            }

            System.out.printf("✅ Cargados %d pedidos desde %s%n", pedidos.size(), rutaArchivo);

        } catch (IOException e) {
            System.err.printf("❌ Error al cargar pedidos: %s%n", e.getMessage());
        } catch (Exception e) {
            System.err.printf("❌ Error de formato en pedidos: %s%n", e.getMessage());
        }

        return pedidos;
    }

    /**
     * Método principal para cargar todos los datos de una vez
     */
    public static DatosMoraPack cargarDatosCompletos(String rutaAeropuertos,
                                                     String rutaVuelos,
                                                     String rutaPedidos) {
        System.out.println("🔄 Iniciando carga masiva de datos...");

        // 1. Cargar aeropuertos
        List<Aeropuerto> aeropuertos = cargarAeropuertos(rutaAeropuertos);

        // 2. Crear mapa de búsqueda rápida
        Map<String, Aeropuerto> aeropuertoMap = new HashMap<>();
        for (Aeropuerto aeropuerto : aeropuertos) {
            aeropuertoMap.put(aeropuerto.getCodigo(), aeropuerto);
        }

        // 3. Cargar vuelos
        List<Vuelo> vuelos = cargarVuelos(rutaVuelos, aeropuertoMap);

        // 4. Cargar pedidos
        List<Pedido> pedidos = cargarPedidos(rutaPedidos, aeropuertoMap);

        // 5. Validar concordancia
        validarConcordancia(aeropuertos, vuelos, pedidos);

        System.out.println("✅ Carga masiva completada exitosamente");

        return new DatosMoraPack(aeropuertos, vuelos, pedidos);
    }

    /**
     * Valida que los datos sean concordantes y consistentes
     */
    private static void validarConcordancia(List<Aeropuerto> aeropuertos,
                                            List<Vuelo> vuelos,
                                            List<Pedido> pedidos) {
        System.out.println("🔍 Validando concordancia de datos...");

        // Validar fábricas
        long fabricas = aeropuertos.stream()
                .filter(a -> Solucion.FABRICAS.contains(a.getCodigo()))
                .count();
        System.out.printf("   📍 Fábricas encontradas: %d/3%n", fabricas);

        // Validar vuelos desde fábricas
        long vuelosDesdefabricas = vuelos.stream()
                .filter(v -> Solucion.FABRICAS.contains(v.getOrigen().getCodigo()))
                .count();
        System.out.printf("   ✈️ Vuelos desde fábricas: %d%n", vuelosDesdefabricas);

        // Validar destinos de pedidos
        Set<String> destinosPedidos = new HashSet<>();
        Set<String> destinosVuelos = new HashSet<>();

        for (Pedido p : pedidos) {
            destinosPedidos.add(p.getLugarDestino().getCodigo());
        }

        for (Vuelo v : vuelos) {
            destinosVuelos.add(v.getDestino().getCodigo());
        }

        Set<String> destinosAlcanzables = new HashSet<>(destinosPedidos);
        destinosAlcanzables.retainAll(destinosVuelos);

        System.out.printf("   📦 Destinos de pedidos: %d%n", destinosPedidos.size());
        System.out.printf("   🎯 Destinos alcanzables: %d/%d%n",
                destinosAlcanzables.size(), destinosPedidos.size());

        // Validar capacidad total
        int capacidadTotal = vuelos.stream().mapToInt(Vuelo::getCapacidadMaxima).sum();
        int demandaTotal = pedidos.stream().mapToInt(Pedido::getCantidad).sum();
        double ratio = (double) capacidadTotal / demandaTotal;

        System.out.printf("   📊 Capacidad total: %d paquetes%n", capacidadTotal);
        System.out.printf("   📊 Demanda total: %d paquetes%n", demandaTotal);
        System.out.printf("   📊 Ratio capacidad/demanda: %.2f %s%n", ratio,
                ratio >= 1.0 ? "(✅ Factible)" : "(⚠️ Sobrecarga)");

        // Validar horarios
        validarHorarios(vuelos, pedidos);
    }

    /**
     * Valida que los horarios sean lógicos y permitan conexiones
     */
    private static void validarHorarios(List<Vuelo> vuelos, List<Pedido> pedidos) {
        // Encontrar rango de fechas
        LocalDateTime minFecha = vuelos.stream()
                .map(Vuelo::getHoraSalida)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        LocalDateTime maxFecha = vuelos.stream()
                .map(Vuelo::getHoraLlegada)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        System.out.printf("   🕐 Rango de vuelos: %s a %s%n",
                minFecha.toLocalDate(), maxFecha.toLocalDate());

        // Validar que fechas límite sean alcanzables
        long pedidosAlcanzables = pedidos.stream()
                .filter(p -> p.getFechaLimite().isAfter(minFecha))
                .count();

        System.out.printf("   ⏰ Pedidos con fechas límite alcanzables: %d/%d%n",
                pedidosAlcanzables, pedidos.size());
    }

    /**
     * Genera estadísticas detalladas de los datos cargados
     */
    public static void mostrarEstadisticas(DatosMoraPack datos) {
        System.out.println("\n📈 ESTADÍSTICAS DETALLADAS:");
        System.out.println("=".repeat(50));

        // Estadísticas de aeropuertos
        Map<String, Long> aeropuertosPorContinente = datos.getAeropuertos().stream()
                .collect(Collectors.groupingBy(Aeropuerto::getContinente, Collectors.counting()));

        System.out.println("📍 AEROPUERTOS POR CONTINENTE:");
        aeropuertosPorContinente.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> System.out.printf("   %s: %d aeropuertos%n", e.getKey(), e.getValue()));

        // Estadísticas de vuelos
        Map<String, Long> vuelosPorOrigen = datos.getVuelos().stream()
                .collect(Collectors.groupingBy(v -> v.getOrigen().getCodigo(), Collectors.counting()));

        System.out.println("\n✈️ VUELOS POR FÁBRICA:");
        for (String fabrica : Solucion.FABRICAS) {
            System.out.printf("   %s: %d vuelos%n", fabrica, vuelosPorOrigen.getOrDefault(fabrica, 0L));
        }

        // Top 10 destinos con más vuelos
        System.out.println("\n🎯 TOP 10 DESTINOS CON MÁS VUELOS:");
        datos.getVuelos().stream()
                .collect(Collectors.groupingBy(v -> v.getDestino().getCodigo(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> System.out.printf("   %s: %d vuelos%n", e.getKey(), e.getValue()));

        // Estadísticas de pedidos
        Map<String, Long> pedidosPorDestino = datos.getPedidos().stream()
                .collect(Collectors.groupingBy(p -> p.getLugarDestino().getCodigo(), Collectors.counting()));

        System.out.println("\n📦 TOP 10 DESTINOS CON MÁS PEDIDOS:");
        pedidosPorDestino.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> System.out.printf("   %s: %d pedidos%n", e.getKey(), e.getValue()));

        // Distribución de tamaños de pedidos
        IntSummaryStatistics estadsPedidos = datos.getPedidos().stream()
                .mapToInt(Pedido::getCantidad)
                .summaryStatistics();

        System.out.println("\n📊 DISTRIBUCIÓN DE PEDIDOS:");
        System.out.printf("   Total pedidos: %d%n", estadsPedidos.getCount());
        System.out.printf("   Paquetes total: %d%n", estadsPedidos.getSum());
        System.out.printf("   Promedio por pedido: %.1f paquetes%n", estadsPedidos.getAverage());
        System.out.printf("   Pedido más pequeño: %d paquetes%n", estadsPedidos.getMin());
        System.out.printf("   Pedido más grande: %d paquetes%n", estadsPedidos.getMax());

        // Distribución de capacidades de vuelos
        IntSummaryStatistics estadsVuelos = datos.getVuelos().stream()
                .mapToInt(Vuelo::getCapacidadMaxima)
                .summaryStatistics();

        System.out.println("\n🛫 DISTRIBUCIÓN DE VUELOS:");
        System.out.printf("   Total vuelos: %d%n", estadsVuelos.getCount());
        System.out.printf("   Capacidad total: %d paquetes%n", estadsVuelos.getSum());
        System.out.printf("   Promedio por vuelo: %.1f paquetes%n", estadsVuelos.getAverage());
        System.out.printf("   Vuelo más pequeño: %d paquetes%n", estadsVuelos.getMin());
        System.out.printf("   Vuelo más grande: %d paquetes%n", estadsVuelos.getMax());
    }

    /**
     * Clase contenedora para todos los datos de MoraPack
     */
    public static class DatosMoraPack {
        private final List<Aeropuerto> aeropuertos;
        private final List<Vuelo> vuelos;
        private final List<Pedido> pedidos;

        public DatosMoraPack(List<Aeropuerto> aeropuertos, List<Vuelo> vuelos, List<Pedido> pedidos) {
            this.aeropuertos = aeropuertos;
            this.vuelos = vuelos;
            this.pedidos = pedidos;
        }

        public List<Aeropuerto> getAeropuertos() { return aeropuertos; }
        public List<Vuelo> getVuelos() { return vuelos; }
        public List<Pedido> getPedidos() { return pedidos; }

        public int getTotalAeropuertos() { return aeropuertos.size(); }
        public int getTotalVuelos() { return vuelos.size(); }
        public int getTotalPedidos() { return pedidos.size(); }

        /**
         * Crea un mapa de aeropuertos para búsqueda rápida
         */
        public Map<String, Aeropuerto> getAeropuertoMap() {
            Map<String, Aeropuerto> map = new HashMap<>();
            for (Aeropuerto aeropuerto : aeropuertos) {
                map.put(aeropuerto.getCodigo(), aeropuerto);
            }
            return map;
        }

        /**
         * Obtiene estadísticas básicas
         */
        public String getResumenEstadisticas() {
            int capacidadTotal = vuelos.stream().mapToInt(Vuelo::getCapacidadMaxima).sum();
            int demandaTotal = pedidos.stream().mapToInt(Pedido::getCantidad).sum();
            double ratio = (double) capacidadTotal / demandaTotal;

            return String.format(
                    "📋 RESUMEN: %d aeropuertos, %d vuelos, %d pedidos | " +
                            "Capacidad: %d | Demanda: %d | Ratio: %.2f",
                    aeropuertos.size(), vuelos.size(), pedidos.size(),
                    capacidadTotal, demandaTotal, ratio
            );
        }
    }
}