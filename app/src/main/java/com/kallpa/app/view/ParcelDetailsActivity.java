package com.kallpa.app.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import com.kallpa.app.controller.ControladorAutenticacion;
import com.kallpa.app.databinding.ActivityParcelDetailsBinding;
import com.kallpa.app.model.Parcela;
import com.kallpa.app.model.RepositorioParcelasLocal;
import java.util.List;
import java.util.Locale;

public class ParcelDetailsActivity extends ActividadBase {
    public static final String EXTRA_ID = "detalle_parcela_id";
    private ActivityParcelDetailsBinding binding;
    private Parcela parcela;
    private String correo;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ControladorAutenticacion auth = new ControladorAutenticacion(this);
        if (!auth.sesionActiva()) { startActivity(new Intent(this, LoginActivity.class)); finish(); return; }
        correo = auth.usuarioActual().getCorreo();
        String id = getIntent().getStringExtra(EXTRA_ID);
        List<Parcela> parcelas = new RepositorioParcelasLocal(this).obtenerPorUsuario(correo);
        for (Parcela p : parcelas) if (p.getId().equals(id)) { parcela = p; break; }
        if (parcela == null) { mensaje("No encontramos esa parcela."); finish(); return; }
        binding = ActivityParcelDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> finish());
        binding.txtName.setText(parcela.getNombre());
        binding.txtData.setText(String.format(Locale.forLanguageTag("es-EC"),
                "Cultivo: %s\nÚltima siembra: %s\nCosecha: %s\nRiego: %s\nSuperficie: %.2f ha\nCantón: %s\nLatitud: %.6f\nLongitud: %.6f",
                parcela.getCultivoActual(), parcela.getFechaSiembra(), parcela.getFechaCosecha(),
                parcela.isTieneRiego() ? "Sí" : "No", parcela.getHectareas(),
                nombreCanton(parcela.getCanton()), parcela.getLatitud(), parcela.getLongitud()));
        binding.btnSelect.setOnClickListener(v -> {
            getSharedPreferences("kallpa_seleccion", MODE_PRIVATE).edit()
                    .putString("parcela_activa_" + correo.toLowerCase(Locale.ROOT), parcela.getId()).apply();
            Intent i = new Intent(this, HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });
    }

    private String nombreCanton(String c) {
        if ("daule".equalsIgnoreCase(c)) return "Daule (Guayas)";
        if ("babahoyo".equalsIgnoreCase(c)) return "Babahoyo (Los Ríos)";
        return c == null ? "Sin cantón" : c;
    }
}
