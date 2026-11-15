package com.kallpa.app.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.kallpa.app.R;
import java.util.Locale;

/**
 * Presentación compartida para ventanas emergentes de Kallpa.
 * Conserva los listeners, los textos, las opciones, los formularios y los datos
 * de los AlertDialog originales: solo modifica colores, cabecera y dimensiones.
 * No agrega conexiones de red ni calcula resultados científicos.
 */
public final class DialogoKallpa extends AlertDialog.Builder {
    private static final int BOSQUE = 0xFF0B5D2A;
    private static final int BOSQUE_OSCURO = 0xFF07391E;
    private static final int CREMA = 0xFFFFFCF5;
    private static final int HOJA_CLARA = 0xFFE7F1D6;
    private static final int BORDE = 0xFFD6E2C5;
    private static final int TEXTO = 0xFF3F5946;
    private static final int PELIGRO = 0xFFAD4A39;
    private final Context contexto;
    private CharSequence titulo;
    private View formulario;
    private boolean customTitleManual;

    public DialogoKallpa(Context context) {
        super(context);
        this.contexto = context;
    }

    @Override public DialogoKallpa setTitle(CharSequence title) {
        titulo = title;
        super.setTitle(title);
        return this;
    }

    @Override public DialogoKallpa setTitle(int titleId) {
        titulo = contexto.getString(titleId);
        super.setTitle(titleId);
        return this;
    }

    @Override public DialogoKallpa setView(View view) {
        formulario = view;
        super.setView(view);
        return this;
    }

    @Override public DialogoKallpa setCustomTitle(View view) {
        customTitleManual = true;
        super.setCustomTitle(view);
        return this;
    }

    @Override public AlertDialog create() {
        if (!customTitleManual && titulo != null && titulo.length() > 0) {
            super.setCustomTitle(cabecera(titulo.toString()));
        }
        AlertDialog dialogo = super.create();
        dialogo.setOnShowListener(ignored -> aplicarEstilo(dialogo));
        return dialogo;
    }

