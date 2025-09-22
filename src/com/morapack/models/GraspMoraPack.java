package com.morapack.models;

import com.morapack.models.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ✅ VERSIÓN CORREGIDA: GRASP con validación de capacidad de vuelos
 * Previene violaciones de capacidad durante la construcción de rutas
 */
public class GraspMoraPack {

    private List<Pedido> pedidos;
    private List<Vuelo> vuelos;
    private Map<String, List<Vuelo>> vuelosPorOrigen;
    private Random random;
    private double alfa;


    private Map<Vuelo, Integer> ocupacionActual;
    // ✅ NUEVO: Sistemas duales de almacenes
    private Map<String, Integer> ocupacionAlmacenes;                    // Sistema estático (compatibilidad)
    private Map<String, List<EventoAlmacen>> historialAlmacenes;        // Sistema temporal (nuevo)
    private Map<String, Aeropuerto> aeropuertoMap;

    // ✅ NUEVO: Constante para liberación automática
    private static final int HORAS_LIBERACION = 2;   // ✅ NUEVO

    public GraspMoraPack(List<Pedido> pedidos, List<Vuelo> vuelos) {
        this.pedidos = new ArrayList<>(pedidos);
        this.vuelos = new ArrayList<>(vuelos);
        this.random = new Random();
        this.vuelosPorOrigen = new HashMap<>();
        this.ocupacionActual = new HashMap<>();
        this.ocupacionAlmacenes = new HashMap<>();         // ✅ RESTAURADO: Sistema estático
        this.historialAlmacenes = new HashMap<>();         // ✅ NUEVO: Sistema temporal
        this.aeropuertoMap = new HashMap<>();

        inicializarVuelos();
        inicializarMapaAeropuertos();
    }
    // ✅ NUEVO: Inicializar mapa de aeropuertos
    private void inicializarMapaAeropuertos() {
        for (Vuelo vuelo : vuelos) {
            aeropuertoMap.put(vuelo.getOrigen().getCodigo(), vuelo.getOrigen());
            aeropuertoMap.put(vuelo.getDestino().getCodigo(), vuelo.getDestino());
        }
        System.out.printf("✅ Mapa de aeropuertos inicializado: %d aeropuertos%n", aeropuertoMap.size());
    }

    /**
     * ✅ NUEVO: Clase interna para representar eventos en almacenes
     */
    private static class EventoAlmacen {
        final LocalDateTime fechaLlegada;
        final LocalDateTime fechaLiberacion;
        final int cantidadPaquetes;
        final String pedidoId; // Para debugging

        EventoAlmacen(LocalDateTime fechaLlegada, int cantidadPaquetes, String pedidoId) {
            this.fechaLlegada = fechaLlegada;
            this.fechaLiberacion = fechaLlegada.plusHours(HORAS_LIBERACION);
            this.cantidadPaquetes = cantidadPaquetes;
            this.pedidoId = pedidoId;
        }

        boolean estaOcupado(LocalDateTime momento) {
            return !momento.isBefore(fechaLlegada) && momento.isBefore(fechaLiberacion);
        }

        @Override
        public String toString() {
            return String.format("Evento[%s: %d paquetes, %s→%s]",
                    pedidoId, cantidadPaquetes, fechaLlegada, fechaLiberacion);
        }
    }

    // ✅ NUEVO: Buscar aeropuerto por código
    private Aeropuerto buscarAeropuertoPorCodigo(String codigo) {
        return aeropuertoMap.get(codigo);
    }

