package com.kallpa.app.model;

/** Ubicación seleccionada de una parcela; no representa un seguimiento en tiempo real. */
public class UbicacionParcela {
    private final double latitud;
    private final double longitud;
    private final String origen;
    private final long guardadaEn;

    public UbicacionParcela(double latitud, double longitud, String origen, long guardadaEn) {
        this.latitud = latitud;
        this.longitud = longitud;
        this.origen = origen;
        this.guardadaEn = guardadaEn;
    }
    public double getLatitud() { return latitud; }
    public double getLongitud() { return longitud; }
    public String getOrigen() { return origen; }
    public long getGuardadaEn() { return guardadaEn; }
}
