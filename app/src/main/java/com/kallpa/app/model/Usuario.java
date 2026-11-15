package com.kallpa.app.model;

/** Datos mínimos del usuario. La contraseña nunca se guarda aquí en texto claro. */
public class Usuario {
    private final String nombre;
    private final String correo;

    public Usuario(String nombre, String correo) {
        this.nombre = nombre;
        this.correo = correo;
    }
    public String getNombre() { return nombre; }
    public String getCorreo() { return correo; }
}