    /**
     *  CORREGIDO: Genera una solución validando capacidades
     */
    public Solucion generarSolucion() {
        SolucionLogistica solucionLogistica = new SolucionLogistica();
        solucionLogistica.setAsignacionPedidos(new HashMap<>());

        // Resetear ocupación al inicio
        ocupacionActual.clear();
        ocupacionAlmacenes.clear();         // ✅ RESTAURADO: Sistema estático
        historialAlmacenes.clear();         // ✅ NUEVO: Sistema temporal

        List<Pedido> pedidosOrdenados = pedidos;
        int pedidosAsignados = 0;
        int pedidosRechazadosVuelos = 0;
        int pedidosRechazadosAlmacenes = 0;

        for (Pedido pedido : pedidosOrdenados) {
            List<Vuelo> rutaAsignada = buscarMejorRutaParaPedidoConCapacidadTemporal(pedido);

            if (!rutaAsignada.isEmpty()) {
                RutaPedido ruta = new RutaPedido(pedido, rutaAsignada);
                configurarRuta(ruta, rutaAsignada);
                solucionLogistica.agregarRutaPedido(pedido, ruta);

                // Actualizar ocupación de vuelos
                actualizarOcupacion(rutaAsignada, pedido.getCantidad());

                // ✅ RESTAURADO: Actualizar sistema estático (compatibilidad)
                actualizarOcupacionAlmacenes(rutaAsignada, pedido.getCantidad());

                // ✅ NUEVO: Registrar eventos temporales en almacenes
                registrarEventosEnAlmacenes(rutaAsignada, pedido);

                pedidosAsignados++;
            } else {
                // Determinar causa del rechazo
                List<Vuelo> rutaSoloVuelos = buscarMejorRutaParaPedidoConCapacidad(pedido);
                if (rutaSoloVuelos.isEmpty()) {
                    pedidosRechazadosVuelos++;
                } else {
                    pedidosRechazadosAlmacenes++;
                }
            }
        }

        System.out.printf("📊 Resumen GRASP: %d asignados, %d rechazados por vuelos, %d por almacenes%n",
                pedidosAsignados, pedidosRechazadosVuelos, pedidosRechazadosAlmacenes);

        return new Solucion(solucionLogistica, pedidos.size());
    }
    private List<Vuelo> buscarMejorRutaParaPedidoConCapacidadTemporal(Pedido pedido) {
        String destinoCodigo = pedido.getLugarDestino().getCodigo();
        List<CandidatoRuta> candidatos = new ArrayList<>();

        for (String codigoFabrica : Solucion.FABRICAS) {
            // Rutas directas
            List<Vuelo> rutaDirecta = buscarRutaDirectaDesdeOrigenConCapacidad(
                    codigoFabrica, destinoCodigo, pedido);
            if (!rutaDirecta.isEmpty() &&
                    rutaTieneCapacidadTemporalCompleta(rutaDirecta, pedido)) {
                double puntuacion = calcularPuntuacionRuta(rutaDirecta);
                candidatos.add(new CandidatoRuta(rutaDirecta, puntuacion));
            }

            // Rutas con escala
            List<List<Vuelo>> rutasConEscala = buscarRutasConEscalaDesdeOrigenConCapacidad(
                    codigoFabrica, destinoCodigo, pedido);
            for (List<Vuelo> ruta : rutasConEscala) {
                if (rutaTieneCapacidadTemporalCompleta(ruta, pedido)) {
                    double puntuacion = calcularPuntuacionRuta(ruta);
                    candidatos.add(new CandidatoRuta(ruta, puntuacion));
                }
            }
        }

        if (candidatos.isEmpty()) {
            return new ArrayList<>();
        }

        return seleccionarCandidatoGRASP(candidatos);
    }

    private boolean rutaTieneCapacidadTemporalCompleta(List<Vuelo> ruta, Pedido pedido) {
        // 1. Verificar capacidad de vuelos (igual que antes)
        for (Vuelo vuelo : ruta) {
            if (!tieneCapacidadDisponible(vuelo, pedido.getCantidad())) {
                return false;
            }
        }

        // 2. ✅ NUEVO: Verificar capacidad temporal de almacenes intermedios (escalas)
        for (int i = 0; i < ruta.size() - 1; i++) {
            Aeropuerto almacenIntermedio = ruta.get(i).getDestino();
            LocalDateTime momentoLlegada = ruta.get(i).getHoraLlegada();

            if (!almacenTieneCapacidadTemporal(almacenIntermedio, pedido.getCantidad(), momentoLlegada)) {
                return false;
            }
        }

        // 3. ✅ NUEVO: Verificar capacidad temporal del almacén de destino final
        if (!ruta.isEmpty()) {
            Aeropuerto almacenFinal = ruta.get(ruta.size() - 1).getDestino();
            LocalDateTime momentoLlegada = ruta.get(ruta.size() - 1).getHoraLlegada();

            if (!almacenTieneCapacidadTemporal(almacenFinal, pedido.getCantidad(), momentoLlegada)) {
                return false;
            }
        }

        return true;
    }

