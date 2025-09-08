

import com.morapack.models.GraspMoraPack;
import com.morapack.models.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Clase de prueba para el algoritmo GRASP con datos sintéticos
 */
public class TestGraspMain {

    public static void main(String[] args) {
        System.out.println("=== PRUEBA DEL ALGORITMO GRASP MORAPACK ===\n");

        // Crear datos sintéticos
        List<Aeropuerto> aeropuertos = crearAeropuertos();
        List<Vuelo> vuelos = crearVuelos(aeropuertos);
        List<Pedido> pedidos = crearPedidos(aeropuertos);

        // Mostrar datos creados
        mostrarDatosSinteticos(aeropuertos, vuelos, pedidos);

        // Ejecutar GRASP
        System.out.println("\n=== EJECUTANDO GRASP ===");
        GraspMoraPack grasp = new GraspMoraPack(pedidos, vuelos);

        // Probar con diferentes valores de alfa
        double[] alphas = {0.0, 0.3, 0.7, 1.0};

        for (double alfa : alphas) {
            System.out.println("\n--- Probando con alfa = " + alfa + " ---");
            grasp.setAlfa(alfa);

            Solucion solucion = grasp.generarSolucion();

            if (solucion != null) {
                System.out.println("✓ Solución generada exitosamente");
                System.out.println("Fitness: " + String.format("%.2f", solucion.getFitness()));
                System.out.println("Pedidos asignados: " +
                        solucion.getSolucionLogistica().getAsignacionPedidos().size() + "/" + pedidos.size());

                // Mostrar algunas rutas de ejemplo
                mostrarEjemplosRutas(solucion);
            } else {
                System.out.println("✗ No se pudo generar solución");
            }
        }

        System.out.println("\n=== PRUEBA COMPLETADA ===");
    }

