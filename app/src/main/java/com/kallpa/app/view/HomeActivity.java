package com.kallpa.app.view;

import android.content.Intent;
import android.os.Bundle;
import com.kallpa.app.controller.ControladorAutenticacion;
import com.kallpa.app.databinding.ActivityHomeBinding;
import com.kallpa.app.model.Parcela;
import com.kallpa.app.model.RepositorioParcelasLocal;
import java.util.List;
import java.util.Locale;

/** Inicio real: la parcela activa es el contexto para los módulos agrícolas. */
public class HomeActivity extends ActividadBase {
    private ActivityHomeBinding binding;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnMyParcels.setOnClickListener(v ->
                startActivity(new Intent(this, MyParcelsActivity.class)));
        binding.btnAddParcel.setOnClickListener(v ->
                startActivity(new Intent(this, ParcelFormActivity.class)));
    }

    @Override protected void onResume() {
        super.onResume();
        ControladorAutenticacion auth = new ControladorAutenticacion(this);
        if (!auth.sesionActiva()) {
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
        String key = "parcela_activa_" + correo.toLowerCase(Locale.ROOT);
        String activaId = getSharedPreferences("kallpa_seleccion", MODE_PRIVATE).getString(key, "");
        Parcela activa = parcelas.get(0);
        for (Parcela p : parcelas) {
            if (p.getId().equals(activaId)) { activa = p; break; }
        }
        binding.txtHello.setText("¡Hola, " + auth.usuarioActual().getNombre() + "!");
        binding.txtParcelName.setText(activa.getNombre());
        binding.txtParcelDetails.setText(String.format(Locale.forLanguageTag("es-EC"),
                "%s  ·  %.2f ha\n%s  ·  Riego: %s",
                activa.getCultivoActual(), activa.getHectareas(),
                nombreCanton(activa.getCanton()), activa.isTieneRiego() ? "Sí" : "No"));
        binding.txtParcelCount.setText(parcelas.size() == 1 ? "1 parcela registrada" : parcelas.size() + " parcelas registradas");
    }

    private String nombreCanton(String canton) {
        if ("daule".equalsIgnoreCase(canton)) return "Daule, Guayas";
        if ("babahoyo".equalsIgnoreCase(canton)) return "Babahoyo, Los Ríos";
        return canton == null ? "Cantón no indicado" : canton;
    }
}
