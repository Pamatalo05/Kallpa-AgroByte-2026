package com.kallpa.app.view;

import android.os.Bundle;
import android.content.Intent;
import androidx.appcompat.app.AlertDialog;
import com.kallpa.app.controller.ControladorAutenticacion;
import com.kallpa.app.controller.ControladorUbicacion;
import com.kallpa.app.databinding.ActivityLoginBinding;

/** VISTA: solo escucha eventos y muestra mensajes del controlador. */
public class LoginActivity extends ActividadBase {
    private ActivityLoginBinding binding;
    private ControladorAutenticacion auth;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        auth = new ControladorAutenticacion(this);
        if (auth.sesionActiva()) { continuar(); return; }
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnShowPassword.setOnClickListener(v -> alternarClave(binding.inputPassword));
        binding.btnLogin.setOnClickListener(v -> {
            String error = auth.ingresar(binding.inputEmail.getText().toString(), binding.inputPassword.getText().toString());
            if (error != null) mensaje(error); else continuar();
        });
        binding.btnGoRegister.setOnClickListener(v -> irA(RegisterActivity.class));
        binding.btnForgot.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Recuperar contraseña")
                .setMessage("Esta demostración solo admite cuentas locales. La recuperación segura de contraseña necesitará un servidor de autenticación en la versión real.")
                .setPositiveButton("Entendido", null).show());
    }
    private void continuar() {
        Class<?> pantalla = new ControladorUbicacion(this).existe() ? HomeActivity.class : LocationPermissionActivity.class;
        startActivity(new Intent(this, pantalla).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }
}
