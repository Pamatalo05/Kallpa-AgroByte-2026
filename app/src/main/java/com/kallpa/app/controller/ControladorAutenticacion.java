package com.kallpa.app.controller;

import android.content.Context;
import android.util.Patterns;
import com.kallpa.app.model.RepositorioUsuarioLocal;
import com.kallpa.app.model.Usuario;

/** Controla validación y flujo de ingreso/registro sin mezclarlo con las vistas. */
public class ControladorAutenticacion {
    private final RepositorioUsuarioLocal repo;
    public ControladorAutenticacion(Context context) { repo = new RepositorioUsuarioLocal(context); }
    public boolean sesionActiva() { return repo.sesionActiva(); }
    public Usuario usuarioActual() { return repo.getUsuario(); }
    public void salir() { repo.cerrarSesion(); }

    /** Devuelve null si todo fue bien; de lo contrario, un mensaje comprensible. */
    public String registrar(String nombre, String correo, String clave, String confirmacion) {
        if (nombre.trim().length() < 2) return "Escribe tu nombre completo.";
        if (!empiezaConMayuscula(nombre)) return "Empieza tu nombre con mayúscula. Ejemplo: María.";
        if (!esCorreoValido(correo)) return "Revisa tu correo: parece que le falta algo (por ejemplo, la @).";
        if (clave.length() < 8) return "La contraseña necesita al menos 8 caracteres.";
        if (!clave.equals(confirmacion)) return "Las contraseñas no coinciden.";
        if (repo.existeCuenta()) return "Ya hay una cuenta en este teléfono. Toca \"Iniciar sesión\".";
        return repo.registrar(new Usuario(nombre.trim(), correo.trim()), clave)
                ? null : "No pudimos crear tu cuenta. Inténtalo otra vez.";
    }

    public String ingresar(String correo, String clave) {
        if (!esCorreoValido(correo)) return "Revisa tu correo: parece que le falta algo (por ejemplo, la @).";
        if (clave.isEmpty()) return "Escribe tu contraseña.";
        return repo.ingresar(correo, clave) ? null : "Correo o contraseña incorrectos.";
    }

    /** El nombre debe empezar con una letra mayúscula (acepta tildes y Ñ). */
    public static boolean empiezaConMayuscula(String nombre) {
        if (nombre == null) return false;
        String limpio = nombre.trim();
        if (limpio.isEmpty()) return false;
        int primera = limpio.codePointAt(0);
        return Character.isLetter(primera) && Character.isUpperCase(primera);
    }

    private boolean esCorreoValido(String correo) {
        return correo != null && Patterns.EMAIL_ADDRESS.matcher(correo.trim()).matches();
    }
}
