package com.kallpa.app.controller;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.ContextCompat;
import com.kallpa.app.model.RepositorioUbicacionLocal;
import com.kallpa.app.model.UbicacionParcela;
import java.util.concurrent.atomic.AtomicBoolean;

/** Ubicación puntual. No solicita seguimiento continuo ni ubicación en segundo plano. */
public class ControladorUbicacion {
    public interface Respuesta { void recibido(Location ubicacion); }
    private final RepositorioUbicacionLocal repo;
    public ControladorUbicacion(Context c) { repo = new RepositorioUbicacionLocal(c); }
    public boolean existe() { return repo.existe(); }
    public UbicacionParcela obtener() { return repo.obtener(); }
    public void guardar(double lat, double lon, String origen) {
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180 || !Double.isFinite(lat) || !Double.isFinite(lon))
            throw new IllegalArgumentException("Coordenadas fuera de rango");
        repo.guardar(new UbicacionParcela(lat, lon, origen, System.currentTimeMillis()));
    }
    public static boolean tienePermiso(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("MissingPermission")
    public static void obtenerUbicacionPuntual(Activity activity, Respuesta callback) {
        if (!tienePermiso(activity)) { callback.recibido(null); return; }
        LocationManager manager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) { callback.recibido(null); return; }
        String proveedor;
        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) proveedor = LocationManager.GPS_PROVIDER;
        else if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) proveedor = LocationManager.NETWORK_PROVIDER;
        else { callback.recibido(null); return; }
        final AtomicBoolean finalizado = new AtomicBoolean(false);
        Handler handler = new Handler(Looper.getMainLooper());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            CancellationSignal cancel = new CancellationSignal();
            handler.postDelayed(() -> {
                if (finalizado.compareAndSet(false, true)) {
                    cancel.cancel(); callback.recibido(null);
                }
            }, 12000);
            try {
                manager.getCurrentLocation(proveedor, cancel, activity.getMainExecutor(), location -> {
                    if (finalizado.compareAndSet(false, true)) callback.recibido(location);
                });
            } catch (SecurityException e) { if (finalizado.compareAndSet(false, true)) callback.recibido(null); }
        } else {
            final LocationListener[] escucha = new LocationListener[1];
            escucha[0] = new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    if (finalizado.compareAndSet(false, true)) callback.recibido(location);
                    manager.removeUpdates(this);
                }
                @Override public void onStatusChanged(String provider, int status, android.os.Bundle extras) { }
                @Override public void onProviderEnabled(String provider) { }
                @Override public void onProviderDisabled(String provider) { }
            };
            handler.postDelayed(() -> {
                if (finalizado.compareAndSet(false, true)) { manager.removeUpdates(escucha[0]); callback.recibido(null); }
            }, 12000);
            try { manager.requestSingleUpdate(proveedor, escucha[0], Looper.getMainLooper()); }
            catch (SecurityException e) { if (finalizado.compareAndSet(false, true)) callback.recibido(null); }
        }
    }
}
