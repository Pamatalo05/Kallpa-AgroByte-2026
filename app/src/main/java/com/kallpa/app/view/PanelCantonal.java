package com.kallpa.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

/** Compara el mismo indicador en dos cantones con fechas y escala compartidas. */
public final class PanelCantonal extends LinearLayout {
    private static final int VERDE=0xFF286B36, AZUL=0xFF276C9B;
    private static final Locale ES=Locale.forLanguageTag("es-EC");
    private final JSONObject datos;
    private final LinearLayout resultados;

    public PanelCantonal(Context c, JSONObject datos) {
        super(c);this.datos=datos;setOrientation(VERTICAL);
        TextView ayuda=texto("Elige un indicador para comparar ambos cantones en la misma escala. Las fechas corresponden a las observaciones disponibles.",14,0xFF62715D);
        addView(ayuda,new LayoutParams(-1,-2));
        TextView tipo=texto("Indicador",14,0xFF173E25);TextoDato.aplicar(tipo,"Indicador",true);
        tipo.setPadding(0,dp(12),0,dp(6));addView(tipo);
        Spinner selector=new Spinner(c);
        selector.setAdapter(new ArrayAdapter<>(c,android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Vegetación · NDVI", "Humedad superficial · SMAP"}));
        selector.setMinimumHeight(dp(52));addView(selector,new LayoutParams(-1,dp(56)));
        resultados=new LinearLayout(c);resultados.setOrientation(VERTICAL);addView(resultados,new LayoutParams(-1,-2));
        selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> p,View v,int i,long id){
                getContext().getSharedPreferences("kallpa_ui",Context.MODE_PRIVATE).edit().putInt("panel_indicador",i).apply();
                mostrar(i==1);
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        selector.setSelection(Math.max(0,Math.min(1,c.getSharedPreferences("kallpa_ui",Context.MODE_PRIVATE).getInt("panel_indicador",0))));
    }

    private void mostrar(boolean humedad) {
        resultados.removeAllViews();
        ArrayList<Punto> daule=serie("daule",humedad),babahoyo=serie("babahoyo",humedad);
        LinearLayout resumen=new LinearLayout(getContext());
        boolean ancho=getResources().getConfiguration().screenWidthDp>=380;
        resumen.setOrientation(ancho?HORIZONTAL:VERTICAL);
        agregarResumen(resumen,"Daule",daule,humedad,VERDE,ancho);
        agregarResumen(resumen,"Babahoyo",babahoyo,humedad,AZUL,ancho);
        resultados.addView(resumen,new LayoutParams(-1,-2));
        if(daule.size()+babahoyo.size()<2) {
            resultados.addView(texto("Las observaciones aparecerán aquí cuando haya más fechas disponibles.",14,0xFF62715D));return;
        }
        TextView leyenda=texto("Daule: línea verde continua\nBabahoyo: línea azul discontinua",12,0xFF42563D);
        TextoDato.aplicar(leyenda,leyenda.getText(),false);leyenda.setPadding(0,dp(12),0,dp(4));resultados.addView(leyenda);
        Grafica grafica=new Grafica(daule,babahoyo,humedad);
        LayoutParams gl=new LayoutParams(-1,dp(245));gl.topMargin=dp(8);resultados.addView(grafica,gl);
        TextView fuente=texto(humedad?"Fuente: NASA SMAP · Escala: aproximadamente 9 km."
                :"Fuente: NASA MODIS · Escala: 250 m.",12,0xFF62715D);
        TextoDato.aplicar(fuente,fuente.getText(),false);fuente.setPadding(0,dp(8),0,0);resultados.addView(fuente);
        resultados.addView(texto("Estimaciones a nivel cantonal para acompañar la observación de tu terreno.",12,0xFF62715D));
    }

    private void agregarResumen(LinearLayout padre,String canton,ArrayList<Punto> serie,boolean humedad,int color,boolean fila) {
        LinearLayout tarjeta=new LinearLayout(getContext());tarjeta.setOrientation(VERTICAL);tarjeta.setPadding(dp(10),dp(12),dp(10),dp(12));
        android.graphics.drawable.GradientDrawable fondo=new android.graphics.drawable.GradientDrawable();
        fondo.setColor(0xFFF0F5E8);fondo.setCornerRadius(dp(12));tarjeta.setBackground(fondo);
        TextView nombre=texto(canton,16,color);TextoDato.aplicar(nombre,canton,true);tarjeta.addView(nombre);
        if(serie.isEmpty()) tarjeta.addView(texto("Observación pendiente",13,0xFF62715D));
        else {
            Punto ultimo=serie.get(serie.size()-1);
            TextView valor=texto("",14,0xFF173E25);
            TextoDato.aplicar(valor,(humedad?"Humedad: ":"NDVI: ")+valor(ultimo.valor,humedad),false);tarjeta.addView(valor);
            TextView fecha=texto("",12,0xFF62715D);TextoDato.aplicar(fecha,"Fecha: "+fecha(ultimo.dia),false);tarjeta.addView(fecha);
        }
        LayoutParams lp=fila?new LayoutParams(0,-2,1):new LayoutParams(-1,-2);
        lp.topMargin=dp(10);if(padre.getChildCount()>0){if(fila)lp.leftMargin=dp(8);}
        padre.addView(tarjeta,lp);
    }

    private ArrayList<Punto> serie(String canton,boolean humedad) {
        ArrayList<Punto> salida=new ArrayList<>();
        JSONObject cantones=datos==null?null:datos.optJSONObject("parcelas_satelitales");
        JSONObject zona=cantones==null?null:cantones.optJSONObject(canton);
        JSONArray arr=zona==null?null:zona.optJSONArray(humedad?"serie_humedad_smap":"serie_ndvi_modis");
        if(arr!=null)for(int i=0;i<arr.length();i++) {
            JSONObject fila=arr.optJSONObject(i);if(fila==null)continue;
            double valor=fila.optDouble(humedad?"humedad_m3_m3":"ndvi",Double.NaN);
            if(!Double.isFinite(valor))continue;
            try {salida.add(new Punto(LocalDate.parse(fila.getString("fecha")).toEpochDay(),(float)(valor*(humedad?100:1))));}
            catch(Exception ignored) { }
        }
        salida.sort(Comparator.comparingLong(p->p.dia));return salida;
    }
    private static final class Punto {
        final long dia;final float valor;
        Punto(long dia,float valor){this.dia=dia;this.valor=valor;}
    }
    private class Grafica extends View {
        private final ArrayList<Punto> a,b;
        private final boolean humedad;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private long inicio=Long.MAX_VALUE,fin=Long.MIN_VALUE;
        private float min=0,max=.1f;
        Grafica(ArrayList<Punto> a,ArrayList<Punto> b,boolean humedad) {
            super(PanelCantonal.this.getContext());this.a=a;this.b=b;this.humedad=humedad;
            for(ArrayList<Punto> serie:java.util.Arrays.asList(a,b))for(Punto punto:serie) {
                inicio=Math.min(inicio,punto.dia);fin=Math.max(fin,punto.dia);
                min=Math.min(min,punto.valor);max=Math.max(max,punto.valor);
            }
            max+=(max-min)*.1f;
            setContentDescription("Comparación cantonal de "+(humedad?"humedad superficial":"vegetación NDVI")
                    +". Periodo: "+fecha(inicio)+" a "+fecha(fin)+". "+descripcion("Daule",a)+" "+descripcion("Babahoyo",b));
        }
        private String descripcion(String canton,ArrayList<Punto> serie) {
            if(serie.isEmpty())return canton+": observación pendiente.";
            Punto x=serie.get(serie.size()-1);return canton+": "+valor(x.valor,humedad)+", "+fecha(x.dia)+".";
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);float l=dp(44),r=getWidth()-dp(14),t=dp(20),bot=getHeight()-dp(32);
            if(r<=l||bot<=t)return;
            p.setTextSize(dp(10));p.setStrokeWidth(dp(1));p.setStyle(Paint.Style.FILL);
            for(int i=0;i<=3;i++) {
                float y=t+(bot-t)*i/3f;p.setColor(0xFFDEE6D3);c.drawLine(l,y,r,y,p);
                p.setColor(0xFF62715D);p.setTextAlign(Paint.Align.RIGHT);
                c.drawText(valor(max-(max-min)*i/3f,humedad),l-dp(6),y+dp(4),p);
            }
            dibujar(c,a,VERDE,false,l,r,t,bot);dibujar(c,b,AZUL,true,l,r,t,bot);
            p.setTextSize(dp(10));p.setColor(0xFF62715D);p.setTextAlign(Paint.Align.LEFT);
            c.drawText(fecha(inicio),l,bot+dp(22),p);p.setTextAlign(Paint.Align.RIGHT);c.drawText(fecha(fin),r,bot+dp(22),p);
        }
        private void dibujar(Canvas c,ArrayList<Punto> serie,int color,boolean guiones,float l,float r,float t,float bot) {
            if(serie.isEmpty())return;
            Path camino=new Path();int i=0;
            for(Punto punto:serie) {
                float x=l+(r-l)*(punto.dia-inicio)/Math.max(1f,fin-inicio),y=bot-(punto.valor-min)/(max-min)*(bot-t);
                if(i++==0)camino.moveTo(x,y);else camino.lineTo(x,y);
            }
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(color);
            p.setPathEffect(guiones?new DashPathEffect(new float[]{dp(6),dp(4)},0):null);c.drawPath(camino,p);p.setPathEffect(null);
            p.setStyle(Paint.Style.FILL);
            for(Punto punto:serie) {
                float x=l+(r-l)*(punto.dia-inicio)/Math.max(1f,fin-inicio),y=bot-(punto.valor-min)/(max-min)*(bot-t);
                c.drawCircle(x,y,dp(2.5f),p);
            }
        }
    }
    private TextView texto(String valor,int size,int color){TextView t=new TextView(getContext());t.setText(valor);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(2),1);return t;}
    private String valor(float n,boolean humedad){return String.format(ES,humedad?"%.0f %%":"%.2f",n);}
    private String fecha(long dia){return LocalDate.ofEpochDay(dia).format(DateTimeFormatter.ofPattern("d MMM yy",ES));}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
}
