package com.kallpa.app.view;

import android.content.Intent;
import android.os.Bundle;

import com.kallpa.app.R;

public class ParcelWelcomeActivity extends ActividadBase {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_parcel_welcome);

        // Abrir el formulario agrícola
        findViewById(R.id.btnAddFirstParcel)
                .setOnClickListener(v -> {

                    Intent intent = new Intent(
                            this,
                            ParcelFormActivity.class
                    );

                    startActivity(intent);
                });
    }
}