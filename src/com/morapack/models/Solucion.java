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
    private int totalPedidosProblema; // NUEVO: Total de pedidos que deberían atenderse

    // CONSTANTE PARA IDENTIFICAR FÁBRICAS
    public static final List<String> FABRICAS = Arrays.asList("SPIM", "EBCI", "UBBB");

    // PESOS AJUSTADOS - PRIORIDAD MÁXIMA A ATENDER TODOS LOS PEDIDOS
    private static final double PESO_ENTREGA_TIEMPO = 0.50;    // 50% - PRIORIDAD #1 (incluye cobertura)
    private static final double PESO_MINIMIZAR_ATRASO = 0.20;  // 20% - PRIORIDAD #2
    private static final double PESO_NO_VIOLAR_CAPACIDAD = 0.15; // 15% - PRIORIDAD #3
    private static final double PESO_APROVECHAR_VUELOS = 0.10;   // 10% - PRIORIDAD #4
    private static final double PESO_EVITAR_RUTAS_MALAS = 0.05;  // 5% - PRIORIDAD #5

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
        double puntuacionCapacidades = calcularRespetarCapacidades(); // Vuelos
        double puntuacionAlmacenes = calcularRespetarCapacidadAlmacenes(); // ✅ NUEVO
        double puntuacionAprovechamiento = calcularAprovechamientoVuelos();
        double puntuacionRutas = calcularCalidadRutas();
        double penalizacionFabricas = calcularPenalizacionFabricas();

        // ✅ MODIFICADO: Reajustar pesos para incluir almacenes
        double fitness = (PESO_ENTREGA_TIEMPO * puntuacionEntregaTiempo) +
                (PESO_MINIMIZAR_ATRASO * puntuacionMinimizarAtraso) +
                (0.10 * puntuacionCapacidades) +        // Reducido de 15% a 10%
                (0.05 * puntuacionAlmacenes) +         // ✅ NUEVO: 5% para almacenes
                (PESO_APROVECHAR_VUELOS * puntuacionAprovechamiento) +
                (PESO_EVITAR_RUTAS_MALAS * puntuacionRutas) +
                penalizacionFabricas;

        actualizarContadores();
        return fitness;
    }

    // ✅ NUEVO: Validar capacidad de almacenes
    private double calcularRespetarCapacidadAlmacenes() {
        Map<String, Integer> cargaPorAlmacen = contarCargaPorAlmacen();
        if (cargaPorAlmacen.isEmpty()) return 100.0;

        int violaciones = 0;
        double penalizacionTotal = 0.0;

        for (Map.Entry<String, Integer> entry : cargaPorAlmacen.entrySet()) {
            String codigoAlmacen = entry.getKey();
            int unidadesEnAlmacen = entry.getValue();

            Aeropuerto almacen = buscarAeropuertoPorCodigo(codigoAlmacen);
            if (almacen != null) {
                int capacidadTotal = almacen.getCapacidad();
                int capacidadUsada = almacen.getCapacidadAct() + unidadesEnAlmacen;

                if (capacidadUsada > capacidadTotal) {
                    violaciones++;
                    int exceso = capacidadUsada - capacidadTotal;
                    penalizacionTotal += PENALIZACION_SOBRECARGA * exceso;
                }
            }
        }

        if (violaciones == 0) return 100.0;
        return Math.max(-1000, 100 + penalizacionTotal);
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
        // Buscar en todos los vuelos de las rutas asignadas
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
        return calcularRespetarCapacidades() >= 0 && calcularRespetarCapacidadAlmacenes() >= 0;
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
        double capacidadAlmacenes = calcularRespetarCapacidadAlmacenes(); // ✅ NUEVO
        double aprovechamiento = calcularAprovechamientoVuelos();
        double calidadRutas = calcularCalidadRutas();
        double penalizacionFabricas = calcularPenalizacionFabricas();

        // Contar violaciones de almacenes
        Map<String, Integer> cargaAlmacenes = contarCargaPorAlmacen();
        int violacionesAlmacenes = 0;
        for (Map.Entry<String, Integer> entry : cargaAlmacenes.entrySet()) {
            Aeropuerto almacen = buscarAeropuertoPorCodigo(entry.getKey());
            if (almacen != null) {
                int capacidadUsada = almacen.getCapacidadAct() + entry.getValue();
                if (capacidadUsada > almacen.getCapacidad()) {
                    violacionesAlmacenes++;
                }
            }
        }

        // Resto del código existente + nuevas líneas
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
                        "PRIORIDAD #3b - Respetar capacidades almacenes (5%%): %.1f/100\n" + // ✅ NUEVO
                        "PRIORIDAD #4 - Aprovechar vuelos (10%%): %.1f/100\n" +
                        "PRIORIDAD #5 - Calidad de rutas (5%%): %.1f/100\n" +
                        "PENALIZACIÓN - Fábricas inválidas: %.0f\n\n" +
                        "ESTADÍSTICAS:\n" +
                        "%s" +
                        "- Pedidos a tiempo: %d/%d (%.1f%%)\n" +
                        "- Pedidos con retraso: %d\n" +
                        "- Violaciones capacidad vuelos: %s\n" +
                        "- Violaciones capacidad almacenes: %d\n" + // ✅ NUEVO
                        "- Pedidos desde fábricas válidas: %d/%d\n" +
                        "- Vuelos utilizados: %d\n" +
                        "- Almacenes utilizados: %d\n" + // ✅ NUEVO
                        "- Factible: %s",
                fitness,
                entregaTiempo, minimizarAtraso, capacidades, capacidadAlmacenes, // ✅ NUEVO
                aprovechamiento, calidadRutas, penalizacionFabricas,
                infoCobertura,
                solucionLogistica.getCantidadAtiempo(),
                pedidosAsignados,
                entregaTiempo,
                solucionLogistica.getCantidadRetraso(),
                calcularRespetarCapacidades() < 0 ? "SÍ" : "NO",
                violacionesAlmacenes, // ✅ NUEVO
                (pedidosAsignados - pedidosInvalidos),
                pedidosAsignados,
                contarCargaPorVuelo().size(),
                cargaAlmacenes.size(), // ✅ NUEVO
                esSolucionFactible() && capacidadAlmacenes >= 0 ? "SÍ" : "NO" // ✅ MODIFICADO
        );
    }
}