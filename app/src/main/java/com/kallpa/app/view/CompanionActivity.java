package com.kallpa.app.view;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.kallpa.app.R;
import com.kallpa.app.controller.ControladorAutenticacion;
import com.kallpa.app.model.MotorSemaforo;
import com.kallpa.app.model.Parcela;
import com.kallpa.app.model.RepositorioParcelasLocal;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * AgroByte / Kallpa. Módulos complementarios:
 * - Bitácora: datos DECLARADOS por el agricultor y guardados localmente.
 * - Muyu: asistente de preguntas frecuentes que explica archivos científicos precargados.
 * No promete sincronización remota ni verificar automáticamente un lote con satélite.
 */
public class CompanionActivity extends ActividadBase {
    public static final String EXTRA_MODE = "kallpa_companion_mode";
    public static final String MODE_LOG = "bitacora";
    public static final String MODE_MUYU = "muyu";

    private static final int FOREST = 0xFF0B5D2A;
    private static final int DARK = 0xFF173E25;
    private static final int MUTED = 0xFF526E59;
    private static final String[] MONTHS = {"Enero","Febrero","Marzo","Abril","Mayo","Junio",
            "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"};
    private static final String[] ACTIVITY_TYPES = {"Siembra","Cosecha","Cambio de cultivo",
            "Riego","Observación del suelo","Otra actividad"};
    private static final String[] SOIL_STATES = {"No observado","Suelo cubierto",
            "Parcialmente cubierto","Suelo descubierto"};