    /**
     * Crea aeropuertos sintéticos
     */
    private static List<Aeropuerto> crearAeropuertos() {
        List<Aeropuerto> aeropuertos = new ArrayList<>();

        // Fábricas (orígenes)
        aeropuertos.add(new Aeropuerto("LIM", "Jorge Chavez", "Peru", 800, 200, -5, "America"));
        aeropuertos.add(new Aeropuerto("BRU", "Frankfurt", "Alemania", 1000, 300, 1, "Europa"));
        aeropuertos.add(new Aeropuerto("BAK", "Damascus", "Siria", 600, 150, 2, "Asia"));

        // Aeropuertos destino
        aeropuertos.add(new Aeropuerto("JFK", "John F Kennedy", "USA", 900, 0, -5, "America"));
        aeropuertos.add(new Aeropuerto("LHR", "Heathrow", "Reino Unido", 1200, 0, 0, "Europa"));
        aeropuertos.add(new Aeropuerto("NRT", "Narita", "Japon", 800, 0, 9, "Asia"));
        aeropuertos.add(new Aeropuerto("SYD", "Sydney", "Australia", 700, 0, 10, "Oceania"));
        aeropuertos.add(new Aeropuerto("DXB", "Dubai", "UAE", 1500, 0, 4, "Asia"));
        aeropuertos.add(new Aeropuerto("CDG", "Charles de Gaulle", "Francia", 1100, 0, 1, "Europa"));
        aeropuertos.add(new Aeropuerto("GRU", "Guarulhos", "Brasil", 600, 0, -3, "America"));

        return aeropuertos;
    }
    /**
     * Crea vuelos sintéticos
     */
    private static List<Vuelo> crearVuelos(List<Aeropuerto> aeropuertos) {
        List<Vuelo> vuelos = new ArrayList<>();
        LocalDateTime fechaBase = LocalDateTime.now().plusDays(1);

        // Mapa para fácil acceso
        Map<String, Aeropuerto> aeropuertoMap = new HashMap<>();
        for (Aeropuerto a : aeropuertos) {
            aeropuertoMap.put(a.getCodigo(), a);
        }

        int vueloId = 1;

        // Vuelos desde Perú (LIM)
        vuelos.add(crearVuelo("V" + (vueloId++), aeropuertoMap.get("LIM"), aeropuertoMap.get("JFK"),
                fechaBase.plusHours(2), 90, 350));
        vuelos.add(crearVuelo("V" + (vueloId++), aeropuertoMap.get("LIM"), aeropuertoMap.get("GRU"),
                fechaBase.plusHours(4), 4, 280));
        vuelos.add(crearVuelo("V" + (vueloId++), aeropuertoMap.get("LIM"), aeropuertoMap.get("CDG"),
                fechaBase.plusHours(6), 14, 320));

        // Vuelos desde Alemania (FRA)
        vuelos.add(crearVuelo("V" + (vueloId++), aeropuertoMap.get("BAK"), aeropuertoMap.get("LHR"),
                fechaBase.plusHours(1), 2, 400));
        vuelos.add(crearVuelo("V" + (vueloId++), aeropuertoMap.get("BAK"), aeropuertoMap.get("JFK"),
                fechaBase.plusHours(3), 9, 380));
        vuelos.add(crearVuelo("V" + (vueloId++), aeropuertoMap.get("BAK"), aeropuertoMap.get("DXB"),
                fechaBase.plusHours(5), 6, 300));

        // Vuelos desde Siria (DAM)
        vuelos.add(crearVuelo("V" + (vueloId++), aeropuertoMap.get("BRU"), aeropuertoMap.get("DXB"),
                fechaBase.plusHours(2), 3, 250));
        vuelos.add(crearVuelo("V" + (vueloId++), aeropuertoMap.get("BRU"), aeropuertoMap.get("BAK"),
                fechaBase.plusHours(4), 4, 220));

        // Vuelos de conexión
        vuelos.add(crearVuelo("V" + (vueloId++), aeropuertoMap.get("DXB"), aeropuertoMap.get("NRT"),
                fechaBase.plusHours(8), 9, 330));
        vuelos.add(crearVuelo("V" + (vueloId++), aeropuertoMap.get("CDG"), aeropuertoMap.get("SYD"),
                fechaBase.plusHours(12), 20, 290));
        vuelos.add(crearVuelo("V" + (vueloId++), aeropuertoMap.get("JFK"), aeropuertoMap.get("NRT"),
                fechaBase.plusHours(16), 14, 350));
        vuelos.add(crearVuelo("V" + (vueloId++), aeropuertoMap.get("GRU"), aeropuertoMap.get("SYD"),
                fechaBase.plusHours(18), 16, 310));
        //vuelos directos
        vuelos.add(crearVuelo("V" + (vueloId++), aeropuertoMap.get("LIM"), aeropuertoMap.get("SYD"),
                fechaBase.plusHours(18), 2, 310));

        return vuelos;
    }

    /**
     * Crea un vuelo con los parámetros dados
     */
    private static Vuelo crearVuelo(String id, Aeropuerto origen, Aeropuerto destino,
                                    LocalDateTime salida, int duracionHoras, int capacidad) {
        LocalDateTime llegada = salida.plusHours(duracionHoras);
        return new Vuelo(id, origen, destino, salida, llegada, capacidad, duracionHoras);
    }

    /**
     * Crea pedidos sintéticos
     */
    private static List<Pedido> crearPedidos(List<Aeropuerto> aeropuertos) {
        List<Pedido> pedidos = new ArrayList<>();
        LocalDateTime fechaBase = LocalDateTime.now();

        // Mapa para fácil acceso
        Map<String, Aeropuerto> aeropuertoMap = new HashMap<>();
        for (Aeropuerto a : aeropuertos) {
            aeropuertoMap.put(a.getCodigo(), a);
        }

        // Pedidos con diferentes destinos y urgencias
        pedidos.add(new Pedido("P001", "CLI001", 50, fechaBase,
                aeropuertoMap.get("JFK"), fechaBase.plusDays(2)));

        pedidos.add(new Pedido("P002", "CLI002", 30, fechaBase,
                aeropuertoMap.get("LHR"), fechaBase.plusDays(3)));

        pedidos.add(new Pedido("P003", "CLI003", 75, fechaBase,
                aeropuertoMap.get("NRT"), fechaBase.plusDays(4)));

        pedidos.add(new Pedido("P004", "CLI004", 25, fechaBase,
                aeropuertoMap.get("SYD"), fechaBase.plusDays(5)));

        pedidos.add(new Pedido("P005", "CLI005", 40, fechaBase,
                aeropuertoMap.get("DXB"), fechaBase.plusDays(3)));

        pedidos.add(new Pedido("P006", "CLI006", 60, fechaBase,
                aeropuertoMap.get("CDG"), fechaBase.plusDays(2)));

        pedidos.add(new Pedido("P007", "CLI007", 35, fechaBase,
                aeropuertoMap.get("GRU"), fechaBase.plusDays(4)));

        pedidos.add(new Pedido("P008", "CLI008", 55, fechaBase,
                aeropuertoMap.get("JFK"), fechaBase.plusDays(6)));

        pedidos.add(new Pedido("P009", "CLI009", 45, fechaBase,
                aeropuertoMap.get("NRT"), fechaBase.plusDays(3)));

        pedidos.add(new Pedido("P010", "CLI010", 70, fechaBase,
                aeropuertoMap.get("SYD"), fechaBase.plusDays(7)));

        return pedidos;
    }

