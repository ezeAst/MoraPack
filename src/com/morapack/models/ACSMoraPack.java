package com.morapack.models;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

public class ACSMoraPack {

    /* ======= Parámetros ACS (puedes ajustar) ======= */
    public double alpha = 1.0;   // peso feromona τ
    public double beta  = 3.0;   // peso heurística η
    public double rho   = 0.10;  // evaporación global
    public double xi    = 0.10;  // actualización local
    public double q0    = 0.90;  // prob. de explotación
    public double tau0  = 0.10;  // feromona inicial
    public double Q     = 1.0;   // constante de refuerzo

    public int iterACS = 50;     // iteraciones
    public int numHormigas = 10; // hormigas/iteración
    public int kPorPedido = 5;   // máximo de rutas candidatas por pedido

    private final Random rnd = new Random();

    // Construido a partir de las semillas GRASP
    private final Map<Pedido, List<RutaPedido>> candidatosPorPedido = new HashMap<>();
    private final Map<Pedido, double[]> tau = new HashMap<>();

    /* ======= ctor: solo semillas de GRASP ======= */
    public ACSMoraPack(List<Solucion> semillasGrasp, int kPorPedido) {
        if (semillasGrasp == null || semillasGrasp.isEmpty()) {
            throw new IllegalArgumentException("Se requieren semillas GRASP.");
        }
        this.kPorPedido = kPorPedido;
        construirCandidatosDesdeSemillas(semillasGrasp);
        inicializarFeromonas();
        calentarFeromonasConSemillas(semillasGrasp);
    }

    /** Ejecuta ACS y devuelve el mejor plan.
     *  coste: plan -> new Solucion(plan).getFitness()*/
    public SolucionLogistica ejecutar(List<Pedido> pedidos, ToDoubleFunction<SolucionLogistica> coste) {
        // orden por deadline ascendente (estable)
        List<Pedido> orden = new ArrayList<>(pedidos);
        //orden.sort(Comparator.comparing(Pedido::getFechaLimite));

        SolucionLogistica mejorGlobal = null;
        double mejorCoste = Double.POSITIVE_INFINITY;

        for (int it = 0; it < iterACS; it++) {
            SolucionLogistica mejorIter = null;
            double mejorCosteIter = Double.POSITIVE_INFINITY;

            for (int h = 0; h < numHormigas; h++) {
                SolucionLogistica plan = construirPlanHormiga(orden);
                if (plan == null) continue;

                double c = coste.applyAsDouble(plan);
                if (Double.isInfinite(c)) continue;

                if (c < mejorCosteIter) {
                    mejorCosteIter = c;
                    mejorIter = plan;
                }
            }

            evaporarGlobal();
            if (mejorIter != null) {
                reforzarConPlan(mejorIter, mejorCosteIter);
                if (mejorCosteIter < mejorCoste) {
                    mejorCoste = mejorCosteIter;
                    mejorGlobal = mejorIter;
                }
            }
        }
        return mejorGlobal;
    }

    /* ======= Construcción de hormiga ======= */
    private SolucionLogistica construirPlanHormiga(List<Pedido> pedidosOrdenados) {
        Map<Vuelo, Integer> ocupacion = new HashMap<>(); // capacidad tentativa por vuelo
        SolucionLogistica plan = new SolucionLogistica();

        for (Pedido p : pedidosOrdenados) {
            List<RutaPedido> lista = candidatosPorPedido.get(p);
            if (lista == null || lista.isEmpty()) return null;

            // Filtra rutas que aún caben (capacidad y no cancelados)
            List<Integer> idxFactibles = new ArrayList<>();
            for (int i = 0; i < lista.size(); i++) {
                if (esFactibleConCapacidad(lista.get(i), ocupacion, p.getCantidad())) {
                    idxFactibles.add(i);
                }
            }
            if (idxFactibles.isEmpty()) return null;

            int elegido = elegirRutaACS(p, lista, tau.get(p), idxFactibles);
            RutaPedido r = lista.get(elegido);

            // actualización local
            double[] t = tau.get(p);
            t[elegido] = (1.0 - xi) * t[elegido] + xi * tau0;

            // reservar ocupación
            reservar(r, ocupacion, p.getCantidad());

            plan.agregarRutaPedido(p, r);
        }
        return plan;
    }

