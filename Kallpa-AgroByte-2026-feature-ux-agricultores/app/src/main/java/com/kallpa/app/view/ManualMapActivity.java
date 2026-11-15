package com.kallpa.app.view;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import com.kallpa.app.controller.ControladorAutenticacion;
import com.kallpa.app.controller.ControladorUbicacion;
import com.kallpa.app.databinding.ActivityManualMapBinding;
import com.kallpa.app.model.Parcela;
import com.kallpa.app.model.RepositorioParcelasLocal;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.events.MapEventsReceiver;
import java.io.File;
import java.util.Locale;

/** El GPS y el marcador pertenecen a la PARCELA, no a la cuenta. */
public class ManualMapActivity extends ActividadBase {
    private ActivityManualMapBinding binding;
    private Marker marcador;
    private GeoPoint seleccionado;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
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
        // Punto de referencia Daule; no representa la ubicación del agricultor.
        binding.mapView.getController().setCenter(new GeoPoint(-1.8595, -79.9810));

        binding.mapView.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint p) { marcar(p); return true; }
            @Override public boolean longPressHelper(GeoPoint p) { marcar(p); return true; }
        }));
        marcador = new Marker(binding.mapView);
        marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marcador.setTitle("Ubicación de mi parcela");

        // Si venimos de GPS, mostrar el punto detectado sobre el mapa.
        Intent entrada = getIntent();
        if (entrada.hasExtra(LocationPermissionActivity.EXTRA_GPS_LAT)
                && entrada.hasExtra(LocationPermissionActivity.EXTRA_GPS_LON)) {
            double lat = entrada.getDoubleExtra(LocationPermissionActivity.EXTRA_GPS_LAT, Double.NaN);
            double lon = entrada.getDoubleExtra(LocationPermissionActivity.EXTRA_GPS_LON, Double.NaN);
            if (coordsValidas(lat, lon)) {
                GeoPoint gps = new GeoPoint(lat, lon);
                marcar(gps);
                binding.mapView.getController().setCenter(gps);
            }
        }

        // Accesos rapidos: se centran en el canton, pero la persona debe
        // tocar el mapa para indicar la posicion real de su parcela.
        binding.btnDaule.setOnClickListener(v -> irACanton(-1.8595, -79.9810, "Daule"));
        binding.btnBabahoyo.setOnClickListener(v -> irACanton(-1.8000, -79.5300, "Babahoyo"));

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnPreview.setOnClickListener(v -> {
            GeoPoint p = leerCoords();
            if (p != null) { marcar(p); binding.mapView.getController().animateTo(p); }
            else mensaje("Revisa los números de la ubicación: parece que falta o sobra algo.");
        });
        binding.btnMyLocation.setOnClickListener(v -> {
            if (!ControladorUbicacion.tienePermiso(this)) {
                mensaje("Para usar tu ubicación, Kallpa necesita tu permiso. También puedes tocar el mapa donde está tu terreno.");
                return;
            }
            binding.btnMyLocation.setEnabled(false);
            ControladorUbicacion.obtenerUbicacionPuntual(this, (Location p) -> runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                binding.btnMyLocation.setEnabled(true);
                if (p != null) {
                    GeoPoint geo = new GeoPoint(p.getLatitude(), p.getLongitude());
                    marcar(geo);
                    binding.mapView.getController().animateTo(geo);
                } else mensaje("No encontramos tu ubicación. Toca el mapa donde está tu terreno.");
            }));
        });
        binding.btnConfirm.setOnClickListener(v -> confirmarUbicacion());
    }

    private void confirmarUbicacion() {
        GeoPoint punto = leerCoords();
        if (punto == null) { mensaje("Toca el mapa donde está tu terreno."); return; }
        if (!getIntent().hasExtra(ParcelFormActivity.EXTRA_NOMBRE)) {
            mensaje("Primero cuéntanos sobre tu parcela.");
            startActivity(new Intent(this, ParcelFormActivity.class));
            finish();
            return;
        }
        // El GeoJSON piloto no contiene límites cantonales completos.
        // Pedimos el cantón explícitamente, sin fingir geolocalización administrativa exacta.
        String[] cantones = {"Daule (Guayas)", "Babahoyo (Los Ríos)"};
        final int[] cantonElegido = {-1};
        AlertDialog dialogo = new AlertDialog.Builder(this)
                .setTitle("¿En qué cantón está tu parcela?")
                // setSingleChoiceItems crea las opciones visibles del diálogo.
                // No mezclarlo con setMessage(): el mensaje oculta la lista.
                .setSingleChoiceItems(cantones, -1, (dialog, opcion) -> {
                    cantonElegido[0] = opcion;
                    ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE)
                            .setEnabled(true);
                })
                .setPositiveButton("Confirmar y guardar", (dialog, which) -> {
                    if (cantonElegido[0] >= 0) {
                        guardarParcela(punto, cantonElegido[0] == 0 ? "daule" : "babahoyo");
                    }
                })
                .setNegativeButton("Cancelar", null)
                .create();
        dialogo.show();
        // Evita guardar una parcela sin elegir su cantón.
        dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    }

    private void guardarParcela(GeoPoint punto, String canton) {
        Intent entrada = getIntent();
        String nombre = entrada.getStringExtra(ParcelFormActivity.EXTRA_NOMBRE);
        String cultivo = entrada.getStringExtra(ParcelFormActivity.EXTRA_CULTIVO);
        String siembra = entrada.getStringExtra(ParcelFormActivity.EXTRA_SIEMBRA);
        String cosecha = entrada.getStringExtra(ParcelFormActivity.EXTRA_COSECHA);
        double hectareas = entrada.getDoubleExtra(ParcelFormActivity.EXTRA_HECTAREAS, Double.NaN);
        if (nombre == null || nombre.trim().isEmpty() || cultivo == null || cultivo.isEmpty()
                || siembra == null || cosecha == null || !Double.isFinite(hectareas)
                || hectareas < 1 || hectareas > 50) {
            mensaje("Faltan datos de tu parcela. Vuelve atrás y complétalos.");
            return;
        }
        ControladorAutenticacion auth = new ControladorAutenticacion(this);
        if (!auth.sesionActiva()) {
            mensaje("Inicia sesión para guardar tu parcela.");
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }
        String correo = auth.usuarioActual().getCorreo();
        Parcela nueva = new Parcela(correo, nombre.trim(), cultivo, siembra, cosecha,
                entrada.getBooleanExtra(ParcelFormActivity.EXTRA_RIEGO, false),
                hectareas, canton, punto.getLatitude(), punto.getLongitude());
        binding.btnConfirm.setEnabled(false);
        if (!new RepositorioParcelasLocal(this).guardar(nueva)) {
            binding.btnConfirm.setEnabled(true);
            avisoError("No pudimos guardar tu parcela. Inténtalo otra vez.");
            return;
        }
        getSharedPreferences("kallpa_seleccion", MODE_PRIVATE).edit()
                .putString("parcela_activa_" + correo.toLowerCase(Locale.ROOT), nueva.getId()).apply();
        Intent destino = new Intent(this, ParcelSuccessActivity.class);
        destino.putExtra(ParcelSuccessActivity.EXTRA_PARCELA, nueva.getNombre());
        destino.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(destino);
        finish();
    }

    private void irACanton(double lat, double lon, String nombre) {
        // Limpiar el punto anterior evita guardar por accidente una parcela
        // de otro canton despues de cambiar la vista del mapa.
        seleccionado = null;
        binding.inputLat.setText("");
        binding.inputLon.setText("");
        binding.mapView.getOverlays().remove(marcador);
        binding.txtSelection.setText("Mapa en " + nombre + ". Toca el terreno para marcarlo.");
        binding.mapView.getController().setZoom(13.0);
        binding.mapView.getController().animateTo(new GeoPoint(lat, lon));
        binding.mapView.invalidate();
    }

    private void marcar(GeoPoint p) {
        seleccionado = p;
        binding.inputLat.setText(String.format(Locale.US, "%.6f", p.getLatitude()));
        binding.inputLon.setText(String.format(Locale.US, "%.6f", p.getLongitude()));
        binding.txtSelection.setText("Ubicación de la parcela seleccionada");
        binding.mapView.getOverlays().remove(marcador);
        marcador.setPosition(p);
        binding.mapView.getOverlays().add(marcador);
        binding.mapView.invalidate();
    }

    private GeoPoint leerCoords() {
        try {
            double lat = Double.parseDouble(binding.inputLat.getText().toString().trim());
            double lon = Double.parseDouble(binding.inputLon.getText().toString().trim());
            if (!coordsValidas(lat, lon)) return null;
            return new GeoPoint(lat, lon);
        } catch (NumberFormatException e) { return null; }
    }
    private boolean coordsValidas(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon)
                && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }
    @Override protected void onResume() { super.onResume(); if (binding != null) binding.mapView.onResume(); }
    @Override protected void onPause() { if (binding != null) binding.mapView.onPause(); super.onPause(); }
    @Override protected void onDestroy() { if (binding != null) binding.mapView.onDetach(); super.onDestroy(); }
}
