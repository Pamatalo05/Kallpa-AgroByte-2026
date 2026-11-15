package com.kallpa.app.view;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import com.kallpa.app.controller.ControladorUbicacion;
import com.kallpa.app.databinding.ActivityLocationPermissionBinding;

public class LocationPermissionActivity extends ActividadBase {
    private static final int CODIGO_PERMISO = 42;
    private ActivityLocationPermissionBinding binding;
    private ControladorUbicacion controlador;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        controlador = new ControladorUbicacion(this);
        binding = ActivityLocationPermissionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnAllow.setOnClickListener(v -> permitir());
        binding.btnManual.setOnClickListener(v -> irA(ManualMapActivity.class));
    }
    private void permitir() {
        if (!ControladorUbicacion.tienePermiso(this)) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, CODIGO_PERMISO);
        } else obtener();
    }
    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != CODIGO_PERMISO) return;
        boolean concedido = false;
        for (int result : results) if (result == PackageManager.PERMISSION_GRANTED) concedido = true;
        if (concedido) obtener();
        else { mensaje("Puedes señalar tu terreno en el mapa sin compartir tu ubicación."); irA(ManualMapActivity.class); }
    }
    private void obtener() {
        binding.btnAllow.setEnabled(false);
        binding.btnAllow.setText("Buscando ubicación…");
        ControladorUbicacion.obtenerUbicacionPuntual(this, (Location lugar) -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            binding.btnAllow.setEnabled(true);
            binding.btnAllow.setText("Usar mi ubicación actual");
            if (lugar != null) {
                controlador.guardar(lugar.getLatitude(), lugar.getLongitude(), "gps");
                startActivity(new Intent(this, HomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            } else {
                mensaje("No se pudo obtener una ubicación precisa. Selecciónala en el mapa.");
                irA(ManualMapActivity.class);
            }
        }));
    }
}