    private boolean almacenTieneCapacidadTemporal(Aeropuerto almacen, int cantidadRequerida, LocalDateTime momentoLlegada) {
        if (almacen == null || momentoLlegada == null) return false;

        // Obtener historial de eventos del almacén
        List<EventoAlmacen> eventos = historialAlmacenes.getOrDefault(almacen.getCodigo(), new ArrayList<>());

        // Calcular ocupación en el momento de llegada del nuevo pedido
        int ocupacionEnMomento = almacen.getCapacidadAct(); // ocupación base inicial

        for (EventoAlmacen evento : eventos) {
            if (evento.estaOcupado(momentoLlegada)) {
                ocupacionEnMomento += evento.cantidadPaquetes;
            }
        }

        int capacidadDisponible = almacen.getCapacidad() - ocupacionEnMomento;
        boolean tieneCapacidad = capacidadDisponible >= cantidadRequerida;

        // Debug opcional (quitar en producción)
        if (!tieneCapacidad && eventos.size() > 0) {
            System.out.printf("⚠️ Almacén %s lleno en %s: %d/%d usado, necesita %d (eventos activos: %d)%n",
                    almacen.getCodigo(), momentoLlegada, ocupacionEnMomento, almacen.getCapacidad(),
                    cantidadRequerida, eventos.stream().mapToInt(e -> e.estaOcupado(momentoLlegada) ? 1 : 0).sum());
        }

        return tieneCapacidad;
    }

    /**
     * ✅ NUEVO: Registra eventos temporales en almacenes
     */
    private void registrarEventosEnAlmacenes(List<Vuelo> ruta, Pedido pedido) {
        // Registrar en almacenes intermedios (escalas)
        for (int i = 0; i < ruta.size() - 1; i++) {
            String codigoAlmacen = ruta.get(i).getDestino().getCodigo();
            LocalDateTime momentoLlegada = ruta.get(i).getHoraLlegada();

            EventoAlmacen evento = new EventoAlmacen(momentoLlegada, pedido.getCantidad(), pedido.getId());
            historialAlmacenes.computeIfAbsent(codigoAlmacen, k -> new ArrayList<>()).add(evento);
        }

        // Registrar en almacén de destino final
        if (!ruta.isEmpty()) {
            Vuelo ultimoVuelo = ruta.get(ruta.size() - 1);
            String codigoDestino = ultimoVuelo.getDestino().getCodigo();
            LocalDateTime momentoLlegada = ultimoVuelo.getHoraLlegada();

            EventoAlmacen evento = new EventoAlmacen(momentoLlegada, pedido.getCantidad(), pedido.getId());
            historialAlmacenes.computeIfAbsent(codigoDestino, k -> new ArrayList<>()).add(evento);
        }
    }

    /**
     * ✅ NUEVO: Método para debug - mostrar estado temporal de almacenes
     */
    public void mostrarEstadoTemporalAlmacenes() {
        System.out.println("\n🏬 ESTADO TEMPORAL DE ALMACENES:");

        if (historialAlmacenes.isEmpty()) {
            System.out.println("   No hay eventos registrados.");
            return;
        }

        for (Map.Entry<String, List<EventoAlmacen>> entry : historialAlmacenes.entrySet()) {
            String codigoAlmacen = entry.getKey();
            List<EventoAlmacen> eventos = entry.getValue();

            Aeropuerto almacen = aeropuertoMap.get(codigoAlmacen);
            if (almacen == null) continue;

            System.out.printf("\n📦 Almacén %s (Cap: %d):%n", codigoAlmacen, almacen.getCapacidad());

            // Ordenar eventos por fecha de llegada
            eventos.stream()
                    .sorted(Comparator.comparing(e -> e.fechaLlegada))
                    .forEach(evento -> {
                        System.out.printf("   • %s%n", evento);
                    });

            // Simular ocupación a lo largo del tiempo
            if (eventos.size() > 1) {
                System.out.printf("   📊 Picos de ocupación simulados:%n");
                simularOcupacionTemporal(almacen, eventos);
            }
        }
    }

