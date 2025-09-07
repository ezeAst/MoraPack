package com.morapack.models;

import java.time.LocalDateTime;
import java.util.List;

public class RutaPedido {
    private String pedidoId;
    private List<Vuelo> secuenciaVuelos;
    private String almacenOrigen;
    private String almacenDestino;
    private LocalDateTime fechaSalida;
    private LocalDateTime fechaLlegada;

    public RutaPedido(String pedidoId, List<Vuelo> secuenciaVuelos, String almacenOrigen, String almacenDestino, LocalDateTime fechaSalida, LocalDateTime fechaLlegada) {
        this.pedidoId = pedidoId;
        this.secuenciaVuelos = secuenciaVuelos;
        this.almacenOrigen = almacenOrigen;
        this.almacenDestino = almacenDestino;
        this.fechaSalida = fechaSalida;
        this.fechaLlegada = fechaLlegada;
    }

    public RutaPedido(String pedidoId, List<Vuelo> vuelos) {
        this.pedidoId = pedidoId;
        this.secuenciaVuelos = vuelos;
    }

    public String getPedidoId() {
        return pedidoId;
    }

    public void setPedidoId(String pedidoId) {
        this.pedidoId = pedidoId;
    }

    public List<Vuelo> getSecuenciaVuelos() {
        return secuenciaVuelos;
    }

    public void setSecuenciaVuelos(List<Vuelo> secuenciaVuelos) {
        this.secuenciaVuelos = secuenciaVuelos;
    }

    public String getAlmacenOrigen() {
        return almacenOrigen;
    }

    public void setAlmacenOrigen(String almacenOrigen) {
        this.almacenOrigen = almacenOrigen;
    }

    public String getAlmacenDestino() {
        return almacenDestino;
    }

    public void setAlmacenDestino(String almacenDestino) {
        this.almacenDestino = almacenDestino;
    }

    public LocalDateTime getFechaSalida() {
        return fechaSalida;
    }

    public void setFechaSalida(LocalDateTime fechaSalida) {
        this.fechaSalida = fechaSalida;
    }

    public LocalDateTime getFechaLlegada() {
        return fechaLlegada;
    }

    public void setFechaLlegada(LocalDateTime fechaLlegada) {
        this.fechaLlegada = fechaLlegada;
    }
}
