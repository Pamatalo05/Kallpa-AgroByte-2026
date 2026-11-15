package com.kallpa.app.view;

import android.content.Intent;
import android.os.Bundle;
import com.kallpa.app.controller.ControladorAutenticacion;
import com.kallpa.app.databinding.ActivityRegisterBinding;

public class RegisterActivity extends ActividadBase {
    private ActivityRegisterBinding binding;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnShowPassword.setOnClickListener(v -> alternarClave(binding.inputPassword));
        binding.btnShowConfirm.setOnClickListener(v -> alternarClave(binding.inputConfirm));
        ControladorAutenticacion auth = new ControladorAutenticacion(this);
        binding.btnCreate.setOnClickListener(v -> {
            String error = auth.registrar(binding.inputName.getText().toString(),
                    binding.inputEmail.getText().toString(), binding.inputPassword.getText().toString(),
                    binding.inputConfirm.getText().toString());
            if (error != null) { mensaje(error); return; }
            startActivity(new Intent(this, LocationPermissionActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        });
        binding.btnBackLogin.setOnClickListener(v -> finish());
    }
}
