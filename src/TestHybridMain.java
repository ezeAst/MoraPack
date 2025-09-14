import com.morapack.models.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Comparación SIMPLE entre GRASP vs GRASP+GA Híbrido
 * Solo fitness y rutas - sin análisis confuso
 */
public class TestHybridMain {

    public static void main(String[] args) {
        System.out.println("=== COMPARACIÓN SIMPLE: GRASP vs HÍBRIDO ===\n");

        // Crear datos
        List<Aeropuerto> aeropuertos = crearAeropuertos();
        List<Vuelo> vuelos = crearVuelos(aeropuertos);
        List<Pedido> pedidos = crearPedidos(aeropuertos);

        System.out.printf("📦 Total de pedidos: %d%n", pedidos.size());
        System.out.printf("✈️ Total de vuelos: %d%n", vuelos.size());
        System.out.println();

        // EXPERIMENTO 1: GRASP PURO
        System.out.println("🔵 EJECUTANDO GRASP PURO...");
        GraspMoraPack grasp = new GraspMoraPack(pedidos, vuelos);
        Solucion solucionGrasp = ejecutarGrasp(grasp, 10);

        // EXPERIMENTO 2: HÍBRIDO GRASP+GA
        System.out.println("\n🟢 EJECUTANDO HÍBRIDO GRASP+GA...");
        GraspGeneticHybrid hibrido = new GraspGeneticHybrid(pedidos, vuelos);
        hibrido.setIteracionesGrasp(10);
        hibrido.setPorcentajePoblacionGrasp(0.4);
        hibrido.configurarParametrosGA(30, 50, 0.15, 0.8);
        Solucion solucionHibrida = hibrido.ejecutarHibrido();

        // COMPARACIÓN SIMPLE
        System.out.println("\n" + "=".repeat(60));
        System.out.println("RESULTADOS DE LA COMPARACIÓN");
        System.out.println("=".repeat(60));

        compararResultados(solucionGrasp, solucionHibrida);
        mostrarRutasComparacion(solucionGrasp, solucionHibrida, pedidos);
    }

    /**
     * Ejecuta GRASP con múltiples iteraciones
     */
    private static Solucion ejecutarGrasp(GraspMoraPack grasp, int iteraciones) {
        double[] alphas = {0.0, 0.3, 0.5, 0.7, 1.0};
        Solucion mejorSolucion = null;
        double mejorFitness = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < iteraciones; i++) {
            double alfa = alphas[i % alphas.length];
            grasp.setAlfa(alfa);
            Solucion solucion = grasp.generarSolucion();

            if (solucion != null && solucion.getFitness() > mejorFitness) {
                mejorFitness = solucion.getFitness();
                mejorSolucion = solucion;
                System.out.printf("  ✓ Nueva mejor solución: %.2f (α=%.1f)%n", mejorFitness, alfa);
            }
        }

