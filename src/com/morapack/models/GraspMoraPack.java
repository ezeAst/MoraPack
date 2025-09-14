package com.morapack.models;

import com.morapack.models.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementación mejorada del algoritmo GRASP para MoraPack
 * Enfoque directo: construye rutas desde fábricas hacia destinos
 */
public class GraspMoraPack {

    private List<Pedido> pedidos;
    private List<Vuelo> vuelos;
    private Map<String, List<Vuelo>> vuelosPorOrigen;
    private Random random;
    private double alfa;

    // Fábricas válidas del sistema

    public GraspMoraPack(List<Pedido> pedidos, List<Vuelo> vuelos) {
        this.pedidos = new ArrayList<>(pedidos);
        this.vuelos = new ArrayList<>(vuelos);
        this.random = new Random();
        this.vuelosPorOrigen = new HashMap<>();

        inicializarVuelos();
    }

    /**
     * Genera una solución usando GRASP mejorado
     */
    public Solucion generarSolucion() {
        SolucionLogistica solucionLogistica = new SolucionLogistica();
        solucionLogistica.setAsignacionPedidos(new HashMap<>());

        // Ordenar pedidos por fecha límite (más urgentes primero)
        List<Pedido> pedidosOrdenados = pedidos.stream()
                .sorted(Comparator.comparing(Pedido::getFechaLimite))
                .collect(Collectors.toList());

        // Asignar ruta a cada pedido
        for (Pedido pedido : pedidosOrdenados) {
            List<Vuelo> rutaAsignada = buscarMejorRutaParaPedido(pedido);

            if (!rutaAsignada.isEmpty()) {
                RutaPedido ruta = new RutaPedido(pedido, rutaAsignada);
                configurarRuta(ruta, rutaAsignada);
                solucionLogistica.agregarRutaPedido(pedido, ruta);
            }
        }

        // CAMBIO PRINCIPAL: Pasar el total de pedidos al constructor
        return new Solucion(solucionLogistica, pedidos.size());
    }

    /**
     * Busca la mejor ruta para un pedido desde todas las fábricas
     */
    private List<Vuelo> buscarMejorRutaParaPedido(Pedido pedido) {
        String destinoCodigo = pedido.getLugarDestino().getCodigo();
        List<CandidatoRuta> candidatos = new ArrayList<>();

        // Buscar rutas desde cada fábrica
        for (String codigoFabrica : Solucion.FABRICAS) {
            // Rutas directas
            List<Vuelo> rutaDirecta = buscarRutaDirectaDesdeOrigen(codigoFabrica, destinoCodigo, pedido);
            if (!rutaDirecta.isEmpty()) {
                double puntuacion = calcularPuntuacionRuta(rutaDirecta);
                candidatos.add(new CandidatoRuta(rutaDirecta, puntuacion));
            }

            // Rutas con 1 escala
            List<List<Vuelo>> rutasConEscala = buscarRutasConEscalaDesdeOrigen(codigoFabrica, destinoCodigo, pedido);
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
     * Busca ruta directa desde una fábrica específica
     */
    private List<Vuelo> buscarRutaDirectaDesdeOrigen(String origen, String destino, Pedido pedido) {
        List<Vuelo> vuelosDirectos = vuelosPorOrigen.getOrDefault(origen, new ArrayList<>())
                .stream()
                .filter(v -> v.getDestino().getCodigo().equals(destino))
                .filter(v -> v.getHoraSalida().isAfter(pedido.getFechaRegistro()))
                .sorted(Comparator.comparing(Vuelo::getHoraSalida))
                .collect(Collectors.toList());

        if (!vuelosDirectos.isEmpty()) {
            // Tomar el primer vuelo disponible (más temprano)
            return Arrays.asList(vuelosDirectos.get(0));
        }

        return new ArrayList<>();
    }

    /**
     * Busca rutas con una escala desde una fábrica específica
     */
    private List<List<Vuelo>> buscarRutasConEscalaDesdeOrigen(String origen, String destino, Pedido pedido) {
        List<List<Vuelo>> rutasEncontradas = new ArrayList<>();

        // Buscar vuelos desde la fábrica
        List<Vuelo> vuelosDesdeOrigen = vuelosPorOrigen.getOrDefault(origen, new ArrayList<>())
                .stream()
                // ✅ CRÍTICO: Primer vuelo debe salir DESPUÉS del registro del pedido
                .filter(v -> v.getHoraSalida().isAfter(pedido.getFechaRegistro()))
                .filter(v -> !v.getDestino().getCodigo().equals(destino)) // No directo
                .sorted(Comparator.comparing(Vuelo::getHoraSalida))
                .limit(5) // Limitar para eficiencia
                .collect(Collectors.toList());

        for (Vuelo primerVuelo : vuelosDesdeOrigen) {
            String aeropuertoEscala = primerVuelo.getDestino().getCodigo();

            // REVISION
            List<Vuelo> vuelosDesdeEscala = vuelosPorOrigen.getOrDefault(aeropuertoEscala, new ArrayList<>())
                    .stream()
                    .filter(v -> v.getDestino().getCodigo().equals(destino))
                    .filter(v -> v.getHoraSalida().isAfter(primerVuelo.getHoraLlegada().plusHours(1)))
                    .sorted(Comparator.comparing(Vuelo::getHoraSalida))
                    .limit(3) // Máximo 3 opciones de conexión
                    .collect(Collectors.toList());

            for (Vuelo segundoVuelo : vuelosDesdeEscala) {
                rutasEncontradas.add(Arrays.asList(primerVuelo, segundoVuelo));
            }
        }

        return rutasEncontradas;
    }

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

        return Math.max(10, puntuacion); // Mínimo 10 puntos
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