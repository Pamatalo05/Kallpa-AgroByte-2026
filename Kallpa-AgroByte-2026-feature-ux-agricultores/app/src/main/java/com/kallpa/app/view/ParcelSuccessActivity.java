package com.kallpa.app.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.kallpa.app.R;

public class ParcelSuccessActivity extends ActividadBase {

    public static final String EXTRA_PARCELA =
            "nombre_parcela_guardada";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_parcel_success);

        TextView txtResumen =
                findViewById(R.id.txtParcelSummary);

        Button btnOtra =
                findViewById(R.id.btnAddAnotherParcel);

        Button btnInicio =
                findViewById(R.id.btnGoHome);

        // Mostrar el nombre de la parcela registrada
        String nombre = getIntent()
                .getStringExtra(EXTRA_PARCELA);

        if (nombre != null && !nombre.trim().isEmpty()) {
            txtResumen.setText(
                    "¡" + nombre + " se guardó correctamente!"
            );
        } else {
            txtResumen.setText(
                    "Tu parcela se guardó en este dispositivo."
            );
        }

        // Registrar otra parcela
        btnOtra.setOnClickListener(v -> {

            Intent intent = new Intent(
                    this,
                    ParcelFormActivity.class
            );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            );

            startActivity(intent);
            finish();
        });

        // Ir a la pantalla principal
        btnInicio.setOnClickListener(v -> {

            Intent intent = new Intent(
                    this,
                    HomeActivity.class
            );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            );

            startActivity(intent);
            finish();
        });
    }
}
