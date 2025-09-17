package com.morapack.models;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ACSMoraPack – Mejorador (ACS) SOLO sobre semillas GRASP.
 * - No construye desde cero: parte de una semilla y prueba reemplazos de rutas
 *   usando únicamente alternativas presentes en las semillas (por id de pedido
 *   o por destino como respaldo).
 * - Respeta capacidades, conexiones y SLA (con husos).
 * - Fitness: SIEMPRE se recalcula creando Solucion con (sl, pedidos.size()).
 */
public class ACSMoraPack {

    /* ========= Parámetros ACS ========= */
    private int numeroHormigas    = 40;
    private int numeroIteraciones = 80;
    public  double alfa = 1.0;
    public  double beta = 3.0;
    public  double rho  = 0.10;
    public  double xi   = 0.10;
    public  double tau0 = 0.01;
    public  double depositoQ = 1.0;

    public int slaHorasNoInternacional = 48;
    public int slaHorasInternacional   = 72;

    /* ========= Datos base ========= */
    private final List<Pedido> pedidos;
    private final List<Vuelo>  vuelos;

    private final Map<String, Double> feromonas = new HashMap<>();
    private final Random aleatorio = new Random(1234);

    private Solucion mejorHastaAhora;
    private List<Solucion> semillas = new ArrayList<>();

    // Índices de rutas de semillas (plantillas sin pedido)
    private final Map<String, List<RutaPedido>> rutasSemillaPorPedidoId = new HashMap<>();
    private final Map<String, List<RutaPedido>> rutasSemillaPorDestino  = new HashMap<>();

    public ACSMoraPack(List<Pedido> pedidos, List<Vuelo> vuelos) {
        this.pedidos = Objects.requireNonNull(pedidos);
        this.vuelos  = Objects.requireNonNull(vuelos);
        inicializarFeromonas(tau0);
    }

    /* ========= Configuración pública ========= */
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