    /**
     * Muestra los datos sintéticos creados
     */
    private static void mostrarDatosSinteticos(List<Aeropuerto> aeropuertos,
                                               List<Vuelo> vuelos, List<Pedido> pedidos) {
        System.out.println("DATOS SINTÉTICOS CREADOS:");

        System.out.println("\n📍 AEROPUERTOS (" + aeropuertos.size() + "):");
        for (Aeropuerto a : aeropuertos) {
            String tipo = (a.getCapacidadAct() > 0) ? "[FÁBRICA]" : "[DESTINO]";
            System.out.printf("  %s %s - %s, %s (Cap: %d, Actual: %d)%n",
                    tipo, a.getCodigo(), a.getNombre(), a.getPais(), a.getCapacidad(), a.getCapacidadAct());
        }

        System.out.println("\n✈️ VUELOS (" + vuelos.size() + "):");
        for (Vuelo v : vuelos) {
            System.out.printf("  %s: %s → %s (Cap: %d, %s)%n",
                    v.getId(), v.getOrigen().getCodigo(), v.getDestino().getCodigo(),
                    v.getCapacidadMaxima(), v.getEsInternacional() ? "Internacional" : "Nacional");
        }

        System.out.println("\n📦 PEDIDOS (" + pedidos.size() + "):");
        for (Pedido p : pedidos) {
            System.out.printf("  %s: %d paquetes → %s (Límite: %s)%n",
                    p.getId(), p.getCantidad(), p.getLugarDestino().getCodigo(),
                    p.getFechaLimite().toLocalDate());
        }
    }

    /**
     * Muestra ejemplos de rutas generadas
     */
    private static void mostrarEjemplosRutas(Solucion solucion) {
        Map<Pedido, RutaPedido> asignaciones = solucion.getSolucionLogistica().getAsignacionPedidos();

        System.out.println("Ejemplos de rutas generadas:");
        int contador = 0;

        for (Map.Entry<Pedido, RutaPedido> entry : asignaciones.entrySet()) {
            if (contador >= 100) break; // Mostrar solo 3 ejemplos

            Pedido pedido = entry.getKey();
            RutaPedido ruta = entry.getValue();

            StringBuilder rutaStr = new StringBuilder();
            for (int i = 0; i < ruta.getSecuenciaVuelos().size(); i++) {
                if (i > 0) rutaStr.append(" → ");
                Vuelo vuelo = ruta.getSecuenciaVuelos().get(i);
                rutaStr.append(vuelo.getOrigen().getCodigo());
            }
            // Agregar destino final
            if (!ruta.getSecuenciaVuelos().isEmpty()) {
                rutaStr.append(" → ");
                rutaStr.append(ruta.getSecuenciaVuelos().get(ruta.getSecuenciaVuelos().size() - 1)
                        .getDestino().getCodigo());
            }

            System.out.printf("  %s: %s (%d vuelos, %s)%n",
                    pedido.getId(), rutaStr.toString(), ruta.getSecuenciaVuelos().size(),
                    ruta.getEsInternacional() ? "Internacional" : "Nacional");

            contador++;
        }

        if (asignaciones.size() > 3) {
            System.out.println("  ... y " + (asignaciones.size() - 3) + " rutas más");
        }
    }
}