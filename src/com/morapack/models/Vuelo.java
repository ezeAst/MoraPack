package com.morapack.models;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class Vuelo {
    private String id;
    private Aeropuerto origen;
    private Aeropuerto destino;
    private LocalDateTime horaSalida;
    private LocalDateTime horaLlegada;
    private int capacidadMaxima;
    private double duracionHoras;
    private EstadoVuelo estadoVuelo;
    private Boolean esInternacional;

    public Vuelo(String id, Aeropuerto origen, Aeropuerto destino, LocalDateTime horaSalida, LocalDateTime horaLlegada, int capacidadMaxima, double duracionHoras) {
        this.id = id;
        this.origen = origen;
        this.destino = destino;
        this.horaSalida = horaSalida;
        this.horaLlegada = horaLlegada;
        this.capacidadMaxima = capacidadMaxima;
        this.duracionHoras = duracionHoras;
        this.estadoVuelo = EstadoVuelo.A_TIEMPO;
        this.esInternacional = origen.getContinente().equals(destino.getContinente());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Aeropuerto getOrigen() {
        return origen;
    }

    public void setOrigen(Aeropuerto origen) {
        this.origen = origen;
    }

    public Aeropuerto getDestino() {
        return destino;
    }

    public void setDestino(Aeropuerto destino) {
        this.destino = destino;
    }

    public LocalDateTime getHoraSalida() {
        return horaSalida;
    }

    public void setHoraSalida(LocalDateTime horaSalida) {
        this.horaSalida = horaSalida;
    }

    public LocalDateTime getHoraLlegada() {
        return horaLlegada;
    }

    public void setHoraLlegada(LocalDateTime horaLlegada) {
        this.horaLlegada = horaLlegada;
    }

    public int getCapacidadMaxima() {
        return capacidadMaxima;
    }

    public void setCapacidadMaxima(int capacidadMaxima) {
        this.capacidadMaxima = capacidadMaxima;
    }

    public double getDuracionHoras() {
        return duracionHoras;
    }

    public void setDuracionHoras(double duracionHoras) {
        this.duracionHoras = duracionHoras;
    }

    public EstadoVuelo getEstadoVuelo() {
        return estadoVuelo;
    }

    public void setEstadoVuelo(EstadoVuelo estadoVuelo) {
        this.estadoVuelo = estadoVuelo;
    }

    public void cancelar() { this.estadoVuelo = EstadoVuelo.CANCELADO; }

    public Boolean getEsInternacional() {
        return esInternacional;
    }
}
