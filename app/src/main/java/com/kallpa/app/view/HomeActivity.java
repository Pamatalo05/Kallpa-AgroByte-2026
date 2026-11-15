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
        accion(R.id.btnResumenMes, () -> abrirSoporte(SupportActivity.MODE_ALERTS));
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

        mostrarResumenDelMes(activa);

        // Kallpa funciona sin internet: el aviso solo aparece si hace falta y no asusta.
        findViewById(R.id.txtConnection).setVisibility(hayInternet() ? View.GONE : View.VISIBLE);
    }

    /**
     * Resumen del mes actual para la parcela activa: semáforo, lluvia y humedad típicas.
     * Usa el mismo paquete_canton.json y la misma regla (MotorSemaforo) que Clima.
     */
    private void mostrarResumenDelMes(Parcela activa) {
        View tarjeta = findViewById(R.id.btnResumenMes);
        String canton = activa.getCanton() == null ? "" : activa.getCanton().trim().toLowerCase(Locale.ROOT);
        int mes = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1;
        try (java.io.InputStream entrada = getAssets().open("datos/paquete_canton.json")) {
            java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream();
            byte[] bloque = new byte[8192];
            int n;
            while ((n = entrada.read(bloque)) != -1) salida.write(bloque, 0, n);
            org.json.JSONObject raiz = new org.json.JSONObject(
                    new String(salida.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
            org.json.JSONArray serie = raiz.getJSONObject("cantones").getJSONObject(canton)
                    .getJSONArray("serie_satelital_ml");
            for (int i = 0; i < serie.length(); i++) {
                org.json.JSONObject dato = serie.getJSONObject(i);
                if (dato.optInt("mes") != mes) continue;
                double humedad = dato.getJSONObject("humedad_raiz_predicha").getDouble("neutro");
                com.kallpa.app.model.MotorSemaforo.Resultado r =
                        com.kallpa.app.model.MotorSemaforo.evaluar(humedad, activa.isTieneRiego());
                String[] meses = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
                        "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
                ((TextView) findViewById(R.id.txtResumenTitulo)).setText(
                        "Si siembras en " + meses[mes - 1] + " en " + ("babahoyo".equals(canton) ? "Babahoyo" : "Daule"));
                TextView estado = findViewById(R.id.txtResumenEstado);
                estado.setText(Formato.semaforo(r.color));
                estado.setTextColor("Verde".equals(r.color) ? 0xFF0B5D2A
                        : "Amarillo".equals(r.color) ? 0xFF916900 : 0xFFB24336);
                ((TextView) findViewById(R.id.txtResumenDetalle)).setText(
                        "Lluvia normal del mes: unos " + Formato.numero(dato.optDouble("lluvia_mm")) + " mm\n"
                        + "Suelo " + Formato.humedadEnPalabras(humedad) + " (humedad cerca de "
                        + Formato.porcentaje(humedad) + ")\n"
                        + "Toca para ver qué hacer");
                tarjeta.setVisibility(View.VISIBLE);
                return;
            }
            tarjeta.setVisibility(View.GONE);
        } catch (Exception sinDatos) {
            // Cantón fuera del piloto o paquete ausente: no se muestra el resumen.
            tarjeta.setVisibility(View.GONE);
        }
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
