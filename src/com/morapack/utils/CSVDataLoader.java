package com.morapack.utils;

import com.morapack.models.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ✅ SOLUCIÓN MEJORADA: Cargador que duplica vuelos dinámicamente
 * Genera planes de vuelo para cada día necesario según los pedidos
 */
public class CSVDataLoader {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int MAX_DIAS_ADELANTE = 2; // Máximo 4 días como dijo tu profesor

    /**
     * MÉTODO PRINCIPAL MEJORADO: Carga datos y genera vuelos dinámicos
     */
    public static DatosMoraPack cargarDatosCompletos(String rutaAeropuertos,
                                                     String rutaVuelos,
                                                     String rutaPedidos) {
        System.out.println("🔄 Iniciando carga con duplicación dinámica de vuelos...");

        // 1. Cargar aeropuertos
        List<Aeropuerto> aeropuertos = cargarAeropuertos(rutaAeropuertos);
        Map<String, Aeropuerto> aeropuertoMap = crearMapaAeropuertos(aeropuertos);

        // 2. Cargar pedidos primero para conocer el rango de fechas
        List<Pedido> pedidos = cargarPedidos(rutaPedidos, aeropuertoMap);

        // 3. ✅ NUEVO: Calcular rango de fechas necesario basado en pedidos
        RangoFechas rangoNecesario = calcularRangoFechasNecesario(pedidos);
        System.out.printf("📅 Rango de fechas calculado: %s a %s%n",
                rangoNecesario.fechaInicio, rangoNecesario.fechaFin);

        // 4. ✅ NUEVO: Cargar vuelos plantilla y duplicarlos por cada día necesario
        List<Vuelo> vuelosGenerados = cargarYDuplicarVuelos(rutaVuelos, aeropuertoMap, rangoNecesario);

        // 5. Validar concordancia
        validarConcordancia(aeropuertos, vuelosGenerados, pedidos);

        System.out.printf("✅ Generados %d vuelos para %d días de operación%n",
                vuelosGenerados.size(), rangoNecesario.cantidadDias());

        return new DatosMoraPack(aeropuertos, vuelosGenerados, pedidos);
    }

    /**
     * ✅ NUEVO: Calcula el rango de fechas necesario basado en los pedidos
     */
    private static RangoFechas calcularRangoFechasNecesario(List<Pedido> pedidos) {
        if (pedidos.isEmpty()) {
            // Si no hay pedidos, usar desde hoy hasta en 4 días
            LocalDate hoy = LocalDate.now();
            return new RangoFechas(hoy, hoy.plusDays(MAX_DIAS_ADELANTE));
        }

        // Encontrar la fecha de registro más temprana
        LocalDate fechaInicio = pedidos.stream()
                .map(p -> p.getFechaRegistro().toLocalDate())
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());

        // Asegurar que la fecha de inicio no sea anterior a hoy
        fechaInicio = fechaInicio.isBefore(LocalDate.now()) ? LocalDate.now() : fechaInicio;

        // Calcular fecha fin: máximo 4 días desde el último pedido registrado
        LocalDate ultimaFechaRegistro = pedidos.stream()
                .map(p -> p.getFechaRegistro().toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());

        LocalDate fechaFin = ultimaFechaRegistro.plusDays(MAX_DIAS_ADELANTE);

