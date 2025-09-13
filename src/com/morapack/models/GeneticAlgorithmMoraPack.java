package com.morapack.models;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Algoritmo Genético para optimización de rutas logísticas en MoraPack
 * Trabaja en conjunto con GRASP para mejorar las soluciones iniciales
 */
public class GeneticAlgorithmMoraPack {

    // Parámetros del algoritmo genético
    private int tamañoPoblacion;
    private int numeroGeneraciones;
    private double tasaMutacion;
    private double tasaCruzamiento;
    private int torneo; // Tamaño del torneo para selección
    private List<Solucion> semillasIniciales;

    // Datos del problema
    private List<Pedido> pedidos;
    private List<Vuelo> vuelos;
    private Map<String, List<Vuelo>> vuelosPorOrigen;
    private GraspMoraPack grasp;
    private Random random;

    // Estadísticas
    private List<Double> historialFitness;
    private Solucion mejorSolucionGlobal;

    public GeneticAlgorithmMoraPack(List<Pedido> pedidos, List<Vuelo> vuelos) {
        this.pedidos = new ArrayList<>(pedidos);
        this.vuelos = new ArrayList<>(vuelos);
        this.grasp = new GraspMoraPack(pedidos, vuelos);
        this.random = new Random();
        this.historialFitness = new ArrayList<>();

        // Parámetros por defecto
        this.tamañoPoblacion = 50;
        this.numeroGeneraciones = 100;
        this.tasaMutacion = 0.15;
        this.tasaCruzamiento = 0.8;
        this.torneo = 5;

        inicializarVuelosPorOrigen();
    }

    /**
     * Ejecuta el algoritmo genético completo
     */
    public Solucion ejecutar() {
        System.out.println("=== INICIANDO ALGORITMO GENÉTICO ===");
        System.out.printf("Parámetros: Población=%d, Generaciones=%d, Mutación=%.2f, Cruzamiento=%.2f%n",
                tamañoPoblacion, numeroGeneraciones, tasaMutacion, tasaCruzamiento);

        // Paso 1: Generar población inicial
        List<Individuo> poblacion = generarPoblacionInicial();
        evaluarPoblacion(poblacion);

        // Inicializar mejor solución
        mejorSolucionGlobal = encontrarMejorIndividuo(poblacion).solucion;
        historialFitness.add(mejorSolucionGlobal.getFitness());

        System.out.printf("Generación 0: Mejor fitness = %.2f%n", mejorSolucionGlobal.getFitness());

        // Paso 2: Evolución por generaciones
        for (int generacion = 1; generacion <= numeroGeneraciones; generacion++) {
            List<Individuo> nuevaPoblacion = new ArrayList<>();

            // Elitismo: preservar los mejores individuos
            List<Individuo> elite = seleccionarElite(poblacion, (int)(tamañoPoblacion * 0.1));
            nuevaPoblacion.addAll(elite);

            // Generar resto de la población
            while (nuevaPoblacion.size() < tamañoPoblacion) {
                // Selección por torneo
                Individuo padre1 = seleccionPorTorneo(poblacion);
                Individuo padre2 = seleccionPorTorneo(poblacion);

                // Cruzamiento
                if (random.nextDouble() < tasaCruzamiento) {
                    List<Individuo> hijos = cruzamiento(padre1, padre2);
                    nuevaPoblacion.addAll(hijos);
                } else {
                    nuevaPoblacion.add(copiarIndividuo(padre1));
                    if (nuevaPoblacion.size() < tamañoPoblacion) {
                        nuevaPoblacion.add(copiarIndividuo(padre2));
                    }
                }
            }

            // Ajustar tamaño si es necesario
            while (nuevaPoblacion.size() > tamañoPoblacion) {
                nuevaPoblacion.remove(nuevaPoblacion.size() - 1);
            }

            // Mutación
            for (int i = elite.size(); i < nuevaPoblacion.size(); i++) {
                if (random.nextDouble() < tasaMutacion) {
                    mutar(nuevaPoblacion.get(i));
                }
            }

            // Evaluar nueva población
            evaluarPoblacion(nuevaPoblacion);
            poblacion = nuevaPoblacion;

            // Actualizar mejor solución global
            Individuo mejorActual = encontrarMejorIndividuo(poblacion);
            if (mejorActual.fitness > mejorSolucionGlobal.getFitness()) {
                mejorSolucionGlobal = mejorActual.solucion;
                System.out.printf("¡Nueva mejor solución en generación %d! Fitness = %.2f%n",
                        generacion, mejorSolucionGlobal.getFitness());
            }

            historialFitness.add(mejorSolucionGlobal.getFitness());

            // Log cada 10 generaciones
            if (generacion % 10 == 0) {
                double fitnessPromedio = poblacion.stream()
                        .mapToDouble(ind -> ind.fitness)
                        .average().orElse(0.0);
                System.out.printf("Generación %d: Mejor=%.2f, Promedio=%.2f%n",
                        generacion, mejorActual.fitness, fitnessPromedio);
            }
        }

        System.out.println("=== ALGORITMO GENÉTICO COMPLETADO ===");
        System.out.printf("Mejor fitness final: %.2f%n", mejorSolucionGlobal.getFitness());

        return mejorSolucionGlobal;
    }

