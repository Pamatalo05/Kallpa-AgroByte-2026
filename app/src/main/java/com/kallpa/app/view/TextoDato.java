package com.kallpa.app.view;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.widget.TextView;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Etiquetas en negrita, valores en peso normal; compartido por pantallas y diálogos. */
public final class TextoDato {
    private TextoDato() { }
    private static final Pattern CAMPO = Pattern.compile(
            "(?:^|\\n|[·•]\\s*)([\\p{L}¿][\\p{L}\\p{N}\\p{Zs}()/%¿?+\\-]{0,55}:)(?!//)");

    public static CharSequence formatear(CharSequence texto) {
        if (texto == null) return "";
        SpannableString resultado = new SpannableString(texto);
        Matcher m = CAMPO.matcher(texto);
        while (m.find()) resultado.setSpan(new StyleSpan(Typeface.BOLD), m.start(1), m.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return resultado;
    }

    public static void aplicar(TextView vista, CharSequence texto, boolean titulo) {
        CharSequence seguro = texto == null ? "" : texto;
        vista.setTypeface(null, titulo && !CAMPO.matcher(seguro).find() ? Typeface.BOLD : Typeface.NORMAL);
        vista.setText(formatear(seguro));
    }
}
