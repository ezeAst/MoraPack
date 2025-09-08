package com.morapack.models;

import java.time.LocalDateTime;
import java.util.List;

public class RutaPedido {
    private Pedido pedido;
    private List<Vuelo> secuenciaVuelos;
    private Aeropuerto almacenOrigen;
    private Aeropuerto almacenDestino;
    private LocalDateTime fechaSalida;
    private LocalDateTime fechaLlegada;
    private Boolean esInternacional;

    public RutaPedido(Pedido pedido, List<Vuelo> secuenciaVuelos, Aeropuerto almacenOrigen, Aeropuerto almacenDestino, LocalDateTime fechaSalida, LocalDateTime fechaLlegada) {
        this.pedido = pedido;
        this.secuenciaVuelos = secuenciaVuelos;
        this.almacenOrigen = almacenOrigen;
        this.almacenDestino = pedido.getLugarDestino();
        this.fechaSalida = fechaSalida;
        this.fechaLlegada = fechaLlegada;
        this.esInternacional = false;
    }

    public RutaPedido(Pedido pedido, List<Vuelo> vuelos) {
        this.pedido = pedido;
        this.secuenciaVuelos = vuelos;
        this.fechaLlegada = vuelos.getLast().getHoraLlegada();
        this.esInternacional = vuelos.stream().anyMatch(Vuelo::getEsInternacional);
        if (this.esInternacional == false) {
            this.pedido.setFechaLimite(this.pedido.getFechaRegistro().plusDays(2));
        }
        else{
            this.pedido.setFechaLimite(this.pedido.getFechaRegistro().plusDays(3));
        }

    }

    public List<Vuelo> getSecuenciaVuelos() {
        return secuenciaVuelos;
    }

    public void setSecuenciaVuelos(List<Vuelo> secuenciaVuelos) {
        this.secuenciaVuelos = secuenciaVuelos;
    }

    public Aeropuerto getAlmacenOrigen() {
        return almacenOrigen;
    }

    public void setAlmacenOrigen(Aeropuerto almacenOrigen) {
        this.almacenOrigen = almacenOrigen;
    }

    public Aeropuerto getAlmacenDestino() {
        return almacenDestino;
    }

    public void setAlmacenDestino(Aeropuerto almacenDestino) {
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

    public Pedido getPedido() {
        return pedido;
    }

    public void setPedido(Pedido pedido) {
        this.pedido = pedido;
    }

    public Boolean getEsInternacional() { return esInternacional;}

    public void setEsInternacional(Boolean esInternacional) { this.esInternacional = esInternacional;}

    public void agregarVuelo(Vuelo vuelo) {
        this.secuenciaVuelos.add(vuelo);
        if (this.secuenciaVuelos.getLast().getEsInternacional()){
            this.setEsInternacional(true);
        }

    }
}
