package com.morapack.models;

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

    public void asignarPedidoARuta(String pedidoId, List<Vuelo> vuelos) {
        RutaPedido ruta = new RutaPedido(pedidoId, vuelos);
        asignacionPedidos.put(pedidoId, ruta);
        //vuelosPorPedido.put(pedidoId, vuelos);
    }



}
