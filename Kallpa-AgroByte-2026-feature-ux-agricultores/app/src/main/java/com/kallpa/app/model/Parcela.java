package com.kallpa.app.model;

import java.util.UUID;

public class Parcela {

    private String id;
    private String usuarioCorreo;
    private String nombre;
    private String cultivoActual;
    private String fechaSiembra;
    private String fechaCosecha;
    private boolean tieneRiego;
    private double hectareas;
    private String canton;
    private double latitud;
    private double longitud;

    public Parcela(
            String usuarioCorreo,
            String nombre,
            String cultivoActual,
            String fechaSiembra,
            String fechaCosecha,
            boolean tieneRiego,
            double hectareas,
            String canton,
            double latitud,
            double longitud
    ) {
        this.id = UUID.randomUUID().toString();
        this.usuarioCorreo = usuarioCorreo;
        this.nombre = nombre;
        this.cultivoActual = cultivoActual;
        this.fechaSiembra = fechaSiembra;
        this.fechaCosecha = fechaCosecha;
        this.tieneRiego = tieneRiego;
        this.hectareas = hectareas;
        this.canton = canton;
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public String getId() {
        return id;
    }

    public String getUsuarioCorreo() {
        return usuarioCorreo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCultivoActual() {
        return cultivoActual;
    }

    public String getFechaSiembra() {
        return fechaSiembra;
    }

    public String getFechaCosecha() {
        return fechaCosecha;
    }

    public boolean isTieneRiego() {
        return tieneRiego;
    }

    public double getHectareas() {
        return hectareas;
    }

    public String getCanton() {
        return canton;
    }

    public double getLatitud() {
        return latitud;
    }

    public double getLongitud() {
        return longitud;
    }

    // Restaurar el ID al leer una parcela guardada
    void restaurarId(String id) {
        if (id != null && !id.trim().isEmpty()) {
            this.id = id;
        }
    }

}
