package com.morapack.models;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ACSMoraPack – Mejorador tipo Ant Colony System sobre semillas GRASP
 * -------------------------------------------------------------------
 * - GRASP construye k soluciones (semillas).
 * - ACS explora/combina SOLO sobre esas rutas (por defecto).
 * - Respeta capacidad, conexiones y SLA considerando husos.
 * - Normaliza fechaSalida/fechaLlegada de RutaPedido a UTC (LocalDateTime),
 *   para que Solucion.esPedidoATiempo() funcione correctamente.
 */
public class ACSMoraPack {

    /* ==================== Parámetros ACS ==================== */
    private int numeroHormigas    = 40;
    private int numeroIteraciones = 80;
    public  double alfa = 1.0;    // peso feromona (τ)
    public  double beta = 3.0;    // peso heurística (η)
    public  double rho  = 0.10;   // evaporación global
    public  double xi   = 0.10;   // actualización local (ACS)
    public  double tau0 = 0.01;   // feromona base
    public  double depositoQ = 1.0; // refuerzo global

    // SLA (usa flag esInternacional en RutaPedido)
    public int slaHorasNoInternacional = 48;
    public int slaHorasInternacional   = 72;

    /* ==================== Datos base ==================== */
    private final List<Pedido> pedidos;
    private final List<Vuelo>  vuelos;
    private final Map<String, List<Vuelo>> vuelosPorOrigen;

    // Feromonas por arco (id de vuelo)
    private final Map<String, Double> feromonas = new HashMap<>();

    // RNG
    private final Random aleatorio = new Random(1234);

    // Mejor solución global
    private Solucion mejorHastaAhora;

    // Semillas GRASP (opcionales)
    private List<Solucion> semillas = new ArrayList<>();

    /* ==================== Modo SOLO semillas ==================== */
    private boolean soloSemillas = true;

    // Índices de plantillas de rutas provenientes de las semillas
    private final Map<String, List<RutaPedido>> rutasSemillaPorPedidoId = new HashMap<>();
    private final Map<String, List<RutaPedido>> rutasSemillaPorDestino  = new HashMap<>();

    /* ==================== Construcción ==================== */
    public ACSMoraPack(List<Pedido> pedidos, List<Vuelo> vuelos) {
        this.pedidos = Objects.requireNonNull(pedidos);
        this.vuelos  = Objects.requireNonNull(vuelos);
        this.vuelosPorOrigen = vuelos.stream()
                .collect(Collectors.groupingBy(v -> v.getOrigen().getCodigo()));
        inicializarFeromonas(tau0);
    }

    /* ==================== Configuración pública ==================== */
    public void establecerSemillas(List<Solucion> semillas) {
        this.semillas = (semillas == null) ? new ArrayList<>() : new ArrayList<>(semillas);
        inicializarFeromonas(tau0);

        rutasSemillaPorPedidoId.clear();
        rutasSemillaPorDestino.clear();

        for (Solucion s : this.semillas) {
            if (s == null || s.getSolucionLogistica() == null) continue;
            Map<Pedido, RutaPedido> asig = s.getSolucionLogistica().getAsignacionPedidos();
            if (asig == null) continue;

            for (Map.Entry<Pedido, RutaPedido> e : asig.entrySet()) {
                Pedido pk = e.getKey();
                RutaPedido rp = e.getValue();
                if (rp == null) continue;

                // plantilla SIN pedido; fechas recalculadas a UTC para coherencia
                RutaPedido plantilla = clonarBasicoSinPedido(rp);

                if (pk != null && pk.getId() != null) {
                    rutasSemillaPorPedidoId
                            .computeIfAbsent(pk.getId(), k -> new ArrayList<>())
                            .add(plantilla);
                }
                String dest = (rp.getAlmacenDestino() != null)
                        ? rp.getAlmacenDestino().getCodigo() : null;
                if (dest != null) {
                    rutasSemillaPorDestino
                            .computeIfAbsent(dest, k -> new ArrayList<>())
                            .add(plantilla);
                }
            }
        }
        dedupIndice(rutasSemillaPorPedidoId);
        dedupIndice(rutasSemillaPorDestino);

        if (!this.semillas.isEmpty()) {
            double mejorFitness = this.semillas.stream().mapToDouble(Solucion::getFitness).max().orElse(1.0);
            double refuerzoBase = 0.05;
            for (Solucion s : this.semillas) {
                double peso = Math.max(0.1, s.getFitness() / mejorFitness);
                for (String arco : extraerArcos(s)) {
                    feromonas.put(arco, feromonas.getOrDefault(arco, tau0) + refuerzoBase * peso);
                }
            }
        }
    }

