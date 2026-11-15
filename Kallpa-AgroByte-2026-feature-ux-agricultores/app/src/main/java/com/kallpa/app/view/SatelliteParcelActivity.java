package com.kallpa.app.view;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import com.kallpa.app.controller.ControladorAutenticacion;
import com.kallpa.app.databinding.ActivitySatelliteParcelBinding;
import com.kallpa.app.model.Parcela;
import com.kallpa.app.model.RepositorioParcelasLocal;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.overlay.Marker;
import java.io.File;
import java.util.Locale;

/** Ubicación satelital de una parcela. No representa sus límites. */
public class SatelliteParcelActivity extends ActividadBase {
    public static final String EXTRA_ID = "satellite_parcel_id";
    private ActivitySatelliteParcelBinding binding;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ControladorAutenticacion auth = new ControladorAutenticacion(this);
        if (!auth.sesionActiva()) { finish(); return; }
        String id = getIntent().getStringExtra(EXTRA_ID);
        Parcela parcela = null;
        for (Parcela p : new RepositorioParcelasLocal(this).obtenerPorUsuario(auth.usuarioActual().getCorreo())) {
            if (p.getId().equals(id)) { parcela = p; break; }
        }
        if (parcela == null) {
            Toast.makeText(this, "No encontramos esta parcela", Toast.LENGTH_LONG).show();
            finish(); return;
        }
        binding = ActivitySatelliteParcelBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> finish());
        binding.txtParcelName.setText(parcela.getNombre());
        binding.txtParcelInfo.setText(String.format(Locale.forLanguageTag("es-EC"),
                "%s · %.2f ha · %s", parcela.getCultivoActual(), parcela.getHectareas(), parcela.getCanton()));
        double lat = parcela.getLatitud(), lon = parcela.getLongitud();
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            binding.txtMapInfo.setText("Esta parcela no tiene coordenadas válidas.");
            binding.mapView.setVisibility(View.GONE); return;
        }
        File base = new File(getFilesDir(), "osmdroid");
        File cache = new File(getCacheDir(), "osmdroid/tiles");
        base.mkdirs(); cache.mkdirs();
        Configuration.getInstance().setOsmdroidBasePath(base);
        Configuration.getInstance().setOsmdroidTileCache(cache);
        Configuration.getInstance().setUserAgentValue(getPackageName() + " Kallpa/1.0");
        XYTileSource satellite = new XYTileSource("EsriWorldImagery", 0, 19, 256, ".jpg",
                new String[]{"https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"}) {
            @Override public String getTileURLString(long index) {
                return getBaseUrl() + MapTileIndex.getZoom(index) + "/" +
                        MapTileIndex.getY(index) + "/" + MapTileIndex.getX(index);
            }
        };
        binding.mapView.setTileSource(satellite);
        binding.mapView.setMultiTouchControls(true);
        binding.mapView.setTilesScaledToDpi(true);
        GeoPoint point = new GeoPoint(lat, lon);
        binding.mapView.getController().setZoom(16.0);
        binding.mapView.getController().setCenter(point);
        Marker marker = new Marker(binding.mapView);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle(parcela.getNombre());
        marker.setSnippet("Ubicación registrada; no es el perímetro");
        binding.mapView.getOverlays().add(marker);
        binding.mapView.invalidate();
    }
    @Override protected void onResume() { super.onResume(); if (binding != null) binding.mapView.onResume(); }
    @Override protected void onPause() { if (binding != null) binding.mapView.onPause(); super.onPause(); }
    @Override protected void onDestroy() { if (binding != null) binding.mapView.onDetach(); super.onDestroy(); }
}
