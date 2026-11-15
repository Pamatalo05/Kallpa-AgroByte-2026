package com.kallpa.app.view;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Módulos complementarios del piloto AgroByte: alertas de planificación,
 * calendario de referencia y estado offline. No ofrece meteorología en vivo.
 * Fuente: paquete_canton.json y reglas de semaforo.py -> MotorSemaforo.java.
 */
public class SupportActivity extends ActividadBase {
    public static final String EXTRA_MODE = "kallpa_support_mode";
    public static final String MODE_ALERTS = "alertas";
    public static final String MODE_CALENDAR = "calendario";
    public static final String MODE_PROFILE = "perfil";

    private static final int FOREST = 0xFF0B5D2A;
    private static final int DARK = 0xFF173E25;
    private static final int SECONDARY = 0xFF526E59;
    private static final String[] MONTHS = { "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre" };
    private static final String[] REQUIRED = { "paquete_canton.json",
            "evidencia_satelital_parcelas.json", "simulador_enso_historico.json",
            "validacion_ml_auditoria.json", "parcelas.geojson", "ecuador_limites.geojson" };

    private LinearLayout content;
    private TextView heading, parcelLabel;
    private ControladorAutenticacion auth;
    private Parcela activeParcel;
    private String canton;
    private JSONObject master;
    private JSONArray monthly;
    private String mode;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_support);
        content = findViewById(R.id.supportContent);
        heading = findViewById(R.id.txtSupportHeading);
        parcelLabel = findViewById(R.id.txtSupportParcel);
        findViewById(R.id.btnSupportBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSupportHome).setOnClickListener(v -> {
            startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });
        auth = new ControladorAutenticacion(this);
        if (!auth.sesionActiva() || auth.usuarioActual() == null) {
            startActivity(new Intent(this, LoginActivity.class)); finish(); return;
        }
        final String mail = auth.usuarioActual().getCorreo();
        List<Parcela> parcels = new RepositorioParcelasLocal(this).obtenerPorUsuario(mail);
        if (parcels.isEmpty()) {
            startActivity(new Intent(this, ParcelWelcomeActivity.class)); finish(); return;
        }
        String selectedId = getSharedPreferences("kallpa_seleccion", MODE_PRIVATE)
                .getString("parcela_activa_" + mail.toLowerCase(Locale.ROOT), "");
        activeParcel = parcels.get(0);
        for (Parcela p : parcels) if (p.getId().equals(selectedId)) {activeParcel = p; break;}
        canton = activeParcel.getCanton() == null ? "" :
                activeParcel.getCanton().toLowerCase(Locale.ROOT).trim();
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = MODE_ALERTS;
        parcelLabel.setText(activeParcel.getNombre() + " · " + zoneLabel());
        if (MODE_PROFILE.equals(mode)) {
            heading.setText("Perfil y datos locales");
            showProfile(parcels);
            return;
        }
        if (!"daule".equals(canton) && !"babahoyo".equals(canton)) {
            heading.setText("Datos no disponibles");
            card("Parcela fuera del piloto", "La versión científica de Kallpa solo contiene información de Daule y Babahoyo. Selecciona una parcela de uno de esos cantones.");
            return;
        }
        try {
            master = openLocalJson("paquete_canton.json");
            JSONObject cantons = master.getJSONObject("cantones");
            monthly = cantons.getJSONObject(canton).getJSONArray("serie_satelital_ml");
            if (monthly.length() != 12) throw new IllegalStateException("No hay 12 meses en el paquete local");
        } catch (Exception e) {
            heading.setText("Datos no disponibles");
            card("No se pudo abrir el paquete local", "Revisa app/src/main/assets/datos/paquete_canton.json.\nDetalle: " + e.getMessage());
            return;
        }
        if (MODE_CALENDAR.equals(mode)) {
            heading.setText("Calendario de mi zona");
            showCalendar();
        } else {
            heading.setText("Clima y alertas");
            showAlerts();
        }
    }

    private JSONObject openLocalJson(String filename) throws Exception {
        try (InputStream input = getAssets().open("datos/" + filename);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192]; int count;
            while ((count = input.read(bytes)) != -1) buffer.write(bytes, 0, count);
            return new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    private JSONObject monthData(int month) {
        for (int i = 0; i < monthly.length(); i++) {
            JSONObject entry = monthly.optJSONObject(i);
            if (entry != null && entry.optInt("mes") == month) return entry;
        }
        return null;
    }

    private MotorSemaforo.Resultado evaluate(JSONObject data) {
        if (data == null) return null;
        JSONObject predictions = data.optJSONObject("humedad_raiz_predicha");
        if (predictions == null || !predictions.has("neutro")) return null;
        double moisture = predictions.optDouble("neutro", Double.NaN);
        return Double.isFinite(moisture) ? MotorSemaforo.evaluar(moisture, activeParcel.isTieneRiego()) : null;
    }

    private void showAlerts() {
        intro("¿Qué debo vigilar antes de sembrar?",
                "Consulta un mes para revisar riesgos hídricos de planificación. "
                + "No son alertas meteorológicas de hoy ni pronósticos de lluvia.");
        card("Tu parcela", activeParcel.getNombre() + " · " + f(activeParcel.getHectareas(), 2)
                + " ha · Riego: " + (activeParcel.isTieneRiego() ? "Sí" : "No"));
        TextView label = text(content, "Selecciona el mes de tu próxima siembra", 15, DARK, true);
        Spinner picker = monthPicker(content);
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        content.addView(result, new LinearLayout.LayoutParams(-1, -2));
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View selected, int position, long id) {
                result.removeAllViews();
                JSONObject datum = monthData(position + 1);
                MotorSemaforo.Resultado risk = evaluate(datum);
                if (datum == null || risk == null) {
                    cardIn(result, "Datos insuficientes", "No hay predicción Ridge disponible para este mes."); return;
                }
                double moisture = datum.optJSONObject("humedad_raiz_predicha").optDouble("neutro");
                String colorMeaning = explanation(moisture, risk);
                TextView h = text(result, "Semáforo Día Cero: " + risk.color,
                        21, colorFor(risk.color), true);
                cardIn(result, "¿Qué significa para tu siembra?", colorMeaning);
                cardIn(result, "Indicadores de referencia · " + MONTHS[position],
                        "Humedad estimada en la raíz: " + f(moisture,3) + " m³/m³\n"
                        + "Lluvia mensual de referencia: " + f(datum.optDouble("lluvia_mm"),1) + " mm\n"
                        + "Temperatura de referencia: " + f(datum.optDouble("temp_c"),1) + " °C\n"
                        + "Fuente: paquete_canton.json · NASA POWER y modelo Ridge");
                cardIn(result, "¿Qué puedes hacer?", advice(moisture, risk));
                button(result, "Ver rotaciones y costos para este mes", true,
                        () -> openScience(ScienceActivity.MODE_FUTURE));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        picker.setSelection((Calendar.getInstance().get(Calendar.MONTH) + 1) % 12);
        text(content, "Información histórica y predictiva a escala regional. Verifica las condiciones de tu terreno "
                + "y consulta asesoría agrícola antes de invertir. Los datos son para Daule y Babahoyo, no por lote.",
                12, SECONDARY, false);
        button(content, "Abrir escenario histórico de El Niño / La Niña", false,
                () -> openScience(ScienceActivity.MODE_PAST));
    }

    private String explanation(double moisture, MotorSemaforo.Resultado result) {
        String note;
        if (moisture < 0.48) note = "La humedad prevista es muy baja.";
        else if (moisture < 0.55) note = "La humedad prevista es algo baja.";
        else if (moisture <= 0.78) note = "La humedad prevista se encuentra en el rango favorable del modelo.";
        else if (moisture <= 0.84) note = "La humedad prevista es elevada.";
        else note = "La humedad prevista supera el rango alto del modelo.";
        return note + "\nResultado: " + result.estado + ".\n"
                + "Este color resume condiciones hídricas de la próxima siembra, no el tiempo de hoy.";
    }
    private String advice(double moisture, MotorSemaforo.Resultado risk) {
        if (moisture > 0.78) return "Revisa el drenaje y posibles encharcamientos de tu terreno antes de sembrar. "
                + "Confirma las condiciones con observación local.";
        if (moisture < 0.55) return activeParcel.isTieneRiego()
                ? "Verifica que el sistema de riego realmente esté disponible y que su costo sea viable."
                : "Evalúa la disponibilidad de agua. Considera cultivos con mejor respuesta en secano y consulta asesoría técnica.";
        return "Comprueba la humedad real, las fechas y el estado del suelo antes de invertir en semillas e insumos.";
    }
    private int colorFor(String risk) {
        if ("Rojo".equalsIgnoreCase(risk)) return 0xFFB24336;
        if ("Amarillo".equalsIgnoreCase(risk)) return 0xFF916900;
        return FOREST;
    }

    private void showCalendar() {
        intro("¿En qué meses conviene revisar mi siembra?",
                "Calendario de referencia construido con la serie mensual local de AgroByte. "
                + "Muestra riesgos para planificar, no pronósticos ni labores confirmadas.");
        card("Tu parcela", activeParcel.getNombre() + " · " + zoneLabel() + " · Riego: "
                + (activeParcel.isTieneRiego() ? "Sí" : "No"));
        text(content,"Elige un mes para ver los detalles",15,DARK,true);
        Spinner picker = monthPicker(content);
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        content.addView(details,new LinearLayout.LayoutParams(-1,-2));
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> adapter,View v,int position,long id){
                details.removeAllViews();
                JSONObject item=monthData(position+1);
                MotorSemaforo.Resultado risk=evaluate(item);
                if(item==null||risk==null){cardIn(details,"Sin datos","No hay información mensual disponible.");return;}
                double humidity = item.optJSONObject("humedad_raiz_predicha").optDouble("neutro");
                cardIn(details,MONTHS[position]+" · "+risk.color,
                        "Lluvia de referencia: "+f(item.optDouble("lluvia_mm"),1)+" mm\n"
                        + "Humedad estimada en raíz: "+f(humidity,3)+" m³/m³\n"
                        + "Temperatura de referencia: "+f(item.optDouble("temp_c"),1)+" °C\n"
                        + "Vegetación regional NDVI: "+f(item.optDouble("ndvi_modis"),3));
                cardIn(details,"Recomendación de planificación", advice(humidity,risk));
                button(details,"Evaluar cultivos y ahorro de urea",true,()->openScience(ScienceActivity.MODE_FUTURE));
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        picker.setSelection(Calendar.getInstance().get(Calendar.MONTH));
        text(content,"Vista de los 12 meses",18,DARK,true);
        for(int i=1;i<=12;i++){
            JSONObject item=monthData(i);MotorSemaforo.Resultado risk=evaluate(item);
            if(item==null||risk==null)continue;
            String date=MONTHS[i-1];
            double humidity=item.optJSONObject("humedad_raiz_predicha").optDouble("neutro");
            LinearLayout box=cardContainer(content);
            text(box,date+"   ·   "+risk.color,15,colorFor(risk.color),true);
            text(box,"Lluvia ref.: "+f(item.optDouble("lluvia_mm"),1)+" mm  ·  Humedad raíz: "+f(humidity,3),13,SECONDARY,false);
            final int index=i-1;
            box.setOnClickListener(v->picker.setSelection(index));
        }
        text(content,"Los valores proceden de la serie mensual del paquete local (periodo de análisis 2005–2025). "
                + "No son un calendario de labores agrícolas confirmado ni un pronóstico meteorológico. "
                + "MODIS y SMAP describen la región, no tu parcela exacta.",12,SECONDARY,false);
    }

    private void showProfile(List<Parcela> parcels) {
        intro("Tu información en Kallpa", "Desarrollado por el equipo AgroByte. "
                + "Los datos agrícolas del piloto están disponibles en el teléfono sin conexión.");
        card("Mi cuenta", "Nombre: " + auth.usuarioActual().getNombre() + "\nCorreo: "
                + auth.usuarioActual().getCorreo());
        card("Mi parcela activa", activeParcel.getNombre() + " · " + zoneLabel() + "\n"
                + f(activeParcel.getHectareas(),2) + " ha · " + activeParcel.getCultivoActual()
                + "\nTienes " + parcels.size() + (parcels.size()==1?" parcela registrada.":" parcelas registradas."));
        button(content, "Cambiar o añadir parcela", true, () -> startActivity(new Intent(this, MyParcelsActivity.class)));
        String state = isOnline() ? "El teléfono tiene conexión a internet." : "El teléfono está sin conexión a internet.";
        card("Estado de conexión", state + "\nLos JSON del piloto se leen localmente, incluso en modo avión.");
        int available=0;
        StringBuilder files = new StringBuilder();
        for(String name:REQUIRED){
            boolean exists;
            try(InputStream in=getAssets().open("datos/"+name)){exists=true;}
            catch(Exception ignored){exists=false;}
            if(exists)available++;
            files.append(exists?"✓ ":"FALTA: ").append(name).append("\n");
        }
        card("Datos agrícolas disponibles: "+available+" de "+REQUIRED.length,files.toString().trim());
        try{
            JSONObject p=openLocalJson("paquete_canton.json");
            JSONObject meta=p.optJSONObject("meta");
            if(meta!=null)card("Información del paquete científico",
                    "Periodo analizado: "+meta.optString("periodo_analizado","No indicado")
                    +"\nArchivo actualizado (UTC): "+meta.optString("actualizado_utc","Sin fecha")
                    +"\nDatos: NASA / NOAA / INIAP / MAG SIPA. Equipo AgroByte.");
        }catch(Exception ignored){card("Paquete científico","No se pudo leer fecha o procedencia.");}
        text(content, "Este prototipo guarda cuentas y parcelas en el dispositivo. "
                + "No anuncia sincronización con servidores porque todavía no existe un servicio de sincronización integrado.",
                12,SECONDARY,false);
        button(content,"Probar datos sin conexión",false,()->new AlertDialog.Builder(this)
                .setTitle("Prueba sin conexión")
                .setMessage("1. Activa modo avión en tu TECNO.\n2. Abre Presente, Futuro y Pasado.\n3. "
                        + "Regresa a Perfil para revisar si están los seis archivos.\n\nNo borres los datos de Kallpa: "
                        + "podrías perder las parcelas locales.")
                .setPositiveButton("Entendido",null).show());
    }

    private boolean isOnline(){
        ConnectivityManager manager=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        if(manager==null)return false;
        Network network=manager.getActiveNetwork();
        NetworkCapabilities caps=network==null?null:manager.getNetworkCapabilities(network);
        return caps!=null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
    private Spinner monthPicker(LinearLayout parent){
        Spinner spinner=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,MONTHS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));
        parent.addView(spinner,lp);
        return spinner;
    }
    private void openScience(String module){
        Intent intent=new Intent(this,ScienceActivity.class);
        intent.putExtra(ScienceActivity.EXTRA_MODE,module);
        startActivity(intent);
    }
    private void intro(String title,String detail){
        text(content,title,19,DARK,true);
        text(content,detail,13,SECONDARY,false);
    }
    private String zoneLabel(){
        return "daule".equals(canton)?"Daule, Guayas":("babahoyo".equals(canton)?"Babahoyo, Los Ríos":"Zona sin identificar");
    }
    private LinearLayout cardContainer(LinearLayout parent){
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16),dp(14),dp(16),dp(14));
        card.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(11);lp.bottomMargin=dp(4);
        parent.addView(card,lp);
        return card;
    }
    private void card(String title,String desc){cardIn(content,title,desc);}
    private void cardIn(LinearLayout parent,String title,String desc){
        LinearLayout card=cardContainer(parent);
        text(card,title,15,DARK,true);
        text(card,desc,14,SECONDARY,false);
    }
    private TextView text(LinearLayout parent,String content,int sp,int color,boolean bold){
        TextView view=new TextView(this);
        view.setText(content);view.setTextSize(sp);view.setTextColor(color);
        if(bold)view.setTypeface(null,Typeface.BOLD);
        view.setLineSpacing(dp(3),1.0f);
        view.setPadding(0,dp(5),0,dp(5));
        parent.addView(view,new LinearLayout.LayoutParams(-1,-2));
        return view;
    }
    private void button(LinearLayout parent,String label,boolean primary,Runnable action){
        Button button=new Button(this);
        button.setText(label);button.setAllCaps(false);
        button.setTextColor(primary?Color.WHITE:FOREST);
        button.setTextSize(14);
        button.setBackgroundResource(primary?R.drawable.bg_button_primary:R.drawable.bg_button_outline);
        button.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(53));
        lp.topMargin=dp(12);parent.addView(button,lp);
    }
    private String f(double value,int decimals){
        return String.format(Locale.US,"%."+decimals+"f",value);
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
}
