package com.kallpa.app.view;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.kallpa.app.R;

/** Una cabecera accesible despliega todo su contenido: texto, gráficas y acciones. */
public final class SeccionDesplegable extends LinearLayout {
    private final LinearLayout contenido;
    private final LinearLayout cabecera;
    private final ImageView flecha;
    private final String clave, titulo;
    private boolean abierta;

    private SeccionDesplegable(Context c, String clave, String titulo, String resumen, boolean defecto) {
        super(c);
        this.clave = c.getClass().getSimpleName() + "." + clave;
        this.titulo = titulo;
        setOrientation(VERTICAL);
        GradientDrawable fondo = new GradientDrawable();
        fondo.setColor(0xFFFFFDF7); fondo.setCornerRadius(dp(20)); fondo.setStroke(dp(1), 0xFFE0E7D6);
        setBackground(fondo);
        cabecera = new LinearLayout(c); cabecera.setOrientation(HORIZONTAL);
        cabecera.setGravity(Gravity.CENTER_VERTICAL); cabecera.setMinimumHeight(dp(60));
        cabecera.setPadding(dp(16),dp(12),dp(14),dp(12));
        cabecera.setClickable(true); cabecera.setFocusable(true);
        android.util.TypedValue valor = new android.util.TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, valor, true);
        cabecera.setForeground(c.getDrawable(valor.resourceId));
        LinearLayout textos = new LinearLayout(c); textos.setOrientation(VERTICAL);
        TextView nombre = new TextView(c); TextoDato.aplicar(nombre,titulo,true);
        nombre.setTextColor(0xFF07391E); nombre.setTextSize(17);
        textos.addView(nombre,new LayoutParams(-1,-2));
        if(resumen != null && !resumen.isEmpty()) {
            TextView ayuda = new TextView(c); ayuda.setText(resumen); ayuda.setTextSize(12);
            ayuda.setTextColor(0xFF62715D); ayuda.setPadding(0,dp(4),0,0);
            textos.addView(ayuda,new LayoutParams(-1,-2));
        }
        textos.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        cabecera.addView(textos,new LayoutParams(0,-2,1));
        flecha = new ImageView(c); flecha.setImageResource(R.drawable.ic_seccion_chevron);
        flecha.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable circulo = new GradientDrawable();circulo.setColor(0xFFEDF4DE);circulo.setShape(GradientDrawable.OVAL);
        flecha.setBackground(circulo);flecha.setPadding(dp(7),dp(7),dp(7),dp(7));
        LayoutParams icono = new LayoutParams(dp(34),dp(34)); icono.leftMargin=dp(12);
        cabecera.addView(flecha,icono);addView(cabecera,new LayoutParams(-1,-2));
        contenido = new LinearLayout(c);contenido.setOrientation(VERTICAL);
        contenido.setPadding(dp(16),0,dp(16),dp(16));addView(contenido,new LayoutParams(-1,-2));
        abierta=c.getSharedPreferences("kallpa_secciones",Context.MODE_PRIVATE).getBoolean(this.clave,defecto);
        actualizar(false);
        cabecera.setOnClickListener(v-> {abierta=!abierta;actualizar(true);});
    }

    private void actualizar(boolean guardar) {
        contenido.setVisibility(abierta ? VISIBLE : GONE); flecha.setRotation(abierta ? 180f : 0f);
        cabecera.setContentDescription(titulo);
        ViewCompat.setStateDescription(cabecera, abierta ? "Desplegado" : "Contraído");
        ViewCompat.removeAccessibilityAction(cabecera, AccessibilityNodeInfoCompat.ACTION_EXPAND);
        ViewCompat.removeAccessibilityAction(cabecera, AccessibilityNodeInfoCompat.ACTION_COLLAPSE);
        ViewCompat.replaceAccessibilityAction(cabecera, abierta
                ? AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_COLLAPSE
                : AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_EXPAND,
                abierta ? "Contraer sección" : "Desplegar sección", (vista,args)-> {
                    abierta=!abierta;actualizar(true);return true;
                });
        if(guardar) getContext().getSharedPreferences("kallpa_secciones",Context.MODE_PRIVATE)
                .edit().putBoolean(clave,abierta).apply();
    }

    public static LinearLayout crear(LinearLayout padre,String clave,String titulo,String resumen,boolean abierta) {
        SeccionDesplegable seccion=new SeccionDesplegable(padre.getContext(),clave,titulo,resumen,abierta);
        LayoutParams lp=new LayoutParams(-1,-2);lp.topMargin=seccion.dp(12);lp.bottomMargin=seccion.dp(4);
        padre.addView(seccion,lp);return seccion.contenido;
    }

    /** Aplica el mismo patrón a una tarjeta XML, conservando todos sus listeners y vistas. */
    public static void envolver(LinearLayout tarjeta,String clave,String titulo,boolean abierta) {
        SeccionDesplegable seccion=new SeccionDesplegable(tarjeta.getContext(),clave,titulo,null,abierta);
        while(tarjeta.getChildCount()>0) {
            View hijo=tarjeta.getChildAt(0);tarjeta.removeViewAt(0);seccion.contenido.addView(hijo);
        }
        tarjeta.setPadding(0,0,0,0);tarjeta.setBackground(null);tarjeta.setElevation(0);
        tarjeta.addView(seccion,new LayoutParams(-1,-2));
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
}
