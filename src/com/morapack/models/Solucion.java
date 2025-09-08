package com.morapack.models;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Solucion {
    private SolucionLogistica solucionLogistica;
    private double fitness;

    private static final double PESO_ENTREGA_TIEMPO = 0.45;    // 45% - Prioridad #1
    private static final double PESO_MINIMIZAR_ATRASO = 0.25;  // 25% - Prioridad #2
    private static final double PESO_NO_VIOLAR_CAPACIDAD = 0.15; // 15% - Prioridad #3
    private static final double PESO_APROVECHAR_VUELOS = 0.10;   // 10% - Prioridad #4
    private static final double PESO_EVITAR_RUTAS_MALAS = 0.05;  // 5% - Prioridad #5

    private static final double PENALIZACION_FABRICA_INVALIDA = -5000; // Penalización por no salir de fábrica válida
    private static final List<String> FABRICAS_VALIDAS = Arrays.asList("LIM", "BRU", "BAK"); // Lima, Bruselas, Baku

    // Penalizaciones severas
    private static final double PENALIZACION_SOBRECARGA = -1000;
    private static final double PENALIZACION_ATRASO_SEVERO = -500;

    public Solucion(SolucionLogistica solucionLogistica) {
        this.solucionLogistica = solucionLogistica;
        this.fitness = calcularFitness();
    }

    public SolucionLogistica getSolucionLogistica() {
        return solucionLogistica;
    }

    public void setSolucionLogistica(SolucionLogistica solucionLogistica) {
        this.solucionLogistica = solucionLogistica;
    }

    public double getFitness() {
        return fitness;
    }

    public void setFitness(double fitness) {
        this.fitness = fitness;
    }
    //------------------------------------------------------------------------------------------------------------------


    private double calcularFitness() {
        if (solucionLogistica == null || solucionLogistica.getAsignacionPedidos().isEmpty()) {
            return -10000; // Solución vacía es la peor posible
        }

        // Calcular cada componente
        double puntuacionEntregaTiempo = calcularEntregaATiempo();
        //double puntuacionMinimizarAtraso = calcularMinimizarAtraso();
        double puntuacionMinimizarAtraso = 0;
        double puntuacionCapacidades = calcularRespetarCapacidades();
        double puntuacionAprovechamiento = calcularAprovechamientoVuelos();
        double puntuacionRutas = calcularCalidadRutas();
        double penalizacionFabricas = calcularPenalizacionFabricas();

        // Calcular fitness total
        double fitness = (PESO_ENTREGA_TIEMPO * puntuacionEntregaTiempo) +
                (PESO_MINIMIZAR_ATRASO * puntuacionMinimizarAtraso) +
                (PESO_NO_VIOLAR_CAPACIDAD * puntuacionCapacidades) +
                (PESO_APROVECHAR_VUELOS * puntuacionAprovechamiento) +
                (PESO_EVITAR_RUTAS_MALAS * puntuacionRutas) +
                penalizacionFabricas;

        // Actualizar contadores en SolucionLogistica
        actualizarContadores();

        return fitness;
    }

    // PRIORIDAD #1: Entregar a tiempo (0-100 puntos)
    private double calcularEntregaATiempo() {
        Map<Pedido, RutaPedido> asignaciones = solucionLogistica.getAsignacionPedidos();
        int totalPedidos = asignaciones.size();

        if (totalPedidos == 0) return 0.0;

        int pedidosATiempo = 0;

        for (RutaPedido ruta : asignaciones.values()) {
            if (esPedidoATiempo(ruta)) {
                pedidosATiempo++;
            }
        }

        // Porcentaje de pedidos a tiempo (0-100)
        return (double) pedidosATiempo / totalPedidos * 100.0;
    }

    // PRIORIDAD #2: Minimizar atraso (0-100 puntos, penaliza atrasos severos)
    private double calcularMinimizarAtraso() {
        Map<Pedido, RutaPedido> asignaciones = solucionLogistica.getAsignacionPedidos();
        int totalPedidos = asignaciones.size();

        if (totalPedidos == 0) return 0.0;

        double sumaAtrasos = 0.0;
        int pedidosConAtraso = 0;

        for (RutaPedido ruta : asignaciones.values()) {
            double diasAtraso = calcularDiasAtraso(ruta);
            if (diasAtraso > 0) {
                pedidosConAtraso++;
                sumaAtrasos += diasAtraso;

                // Penalización severa por atrasos mayores a 7 días
                if (diasAtraso > 7) {
                    sumaAtrasos += PENALIZACION_ATRASO_SEVERO;
                }
            }
        }

        if (pedidosConAtraso == 0) return 100.0; // Perfecto, sin atrasos

        // Penalizar proporcionalmente al atraso promedio
        double atrasoPromedio = sumaAtrasos / pedidosConAtraso;
        return Math.max(0, 100 - (atrasoPromedio * 10)); // 10 puntos menos por día de atraso
    }

    // PRIORIDAD #3: No violar capacidades (0-100 puntos, penalización severa)
    private double calcularRespetarCapacidades() {
        Map<String, Integer> ocupacionPorVuelo = contarPedidosPorVuelo();

        if (ocupacionPorVuelo.isEmpty()) return 100.0;

        int violaciones = 0;
        double penalizacionTotal = 0.0;

        for (Map.Entry<String, Integer> entry : ocupacionPorVuelo.entrySet()) {
            String vueloId = entry.getKey();
            int pedidosEnVuelo = entry.getValue();
            int capacidadVuelo = obtenerCapacidadVuelo(vueloId);

            if (pedidosEnVuelo > capacidadVuelo) {
                violaciones++;
                int exceso = pedidosEnVuelo - capacidadVuelo;
                penalizacionTotal += PENALIZACION_SOBRECARGA * exceso;
            }
        }

        if (violaciones == 0) return 100.0; // Perfecto, sin violaciones

        // Penalización severa proporcional a violaciones
        return Math.max(-1000, 100 + penalizacionTotal);
    }

    // PRIORIDAD #4: Aprovechar bien los vuelos (0-100 puntos)
    private double calcularAprovechamientoVuelos() {
        Map<String, Integer> ocupacionPorVuelo = contarPedidosPorVuelo();

        if (ocupacionPorVuelo.isEmpty()) return 0.0;

        double sumaEficiencias = 0.0;
        int vuelosValidos = 0;

        for (Map.Entry<String, Integer> entry : ocupacionPorVuelo.entrySet()) {
            String vueloId = entry.getKey();
            int pedidosEnVuelo = entry.getValue();
            int capacidadVuelo = obtenerCapacidadVuelo(vueloId);

            if (capacidadVuelo > 0 && pedidosEnVuelo <= capacidadVuelo) {
                // Calcular eficiencia de ocupación
                double eficiencia = (double) pedidosEnVuelo / capacidadVuelo * 100.0;

                // Bonus por ocupaciones altas (>80%)
                if (eficiencia > 80) {
                    eficiencia += 10; // Bonus de 10 puntos
                }

                sumaEficiencias += Math.min(100, eficiencia);
                vuelosValidos++;
            }
        }

        return vuelosValidos > 0 ? sumaEficiencias / vuelosValidos : 0.0;
    }

    // PRIORIDAD #5: Evitar rutas malas (0-100 puntos)
    private double calcularCalidadRutas() {
        Map<Pedido, RutaPedido> asignaciones = solucionLogistica.getAsignacionPedidos();

        if (asignaciones.isEmpty()) return 0.0;

        double sumaCalidad = 0.0;

        for (RutaPedido ruta : asignaciones.values()) {
            double calidadRuta = evaluarCalidadRuta(ruta);
            sumaCalidad += calidadRuta;
        }

        return sumaCalidad / asignaciones.size();
    }

    private double evaluarCalidadRuta(RutaPedido ruta) {
        List<Vuelo> vuelos = ruta.getSecuenciaVuelos();

        if (vuelos.isEmpty()) return 0.0;

        double puntuacion = 100.0;

        // Penalizar demasiados saltos (más de 3 vuelos)
        if (vuelos.size() > 3) {
            puntuacion -= (vuelos.size() - 3) * 15; // 15 puntos menos por salto extra
        }

        // Penalizar tiempos muertos largos entre vuelos
        for (int i = 0; i < vuelos.size() - 1; i++) {
            Vuelo vueloActual = vuelos.get(i);
            Vuelo siguienteVuelo = vuelos.get(i + 1);

            if (vueloActual.getHoraLlegada() != null && siguienteVuelo.getHoraSalida() != null) {
                long horasEspera = ChronoUnit.HOURS.between(
                        vueloActual.getHoraLlegada(),
                        siguienteVuelo.getHoraSalida()
                );

                // Penalizar esperas mayores a 12 horas
                if (horasEspera > 12) {
                    puntuacion -= (horasEspera - 12) * 2; // 2 puntos menos por hora extra
                }
            }
        }

        return Math.max(0, puntuacion);
    }

    // Métodos auxiliares
    private boolean esPedidoATiempo(RutaPedido ruta) {
        LocalDateTime fechaSalida = ruta.getFechaSalida();
        LocalDateTime fechaLlegada = ruta.getFechaLlegada();

        if (fechaSalida == null || fechaLlegada == null) {
            return calcularTiempoBasadoEnVuelos(ruta);
        }

        long diasEntrega = ChronoUnit.DAYS.between(fechaLlegada, ruta.getPedido().getFechaLimite());
        return diasEntrega >=0;
    }

    private double calcularDiasAtraso(RutaPedido ruta) {
        LocalDateTime fechaSalida = ruta.getFechaSalida();
        LocalDateTime fechaLlegada = ruta.getFechaLlegada();

        if (fechaSalida == null || fechaLlegada == null) {
            List<Vuelo> vuelos = ruta.getSecuenciaVuelos();
            if (vuelos.isEmpty()) return 0.0;

            Vuelo primerVuelo = vuelos.get(0);
            Vuelo ultimoVuelo = vuelos.get(vuelos.size() - 1);

            if (primerVuelo.getHoraSalida() == null || ultimoVuelo.getHoraLlegada() == null) {
                return 0.0;
            }

            fechaSalida = primerVuelo.getHoraSalida();
            fechaLlegada = ultimoVuelo.getHoraLlegada();
        }

        long diasEntrega = ChronoUnit.DAYS.between(fechaSalida, fechaLlegada);
        return Math.max(0, diasEntrega - 3); // Atraso = días por encima de 3
    }

    private boolean calcularTiempoBasadoEnVuelos(RutaPedido ruta) {
        List<Vuelo> vuelos = ruta.getSecuenciaVuelos();

        if (vuelos.isEmpty()) return false;

        Vuelo primerVuelo = vuelos.get(0);
        Vuelo ultimoVuelo = vuelos.get(vuelos.size() - 1);

        if (primerVuelo.getHoraSalida() == null || ultimoVuelo.getHoraLlegada() == null) {
            return false;
        }

        long diasEntrega = ChronoUnit.DAYS.between(
                primerVuelo.getHoraSalida(),
                ultimoVuelo.getHoraLlegada()
        );

        return diasEntrega <= 3;
    }

    private Map<String, Integer> contarPedidosPorVuelo() {
        Map<String, Integer> conteo = new HashMap<>();

        for (RutaPedido ruta : solucionLogistica.getAsignacionPedidos().values()) {
            for (Vuelo vuelo : ruta.getSecuenciaVuelos()) {
                String vueloId = vuelo.getId();
                conteo.put(vueloId, conteo.getOrDefault(vueloId, 0) + 1);
            }
        }

        return conteo;
    }

    private int obtenerCapacidadVuelo(String vueloId) {
        for (RutaPedido ruta : solucionLogistica.getAsignacionPedidos().values()) {
            for (Vuelo vuelo : ruta.getSecuenciaVuelos()) {
                if (vuelo.getId().equals(vueloId)) {
                    return vuelo.getCapacidadMaxima();
                }
            }
        }
        return 300; // Valor por defecto
    }

    private void actualizarContadores() {
        int aTiempo = 0;
        int conRetraso = 0;

        for (RutaPedido ruta : solucionLogistica.getAsignacionPedidos().values()) {
            if (esPedidoATiempo(ruta)) {
                aTiempo++;
            } else {
                conRetraso++;
            }
        }

        solucionLogistica.setCantidadAtiempo(aTiempo);
        solucionLogistica.setCantidadRetraso(conRetraso);
    }

    // Reporte detallado
    public String obtenerReporteFitness() {
        double entregaTiempo = calcularEntregaATiempo();
        double minimizarAtraso = calcularMinimizarAtraso();
        double capacidades = calcularRespetarCapacidades();
        double aprovechamiento = calcularAprovechamientoVuelos();
        double calidadRutas = calcularCalidadRutas();
        double penalizacionFabricas = calcularPenalizacionFabricas();

        // Contar pedidos válidos e inválidos por fábrica
        Map<String, Integer> pedidosPorFabrica = contarPedidosPorFabrica();
        int pedidosInvalidos = contarPedidosInvalidos();

        return String.format(
                "=== REPORTE DE FITNESS JERÁRQUICO ===\n" +
                        "FITNESS TOTAL: %.2f\n\n" +
                        "PRIORIDAD #1 - Entregar a tiempo (45%%): %.1f/100\n" +
                        "PRIORIDAD #2 - Minimizar atraso (25%%): %.1f/100\n" +
                        "PRIORIDAD #3 - Respetar capacidades (15%%): %.1f/100\n" +
                        "PRIORIDAD #4 - Aprovechar vuelos (10%%): %.1f/100\n" +
                        "PRIORIDAD #5 - Calidad de rutas (5%%): %.1f/100\n" +
                        "PENALIZACIÓN - Fábricas inválidas: %.0f\n\n" +
                        "ESTADÍSTICAS:\n" +
                        "- Pedidos a tiempo: %d/%d (%.1f%%)\n" +
                        "- Pedidos con retraso: %d\n" +
                        "- Pedidos desde fábricas válidas: %d/%d\n" +
                        "- Pedidos desde Lima (LIM): %d\n" +
                        "- Pedidos desde Bruselas (BRU): %d\n" +
                        "- Pedidos desde Baku (BAK): %d\n" +
                        "- Pedidos INVÁLIDOS: %d\n" +
                        "- Vuelos utilizados: %d\n" +
                        "- Factible: %s",
                fitness,
                entregaTiempo, minimizarAtraso, capacidades, aprovechamiento, calidadRutas, penalizacionFabricas,
                solucionLogistica.getCantidadAtiempo(),
                solucionLogistica.getAsignacionPedidos().size(),
                entregaTiempo,
                solucionLogistica.getCantidadRetraso(),
                (solucionLogistica.getAsignacionPedidos().size() - pedidosInvalidos),
                solucionLogistica.getAsignacionPedidos().size(),
                pedidosPorFabrica.getOrDefault("LIM", 0),
                pedidosPorFabrica.getOrDefault("BRU", 0),
                pedidosPorFabrica.getOrDefault("BAK", 0),
                pedidosInvalidos,
                contarPedidosPorVuelo().size(),
                esSolucionFactible() ? "SÍ" : "NO"
        );
    }

    private double calcularPenalizacionFabricas() {
        Map<Pedido, RutaPedido> asignaciones = solucionLogistica.getAsignacionPedidos();
        double penalizacion = 0.0;
        int pedidosInvalidos = 0;

        for (Map.Entry<Pedido, RutaPedido> entry : asignaciones.entrySet()) {
            Pedido pedido = entry.getKey();
            RutaPedido ruta = entry.getValue();

            if (!pedidoSaleDeFabricaValida(ruta)) {
                penalizacion += PENALIZACION_FABRICA_INVALIDA;
                pedidosInvalidos++;
            }
        }

        // Log para debugging (opcional)
        if (pedidosInvalidos > 0) {
            System.out.printf("⚠️ Penalización fábricas: %d pedidos inválidos (%.0f puntos)%n",
                    pedidosInvalidos, penalizacion);
        }

        return penalizacion;
    }

    private boolean pedidoSaleDeFabricaValida(RutaPedido ruta) {
        if (ruta == null || ruta.getSecuenciaVuelos().isEmpty()) {
            return false; // Sin vuelos = inválido
        }

        // Obtener el aeropuerto de origen del primer vuelo
        Vuelo primerVuelo = ruta.getSecuenciaVuelos().get(0);
        String codigoOrigenPrimerVuelo = primerVuelo.getOrigen().getCodigo();

        // Verificar si el origen está en la lista de fábricas válidas
        return FABRICAS_VALIDAS.contains(codigoOrigenPrimerVuelo);
    }

    public boolean esSolucionFactible() {
        return calcularRespetarCapacidades() >= 0; // Sin violaciones de capacidad
    }

    private Map<String, Integer> contarPedidosPorFabrica() {
        Map<String, Integer> contador = new HashMap<>();

        // Inicializar contadores
        for (String fabrica : FABRICAS_VALIDAS) {
            contador.put(fabrica, 0);
        }

        // Contar pedidos por fábrica
        for (RutaPedido ruta : solucionLogistica.getAsignacionPedidos().values()) {
            if (ruta.getSecuenciaVuelos().isEmpty()) continue;

            String origenCodigo = ruta.getSecuenciaVuelos().get(0).getOrigen().getCodigo();
            if (FABRICAS_VALIDAS.contains(origenCodigo)) {
                contador.put(origenCodigo, contador.get(origenCodigo) + 1);
            }
        }

        return contador;
    }

    private int contarPedidosInvalidos() {
        int contador = 0;
        for (RutaPedido ruta : solucionLogistica.getAsignacionPedidos().values()) {
            if (!pedidoSaleDeFabricaValida(ruta)) {
                contador++;
            }
        }
        return contador;
    }



}
