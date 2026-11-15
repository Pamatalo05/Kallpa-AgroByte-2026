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
                "babahoyo".equals(canton) ? "Babahoyo" : "Zona sin datos todavía"));
        mode = getIntent().getStringExtra(EXTRA_MODE);
        activarBarraInferior(MODE_MUYU.equals(mode) ? SECCION_MUYU : -1, MODE_MUYU.equals(mode));
        if (MODE_MUYU.equals(mode)) {heading.setText("Pregúntale a Muyu"); showMuyu();}
        else {heading.setText("Mi bitácora"); showDiary();}
    }

    // ---------------- Bitácora: entradas de campo y evidencias SEPARADAS ----------------
    private void showDiary() {
        body.removeAllViews();
        intro("Lo que pasa en tu terreno", "Anota aquí lo que haces y lo que ves en tu parcela. "
                + "Te sirve para recordar y para comparar con lo que dicen los satélites.");
        button(body,"+ Anotar algo nuevo",true,this::showAddDialog);
        text(body,"Mis anotaciones",19,DARK,true);
        JSONArray all = readDiary();
        if (all.length() == 0) {
            section("Todavía no has anotado nada", "Puedes anotar cuándo sembraste o cosechaste, "
                    + "cómo viste el suelo o cualquier cosa que quieras recordar.");
        } else {
            for (int i = all.length() - 1; i >= 0; i--) {
                JSONObject e = all.optJSONObject(i);
                if (e == null) continue;
                String kind = e.optString("tipo", "Actividad");
                String when = e.optString("fecha", "Fecha sin indicar");
                String soil = e.optString("estado_suelo", "No observado");
                String note = e.optString("comentario", "");
                String source = e.optString("fuente", "agricultor");
                section(kind + " · " + when, ("No observado".equals(soil) ? "" : "Suelo: " + soil.toLowerCase(Locale.ROOT) + "\n")
                        + (note.isEmpty() ? "Sin comentario" : note));
            }
        }
        text(body,"Satélite y tu terreno",19,DARK,true);
        showSatelliteReference();
        text(body,"Tus anotaciones se guardan solo en este teléfono. "
                + "Si borras la app o sus datos, se pierden.",14,MUTED,false);
    }

    private void showSatelliteReference() {
        if (!"daule".equals(canton) && !"babahoyo".equals(canton)) {
            section("Lo que ve el satélite", "Por ahora solo tenemos datos de Daule y Babahoyo.");
            return;
        }
        try {
            JSONObject root = readData("evidencia_satelital_parcelas.json");
            JSONObject place = root.getJSONObject("parcelas_satelitales").getJSONObject(canton);
            JSONObject s = place.getJSONObject("resumen_actual");

            // Mensaje cercano al agricultor: sin cifras técnicas en la pantalla principal.
            String cobertura = s.optString("estado_cobertura", "no informada");
            String explicacion = cobertura.toLowerCase(Locale.ROOT).contains("descubierto")
                    ? "El satélite ve poca vegetación en tu zona: mucho suelo descubierto."
                    : "El satélite ve vegetación verde en tu zona.";
            section("Lo que ve el satélite", explicacion
                    + "\nMira toda la zona, no solo tu parcela.");

            // Fechas, fuentes, unidades y resolución siguen disponibles bajo demanda.
            button(body, "Ver las cifras del satélite", false, () -> explicar("Lo que ve el satélite",
                    "🌿 Verdor de las plantas: " + Formato.verdor(s.optDouble("ndvi_ultimo", 0))
                    + "\nÚltima foto: " + s.optString("fecha_ndvi", "sin fecha")
                    + "\n\n💧 Humedad en la superficie: " + Formato.porcentaje(s.optDouble("smap_humedad_ultima", 0))
                    + "\nÚltima medición: " + s.optString("fecha_smap", "sin fecha")
                    + "\n\nDatos de satélites de la NASA. Miran toda la zona, no solo tu parcela."));

            // Una sola acción abre las tres respuestas; no sobrecargamos la Bitácora.
            button(body, "¿Coincide con lo que ves?", true, () ->
                    new AlertDialog.Builder(this)
                            .setTitle("¿Tu terreno se ve así?")
                            .setItems(new String[]{"Sí, se ve así", "No, se ve distinto", "No estoy seguro"},
                                    (dialog, which) -> {
                                        String estado = which == 0 ? "Coincide"
                                                : (which == 1 ? "No coincide" : "Sin confirmar");
                                        verifySatellite(estado, s);
                                    })
                            .setNegativeButton("Cancelar", null)
                            .show());
        } catch (Exception e) {
            section("No pudimos leer los datos del satélite", "Igual puedes seguir anotando en tu bitácora.");
        }
    }

    private void verifySatellite(String status, JSONObject snapshot) {
        // No se altera el archivo NASA: guardamos únicamente la opinión de campo.
        final EditText note = input("¿Qué viste en tu parcela? (si quieres)",true);
        new AlertDialog.Builder(this).setTitle("Cuéntanos qué viste")
                .setMessage("Respuesta: " + status.toLowerCase(Locale.ROOT) + ". Lo guardamos en tu bitácora.")
                .setView(note)
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Guardar",(d,w)->{
                    String msg = "Comparé con el satélite: " + status.toLowerCase(Locale.ROOT) + ". "
                            + note.getText().toString().trim()
                            + "\n(Foto del satélite del " + snapshot.optString("fecha_ndvi", "sin fecha") + ")";
                    saveEntry("Comparación con el satélite",today(),"No observado",msg);
                }).show();
    }

    private void showAddDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(18),dp(10),dp(18),dp(8));
        form.setOrientation(LinearLayout.VERTICAL);
        text(form,"¿Qué hiciste o viste?",15,DARK,true);
        Spinner activity = spinner(form,ACTIVITY_TYPES);
        text(form,"¿Cuándo?",15,DARK,true);
        final Button date = new Button(this);
        date.setAllCaps(false);date.setText(today());date.setTextSize(16);
        date.setTextColor(DARK);date.setBackgroundResource(R.drawable.bg_input);date.setStateListAnimator(null);
        form.addView(date,new LinearLayout.LayoutParams(-1,dp(48)));
        date.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this,(picker,year,month,day)->date.setText(String.format(Locale.US,
                    "%04d-%02d-%02d",year,month+1,day)),c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
        });
        text(form,"¿Cómo estaba el suelo?",15,DARK,true);
        Spinner soil = spinner(form,SOIL_STATES);
        text(form,"Algo más que quieras anotar (si quieres)",15,DARK,true);
        EditText note = input("Ej.: sembré maíz; el suelo estaba húmedo",false);
        form.addView(note,new LinearLayout.LayoutParams(-1,dp(100)));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Anotar en mi bitácora")
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
            if (!ok) {avisoError("No pudimos guardar tu anotación. Inténtalo otra vez.");return;}
            showDiary();
            avisoExito("¡Listo! Tu anotación quedó guardada.");
        } catch(Exception e){avisoError("No pudimos guardar tu anotación. Inténtalo otra vez.");}
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
        welcome.setText("¡Hola! Soy Muyu 🌱\nTe explico tu parcela con palabras sencillas.");
        welcome.setTextSize(17); welcome.setTextColor(DARK);welcome.setTypeface(null,Typeface.BOLD);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0,-2,1);
        wl.leftMargin=dp(8);title.addView(welcome,wl);
        body.addView(title,new LinearLayout.LayoutParams(-1,-2));
        intro("¿En qué te ayudo?", "Toca una pregunta o escribe la tuya. "
                + "Te respondo aunque no tengas internet.");
        section("Tu parcela",active.getNombre()+" · "+Formato.hectareas(active.getHectareas())+" · "
                +(active.isTieneRiego()?"Con riego":"Sin riego"));
        text(body,"Preguntas frecuentes",18,DARK,true);
        button(body,"🌱 ¿Qué siembro después?",false,()->answer(rotationAnswer()));
        button(body,"📅 ¿Es buen mes para sembrar?",false,()->askMonth());
        button(body,"💵 ¿Cuánto ahorro en urea?",false,()->answer(savingAnswer()));
        button(body,"🌧️ ¿Qué es El Niño y La Niña?",false,()->answer(ensoAnswer()));
        button(body,"🛰️ ¿Qué ven los satélites?",false,()->answer(satelliteAnswer()));
        button(body,"📚 ¿De dónde sacas los datos?",false,()->answer(sourcesAnswer()));
        text(body,"O escribe tu pregunta",18,DARK,true);
        EditText question = input("Ej.: ¿qué siembro después del arroz?",false);
        question.setBackgroundResource(R.drawable.bg_input);
        question.setPadding(dp(16),dp(12),dp(16),dp(12));
        question.setMinHeight(dp(56));
        body.addView(question,new LinearLayout.LayoutParams(-1,-2));
        button(body,"Preguntar a Muyu",true,()->{
            String q=question.getText().toString().trim().toLowerCase(Locale.ROOT);
            if(q.isEmpty()){mensaje("Escribe tu pregunta primero.");return;}
            if(q.contains("ndvi")||q.contains("satélite")||q.contains("satelite")||q.contains("smap")||q.contains("humedad superficial"))answer(satelliteAnswer());
            else if(q.contains("semáforo")||q.contains("semaforo")||q.contains("riesgo")||q.contains("mes")||q.contains("siembra"))askMonth();
            else if(q.contains("urea")||q.contains("ahorr")||q.contains("dinero"))answer(savingAnswer());
            else if(q.contains("legum")||q.contains("cultivo")||q.contains("rotaci")||q.contains("maní")||q.contains("soya"))answer(rotationAnswer());
            else if(q.contains("niño")||q.contains("nina")||q.contains("niña")||q.contains("enso"))answer(ensoAnswer());
            else if(q.contains("fuente")||q.contains("nasa")||q.contains("iniap"))answer(sourcesAnswer());
            else answer("¡Uy! Esa pregunta todavía no la sé responder. 🌱\n\n"
                    + "Puedo ayudarte con qué sembrar, el mejor mes, el ahorro en urea, "
                    + "El Niño y La Niña, y lo que ven los satélites. Prueba con una de las preguntas de arriba.\n\n"
                    + "Para algo muy puntual de tu terreno, lo mejor es conversar con un técnico agrícola.");
        });
        text(body,"Muyu te orienta, pero no reemplaza a un técnico ni un análisis de suelo. "
                + "Los datos son de tu zona, no de tu terreno exacto.",14,MUTED,false);
    }

    private void askMonth() {
        String[] months=MONTHS;
        new AlertDialog.Builder(this).setTitle("¿En qué mes quieres sembrar?")
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
                    return MONTHS[month-1]+": "+Formato.semaforo(risk.color)+"\n\n"
                            +"En ese mes el suelo de tu zona suele estar "+Formato.humedadEnPalabras(moisture)+".\n"
                            +friendlyMeaning(risk.color,moisture)
                            +"\n\nEs lo que suele pasar en ese mes en años normales, no el pronóstico del tiempo.";
                }
            }
            return "Todavía no tengo datos de ese mes.";
        } catch(Exception e){return "Por ahora no tengo datos del clima para tu zona. Solo tengo Daule y Babahoyo.";}
    }
    private String friendlyMeaning(String risk,double humidity) {
        if("Verde".equals(risk))return humidity<0.55
                ?"Como tienes riego, puedes sembrar tranquilo. Solo revisa que tu riego funcione bien."
                :"Es un buen mes. Igual mira cómo está tu terreno antes de sembrar.";
        if("Amarillo".equals(risk))return humidity>0.78
                ?"Podría sobrar agua: revisa que no se encharque y que el agua corra bien."
                :"Podría faltar agua: asegúrate de poder regar antes de invertir.";
        return "Es un mes arriesgado para sembrar. Si puedes, espera otro mes o pide consejo a un técnico.";
    }
    private String satelliteAnswer() {
        try {
            JSONObject d=readData("evidencia_satelital_parcelas.json")
                    .getJSONObject("parcelas_satelitales").getJSONObject(canton)
                    .getJSONObject("resumen_actual");
            return "🌿 Verdor de las plantas en tu zona: "+Formato.verdor(d.getDouble("ndvi_ultimo"))
                    +" (foto del "+d.optString("fecha_ndvi")+"). Mientras más alto, más plantas verdes hay.\n\n"
                    +"💧 Humedad en la superficie del suelo: "+Formato.porcentaje(d.getDouble("smap_humedad_ultima"))
                    +" (medida el "+d.optString("fecha_smap")+").\n\n"
                    +"Son satélites de la NASA que miran toda la zona, no solo tu parcela. "
                    +"Compáralo con lo que ves en tu terreno.";
        }catch(Exception e){return "Todavía no tengo datos del satélite para tu parcela. Por ahora solo tengo Daule y Babahoyo.";}
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
            return "¡Buena pregunta! 🌱 Para una parcela "+(active.isTieneRiego()?"con riego":"sin riego")
                    +" como la tuya, lo que mejor resultó en años normales fue: "+c.getString("cultivo")+".\n\n"
                    +Formato.evaluacionCultivo(c.optString("estado"))+".\n"+c.optString("nota_agronomica")
                    +"\n\nTambién puedes mirar soya, fréjol caupí, maní y mucuna en \"Siembra\". "
                    +"Todas devuelven nitrógeno a la tierra.";
        }catch(Exception e){return "Todavía no tengo cultivos recomendados para tu cantón. Por ahora solo tengo Daule y Babahoyo.";}
    }
    private String savingAnswer() {
        try {
            JSONObject c=bestCrop();
            double kgHa=c.getDouble("ahorro_urea_kg_ha");
            double kg=kgHa * active.getHectareas();
            double usd=kg*MotorSemaforo.PRECIO_UREA_USD_KG;
            return "Si siembras "+c.getString("cultivo")+", ese cultivo deja nitrógeno en la tierra. "
                    +"Así, en tus "+Formato.hectareas(active.getHectareas())+" podrías dejar de poner unos "
                    +Formato.kilos(kg)+" de urea.\n\n"
                    +"💵 Eso es "+Formato.unos(usd)+" (con el saco de 50 kg a unos $38).\n\n"
                    +"Es una cuenta aproximada: depende de tu suelo y de cuánta urea usas hoy. "
                    +"Si siembras otra vez lo mismo, no ahorrarías urea.";
        }catch(Exception e){return "Todavía no puedo calcular tu ahorro: no tengo los datos de tu cantón.";}
    }
    private String ensoAnswer() {
        return "El Niño y La Niña son cambios en el mar que cambian las lluvias en la Costa.\n\n"
                +"🌧️ El Niño: suele traer más lluvia y riesgo de inundaciones.\n"
                +"☀️ La Niña: suele traer menos lluvia.\n\n"
                +"En \"El Niño y La Niña\" puedes ver cómo le fue a tu zona en esos años (de 2005 a 2025). "
                +"Sirve para prepararte, pero nadie sabe con certeza cuándo vendrá el próximo.";
    }
    private String sourcesAnswer() {
        return "Uso datos de fuentes confiables:\n\n"
                +"🛰️ NASA: satélites que miden lluvia, humedad del suelo y verdor de las plantas.\n"
                +"🌎 NOAA: los años de El Niño y La Niña.\n"
                +"🌱 INIAP y FAO: información de los cultivos.\n"
                +"💵 MAG: precio de la urea (unos $38 el saco de 50 kg).\n\n"
                +"El equipo AgroByte juntó todo esto. Son datos de tu zona, no de tu terreno exacto, "
                +"y las cifras son aproximadas.";
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
        explicar("Muyu te explica 🌱", result);
    }

    // ---------------- Helpers de interfaz ----------------
    private void intro(String title,String subtitle){text(body,title,20,DARK,true);text(body,subtitle,15,MUTED,false);}
    private void section(String title,String info){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15),dp(14),dp(15),dp(14));card.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(12);lp.bottomMargin=dp(3);body.addView(card,lp);
        text(card,title,17,DARK,true);text(card,info,15,MUTED,false);
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
        s.setAdapter(adapter);s.setBackgroundResource(R.drawable.bg_selector);s.setPadding(dp(8),0,dp(48),0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));lp.bottomMargin=dp(8);
        parent.addView(s,lp);return s;
    }
    private EditText input(String hint,boolean singleLine){
        EditText v=new EditText(this);v.setHint(hint);v.setTextSize(16);
        v.setSingleLine(singleLine);v.setFilters(new InputFilter[]{new InputFilter.LengthFilter(600)});
        return v;
    }
    private void button(LinearLayout parent,String label,boolean primary,Runnable callback){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(16);
        b.setTypeface(null,Typeface.BOLD);b.setStateListAnimator(null);
        b.setTextColor(primary?Color.WHITE:FOREST);
        b.setBackgroundResource(primary?R.drawable.bg_button_primary:R.drawable.bg_button_outline);
        b.setMinHeight(dp(54));b.setPadding(dp(16),dp(10),dp(16),dp(10));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);
        parent.addView(b,lp);b.setOnClickListener(v->callback.run());
    }
    private String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new java.util.Date());}
    private String fmt(double x,int digits){return String.format(Locale.US,"%."+digits+"f",x);}
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
}
