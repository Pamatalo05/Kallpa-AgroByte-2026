package com.kallpa.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import com.kallpa.app.R;

/**
 * Gráfico pensado para leerse de un vistazo:
 * - meses o fechas escritos debajo,
 * - valores con su unidad (mm, %),
 * - una franja verde opcional que marca "lo bueno para sembrar",
 * - el punto o barra destacada (por ejemplo, el mes actual) en color fuerte.
 */
public class GraficoSencillo extends View {

    /** Convierte un valor del gráfico en texto con unidad: 187 -> "187 mm". */
    public interface Etiqueta {
        String texto(float valor);
    }

    private final float[] valores;
    private final String[] etiquetasX;
    private final boolean barras;
    private final int color;
    private final Etiqueta etiqueta;
    private float minimo, maximo;
    private float franjaDesde = Float.NaN, franjaHasta = Float.NaN;
    private String franjaTexto;
    private int destacado = -1;

    private final Paint pincel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path camino = new Path();
    private final RectF rect = new RectF();

    private GraficoSencillo(Context contexto, float[] valores, String[] etiquetasX, boolean barras,
                            int color, Etiqueta etiqueta) {
        super(contexto);
        this.valores = valores;
        this.etiquetasX = etiquetasX;
        this.barras = barras;
        this.color = color;
        this.etiqueta = etiqueta;
        setBackgroundResource(R.drawable.bg_card);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float v : valores) { min = Math.min(min, v); max = Math.max(max, v); }
        // Las barras siempre parten de cero para no exagerar diferencias.
        minimo = barras ? 0f : min;
        maximo = max <= minimo ? minimo + 1f : max;
        setContentDescription("Gráfico");
    }

    /** Barras, una por mes (ideal para lluvia). */
    public static GraficoSencillo barras(Context c, float[] valores, String[] etiquetasX,
                                         int color, Etiqueta etiqueta) {
        return new GraficoSencillo(c, valores, etiquetasX, true, color, etiqueta);
    }

    /** Línea con puntos (ideal para humedad o verdor). */
    public static GraficoSencillo linea(Context c, float[] valores, String[] etiquetasX,
                                        int color, Etiqueta etiqueta) {
        return new GraficoSencillo(c, valores, etiquetasX, false, color, etiqueta);
    }

    /** Fija la escala vertical (por ejemplo, humedad de 40 % a 90 %). */
    public GraficoSencillo escala(float desde, float hasta) {
        minimo = Math.min(desde, minimo);
        maximo = Math.max(hasta, maximo);
        return this;
    }

    /** Marca una franja de valores buenos con un texto corto. */
    public GraficoSencillo franja(float desde, float hasta, String texto) {
        franjaDesde = desde; franjaHasta = hasta; franjaTexto = texto;
        return this;
    }

    /** Resalta una posición (por ejemplo, el mes actual). */
    public GraficoSencillo destacar(int indice) {
        destacado = indice;
        return this;
    }

    @Override protected void onMeasure(int anchoSpec, int altoSpec) {
        super.onMeasure(anchoSpec, MeasureSpec.makeMeasureSpec(dp(230), MeasureSpec.EXACTLY));
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (valores.length == 0) return;
        float izq = dp(46), der = getWidth() - dp(14), arriba = dp(24), abajo = getHeight() - dp(30);

        // Franja "buena" en verde claro.
        if (!Float.isNaN(franjaDesde)) {
            float y1 = y(Math.min(franjaHasta, maximo), arriba, abajo);
            float y2 = y(Math.max(franjaDesde, minimo), arriba, abajo);
            pincel.setStyle(Paint.Style.FILL);
            pincel.setColor(0x2667A83C);
            c.drawRect(izq, y1, der, y2, pincel);
            if (franjaTexto != null) {
                texto(c, franjaTexto, der - dp(4), y1 + dp(13), 11, 0xFF3B7A22, Paint.Align.RIGHT, true);
            }
        }

        // Tres líneas guía con su valor.
        pincel.setStrokeWidth(dp(1));
        for (int i = 0; i <= 2; i++) {
            float valor = minimo + (maximo - minimo) * i / 2f;
            float yy = y(valor, arriba, abajo);
            pincel.setStyle(Paint.Style.STROKE);
            pincel.setColor(i == 0 ? 0xFFB9C7AE : 0xFFE3E8D6);
            c.drawLine(izq, yy, der, yy, pincel);
            texto(c, etiqueta.texto(valor), izq - dp(6), yy + dp(4), 11, 0xFF5F6B5C, Paint.Align.RIGHT, false);
        }

        int n = valores.length;
        float paso = (der - izq) / n;
        float anchoBarra = Math.min(paso * 0.62f, dp(26));
        boolean caben = paso >= dp(22);
        int indiceMax = 0, indiceMin = 0;
        for (int i = 1; i < n; i++) {
            if (valores[i] > valores[indiceMax]) indiceMax = i;
            if (valores[i] < valores[indiceMin]) indiceMin = i;
        }

        if (barras) {
            for (int i = 0; i < n; i++) {
                float cx = izq + paso * (i + 0.5f);
                float tope = y(valores[i], arriba, abajo);
                boolean fuerte = i == destacado || (destacado < 0 && i == indiceMax);
                pincel.setStyle(Paint.Style.FILL);
                pincel.setColor(fuerte ? color : (color & 0x00FFFFFF) | 0x8C000000);
                rect.set(cx - anchoBarra / 2, tope, cx + anchoBarra / 2, abajo);
                c.drawRoundRect(rect, dp(4), dp(4), pincel);
                if (caben || i == indiceMax || i == destacado) {
                    texto(c, String.valueOf(Math.round(valores[i])), cx, tope - dp(4), 10,
                            0xFF16371E, Paint.Align.CENTER, fuerte);
                }
            }
        } else {
            camino.reset();
            for (int i = 0; i < n; i++) {
                float cx = izq + paso * (i + 0.5f), cy = y(valores[i], arriba, abajo);
                if (i == 0) camino.moveTo(cx, cy); else camino.lineTo(cx, cy);
            }
            pincel.setStyle(Paint.Style.STROKE);
            pincel.setColor(color);
            pincel.setStrokeWidth(dp(3));
            pincel.setStrokeJoin(Paint.Join.ROUND);
            c.drawPath(camino, pincel);
            pincel.setStyle(Paint.Style.FILL);
            for (int i = 0; i < n; i++) {
                float cx = izq + paso * (i + 0.5f), cy = y(valores[i], arriba, abajo);
                boolean fuerte = i == destacado || i == indiceMax || i == indiceMin;
                pincel.setColor(fuerte ? color : 0xFFFFFFFF);
                c.drawCircle(cx, cy, dp(fuerte ? 5 : 3.5f), pincel);
                if (!fuerte) {
                    pincel.setStyle(Paint.Style.STROKE); pincel.setStrokeWidth(dp(2)); pincel.setColor(color);
                    c.drawCircle(cx, cy, dp(3.5f), pincel);
                    pincel.setStyle(Paint.Style.FILL);
                }
                if (i == indiceMax || i == indiceMin || i == destacado) {
                    float dy = i == indiceMin ? dp(16) : -dp(9);
                    texto(c, etiqueta.texto(valores[i]), cx, cy + dy, 11, 0xFF16371E, Paint.Align.CENTER, true);
                }
            }
        }

        // Meses o fechas debajo.
        for (int i = 0; i < n && i < etiquetasX.length; i++) {
            if (etiquetasX[i] == null || etiquetasX[i].isEmpty()) continue;
            float cx = izq + paso * (i + 0.5f);
            boolean fuerte = i == destacado;
            texto(c, etiquetasX[i], cx, abajo + dp(18), 11, fuerte ? 0xFF0B5D2A : 0xFF5F6B5C,
                    Paint.Align.CENTER, fuerte);
        }
    }

    private float y(float valor, float arriba, float abajo) {
        return abajo - (valor - minimo) / (maximo - minimo) * (abajo - arriba);
    }

    private void texto(Canvas c, String s, float x, float y, int sp, int col, Paint.Align alin, boolean negrita) {
        pincel.setStyle(Paint.Style.FILL);
        pincel.setColor(col);
        pincel.setTextSize(sp * getResources().getDisplayMetrics().scaledDensity);
        pincel.setTextAlign(alin);
        pincel.setTypeface(negrita ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        c.drawText(s, x, y, pincel);
    }

    private int dp(float valor) {
        return (int) (valor * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Iniciales de los meses para el eje de abajo. */
    public static final String[] MESES_CORTOS =
            {"E", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"};
}
