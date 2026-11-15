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
        accion(R.id.btnChangeParcel, () -> irA(MyParcelsActivity.class));

        // Una acción principal y cuatro accesos. Mi parcela, Siembra y Clima están
        // en la barra inferior; Muyu flota sobre la pantalla.
        accion(R.id.btnRotations, () -> abrirCiencia(ScienceActivity.MODE_FUTURE));
        accion(R.id.btnPlotCard, () -> irA(MyParcelsActivity.class));
        accion(R.id.btnClimateCard, () -> abrirCiencia(ScienceActivity.MODE_PAST));
        accion(R.id.btnCalendarCard, () -> abrirSoporte(SupportActivity.MODE_CALENDAR));
        accion(R.id.btnDiaryCard, () -> abrirComplementario(CompanionActivity.MODE_LOG));
        activarBarraInferior(SECCION_INICIO, true);
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
                activa.getCultivoActual() + " · " + Formato.hectareas(activa.getHectareas())
                        + " · " + canton);
        ((TextView) findViewById(R.id.txtParcelCount)).setText(
                parcelas.size() == 1 ? "Cambiar" : "Cambiar (" + parcelas.size() + ")");

        // Kallpa funciona sin internet: el aviso solo aparece si hace falta y no asusta.
        findViewById(R.id.txtConnection).setVisibility(hayInternet() ? View.GONE : View.VISIBLE);
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
        // Lo más usado está en la barra inferior; aquí queda el resto.
        menu.getMenu().add(0, 11, 0, "Mi perfil");
        menu.getMenu().add(0, 7, 1, "Añadir otra parcela");
        menu.getMenu().add(0, 6, 2, "Preguntarle a Muyu");
        menu.getMenu().add(0, 8, 3, "Cerrar sesión");
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
                case 6: abrirMuyu(); return true;
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
                .setMessage("Tranquilo: tus parcelas y tu bitácora se quedan guardadas en este teléfono.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Sí, cerrar", (d, w) -> {
                    new ControladorAutenticacion(this).salir();
                    Intent i = new Intent(this, LoginActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                }).show();
    }
}