    /**
     * Genera población inicial usando GRASP y variaciones aleatorias
     */
    private List<Individuo> generarPoblacionInicial() {
        List<Individuo> poblacion = new ArrayList<>();

        // 30% de la población usando GRASP con diferentes valores de alfa
        double[] alphasGrasp = {0.0, 0.2, 0.5, 0.8, 1.0};
        int individuosGrasp = (int)(tamañoPoblacion * 0.3);

        for (int i = 0; i < individuosGrasp; i++) {
            double alfa = alphasGrasp[i % alphasGrasp.length];
            grasp.setAlfa(alfa);
            Solucion solucion = grasp.generarSolucion();
            if (solucion != null) {
                poblacion.add(new Individuo(solucion));
            }
        }

        // 70% restante usando generación aleatoria mejorada
        while (poblacion.size() < tamañoPoblacion) {
            Solucion solucionAleatoria = generarSolucionAleatoria();
            if (solucionAleatoria != null) {
                poblacion.add(new Individuo(solucionAleatoria));
            }
        }

        return poblacion;
    }

    /**
     * Genera una solución completamente aleatoria pero válida
     */
    private Solucion generarSolucionAleatoria() {
        SolucionLogistica solucionLogistica = new SolucionLogistica();
        solucionLogistica.setAsignacionPedidos(new HashMap<>());

        List<Pedido> pedidosAleatorios = new ArrayList<>(pedidos);
        Collections.shuffle(pedidosAleatorios, random);

        for (Pedido pedido : pedidosAleatorios) {
            List<Vuelo> ruta = generarRutaAleatoria(pedido);
            if (!ruta.isEmpty()) {
                RutaPedido rutaPedido = new RutaPedido(pedido, ruta);
                configurarRuta(rutaPedido, ruta);
                solucionLogistica.agregarRutaPedido(pedido, rutaPedido);
            }
        }

        return new Solucion(solucionLogistica, pedidos.size());
    }

    /**
     * Genera una ruta aleatoria válida para un pedido
     */
    private List<Vuelo> generarRutaAleatoria(Pedido pedido) {
        String destino = pedido.getLugarDestino().getCodigo();
        List<String> fabricas = Solucion.FABRICAS; // CAMBIO: Usar la constante centralizada

        // Seleccionar fábrica aleatoria
        String fabricaOrigen = fabricas.get(random.nextInt(fabricas.size()));

        // Intentar ruta directa primero (50% probabilidad)
        if (random.nextDouble() < 0.5) {
            List<Vuelo> rutaDirecta = buscarVueloDirecto(fabricaOrigen, destino);
            if (!rutaDirecta.isEmpty()) {
                return rutaDirecta;
            }
        }

        // Si no hay ruta directa, buscar con escala
        return buscarRutaConEscalaAleatoria(fabricaOrigen, destino);
    }

