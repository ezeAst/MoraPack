package com.morapack.models;

import java.time.LocalDateTime;

public class Pedido {
    private String id;
    private String clienteId;
    private int cantidad;
    private LocalDateTime fechaRegistro;
    private Aeropuerto lugarDestino;
    private LocalDateTime fechaLimite;

    public Pedido(String id, String clienteId, int cantidad, LocalDateTime fechaRegistro, Aeropuerto lugarDestino, LocalDateTime fechaLimite) {
        this.id = id;
        this.clienteId = clienteId;
        this.cantidad = cantidad;
        this.fechaRegistro = fechaRegistro;
        this.lugarDestino = lugarDestino;
        this.fechaLimite = fechaLimite;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public Aeropuerto getLugarDestino() {
        return lugarDestino;
    }

    public void setLugarDestino(Aeropuerto lugarDestino) {
        this.lugarDestino = lugarDestino;
    }

    public LocalDateTime getFechaLimite() {
        return fechaLimite;
    }

    public void setFechaLimite(LocalDateTime fechaLimite) {
        this.fechaLimite = fechaLimite;
    }
}
