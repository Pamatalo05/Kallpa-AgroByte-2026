package com.kallpa.app.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** SOLO DEMOSTRACIÓN: una cuenta local por dispositivo, sin servidor de autenticación. */
public class RepositorioUsuarioLocal {
    private static final String PREFS = "cuenta_kallpa_demo";
    private static final int ITERACIONES = 120_000;
    private final SharedPreferences prefs;

    public RepositorioUsuarioLocal(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean existeCuenta() { return prefs.contains("correo"); }
    public boolean sesionActiva() { return prefs.getBoolean("sesion", false); }
    public void cerrarSesion() { prefs.edit().putBoolean("sesion", false).apply(); }
    public Usuario getUsuario() {
        return new Usuario(prefs.getString("nombre", "Agricultor"), prefs.getString("correo", ""));
    }

    public boolean registrar(Usuario usuario, String contrasena) {
        if (existeCuenta()) return false;
        byte[] sal = new byte[16];
        new SecureRandom().nextBytes(sal);
        String hash = calcularHash(contrasena, sal);
        if (hash == null) return false;
        prefs.edit().putString("nombre", usuario.getNombre())
                .putString("correo", usuario.getCorreo().trim().toLowerCase(java.util.Locale.ROOT))
                .putString("sal", Base64.encodeToString(sal, Base64.NO_WRAP))
                .putString("hash", hash).putBoolean("sesion", true).apply();
        return true;
    }

    public boolean ingresar(String correo, String contrasena) {
        if (!existeCuenta() || !prefs.getString("correo", "").equals(correo.trim().toLowerCase(java.util.Locale.ROOT))) return false;
        try {
            byte[] sal = Base64.decode(prefs.getString("sal", ""), Base64.NO_WRAP);
            byte[] guardado = Base64.decode(prefs.getString("hash", ""), Base64.NO_WRAP);
            String nuevoHash = calcularHash(contrasena, sal);
            if (nuevoHash == null) return false;
            byte[] calculado = Base64.decode(nuevoHash, Base64.NO_WRAP);
            if (!java.security.MessageDigest.isEqual(guardado, calculado)) return false;
            prefs.edit().putBoolean("sesion", true).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String calcularHash(String contrasena, byte[] sal) {
        PBEKeySpec clave = new PBEKeySpec(contrasena.toCharArray(), sal, ITERACIONES, 256);
        try {
            byte[] resultado = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(clave).getEncoded();
            return Base64.encodeToString(resultado, Base64.NO_WRAP);
        } catch (Exception e) { return null; }
        finally { clave.clearPassword(); }
    }
}