    /**
     * Operador de cruzamiento: Order Crossover (OX) adaptado
     */
    private List<Individuo> cruzamiento(Individuo padre1, Individuo padre2) {
        Map<Pedido, RutaPedido> rutas1 = padre1.solucion.getSolucionLogistica().getAsignacionPedidos();
        Map<Pedido, RutaPedido> rutas2 = padre2.solucion.getSolucionLogistica().getAsignacionPedidos();

        SolucionLogistica hijo1 = new SolucionLogistica();
        SolucionLogistica hijo2 = new SolucionLogistica();
        hijo1.setAsignacionPedidos(new HashMap<>());
        hijo2.setAsignacionPedidos(new HashMap<>());

        List<Pedido> pedidosComunes = rutas1.keySet().stream()
                .filter(rutas2::containsKey)
                .collect(Collectors.toList());

        if (pedidosComunes.isEmpty()) {
            return Arrays.asList(copiarIndividuo(padre1), copiarIndividuo(padre2));
        }

        // Punto de corte aleatorio
        int puntoCorte = random.nextInt(pedidosComunes.size());

        // Hijo 1: primera parte del padre 1, segunda parte del padre 2
        for (int i = 0; i < pedidosComunes.size(); i++) {
            Pedido pedido = pedidosComunes.get(i);
            if (i < puntoCorte) {
                hijo1.agregarRutaPedido(pedido, copiarRuta(rutas1.get(pedido)));
            } else {
                hijo1.agregarRutaPedido(pedido, copiarRuta(rutas2.get(pedido)));
            }
        }

        // Hijo 2: primera parte del padre 2, segunda parte del padre 1
        for (int i = 0; i < pedidosComunes.size(); i++) {
            Pedido pedido = pedidosComunes.get(i);
            if (i < puntoCorte) {
                hijo2.agregarRutaPedido(pedido, copiarRuta(rutas2.get(pedido)));
            } else {
                hijo2.agregarRutaPedido(pedido, copiarRuta(rutas1.get(pedido)));
            }
        }

        // CAMBIO: Pasar total de pedidos a los nuevos individuos
        return Arrays.asList(
                new Individuo(new Solucion(hijo1, pedidos.size())),
                new Individuo(new Solucion(hijo2, pedidos.size()))
        );
    }

    /**
     * Operador de mutación: cambia rutas de pedidos aleatorios
     */
    private void mutar(Individuo individuo) {
        Map<Pedido, RutaPedido> rutas = individuo.solucion.getSolucionLogistica().getAsignacionPedidos();

        if (rutas.isEmpty()) return;

        // Mutar entre 1 y 3 pedidos aleatorios
        int numMutaciones = 1 + random.nextInt(Math.min(3, rutas.size()));
        List<Pedido> pedidosParaMutar = new ArrayList<>(rutas.keySet());
        Collections.shuffle(pedidosParaMutar, random);

        for (int i = 0; i < numMutaciones; i++) {
            Pedido pedido = pedidosParaMutar.get(i);

            // Tipo de mutación aleatoria
            double tipoMutacion = random.nextDouble();

            if (tipoMutacion < 0.4) {
                // Cambiar fábrica de origen
                mutarCambiarFabrica(pedido, rutas);
            } else if (tipoMutacion < 0.7) {
                // Cambiar a ruta directa/con escala
                mutarCambiarTipoRuta(pedido, rutas);
            } else {
                // Regenerar ruta completamente
                mutarRegenerarRuta(pedido, rutas);
            }
        }

        // Recalcular fitness
        // CAMBIO: Recalcular fitness con el total correcto de pedidos
        individuo.solucion = new Solucion(individuo.solucion.getSolucionLogistica(), pedidos.size());
        individuo.fitness = individuo.solucion.getFitness();
    }

    /**
     * Mutación: cambiar fábrica de origen
     */
    private void mutarCambiarFabrica(Pedido pedido, Map<Pedido, RutaPedido> rutas) {
        List<String> fabricas = Solucion.FABRICAS; // CAMBIO: Usar constante centralizada
        String nuevaFabrica = fabricas.get(random.nextInt(fabricas.size()));
        String destino = pedido.getLugarDestino().getCodigo();

        // Intentar ruta directa primero
        List<Vuelo> nuevaRuta = buscarVueloDirecto(nuevaFabrica, destino);
        if (nuevaRuta.isEmpty()) {
            nuevaRuta = buscarRutaConEscalaAleatoria(nuevaFabrica, destino);
        }

        if (!nuevaRuta.isEmpty()) {
            RutaPedido nuevaRutaPedido = new RutaPedido(pedido, nuevaRuta);
            configurarRuta(nuevaRutaPedido, nuevaRuta);
            rutas.put(pedido, nuevaRutaPedido);
        }
    }

