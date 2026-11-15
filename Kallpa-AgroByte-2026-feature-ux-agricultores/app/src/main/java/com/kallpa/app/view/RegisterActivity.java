
package com.kallpa.app.view;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;

import com.kallpa.app.controller.ControladorAutenticacion;
import com.kallpa.app.databinding.ActivityRegisterBinding;

public class RegisterActivity extends ActividadBase {

    private ActivityRegisterBinding binding;
    private ControladorAutenticacion auth;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = new ControladorAutenticacion(this);

        // Mostrar u ocultar contraseñas
        binding.btnShowPassword.setOnClickListener(v ->
                alternarClave(binding.inputPassword));

        binding.btnShowConfirm.setOnClickListener(v ->
                alternarClave(binding.inputConfirm));

        // Crear cuenta
        binding.btnCreate.setOnClickListener(v -> registrarUsuario());

        // Volver al inicio de sesión
        binding.btnBackLogin.setOnClickListener(v -> finish());
    }

    private void registrarUsuario() {

        String nombre = binding.inputName.getText().toString().trim();
        String correo = binding.inputEmail.getText().toString().trim();
        String clave = binding.inputPassword.getText().toString();
        String confirmar = binding.inputConfirm.getText().toString();

        // No continuar mientras existan errores
        if (!validarCampos(nombre, correo, clave, confirmar)) {
            return;
        }

        // Guardar cuenta localmente usando el controlador existente
        String error = auth.registrar(nombre, correo, clave, confirmar);

        if (error != null) {
            mensaje(error);
            return;
        }

        avisoExito("¡Bienvenido! Tu cuenta quedó creada.");

        // Continuar con el registro de ubicación

        startActivity(new Intent(this, ParcelWelcomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();

    }

    private boolean validarCampos(
            String nombre,
            String correo,
            String clave,
            String confirmar
    ) {

        // Validar nombre
        if (nombre.isEmpty()) {
            binding.inputName.setError("El nombre es obligatorio");
            binding.inputName.requestFocus();
            return false;
        }

        if (nombre.length() < 2) {
            binding.inputName.setError("Ingresa un nombre válido");
            binding.inputName.requestFocus();
            return false;
        }

        if (!ControladorAutenticacion.empiezaConMayuscula(nombre)) {
            binding.inputName.setError("Empieza tu nombre con mayúscula. Ejemplo: María");
            binding.inputName.requestFocus();
            return false;
        }

        // Validar correo
        if (correo.isEmpty()) {
            binding.inputEmail.setError("El correo es obligatorio");
            binding.inputEmail.requestFocus();
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            binding.inputEmail.setError("Ingresa un correo válido");
            binding.inputEmail.requestFocus();
            return false;
        }

        // Validar contraseña
        if (clave.isEmpty() || clave.trim().isEmpty()) {
            binding.inputPassword.setError("La contraseña es obligatoria");
            binding.inputPassword.requestFocus();
            return false;
        }

        if (clave.length() < 8) {
            binding.inputPassword.setError(
                    "Debe tener al menos 8 caracteres"
            );
            binding.inputPassword.requestFocus();
            return false;
        }

        // Validar confirmación
        if (confirmar.isEmpty()) {
            binding.inputConfirm.setError("Confirma tu contraseña");
            binding.inputConfirm.requestFocus();
            return false;
        }

        if (!clave.equals(confirmar)) {
            binding.inputConfirm.setError(
                    "Las contraseñas no coinciden"
            );
            binding.inputConfirm.requestFocus();
            return false;
        }

        return true;
    }
}
