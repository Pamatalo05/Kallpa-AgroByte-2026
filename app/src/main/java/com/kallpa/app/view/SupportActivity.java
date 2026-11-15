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
        // Perfil no es una sección de la barra; Clima y Calendario van juntos.
        activarBarraInferior(MODE_PROFILE.equals(mode) ? -1 : SECCION_CLIMA, MODE_ALERTS.equals(mode));
        parcelLabel.setText(activeParcel.getNombre() + " · " + zoneLabel());
        if (MODE_PROFILE.equals(mode)) {
            heading.setText("Mi perfil");
            showProfile(parcels);
            return;
        }
        if (!"daule".equals(canton) && !"babahoyo".equals(canton)) {
            heading.setText("Aún no tenemos tu zona");
            card("Por ahora solo Daule y Babahoyo", "Kallpa todavía no tiene datos de tu cantón. "
                    + "Si tienes una parcela en Daule o Babahoyo, elígela en \"Mi parcela\".");
            return;
        }
        try {
            master = openLocalJson("paquete_canton.json");
            JSONObject cantons = master.getJSONObject("cantones");
            monthly = cantons.getJSONObject(canton).getJSONArray("serie_satelital_ml");
            if (monthly.length() != 12) throw new IllegalStateException("No hay 12 meses en el paquete local");
        } catch (Exception e) {
            heading.setText("No pudimos abrir los datos");
            card("Algo salió mal", "Kallpa no encontró la información de tu zona en este teléfono. "
                    + "Prueba cerrando y abriendo la app; si sigue igual, vuelve a instalarla.");
            return;
        }
        if (MODE_CALENDAR.equals(mode)) {
            heading.setText("Calendario de tu zona");
            showCalendar();
        } else {
            heading.setText("Clima para sembrar");
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
        intro("¿Cómo estará el suelo para sembrar?",
                "Elige el mes en que piensas sembrar. Te decimos cómo suele estar el suelo "
                + "de tu zona en ese mes. No es el pronóstico del tiempo de hoy.");
        card("Tu parcela", activeParcel.getNombre() + " · " + Formato.hectareas(activeParcel.getHectareas())
                + " · " + (activeParcel.isTieneRiego() ? "Con riego" : "Sin riego"));
        text(content, "¿En qué mes piensas sembrar?", 18, DARK, true);
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
                    cardIn(result, "Aún no hay datos", "Todavía no sabemos cómo suele estar el suelo en "
                            + MONTHS[position].toLowerCase(Locale.ROOT) + "."); return;
                }
                double moisture = datum.optJSONObject("humedad_raiz_predicha").optDouble("neutro");
                text(result, Formato.semaforo(risk.color), 21, colorFor(risk.color), true);
                cardIn(result, "¿Qué quiere decir?", explanation(moisture, risk));
                cardIn(result, "¿Qué puedes hacer?", advice(moisture, risk));
                button(result, "Ver qué sembrar y cuánto ahorrar", true,
                        () -> openScience(ScienceActivity.MODE_FUTURE));
                link(result, "Ver las cifras de " + MONTHS[position].toLowerCase(Locale.ROOT),
                        () -> explicar("Cifras de " + MONTHS[position].toLowerCase(Locale.ROOT),
                                "Lluvia normal del mes: unos " + Formato.numero(datum.optDouble("lluvia_mm")) + " mm\n"
                                + "Humedad del suelo en las raíces: cerca de " + Formato.porcentaje(moisture) + "\n"
                                + "Calor máximo: unos " + Formato.numero(datum.optDouble("temp_c")) + " °C\n\n"
                                + "Son promedios de tu zona en los últimos 20 años (datos de la NASA). "
                                + "Tu terreno puede estar un poco distinto."));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        picker.setSelection((Calendar.getInstance().get(Calendar.MONTH) + 1) % 12);
        text(content, "Los datos son de toda la zona de " + zoneLabel().split(",")[0]
                + ", no de tu lote. Antes de invertir, mira tu terreno y, si puedes, pide consejo a un técnico.",
                14, SECONDARY, false);
        link(content, "Ver cómo fue en años de El Niño y La Niña",
                () -> openScience(ScienceActivity.MODE_PAST));
    }

    private String explanation(double moisture, MotorSemaforo.Resultado result) {
        String suelo = "En ese mes el suelo de tu zona suele estar " + Formato.humedadEnPalabras(moisture) + ".";
        if ("Verde".equalsIgnoreCase(result.color)) {
            return suelo + (moisture < 0.55 && activeParcel.isTieneRiego()
                    ? " Como tienes riego, puedes sembrar con tranquilidad."
                    : " Es un buen momento para sembrar.");
        }
        if ("Amarillo".equalsIgnoreCase(result.color)) {
            return suelo + " Puedes sembrar, pero hay algo de riesgo por "
                    + (moisture > 0.78 ? "exceso de agua." : "falta de agua.");
        }
        return suelo + " Sembrar ese mes es arriesgado: podrías perder la siembra.";
    }
    private String advice(double moisture, MotorSemaforo.Resultado risk) {
        if (moisture > 0.78) return "Revisa que el agua corra bien y que no se encharque tu terreno antes de sembrar.";
        if (moisture < 0.55) return activeParcel.isTieneRiego()
                ? "Revisa que tu riego funcione bien y que te alcance el agua."
                : "Podría faltar agua. Piensa en cómo regar o elige un cultivo que aguante la sequía.";
        return "Igual revisa tu terreno y las fechas antes de comprar semillas e insumos.";
    }
    private int colorFor(String risk) {
        if ("Rojo".equalsIgnoreCase(risk)) return 0xFFB24336;
        if ("Amarillo".equalsIgnoreCase(risk)) return 0xFF916900;
        return FOREST;
    }

    private void showCalendar() {
        intro("¿Qué meses son buenos para sembrar?",
                "Así suele estar el suelo de tu zona cada mes, según los últimos 20 años. "
                + "Toca un mes para ver qué hacer.");
        card("Tu parcela", activeParcel.getNombre() + " · " + zoneLabel() + " · "
                + (activeParcel.isTieneRiego() ? "Con riego" : "Sin riego"));
        text(content, "Tu año de un vistazo", 19, DARK, true);
        leyenda(content);
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        View[] cuadros = new View[12];
        LinearLayout fila = null;
        for (int i = 0; i < 12; i++) {
            if (i % 3 == 0) {
                fila = new LinearLayout(this);
                fila.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
                fl.topMargin = dp(8);
                content.addView(fila, fl);
            }
            final int mes = i;
            JSONObject item = monthData(i + 1);
            MotorSemaforo.Resultado risk = evaluate(item);
            LinearLayout cuadro = new LinearLayout(this);
            cuadro.setOrientation(LinearLayout.VERTICAL);
            cuadro.setGravity(Gravity.CENTER);
            cuadro.setMinimumHeight(dp(84));
            cuadro.setPadding(dp(6), dp(8), dp(6), dp(8));
            cuadro.setClickable(true);
            cuadro.setFocusable(true);
            TextView nombre = new TextView(this);
            nombre.setText(MONTHS[i].substring(0, 3));
            nombre.setTextSize(18); nombre.setTypeface(null, Typeface.BOLD); nombre.setTextColor(DARK);
            nombre.setGravity(Gravity.CENTER);
            cuadro.addView(nombre);
            TextView estado = new TextView(this);
            estado.setTextSize(13); estado.setGravity(Gravity.CENTER);
            if (risk == null) {
                estado.setText("Sin datos"); estado.setTextColor(SECONDARY);
            } else {
                estado.setText(palabraSemaforo(risk.color));
                estado.setTextColor(colorFor(risk.color));
                estado.setTypeface(null, Typeface.BOLD);
            }
            cuadro.addView(estado);
            cuadro.setContentDescription(MONTHS[i] + ": " + estado.getText());
            cuadros[i] = cuadro;
            pintarCuadro(cuadro, risk == null ? null : risk.color, false);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i % 3 != 0) cl.leftMargin = dp(8);
            fila.addView(cuadro, cl);
            cuadro.setOnClickListener(v -> mostrarMes(mes, cuadros, details));
        }
        content.addView(details, new LinearLayout.LayoutParams(-1, -2));
        mostrarMes(Calendar.getInstance().get(Calendar.MONTH), cuadros, details);
        text(content, "Es lo que suele pasar cada mes, no el pronóstico del tiempo. "
                + "Los satélites miran toda la zona, no tu terreno exacto.", 14, SECONDARY, false);
    }

    /** Detalle del mes tocado; el cuadro elegido queda marcado con borde. */
    private void mostrarMes(int mes, View[] cuadros, LinearLayout details) {
        for (int i = 0; i < 12; i++) {
            MotorSemaforo.Resultado r = evaluate(monthData(i + 1));
            pintarCuadro(cuadros[i], r == null ? null : r.color, i == mes);
        }
        details.removeAllViews();
        JSONObject item = monthData(mes + 1);
        MotorSemaforo.Resultado risk = evaluate(item);
        if (item == null || risk == null) { cardIn(details, MONTHS[mes], "Todavía no tenemos información de este mes."); return; }
        double humidity = item.optJSONObject("humedad_raiz_predicha").optDouble("neutro");
        LinearLayout box = cardContainer(details);
        text(box, MONTHS[mes], 18, DARK, true);
        text(box, Formato.semaforo(risk.color), 17, colorFor(risk.color), true);
        text(box, explanation(humidity, risk), 15, SECONDARY, false);
        text(box, "Qué hacer: " + advice(humidity, risk), 15, DARK, false);
        text(box, "Lluvia normal: unos " + Formato.numero(item.optDouble("lluvia_mm")) + " mm · Humedad del suelo: "
                + Formato.porcentaje(humidity), 14, SECONDARY, false);
        button(details, "Ver qué sembrar en " + MONTHS[mes].toLowerCase(Locale.ROOT), true,
                () -> openScience(ScienceActivity.MODE_FUTURE));
    }

    private void pintarCuadro(View cuadro, String color, boolean elegido) {
        android.graphics.drawable.GradientDrawable fondo = new android.graphics.drawable.GradientDrawable();
        fondo.setCornerRadius(dp(16));
        fondo.setColor(color == null ? 0xFFF1F1EC
                : "Verde".equalsIgnoreCase(color) ? 0xFFDDF0CF
                : "Amarillo".equalsIgnoreCase(color) ? 0xFFFCEBB8 : 0xFFF8D3CC);
        if (elegido) fondo.setStroke(dp(3), DARK);
        cuadro.setBackground(fondo);
    }

    private String palabraSemaforo(String color) {
        if ("Verde".equalsIgnoreCase(color)) return "Bueno";
        if ("Amarillo".equalsIgnoreCase(color)) return "Con cuidado";
        return "Arriesgado";
    }

    /** Explica los tres colores con muestras pequeñas. */
    private void leyenda(LinearLayout parent) {
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        String[] colores = {"Verde", "Amarillo", "Rojo"};
        for (String c : colores) {
            View muestra = new View(this);
            pintarCuadro(muestra, c, false);
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(dp(18), dp(18));
            ml.rightMargin = dp(6);
            fila.addView(muestra, ml);
            TextView t = new TextView(this);
            t.setText(palabraSemaforo(c)); t.setTextSize(14); t.setTextColor(DARK);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
            tl.rightMargin = dp(16);
            fila.addView(t, tl);
        }
        parent.addView(fila, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showProfile(List<Parcela> parcels) {
        intro("Hola, " + auth.usuarioActual().getNombre().trim().split("\\s+")[0],
                "Aquí está tu cuenta y tus parcelas. Kallpa funciona aunque no tengas internet.");
        card("Mi cuenta", auth.usuarioActual().getNombre() + "\n" + auth.usuarioActual().getCorreo());
        card("La parcela que estás viendo", activeParcel.getNombre() + " · " + zoneLabel() + "\n"
                + Formato.hectareas(activeParcel.getHectareas()) + " · " + activeParcel.getCultivoActual()
                + "\nTienes " + parcels.size() + (parcels.size() == 1 ? " parcela registrada." : " parcelas registradas."));
        button(content, "Cambiar o añadir parcela", true, () -> startActivity(new Intent(this, MyParcelsActivity.class)));
        int available = 0;
        for (String name : REQUIRED) {
            try (InputStream in = getAssets().open("datos/" + name)) { available++; }
            catch (Exception ignored) { }
        }
        boolean completos = available == REQUIRED.length;
        card(completos ? "Todo listo para usar sin internet" : "Faltan datos en este teléfono",
                (isOnline() ? "Ahora tienes internet. " : "Ahora no tienes internet. ")
                + (completos ? "La información de tu zona ya está guardada en el teléfono: puedes usar Kallpa en el campo."
                             : "Faltan " + (REQUIRED.length - available) + " archivos de datos. Vuelve a instalar Kallpa."));
        text(content, "Tus cuentas, parcelas y bitácora se guardan solo en este teléfono. "
                + "Si borras la app o sus datos, se pierden.", 14, SECONDARY, false);
        link(content, "¿De dónde salen los datos?", () -> {
            String periodo = "2005–2025";
            try {
                JSONObject meta = openLocalJson("paquete_canton.json").optJSONObject("meta");
                if (meta != null) periodo = meta.optString("periodo_analizado", periodo);
            } catch (Exception ignored) { }
            explicar("¿De dónde salen los datos?",
                    "Satélites de la NASA: lluvia, humedad del suelo y verdor de las plantas.\n"
                    + "NOAA: los años de El Niño y La Niña.\n"
                    + "INIAP y FAO: información de los cultivos.\n"
                    + "MAG: precio de referencia de la urea.\n\n"
                    + "Años analizados: " + periodo + ". El equipo AgroByte juntó y revisó estos datos. "
                    + "Son una guía para tu zona, no una medición exacta de tu terreno.");
        });
        button(content, "Cerrar sesión", false, () -> new AlertDialog.Builder(this)
                .setTitle("¿Cerrar sesión?")
                .setMessage("Tranquilo: tus parcelas y tu bitácora se quedan guardadas en este teléfono.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Sí, cerrar", (d, w) -> {
                    auth.salir();
                    startActivity(new Intent(this, LoginActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                }).show());
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
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,MONTHS){
            @Override public View getView(int position, View convertView, android.view.ViewGroup group){
                TextView v=(TextView)super.getView(position,convertView,group);
                v.setTextSize(17);v.setTextColor(DARK);v.setTypeface(null,Typeface.BOLD);return v;
            }
            @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup group){
                TextView v=(TextView)super.getDropDownView(position,convertView,group);
                v.setTextSize(17);v.setTextColor(DARK);v.setMinHeight(dp(52));v.setPadding(dp(18),0,dp(18),0);return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setBackgroundResource(R.drawable.bg_selector);
        spinner.setPadding(dp(8),0,dp(48),0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));
        lp.topMargin=dp(6);lp.bottomMargin=dp(4);
        parent.addView(spinner,lp);
        return spinner;
    }
    private void openScience(String module){
        Intent intent=new Intent(this,ScienceActivity.class);
        intent.putExtra(ScienceActivity.EXTRA_MODE,module);
        startActivity(intent);
    }
    private void intro(String title,String detail){
        text(content,title,20,DARK,true);
        text(content,detail,15,SECONDARY,false);
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
        text(card,title,17,DARK,true);
        text(card,desc,15,SECONDARY,false);
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
        button.setTextSize(16);button.setTypeface(null,Typeface.BOLD);button.setStateListAnimator(null);
        button.setBackgroundResource(primary?R.drawable.bg_button_primary:R.drawable.bg_button_soft);
        button.setOnClickListener(v->action.run());
        button.setMinHeight(dp(54));button.setPadding(dp(16),dp(10),dp(16),dp(10));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(12);parent.addView(button,lp);
    }
    /** Acción secundaria como enlace de texto: evita apilar botones grandes. */
    private void link(LinearLayout parent,String label,Runnable action){
        TextView t=new TextView(this);
        t.setText(label+"  ›");t.setTextSize(16);t.setTextColor(FOREST);t.setTypeface(null,Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);t.setMinHeight(dp(48));
        t.setBackgroundResource(android.R.drawable.list_selector_background);
        t.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.topMargin=dp(4);
        parent.addView(t,lp);
    }
    private String f(double value,int decimals){
        return String.format(Locale.US,"%."+decimals+"f",value);
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
}