    public void configurarParametrosACS(int numeroHormigas, int numeroIteraciones,
                                        double alfa, double beta, double rho, double xi) {
        this.numeroHormigas = numeroHormigas;
        this.numeroIteraciones = numeroIteraciones;
        this.alfa = alfa; this.beta = beta; this.rho = rho; this.xi = xi;
    }

    public void establecerSemillaAleatoria(long semilla) { this.aleatorio.setSeed(semilla); }
    public void setSoloSemillas(boolean soloSemillas) { this.soloSemillas = soloSemillas; }

    /* ==================== Ejecución ==================== */
    public Solucion ejecutar() {
        if (mejorHastaAhora == null) {
            mejorHastaAhora = construirSolucionPorHormiga();
        }
        for (int it = 0; it < numeroIteraciones; it++) {
            Solucion mejorIteracion = null;
            for (int h = 0; h < numeroHormigas; h++) {
                Solucion s = construirSolucionPorHormiga();
                if (mejorIteracion == null || s.getFitness() > mejorIteracion.getFitness()) {
                    mejorIteracion = s;
                }
            }
            if (mejorIteracion != null &&
                    (mejorHastaAhora == null || mejorIteracion.getFitness() > mejorHastaAhora.getFitness())) {
                mejorHastaAhora = mejorIteracion;
            }
            actualizarFeromonaGlobal(mejorHastaAhora);
        }
        return mejorHastaAhora;
    }

    /* ==================== Construcción por hormiga ==================== */
    private Solucion construirSolucionPorHormiga() {
        Map<String, Integer> ocupacion = new HashMap<>();
        Map<Pedido, RutaPedido> asignacion = new LinkedHashMap<>();

        List<Pedido> orden = new ArrayList<>(pedidos);
        orden.sort(Comparator.comparingInt(Pedido::getCantidad).reversed());

        for (Pedido p : orden) {
            List<RutaPedido> candidatos = generarCandidatosParaPedido(p);

            List<RutaPedido> factibles = candidatos.stream()
                    .filter(r -> r.getPedido() != null)
                    .filter(r -> respetaCapacidades(r, ocupacion))
                    .filter(this::llegaDentroDeSLA)
                    .collect(Collectors.toList());

            if (factibles.isEmpty()) continue;

            RutaPedido elegida = seleccionarPorProbabilidad(factibles, ocupacion);

            aplicarOcupacion(elegida, ocupacion, +p.getCantidad());
            asignacion.put(p, elegida);

            for (Vuelo v : elegida.getSecuenciaVuelos()) {
                String arco = v.getId();
                double tauAnt = feromonas.getOrDefault(arco, tau0);
                feromonas.put(arco, (1 - xi) * tauAnt + xi * tau0);
            }
        }

        SolucionLogistica sl = new SolucionLogistica();
        sl.setAsignacionPedidos(asignacion);
        Solucion s = new Solucion(sl);

        return s;
    }

    /* ==================== Selección probabilística ==================== */
    private RutaPedido seleccionarPorProbabilidad(List<RutaPedido> opciones, Map<String, Integer> ocupacion) {
        double[] pesos = new double[opciones.size()];
        double suma = 0.0;
        for (int i = 0; i < opciones.size(); i++) {
            RutaPedido r = opciones.get(i);
            double tauRuta = promedioFeromonaEnRuta(r);
            double etaRuta = heuristicaDeRuta(r, ocupacion);
            double val = Math.pow(Math.max(1e-6, tauRuta), alfa) * Math.pow(Math.max(1e-6, etaRuta), beta);
            pesos[i] = val; suma += val;
        }
        double u = aleatorio.nextDouble() * suma;
        double acumulado = 0.0;
        for (int i = 0; i < opciones.size(); i++) {
            acumulado += pesos[i];
            if (u <= acumulado) return opciones.get(i);
        }
        return opciones.get(opciones.size() - 1);
    }

    private double promedioFeromonaEnRuta(RutaPedido r) {
        if (r.getSecuenciaVuelos().isEmpty()) return tau0;
        double acum = 0.0;
        for (Vuelo v : r.getSecuenciaVuelos()) {
            acum += feromonas.getOrDefault(v.getId(), tau0);
        }
        return acum / r.getSecuenciaVuelos().size();
    }

    private double heuristicaDeRuta(RutaPedido r, Map<String, Integer> ocupacion) {
        double horas = duracionEnHoras(r);
        double hTiempo = 1.0 / (1.0 + horas);

        double holguraMin = holguraEnMinutos(r);
        double hHolgura = 1.0 / (1.0 + Math.exp(-holguraMin / 180.0));

        double hCapacidad = capacidadDisponiblePromedio(r, ocupacion);

        return 0.4 * hTiempo + 0.35 * hHolgura + 0.25 * hCapacidad;
    }

