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
        if (!esCorreoValido(correo)) return "Ingresa un correo válido.";
        if (clave.length() < 8) return "La contraseña necesita al menos 8 caracteres.";
        if (!clave.equals(confirmacion)) return "Las contraseñas no coinciden.";
        if (repo.existeCuenta()) return "Ya existe una cuenta en este dispositivo. Inicia sesión.";
        return repo.registrar(new Usuario(nombre.trim(), correo.trim()), clave)
                ? null : "No se pudo crear la cuenta local.";
    }

    public String ingresar(String correo, String clave) {
        if (!esCorreoValido(correo)) return "Ingresa un correo válido.";
        if (clave.isEmpty()) return "Escribe tu contraseña.";
        return repo.ingresar(correo, clave) ? null : "Correo o contraseña incorrectos.";
    }

    private boolean esCorreoValido(String correo) {
        return correo != null && Patterns.EMAIL_ADDRESS.matcher(correo.trim()).matches();
    }
}
