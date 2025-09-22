package com.morapack.models;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class Solucion {
    private SolucionLogistica solucionLogistica;
    private double fitness;
    private int totalPedidosProblema; // NUEVO: Total de pedidos que deberían atenderse

    // CONSTANTE PARA IDENTIFICAR FÁBRICAS
    public static final List<String> FABRICAS = Arrays.asList("SPIM", "EBCI", "UBBB");

    // PESOS AJUSTADOS - PRIORIDAD MÁXIMA A ATENDER TODOS LOS PEDIDOS
    private static final double PESO_ENTREGA_TIEMPO = 0.50;    // 50% - PRIORIDAD #1 (incluye cobertura)
    private static final double PESO_MINIMIZAR_ATRASO = 0.20;  // 20% - PRIORIDAD #2
    private static final double PESO_NO_VIOLAR_CAPACIDAD = 0.15; // 15% - PRIORIDAD #3
    private static final double PESO_APROVECHAR_VUELOS = 0.10;   // 10% - PRIORIDAD #4
    private static final double PESO_EVITAR_RUTAS_MALAS = 0.05;  // 5% - PRIORIDAD #5

    // ✅ NUEVO: Constante para liberación temporal
    private static final int HORAS_LIBERACION = 2;

    private static final double PENALIZACION_FABRICA_INVALIDA = -5000; // Penalización por no salir de fábrica válida

    // Penalizaciones severas
    private static final double PENALIZACION_SOBRECARGA = -1000;
    private static final double PENALIZACION_ATRASO_SEVERO = -500;

    // CONSTRUCTORES
    public Solucion(SolucionLogistica solucionLogistica) {
        this.solucionLogistica = solucionLogistica;
        this.totalPedidosProblema = 0; // Se establecerá después
        this.fitness = calcularFitness();
    }

    /**
     * Constructor NUEVO que recibe el total de pedidos del problema
     */
    public Solucion(SolucionLogistica solucionLogistica, int totalPedidosProblema) {
        this.solucionLogistica = solucionLogistica;
        this.totalPedidosProblema = totalPedidosProblema;
        this.fitness = calcularFitness();
    }

    private static class EventoAlmacenTemporal {
        final LocalDateTime fechaLlegada;
        final LocalDateTime fechaLiberacion;
        final int cantidadPaquetes;
        final String pedidoId;

        EventoAlmacenTemporal(LocalDateTime fechaLlegada, int cantidadPaquetes, String pedidoId) {
            this.fechaLlegada = fechaLlegada;
            this.fechaLiberacion = fechaLlegada.plusHours(HORAS_LIBERACION);
            this.cantidadPaquetes = cantidadPaquetes;
            this.pedidoId = pedidoId;
        }

        boolean estaOcupado(LocalDateTime momento) {
            return !momento.isBefore(fechaLlegada) && momento.isBefore(fechaLiberacion);
        }
    }

    // Getters y Setters
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

    public int getTotalPedidosProblema() {
        return totalPedidosProblema;
    }

    public void setTotalPedidosProblema(int totalPedidosProblema) {
        this.totalPedidosProblema = totalPedidosProblema;
        this.fitness = calcularFitness(); // Recalcular fitness con el nuevo total
    }

    //------------------------------------------------------------------------------------------------------------------

    /**
     * Calcula el fitness con PRIORIDAD ABSOLUTA en atender todos los pedidos
     */
    private double calcularFitness() {
        if (solucionLogistica == null || solucionLogistica.getAsignacionPedidos().isEmpty()) {
            return -10000;
        }

        double puntuacionEntregaTiempo = calcularEntregaATiempoCorregido();
        double puntuacionMinimizarAtraso = calcularMinimizarAtraso();
        double puntuacionCapacidades = calcularRespetarCapacidades();
        double puntuacionAlmacenes = calcularRespetarCapacidadAlmacenesTemporal(); // ✅ MODIFICADO
        double puntuacionAprovechamiento = calcularAprovechamientoVuelos();
        double puntuacionRutas = calcularCalidadRutas();
        double penalizacionFabricas = calcularPenalizacionFabricas();

        double fitness = (PESO_ENTREGA_TIEMPO * puntuacionEntregaTiempo) +
                (PESO_MINIMIZAR_ATRASO * puntuacionMinimizarAtraso) +
                (0.10 * puntuacionCapacidades) +
                (0.05 * puntuacionAlmacenes) +
                (PESO_APROVECHAR_VUELOS * puntuacionAprovechamiento) +
                (PESO_EVITAR_RUTAS_MALAS * puntuacionRutas) +
                penalizacionFabricas;

        actualizarContadores();
        return fitness;
    }

    // ✅ NUEVO: Validar capacidad de almacenes
    private double calcularRespetarCapacidadAlmacenesTemporal() {
        // 1. Construir historial temporal de eventos en cada almacén
        Map<String, List<EventoAlmacenTemporal>> historialPorAlmacen = construirHistorialTemporal();

        if (historialPorAlmacen.isEmpty()) return 100.0;

        int violaciones = 0;
        double penalizacionTotal = 0.0;
        int almacenesEvaluados = 0;

        // 2. Evaluar cada almacén en todos los momentos críticos
        for (Map.Entry<String, List<EventoAlmacenTemporal>> entry : historialPorAlmacen.entrySet()) {
            String codigoAlmacen = entry.getKey();
            List<EventoAlmacenTemporal> eventos = entry.getValue();

            Aeropuerto almacen = buscarAeropuertoPorCodigo(codigoAlmacen);
            if (almacen == null) continue;

            almacenesEvaluados++;

            // 3. Evaluar en cada momento de llegada
            for (EventoAlmacenTemporal eventoActual : eventos) {
                int ocupacionEnMomento = almacen.getCapacidadAct();

                // Sumar todos los eventos que están ocupando el almacén en este momento
                for (EventoAlmacenTemporal evento : eventos) {
                    if (evento.estaOcupado(eventoActual.fechaLlegada)) {
                        ocupacionEnMomento += evento.cantidadPaquetes;
                    }
                }

                // Verificar si hay violación de capacidad
                if (ocupacionEnMomento > almacen.getCapacidad()) {
                    violaciones++;
                    int exceso = ocupacionEnMomento - almacen.getCapacidad();
                    penalizacionTotal += PENALIZACION_SOBRECARGA * exceso * 0.1; // Penalización reducida vs vuelos
                }
            }
        }


        if (violaciones == 0) return 100.0;

        // Normalizar la penalización por número de almacenes evaluados
        double penalizacionPromedio = almacenesEvaluados > 0 ? penalizacionTotal / almacenesEvaluados : penalizacionTotal;

        return Math.max(-500, 100 + penalizacionPromedio); // Penalización máxima menor que vuelos
    }

    private Map<String, List<EventoAlmacenTemporal>> construirHistorialTemporal() {
        Map<String, List<EventoAlmacenTemporal>> historial = new HashMap<>();

        for (Map.Entry<Pedido, RutaPedido> entry : solucionLogistica.getAsignacionPedidos().entrySet()) {
            Pedido pedido = entry.getKey();
            RutaPedido ruta = entry.getValue();

            if (ruta == null || ruta.getSecuenciaVuelos().isEmpty()) continue;

            // Registrar eventos en almacenes intermedios (escalas)
            for (int i = 0; i < ruta.getSecuenciaVuelos().size() - 1; i++) {
                Vuelo vuelo = ruta.getSecuenciaVuelos().get(i);
                String codigoAlmacen = vuelo.getDestino().getCodigo();
                LocalDateTime momentoLlegada = vuelo.getHoraLlegada();

                if (momentoLlegada != null) {
                    EventoAlmacenTemporal evento = new EventoAlmacenTemporal(
                            momentoLlegada, pedido.getCantidad(), pedido.getId());
                    historial.computeIfAbsent(codigoAlmacen, k -> new ArrayList<>()).add(evento);
                }
            }

            // Registrar evento en almacén de destino final
            Vuelo ultimoVuelo = ruta.getSecuenciaVuelos().get(ruta.getSecuenciaVuelos().size() - 1);
            String codigoDestino = ultimoVuelo.getDestino().getCodigo();
            LocalDateTime momentoLlegada = ultimoVuelo.getHoraLlegada();

            if (momentoLlegada != null) {
                EventoAlmacenTemporal evento = new EventoAlmacenTemporal(
                        momentoLlegada, pedido.getCantidad(), pedido.getId());
                historial.computeIfAbsent(codigoDestino, k -> new ArrayList<>()).add(evento);
            }
        }

        // Ordenar eventos por fecha de llegada en cada almacén
        for (List<EventoAlmacenTemporal> eventos : historial.values()) {
            eventos.sort(Comparator.comparing(e -> e.fechaLlegada));
        }

        return historial;
    }

    // ✅ NUEVO: Contar carga por almacén
    private Map<String, Integer> contarCargaPorAlmacen() {
        Map<String, Integer> carga = new HashMap<>();

        for (RutaPedido ruta : solucionLogistica.getAsignacionPedidos().values()) {
            int unidades = ruta.getPedido().getCantidad();

            // Contar en almacenes intermedios (escalas)
            for (int i = 0; i < ruta.getSecuenciaVuelos().size() - 1; i++) {
                String codigoAlmacen = ruta.getSecuenciaVuelos().get(i).getDestino().getCodigo();
                carga.merge(codigoAlmacen, unidades, Integer::sum);
            }

            // Contar en almacén de destino final
            List<Vuelo> vuelos = ruta.getSecuenciaVuelos();
            if (!vuelos.isEmpty()) {
                String codigoDestino = vuelos.get(vuelos.size() - 1).getDestino().getCodigo();
                carga.merge(codigoDestino, unidades, Integer::sum);
            }
        }

        return carga;
    }

    // ✅ NUEVO: Buscar aeropuerto por código
    private Aeropuerto buscarAeropuertoPorCodigo(String codigo) {
        for (RutaPedido ruta : solucionLogistica.getAsignacionPedidos().values()) {
            for (Vuelo vuelo : ruta.getSecuenciaVuelos()) {
                if (vuelo.getOrigen().getCodigo().equals(codigo)) {
                    return vuelo.getOrigen();
                }
                if (vuelo.getDestino().getCodigo().equals(codigo)) {
                    return vuelo.getDestino();
                }
            }
        }
        return null;
    }

    /**
     * PRIORIDAD #1 CORREGIDA: Entregar a tiempo considerando TODOS los pedidos del problema
     * Un pedido no asignado = Un pedido NO entregado a tiempo
     */
    private double calcularEntregaATiempoCorregido() {
        Map<Pedido, RutaPedido> asignaciones = solucionLogistica.getAsignacionPedidos();

        // Si no conocemos el total, usar el método anterior como fallback
        if (totalPedidosProblema == 0) {
            return calcularEntregaATiempoLegacy();
        }

        int pedidosATiempo = 0;

        // Contar solo los pedidos asignados que están a tiempo
        for (RutaPedido ruta : asignaciones.values()) {
            if (esPedidoATiempo(ruta)) {
                pedidosATiempo++;
            }
        }

        // CLAVE: El porcentaje se calcula sobre TODOS los pedidos del problema
        double porcentajeCobertura = (double) pedidosATiempo / totalPedidosProblema * 100.0;

        return porcentajeCobertura;
    }

    /**
     * Método legacy para compatibilidad hacia atrás
     */
    private double calcularEntregaATiempoLegacy() {
        Map<Pedido, RutaPedido> asignaciones = solucionLogistica.getAsignacionPedidos();
        int totalPedidos = asignaciones.size();

        if (totalPedidos == 0) return 0.0;

        int pedidosATiempo = 0;

        for (RutaPedido ruta : asignaciones.values()) {
            if (esPedidoATiempo(ruta)) {
                pedidosATiempo++;
            }
        }

        // Porcentaje de pedidos a tiempo solo sobre los asignados
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
        Map<Vuelo, Integer> cargaPorVuelo = contarCargaPorVuelo();
        if (cargaPorVuelo.isEmpty()) return 100.0;

        int violaciones = 0;
        double penalizacionTotal = 0.0;

        for (Map.Entry<Vuelo,Integer> e : cargaPorVuelo.entrySet()) {
            Vuelo v = e.getKey();
            int usados = e.getValue();
            int capacidad = v.getCapacidadMaxima();
            if (usados > capacidad) {
                violaciones++;
                int exceso = usados - capacidad;
                penalizacionTotal += PENALIZACION_SOBRECARGA * exceso;
            }
        }
        if (violaciones == 0) return 100.0;
        return Math.max(-1000, 100 + penalizacionTotal);
    }

    // PRIORIDAD #4: Aprovechar bien los vuelos (0-100 puntos)
    private double calcularAprovechamientoVuelos() {
        Map<Vuelo, Integer> ocupacionPorVuelo = contarCargaPorVuelo();
        if (ocupacionPorVuelo.isEmpty()) return 0.0;

        double sumaEficiencias = 0.0;
        int vuelosValidos = 0;

        for (Map.Entry<Vuelo, Integer> e : ocupacionPorVuelo.entrySet()) {
            Vuelo vuelo = e.getKey();
            int unidadesEnVuelo = e.getValue();
            int capacidad = vuelo.getCapacidadMaxima();

            if (capacidad > 0 && unidadesEnVuelo <= capacidad) {
                double eficiencia = (100.0 * unidadesEnVuelo) / capacidad;

                // Bonus por alta ocupación
                if (eficiencia > 80.0) eficiencia += 10.0;

                sumaEficiencias += Math.min(100.0, eficiencia);
                vuelosValidos++;
            }
        }

        return (vuelosValidos > 0) ? (sumaEficiencias / vuelosValidos) : 0.0;
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
        LocalDateTime fechaSalida  = ruta.getFechaSalida();
        LocalDateTime fechaLlegada = ruta.getFechaLlegada();

        if (fechaSalida == null || fechaLlegada == null) {
            // Reutiliza tu cálculo alterno
            return calcularTiempoBasadoEnVuelos(ruta);
        }

        // Si esInternacional es null, lo tratamos como false (no internacional)
        boolean internacional = Boolean.TRUE.equals(ruta.getEsInternacional());
        int slaHoras = internacional ? 72 : 48;

        LocalDateTime limiteVirtual = fechaSalida.plusHours(slaHoras);
        // A tiempo si no llega después del límite
        return !fechaLlegada.isAfter(limiteVirtual);
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

    // Sumar CANTIDAD de pedidos por vuelo (no el número de pedidos)
    private Map<Vuelo, Integer> contarCargaPorVuelo() {
        Map<Vuelo, Integer> carga = new HashMap<>();
        for (RutaPedido ruta : solucionLogistica.getAsignacionPedidos().values()) {
            int unidades = ruta.getPedido().getCantidad(); // paquetes del pedido
            for (Vuelo v : ruta.getSecuenciaVuelos()) {
                carga.put(v, carga.getOrDefault(v, 0) + unidades);
            }
        }
        return carga;
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
        return FABRICAS.contains(codigoOrigenPrimerVuelo);
    }

    public boolean esSolucionFactible() {
        return calcularRespetarCapacidades() >= 0 && calcularRespetarCapacidadAlmacenesTemporal() >= 0;
    }



    private Map<String, Integer> contarPedidosPorFabrica() {
        Map<String, Integer> contador = new HashMap<>();

        // Inicializar contadores
        for (String fabrica : FABRICAS) {
            contador.put(fabrica, 0);
        }

        // Contar pedidos por fábrica
        for (RutaPedido ruta : solucionLogistica.getAsignacionPedidos().values()) {
            if (ruta.getSecuenciaVuelos().isEmpty()) continue;

            String origenCodigo = ruta.getSecuenciaVuelos().get(0).getOrigen().getCodigo();
            if (FABRICAS.contains(origenCodigo)) {
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

    // Reporte detallado ACTUALIZADO
    public String obtenerReporteFitness() {
        double entregaTiempo = calcularEntregaATiempoCorregido();
        double minimizarAtraso = calcularMinimizarAtraso();
        double capacidades = calcularRespetarCapacidades();
        double capacidadAlmacenes = calcularRespetarCapacidadAlmacenesTemporal(); // ✅ MODIFICADO
        double aprovechamiento = calcularAprovechamientoVuelos();
        double calidadRutas = calcularCalidadRutas();
        double penalizacionFabricas = calcularPenalizacionFabricas();

        // ✅ NUEVO: Estadísticas temporales de almacenes
        Map<String, List<EventoAlmacenTemporal>> historial = construirHistorialTemporal();
        int violacionesTemporales = 0;
        int almacenesConViolaciones = 0;

        for (Map.Entry<String, List<EventoAlmacenTemporal>> entry : historial.entrySet()) {
            String codigoAlmacen = entry.getKey();
            List<EventoAlmacenTemporal> eventos = entry.getValue();
            Aeropuerto almacen = buscarAeropuertoPorCodigo(codigoAlmacen);

            if (almacen != null) {
                boolean tieneViolaciones = false;
                for (EventoAlmacenTemporal eventoActual : eventos) {
                    int ocupacion = almacen.getCapacidadAct();
                    for (EventoAlmacenTemporal evento : eventos) {
                        if (evento.estaOcupado(eventoActual.fechaLlegada)) {
                            ocupacion += evento.cantidadPaquetes;
                        }
                    }
                    if (ocupacion > almacen.getCapacidad()) {
                        violacionesTemporales++;
                        tieneViolaciones = true;
                    }
                }
                if (tieneViolaciones) {
                    almacenesConViolaciones++;
                }
            }
        }

        Map<String, Integer> pedidosPorFabrica = contarPedidosPorFabrica();
        int pedidosInvalidos = contarPedidosInvalidos();
        int pedidosAsignados = solucionLogistica.getAsignacionPedidos().size();

        String infoCobertura = "";
        if (totalPedidosProblema > 0) {
            double porcentajeCobertura = (double) pedidosAsignados / totalPedidosProblema * 100.0;
            infoCobertura = String.format("- Cobertura total: %d/%d pedidos (%.1f%%)%n",
                    pedidosAsignados, totalPedidosProblema, porcentajeCobertura);
        }

        return String.format(
                "=== REPORTE DE FITNESS JERÁRQUICO ===\n" +
                        "FITNESS TOTAL: %.2f\n\n" +
                        "PRIORIDAD #1 - Entregar a tiempo (50%%) [INCLUYE COBERTURA]: %.1f/100\n" +
                        "PRIORIDAD #2 - Minimizar atraso (20%%): %.1f/100\n" +
                        "PRIORIDAD #3 - Respetar capacidades vuelos (10%%): %.1f/100\n" +
                        "PRIORIDAD #3b - Respetar capacidades almacenes temporales (5%%): %.1f/100\n" +
                        "PRIORIDAD #4 - Aprovechar vuelos (10%%): %.1f/100\n" +
                        "PRIORIDAD #5 - Calidad de rutas (5%%): %.1f/100\n" +
                        "PENALIZACIÓN - Fábricas inválidas: %.0f\n\n" +
                        "ESTADÍSTICAS:\n" +
                        "%s" +
                        "- Pedidos a tiempo: %d/%d (%.1f%%)\n" +
                        "- Pedidos con retraso: %d\n" +
                        "- Violaciones capacidad vuelos: %s\n" +
                        "- Violaciones capacidad almacenes temporales: %d en %d almacenes\n" +
                        "- Eventos temporales registrados: %d en %d almacenes\n" +
                        "- Liberación automática después de: %d horas\n" +
                        "- Pedidos desde fábricas válidas: %d/%d\n" +
                        "- Vuelos utilizados: %d\n" +
                        "- Almacenes utilizados: %d\n" +
                        "- Factible: %s",
                fitness,
                entregaTiempo, minimizarAtraso, capacidades, capacidadAlmacenes,
                aprovechamiento, calidadRutas, penalizacionFabricas,
                infoCobertura,
                solucionLogistica.getCantidadAtiempo(),
                pedidosAsignados,
                entregaTiempo,
                solucionLogistica.getCantidadRetraso(),
                calcularRespetarCapacidades() < 0 ? "SÍ" : "NO",
                violacionesTemporales, almacenesConViolaciones,
                historial.values().stream().mapToInt(List::size).sum(), historial.size(),
                HORAS_LIBERACION,
                (pedidosAsignados - pedidosInvalidos),
                pedidosAsignados,
                contarCargaPorVuelo().size(),
                historial.size(),
                esSolucionFactible() ? "SÍ" : "NO"
        );
    }
}