    /* ==================== Feromonas – actualización global ==================== */
    private void actualizarFeromonaGlobal(Solucion s) {
        for (Map.Entry<String, Double> e : feromonas.entrySet()) {
            e.setValue((1 - rho) * e.getValue());
        }
        if (s == null || s.getSolucionLogistica() == null) return;

        double refuerzo = depositoQ * Math.max(0.01, s.getFitness());
        for (RutaPedido r : s.getSolucionLogistica().getAsignacionPedidos().values()) {
            for (Vuelo v : r.getSecuenciaVuelos()) {
                String arco = v.getId();
                feromonas.put(arco, feromonas.getOrDefault(arco, tau0) + refuerzo);
            }
        }
    }

    /* ==================== Candidatos (semillas + fallback) ==================== */
    private List<RutaPedido> generarCandidatosParaPedido(Pedido p) {
        List<RutaPedido> res = new ArrayList<>();
        if (p == null) return res;

        if (soloSemillas) {
            if (p.getId() != null) {
                List<RutaPedido> plantillas = rutasSemillaPorPedidoId.get(p.getId());
                if (plantillas != null) {
                    for (RutaPedido tpl : plantillas) res.add(clonarParaPedido(p, tpl));
                }
            }
            if (res.isEmpty() && p.getLugarDestino() != null) {
                String destino = p.getLugarDestino().getCodigo();
                List<RutaPedido> plantillas = rutasSemillaPorDestino.get(destino);
                if (plantillas != null) {
                    for (RutaPedido tpl : plantillas) res.add(clonarParaPedido(p, tpl));
                }
            }
            Map<String, RutaPedido> unico = new LinkedHashMap<>();
            for (RutaPedido r : res) unico.put(claveDeRuta(r), r);
            return new ArrayList<>(unico.values());
        }

        return res;
    }

    /* ==================== Factibilidad y utilidades ==================== */
    private boolean respetaCapacidades(RutaPedido r, Map<String, Integer> ocupacion) {
        int demanda = r.getPedido().getCantidad();
        for (Vuelo v : r.getSecuenciaVuelos()) {
            int usado = ocupacion.getOrDefault(v.getId(), 0);
            int cap   = capacidadVuelo(v);
            if (usado + demanda > cap) return false;
        }
        return true;
    }

    private int capacidadVuelo(Vuelo v) {
        try { return v.getCapacidadMaxima(); }
        catch (Throwable t) {
            try { return v.getCapacidadMaxima(); }
            catch (Throwable t2) { return Integer.MAX_VALUE; }
        }
    }

    private void aplicarOcupacion(RutaPedido r, Map<String, Integer> ocupacion, int delta) {
        for (Vuelo v : r.getSecuenciaVuelos()) {
            ocupacion.merge(v.getId(), delta, Integer::sum);
        }
    }

    /* ==================== Husos y fechas ==================== */
    // Tu Aeropuerto tiene getHusoHorario(): usamos ese offset en horas.
    private ZoneId zonaDe(Aeropuerto a) {
        if (a == null) return ZoneOffset.UTC;
        try { return ZoneOffset.ofHours(a.getHusoHorario()); }
        catch (Throwable t) { return ZoneOffset.UTC; }
    }

    // Convierte hora local del vuelo a OffsetDateTime con la zona del aeropuerto
    private OffsetDateTime salidaODT(Vuelo v) {
        LocalDateTime ldt = v.getHoraSalida();
        return ldt.atZone(zonaDe(v.getOrigen())).toOffsetDateTime();
    }
    private OffsetDateTime llegadaODT(Vuelo v) {
        LocalDateTime ldt = v.getHoraLlegada();
        return ldt.atZone(zonaDe(v.getDestino())).toOffsetDateTime();
    }

    private boolean conecta(Vuelo a, Vuelo b) {
        OffsetDateTime llegadaA = llegadaODT(a);
        OffsetDateTime salidaB  = salidaODT(b);
        long min = Duration.between(llegadaA, salidaB).toMinutes();
        return min >= 45;
    }

    private double duracionEnHoras(RutaPedido r) {
        List<Vuelo> vs = r.getSecuenciaVuelos();
        OffsetDateTime os = salidaODT(vs.get(0));
        OffsetDateTime ol = llegadaODT(vs.get(vs.size() - 1));
        long min = Duration.between(os, ol).toMinutes();
        return Math.max(0.1, min / 60.0);
    }

    private boolean llegaDentroDeSLA(RutaPedido r) {
        List<Vuelo> vs = r.getSecuenciaVuelos();
        OffsetDateTime os = salidaODT(vs.get(0));
        OffsetDateTime ol = llegadaODT(vs.get(vs.size() - 1));
        boolean internacional = Boolean.TRUE.equals(r.getEsInternacional());
        int slaH = internacional ? slaHorasInternacional : slaHorasNoInternacional;
        OffsetDateTime limite = os.plusHours(slaH);
        return !ol.isAfter(limite);
    }

