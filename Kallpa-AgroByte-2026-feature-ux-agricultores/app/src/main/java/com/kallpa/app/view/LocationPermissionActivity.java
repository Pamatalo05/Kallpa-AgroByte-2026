
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

    public static final String EXTRA_GPS_LAT = "gps_lat";
    public static final String EXTRA_GPS_LON = "gps_lon";

    private ActivityLocationPermissionBinding binding;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        binding = ActivityLocationPermissionBinding.inflate(
                getLayoutInflater()
        );

        setContentView(binding.getRoot());

        // Detectar la ubicación del terreno
        binding.btnAllow.setOnClickListener(v -> permitir());

        // Elegir la ubicación manualmente
        binding.btnManual.setOnClickListener(v -> abrirMapa(null));
    }

    private void permitir() {

        if (!ControladorUbicacion.tienePermiso(this)) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    CODIGO_PERMISO
            );

        } else {
            obtenerUbicacion();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] results
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                results
        );

        if (requestCode != CODIGO_PERMISO) {
            return;
        }

        boolean concedido = false;

        for (int resultado : results) {
            if (resultado == PackageManager.PERMISSION_GRANTED) {
                concedido = true;
            }
        }

        if (concedido) {
            obtenerUbicacion();
        } else {
            mensaje("Puedes ubicar tu parcela manualmente.");
            abrirMapa(null);
        }
    }

    private void obtenerUbicacion() {

        binding.btnAllow.setEnabled(false);
        binding.btnAllow.setText("Buscando ubicación...");

        ControladorUbicacion.obtenerUbicacionPuntual(
                this,
                (Location lugar) -> runOnUiThread(() -> {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    binding.btnAllow.setEnabled(true);
                    binding.btnAllow.setText("Usar mi ubicación actual");

                    if (lugar != null) {

                        // Las coordenadas pertenecen a la parcela
                        abrirMapa(lugar);

                    } else {

                        mensaje(
                                "No encontramos tu ubicación. " +
                                        "Puedes elegirla en el mapa."
                        );

                        abrirMapa(null);
                    }
                })
        );
    }

    private void abrirMapa(Location ubicacion) {

        Intent intent = new Intent(
                this,
                ManualMapActivity.class
        );

        // Conservar todos los datos del formulario agrícola
        if (getIntent().getExtras() != null) {
            intent.putExtras(getIntent().getExtras());
        }

        // Incluir coordenadas GPS, cuando existan
        if (ubicacion != null) {
            intent.putExtra(
                    EXTRA_GPS_LAT,
                    ubicacion.getLatitude()
            );

            intent.putExtra(
                    EXTRA_GPS_LON,
                    ubicacion.getLongitude()
            );
        }

        startActivity(intent);
    }
}