        // Refuerzo inicial (feromonas) en arcos que aparecen en semillas
        if (!this.semillas.isEmpty()) {
            double mejorFit = this.semillas.stream().mapToDouble(Solucion::getFitness).max().orElse(1.0);
            double refuerzoBase = 0.05;
            for (Solucion s : this.semillas) {
                double peso = Math.max(0.1, s.getFitness() / mejorFit);
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

    /* ========= Ejecución (refinamiento) ========= */
    public Solucion ejecutar() {
        // Arranca desde la mejor semilla (nunca peor que GRASP)
        if (mejorHastaAhora == null && semillas != null && !semillas.isEmpty()) {
            mejorHastaAhora = semillas.stream()
                    .filter(Objects::nonNull)
                    .max(Comparator.comparingDouble(Solucion::getFitness))
                    .map(this::clonarComoNuevaSolucion) // normaliza y fija totalPedidos
                    .orElse(null);
        }
        if (mejorHastaAhora == null) {
            // Sin semillas válidas: devolver vacía (con fitness calculado)
            SolucionLogistica sl = new SolucionLogistica();
            sl.setAsignacionPedidos(new LinkedHashMap<>());
            return new Solucion(sl, pedidos.size());
        }

        for (int it = 0; it < numeroIteraciones; it++) {
            Solucion mejorIteracion = null;

            for (int h = 0; h < numeroHormigas; h++) {
                Solucion s = construirDesdeSemillaConRefinamiento();
                if (mejorIteracion == null || s.getFitness() > mejorIteracion.getFitness()) {
                    mejorIteracion = s;
                }
            }

            if (mejorIteracion != null &&
                    (mejorHastaAhora == null || mejorIteracion.getFitness() > mejorHastaAhora.getFitness())) {
                mejorHastaAhora = mejorIteracion;
            }

            // Evitar aprender soluciones hundidas (muy negativo)
            if (mejorHastaAhora != null && mejorHastaAhora.getFitness() > -900) {
                actualizarFeromonaGlobal(mejorHastaAhora);
            }
        }
        return mejorHastaAhora;
    }

    /* ========= Núcleo: refinamiento sobre semilla ========= */

    private Solucion construirDesdeSemillaConRefinamiento() {
        Solucion sem = elegirSemilla();
        if (sem == null) return mejorHastaAhora;

        // Copia profunda (y reconstruye ocupación)
        Map<String, Integer> ocupacion = new HashMap<>();
        Solucion actual = copiarSolucion(sem, ocupacion);

        Map<Pedido, RutaPedido> asignacion = actual.getSolucionLogistica().getAsignacionPedidos();
        if (asignacion == null) {
            asignacion = new LinkedHashMap<>();
            actual.getSolucionLogistica().setAsignacionPedidos(asignacion);
        }

        // Intentar mejorar ~30% de pedidos
        List<Pedido> orden = new ArrayList<>(pedidos);
        Collections.shuffle(orden, aleatorio);
        int intentos = Math.max(1, (int)Math.round(orden.size() * 0.30));

        for (int i = 0; i < intentos; i++) {
            Pedido p = orden.get(i);

            List<RutaPedido> cand = generarCandidatosParaPedido(p);
            if (cand.isEmpty()) continue;

            RutaPedido rActual = asignacion.get(p);

            List<RutaPedido> fact = cand.stream()
                    .filter(r -> respetaCapacidadesSwap(r, rActual, p, ocupacion))
                    .filter(this::llegaDentroDeSLA)
                    .collect(Collectors.toList());
            if (fact.isEmpty()) continue;

            RutaPedido elegida = seleccionarPorProbabilidad(fact, ocupacion);

            if (rActual == null || !claveDeRuta(rActual).equals(claveDeRuta(elegida))) {
                if (rActual != null) aplicarOcupacion(rActual, ocupacion, -p.getCantidad());
                aplicarOcupacion(elegida, ocupacion, +p.getCantidad());
                asignacion.put(p, elegida);

                // actualización local ACS
                for (Vuelo v : elegida.getSecuenciaVuelos()) {
                    String arco = v.getId();
                    double tauAnt = feromonas.getOrDefault(arco, tau0);
                    feromonas.put(arco, (1 - xi) * tauAnt + xi * tau0);
                }
            }
        }

        // *** Recalcular fitness creando una NUEVA Solucion (calculaFitness interno) ***
        return new Solucion(actual.getSolucionLogistica(), pedidos.size());
    }

    private Solucion elegirSemilla() {
        if (semillas == null || semillas.isEmpty()) return null;
        double[] pesos = new double[semillas.size()];
        double suma = 0.0;
        for (int i = 0; i < semillas.size(); i++) {
            Solucion s = semillas.get(i);
            double fit = Math.max(1e-6, s.getFitness());
            // pequeña señal de feromona media de la semilla
            double tauAvg = 0.0; int cnt = 0;
            if (s.getSolucionLogistica() != null && s.getSolucionLogistica().getAsignacionPedidos() != null) {
                for (RutaPedido r : s.getSolucionLogistica().getAsignacionPedidos().values()) {
                    for (Vuelo v : r.getSecuenciaVuelos()) {
                        tauAvg += feromonas.getOrDefault(v.getId(), tau0); cnt++;
                    }
                }
            }
            tauAvg = (cnt > 0) ? tauAvg / cnt : tau0;
            double w = fit * 0.8 + tauAvg * 0.2;
            pesos[i] = w; suma += w;
        }
        double u = aleatorio.nextDouble() * suma, acc = 0.0;
        for (int i = 0; i < pesos.length; i++) { acc += pesos[i]; if (u <= acc) return semillas.get(i); }
        return semillas.get(semillas.size() - 1);
    }

    private Solucion clonarComoNuevaSolucion(Solucion base) {
        Map<Pedido, RutaPedido> asigBase = (base.getSolucionLogistica() != null)
                ? base.getSolucionLogistica().getAsignacionPedidos() : null;

        Map<Pedido, RutaPedido> asigNueva = new LinkedHashMap<>();
        if (asigBase != null) {
            for (Map.Entry<Pedido, RutaPedido> e : asigBase.entrySet()) {
                Pedido p = e.getKey();
                RutaPedido r = e.getValue();
                if (p == null || r == null) continue;
                asigNueva.put(p, clonarParaPedido(p, r)); // normaliza fechas a UTC
            }
        }
        SolucionLogistica sl = new SolucionLogistica();
        sl.setAsignacionPedidos(asigNueva);
        return new Solucion(sl, pedidos.size());
    }

    private Solucion copiarSolucion(Solucion base, Map<String,Integer> ocupacionOut) {
        Map<Pedido, RutaPedido> asigBase = (base.getSolucionLogistica() != null)
                ? base.getSolucionLogistica().getAsignacionPedidos() : null;

        Map<Pedido, RutaPedido> asigNueva = new LinkedHashMap<>();
        Map<String, Integer> ocupacion = new HashMap<>();

        if (asigBase != null) {
            for (Map.Entry<Pedido, RutaPedido> e : asigBase.entrySet()) {
                Pedido p = e.getKey();
                RutaPedido r = e.getValue();
                if (p == null || r == null) continue;

                RutaPedido r2 = clonarParaPedido(p, r);
                asigNueva.put(p, r2);

                int demanda = p.getCantidad();
                for (Vuelo v : r2.getSecuenciaVuelos()) {
                    ocupacion.merge(v.getId(), demanda, Integer::sum);
                }
            }
        }
        if (ocupacionOut != null) { ocupacionOut.clear(); ocupacionOut.putAll(ocupacion); }

        SolucionLogistica sl = new SolucionLogistica();
        sl.setAsignacionPedidos(asigNueva);
        return new Solucion(sl, pedidos.size());
    }

    /* ========= Selección y heurísticas ========= */

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
        double u = aleatorio.nextDouble() * suma, acc = 0.0;
        for (int i = 0; i < opciones.size(); i++) {
            acc += pesos[i];
            if (u <= acc) return opciones.get(i);
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

    /* ========= Feromonas global ========= */
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

    /* ========= Candidatos SOLO desde semillas ========= */
    private List<RutaPedido> generarCandidatosParaPedido(Pedido p) {
        List<RutaPedido> res = new ArrayList<>();
        if (p == null) return res;

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

    /* ========= Factibilidad y utilidades ========= */

    private boolean respetaCapacidades(RutaPedido r, Map<String, Integer> ocupacion) {
        int demanda = r.getPedido().getCantidad();
        for (Vuelo v : r.getSecuenciaVuelos()) {
            int usado = ocupacion.getOrDefault(v.getId(), 0);
            int cap   = capacidadVuelo(v);
            if (usado + demanda > cap) return false;
        }
        return true;
    }

    private boolean respetaCapacidadesSwap(RutaPedido nueva, RutaPedido actual, Pedido p, Map<String, Integer> ocupacion) {
        int demanda = p.getCantidad();
        if (actual != null) {
            for (Vuelo v : actual.getSecuenciaVuelos()) {
                int usado = ocupacion.getOrDefault(v.getId(), 0);
                ocupacion.put(v.getId(), usado - demanda);
            }
        }
        boolean ok = respetaCapacidades(nueva, ocupacion);
        if (actual != null) {
            for (Vuelo v : actual.getSecuenciaVuelos()) {
                ocupacion.merge(v.getId(), demanda, Integer::sum);
            }
        }
        return ok;
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

    /* ========= Husos y fechas ========= */

    private ZoneId zonaDe(Aeropuerto a) {
        if (a == null) return ZoneOffset.UTC;
        try { return ZoneOffset.ofHours(a.getHusoHorario()); }
        catch (Throwable t) { return ZoneOffset.UTC; }
    }

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

    /* ========= Construcción/clonado de rutas (sin crear nuevas) ========= */

    // Normaliza fechas a UTC (LDT), para coherencia con Solucion (usa LDT)
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

    private RutaPedido clonarParaPedido(Pedido p, RutaPedido base) {
        List<Vuelo> seq = new ArrayList<>(base.getSecuenciaVuelos());
        return rutaDe(p, seq.toArray(new Vuelo[0]));
    }

    private RutaPedido clonarBasicoSinPedido(RutaPedido base) {
        List<Vuelo> seq = new ArrayList<>(base.getSecuenciaVuelos());
        return rutaDe(null, seq.toArray(new Vuelo[0]));
    }

    /* ========= Auxiliares ========= */

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
}