    private double holguraEnMinutos(RutaPedido r) {
        List<Vuelo> vs = r.getSecuenciaVuelos();
        OffsetDateTime os = salidaODT(vs.get(0));
        OffsetDateTime ol = llegadaODT(vs.get(vs.size() - 1));
        boolean internacional = Boolean.TRUE.equals(r.getEsInternacional());
        int slaH = internacional ? slaHorasInternacional : slaHorasNoInternacional;
        OffsetDateTime limite = os.plusHours(slaH);
        return Duration.between(ol, limite).toMinutes();
    }

    private double capacidadDisponiblePromedio(RutaPedido r, Map<String, Integer> ocupacion) {
        double acum = 0.0;
        for (Vuelo v : r.getSecuenciaVuelos()) {
            int usado = ocupacion.getOrDefault(v.getId(), 0);
            int cap   = Math.max(1, capacidadVuelo(v));
            double disp = Math.max(0.0, (cap - usado) / (double) cap);
            acum += disp;
        }
        return acum / Math.max(1, r.getSecuenciaVuelos().size());
    }

    /* ==================== Construcción/clonado de rutas ==================== */
    // Normaliza a UTC las fechas de la ruta (LDT), para que Solucion (que usa LDT) compare bien.
    private RutaPedido rutaDe(Pedido p, Vuelo... secuencia) {
        OffsetDateTime os = salidaODT(secuencia[0]);
        OffsetDateTime ol = llegadaODT(secuencia[secuencia.length - 1]);
        LocalDateTime salidaUTC  = os.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime llegadaUTC = ol.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();

        RutaPedido r = new RutaPedido(
                p,
                new ArrayList<>(Arrays.asList(secuencia)),
                secuencia[0].getOrigen(),
                secuencia[secuencia.length - 1].getDestino(),
                salidaUTC,
                llegadaUTC
        );
        boolean inter = false;
        for (Vuelo v : secuencia) { if (v.getEsInternacional()) { inter = true; break; } }
        r.setEsInternacional(inter);
        return r;
    }

    // No usada en modo soloSemillas, pero la dejamos por compatibilidad.
    private RutaPedido rutaDe(Vuelo... secuencia) {
        return rutaDe(null, secuencia);
    }

    // Clona plantilla asignando p y recalculando fechas a UTC desde la secuencia
    private RutaPedido clonarParaPedido(Pedido p, RutaPedido base) {
        List<Vuelo> seq = new ArrayList<>(base.getSecuenciaVuelos());
        return rutaDe(p, seq.toArray(new Vuelo[0]));
    }

    // Plantilla sin pedido; fechas normalizadas a UTC
    private RutaPedido clonarBasicoSinPedido(RutaPedido base) {
        List<Vuelo> seq = new ArrayList<>(base.getSecuenciaVuelos());
        return rutaDe(null, seq.toArray(new Vuelo[0]));
    }

    /* =================== Auxiliares ==================== */
    private Set<String> extraerArcos(Solucion s) {
        Set<String> arcos = new HashSet<>();
        if (s == null || s.getSolucionLogistica() == null) return arcos;
        Map<Pedido, RutaPedido> asig = s.getSolucionLogistica().getAsignacionPedidos();
        if (asig == null) return arcos;
        for (RutaPedido r : asig.values()) {
            for (Vuelo v : r.getSecuenciaVuelos()) arcos.add(v.getId());
        }
        return arcos;
    }

    private void inicializarFeromonas(double valor) {
        feromonas.clear();
        for (Vuelo v : vuelos) feromonas.put(v.getId(), valor);
    }

    private static String claveDeRuta(RutaPedido r) {
        return r.getSecuenciaVuelos().stream().map(Vuelo::getId).collect(Collectors.joining("→"));
    }

    private void dedupIndice(Map<String, List<RutaPedido>> indice) {
        for (Map.Entry<String, List<RutaPedido>> ent : indice.entrySet()) {
            Map<String, RutaPedido> unico = new LinkedHashMap<>();
            for (RutaPedido r : ent.getValue()) unico.put(claveDeRuta(r), r);
            ent.setValue(new ArrayList<>(unico.values()));
        }
    }

    /* ==================== Getters ==================== */
    public Solucion obtenerMejorSolucion() { return mejorHastaAhora; }
    public Map<String, Double> obtenerFeromonas() { return Collections.unmodifiableMap(feromonas); }
    public Solucion getBestSoFar() { return mejorHastaAhora; }
}