    /**
     * ✅ NUEVO: Simula la ocupación temporal para debugging
     */
    private void simularOcupacionTemporal(Aeropuerto almacen, List<EventoAlmacen> eventos) {
        // Crear lista de todos los momentos importantes (llegadas y liberaciones)
        Set<LocalDateTime> momentosImportantes = new TreeSet<>();

        for (EventoAlmacen evento : eventos) {
            momentosImportantes.add(evento.fechaLlegada);
            momentosImportantes.add(evento.fechaLiberacion);
        }

        // Calcular ocupación en cada momento
        int maxOcupacion = 0;
        LocalDateTime momentoMaximo = null;

        for (LocalDateTime momento : momentosImportantes) {
            int ocupacion = almacen.getCapacidadAct();

            for (EventoAlmacen evento : eventos) {
                if (evento.estaOcupado(momento)) {
                    ocupacion += evento.cantidadPaquetes;
                }
            }

            if (ocupacion > maxOcupacion) {
                maxOcupacion = ocupacion;
                momentoMaximo = momento;
            }

            // Mostrar solo puntos de interés
            if (ocupacion > almacen.getCapacidad() * 0.8) { // Más del 80%
                double porcentaje = (double) ocupacion / almacen.getCapacidad() * 100;
                String estado = ocupacion > almacen.getCapacidad() ? "❌ SOBRECARGA" :
                        ocupacion > almacen.getCapacidad() * 0.9 ? "⚠️ CRÍTICO" : "🟡 ALTO";

                System.out.printf("     %s: %d/%d (%.1f%%) %s%n",
                        momento, ocupacion, almacen.getCapacidad(), porcentaje, estado);
            }
        }

        System.out.printf("   🔴 Pico máximo: %d/%d en %s%n",
                maxOcupacion, almacen.getCapacidad(), momentoMaximo);
    }

    // ✅ NUEVO: Busca ruta validando VUELOS Y ALMACENES
    private List<Vuelo> buscarMejorRutaParaPedidoConCapacidadCompleta(Pedido pedido) {
        String destinoCodigo = pedido.getLugarDestino().getCodigo();
        List<CandidatoRuta> candidatos = new ArrayList<>();

        for (String codigoFabrica : Solucion.FABRICAS) {
            // Rutas directas CON VALIDACIÓN COMPLETA
            List<Vuelo> rutaDirecta = buscarRutaDirectaDesdeOrigenConCapacidad(
                    codigoFabrica, destinoCodigo, pedido);
            if (!rutaDirecta.isEmpty() &&
                    rutaTieneCapacidadCompleta(rutaDirecta, pedido.getCantidad())) {
                double puntuacion = calcularPuntuacionRuta(rutaDirecta);
                candidatos.add(new CandidatoRuta(rutaDirecta, puntuacion));
            }

            // Rutas con escala CON VALIDACIÓN COMPLETA
            List<List<Vuelo>> rutasConEscala = buscarRutasConEscalaDesdeOrigenConCapacidad(
                    codigoFabrica, destinoCodigo, pedido);
            for (List<Vuelo> ruta : rutasConEscala) {
                if (rutaTieneCapacidadCompleta(ruta, pedido.getCantidad())) {
                    double puntuacion = calcularPuntuacionRuta(ruta);
                    candidatos.add(new CandidatoRuta(ruta, puntuacion));
                }
            }
        }

        if (candidatos.isEmpty()) {
            return new ArrayList<>();
        }

        return seleccionarCandidatoGRASP(candidatos);
    }