    /**
     * Mutación: cambiar tipo de ruta (directa ↔ con escala)
     */
    private void mutarCambiarTipoRuta(Pedido pedido, Map<Pedido, RutaPedido> rutas) {
        RutaPedido rutaActual = rutas.get(pedido);
        if (rutaActual == null || rutaActual.getSecuenciaVuelos().isEmpty()) return;

        String fabricaActual = rutaActual.getSecuenciaVuelos().get(0).getOrigen().getCodigo();
        String destino = pedido.getLugarDestino().getCodigo();

        List<Vuelo> nuevaRuta;
        if (rutaActual.getSecuenciaVuelos().size() == 1) {
            // Actual es directa, cambiar a con escala
            nuevaRuta = buscarRutaConEscalaAleatoria(fabricaActual, destino);
        } else {
            // Actual tiene escalas, cambiar a directa
            nuevaRuta = buscarVueloDirecto(fabricaActual, destino);
        }

        if (!nuevaRuta.isEmpty()) {
            RutaPedido nuevaRutaPedido = new RutaPedido(pedido, nuevaRuta);
            configurarRuta(nuevaRutaPedido, nuevaRuta);
            rutas.put(pedido, nuevaRutaPedido);
        }
    }

    /**
     * Mutación: regenerar ruta completamente
     */
    private void mutarRegenerarRuta(Pedido pedido, Map<Pedido, RutaPedido> rutas) {
        List<Vuelo> nuevaRuta = generarRutaAleatoria(pedido);
        if (!nuevaRuta.isEmpty()) {
            RutaPedido nuevaRutaPedido = new RutaPedido(pedido, nuevaRuta);
            configurarRuta(nuevaRutaPedido, nuevaRuta);
            rutas.put(pedido, nuevaRutaPedido);
        }
    }

    /**
     * Selección por torneo
     */
    private Individuo seleccionPorTorneo(List<Individuo> poblacion) {
        List<Individuo> candidatos = new ArrayList<>();
        for (int i = 0; i < torneo; i++) {
            candidatos.add(poblacion.get(random.nextInt(poblacion.size())));
        }
        return candidatos.stream()
                .max(Comparator.comparingDouble(ind -> ind.fitness))
                .orElse(candidatos.get(0));
    }

    /**
     * Selecciona los mejores individuos (elitismo)
     */
    private List<Individuo> seleccionarElite(List<Individuo> poblacion, int cantidad) {
        return poblacion.stream()
                .sorted((a, b) -> Double.compare(b.fitness, a.fitness))
                .limit(cantidad)
                .map(this::copiarIndividuo)
                .collect(Collectors.toList());
    }

    /**
     * Evalúa toda la población
     */
    private void evaluarPoblacion(List<Individuo> poblacion) {
        for (Individuo individuo : poblacion) {
            if (individuo.fitness == 0.0) {
                individuo.fitness = individuo.solucion.getFitness();
            }
        }
    }

    /**
     * Encuentra el mejor individuo de la población
     */
    private Individuo encontrarMejorIndividuo(List<Individuo> poblacion) {
        return poblacion.stream()
                .max(Comparator.comparingDouble(ind -> ind.fitness))
                .orElse(poblacion.get(0));
    }

    // Métodos auxiliares
    private void inicializarVuelosPorOrigen() {
        vuelosPorOrigen = new HashMap<>();
        for (Vuelo vuelo : vuelos) {
            vuelosPorOrigen.computeIfAbsent(vuelo.getOrigen().getCodigo(), k -> new ArrayList<>()).add(vuelo);
        }
    }

    private List<Vuelo> buscarVueloDirecto(String origen, String destino) {
        return vuelosPorOrigen.getOrDefault(origen, new ArrayList<>())
                .stream()
                .filter(v -> v.getDestino().getCodigo().equals(destino))
                .filter(v -> v.getHoraSalida().isAfter(LocalDateTime.now()))
                .limit(1)
                .collect(Collectors.toList());
    }