    private int elegirRutaACS(Pedido p, List<RutaPedido> rutas, double[] t, List<Integer> idxFactibles) {
        double q = rnd.nextDouble();
        if (q < q0) {
            // explotación: argmax τ^α * η^β
            double mejor = -1.0;
            int mejorInd = idxFactibles.get(0);
            for (int i : idxFactibles) {
                double val = Math.pow(t[i], alpha) * Math.pow(heuristica(rutas.get(i)), beta);
                if (val > mejor) { mejor = val; mejorInd = i; }
            }
            return mejorInd;
        } else {
            // exploración: ruleta proporcional
            double suma = 0.0;
            double[] prob = new double[rutas.size()];
            for (int i : idxFactibles) {
                prob[i] = Math.pow(t[i], alpha) * Math.pow(heuristica(rutas.get(i)), beta);
                suma += prob[i];
            }
            double r = rnd.nextDouble() * suma;
            double acum = 0.0;
            for (int i : idxFactibles) {
                acum += prob[i];
                if (acum >= r) return i;
            }
            return idxFactibles.get(idxFactibles.size() - 1);
        }
    }

    /* ======= Feromonas ======= */
    private void evaporarGlobal() {
        for (double[] arr : tau.values()) {
            for (int i = 0; i < arr.length; i++) {
                arr[i] = (1.0 - rho) * arr[i];
            }
        }
    }

    private void reforzarConPlan(SolucionLogistica plan, double costePlan) {
        double dTau = Q / Math.max(1e-6, costePlan);
        for (Map.Entry<Pedido, RutaPedido> e : plan.getAsignacionPedidos().entrySet()) {
            Pedido p = e.getKey();
            List<RutaPedido> lista = candidatosPorPedido.get(p);
            if (lista == null) continue;
            int idx = indexOfRutaPorClave(lista, e.getValue());
            if (idx >= 0) {
                tau.get(p)[idx] += dTau;
            }
        }
    }

    /* ======= Heurística η (pocas escalas, corta, con holgura) ======= */
    private double heuristica(RutaPedido r) {
        int h = hops(r);
        long dur = duracionMin(r);
        long holg = Math.max(0L, holguraMin(r));

        double fHops = 1.0 / (1 + h);               // 1, 0.5, 0.33...
        double fDur  = 1.0 / (1 + dur / 60.0);      // penaliza duración larga
        double fHolg = 1.0 + Math.min(holg, 12*60) / (12.0*60.0); // hasta 2.0 si holg ≥ 12h

        return fHops * fDur * fHolg;
    }

    private static int hops(RutaPedido r) {
        return r.getSecuenciaVuelos().size();
    }

    private static long duracionMin(RutaPedido r) {
        List<Vuelo> vs = r.getSecuenciaVuelos();
        LocalDateTime s = vs.get(0).getHoraSalida();
        LocalDateTime l = vs.get(vs.size()-1).getHoraLlegada();
        return Duration.between(s, l).toMinutes();
    }

    private static int slaHoras(Aeropuerto origen, Aeropuerto destino) {
        String c1 = origen != null && origen.getContinente() != null ? origen.getContinente().trim() : "";
        String c2 = destino != null && destino.getContinente() != null ? destino.getContinente().trim() : "";
        boolean mismoContinente = !c1.isEmpty() && c1.equalsIgnoreCase(c2);
        return mismoContinente ? 48 : 72;
    }

    private static long holguraMin(RutaPedido r) {
        List<Vuelo> vs = r.getSecuenciaVuelos();
        if (vs == null || vs.isEmpty()) return Long.MIN_VALUE; // sin vuelos: no hay holgura

        // Tomamos salida y llegada reales de la ruta
        Vuelo primero = vs.get(0);
        Vuelo ultimo   = vs.get(vs.size() - 1);

        // Si tus vuelos usan OffsetDateTime, usa esos tipos; si son LocalDateTime, mantén LocalDateTime.
        // Aquí asumo OffsetDateTime en los vuelos (ajusta si es LocalDateTime).
        java.time.OffsetDateTime salida   = OffsetDateTime.from(primero.getHoraSalida());
        java.time.OffsetDateTime llegada  = OffsetDateTime.from(ultimo.getHoraLlegada());

        Aeropuerto origen  = primero.getOrigen();
        Aeropuerto destino = r.getPedido().getLugarDestino();

        // SLA calculado desde la "fechaRegistro" del pedido, llevando ese LocalDateTime al offset de la ruta
        java.time.OffsetDateTime base = r.getPedido()
                .getFechaRegistro()
                .atOffset(salida.getOffset());

        int horasSla = slaHoras(origen, destino);
        java.time.OffsetDateTime limiteVirtual = base.plusHours(horasSla);

        // holgura = minutos que sobran (o faltan) respecto al límite virtual
        return java.time.Duration.between(llegada, limiteVirtual).toMinutes();
    }