    private boolean almacenTieneCapacidad(Aeropuerto almacen, int cantidadRequerida) {
        if (almacen == null) return false;

        int ocupacionActual = ocupacionAlmacenes.getOrDefault(almacen.getCodigo(), 0);
        int capacidadDisponible = almacen.getCapacidad() - almacen.getCapacidadAct() - ocupacionActual;

        boolean tieneCapacidad = capacidadDisponible >= cantidadRequerida;


        return tieneCapacidad;
    }
    /**
     *  NUEVO: Busca ruta validando capacidad disponible
     */
    private List<Vuelo> buscarMejorRutaParaPedidoConCapacidad(Pedido pedido) {
        String destinoCodigo = pedido.getLugarDestino().getCodigo();
        List<CandidatoRuta> candidatos = new ArrayList<>();

        // Buscar rutas desde cada fábrica
        for (String codigoFabrica : Solucion.FABRICAS) {
            // Rutas directas CON VALIDACIÓN
            List<Vuelo> rutaDirecta = buscarRutaDirectaDesdeOrigenConCapacidad(
                    codigoFabrica, destinoCodigo, pedido);
            if (!rutaDirecta.isEmpty()) {
                double puntuacion = calcularPuntuacionRuta(rutaDirecta);
                candidatos.add(new CandidatoRuta(rutaDirecta, puntuacion));
            }

            // Rutas con 1 escala CON VALIDACIÓN
            List<List<Vuelo>> rutasConEscala = buscarRutasConEscalaDesdeOrigenConCapacidad(
                    codigoFabrica, destinoCodigo, pedido);
            for (List<Vuelo> ruta : rutasConEscala) {
                double puntuacion = calcularPuntuacionRuta(ruta);
                candidatos.add(new CandidatoRuta(ruta, puntuacion));
            }
        }

        if (candidatos.isEmpty()) {
            return new ArrayList<>();
        }

        // Seleccionar usando estrategia GRASP
        return seleccionarCandidatoGRASP(candidatos);
    }

    /**
     * ✅ NUEVO: Busca ruta directa validando capacidad
     */
    private List<Vuelo> buscarRutaDirectaDesdeOrigenConCapacidad(String origen, String destino, Pedido pedido) {
        List<Vuelo> vuelosDirectos = vuelosPorOrigen.getOrDefault(origen, new ArrayList<>())
                .stream()
                .filter(v -> v.getDestino().getCodigo().equals(destino))
                .filter(v -> v.getHoraSalida().isAfter(pedido.getFechaRegistro()))
                .filter(v -> tieneCapacidadDisponible(v, pedido.getCantidad())) // ✅ NUEVO
                .sorted(Comparator.comparing(Vuelo::getHoraSalida))
                .collect(Collectors.toList());

        if (!vuelosDirectos.isEmpty()) {
            return Arrays.asList(vuelosDirectos.get(0));
        }

        return new ArrayList<>();
    }

    /**
     * ✅ NUEVO: Busca rutas con escala validando capacidad
     */
    private List<List<Vuelo>> buscarRutasConEscalaDesdeOrigenConCapacidad(String origen, String destino, Pedido pedido) {
        List<List<Vuelo>> rutasEncontradas = new ArrayList<>();

        // Buscar vuelos desde la fábrica
        List<Vuelo> vuelosDesdeOrigen = vuelosPorOrigen.getOrDefault(origen, new ArrayList<>())
                .stream()
                .filter(v -> v.getHoraSalida().isAfter(pedido.getFechaRegistro()))
                .filter(v -> !v.getDestino().getCodigo().equals(destino)) // No directo
                .filter(v -> tieneCapacidadDisponible(v, pedido.getCantidad())) // ✅ NUEVO
                .sorted(Comparator.comparing(Vuelo::getHoraSalida))
                .limit(5)
                .collect(Collectors.toList());

        for (Vuelo primerVuelo : vuelosDesdeOrigen) {
            String aeropuertoEscala = primerVuelo.getDestino().getCodigo();

            List<Vuelo> vuelosDesdeEscala = vuelosPorOrigen.getOrDefault(aeropuertoEscala, new ArrayList<>())
                    .stream()
                    .filter(v -> v.getDestino().getCodigo().equals(destino))
                    .filter(v -> v.getHoraSalida().isAfter(primerVuelo.getHoraLlegada().plusHours(1)))
                    .filter(v -> tieneCapacidadDisponible(v, pedido.getCantidad())) // ✅ NUEVO
                    .sorted(Comparator.comparing(Vuelo::getHoraSalida))
                    .limit(3)
                    .collect(Collectors.toList());

            for (Vuelo segundoVuelo : vuelosDesdeEscala) {
                //  VERIFICAR QUE AMBOS VUELOS TENGAN CAPACIDAD
                if (rutaTieneCapacidadCompleta(Arrays.asList(primerVuelo, segundoVuelo), pedido.getCantidad())) {
                    rutasEncontradas.add(Arrays.asList(primerVuelo, segundoVuelo));
                }
            }
        }

        return rutasEncontradas;
    }