    private List<Vuelo> buscarRutaConEscalaAleatoria(String origen, String destino) {
        List<Vuelo> vuelosDesdeOrigen = vuelosPorOrigen.getOrDefault(origen, new ArrayList<>())
                .stream()
                .filter(v -> !v.getDestino().getCodigo().equals(destino))
                .filter(v -> v.getHoraSalida().isAfter(LocalDateTime.now()))
                .collect(Collectors.toList());

        Collections.shuffle(vuelosDesdeOrigen, random);

        for (Vuelo primerVuelo : vuelosDesdeOrigen.stream().limit(3).collect(Collectors.toList())) {
            String escala = primerVuelo.getDestino().getCodigo();
            List<Vuelo> segundoVuelos = vuelosPorOrigen.getOrDefault(escala, new ArrayList<>())
                    .stream()
                    .filter(v -> v.getDestino().getCodigo().equals(destino))
                    .filter(v -> v.getHoraSalida().isAfter(primerVuelo.getHoraLlegada().plusHours(1)))
                    .limit(1)
                    .collect(Collectors.toList());

            if (!segundoVuelos.isEmpty()) {
                return Arrays.asList(primerVuelo, segundoVuelos.get(0));
            }
        }

        return new ArrayList<>();
    }

    private void configurarRuta(RutaPedido ruta, List<Vuelo> vuelos) {
        if (!vuelos.isEmpty()) {
            ruta.setFechaSalida(vuelos.get(0).getHoraSalida());
            ruta.setFechaLlegada(vuelos.get(vuelos.size() - 1).getHoraLlegada());
            boolean esInternacional = vuelos.stream().anyMatch(Vuelo::getEsInternacional);
            ruta.setEsInternacional(esInternacional);
        }
    }

    private Individuo copiarIndividuo(Individuo original) {
        Map<Pedido, RutaPedido> rutasOriginales = original.solucion.getSolucionLogistica().getAsignacionPedidos();
        SolucionLogistica nuevaSolucion = new SolucionLogistica();
        nuevaSolucion.setAsignacionPedidos(new HashMap<>());

        for (Map.Entry<Pedido, RutaPedido> entry : rutasOriginales.entrySet()) {
            nuevaSolucion.agregarRutaPedido(entry.getKey(), copiarRuta(entry.getValue()));
        }

        return new Individuo(new Solucion(nuevaSolucion, pedidos.size()));
    }

    private RutaPedido copiarRuta(RutaPedido original) {
        return new RutaPedido(original.getPedido(), new ArrayList<>(original.getSecuenciaVuelos()));
    }

    // Getters y setters para parámetros
    public void setTamañoPoblacion(int tamañoPoblacion) {
        this.tamañoPoblacion = Math.max(10, tamañoPoblacion);
    }

    public int getTamañoPoblacion() {
        return tamañoPoblacion;
    }

    public void setNumeroGeneraciones(int numeroGeneraciones) {
        this.numeroGeneraciones = Math.max(1, numeroGeneraciones);
    }

    public int getNumeroGeneraciones() {
        return numeroGeneraciones;
    }

    public void setTasaMutacion(double tasaMutacion) {
        this.tasaMutacion = Math.max(0.0, Math.min(1.0, tasaMutacion));
    }

    public void setTasaCruzamiento(double tasaCruzamiento) {
        this.tasaCruzamiento = Math.max(0.0, Math.min(1.0, tasaCruzamiento));
    }

    public void setSemillasIniciales(List<Solucion> semillas) {
        this.semillasIniciales = new ArrayList<>(semillas);
    }

    public List<Double> getHistorialFitness() {
        return new ArrayList<>(historialFitness);
    }

    public Solucion getMejorSolucionGlobal() {
        return mejorSolucionGlobal;
    }

    /**
     * Clase interna para representar individuos en la población
     */
    private static class Individuo {
        Solucion solucion;
        double fitness;

        Individuo(Solucion solucion) {
            this.solucion = solucion;
            this.fitness = solucion.getFitness();
        }
    }
}