        System.out.printf("GRASP terminado. Mejor fitness: %.2f%n", mejorFitness);
        return mejorSolucion;
    }

    /**
     * Compara los resultados de ambos algoritmos
     */
    private static void compararResultados(Solucion grasp, Solucion hibrido) {
        System.out.println("📊 COMPARACIÓN DE FITNESS:");
        System.out.println("-".repeat(50));

        if (grasp != null) {
            int pedidosGrasp = grasp.getSolucionLogistica().getAsignacionPedidos().size();
            System.out.printf("🔵 GRASP:    Fitness = %.2f  |  Pedidos = %d%n",
                    grasp.getFitness(), pedidosGrasp);
        }

        if (hibrido != null) {
            int pedidosHibrido = hibrido.getSolucionLogistica().getAsignacionPedidos().size();
            System.out.printf("🟢 HÍBRIDO:  Fitness = %.2f  |  Pedidos = %d%n",
                    hibrido.getFitness(), pedidosHibrido);
        }

        // Determinar ganador
        if (grasp != null && hibrido != null) {
            System.out.println("-".repeat(50));
            if (hibrido.getFitness() > grasp.getFitness()) {
                double mejora = hibrido.getFitness() - grasp.getFitness();
                System.out.printf("🏆 GANADOR: HÍBRIDO (+%.2f puntos)%n", mejora);
            } else if (grasp.getFitness() > hibrido.getFitness()) {
                double mejora = grasp.getFitness() - hibrido.getFitness();
                System.out.printf("🏆 GANADOR: GRASP (+%.2f puntos)%n", mejora);
            } else {
                System.out.println("🤝 EMPATE");
            }
        }

        // NUEVO: Análisis detallado de componentes de fitness
        if (grasp != null && hibrido != null) {
            System.out.println("\n🔬 ANÁLISIS DETALLADO DE COMPONENTES DE FITNESS:");
            mostrarComponentesFitness(grasp, hibrido);
        }
    }

    /**
     * Muestra una tabla comparativa de todos los componentes internos del fitness
     */
    private static void mostrarComponentesFitness(Solucion grasp, Solucion hibrido) {
        System.out.println("=".repeat(80));
        System.out.printf("%-35s | %-15s | %-15s | %-10s%n", "COMPONENTE", "GRASP", "HÍBRIDO", "DIFERENCIA");
        System.out.println("=".repeat(80));

        // Calcular componentes individuales para GRASP
        ComponentesFitness compGrasp = calcularComponentes(grasp);
        ComponentesFitness compHibrido = calcularComponentes(hibrido);

        // Mostrar cada componente
        System.out.printf("%-35s | %-15.2f | %-15.2f | %+10.2f%n",
                "1. Entrega a Tiempo (50%)",
                compGrasp.entregaTiempo, compHibrido.entregaTiempo,
                compHibrido.entregaTiempo - compGrasp.entregaTiempo);

        System.out.printf("%-35s | %-15.2f | %-15.2f | %+10.2f%n",
                "2. Minimizar Atraso (20%)",
                compGrasp.minimizarAtraso, compHibrido.minimizarAtraso,
                compHibrido.minimizarAtraso - compGrasp.minimizarAtraso);

        System.out.printf("%-35s | %-15.2f | %-15.2f | %+10.2f%n",
                "3. Respetar Capacidades (15%)",
                compGrasp.respetarCapacidades, compHibrido.respetarCapacidades,
                compHibrido.respetarCapacidades - compGrasp.respetarCapacidades);

        System.out.printf("%-35s | %-15.2f | %-15.2f | %+10.2f%n",
                "4. Aprovechar Vuelos (10%)",
                compGrasp.aprovechamientoVuelos, compHibrido.aprovechamientoVuelos,
                compHibrido.aprovechamientoVuelos - compGrasp.aprovechamientoVuelos);

        System.out.printf("%-35s | %-15.2f | %-15.2f | %+10.2f%n",
                "5. Calidad Rutas (5%)",
                compGrasp.calidadRutas, compHibrido.calidadRutas,
                compHibrido.calidadRutas - compGrasp.calidadRutas);

        System.out.printf("%-35s | %-15.2f | %-15.2f | %+10.2f%n",
                "6. Penalización Fábricas",
                compGrasp.penalizacionFabricas, compHibrido.penalizacionFabricas,
                compHibrido.penalizacionFabricas - compGrasp.penalizacionFabricas);

        System.out.println("-".repeat(80));

        // Mostrar contribuciones ponderadas
        System.out.println("CONTRIBUCIONES PONDERADAS AL FITNESS:");
        System.out.println("-".repeat(80));

        double contrib1Grasp = compGrasp.entregaTiempo * 0.50;
        double contrib1Hibrido = compHibrido.entregaTiempo * 0.50;
        System.out.printf("%-35s | %-15.2f | %-15.2f | %+10.2f%n",
                "Entrega a Tiempo × 50%", contrib1Grasp, contrib1Hibrido,
                contrib1Hibrido - contrib1Grasp);

        double contrib2Grasp = compGrasp.minimizarAtraso * 0.20;
        double contrib2Hibrido = compHibrido.minimizarAtraso * 0.20;
        System.out.printf("%-35s | %-15.2f | %-15.2f | %+10.2f%n",
                "Minimizar Atraso × 20%", contrib2Grasp, contrib2Hibrido,
                contrib2Hibrido - contrib2Grasp);

        double contrib3Grasp = compGrasp.respetarCapacidades * 0.15;
        double contrib3Hibrido = compHibrido.respetarCapacidades * 0.15;
        System.out.printf("%-35s | %-15.2f | %-15.2f | %+10.2f%n",
                "Respetar Capacidades × 15%", contrib3Grasp, contrib3Hibrido,
                contrib3Hibrido - contrib3Grasp);

        double contrib4Grasp = compGrasp.aprovechamientoVuelos * 0.10;
        double contrib4Hibrido = compHibrido.aprovechamientoVuelos * 0.10;
        System.out.printf("%-35s | %-15.2f | %-15.2f | %+10.2f%n",
                "Aprovechar Vuelos × 10%", contrib4Grasp, contrib4Hibrido,
                contrib4Hibrido - contrib4Grasp);

        double contrib5Grasp = compGrasp.calidadRutas * 0.05;
        double contrib5Hibrido = compHibrido.calidadRutas * 0.05;
        System.out.printf("%-35s | %-15.2f | %-15.2f | %+10.2f%n",
                "Calidad Rutas × 5%", contrib5Grasp, contrib5Hibrido,
                contrib5Hibrido - contrib5Grasp);

        System.out.printf("%-35s | %-15.2f | %-15.2f | %+10.2f%n",
                "Penalización Fábricas", compGrasp.penalizacionFabricas, compHibrido.penalizacionFabricas,
                compHibrido.penalizacionFabricas - compGrasp.penalizacionFabricas);

        System.out.println("=".repeat(80));

        // Fitness total calculado
        double fitnessCalculadoGrasp = contrib1Grasp + contrib2Grasp + contrib3Grasp +
                contrib4Grasp + contrib5Grasp + compGrasp.penalizacionFabricas;
        double fitnessCalculadoHibrido = contrib1Hibrido + contrib2Hibrido + contrib3Hibrido +
                contrib4Hibrido + contrib5Hibrido + compHibrido.penalizacionFabricas;

        System.out.printf("%-35s | %-15.2f | %-15.2f | %+10.2f%n",
                "FITNESS TOTAL CALCULADO", fitnessCalculadoGrasp, fitnessCalculadoHibrido,
                fitnessCalculadoHibrido - fitnessCalculadoGrasp);

        System.out.printf("%-35s | %-15.2f | %-15.2f | %+10.2f%n",
                "FITNESS REAL (verificación)", grasp.getFitness(), hibrido.getFitness(),
                hibrido.getFitness() - grasp.getFitness());

        System.out.println("=".repeat(80));

        // Análisis del componente dominante
        analizarComponenteDominante(compGrasp, compHibrido);
    }

    /**
     * Analiza qué componente tiene mayor impacto en la diferencia de fitness
     */
    private static void analizarComponenteDominante(ComponentesFitness grasp, ComponentesFitness hibrido) {
        System.out.println("\n💡 ANÁLISIS DEL COMPONENTE DOMINANTE:");

        double difEntrega = Math.abs((hibrido.entregaTiempo - grasp.entregaTiempo) * 0.50);
        double difAtraso = Math.abs((hibrido.minimizarAtraso - grasp.minimizarAtraso) * 0.20);
        double difCapacidades = Math.abs((hibrido.respetarCapacidades - grasp.respetarCapacidades) * 0.15);
        double difAprovechamiento = Math.abs((hibrido.aprovechamientoVuelos - grasp.aprovechamientoVuelos) * 0.10);
        double difCalidad = Math.abs((hibrido.calidadRutas - grasp.calidadRutas) * 0.05);
        double difPenalizacion = Math.abs(hibrido.penalizacionFabricas - grasp.penalizacionFabricas);

        System.out.printf("• Mayor impacto: Entrega a Tiempo (%.2f puntos de diferencia)%n", difEntrega);
        System.out.printf("• Segundo: Minimizar Atraso (%.2f puntos)%n", difAtraso);
        System.out.printf("• Tercero: Respetar Capacidades (%.2f puntos)%n", difCapacidades);

        if (grasp.entregaTiempo > hibrido.entregaTiempo) {
            System.out.println("👉 GRASP gana principalmente porque atiende más pedidos a tiempo");
        } else {
            System.out.println("👉 HÍBRIDO gana principalmente porque atiende más pedidos a tiempo");
        }
    }

    /**
     * Calcula todos los componentes individuales del fitness para una solución
     */
    private static ComponentesFitness calcularComponentes(Solucion solucion) {
        // Necesitamos acceder a los métodos privados, así que usamos reflexión
        // o recreamos la lógica aquí
        ComponentesFitness comp = new ComponentesFitness();

        // Para simplificar, extraeremos la información del reporte de fitness
        String reporte = solucion.obtenerReporteFitness();
        comp = extraerComponentesDelReporte(reporte);

        return comp;
    }

    /**
     * Extrae los componentes del reporte de fitness (método simple)
     */
    private static ComponentesFitness extraerComponentesDelReporte(String reporte) {
        ComponentesFitness comp = new ComponentesFitness();

        // Parsear el reporte para extraer los valores
        String[] lineas = reporte.split("\n");
        for (String linea : lineas) {
            if (linea.contains("Entregar a tiempo")) {
                comp.entregaTiempo = extraerValor(linea);
            } else if (linea.contains("Minimizar atraso")) {
                comp.minimizarAtraso = extraerValor(linea);
            } else if (linea.contains("Respetar capacidades")) {
                comp.respetarCapacidades = extraerValor(linea);
            } else if (linea.contains("Aprovechar vuelos")) {
                comp.aprovechamientoVuelos = extraerValor(linea);
            } else if (linea.contains("Calidad de rutas")) {
                comp.calidadRutas = extraerValor(linea);
            } else if (linea.contains("Fábricas inválidas")) {
                comp.penalizacionFabricas = extraerValorPenalizacion(linea);
            }
        }

        return comp;
    }

    /**
     * Extrae el valor numérico de una línea como "PRIORIDAD #1 - Entregar a tiempo (50%): 87.5/100"
     */
    private static double extraerValor(String linea) {
        try {
            int inicio = linea.lastIndexOf(": ") + 2;
            int fin = linea.lastIndexOf("/100");
            if (inicio > 1 && fin > inicio) {
                return Double.parseDouble(linea.substring(inicio, fin));
            }
        } catch (Exception e) {
            // Si falla el parsing, devolver 0
        }
        return 0.0;
    }

    /**
     * Extrae el valor de penalización
     */
    private static double extraerValorPenalizacion(String linea) {
        try {
            int inicio = linea.lastIndexOf(": ") + 2;
            return Double.parseDouble(linea.substring(inicio));
        } catch (Exception e) {
            // Si falla el parsing, devolver 0
        }
        return 0.0;
    }

    /**
     * Clase para almacenar los componentes individuales del fitness
     */
    private static class ComponentesFitness {
        double entregaTiempo = 0.0;
        double minimizarAtraso = 0.0;
        double respetarCapacidades = 0.0;
        double aprovechamientoVuelos = 0.0;
        double calidadRutas = 0.0;
        double penalizacionFabricas = 0.0;
    }

    /**
     * Muestra las rutas de ambas soluciones lado a lado
     */
    private static void mostrarRutasComparacion(Solucion grasp, Solucion hibrido, List<Pedido> todosPedidos) {
        System.out.println("\n🗺️ COMPARACIÓN DE RUTAS:");
        System.out.println("-".repeat(70));
        System.out.printf("%-8s | %-25s | %-25s%n", "PEDIDO", "RUTA GRASP", "RUTA HÍBRIDO");
        System.out.println("-".repeat(70));

        Map<Pedido, RutaPedido> rutasGrasp = grasp != null ?
                grasp.getSolucionLogistica().getAsignacionPedidos() : new HashMap<>();
        Map<Pedido, RutaPedido> rutasHibrido = hibrido != null ?
                hibrido.getSolucionLogistica().getAsignacionPedidos() : new HashMap<>();

        for (Pedido pedido : todosPedidos) {
            String rutaGraspStr = obtenerRutaString(rutasGrasp.get(pedido));
            String rutaHibridoStr = obtenerRutaString(rutasHibrido.get(pedido));

            // Truncar si es muy largo
            rutaGraspStr = truncar(rutaGraspStr, 25);
            rutaHibridoStr = truncar(rutaHibridoStr, 25);

            System.out.printf("%-8s | %-25s | %-25s%n",
                    pedido.getId(), rutaGraspStr, rutaHibridoStr);
        }

        // Resumen de rutas
        System.out.println("-".repeat(70));
        if (grasp != null && hibrido != null) {
            int rutasGraspCount = rutasGrasp.size();
            int rutasHibridoCount = rutasHibrido.size();
            System.out.printf("TOTAL RUTAS:   GRASP = %d/%d  |  HÍBRIDO = %d/%d%n",
                    rutasGraspCount, todosPedidos.size(),
                    rutasHibridoCount, todosPedidos.size());
        }
    }

    /**
     * Convierte una ruta a string legible
     */
    private static String obtenerRutaString(RutaPedido ruta) {
        if (ruta == null || ruta.getSecuenciaVuelos().isEmpty()) {
            return "NO ASIGNADO";
        }

        StringBuilder sb = new StringBuilder();
        List<Vuelo> vuelos = ruta.getSecuenciaVuelos();

        for (int i = 0; i < vuelos.size(); i++) {
            if (i > 0) sb.append("→");
            sb.append(vuelos.get(i).getOrigen().getCodigo());
        }
        sb.append("→").append(vuelos.get(vuelos.size() - 1).getDestino().getCodigo());

        return sb.toString();
    }

    /**
     * Trunca texto si es muy largo
     */
    private static String truncar(String texto, int maxLongitud) {
        if (texto.length() <= maxLongitud) {
            return texto;
        }
        return texto.substring(0, maxLongitud - 3) + "...";
    }

    // MÉTODOS DE CREACIÓN DE DATOS (simplificados)
    private static List<Aeropuerto> crearAeropuertos() {
        List<Aeropuerto> aeropuertos = new ArrayList<>();

        // Fábricas
        aeropuertos.add(new Aeropuerto("LIM", "Lima", "Peru", 800, 0, -5, "America"));
        aeropuertos.add(new Aeropuerto("BRU", "Bruselas", "Bélgica", 1000, 0, 1, "Europa"));
        aeropuertos.add(new Aeropuerto("BAK", "Baku", "Azerbaiyán", 600, 0, 4, "Asia"));

        // Destinos
        aeropuertos.add(new Aeropuerto("JFK", "Nueva York", "USA", 900, 0, -5, "America"));
        aeropuertos.add(new Aeropuerto("LHR", "Londres", "Reino Unido", 1200, 0, 0, "Europa"));
        aeropuertos.add(new Aeropuerto("NRT", "Tokio", "Japón", 800, 0, 9, "Asia"));
        aeropuertos.add(new Aeropuerto("SYD", "Sidney", "Australia", 700, 0, 10, "Oceania"));
        aeropuertos.add(new Aeropuerto("DXB", "Dubai", "UAE", 1500, 0, 4, "Asia"));
        aeropuertos.add(new Aeropuerto("CDG", "París", "Francia", 1100, 0, 1, "Europa"));
        aeropuertos.add(new Aeropuerto("GRU", "São Paulo", "Brasil", 600, 0, -3, "America"));

        return aeropuertos;
    }

    private static List<Vuelo> crearVuelos(List<Aeropuerto> aeropuertos) {
        List<Vuelo> vuelos = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now().plusDays(1);

        Map<String, Aeropuerto> map = new HashMap<>();
        for (Aeropuerto a : aeropuertos) {
            map.put(a.getCodigo(), a);
        }

        int id = 1;

        // Vuelos desde LIM
        vuelos.add(new Vuelo("V" + (id++), map.get("LIM"), map.get("JFK"), base.plusHours(2), base.plusHours(12), 350, 10));
        vuelos.add(new Vuelo("V" + (id++), map.get("LIM"), map.get("GRU"), base.plusHours(4), base.plusHours(8), 280, 4));
        vuelos.add(new Vuelo("V" + (id++), map.get("LIM"), map.get("CDG"), base.plusHours(6), base.plusHours(20), 320, 14));

        // Vuelos desde BRU
        vuelos.add(new Vuelo("V" + (id++), map.get("BRU"), map.get("LHR"), base.plusHours(1), base.plusHours(2), 400, 1));
        vuelos.add(new Vuelo("V" + (id++), map.get("BRU"), map.get("JFK"), base.plusHours(3), base.plusHours(12), 380, 9));
        vuelos.add(new Vuelo("V" + (id++), map.get("BRU"), map.get("DXB"), base.plusHours(5), base.plusHours(11), 300, 6));

        // Vuelos desde BAK
        vuelos.add(new Vuelo("V" + (id++), map.get("BAK"), map.get("DXB"), base.plusHours(2), base.plusHours(5), 250, 3));
        vuelos.add(new Vuelo("V" + (id++), map.get("BAK"), map.get("NRT"), base.plusHours(4), base.plusHours(14), 220, 10));

        // Conexiones
        vuelos.add(new Vuelo("V" + (id++), map.get("DXB"), map.get("NRT"), base.plusHours(8), base.plusHours(17), 330, 9));
        vuelos.add(new Vuelo("V" + (id++), map.get("CDG"), map.get("SYD"), base.plusHours(12), base.plusHours(32), 290, 20));
        vuelos.add(new Vuelo("V" + (id++), map.get("JFK"), map.get("NRT"), base.plusHours(16), base.plusHours(30), 350, 14));
        vuelos.add(new Vuelo("V" + (id++), map.get("GRU"), map.get("SYD"), base.plusHours(18), base.plusHours(34), 310, 16));

        return vuelos;
    }

    private static List<Pedido> crearPedidos(List<Aeropuerto> aeropuertos) {
        List<Pedido> pedidos = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now();

        Map<String, Aeropuerto> map = new HashMap<>();
        for (Aeropuerto a : aeropuertos) {
            map.put(a.getCodigo(), a);
        }
/*
        pedidos.add(new Pedido("P001", "CLI001", 50, base, map.get("JFK"), base.plusDays(2)));
        pedidos.add(new Pedido("P002", "CLI002", 30, base, map.get("LHR"), base.plusDays(3)));
        pedidos.add(new Pedido("P003", "CLI003", 75, base, map.get("NRT"), base.plusDays(4)));
        pedidos.add(new Pedido("P004", "CLI004", 25, base, map.get("SYD"), base.plusDays(5)));
        pedidos.add(new Pedido("P005", "CLI005", 40, base, map.get("DXB"), base.plusDays(3)));
        pedidos.add(new Pedido("P006", "CLI006", 60, base, map.get("CDG"), base.plusDays(2)));
        pedidos.add(new Pedido("P007", "CLI007", 35, base, map.get("GRU"), base.plusDays(4)));
        pedidos.add(new Pedido("P008", "CLI008", 55, base, map.get("JFK"), base.plusDays(6)));
*/
        return pedidos;
    }
}