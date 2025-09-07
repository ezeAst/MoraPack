package com.morapack.models;

import java.util.List;
import java.util.Map;

public class Solucion {
    private SolucionLogistica solucionLogistica;
    private double fitness;

    public Solucion(SolucionLogistica solucionLogistica) {
        this.solucionLogistica = solucionLogistica;
        this.fitness = calcularFitness();
    }

    private double calcularFitness() {
        // Calcular fitness basado en la solución logística
        return 0.0; // Implementar según tus criterios
    }

    public SolucionLogistica getSolucionLogistica() {
        return solucionLogistica;
    }

    public void setSolucionLogistica(SolucionLogistica solucionLogistica) {
        this.solucionLogistica = solucionLogistica;
    }

    public double getFitness() {
        return fitness;
    }

    public void setFitness(double fitness) {
        this.fitness = fitness;
    }
}
