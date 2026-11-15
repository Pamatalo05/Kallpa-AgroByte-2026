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
            TextView vacio = texto("Todavía no tienes parcelas. Añade la primera para comenzar.", 16, false);
            binding.listContainer.addView(vacio); return;
        }
        for (Parcela p : parcelas) {
            LinearLayout tarjeta = new LinearLayout(this);
            tarjeta.setOrientation(LinearLayout.VERTICAL);
            tarjeta.setPadding(dp(18), dp(16), dp(18), dp(16));
            GradientDrawable fondo = new GradientDrawable();
            fondo.setColor(0xFFFFFFFF);
            fondo.setCornerRadius(dp(18));
            fondo.setStroke(dp(1), p.getId().equals(seleccionada) ? 0xFF67A83C : 0xFFCAD5BB);
            tarjeta.setBackground(fondo);
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(-1, -2);
            itemLp.bottomMargin = dp(14);
            binding.listContainer.addView(tarjeta, itemLp);

            tarjeta.addView(texto(p.getNombre() + (p.getId().equals(seleccionada) ? "  ✓ Activa" : ""), 19, true));
            tarjeta.addView(texto(String.format(Locale.forLanguageTag("es-EC"),
                    "%s · %.2f ha · %s", p.getCultivoActual(), p.getHectareas(), nombreCanton(p.getCanton())), 14, false));

            LinearLayout acciones = new LinearLayout(this);
            acciones.setOrientation(LinearLayout.HORIZONTAL);
            acciones.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
            actionsLp.topMargin = dp(10);
            tarjeta.addView(acciones, actionsLp);

            Button detalles = new Button(this);
            detalles.setText("Ver detalles");
            detalles.setAllCaps(false);
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
            acciones.addView(detalles, dLp);
            detalles.setOnClickListener(v -> {
                Intent i = new Intent(this, ParcelDetailsActivity.class);
                i.putExtra(ParcelDetailsActivity.EXTRA_ID, p.getId());
                startActivity(i);
            });

            Button elegir = new Button(this);
            elegir.setText(p.getId().equals(seleccionada) ? "Seleccionada" : "Seleccionar");
            elegir.setAllCaps(false);
            elegir.setEnabled(!p.getId().equals(seleccionada));
            LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
            eLp.leftMargin = dp(6);
            acciones.addView(elegir, eLp);
            elegir.setOnClickListener(v -> {
                getSharedPreferences("kallpa_seleccion", MODE_PRIVATE).edit().putString(key, p.getId()).apply();
                Intent i = new Intent(this, HomeActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
                finish();
            });
        }
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