    /**
     * ✅ NUEVO: Verifica si un vuelo tiene capacidad disponible
     */
    private boolean tieneCapacidadDisponible(Vuelo vuelo, int cantidadRequerida) {
        int ocupacionActualVuelo = ocupacionActual.getOrDefault(vuelo, 0);
        int capacidadDisponible = vuelo.getCapacidadMaxima() - ocupacionActualVuelo;

        boolean tienCapacidad = capacidadDisponible >= cantidadRequerida;

        // Log para debugging (opcional, quitar en producción)
        /*if (!tienCapacidad) {
            System.out.printf("⚠ Vuelo %s sin capacidad: %d/%d usado, necesita %d%n",
                    vuelo.getId(), ocupacionActualVuelo, vuelo.getCapacidadMaxima(), cantidadRequerida);
        }*/

        return tienCapacidad;
    }

    /**
     * ✅ NUEVO: Verifica que toda una ruta tenga capacidad
     */
    // ✅ MODIFICADO: Verifica capacidad de toda una ruta (vuelos + almacenes)
    private boolean rutaTieneCapacidadCompleta(List<Vuelo> ruta, int cantidadRequerida) {
        // 1. Verificar capacidad de vuelos (ya existe)
        for (Vuelo vuelo : ruta) {
            if (!tieneCapacidadDisponible(vuelo, cantidadRequerida)) {
                return false;
            }
        }

        // 2. ✅ NUEVO: Verificar capacidad de almacenes intermedios (escalas)
        for (int i = 0; i < ruta.size() - 1; i++) {
            Aeropuerto almacenIntermedio = ruta.get(i).getDestino();
            if (!almacenTieneCapacidad(almacenIntermedio, cantidadRequerida)) {
                return false;
            }
        }

        // 3. ✅ NUEVO: Verificar capacidad del almacén de destino final
        if (!ruta.isEmpty()) {
            Aeropuerto almacenFinal = ruta.get(ruta.size() - 1).getDestino();
            if (!almacenTieneCapacidad(almacenFinal, cantidadRequerida)) {
                return false;
            }
        }

        return true;
    }

    private void actualizarOcupacionAlmacenes(List<Vuelo> ruta, int cantidadPaquetes) {
        // Actualizar almacenes intermedios (escalas)
        for (int i = 0; i < ruta.size() - 1; i++) {
            String codigoAlmacen = ruta.get(i).getDestino().getCodigo();
            ocupacionAlmacenes.merge(codigoAlmacen, cantidadPaquetes, Integer::sum);
        }

        // Actualizar almacén de destino final
        if (!ruta.isEmpty()) {
            String codigoDestino = ruta.get(ruta.size() - 1).getDestino().getCodigo();
            ocupacionAlmacenes.merge(codigoDestino, cantidadPaquetes, Integer::sum);
        }
    }

    /**
     * ✅ NUEVO: Actualiza la ocupación después de asignar una ruta
     */
    private void actualizarOcupacion(List<Vuelo> ruta, int cantidadPaquetes) {
        for (Vuelo vuelo : ruta) {
            int ocupacionAnterior = ocupacionActual.getOrDefault(vuelo, 0);
            ocupacionActual.put(vuelo, ocupacionAnterior + cantidadPaquetes);
        }
    }