    private View cabecera(String nombre) {
        LinearLayout conjunto = new LinearLayout(contexto);
        conjunto.setOrientation(LinearLayout.VERTICAL);
        conjunto.setPadding(dp(23), dp(20), dp(23), dp(12));

        LinearLayout fila = new LinearLayout(contexto);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icono = new ImageView(contexto);
        icono.setImageResource(R.drawable.ic_kallpa_dialog_brote);
        icono.setColorFilter(BOSQUE);
        icono.setPadding(dp(11), dp(11), dp(11), dp(11));
        icono.setBackground(redondeado(HOJA_CLARA, 16, 0, 0));
        fila.addView(icono, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout textos = new LinearLayout(contexto);
        textos.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams pText = new LinearLayout.LayoutParams(0, -2, 1f);
        pText.leftMargin = dp(13);
        fila.addView(textos, pText);

        TextView insignia = new TextView(contexto);
        insignia.setText(seccion(nombre));
        insignia.setTextColor(BOSQUE);
        insignia.setTextSize(10);
        insignia.setLetterSpacing(0.075f);
        insignia.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textos.addView(insignia, new LinearLayout.LayoutParams(-1, -2));

        TextView tituloVisible = new TextView(contexto);
        tituloVisible.setText(nombre);
        tituloVisible.setTextColor(BOSQUE_OSCURO);
        tituloVisible.setTextSize(19);
        tituloVisible.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        tituloVisible.setMaxLines(3);
        LinearLayout.LayoutParams pTitulo = new LinearLayout.LayoutParams(-1, -2);
        pTitulo.topMargin = dp(3);
        textos.addView(tituloVisible, pTitulo);
        conjunto.addView(fila, new LinearLayout.LayoutParams(-1, -2));

        View rayita = new View(contexto);
        rayita.setBackground(redondeado(0xFFE8D99E, 4, 0, 0));
        LinearLayout.LayoutParams linea = new LinearLayout.LayoutParams(-1, dp(3));
        linea.topMargin = dp(15);
        conjunto.addView(rayita, linea);
        return conjunto;
    }

    private String seccion(String nombre) {
        String n = nombre.toLowerCase(Locale.ROOT);
        if (n.contains("cerrar sesión") || n.contains("cancelar")) return "CONFIRMACIÓN";
        if (n.contains("registro") || n.contains("actividad") || n.contains("terreno") || n.contains("satélite"))
            return "MI PARCELA";
        if (n.contains("dato") || n.contains("fuente") || n.contains("método") || n.contains("humedad") || n.contains("información"))
            return "INFORMACIÓN Y FUENTES";
        if (n.contains("mes") || n.contains("siembra") || n.contains("cultivo")) return "PLANIFICACIÓN";
        if (n.contains("muyu")) return "TU ACOMPAÑANTE";
        return "KALLPA · AGROBYTE";
    }

    private void aplicarEstilo(AlertDialog dialogo) {
        Window ventana = dialogo.getWindow();
        if (ventana != null) {
            ventana.setBackgroundDrawable(redondeado(CREMA, 25, BORDE, 1));
            ventana.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams p = ventana.getAttributes();
            p.dimAmount = 0.5f;
            ventana.setAttributes(p);
            int anchoPantalla = Resources.getSystem().getDisplayMetrics().widthPixels;
            int ancho = Math.min(anchoPantalla - dp(36), dp(520));
            ventana.setLayout(ancho, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        TextView mensaje = dialogo.findViewById(android.R.id.message);
        if (mensaje != null) {
            TextoDato.aplicar(mensaje, mensaje.getText(), false);
            mensaje.setTextColor(TEXTO);
            mensaje.setTextSize(14);
            mensaje.setLineSpacing(dp(4), 1.05f);
        }
        ListView lista = dialogo.getListView();
        if (lista != null) {
            lista.setDivider(new ColorDrawable(BORDE));
            lista.setDividerHeight(dp(1));
            lista.setPadding(dp(8), dp(2), dp(8), dp(6));
        }
        if (formulario != null) estilizarFormulario(formulario);
        boton(dialogo.getButton(AlertDialog.BUTTON_POSITIVE), true, esPeligro());
        boton(dialogo.getButton(AlertDialog.BUTTON_NEGATIVE), false, false);
        boton(dialogo.getButton(AlertDialog.BUTTON_NEUTRAL), false, false);
    }

    private boolean esPeligro() {
        return titulo != null && titulo.toString().toLowerCase(Locale.ROOT).contains("cerrar sesión");
    }

    private void boton(Button btn, boolean principal, boolean peligro) {
        if (btn == null) return;
        btn.setAllCaps(false);
        btn.setTextSize(14);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setMinHeight(dp(44));
        btn.setPadding(dp(13), dp(8), dp(13), dp(8));
        btn.setTextColor(principal ? Color.WHITE : BOSQUE);
        btn.setBackground(redondeado(principal ? (peligro ? PELIGRO : BOSQUE) : HOJA_CLARA,
                15, principal ? 0 : BORDE, principal ? 0 : 1));
    }

    private void estilizarFormulario(View vista) {
        if (vista instanceof EditText) {
            EditText e = (EditText) vista;
            e.setTextColor(BOSQUE_OSCURO);
            e.setHintTextColor(0xFF829183);
            e.setTextSize(14);
            e.setBackground(redondeado(0xFFFFFDF8, 12, BORDE, 1));
            e.setPadding(dp(12), dp(10), dp(12), dp(10));
        } else if (vista instanceof Spinner) {
            Spinner e = (Spinner) vista;
            e.setBackground(redondeado(0xFFF1F6E7, 12, BORDE, 1));
            e.setPadding(dp(9), dp(5), dp(9), dp(5));
        } else if (vista instanceof Button) {
            Button e = (Button) vista;
            e.setTextColor(BOSQUE);
            e.setAllCaps(false);
            e.setBackground(redondeado(HOJA_CLARA, 12, BORDE, 1));
        }
        if (vista instanceof ViewGroup) {
            ViewGroup p = (ViewGroup) vista;
            for (int i = 0; i < p.getChildCount(); i++) estilizarFormulario(p.getChildAt(i));
        }
    }

    private GradientDrawable redondeado(int relleno, int radioDp, int borde, int bordeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(relleno);
        d.setCornerRadius(dp(radioDp));
        if (bordeDp > 0) d.setStroke(dp(bordeDp), borde);
        return d;
    }

    private int dp(float dip) { return Math.round(dip * contexto.getResources().getDisplayMetrics().density); }
}