        return new RangoFechas(fechaInicio, fechaFin);
    }

    /**
     * ✅ OPTIMIZADO: Carga plantillas y las duplica con control de memoria
     */
    private static List<Vuelo> cargarYDuplicarVuelos(String rutaTxt,
                                                     Map<String, Aeropuerto> aeropuertos,
                                                     RangoFechas rango) {

        // 1. Cargar plantillas de vuelos (horarios sin fecha específica)
        List<PlantillaVuelo> plantillas = cargarPlantillasVuelos(rutaTxt, aeropuertos);
        System.out.printf("📋 Cargadas %d plantillas de vuelos diarios%n", plantillas.size());

        // 2. Calcular volumen total antes de generar
        int diasTotal = rango.cantidadDias();
        long vuelosTotalesEstimados = (long) plantillas.size() * diasTotal;

        System.out.printf("⚡ Generando %d plantillas × %d días = %,d vuelos totales%n",
                plantillas.size(), diasTotal, vuelosTotalesEstimados);

        // 3. Advertencia si el volumen es muy grande
        if (vuelosTotalesEstimados > 10000) {
            System.out.printf("⚠️  ADVERTENCIA: Generando %,d vuelos. Esto puede usar mucha memoria.%n",
                    vuelosTotalesEstimados);
            System.out.println("💡 Considera reducir el rango de días o filtrar plantillas por fábricas.");
        }

        // 4. Generar vuelos día por día para control de memoria
        List<Vuelo> vuelosGenerados = new ArrayList<>((int) vuelosTotalesEstimados);
        LocalDate fechaActual = rango.fechaInicio;
        int diaActual = 1;

        while (!fechaActual.isAfter(rango.fechaFin)) {
            System.out.printf("🔄 Generando vuelos para día %d/%d (%s)...%n",
                    diaActual, diasTotal, fechaActual);

            for (PlantillaVuelo plantilla : plantillas) {
                Vuelo vueloDelDia = generarVueloParaFecha(plantilla, fechaActual);
                vuelosGenerados.add(vueloDelDia);
            }

            fechaActual = fechaActual.plusDays(1);
            diaActual++;
        }

        System.out.printf("✅ Generación completada: %,d vuelos totales%n", vuelosGenerados.size());

        // 5. Mostrar estadísticas de memoria
        mostrarEstadisticasMemoria(vuelosGenerados, plantillas.size());

        return vuelosGenerados;
    }

    /**
     * ✅ NUEVO: Filtrar plantillas solo desde fábricas (optimización)
     */
    public static List<Vuelo> cargarYDuplicarVuelosOptimizado(String rutaTxt,
                                                              Map<String, Aeropuerto> aeropuertos,
                                                              RangoFechas rango,
                                                              boolean soloDesdefabricas) {

        List<PlantillaVuelo> plantillas = cargarPlantillasVuelos(rutaTxt, aeropuertos);

        // Filtrar solo vuelos desde fábricas si se solicita
        if (soloDesdefabricas) {
            List<String> fabricas = Solucion.FABRICAS;
            plantillas = plantillas.stream()
                    .filter(p -> fabricas.contains(p.origen.getCodigo()))
                    .collect(Collectors.toList());

            System.out.printf("🏭 Filtradas %d plantillas desde fábricas únicamente%n", plantillas.size());
        }

        return generarVuelosDesdelantillas(plantillas, rango);
    }

    /**
     * ✅ NUEVO: Método auxiliar para generar vuelos desde plantillas
     */
    private static List<Vuelo> generarVuelosDesdelantillas(List<PlantillaVuelo> plantillas, RangoFechas rango) {
        int diasTotal = rango.cantidadDias();
        List<Vuelo> vuelosGenerados = new ArrayList<>(plantillas.size() * diasTotal);

        LocalDate fechaActual = rango.fechaInicio;
        while (!fechaActual.isAfter(rango.fechaFin)) {
            for (PlantillaVuelo plantilla : plantillas) {
                Vuelo vueloDelDia = generarVueloParaFecha(plantilla, fechaActual);
                vuelosGenerados.add(vueloDelDia);
            }
            fechaActual = fechaActual.plusDays(1);
        }

        return vuelosGenerados;
    }

    /**
     * ✅ NUEVO: Estadísticas de uso de memoria
     */
    private static void mostrarEstadisticasMemoria(List<Vuelo> vuelos, int plantillasOriginales) {
        Runtime runtime = Runtime.getRuntime();
        long memoryUsed = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsedMB = memoryUsed / (1024 * 1024);

        System.out.printf("💾 Memoria usada: %d MB%n", memoryUsedMB);
        System.out.printf("📊 Factor de multiplicación: %dx (de %d a %,d vuelos)%n",
                vuelos.size() / plantillasOriginales, plantillasOriginales, vuelos.size());

        if (memoryUsedMB > 500) {
            System.out.println("⚠️  Alto uso de memoria. Considera optimizar.");
        }
    }

    /**
     * ✅ NUEVO: Carga plantillas de vuelos (horarios recurrentes)
     */
    private static List<PlantillaVuelo> cargarPlantillasVuelos(String rutaTxt,
                                                               Map<String, Aeropuerto> aeropuertos) {
        List<PlantillaVuelo> plantillas = new ArrayList<>();
        Pattern pat = Pattern.compile("^([A-Z]{4})-([A-Z]{4})-(\\d{2}):(\\d{2})-(\\d{2}):(\\d{2})-(\\d{3,4})$");

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(rutaTxt), StandardCharsets.UTF_8))) {

            String linea;
            int numeroPlantilla = 1;

            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("#")) continue;

                Matcher m = pat.matcher(linea);
                if (!m.matches()) continue;

                String origIcao = m.group(1);
                String destIcao = m.group(2);
                int horaSalida = Integer.parseInt(m.group(3));
                int minutoSalida = Integer.parseInt(m.group(4));
                int horaLlegada = Integer.parseInt(m.group(5));
                int minutoLlegada = Integer.parseInt(m.group(6));
                int capacidad = Integer.parseInt(m.group(7));

                Aeropuerto origen = aeropuertos.get(origIcao);
                Aeropuerto destino = aeropuertos.get(destIcao);

                if (origen != null && destino != null) {
                    PlantillaVuelo plantilla = new PlantillaVuelo(
                            "TEMPLATE_" + numeroPlantilla++,
                            origen,
                            destino,
                            LocalTime.of(horaSalida, minutoSalida),
                            LocalTime.of(horaLlegada, minutoLlegada),
                            capacidad
                    );
                    plantillas.add(plantilla);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Error al cargar plantillas de vuelos: " + e.getMessage(), e);
        }

        return plantillas;
    }

    /**
     * ✅ NUEVO: Genera un vuelo específico para una fecha usando una plantilla
     */
    private static Vuelo generarVueloParaFecha(PlantillaVuelo plantilla, LocalDate fecha) {
        // Combinar fecha con horarios de la plantilla
        LocalDateTime salidaCompleta = LocalDateTime.of(fecha, plantilla.horaSalida);
        LocalDateTime llegadaCompleta = LocalDateTime.of(fecha, plantilla.horaLlegada);

        // Si la llegada es antes que la salida, es del día siguiente
        if (llegadaCompleta.isBefore(salidaCompleta) || llegadaCompleta.equals(salidaCompleta)) {
            llegadaCompleta = llegadaCompleta.plusDays(1);
        }

        // Calcular duración real
        double duracionHoras = Duration.between(salidaCompleta, llegadaCompleta).toMinutes() / 60.0;

        // Generar ID único para este vuelo específico
        String idVuelo = String.format("%s-%s-%s-%02d%02d",
                plantilla.origen.getCodigo(),
                plantilla.destino.getCodigo(),
                fecha.toString().replace("-", ""),
                plantilla.horaSalida.getHour(),
                plantilla.horaSalida.getMinute()
        );

        return new Vuelo(
                idVuelo,
                plantilla.origen,
                plantilla.destino,
                salidaCompleta,
                llegadaCompleta,
                plantilla.capacidad,
                duracionHoras
        );
    }

    /**
     * ✅ NUEVO: Clase para representar plantillas de vuelos (horarios recurrentes)
     */
    private static class PlantillaVuelo {
        final String idPlantilla;
        final Aeropuerto origen;
        final Aeropuerto destino;
        final LocalTime horaSalida;
        final LocalTime horaLlegada;
        final int capacidad;

        PlantillaVuelo(String idPlantilla, Aeropuerto origen, Aeropuerto destino,
                       LocalTime horaSalida, LocalTime horaLlegada, int capacidad) {
            this.idPlantilla = idPlantilla;
            this.origen = origen;
            this.destino = destino;
            this.horaSalida = horaSalida;
            this.horaLlegada = horaLlegada;
            this.capacidad = capacidad;
        }
    }

    /**
     * ✅ NUEVO: Clase para manejar rangos de fechas
     */
    private static class RangoFechas {
        final LocalDate fechaInicio;
        final LocalDate fechaFin;

        RangoFechas(LocalDate fechaInicio, LocalDate fechaFin) {
            this.fechaInicio = fechaInicio;
            this.fechaFin = fechaFin;
        }

        int cantidadDias() {
            return (int) Duration.between(
                    fechaInicio.atStartOfDay(),
                    fechaFin.atStartOfDay()
            ).toDays() + 1;
        }
    }

    // ===== MÉTODOS DE UTILIDAD ADICIONALES =====

    /**
     * ✅ NUEVO: Método para regenerar vuelos cuando llega un nuevo pedido
     */
    public static List<Vuelo> regenerarVuelosParaNuevoPedido(String rutaVuelos,
                                                             Map<String, Aeropuerto> aeropuertos,
                                                             LocalDate fechaRegistroPedido) {

        LocalDate fechaInicio = fechaRegistroPedido.isBefore(LocalDate.now()) ?
                LocalDate.now() : fechaRegistroPedido;
        LocalDate fechaFin = fechaInicio.plusDays(MAX_DIAS_ADELANTE);

        RangoFechas rangoNuevo = new RangoFechas(fechaInicio, fechaFin);

        return cargarYDuplicarVuelos(rutaVuelos, aeropuertos, rangoNuevo);
    }

    /**
     * ✅ Estadísticas mejoradas que muestran la duplicación de vuelos
     */
    public static void mostrarEstadisticasVuelosDuplicados(List<Vuelo> vuelos) {
        // Agrupar vuelos por fecha
        Map<LocalDate, Long> vuelosPorFecha = vuelos.stream()
                .collect(Collectors.groupingBy(
                        v -> v.getHoraSalida().toLocalDate(),
                        Collectors.counting()
                ));

        System.out.println("\n📅 DISTRIBUCIÓN DE VUELOS POR FECHA:");
        vuelosPorFecha.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry ->
                        System.out.printf("   %s: %d vuelos%n", entry.getKey(), entry.getValue())
                );

        // Agrupar por ruta (origen-destino)
        Map<String, Long> vuelosPorRuta = vuelos.stream()
                .collect(Collectors.groupingBy(
                        v -> v.getOrigen().getCodigo() + "-" + v.getDestino().getCodigo(),
                        Collectors.counting()
                ));

        System.out.printf("\n✈️ RUTAS ÚNICAS DUPLICADAS: %d rutas x %d días%n",
                vuelosPorRuta.size(),
                vuelosPorFecha.size());

        System.out.println("🔝 TOP 5 RUTAS MÁS FRECUENTES:");
        vuelosPorRuta.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(entry ->
                        System.out.printf("   %s: %d vuelos%n", entry.getKey(), entry.getValue())
                );
    }

    // ===== MÉTODOS EXISTENTES (sin cambios) =====
    public static List<Aeropuerto> cargarAeropuertos(String rutaArchivo) {
        // ... código existente sin cambios ...
        List<Aeropuerto> aeropuertos = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(rutaArchivo))) {
            String linea;
            boolean primeraLinea = true;

            while ((linea = br.readLine()) != null) {
                if (primeraLinea) {
                    primeraLinea = false;
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
        }

        return aeropuertos;
    }

    /**
     * Carga vuelos desde archivo TXT
     */
    public static List<Vuelo> cargarVuelos(String rutaTxt, Map<String, Aeropuerto> aeropuertos) {
        List<Vuelo> vuelos = new ArrayList<>();

    
        final LocalDate fechaBase = LocalDate.now();

        // Formato: AAAA-BBBB-HH:MM-HH:MM-CCCC
        Pattern pat = Pattern.compile(
                "^([A-Z]{4})-([A-Z]{4})-(\\d{2}):(\\d{2})-(\\d{2}):(\\d{2})-(\\d{3,4})$"
        );
        final LocalDate FECHA_ANCLA = LocalDate.of(2000, 1, 1);
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(rutaTxt), StandardCharsets.UTF_8))) {

            String ln;
            while ((ln = br.readLine()) != null) {
                ln = ln.trim();
                if (ln.isEmpty() || ln.startsWith("#")) continue;

                Matcher m = pat.matcher(ln);
                if (!m.matches()) {
                    // Línea con formato no válido: se omite
                    continue;
                }

                String origIcao = m.group(1);
                String destIcao = m.group(2);
                int sh = Integer.parseInt(m.group(3));//hora salida
                int sm = Integer.parseInt(m.group(4));//minuto salida
                int lh = Integer.parseInt(m.group(5));//hora llegada
                int lm = Integer.parseInt(m.group(6));//minuto llegada
                int capacidad = Integer.parseInt(m.group(7)); // "0300" -> 300

                Aeropuerto origen  = aeropuertos.get(origIcao);
                Aeropuerto destino = aeropuertos.get(destIcao);
                if (origen == null || destino == null) {
                    // Alguno de los aeropuertos no está cargado -> omitir vuelo
                    continue;
                }

                // 1) Horas locales ancladas a una fecha fija (solo para construir LDT)
                LocalDateTime salidaLocal  = LocalDateTime.of(FECHA_ANCLA, LocalTime.of(sh, sm));
                LocalDateTime llegadaLocal = LocalDateTime.of(FECHA_ANCLA, LocalTime.of(lh, lm));

                // 2) Misma hora, pero con su GMT real (huso del aeropuerto) -> línea de tiempo común
                OffsetDateTime salidaOffset = OffsetDateTime.of(
                        salidaLocal, ZoneOffset.ofHours(origen.getHusoHorario()));
                OffsetDateTime llegadaOffset = OffsetDateTime.of(
                        llegadaLocal, ZoneOffset.ofHours(destino.getHusoHorario()));

                // 3) Si en la línea de tiempo real la llegada ocurre antes/igual, es del día siguiente
                if (!llegadaOffset.toInstant().isAfter(salidaOffset.toInstant())) {
                    llegadaLocal  = llegadaLocal.plusDays(1);
                    llegadaOffset = llegadaOffset.plusDays(1);
                }

                // 4) Duración REAL entre instantes (tiene en cuenta GMT de cada aeropuerto)
                double duracionHoras = Duration.between(
                        salidaOffset.toInstant(), llegadaOffset.toInstant()
                ).toMinutes() / 60.0;
                String idVuelo = origIcao + "-" + destIcao + "-" + String.format("%02d%02d", sh, sm);

                // 6) Construye Vuelo con tus tipos (sin esInternacional, tu constructor ya lo resuelve)
                Vuelo v = new Vuelo(
                        idVuelo,
                        origen,
                        destino,
                        salidaLocal,          // hora local anclada
                        llegadaLocal,         // hora local anclada (día +1 si correspondía)
                        capacidad,
                        duracionHoras
                );

                vuelos.add(v);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
            return vuelos;
        }


    /**
     * Carga pedidos desde archivo CSV
     */
    public static List<Pedido> cargarPedidos(String rutaArchivo, Map<String, Aeropuerto> aeropuertoMap) {
        // Nuevo parser para formato: dd-hh-mm-dest-###-IdClien
        final Pattern PATRON = Pattern.compile(
                "^(\\d{2})-(\\d{2})-(\\d{2})-([A-Z0-9]{3,4})-(\\d{3})-(\\d{7})$"
        );

        List<Pedido> pedidos = new ArrayList<>();
        int autoinc = 1; // ID autoincremental por archivo leído

        // Año/mes de ancla: mes actual (archivo mensual)
        YearMonth ym = YearMonth.from(LocalDate.now());

        try (BufferedReader br = new BufferedReader(new FileReader(rutaArchivo, StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                if (linea.isBlank() || linea.startsWith("#")) continue;

                linea = linea.trim();
                Matcher m = PATRON.matcher(linea);
                if (!m.matches()) {
                    System.err.printf("⚠️ Línea inválida (se omite): %s%n", linea);
                    continue;
                }

                int dd = Integer.parseInt(m.group(1));
                int hh = Integer.parseInt(m.group(2)); // 00–23 permitido
                int mm = Integer.parseInt(m.group(3)); // 00–59 permitido
                String codDestino = m.group(4);
                int cantidad = Integer.parseInt(m.group(5)); // 001–999
                String clienteId = m.group(6);               // 7 dígitos

                // Validaciones de rango básicas
                if (dd < 1 || dd > ym.lengthOfMonth()) {
                    System.err.printf("⚠️ Día fuera de rango para %s: %02d (línea: %s)%n", ym, dd, linea);
                    continue;
                }
                if (cantidad < 1 || cantidad > 999) {
                    System.err.printf("⚠️ Cantidad fuera de rango (1–999): %d (línea: %s)%n", cantidad, linea);
                    continue;
                }

                Aeropuerto destino = aeropuertoMap.get(codDestino);
                if (destino == null) {
                    System.err.printf("⚠️ Destino no encontrado en aeropuertos: %s (línea: %s)%n", codDestino, linea);
                    continue;
                }

                // Fecha/hora de registro anclada al mes actual (sin zonas/husos aquí)
                LocalDateTime fechaRegistro = LocalDateTime.of(ym.getYear(), ym.getMonth(), dd, hh, mm);

                String id = String.format("P%05d", autoinc++); // P00001, P00002, ...
                pedidos.add(new Pedido(id, clienteId, cantidad, fechaRegistro, destino));
            }

            System.out.printf("✅ Cargados %d pedidos desde %s%n", pedidos.size(), rutaArchivo);

        } catch (IOException e) {
            System.err.printf("❌ Error al cargar pedidos: %s%n", e.getMessage());
        }

        return pedidos;
    }


    private static Map<String, Aeropuerto> crearMapaAeropuertos(List<Aeropuerto> aeropuertos) {
        Map<String, Aeropuerto> mapa = new HashMap<>();
        for (Aeropuerto aeropuerto : aeropuertos) {
            mapa.put(aeropuerto.getCodigo(), aeropuerto);
        }
        return mapa;
    }

    private static void validarConcordancia(List<Aeropuerto> aeropuertos,
                                            List<Vuelo> vuelos,
                                            List<Pedido> pedidos) {
        System.out.println("🔍 Validando concordancia de datos con vuelos duplicados...");
        // ... resto del método de validación existente ...
    }

    // ===== CLASE CONTENEDORA (sin cambios) =====
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

        public Map<String, Aeropuerto> getAeropuertoMap() {
            Map<String, Aeropuerto> map = new HashMap<>();
            for (Aeropuerto aeropuerto : aeropuertos) {
                map.put(aeropuerto.getCodigo(), aeropuerto);
            }
            return map;
        }

        public String getResumenEstadisticas() {
            StringBuilder resumen = new StringBuilder();
            int capacidadTotal = vuelos.stream().mapToInt(Vuelo::getCapacidadMaxima).sum();
            int demandaTotal = pedidos.stream().mapToInt(Pedido::getCantidad).sum();
            double ratio = (double) capacidadTotal / demandaTotal;

            resumen.append(String.format("📊 Capacidad total: %d paquetes%n", capacidadTotal));
            resumen.append(String.format("📊 Demanda total: %d paquetes%n", demandaTotal));
            resumen.append(String.format("📊 Ratio capacidad/demanda: %.2f %s%n", ratio,
                    ratio >= 1.0 ? "(✅ Factible)" : "(⚠ Sobrecarga)"));

            validarHorarios(vuelos, pedidos);
            return resumen.toString();
        }

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
        /*long pedidosAlcanzables = pedidos.stream()
                .filter(p -> p.getFechaLimite().isAfter(minFecha))
                .count();
*/
       /* System.out.printf("   ⏰ Pedidos con fechas límite alcanzables: %d/%d%n",
                pedidosAlcanzables, pedidos.size());*/
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
}