package com.morapack.models;

import java.time.LocalDateTime;

public class Aeropuerto {
    private String codigo;
    private String nombre;
    private String pais;
    private int capacidad;
    private int capacidadAct;
    private int husoHorario;
    private String continente;

    public Aeropuerto(String codigo, String nombre, String pais, int capacidad, int capacidadAct, int husoHorario, String continente) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.pais = pais;
        this.capacidad = capacidad;
        this.capacidadAct = capacidadAct;
        this.husoHorario = husoHorario;
        this.continente = continente;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getPais() {
        return pais;
    }

    public void setPais(String pais) {
        this.pais = pais;
    }

    public int getCapacidad() {
        return capacidad;
    }

    public void setCapacidad(int capacidad) {
        this.capacidad = capacidad;
    }

    public int getCapacidadAct() {
        return capacidadAct;
    }

    public void setCapacidadAct(int capacidadAct) {
        this.capacidadAct = capacidadAct;
    }

    public int getHusoHorario() {
        return husoHorario;
    }

    public void setHusoHorario(int husoHorario) {
        this.husoHorario = husoHorario;
    }

    public String getContinente() { return continente; }

    public boolean esFabrica() {
        return Solucion.FABRICAS.contains(this.codigo);
    }

    // Agregar simulación de tiempo:
    public boolean puedeRecibirPaquetes(int cantidad) {
        return (capacidadAct + cantidad) <= capacidad;
    }

    public void recibirPaquetes(int cantidad, LocalDateTime tiempoLlegada) {
        // Simular que los paquetes se van después de 2 horas
        this.capacidadAct += cantidad;
        // TODO: Implementar liberación automática después de 2 horas
    }
}