    private LinearLayout body;
    private TextView heading, subtitle;
    private Parcela active;
    private String owner;
    private String canton;
    private String mode;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_companion);
        body = findViewById(R.id.companionBody);
        heading = findViewById(R.id.txtCompanionHeading);
        subtitle = findViewById(R.id.txtCompanionParcel);
        findViewById(R.id.btnCompanionBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCompanionHome).setOnClickListener(v -> {
            Intent i = new Intent(this, HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
        });
        ControladorAutenticacion auth = new ControladorAutenticacion(this);
        if (!auth.sesionActiva() || auth.usuarioActual() == null) {
            startActivity(new Intent(this, LoginActivity.class)); finish(); return;
        }
        owner = auth.usuarioActual().getCorreo();
        List<Parcela> parcels = new RepositorioParcelasLocal(this).obtenerPorUsuario(owner);
        if (parcels.isEmpty()) {
            startActivity(new Intent(this, ParcelWelcomeActivity.class)); finish(); return;
        }
        String selectedId = getSharedPreferences("kallpa_seleccion", MODE_PRIVATE)
                .getString("parcela_activa_" + owner.toLowerCase(Locale.ROOT), "");
        active = parcels.get(0);
        for (Parcela p : parcels) if (p.getId().equals(selectedId)) {active = p;break;}
        canton = active.getCanton() == null ? "" : active.getCanton().trim().toLowerCase(Locale.ROOT);
        subtitle.setText(active.getNombre() + " · " + ("daule".equals(canton) ? "Daule" :
                "babahoyo".equals(canton) ? "Babahoyo" : "Fuera del piloto"));
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (MODE_MUYU.equals(mode)) {heading.setText("Conversa con Muyu"); showMuyu();}
        else {heading.setText("Mi bitácora"); showDiary();}
    }

    // ---------------- Bitácora: entradas de campo y evidencias SEPARADAS ----------------
    private void showDiary() {
        body.removeAllViews();
        intro("Lo que ocurrió en mi terreno", "Guarda tus observaciones y actividades en este teléfono. "
                + "Los datos del agricultor se mantienen separados de los datos de los satélites.");
        section("Parcela", active.getNombre() + " · " + fmt(active.getHectareas(),2) + " ha · " + active.getCultivoActual());
        showSatelliteReference();
        button(body,"+ Registrar actividad u observación",true,this::showAddDialog);
        text(body,"Mis registros de campo",19,DARK,true);
        JSONArray all = readDiary();
        if (all.length() == 0) {
            section("Todavía no hay registros", "Puedes anotar cuándo sembraste o cosechaste, "
                    + "qué observaste en el suelo y cualquier comentario útil para tu parcela.");
        } else {
            for (int i = all.length() - 1; i >= 0; i--) {
                JSONObject e = all.optJSONObject(i);
                if (e == null) continue;
                String kind = e.optString("tipo", "Actividad");
                String when = e.optString("fecha", "Fecha sin indicar");
                String soil = e.optString("estado_suelo", "No observado");
                String note = e.optString("comentario", "");
                String source = e.optString("fuente", "agricultor");
                section(kind + " · " + when, soil + "\n" + (note.isEmpty()?"Sin comentario adicional":note)
                        + "\nFuente: " + ("agricultor".equals(source) ? "observación del agricultor" : "declaración del agricultor"));
            }
        }
        text(body,"Los registros quedan guardados solo en este dispositivo. No existe aún "
                + "sincronización con servidores; no desinstales la aplicación ni borres sus datos.",12,MUTED,false);
    }

    private void showSatelliteReference() {
        if (!"daule".equals(canton) && !"babahoyo".equals(canton)) {
            section("Evidencia del satélite", "Solo disponible para Daule y Babahoyo.");
            return;
        }
        try {
            JSONObject root = readData("evidencia_satelital_parcelas.json");
            JSONObject place = root.getJSONObject("parcelas_satelitales").getJSONObject(canton);
            JSONObject s = place.getJSONObject("resumen_actual");

            // Mensaje cercano al agricultor: sin cifras técnicas en la pantalla principal.
            String cobertura = s.optString("estado_cobertura", "no informada");
            String explicacion = cobertura.toLowerCase(Locale.ROOT).contains("descubierto")
                    ? "La observación de tu zona sugiere suelo con poca vegetación."
                    : "El satélite muestra información sobre la vegetación de tu zona.";
            section("¿Qué observa el satélite?", explicacion
                    + "\nEs una referencia regional, no una medición exacta de tu parcela.");

            // Fechas, fuentes, unidades y resolución siguen disponibles bajo demanda.
            button(body, "Ver datos y fuentes", false, () -> {
                String detalles = "Vegetación (NDVI): " + fmt(s.optDouble("ndvi_ultimo", 0), 3)
                        + "\nFecha: " + s.optString("fecha_ndvi", "sin fecha")
                        + "\n\nHumedad superficial (SMAP): "
                        + fmt(s.optDouble("smap_humedad_ultima", 0), 3) + " m³/m³"
                        + "\nFecha: " + s.optString("fecha_smap", "sin fecha")
                        + "\n\nCobertura regional: " + cobertura
                        + "\n\nFuentes: NASA MODIS NDVI (250 m) y NASA SMAP "
                        + "(humedad regional). Estas observaciones no verifican "
                        + "el estado exacto de una finca.";
                new AlertDialog.Builder(this)
                        .setTitle("Datos del satélite")
                        .setMessage(detalles)
                        .setPositiveButton("Entendido", null)
                        .show();
            });

            // Una sola acción abre las tres respuestas; no sobrecargamos la Bitácora.
            button(body, "¿Coincide con lo que ves?", true, () ->
                    new AlertDialog.Builder(this)
                            .setTitle("¿Cómo está tu terreno?")
                            .setItems(new String[]{"Sí, coincide", "No, es diferente", "No estoy seguro"},
                                    (dialog, which) -> {
                                        String estado = which == 0 ? "Coincide"
                                                : (which == 1 ? "No coincide" : "Sin confirmar");
                                        verifySatellite(estado, s);
                                    })
                            .setNegativeButton("Cancelar", null)
                            .show());
        } catch (Exception e) {
            section("Evidencia no disponible", "Puedes seguir usando tu bitácora sin internet.");
        }
    }

    private void verifySatellite(String status, JSONObject snapshot) {
        // No se altera el archivo NASA: guardamos únicamente la opinión de campo.
        final EditText note = input("¿Qué viste en tu parcela? (opcional)",true);
        new AlertDialog.Builder(this).setTitle("Comparar con el satélite")
                .setMessage("Estado: " + status + ". La observación original seguirá intacta.")
                .setView(note)
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Guardar",(d,w)->{
                    String msg = "Validación del agricultor: " + status + ". "
                            + note.getText().toString().trim()
                            + "\nReferencia MODIS: " + snapshot.optString("fecha_ndvi", "sin fecha");
                    saveEntry("Verificación de cobertura satelital",today(),"No observado",msg);
                }).show();
    }

    private void showAddDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(18),dp(10),dp(18),dp(8));
        form.setOrientation(LinearLayout.VERTICAL);
        text(form,"Tipo de actividad",13,DARK,true);
        Spinner activity = spinner(form,ACTIVITY_TYPES);
        text(form,"Fecha",13,DARK,true);
        final Button date = new Button(this);
        date.setAllCaps(false);date.setText(today());
        form.addView(date,new LinearLayout.LayoutParams(-1,dp(48)));
        date.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this,(picker,year,month,day)->date.setText(String.format(Locale.US,
                    "%04d-%02d-%02d",year,month+1,day)),c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
        });
        text(form,"Estado del suelo observado",13,DARK,true);
        Spinner soil = spinner(form,SOIL_STATES);
        text(form,"Comentario (opcional)",13,DARK,true);
        EditText note = input("Ej.: sembré maíz; el suelo estaba húmedo...",false);
        form.addView(note,new LinearLayout.LayoutParams(-1,dp(100)));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Nuevo registro de campo")
                .setView(form)
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Guardar",(d,w) -> saveEntry(
                        String.valueOf(activity.getSelectedItem()),String.valueOf(date.getText()),
                        String.valueOf(soil.getSelectedItem()),note.getText().toString().trim()))
                .create();
        dialog.show();
    }

    private String diaryKey() {
        return "registro_" + owner.trim().toLowerCase(Locale.ROOT) + "_" + active.getId();
    }
    private JSONArray readDiary() {
        String raw = getSharedPreferences("kallpa_bitacora",MODE_PRIVATE)
                .getString(diaryKey(),"[]");
        try {return new JSONArray(raw);} catch(Exception e) {return new JSONArray();}
    }
    private void saveEntry(String type,String date,String soil,String note) {
        try {
            JSONObject entry = new JSONObject();
            entry.put("id", UUID.randomUUID().toString());
            entry.put("fecha",date);
            entry.put("tipo",type);
            entry.put("estado_suelo",soil);
            entry.put("comentario",note);
            entry.put("fuente","agricultor");
            entry.put("creado_ms",System.currentTimeMillis());
            JSONArray list = readDiary();list.put(entry);
            boolean ok = getSharedPreferences("kallpa_bitacora",MODE_PRIVATE)
                    .edit().putString(diaryKey(),list.toString()).commit();
            if (!ok) {mensaje("No se pudo guardar el registro.");return;}
            showDiary();
            mensaje("Registro guardado en este dispositivo.");
        } catch(Exception e){mensaje("No se pudo guardar el registro: " + e.getMessage());}
    }

    // ---------------- Muyu: respuestas de evidencia offline ----------------
    private void showMuyu() {
        body.removeAllViews();
        LinearLayout title = new LinearLayout(this);
        title.setGravity(Gravity.CENTER_VERTICAL);title.setOrientation(LinearLayout.HORIZONTAL);
        ImageView mascot = new ImageView(this);
        mascot.setImageResource(R.drawable.ic_kallpa); // La mascota de la guía de marca, no un símbolo de flor.
        title.addView(mascot,new LinearLayout.LayoutParams(dp(85),dp(85)));
        TextView welcome = new TextView(this);
        welcome.setText("¡Hola! Soy Muyu 🌱\nTe ayudo a entender tu parcela.");
        welcome.setTextSize(16); welcome.setTextColor(DARK);welcome.setTypeface(null,Typeface.BOLD);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0,-2,1);
        wl.leftMargin=dp(8);title.addView(welcome,wl);
        body.addView(title,new LinearLayout.LayoutParams(-1,-2));
        intro("Pregúntame sobre tu terreno", "Mis respuestas utilizan datos locales del piloto AgroByte "
                + "y explicaciones precargadas. Funcionan sin internet.");
        section("Tu parcela",active.getNombre()+" · "+active.getHectareas()+" ha · Riego: "
                +(active.isTieneRiego()?"Sí":"No"));
        text(body,"Consultas rápidas",18,DARK,true);
        button(body,"¿Qué dicen los satélites de mi zona?",false,()->answer(satelliteAnswer()));
        button(body,"¿Qué significa el semáforo?",false,()->askMonth());
        button(body,"¿Qué leguminosa podría convenirme?",false,()->answer(rotationAnswer()));
        button(body,"¿Cuánto podría ahorrar en urea?",false,()->answer(savingAnswer()));
        button(body,"¿Qué significa El Niño y La Niña?",false,()->answer(ensoAnswer()));
        button(body,"¿De dónde sale esta información?",false,()->answer(sourcesAnswer()));
        text(body,"Escribe una pregunta",15,DARK,true);
        EditText question = input("Ej.: ¿qué es NDVI?",false);
        body.addView(question,new LinearLayout.LayoutParams(-1,dp(66)));
        button(body,"Consultar a Muyu",true,()->{
            String q=question.getText().toString().trim().toLowerCase(Locale.ROOT);
            if(q.isEmpty()){mensaje("Escribe una pregunta.");return;}
            if(q.contains("ndvi")||q.contains("satélite")||q.contains("satelite")||q.contains("smap")||q.contains("humedad superficial"))answer(satelliteAnswer());
            else if(q.contains("semáforo")||q.contains("semaforo")||q.contains("riesgo")||q.contains("mes")||q.contains("siembra"))askMonth();
            else if(q.contains("urea")||q.contains("ahorr")||q.contains("dinero"))answer(savingAnswer());
            else if(q.contains("legum")||q.contains("cultivo")||q.contains("rotaci")||q.contains("maní")||q.contains("soya"))answer(rotationAnswer());
            else if(q.contains("niño")||q.contains("nina")||q.contains("niña")||q.contains("enso"))answer(ensoAnswer());
            else if(q.contains("fuente")||q.contains("nasa")||q.contains("iniap"))answer(sourcesAnswer());
            else answer("Por ahora solo puedo responder las preguntas frecuentes de arriba usando los datos precargados. "
                    + "No tengo una conversación de IA en línea habilitada. Puedes consultar con un técnico agrícola si necesitas una evaluación específica.");
        });
        text(body,"Muyu orienta; no sustituye un análisis de suelo ni el criterio de un agrónomo. "
                + "Los indicadores son regionales, no mediciones directas de tu finca.",12,MUTED,false);
    }

    private void askMonth() {
        String[] months=MONTHS;
        new AlertDialog.Builder(this).setTitle("¿Para qué mes planeas sembrar?")
                .setItems(months,(d,which)->answer(trafficLightAnswer(which+1)))
                .setNegativeButton("Cancelar",null).show();
    }
    private String trafficLightAnswer(int month) {
        try {
            JSONObject local=cantonInfo();
            JSONArray series=local.getJSONArray("serie_satelital_ml");
            for(int i=0;i<series.length();i++){
                JSONObject datum=series.getJSONObject(i);
                if(datum.optInt("mes")==month){
                    double moisture=datum.getJSONObject("humedad_raiz_predicha").getDouble("neutro");
                    MotorSemaforo.Resultado risk=MotorSemaforo.evaluar(moisture,active.isTieneRiego());
                    return MONTHS[month-1]+": semáforo "+risk.color+".\nHumedad estimada en la raíz: "
                            +fmt(moisture,3)+" m³/m³.\n"+friendlyMeaning(risk.color,moisture)
                            +"\n\nEste resultado viene del modelo Ridge (escenario neutro), "
                            +"no es un pronóstico meteorológico en vivo.";
                }
            }
            return "Falta la información para ese mes.";
        } catch(Exception e){return "No pude leer la predicción local. Revisa assets/datos/paquete_canton.json.";}
    }
    private String friendlyMeaning(String risk,double humidity) {
        if("Verde".equals(risk))return humidity<0.55
                ?"Tu sistema de riego declarado reduce el riesgo por baja humedad. Comprueba que funcione antes de sembrar."
                :"La humedad estimada está en el rango favorable del modelo. Comprueba las condiciones reales.";
        if("Amarillo".equals(risk))return humidity>0.78
                ?"Podría haber exceso de humedad. Revisa drenajes y encharcamientos."
                :"Podría faltar agua. Confirma la disponibilidad de riego antes de invertir.";
        return "El modelo detecta condiciones hídricas desfavorables. Consulta a un técnico agrícola.";
    }
    private String satelliteAnswer() {
        try {
            JSONObject d=readData("evidencia_satelital_parcelas.json")
                    .getJSONObject("parcelas_satelitales").getJSONObject(canton)
                    .getJSONObject("resumen_actual");
            return "La vegetación observada en tu zona tiene un índice NDVI de "
                    +fmt(d.getDouble("ndvi_ultimo"),3)+" ("+d.optString("fecha_ndvi")+"). "
                    +"El índice ayuda a describir la cobertura vegetal, no identifica con certeza tu cultivo.\n\n"
                    +"La humedad superficial regional SMAP es "+fmt(d.getDouble("smap_humedad_ultima"),3)
                    +" m³/m³ ("+d.optString("fecha_smap")+").\n\n"
                    +"Fuente: NASA MODIS (250 m) y SMAP (regional). Los datos no miden tu parcela exacta.";
        }catch(Exception e){return "No hay datos satelitales locales para esta parcela. La cobertura actual solo está precargada para Daule y Babahoyo.";}
    }
    private JSONObject bestCrop() throws Exception {
        String irrigation=active.isTieneRiego()?"con_riego":"sin_riego";
        JSONArray crops=readData("simulador_enso_historico.json").getJSONObject("cantones")
                .getJSONObject(canton).getJSONObject("escenarios").getJSONObject("neutro")
                .getJSONObject("recomendacion_por_parcela").getJSONArray(irrigation);
        if(crops.length()==0)throw new IllegalStateException("Sin cultivos");
        return crops.getJSONObject(0);
    }
    private String rotationAnswer() {
        try {
            JSONObject c=bestCrop();
            return "Para "+(active.isTieneRiego()?"una parcela con riego":"una parcela sin riego")
                    +", el ranking histórico del escenario neutro sitúa primero a "
                    +c.getString("cultivo")+".\n\nEvaluación: "+c.optString("estado")
                    +".\n"+c.optString("nota_agronomica")
                    +"\n\nTambién se comparan soya INIAP-308, fréjol caupí INIAP-463, maní INIAP-381 y mucuna. "
                    +"Esta comparación es regional y necesita validación agronómica local.";
        }catch(Exception e){return "No pude consultar el ranking local para este cantón. Revisa simulador_enso_historico.json.";}
    }
    private String savingAnswer() {
        try {
            JSONObject c=bestCrop();
            double kg=c.getDouble("ahorro_urea_kg_ha") * active.getHectareas();
            double usd=kg*MotorSemaforo.PRECIO_UREA_USD_KG;
            return "Para la opción "+c.getString("cultivo")+" y tus "
                    +fmt(active.getHectareas(),2)+" hectáreas, la estimación es evitar "
                    +fmt(kg,1)+" kg de urea, equivalentes a aproximadamente USD "
                    +fmt(usd,2)+".\n\nCálculo: "
                    +fmt(c.getDouble("ahorro_urea_kg_ha"),1)+" kg/ha × "
                    +fmt(active.getHectareas(),2)+" ha × USD 0,76/kg.\n"
                    +"Monocultivo de referencia: ahorro por rotación USD 0.\n"
                    +"Fuente: INIAP / FAO y precio de referencia MAG SIPA. No es un ahorro garantizado.";
        }catch(Exception e){return "No pude consultar el ahorro estimado. Revisa el JSON local de ENSO.";}
    }
    private String ensoAnswer() {
        try {
            JSONObject d=readData("simulador_enso_historico.json");
            String info=d.optJSONObject("metadata") == null?"":d.getJSONObject("metadata").optString("alcance_nota","");
            return "El Niño y La Niña son cambios del sistema climático que pueden modificar las lluvias y la humedad. "
                    +"Kallpa compara eventos históricos (2005–2025) para comprender cómo respondió la zona. "
                    +"No predice con certeza el próximo evento.\n\n"+info
                    +"\n\nPuedes entrar a ‘Pasado · Historial ENSO’ para elegir un escenario.";
        }catch(Exception e){return "Kallpa utiliza registros históricos NASA y NOAA para analizar ENSO; no ofrece pronósticos en tiempo real.";}
    }
    private String sourcesAnswer() {
        return "• NASA MODIS: índice de vegetación NDVI, resolución 250 m.\n"
                +"• NASA SMAP: humedad superficial regional.\n"
                +"• NASA POWER: registros climáticos históricos.\n"
                +"• NOAA: escenarios históricos El Niño y La Niña.\n"
                +"• INIAP y FAO: fichas y criterios agronómicos de leguminosas.\n"
                +"• MAG SIPA: precio referencial de urea (USD 38 por 50 kg).\n\n"
                +"Fuentes y resultados procesados por el equipo AgroByte. "
                +"No son mediciones exactas de la parcela ni una garantía de ahorro o rendimiento.";
    }
    private JSONObject cantonInfo() throws Exception {
        if(!"daule".equals(canton)&&!"babahoyo".equals(canton))
            throw new IllegalStateException("Parcela fuera del piloto");
        return readData("paquete_canton.json").getJSONObject("cantones").getJSONObject(canton);
    }
    private JSONObject readData(String name) throws Exception {
        try(InputStream input = getAssets().open("datos/"+name);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int n;
            while((n=input.read(buffer))!=-1)out.write(buffer,0,n);
            return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
        }
    }
    private void answer(String result) {
        new AlertDialog.Builder(this).setTitle("Muyu te explica 🌱")
                .setMessage(result).setPositiveButton("Entendido",null).show();
    }

    // ---------------- Helpers de interfaz ----------------
    private void intro(String title,String subtitle){text(body,title,20,DARK,true);text(body,subtitle,14,MUTED,false);}
    private void section(String title,String info){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15),dp(14),dp(15),dp(14));card.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(12);lp.bottomMargin=dp(3);body.addView(card,lp);
        text(card,title,16,DARK,true);text(card,info,14,MUTED,false);
    }
    private TextView text(LinearLayout parent,String s,int size,int color,boolean bold){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);
        v.setPadding(0,dp(4),0,dp(4));v.setLineSpacing(dp(3),1f);
        if(bold)v.setTypeface(null,Typeface.BOLD);
        parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;
    }
    private Spinner spinner(LinearLayout parent,String[] values){
        Spinner s=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);parent.addView(s,new LinearLayout.LayoutParams(-1,dp(45)));return s;
    }
    private EditText input(String hint,boolean singleLine){
        EditText v=new EditText(this);v.setHint(hint);v.setTextSize(14);
        v.setSingleLine(singleLine);v.setFilters(new InputFilter[]{new InputFilter.LengthFilter(600)});
        return v;
    }
    private void button(LinearLayout parent,String label,boolean primary,Runnable callback){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);
        b.setTextColor(primary?Color.WHITE:FOREST);
        b.setBackgroundResource(primary?R.drawable.bg_button_primary:R.drawable.bg_button_outline);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(53));lp.topMargin=dp(9);
        parent.addView(b,lp);b.setOnClickListener(v->callback.run());
    }
    private String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new java.util.Date());}
    private String fmt(double x,int digits){return String.format(Locale.US,"%."+digits+"f",x);}
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
}
