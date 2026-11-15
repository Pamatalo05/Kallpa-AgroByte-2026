package com.kallpa.app.model;

import android.content.Context;
import android.content.SharedPreferences;

/** Guarda las coordenadas de manera local, también sin internet. */
public class RepositorioUbicacionLocal {
    private final SharedPreferences prefs;
    public RepositorioUbicacionLocal(Context context) {
        prefs = context.getSharedPreferences("ubicacion_kallpa_demo", Context.MODE_PRIVATE);
    }
    public void guardar(UbicacionParcela p) {
        prefs.edit().putFloat("lat", (float)p.getLatitud())
                .putFloat("lon", (float)p.getLongitud())
                .putString("origen", p.getOrigen())
                .putLong("fecha", p.getGuardadaEn())
                .putBoolean("existe", true).apply();
    }
    public boolean existe() { return prefs.getBoolean("existe", false); }
    public UbicacionParcela obtener() {
        if (!existe()) return null;
        return new UbicacionParcela(prefs.getFloat("lat", 0), prefs.getFloat("lon", 0),
                prefs.getString("origen", "manual"), prefs.getLong("fecha", 0));
    }
    public void borrar() { prefs.edit().clear().apply(); }
}