    /* ======= Capacidad por hormiga (simple, sin tracker global) ======= */
    private static boolean esFactibleConCapacidad(RutaPedido ruta, Map<Vuelo,Integer> ocupacion, int unidades) {
        for (Vuelo v : ruta.getSecuenciaVuelos()) {
            if (v.getEstadoVuelo() == EstadoVuelo.CANCELADO) return false;
            int usados = ocupacion.getOrDefault(v, 0);
            if (usados + unidades > v.getCapacidadMaxima()) return false;
        }
        return true;
    }

    private static void reservar(RutaPedido ruta, Map<Vuelo,Integer> ocupacion, int unidades) {
        for (Vuelo v : ruta.getSecuenciaVuelos()) {
            ocupacion.put(v, ocupacion.getOrDefault(v, 0) + unidades);
        }
    }

    /* ======= Construcción de candidatos desde GRASP ======= */
    private void construirCandidatosDesdeSemillas(List<Solucion> semillasGrasp) {
        for (Solucion s : semillasGrasp) {
            SolucionLogistica plan = s.getSolucionLogistica();
            for (Map.Entry<Pedido, RutaPedido> e : plan.getAsignacionPedidos().entrySet()) {
                Pedido p = e.getKey();
                RutaPedido r = e.getValue();
                candidatosPorPedido.computeIfAbsent(p, kk -> new ArrayList<>());
                List<RutaPedido> lista = candidatosPorPedido.get(p);
                if (!contieneRutaPorClave(lista, r)) {
                    lista.add(r);
                }
            }
        }
        // ordenar por calidad y recortar a K por pedido
        for (Map.Entry<Pedido, List<RutaPedido>> e : candidatosPorPedido.entrySet()) {
            List<RutaPedido> lista = e.getValue();
            lista.sort(Comparator
                    .comparingInt(ACSMoraPack::hops)
                    .thenComparingLong(ACSMoraPack::duracionMin)
                    .thenComparingLong((RutaPedido rr) -> -holguraMin(rr))
            );
            if (lista.size() > kPorPedido) {
                e.setValue(new ArrayList<>(lista.subList(0, kPorPedido)));
            }
        }
    }

    private void inicializarFeromonas() {
        tau.clear();
        for (Map.Entry<Pedido, List<RutaPedido>> e : candidatosPorPedido.entrySet()) {
            double[] t = new double[e.getValue().size()];
            Arrays.fill(t, tau0);
            tau.put(e.getKey(), t);
        }
    }

    private void calentarFeromonasConSemillas(List<Solucion> semillasGrasp) {
        for (Solucion s : semillasGrasp) {
            SolucionLogistica plan = s.getSolucionLogistica();
            double c = s.getFitness(); // o s.score() si ese es tu método
            if (Double.isInfinite(c)) continue;
            double dTau = Q / Math.max(1e-6, c);

            for (Map.Entry<Pedido, RutaPedido> e : plan.getAsignacionPedidos().entrySet()) {
                Pedido p = e.getKey();
                List<RutaPedido> lista = candidatosPorPedido.get(p);
                if (lista == null) continue;
                int idx = indexOfRutaPorClave(lista, e.getValue());
                if (idx >= 0) {
                    tau.get(p)[idx] += dTau;
                }
            }
        }
    }

    /* ======= util clave de ruta (por IDs de vuelos) ======= */
    private static boolean contieneRutaPorClave(List<RutaPedido> lista, RutaPedido r) {
        String k = claveRuta(r);
        for (RutaPedido x : lista) {
            if (claveRuta(x).equals(k)) return true;
        }
        return false;
    }

    private static int indexOfRutaPorClave(List<RutaPedido> lista, RutaPedido r) {
        String k = claveRuta(r);
        for (int i = 0; i < lista.size(); i++) {
            if (claveRuta(lista.get(i)).equals(k)) return i;
        }
        return -1;
    }

    private static String claveRuta(RutaPedido r) {
        return r.getSecuenciaVuelos().stream()
                .map(Vuelo::getId)
                .collect(Collectors.joining("→"));
    }
}
