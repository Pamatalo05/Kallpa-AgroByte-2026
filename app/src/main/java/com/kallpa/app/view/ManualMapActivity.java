package com.kallpa.app.view;

import android.location.Location;
import android.os.Bundle;
import android.content.Intent;
import com.kallpa.app.controller.ControladorUbicacion;
import com.kallpa.app.databinding.ActivityManualMapBinding;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.events.MapEventsReceiver;
import java.io.File;
import java.util.Locale;

public class ManualMapActivity extends ActividadBase {
    private ActivityManualMapBinding binding;
    private ControladorUbicacion controlador;
    private Marker marcador;
    private GeoPoint seleccionado;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        controlador = new ControladorUbicacion(this);
        // Almacena mosaicos descargados en el almacenamiento privado de la app.
        File raiz = new File(getFilesDir(), "osmdroid"); raiz.mkdirs();
        File cache = new File(getCacheDir(), "osmdroid/tiles"); cache.mkdirs();
        Configuration.getInstance().setOsmdroidBasePath(raiz);
        Configuration.getInstance().setOsmdroidTileCache(cache);
        Configuration.getInstance().setUserAgentValue(getPackageName() + " Kallpa/1.0");
        binding = ActivityManualMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK);
        binding.mapView.setMultiTouchControls(true);
        binding.mapView.setTilesScaledToDpi(true);
        binding.mapView.getController().setZoom(12.5);
        binding.mapView.getController().setCenter(new GeoPoint(-2.1709, -79.9224)); // Guayas como punto inicial visual, NO ubicación del usuario
        binding.mapView.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint p) { marcar(p); return true; }
            @Override public boolean longPressHelper(GeoPoint p) { marcar(p); return true; }
        }));
        marcador = new Marker(binding.mapView);
        marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marcador.setTitle("Mi terreno");
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnPreview.setOnClickListener(v -> {
            GeoPoint p = leerCoords();
            if (p != null) { marcar(p); binding.mapView.getController().animateTo(p); }
        });
        binding.btnMyLocation.setOnClickListener(v -> {
            if (!ControladorUbicacion.tienePermiso(this)) {
                mensaje("Puedes marcar la ubicación tocando el mapa; para GPS, primero autoriza el permiso.");
                return;
            }
            binding.btnMyLocation.setEnabled(false);
            ControladorUbicacion.obtenerUbicacionPuntual(this, (Location p) -> runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                binding.btnMyLocation.setEnabled(true);
                if (p != null) {
                    GeoPoint geo = new GeoPoint(p.getLatitude(), p.getLongitude());
                    marcar(geo); binding.mapView.getController().animateTo(geo);
                } else mensaje("No se pudo localizar el dispositivo. Selecciona el punto manualmente.");
            }));
        });
        binding.btnConfirm.setOnClickListener(v -> {
            GeoPoint p = leerCoords();
            if (p == null) { mensaje("Toca el mapa o escribe coordenadas válidas."); return; }
            controlador.guardar(p.getLatitude(), p.getLongitude(), "manual");
            startActivity(new Intent(this, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
    }
    private void marcar(GeoPoint p) {
        seleccionado = p;
        binding.inputLat.setText(String.format(Locale.US, "%.6f", p.getLatitude()));
        binding.inputLon.setText(String.format(Locale.US, "%.6f", p.getLongitude()));
        binding.txtSelection.setText("Ubicación seleccionada");
        binding.mapView.getOverlays().remove(marcador);
        marcador.setPosition(p);
        binding.mapView.getOverlays().add(marcador);
        binding.mapView.invalidate();
    }
    private GeoPoint leerCoords() {
        try {
            double lat = Double.parseDouble(binding.inputLat.getText().toString().trim());
            double lon = Double.parseDouble(binding.inputLon.getText().toString().trim());
            if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180)
                return null;
            return new GeoPoint(lat, lon);
        } catch (NumberFormatException e) { return null; }
    }
    @Override protected void onResume() { super.onResume(); if (binding != null) binding.mapView.onResume(); }
    @Override protected void onPause() { if (binding != null) binding.mapView.onPause(); super.onPause(); }
    @Override protected void onDestroy() { if (binding != null) binding.mapView.onDetach(); super.onDestroy(); }
}
