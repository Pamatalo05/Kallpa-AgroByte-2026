package com.kallpa.app.view;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;
import com.kallpa.app.R;
import com.kallpa.app.controller.ControladorAutenticacion;
import com.kallpa.app.databinding.ActivityMyParcelsBinding;
import com.kallpa.app.model.Parcela;
import com.kallpa.app.model.RepositorioParcelasLocal;
import java.util.List;
import java.util.Locale;

/** Listado completo, selección persistente y acceso a detalle. */
public class MyParcelsActivity extends ActividadBase {
    private ActivityMyParcelsBinding binding;
    private String correo;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ControladorAutenticacion auth = new ControladorAutenticacion(this);
        if (!auth.sesionActiva()) {
            startActivity(new Intent(this, LoginActivity.class)); finish(); return;
        }
        correo = auth.usuarioActual().getCorreo();
        binding = ActivityMyParcelsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> finish());
        activarBarraInferior(SECCION_PARCELA, false);
        binding.btnAddParcel.setOnClickListener(v ->
                startActivity(new Intent(this, ParcelFormActivity.class)));
    }

    @Override protected void onResume() {
        super.onResume();
        if (binding == null || correo == null) return;
        mostrarParcelas();
    }

    private void mostrarParcelas() {
        List<Parcela> parcelas = new RepositorioParcelasLocal(this).obtenerPorUsuario(correo);
        binding.listContainer.removeAllViews();
        String key = "parcela_activa_" + correo.toLowerCase(Locale.ROOT);
        String seleccionada = getSharedPreferences("kallpa_seleccion", MODE_PRIVATE).getString(key, "");
        if (parcelas.isEmpty()) {
            TextView vacio = texto("Todavía no tienes parcelas. Toca \"Añadir otra parcela\" para registrar la primera.", 16, false);
            binding.listContainer.addView(vacio); return;
        }
        for (Parcela p : parcelas) {
            LinearLayout tarjeta = new LinearLayout(this);
            tarjeta.setOrientation(LinearLayout.VERTICAL);
            tarjeta.setPadding(dp(18), dp(16), dp(18), dp(16));
            GradientDrawable fondo = new GradientDrawable();
            fondo.setColor(0xFFFFFFFF);
            fondo.setCornerRadius(dp(18));
            boolean activa = p.getId().equals(seleccionada);
            fondo.setStroke(dp(activa ? 2 : 1), activa ? 0xFF67A83C : 0xFFCAD5BB);
            tarjeta.setBackground(fondo);
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(-1, -2);
            itemLp.bottomMargin = dp(14);
            binding.listContainer.addView(tarjeta, itemLp);

            if (activa) {
                TextView etiqueta = texto("La estás viendo ahora", 13, true);
                etiqueta.setTextColor(0xFF0B5D2A);
                tarjeta.addView(etiqueta);
            }
            tarjeta.addView(texto(p.getNombre(), 20, true));
            tarjeta.addView(texto(p.getCultivoActual() + " · " + Formato.hectareas(p.getHectareas())
                    + " · " + nombreCanton(p.getCanton()), 15, false));

            LinearLayout acciones = new LinearLayout(this);
            acciones.setOrientation(LinearLayout.HORIZONTAL);
            acciones.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
            actionsLp.topMargin = dp(10);
            tarjeta.addView(acciones, actionsLp);

            Button detalles = new Button(this);
            detalles.setText("Ver datos");
            estiloBoton(detalles, false);
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
            acciones.addView(detalles, dLp);
            detalles.setOnClickListener(v -> {
                Intent i = new Intent(this, ParcelDetailsActivity.class);
                i.putExtra(ParcelDetailsActivity.EXTRA_ID, p.getId());
                startActivity(i);
            });

            Button elegir = new Button(this);
            elegir.setText(activa ? "Es la actual" : "Ver esta");
            estiloBoton(elegir, !activa);
            elegir.setEnabled(!activa);
            if (activa) {
                elegir.setBackgroundResource(R.drawable.bg_button_disabled);
                elegir.setTextColor(0xFF6D7468);
            }
            LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
            eLp.leftMargin = dp(10);
            acciones.addView(elegir, eLp);

            Button verMapa = new Button(this);
            verMapa.setText("Ver mapa satelital");
            estiloBoton(verMapa, false);
            LinearLayout.LayoutParams mapaLp = new LinearLayout.LayoutParams(-1, dp(48));
            mapaLp.topMargin = dp(8);
            tarjeta.addView(verMapa, mapaLp);
            verMapa.setOnClickListener(v -> {
                Intent intent = new Intent(this, SatelliteParcelActivity.class);
                intent.putExtra(SatelliteParcelActivity.EXTRA_ID, p.getId());
                startActivity(intent);
            });
            elegir.setOnClickListener(v -> {
                getSharedPreferences("kallpa_seleccion", MODE_PRIVATE).edit().putString(key, p.getId()).apply();
                Intent i = new Intent(this, HomeActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
                finish();
            });
        }
    }

    private void estiloBoton(Button boton, boolean principal) {
        boton.setAllCaps(false);
        boton.setTextSize(15);
        boton.setTypeface(null, Typeface.BOLD);
        boton.setTextColor(principal ? 0xFFFFFFFF : 0xFF0B5D2A);
        boton.setBackgroundResource(principal ? R.drawable.bg_button_primary : R.drawable.bg_button_soft);
        boton.setStateListAnimator(null);
    }

    private TextView texto(String mensaje, int size, boolean negrita) {
        TextView t = new TextView(this);
        t.setText(mensaje);
        t.setTextSize(size);
        t.setTextColor(negrita ? 0xFF07391E : 0xFF6D7468);
        if (negrita) t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, dp(3), 0, dp(5));
        return t;
    }
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    private String nombreCanton(String c) {
        if ("daule".equalsIgnoreCase(c)) return "Daule";
        if ("babahoyo".equalsIgnoreCase(c)) return "Babahoyo";
        return c == null ? "Sin cantón" : c;
    }
}
