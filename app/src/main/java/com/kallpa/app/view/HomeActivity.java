package com.kallpa.app.view;

import android.content.Intent;
import android.os.Bundle;
import com.kallpa.app.controller.ControladorAutenticacion;
import com.kallpa.app.controller.ControladorUbicacion;
import com.kallpa.app.model.UbicacionParcela;
import com.kallpa.app.databinding.ActivityHomeBinding;
import java.util.Locale;

/** Pantalla de cierre del flujo. Posteriormente se conecta al dashboard agrícola. */
public class HomeActivity extends ActividadBase {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ActivityHomeBinding b = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        ControladorAutenticacion auth = new ControladorAutenticacion(this);
        ControladorUbicacion loc = new ControladorUbicacion(this);
        if (!auth.sesionActiva()) {
            startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish(); return;
        }
        b.txtHello.setText("¡Hola, " + auth.usuarioActual().getNombre() + "! 🌱");
        UbicacionParcela p = loc.obtener();
        if (p == null) b.txtLocation.setText("Todavía no has elegido una ubicación.");
        else b.txtLocation.setText(String.format(Locale.US,
                "Ubicación registrada: %.5f, %.5f\nOrigen: %s", p.getLatitud(), p.getLongitud(),
                p.getOrigen().equals("gps") ? "GPS (consulta puntual)" : "Mapa / coordenadas"));
        b.btnEdit.setOnClickListener(v -> irA(ManualMapActivity.class));
        b.btnLogout.setOnClickListener(v -> {
            auth.salir();
            startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        });
    }
}
