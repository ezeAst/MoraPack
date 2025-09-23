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
 * Respeta:
 *   - Escalas >= 60 min entre vuelos consecutivos
 *   - Capacidad temporal de aeropuertos: ventana [llegada, llegada+2h)
 *   - Capacidad de vuelos
 *   - SLA con husos
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

        // Refuerzo inicial en arcos que aparecen en semillas
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
        if (mejorHastaAhora == null && semillas != null && !semillas.isEmpty()) {
            mejorHastaAhora = semillas.stream()
                    .filter(Objects::nonNull)
                    .max(Comparator.comparingDouble(Solucion::getFitness))
                    .map(this::clonarComoNuevaSolucion)
                    .orElse(null);
        }
        if (mejorHastaAhora == null) {
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

        Map<String, Integer> ocupacion = new HashMap<>();
        Solucion actual = copiarSolucion(sem, ocupacion);

        Map<Pedido, RutaPedido> asignacion = actual.getSolucionLogistica().getAsignacionPedidos();
        if (asignacion == null) {
            asignacion = new LinkedHashMap<>();
            actual.getSolucionLogistica().setAsignacionPedidos(asignacion);
        }
        // referencia efectivamente final para lambdas
        final Map<Pedido, RutaPedido> asignacionRef = asignacion;

        List<Pedido> orden = new ArrayList<>(pedidos);
        Collections.shuffle(orden, aleatorio);
        int intentos = Math.max(1, (int) Math.round(orden.size() * 0.30));

        for (int i = 0; i < intentos; i++) {
            Pedido p = orden.get(i);
            final Pedido pRef = p;

            List<RutaPedido> cand = generarCandidatosParaPedido(pRef);
            if (cand.isEmpty()) continue;

            RutaPedido rActual = asignacionRef.get(pRef);
            final RutaPedido rActualRef = rActual;

            List<RutaPedido> fact = cand.stream()
                    .filter(this::conexionesValidas) // >= 60 min entre vuelos
                    .filter(r -> respetaCapacidadesSwap(r, rActualRef, pRef, ocupacion))
                    .filter(r -> respetaCapacidadAlmacenesTemporalesSwap(r, rActualRef, pRef, asignacionRef))
                    .filter(this::llegaDentroDeSLA)
                    .collect(Collectors.toList());
            if (fact.isEmpty()) continue;

            RutaPedido elegida = seleccionarPorProbabilidad(fact, ocupacion);

            if (rActualRef == null || !claveDeRuta(rActualRef).equals(claveDeRuta(elegida))) {
                if (rActualRef != null) aplicarOcupacion(rActualRef, ocupacion, -pRef.getCantidad());
                aplicarOcupacion(elegida, ocupacion, +pRef.getCantidad());
                asignacionRef.put(pRef, elegida);

                // actualización local ACS
                for (Vuelo v : elegida.getSecuenciaVuelos()) {
                    String arco = v.getId();
                    double tauAnt = feromonas.getOrDefault(arco, tau0);
                    feromonas.put(arco, (1 - xi) * tauAnt + xi * tau0);
                }
            }
        }
        repararViolacionesAlmacenes(asignacionRef);
        return new Solucion(actual.getSolucionLogistica(), pedidos.size());
    }

    // ===== Reparación post-proceso de almacenes (misma semántica que Solucion.java) =====
    private void repararViolacionesAlmacenes(final Map<Pedido, RutaPedido> asignacion) {
        if (asignacion == null || asignacion.isEmpty()) return;

        // Construye el historial: por aeropuerto, lista de (llegada, libera, q, pedido)
        class Ev { String code; LocalDateTime t0,t1; int q; Pedido p; RutaPedido r;
            Ev(String c, LocalDateTime t0, int q, Pedido p, RutaPedido r){ this.code=c; this.t0=t0; this.t1=t0.plusHours(2); this.q=q; this.p=p; this.r=r; }
            boolean ocupa(LocalDateTime t){ return !t.isBefore(t0) && t.isBefore(t1); }
        }
        Map<String,List<Ev>> hist = new HashMap<>();
        for (Map.Entry<Pedido,RutaPedido> e : asignacion.entrySet()){
            Pedido p = e.getKey(); RutaPedido r = e.getValue();
            if (p==null || r==null) continue;
            List<Vuelo> vs = r.getSecuenciaVuelos();
            for (int i=0;i<vs.size()-1;i++){
                Vuelo v = vs.get(i);
                if (v.getDestino()!=null && v.getHoraLlegada()!=null)
                    hist.computeIfAbsent(v.getDestino().getCodigo(),k->new ArrayList<>()).add(new Ev(v.getDestino().getCodigo(), v.getHoraLlegada(), p.getCantidad(), p, r));
            }
            if (!vs.isEmpty()){
                Vuelo last = vs.get(vs.size()-1);
                if (last.getDestino()!=null && last.getHoraLlegada()!=null)
                    hist.computeIfAbsent(last.getDestino().getCodigo(),k->new ArrayList<>()).add(new Ev(last.getDestino().getCodigo(), last.getHoraLlegada(), p.getCantidad(), p, r));
            }
        }

        // util: buscar objeto Aeropuerto por código
        java.util.function.Function<String, Aeropuerto> findA = code -> {
            for (RutaPedido r : asignacion.values()){
                if (r==null) continue;
                for (Vuelo v : r.getSecuenciaVuelos()){
                    if (v.getDestino()!=null && code.equals(v.getDestino().getCodigo())) return v.getDestino();
                    if (v.getOrigen()!=null  && code.equals(v.getOrigen().getCodigo()))  return v.getOrigen();
                }
            }
            return null;
        };

        boolean cambio; int passes=0;
        do {
            cambio = false;
            for (Map.Entry<String,List<Ev>> ent : new ArrayList<>(hist.entrySet())) {
                String code = ent.getKey(); List<Ev> evs = ent.getValue();
                if (evs.isEmpty()) continue;

                Aeropuerto A = findA.apply(code);
                if (A==null) continue;
                int cap = A.getCapacidad(), base = A.getCapacidadAct();

                // momentos críticos = TODAS las llegadas de este aeropuerto
                TreeSet<LocalDateTime> momentos = new TreeSet<>();
                for (Ev ev: evs) momentos.add(ev.t0);

                for (LocalDateTime t : momentos) {
                    int ocup = base;
                    List<Ev> contrib = new ArrayList<>();
                    for (Ev ev: evs) if (ev.ocupa(t)) { ocup += ev.q; contrib.add(ev); }

                    if (ocup <= cap) continue; // sin violación en t

                    // intentar mover pedidos contribuyentes (de mayor a menor q)
                    contrib.sort((a,b)->Integer.compare(b.q,a.q));

                    for (Ev ev : contrib) {
                        Pedido p = ev.p;
                        RutaPedido rActual = asignacion.get(p);
                        if (rActual == null) continue;

                        // generar candidatos desde semillas y filtrar con EXACTA semántica del fitness
                        List<RutaPedido> cand = generarCandidatosParaPedido(p);
                        // reconstruir ocupación de vuelos real para este estado (para respetaCapacidadesSwap)
                        Map<String,Integer> occVuelos = new HashMap<>();
                        for (Map.Entry<Pedido,RutaPedido> e2 : asignacion.entrySet()){
                            Pedido pp = e2.getKey(); RutaPedido rr = e2.getValue();
                            if (pp==null || rr==null) continue;
                            int qpp = pp.getCantidad();
                            for (Vuelo vv : rr.getSecuenciaVuelos()) occVuelos.merge(vv.getId(), qpp, Integer::sum);
                        }

                        cand = cand.stream()
                                .filter(this::conexionesValidas)
                                .filter(r -> respetaCapacidadesSwap(r, rActual, p, occVuelos))
                                .filter(r -> respetaCapacidadAlmacenesTemporalesSwap(r, rActual, p, asignacion))
                                .filter(this::llegaDentroDeSLA)
                                .collect(Collectors.toList());

                        boolean reemplazo = false;
                        for (RutaPedido rNew : cand) {
                            // comprobar que en el mismo instante t baja bajo el límite
                            int ocup2 = base;
                            // todos menos el actual contribuyente
                            for (Ev e3 : evs) if (e3 != ev && e3.ocupa(t)) ocup2 += e3.q;
                            // sumar aportes de rNew en ese aeropuerto en t
                            for (Vuelo v2 : rNew.getSecuenciaVuelos()){
                                if (v2.getDestino()!=null && code.equals(v2.getDestino().getCodigo()) && v2.getHoraLlegada()!=null) {
                                    LocalDateTime ta = v2.getHoraLlegada();
                                    if (!t.isBefore(ta) && t.isBefore(ta.plusHours(2))) ocup2 += p.getCantidad();
                                }
                            }
                            if (ocup2 <= cap) {
                                // aplicar swap: actualizar asignación y el historial de eventos
                                asignacion.put(p, rNew);
                                evs.removeIf(z -> z.p == p);
                                List<Vuelo> vsN = rNew.getSecuenciaVuelos();
                                for (int i=0;i<vsN.size()-1;i++){
                                    Vuelo v2 = vsN.get(i);
                                    if (v2.getDestino()!=null && v2.getHoraLlegada()!=null && code.equals(v2.getDestino().getCodigo()))
                                        evs.add(new Ev(code, v2.getHoraLlegada(), p.getCantidad(), p, rNew));
                                }
                                Vuelo lastN = vsN.get(vsN.size()-1);
                                if (lastN.getDestino()!=null && lastN.getHoraLlegada()!=null && code.equals(lastN.getDestino().getCodigo()))
                                    evs.add(new Ev(code, lastN.getHoraLlegada(), p.getCantidad(), p, rNew));
                                cambio = true; reemplazo = true;
                                break;
                            }
                        }

                        if (!reemplazo) {
                            // último recurso: desasignar pedido para sacar la violación
                            asignacion.remove(p);
                            evs.removeIf(z -> z.p == p);
                            cambio = true;
                        }

                        // si ya quedó bajo capacidad en t, pasa al siguiente momento
                        int check = base;
                        for (Ev e4 : evs) if (e4.ocupa(t)) check += e4.q;
                        if (check <= cap) break;
                    }
                }
            }
        } while (cambio && ++passes < 5);
    }


    private Solucion elegirSemilla() {
        if (semillas == null || semillas.isEmpty()) return null;
        double[] pesos = new double[semillas.size()];
        double suma = 0.0;
        for (int i = 0; i < semillas.size(); i++) {
            Solucion s = semillas.get(i);
            double fit = Math.max(1e-6, s.getFitness());

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
                asigNueva.put(p, clonarParaPedido(p, r));
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

    // ========= NUEVO: Validación temporal de almacenes (2h) para swaps/candidatos =========
    // Sustituir COMPLETO el método por este:
    private boolean respetaCapacidadAlmacenesTemporalesSwap(
            RutaPedido nueva, RutaPedido actual, Pedido p,
            Map<Pedido, RutaPedido> asignacionActual) {

        class Ev {
            final String code; final LocalDateTime llegada; final LocalDateTime libera; final int q;
            Ev(String c, LocalDateTime t0, int q){ this.code=c; this.llegada=t0; this.libera=t0.plusHours(2); this.q=q; }
            boolean ocupa(LocalDateTime t){ return !t.isBefore(llegada) && t.isBefore(libera); }
        }

        // 1) recolectar eventos existentes (escalas + destino), excluyendo la ruta actual si es swap
        List<Ev> evs = new ArrayList<>();
        if (asignacionActual != null) {
            for (Map.Entry<Pedido, RutaPedido> e : asignacionActual.entrySet()) {
                RutaPedido r = e.getValue();
                if (r == null) continue;
                if (actual != null && r == actual) continue; // liberar aportes de la ruta a reemplazar

                int q = (e.getKey() != null) ? e.getKey().getCantidad() : 0;
                List<Vuelo> vs = r.getSecuenciaVuelos();

                for (int i = 0; i < vs.size() - 1; i++) {
                    Vuelo v = vs.get(i);
                    if (v.getDestino()!=null && v.getHoraLlegada()!=null) {
                        evs.add(new Ev(v.getDestino().getCodigo(), v.getHoraLlegada(), q));
                    }
                }
                if (!vs.isEmpty()) {
                    Vuelo last = vs.get(vs.size()-1);
                    if (last.getDestino()!=null && last.getHoraLlegada()!=null) {
                        evs.add(new Ev(last.getDestino().getCodigo(), last.getHoraLlegada(), q));
                    }
                }
            }
        }

        // 2) chequear cada llegada de la NUEVA ruta (escalas + destino) en su momento de llegada
        int qNueva = p.getCantidad();
        for (Vuelo v : nueva.getSecuenciaVuelos()) {
            Aeropuerto a = v.getDestino();
            LocalDateTime tArr = v.getHoraLlegada();
            if (a == null || tArr == null) return false;

            int ocup = a.getCapacidadAct();      // base, igual que usa Solucion.java
            int cap  = a.getCapacidad();

            // sumar todos los eventos existentes “vivos” en el instante tArr
            for (Ev ev : evs) {
                if (a.getCodigo().equals(ev.code) && ev.ocupa(tArr)) {
                    ocup += ev.q;
                }
            }
            // sumar la propia llegada del candidato
            ocup += qNueva;

            if (ocup > cap) return false;
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

    // ======== 60 minutos mínimo entre conexiones (antes 45) ========
    private boolean conecta(Vuelo a, Vuelo b) {
        OffsetDateTime llegadaA = llegadaODT(a);
        OffsetDateTime salidaB  = salidaODT(b);
        long min = Duration.between(llegadaA, salidaB).toMinutes();
        return min >= 60;
    }

    private boolean conexionesValidas(RutaPedido r) {
        List<Vuelo> vs = r.getSecuenciaVuelos();
        for (int i = 0; i < vs.size() - 1; i++) {
            if (!conecta(vs.get(i), vs.get(i + 1))) return false;
        }
        return true;
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
