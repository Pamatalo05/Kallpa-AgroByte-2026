package com.kallpa.app.view;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.kallpa.app.R;

public abstract class ActividadBase extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getColor(R.color.forest_dark));
        getWindow().setNavigationBarColor(getColor(R.color.cream));
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    /** Alterna el campo sin perder la posición del cursor. */
    protected void alternarClave(android.widget.EditText campo) {
        boolean oculta = campo.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod;
        campo.setTransformationMethod(oculta ? null : android.text.method.PasswordTransformationMethod.getInstance());
        campo.setSelection(campo.length());
    }
    protected void mensaje(String texto) { Toast.makeText(this, texto, Toast.LENGTH_LONG).show(); }
    protected void irA(Class<?> destino) {
        startActivity(new android.content.Intent(this, destino));
    }
}