    /**
     * ✅ NUEVO: Método para debug - mostrar estado de ocupación
     */
    public void mostrarEstadoOcupacion() {
        System.out.println("📊 ESTADO DE OCUPACIÓN DE VUELOS:");
        ocupacionActual.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((v1, v2) -> v1.getId().compareTo(v2.getId())))
                .forEach(entry -> {
                    Vuelo vuelo = entry.getKey();
                    int usado = entry.getValue();
                    double porcentaje = (double) usado / vuelo.getCapacidadMaxima() * 100;

                    System.out.printf("   %s: %d/%d (%.1f%%) %s%n",
                            vuelo.getId(), usado, vuelo.getCapacidadMaxima(), porcentaje,
                            porcentaje > 100 ? "❌ SOBRECARGA" :
                                    porcentaje > 80 ? "⚠ ALTO" : "✅");
                });
    }

    /**
     * ✅ NUEVO: Obtener estadísticas de ocupación
     */
    public Map<String, Integer> obtenerEstadisticasOcupacion() {
        Map<String, Integer> stats = new HashMap<>();
        int vuelosUsados = ocupacionActual.size();
        int vuelosSobrecargados = 0;
        int ocupacionTotal = 0;
        int capacidadTotal = 0;

        for (Map.Entry<Vuelo, Integer> entry : ocupacionActual.entrySet()) {
            Vuelo vuelo = entry.getKey();
            int usado = entry.getValue();

            if (usado > vuelo.getCapacidadMaxima()) {
                vuelosSobrecargados++;
            }

            ocupacionTotal += usado;
            capacidadTotal += vuelo.getCapacidadMaxima();
        }

        stats.put("vuelosUsados", vuelosUsados);
        stats.put("vuelosSobrecargados", vuelosSobrecargados);
        stats.put("ocupacionTotal", ocupacionTotal);
        stats.put("capacidadTotal", capacidadTotal);
        stats.put("eficienciaPromedio", capacidadTotal > 0 ? (ocupacionTotal * 100 / capacidadTotal) : 0);

        return stats;
    }

    public Map<String, Object> obtenerEstadisticasTemporales() {
        Map<String, Object> stats = new HashMap<>();

        // Estadísticas del sistema temporal
        int almacenesUsados = historialAlmacenes.size();
        int eventosTotal = historialAlmacenes.values().stream()
                .mapToInt(List::size)
                .sum();

        // Estadísticas del sistema estático (compatibilidad)
        int almacenesEstaticos = ocupacionAlmacenes.size();
        int paquetesTotalesEstaticos = ocupacionAlmacenes.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        int almacenesConSobrecarga = 0;
        int eventosConSobrecarga = 0;

        for (Map.Entry<String, List<EventoAlmacen>> entry : historialAlmacenes.entrySet()) {
            String codigoAlmacen = entry.getKey();
            List<EventoAlmacen> eventos = entry.getValue();
            Aeropuerto almacen = aeropuertoMap.get(codigoAlmacen);

            if (almacen != null) {
                boolean tieneSobrecarga = eventos.stream()
                        .anyMatch(evento -> {
                            int ocupacionEnMomento = almacen.getCapacidadAct() +
                                    eventos.stream()
                                            .filter(e -> e.estaOcupado(evento.fechaLlegada))
                                            .mapToInt(e -> e.cantidadPaquetes)
                                            .sum();
                            return ocupacionEnMomento > almacen.getCapacidad();
                        });

                if (tieneSobrecarga) {
                    almacenesConSobrecarga++;
                    eventosConSobrecarga += eventos.size();
                }
            }
        }

        // Combinar estadísticas
        stats.put("almacenesUsadosTemporales", almacenesUsados);
        stats.put("almacenesUsadosEstaticos", almacenesEstaticos);
        stats.put("eventosTemporalesTotal", eventosTotal);
        stats.put("paquetesTotalesEstaticos", paquetesTotalesEstaticos);
        stats.put("almacenesConSobrecarga", almacenesConSobrecarga);
        stats.put("eventosConSobrecarga", eventosConSobrecarga);
        stats.put("horasLiberacion", HORAS_LIBERACION);
        stats.put("sistemaActivo", "TEMPORAL + ESTÁTICO (compatibilidad)");

        return stats;
    }
    // ===== MÉTODOS EXISTENTES SIN CAMBIOS =====

    /**
     * Calcula puntuación mejorada para una ruta
     */
    private double calcularPuntuacionRuta(List<Vuelo> ruta) {
        if (ruta.isEmpty()) return 0.0;

        double puntuacion = 100.0;

        // Factor 1: Preferir rutas directas (menos escalas)
        if (ruta.size() == 1) {
            puntuacion += 50; // Bonus grande por ruta directa
        } else {
            puntuacion -= (ruta.size() - 1) * 25; // Penalización por escalas
        }

        // Factor 2: Premiar capacidad alta
        double capacidadPromedio = ruta.stream()
                .mapToInt(Vuelo::getCapacidadMaxima)
                .average()
                .orElse(200);

        if (capacidadPromedio > 350) {
            puntuacion += 20;
        } else if (capacidadPromedio > 300) {
            puntuacion += 10;
        }

        // Factor 3: Penalizar ligeramente rutas internacionales
        boolean tieneVueloInternacional = ruta.stream()
                .anyMatch(Vuelo::getEsInternacional);
        if (tieneVueloInternacional) {
            puntuacion -= 5;
        }

        // Factor 4: Premiar salidas tempranas
        Vuelo primerVuelo = ruta.get(0);
        int horaSalida = primerVuelo.getHoraSalida().getHour();
        if (horaSalida >= 6 && horaSalida <= 12) {
            puntuacion += 15; // Horarios matutinos
        }

        // ✅ NUEVO: Factor 5: Premiar vuelos con buena capacidad disponible
        double capacidadDisponiblePromedio = ruta.stream()
                .mapToDouble(vuelo -> {
                    int ocupado = ocupacionActual.getOrDefault(vuelo, 0);
                    return (double)(vuelo.getCapacidadMaxima() - ocupado) / vuelo.getCapacidadMaxima();
                })
                .average()
                .orElse(0.5);

        puntuacion += capacidadDisponiblePromedio * 10; // Hasta 10 puntos por capacidad libre

        return Math.max(10, puntuacion);
    }

    /**
     * Selecciona candidato usando estrategia GRASP
     */
    private List<Vuelo> seleccionarCandidatoGRASP(List<CandidatoRuta> candidatos) {
        // Ordenar por puntuación descendente
        candidatos.sort((c1, c2) -> Double.compare(c2.puntuacion, c1.puntuacion));

        if (candidatos.size() == 1) {
            return candidatos.get(0).ruta;
        }

        // Crear Lista de Candidatos Restringida (RCL)
        double mejorPuntuacion = candidatos.get(0).puntuacion;
        double peorPuntuacion = candidatos.get(candidatos.size() - 1).puntuacion;
        double umbral = peorPuntuacion + alfa * (mejorPuntuacion - peorPuntuacion);

        List<CandidatoRuta> rcl = candidatos.stream()
                .filter(c -> c.puntuacion >= umbral)
                .collect(Collectors.toList());

        // Selección aleatoria de la RCL
        CandidatoRuta seleccionado = rcl.get(random.nextInt(rcl.size()));
        return seleccionado.ruta;
    }

    /**
     * Configura los datos de la ruta
     */
    private void configurarRuta(RutaPedido ruta, List<Vuelo> vuelos) {
        if (!vuelos.isEmpty()) {
            ruta.setFechaSalida(vuelos.get(0).getHoraSalida());
            ruta.setFechaLlegada(vuelos.get(vuelos.size() - 1).getHoraLlegada());

            boolean esInternacional = vuelos.stream().anyMatch(Vuelo::getEsInternacional);
            ruta.setEsInternacional(esInternacional);
        }
    }

    /**
     * Inicializa la estructura de vuelos por origen
     */
    private void inicializarVuelos() {
        for (Vuelo vuelo : vuelos) {
            vuelosPorOrigen.computeIfAbsent(vuelo.getOrigen().getCodigo(), k -> new ArrayList<>()).add(vuelo);
        }
/*
        System.out.println("Vuelos indexados por origen:");
        for (String fabrica : Solucion.FABRICAS) {
            int cantidadVuelos = vuelosPorOrigen.getOrDefault(fabrica, new ArrayList<>()).size();
            System.out.println("- " + fabrica + ": " + cantidadVuelos + " vuelos");
        }*/
    }

    // Setters para configuración
    public void setAlfa(double alfa) {
        this.alfa = Math.max(0, Math.min(1, alfa));
    }

    /**
     * Clase interna para candidatos de ruta
     */
    private static class CandidatoRuta {
        List<Vuelo> ruta;
        double puntuacion;

        CandidatoRuta(List<Vuelo> ruta, double puntuacion) {
            this.ruta = ruta;
            this.puntuacion = puntuacion;
        }
    }
}