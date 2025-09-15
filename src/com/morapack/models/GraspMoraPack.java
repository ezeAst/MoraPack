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

    // ✅ NUEVO: Tracker de capacidad ocupada por vuelo durante construcción
    private Map<Vuelo, Integer> ocupacionActual;

    public GraspMoraPack(List<Pedido> pedidos, List<Vuelo> vuelos) {
        this.pedidos = new ArrayList<>(pedidos);
        this.vuelos = new ArrayList<>(vuelos);
        this.random = new Random();
        this.vuelosPorOrigen = new HashMap<>();
        this.ocupacionActual = new HashMap<>(); // ✅ NUEVO

        inicializarVuelos();
    }

    /**
     * ✅ CORREGIDO: Genera una solución validando capacidades
     */
    public Solucion generarSolucion() {
        SolucionLogistica solucionLogistica = new SolucionLogistica();
        solucionLogistica.setAsignacionPedidos(new HashMap<>());

        // ✅ NUEVO: Resetear ocupación al inicio de cada construcción
        ocupacionActual.clear();

        // Ordenar pedidos por fecha límite (más urgentes primero)
        List<Pedido> pedidosOrdenados = pedidos;/*.stream()
                .sorted(Comparator.comparing(Pedido::getFechaLimite))
                .collect(Collectors.toList());*/

        int pedidosAsignados = 0;
        int pedidosRechazados = 0;

        // Asignar ruta a cada pedido CON VALIDACIÓN DE CAPACIDAD
        for (Pedido pedido : pedidosOrdenados) {
            List<Vuelo> rutaAsignada = buscarMejorRutaParaPedidoConCapacidad(pedido);

            if (!rutaAsignada.isEmpty()) {
                RutaPedido ruta = new RutaPedido(pedido, rutaAsignada);
                configurarRuta(ruta, rutaAsignada);
                solucionLogistica.agregarRutaPedido(pedido, ruta);

                // ✅ NUEVO: Actualizar ocupación después de asignar
                actualizarOcupacion(rutaAsignada, pedido.getCantidad());
                pedidosAsignados++;
            } else {
                pedidosRechazados++;
            }
        }

        // Log de estadísticas
        System.out.printf("✅ GRASP: %d asignados, %d rechazados por capacidad%n",
                pedidosAsignados, pedidosRechazados);

        return new Solucion(solucionLogistica, pedidos.size());
    }

    /**
     * ✅ NUEVO: Busca ruta validando capacidad disponible
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
                // ✅ VERIFICAR QUE AMBOS VUELOS TENGAN CAPACIDAD
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
        if (!tienCapacidad) {
            System.out.printf("⚠ Vuelo %s sin capacidad: %d/%d usado, necesita %d%n",
                    vuelo.getId(), ocupacionActualVuelo, vuelo.getCapacidadMaxima(), cantidadRequerida);
        }

        return tienCapacidad;
    }

    /**
     * ✅ NUEVO: Verifica que toda una ruta tenga capacidad
     */
    private boolean rutaTieneCapacidadCompleta(List<Vuelo> ruta, int cantidadRequerida) {
        for (Vuelo vuelo : ruta) {
            if (!tieneCapacidadDisponible(vuelo, cantidadRequerida)) {
                return false;
            }
        }
        return true;
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

        System.out.println("Vuelos indexados por origen:");
        for (String fabrica : Solucion.FABRICAS) {
            int cantidadVuelos = vuelosPorOrigen.getOrDefault(fabrica, new ArrayList<>()).size();
            System.out.println("- " + fabrica + ": " + cantidadVuelos + " vuelos");
        }
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