package com.morapack.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SolucionLogistica
{
    private Map<String, RutaPedido> asignacionPedidos;
    //private Map<String, List<String>> vuelosPorPedido;  // Vista inversa
    private int cantidadAtiempo;
    private int cantidadRetraso;

    public SolucionLogistica(Map<String, RutaPedido> asignacionPedidos, int cantidadAtiempo, int cantidadRetraso) {
        this.asignacionPedidos = asignacionPedidos;
        this.cantidadAtiempo = cantidadAtiempo;
        this.cantidadRetraso = cantidadRetraso;
    }

    public SolucionLogistica() {

    }


    public Map<String, RutaPedido> getAsignacionPedidos() {
        return asignacionPedidos;
    }

    public void setAsignacionPedidos(Map<String, RutaPedido> asignacionPedidos) {
        this.asignacionPedidos = asignacionPedidos;
    }

    public int getCantidadAtiempo() {
        return cantidadAtiempo;
    }

    public void setCantidadAtiempo(int cantidadAtiempo) {
        this.cantidadAtiempo = cantidadAtiempo;
    }

    public int getCantidadRetraso() {
        return cantidadRetraso;
    }

    public void setCantidadRetraso(int cantidadRetraso) {
        this.cantidadRetraso = cantidadRetraso;
    }

    public void asignarPedidoARuta(Pedido pedido, List<Vuelo> vuelos) {
        RutaPedido ruta = new RutaPedido(pedido, vuelos);
        asignacionPedidos.put(pedido, ruta);
        //vuelosPorPedido.put(pedidoId, vuelos);
    }

    // Agregar este método en SolucionLogistica para facilitar operaciones
    public void agregarRutaPedido(String pedidoId, RutaPedido ruta) {
        if (asignacionPedidos == null) {
            asignacionPedidos = new HashMap<>();
        }
        asignacionPedidos.put(pedidoId, ruta);
    }

    // También agregar método para obtener pedidos en un vuelo específico
    public List<String> getPedidosEnVuelo(String vueloId) {
        if (asignacionPedidos == null) return new ArrayList<>();

        return asignacionPedidos.values().stream()
                .filter(ruta -> ruta.getSecuenciaVuelos().stream()
                        .anyMatch(vuelo -> vuelo.getId().equals(vueloId)))
                .map(RutaPedido::getPedido)
                .collect(Collectors.toList());
    }


}
