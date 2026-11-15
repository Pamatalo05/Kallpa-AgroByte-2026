package com.kallpa.app.view;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.kallpa.app.R;
import com.kallpa.app.controller.ControladorAutenticacion;
import com.kallpa.app.model.Parcela;
import com.kallpa.app.model.RepositorioParcelasLocal;
import java.util.List;
import java.util.Locale;

/** Inicio y menú principal del MVP Android Kallpa. */
public class HomeActivity extends ActividadBase {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_home);
        findViewById(R.id.btnMenu).setOnClickListener(v -> mostrarMenu());
        findViewById(R.id.btnMyParcels).setOnClickListener(v -> irA(MyParcelsActivity.class));
        findViewById(R.id.btnAddParcel).setOnClickListener(v -> irA(ParcelFormActivity.class));
        findViewById(R.id.btnDiagnosis).setOnClickListener(v -> abrirCiencia(ScienceActivity.MODE_PRESENT));
        findViewById(R.id.btnFuture).setOnClickListener(v -> abrirCiencia(ScienceActivity.MODE_FUTURE));
        findViewById(R.id.btnPast).setOnClickListener(v -> abrirCiencia(ScienceActivity.MODE_PAST));
        findViewById(R.id.btnMuyu).setOnClickListener(v -> abrirComplementario(CompanionActivity.MODE_MUYU));
    }

    @Override protected void onResume() {
        super.onResume();
        ControladorAutenticacion auth = new ControladorAutenticacion(this);
        if (!auth.sesionActiva() || auth.usuarioActual() == null) {
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i); finish(); return;
        }
        String correo = auth.usuarioActual().getCorreo();
        List<Parcela> parcelas = new RepositorioParcelasLocal(this).obtenerPorUsuario(correo);
        if (parcelas.isEmpty()) {
            Intent i = new Intent(this, ParcelWelcomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i); finish(); return;
        }
        String id = getSharedPreferences("kallpa_seleccion", MODE_PRIVATE)
                .getString("parcela_activa_" + correo.toLowerCase(Locale.ROOT), "");
        Parcela activa = parcelas.get(0);
        for (Parcela p : parcelas) if (p.getId().equals(id)) { activa = p; break; }
        String nombre = auth.usuarioActual().getNombre();
        if (nombre != null && nombre.trim().contains(" ")) nombre = nombre.trim().split("\\s+")[0];
        ((TextView)findViewById(R.id.txtHello)).setText("¡Hola, " + (nombre == null ? "" : nombre) + "!");
        ((TextView)findViewById(R.id.txtParcelName)).setText(activa.getNombre());
        ((TextView)findViewById(R.id.txtParcelDetails)).setText(String.format(Locale.forLanguageTag("es-EC"),
                "%s  ·  %.2f ha\n%s  ·  Riego: %s", activa.getCultivoActual(), activa.getHectareas(),
                "daule".equalsIgnoreCase(activa.getCanton()) ? "Daule, Guayas" :
                "babahoyo".equalsIgnoreCase(activa.getCanton()) ? "Babahoyo, Los Ríos" : "Zona sin identificar",
                activa.isTieneRiego() ? "Sí" : "No"));
        ((TextView)findViewById(R.id.txtParcelCount)).setText(parcelas.size() == 1
                ? "1 parcela registrada · toca «Mis parcelas» para cambiarla"
                : parcelas.size() + " parcelas registradas · puedes cambiar la activa");
        boolean online = hayInternet();
        ((TextView)findViewById(R.id.txtConnection)).setText(online
                ? "● En línea · datos agrícolas guardados localmente"
                : "● Sin conexión · consultas agrícolas locales disponibles");
    }

    private boolean hayInternet() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network n = cm.getActiveNetwork();
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void mostrarMenu() {
        PopupMenu menu = new PopupMenu(this, findViewById(R.id.btnMenu));
        menu.getMenu().add(0,1,0,"Inicio");
        menu.getMenu().add(0,2,1,"Mis parcelas");
        menu.getMenu().add(0,3,2,"Presente · Diagnóstico satelital");
        menu.getMenu().add(0,4,3,"Futuro · Próxima siembra");
        menu.getMenu().add(0,5,4,"Pasado · Historia ENSO");
        menu.getMenu().add(0,6,5,"Hablar con Muyu");
        menu.getMenu().add(0,12,6,"Mi bitácora agrícola");
        menu.getMenu().add(0,7,6,"Añadir otra parcela");
        menu.getMenu().add(0,9,7,"Clima y alertas para mi siembra");
        menu.getMenu().add(0,10,8,"Calendario mensual de referencia");
        menu.getMenu().add(0,11,9,"Perfil y datos sin conexión");
        menu.getMenu().add(0,8,10,"Cerrar sesión");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: break;
                case 2: irA(MyParcelsActivity.class); break;
                case 3: abrirCiencia(ScienceActivity.MODE_PRESENT); break;
                case 4: abrirCiencia(ScienceActivity.MODE_FUTURE); break;
                case 5: abrirCiencia(ScienceActivity.MODE_PAST); break;
                case 6: abrirComplementario(CompanionActivity.MODE_MUYU); break;
                case 12: abrirComplementario(CompanionActivity.MODE_LOG); break;
                case 7: irA(ParcelFormActivity.class); break;
                case 9: abrirSoporte(SupportActivity.MODE_ALERTS); break;
                case 10: abrirSoporte(SupportActivity.MODE_CALENDAR); break;
                case 11: abrirSoporte(SupportActivity.MODE_PROFILE); break;
                case 8: confirmarSalir(); break;
                default: return false;
            }
            return true;
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
                .setPositiveButton("Cerrar sesión", (d,w) -> {
                    new ControladorAutenticacion(this).salir();
                    Intent i = new Intent(this, LoginActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i); finish();
                }).show();
    }
}
