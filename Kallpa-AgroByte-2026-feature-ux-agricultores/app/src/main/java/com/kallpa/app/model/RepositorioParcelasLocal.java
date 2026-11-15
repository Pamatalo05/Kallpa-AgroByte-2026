
package com.kallpa.app.model;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class RepositorioParcelasLocal {

    private static final String ARCHIVO = "kallpa_parcelas";
    private static final String CLAVE = "parcelas";

    private final SharedPreferences preferencias;

    public RepositorioParcelasLocal(Context context) {
        preferencias = context.getApplicationContext()
                .getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE);
    }

    // Guardar una parcela nueva o actualizar una existente
    public boolean guardar(Parcela parcela) {

        if (parcela == null ||
                parcela.getUsuarioCorreo() == null ||
                parcela.getUsuarioCorreo().trim().isEmpty()) {
            return false;
        }

        try {
            JSONArray anteriores = leerParcelas();
            JSONArray actualizadas = new JSONArray();

            boolean encontrada = false;

            for (int i = 0; i < anteriores.length(); i++) {
                JSONObject actual = anteriores.optJSONObject(i);

                if (actual == null) {
                    continue;
                }

                if (parcela.getId().equals(
                        actual.optString("id"))) {

                    actualizadas.put(convertirAJson(parcela));
                    encontrada = true;

                } else {
                    actualizadas.put(actual);
                }
            }

            if (!encontrada) {
                actualizadas.put(convertirAJson(parcela));
            }

            return preferencias.edit()
                    .putString(CLAVE, actualizadas.toString())
                    .commit();

        } catch (JSONException e) {
            return false;
        }
    }

    // Obtener todas las parcelas de un usuario
    public List<Parcela> obtenerPorUsuario(String correo) {

        List<Parcela> resultado = new ArrayList<>();

        if (correo == null) {
            return resultado;
        }

        JSONArray datos = leerParcelas();

        for (int i = 0; i < datos.length(); i++) {

            JSONObject objeto = datos.optJSONObject(i);

            if (objeto == null) {
                continue;
            }

            String propietario =
                    objeto.optString("usuarioCorreo", "");

            if (!propietario.trim().equalsIgnoreCase(
                    correo.trim())) {
                continue;
            }

            Parcela parcela = new Parcela(
                    propietario,
                    objeto.optString("nombre"),
                    objeto.optString("cultivoActual"),
                    objeto.optString("fechaSiembra"),
                    objeto.optString("fechaCosecha"),
                    objeto.optBoolean("tieneRiego"),
                    objeto.optDouble("hectareas"),
                    objeto.optString("canton"),
                    objeto.optDouble("latitud"),
                    objeto.optDouble("longitud")
            );

            // Recuperar el identificador original
            parcela.restaurarId(objeto.optString("id"));

            resultado.add(parcela);
        }

        return resultado;
    }

    // Comprobar si el usuario ya registró parcelas
    public boolean tieneParcelas(String correo) {
        return !obtenerPorUsuario(correo).isEmpty();
    }

    // Convertir una parcela a JSON
    private JSONObject convertirAJson(Parcela parcela)
            throws JSONException {

        JSONObject objeto = new JSONObject();

        objeto.put("id", parcela.getId());
        objeto.put("usuarioCorreo",
                parcela.getUsuarioCorreo());
        objeto.put("nombre", parcela.getNombre());
        objeto.put("cultivoActual",
                parcela.getCultivoActual());
        objeto.put("fechaSiembra",
                parcela.getFechaSiembra());
        objeto.put("fechaCosecha",
                parcela.getFechaCosecha());
        objeto.put("tieneRiego",
                parcela.isTieneRiego());
        objeto.put("hectareas",
                parcela.getHectareas());
        objeto.put("canton", parcela.getCanton());
        objeto.put("latitud", parcela.getLatitud());
        objeto.put("longitud", parcela.getLongitud());

        return objeto;
    }

    // Leer los registros guardados
    private JSONArray leerParcelas() {

        String contenido = preferencias.getString(CLAVE, "[]");

        try {
            return new JSONArray(contenido);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }
}
