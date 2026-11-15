package com.kallpa.app.view;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.kallpa.app.R;
import com.kallpa.app.controller.ControladorAutenticacion;
import com.kallpa.app.model.Parcela;
import com.kallpa.app.model.RepositorioParcelasLocal;

import java.util.List;
import java.util.Locale;

/**
 * Pantalla de inicio de Kallpa - equipo AgroByte.
 * Solo organiza la navegacion: no recalcula ni altera los datos de Arianna.
 */
public class HomeActivity extends ActividadBase {

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_home);

        accion(R.id.btnMenu, this::mostrarMenu);
        accion(R.id.btnProfile, () -> abrirSoporte(SupportActivity.MODE_PROFILE));
        accion(R.id.btnAlerts, () -> abrirSoporte(SupportActivity.MODE_ALERTS));
        accion(R.id.btnChangeParcel, () -> irA(MyParcelsActivity.class));

        // Una funcion principal + cuatro tarjetas del diseno acordado.
        accion(R.id.btnRotations, () -> abrirCiencia(ScienceActivity.MODE_FUTURE));
        accion(R.id.btnPlotCard, () -> irA(MyParcelsActivity.class));
        accion(R.id.btnClimateCard, () -> abrirSoporte(SupportActivity.MODE_ALERTS));
        accion(R.id.btnCalendarCard, () -> abrirSoporte(SupportActivity.MODE_CALENDAR));
        accion(R.id.btnDiaryCard, () -> abrirComplementario(CompanionActivity.MODE_LOG));

        // Evidencias cientificas del MVP, accesibles sin saturar la portada.
        accion(R.id.btnPresent, () -> abrirCiencia(ScienceActivity.MODE_PRESENT));
        accion(R.id.btnPast, () -> abrirCiencia(ScienceActivity.MODE_PAST));
        accion(R.id.btnMuyu, () -> abrirComplementario(CompanionActivity.MODE_MUYU));
    }

    private void accion(int viewId, Runnable action) {
        View target = findViewById(viewId);
        if (target != null) target.setOnClickListener(v -> action.run());
    }

    @Override protected void onResume() {
        super.onResume();
        ControladorAutenticacion auth = new ControladorAutenticacion(this);
        if (!auth.sesionActiva() || auth.usuarioActual() == null) {
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return;
        }

        String correo = auth.usuarioActual().getCorreo();
        List<Parcela> parcelas = new RepositorioParcelasLocal(this).obtenerPorUsuario(correo);
        if (parcelas.isEmpty()) {
            Intent i = new Intent(this, ParcelWelcomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return;
        }

        String id = getSharedPreferences("kallpa_seleccion", MODE_PRIVATE)
                .getString("parcela_activa_" + correo.toLowerCase(Locale.ROOT), "");
        Parcela activa = parcelas.get(0);
        for (Parcela p : parcelas) {
            if (p.getId().equals(id)) {
                activa = p;
                break;
            }
        }

        String nombre = auth.usuarioActual().getNombre();
        if (nombre == null || nombre.trim().isEmpty()) nombre = "";
        else nombre = nombre.trim().split("\\s+")[0];

        ((TextView) findViewById(R.id.txtHello)).setText(
                nombre.isEmpty() ? "¡Hola!" : "¡Hola, " + nombre + "!");
        ((TextView) findViewById(R.id.txtParcelName)).setText(activa.getNombre());

        String canton = "daule".equalsIgnoreCase(activa.getCanton()) ? "Daule" :
                "babahoyo".equalsIgnoreCase(activa.getCanton()) ? "Babahoyo" : "Cantón por confirmar";
        ((TextView) findViewById(R.id.txtParcelDetails)).setText(
                String.format(Locale.forLanguageTag("es-EC"),
                        "%s  ·  %.2f ha  ·  %s",
                        activa.getCultivoActual(), activa.getHectareas(), canton));
        ((TextView) findViewById(R.id.txtParcelCount)).setText(
                parcelas.size() == 1 ? "Cambiar mi parcela" :
                        "Cambiar parcela · " + parcelas.size() + " registradas");

        ((TextView) findViewById(R.id.txtConnection)).setText(
                hayInternet() ? "En línea" : "Sin red");
    }

    private boolean hayInternet() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network n = cm.getActiveNetwork();
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void mostrarMenu() {
        PopupMenu menu = new PopupMenu(this, findViewById(R.id.btnMenu));
        menu.getMenu().add(0, 1, 0, "Inicio");
        menu.getMenu().add(0, 2, 1, "Mis parcelas");
        menu.getMenu().add(0, 3, 2, "Presente · Diagnóstico satelital");
        menu.getMenu().add(0, 4, 3, "Futuro · Próxima siembra");
        menu.getMenu().add(0, 5, 4, "Pasado · El Niño y La Niña");
        menu.getMenu().add(0, 9, 5, "Clima y alertas");
        menu.getMenu().add(0, 10, 6, "Calendario mensual");
        menu.getMenu().add(0, 12, 7, "Bitácora agrícola");
        menu.getMenu().add(0, 6, 8, "Hablar con Muyu");
        menu.getMenu().add(0, 7, 9, "Añadir parcela");
        menu.getMenu().add(0, 11, 10, "Perfil y datos sin conexión");
        menu.getMenu().add(0, 8, 11, "Cerrar sesión");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: return true;
                case 2: irA(MyParcelsActivity.class); return true;
                case 3: abrirCiencia(ScienceActivity.MODE_PRESENT); return true;
                case 4: abrirCiencia(ScienceActivity.MODE_FUTURE); return true;
                case 5: abrirCiencia(ScienceActivity.MODE_PAST); return true;
                case 9: abrirSoporte(SupportActivity.MODE_ALERTS); return true;
                case 10: abrirSoporte(SupportActivity.MODE_CALENDAR); return true;
                case 12: abrirComplementario(CompanionActivity.MODE_LOG); return true;
                case 6: abrirComplementario(CompanionActivity.MODE_MUYU); return true;
                case 7: irA(ParcelFormActivity.class); return true;
                case 11: abrirSoporte(SupportActivity.MODE_PROFILE); return true;
                case 8: confirmarSalir(); return true;
                default: return false;
            }
        });
        menu.show();
    }

    private void abrirCiencia(String modulo) {
        Intent intent = new Intent(this, ScienceActivity.class);
        intent.putExtra(ScienceActivity.EXTRA_MODE, modulo);
        startActivity(intent);
    }

    private void abrirComplementario(String modulo) {
        Intent intent = new Intent(this, CompanionActivity.class);
        intent.putExtra(CompanionActivity.EXTRA_MODE, modulo);
        startActivity(intent);
    }

    private void abrirSoporte(String modulo) {
        Intent intent = new Intent(this, SupportActivity.class);
        intent.putExtra(SupportActivity.EXTRA_MODE, modulo);
        startActivity(intent);
    }

    private void confirmarSalir() {
        new AlertDialog.Builder(this)
                .setTitle("¿Cerrar sesión?")
                .setMessage("Tus parcelas seguirán guardadas en este dispositivo.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Cerrar sesión", (d, w) -> {
                    new ControladorAutenticacion(this).salir();
                    Intent i = new Intent(this, LoginActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                }).show();
    }
}
