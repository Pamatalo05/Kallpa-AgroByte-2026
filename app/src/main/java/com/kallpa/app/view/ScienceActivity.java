package com.kallpa.app.view;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;
import com.kallpa.app.R;
import com.kallpa.app.controller.ControladorAutenticacion;
import com.kallpa.app.model.Parcela;
import com.kallpa.app.model.MotorSemaforo;
import com.kallpa.app.model.RepositorioParcelasLocal;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Módulos científicos. Todos los valores provienen de JSON locales; nunca de cifras de demostración. */
public class ScienceActivity extends ActividadBase {
    public static final String EXTRA_MODE = "kallpa_science_mode";
    public static final String MODE_PRESENT = "presente";
    public static final String MODE_FUTURE = "futuro";
    public static final String MODE_PAST = "pasado";
    public static final String MODE_MUYU = "muyu";
    private static final int VERDE = 0xFF0B5D2A, OSCURO = 0xFF07391E, GRIS = 0xFF6D7468;
    private LinearLayout contenido;
    private TextView titulo, subtitulo;
    private Parcela parcela;
    private String canton, modo;
    private JSONObject satelites, paquete, enso, auditoria;
    private MapView mapa;
    private boolean primeraPantalla = true;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_science);
        contenido = findViewById(R.id.scienceContent);
        titulo = findViewById(R.id.txtScienceTitle);
        subtitulo = findViewById(R.id.txtScienceParcel);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnHome).setOnClickListener(v -> {
            startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish();
        });
        findViewById(R.id.btnAskMuyu).setOnClickListener(v -> mostrar(MODE_MUYU));
        ControladorAutenticacion auth = new ControladorAutenticacion(this);
        if (!auth.sesionActiva() || auth.usuarioActual() == null) {
            startActivity(new Intent(this, LoginActivity.class)); finish(); return;
        }
        String correo = auth.usuarioActual().getCorreo();
        List<Parcela> lista = new RepositorioParcelasLocal(this).obtenerPorUsuario(correo);
        if (lista.isEmpty()) {
            startActivity(new Intent(this, ParcelWelcomeActivity.class)); finish(); return;
        }
        String id = getSharedPreferences("kallpa_seleccion", MODE_PRIVATE)
                .getString("parcela_activa_" + correo.toLowerCase(Locale.ROOT), "");
        parcela = lista.get(0);
        for (Parcela p : lista) if (p.getId().equals(id)) { parcela = p; break; }
        canton = parcela.getCanton() == null ? "" : parcela.getCanton().trim().toLowerCase(Locale.ROOT);
        try {
            satelites = leerJson("evidencia_satelital_parcelas.json");
            paquete = leerJson("paquete_canton.json");
            enso = leerJson("simulador_enso_historico.json");
            auditoria = leerJson("validacion_ml_auditoria.json");
        } catch (Exception e) {
            android.util.Log.e("KallpaDatos", "No se pudo leer un archivo de assets/datos", e);
            titulo.setText("Datos del piloto no disponibles");
            texto(contenido,
                    "No se pudo leer el paquete local de AgroByte.\n\nDetalle: "
                            + e.getMessage()
                            + "\n\nComprueba que los archivos JSON estén en "
                            + "app/src/main/assets/datos/ y vuelve a instalar Kallpa.",
                    15, GRIS, false);
            return;
        }
        modo = getIntent().getStringExtra(EXTRA_MODE);
        mostrar(modo == null ? MODE_PRESENT : modo);
    }

    private JSONObject leerJson(String nombre) throws Exception {
        try (InputStream in = getAssets().open("datos/" + nombre);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new java.io.IOException("datos/" + nombre + " — " + error.getMessage(), error);
        }
    }
    private JSONObject objetoCanton(JSONObject root, String clave) {
        JSONObject cantones = root.optJSONObject(clave);
        return cantones == null ? null : cantones.optJSONObject(canton);
    }
    private void mostrar(String destino) {
        if (mapa != null) { mapa.onDetach(); mapa = null; }
        contenido.removeAllViews();
        modo = destino;
        subtitulo.setText(parcela.getNombre() + " · " + nombreCanton());
        if (!"daule".equals(canton) && !"babahoyo".equals(canton)) {
            titulo.setText("Zona fuera del piloto");
            bloque("Consulta no disponible", "Los datos verificados del prototipo corresponden únicamente a Daule y Babahoyo. Cambia la parcela activa desde Inicio.");
            return;
        }
        if (MODE_FUTURE.equals(destino)) { titulo.setText("Futuro · Próxima siembra"); futuro(); }
        else if (MODE_PAST.equals(destino)) { titulo.setText("Pasado · El Niño y La Niña"); pasado(); }
        else if (MODE_MUYU.equals(destino)) { titulo.setText("Muyu · Te acompaña"); muyu(); }
        else { modo = MODE_PRESENT; titulo.setText("Presente · Mi suelo"); presente(); }
    }
    private String nombreCanton() { return "babahoyo".equals(canton) ? "Babahoyo, Los Ríos" : "Daule, Guayas"; }
    private String f(double v, int decimales) { return String.format(Locale.US, "%1$." + decimales + "f", v); }

    private void presente() {
        JSONObject dato = objetoCanton(satelites, "parcelas_satelitales");
        JSONObject info = dato == null ? null : dato.optJSONObject("resumen_actual");
        JSONObject clima = objetoCanton(paquete, "cantones");
        if (info == null) { bloque("Sin evidencia disponible", "No encontramos datos satelitales de este cantón."); return; }
        texto(contenido, "¿Qué muestran los satélites sobre mi zona?", 18, OSCURO, true);
        texto(contenido, "Últimas observaciones regionales disponibles; no describen cada metro de tu finca.", 13, GRIS, false);
        bloque("Vegetación · NASA MODIS", "NDVI: " + f(info.optDouble("ndvi_ultimo"), 3)
                + "  ·  " + info.optString("fecha_ndvi") + "\n" + info.optString("estado_cobertura") + "\nResolución: 250 m");
        bloque("Humedad superficial · NASA SMAP", f(info.optDouble("smap_humedad_ultima"), 3)
                + " m³/m³  ·  " + info.optString("fecha_smap") + "\nResolución regional: ~9 km");
        if (clima != null) bloque("Suelo de referencia", clima.optString("suelo_referencia")
                + "\nReferencia general, no análisis de laboratorio de tu parcela.");
        texto(contenido, "Mapa de ubicación de tu terreno", 17, OSCURO, true);
        // Mapa desactivado TEMPORALMENTE para separar el diagnóstico JSON de
        // los posibles bloqueos del visor OpenStreetMap en dispositivos físicos.
        // crearMapa();
        texto(contenido, "El mapa satelital se está revisando. Los indicadores regionales "
                + "MODIS/SMAP y sus fechas sí se leen de los JSON precargados.",
                12, GRIS, false);
        texto(contenido, "Evolución de la evidencia satelital", 17, OSCURO, true);
        Button botonNdvi = boton(contenido, "Ver serie NDVI (vegetación)", false, () -> mostrarSerie(dato,"serie_ndvi_modis","ndvi","NDVI de MODIS",0xFF67A83C));
        Button botonSmap = boton(contenido, "Ver serie SMAP (humedad)", false, () -> mostrarSerie(dato,"serie_humedad_smap","humedad_m3_m3","SMAP m³/m³",0xFF228B87));
        // Los gráficos se agregan al final, separados de las tarjetas para evitar saturación.
        texto(contenido, "Fuentes: " + satelites.optJSONObject("fuentes").optString("cobertura_vegetal")
                + "; " + satelites.optJSONObject("fuentes").optString("humedad_superficial"), 11, GRIS, false);
        boton(contenido, "Continuar a mi próxima siembra  ›", true, () -> mostrar(MODE_FUTURE));
    }
    private void crearMapa() {
        try {
            File raiz = new File(getFilesDir(), "osmdroid"); raiz.mkdirs();
            File cache = new File(getCacheDir(), "osmdroid/tiles"); cache.mkdirs();
            Configuration.getInstance().setOsmdroidBasePath(raiz);
            Configuration.getInstance().setOsmdroidTileCache(cache);
            Configuration.getInstance().setUserAgentValue(getPackageName()+" Kallpa/1.0");
            mapa = new MapView(this);
            mapa.setTileSource(TileSourceFactory.MAPNIK);
            mapa.setMultiTouchControls(true);
            mapa.getController().setZoom(12.5);
            GeoPoint punto = new GeoPoint(parcela.getLatitud(), parcela.getLongitud());
            mapa.getController().setCenter(punto);
            Marker pin = new Marker(mapa); pin.setPosition(punto);
            pin.setTitle(parcela.getNombre());
            pin.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            mapa.getOverlays().add(pin);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(205));
            lp.topMargin = dp(8); lp.bottomMargin = dp(8);
            contenido.addView(mapa, lp);
            mapa.onResume();
        } catch (Exception e) { texto(contenido, "Mapa base no disponible. Las observaciones pueden consultarse sin conexión.", 13, GRIS, false); }
    }
    private void mostrarSerie(JSONObject dato, String key, String valueKey, String label, int color) {
        JSONArray valores = dato == null ? null : dato.optJSONArray(key);
        if (valores == null || valores.length() == 0) { mensaje("No hay serie disponible."); return; }
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(label)
                .setView(crearGrafico(valores, valueKey, color, false))
                .setPositiveButton("Cerrar", null).show();
    }
    /** Próxima siembra: traslada la lógica auditada de semaforo.py de AgroByte. */
    private void futuro() {
        JSONObject cantonMl = objetoCanton(paquete, "cantones");
        JSONObject cantonEnso = objetoCanton(enso, "cantones");
        if (cantonMl == null) {
            bloque("Información no disponible", "No encontramos datos de próxima siembra para este cantón.");
            return;
        }

        texto(contenido, "¿Qué puedo sembrar el próximo mes?", 18, OSCURO, true);
        texto(contenido, "Elige tu mes de siembra. Kallpa usa la humedad estimada por "
                + "el modelo Ridge para orientar el riesgo hídrico y comparar las leguminosas "
                + "según el riego disponible.", 14, GRIS, false);
        bloque("Mi terreno", parcela.getNombre() + " · " + nombreCanton() + "\n"
                + "Cultivo actual: " + parcela.getCultivoActual() + " · "
                + f(parcela.getHectareas(), 2) + " hectáreas · "
                + (parcela.isTieneRiego() ? "Con riego" : "Sin riego"));

        texto(contenido, "1. ¿En qué mes planeas sembrar?", 17, OSCURO, true);
        final String[] meses = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};
        Spinner selector = new Spinner(this);
        selector.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, meses));
        contenido.addView(selector, new LinearLayout.LayoutParams(-1, dp(54)));
        LinearLayout resultado = tarjeta(contenido);
        final JSONArray serie = cantonMl.optJSONArray("serie_satelital_ml");

        texto(contenido, "2. Cuatro opciones para cuidar el suelo", 17, OSCURO, true);
        texto(contenido, "El orden y las evaluaciones agronómicas provienen del "
                + "simulador ENSO de AgroByte para un año histórico neutro. "
                + "Se adaptan a si tienes riego, no al mes escogido; "
                + "el dinero en riesgo sí cambia con el semáforo mensual.",
                13, GRIS, false);

        JSONArray opciones = null;
        if (cantonEnso != null) {
            JSONObject escenarios = cantonEnso.optJSONObject("escenarios");
            JSONObject neutro = escenarios == null ? null : escenarios.optJSONObject("neutro");
            JSONObject porRiego = neutro == null ? null : neutro.optJSONObject("recomendacion_por_parcela");
            opciones = porRiego == null ? null : porRiego.optJSONArray(
                    parcela.isTieneRiego() ? "con_riego" : "sin_riego");
        }
        final JSONArray opcionesAuditorias = opciones;
        LinearLayout listado = new LinearLayout(this);
        listado.setOrientation(LinearLayout.VERTICAL);
        contenido.addView(listado, new LinearLayout.LayoutParams(-1, -2));

        bloque("¿Y si continúo con el mismo cultivo?",
                "Monocultivo de referencia: " + parcela.getCultivoActual() + ".\n"
                + "Ahorro de urea por rotación: $0,00. "
                + "Esto no significa que todos los costos sean cero.");

        JSONObject metrica = auditoria == null ? null
                : auditoria.optJSONObject("metricas_modelo_seleccionado");
        String r2 = metrica != null && metrica.has("r2_test_2021_2025")
                ? f(metrica.optDouble("r2_test_2021_2025"), 4)
                : (paquete.optJSONObject("ml_metadata") != null
                    ? f(paquete.optJSONObject("ml_metadata").optDouble("r2_test"), 4)
                    : "No disponible");
        boton(contenido, "¿Cómo se calcularon estos resultados?", false, () ->
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Método, fuentes y limitaciones")
                        .setMessage("Semáforo Día Cero: reglas originales de semaforo.py "
                                + "del equipo AgroByte, con humedad radicular estimada Ridge "
                                + "para el mes y escenario neutro, más disponibilidad de riego.\n\n"
                                + "Ridge: R² en prueba temporal 2021–2025: " + r2
                                + ". No garantiza exactitud por finca.\n\n"
                                + "Ranking: cuatro leguminosas del simulador ENSO, "
                                + "escenario histórico neutro, clasificadas según riego.\n\n"
                                + "Capital en riesgo = costo de insumos por hectárea × hectáreas "
                                + "× factor del semáforo (verde 0 %, amarillo 30 %, rojo 100 %).\n\n"
                                + "Ahorro de urea = kg/ha evitables × hectáreas × $0,76/kg "
                                + "(MAG SIPA, $38 por saco de 50 kg).\n\n"
                                + "Margen de referencia = (ingreso bruto - insumos) × hectáreas "
                                + "+ ahorro de urea. No incluye todos los costos de producción.\n\n"
                                + "Fuentes: NASA POWER/SMAP, NOAA, INIAP, FAO y MAG SIPA. "
                                + "Son referencias regionales/históricas; valida con "
                                + "un especialista agrícola antes de invertir.")
                        .setPositiveButton("Entendido", null).show());
        boton(contenido, "Explorar El Niño y La Niña  ›", true,
                () -> mostrar(MODE_PAST));

        // Actualiza a la vez humedad, semáforo, factor de riesgo y montos de cada cultivo.
        selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                                                  int posicion, long id) {
                resultado.removeAllViews();
                listado.removeAllViews();
                JSONObject datoMes = buscarMes(serie, posicion + 1);
                if (datoMes == null) {
                    texto(resultado, "Sin datos para " + meses[posicion] + ".", 14, GRIS, false);
                    mostrarRotacionesParaProximaSiembra(listado, opcionesAuditorias, null);
                    return;
                }
                JSONObject predicciones = datoMes.optJSONObject("humedad_raiz_predicha");
                double humedad = predicciones == null ? Double.NaN
                        : predicciones.optDouble("neutro", Double.NaN);
                if (!Double.isFinite(humedad)) {
                    texto(resultado, "No está disponible la humedad estimada de "
                            + meses[posicion] + ".", 14, GRIS, false);
                    mostrarRotacionesParaProximaSiembra(listado, opcionesAuditorias, null);
                    return;
                }

                MotorSemaforo.Resultado semaforo =
                        MotorSemaforo.evaluar(humedad, parcela.isTieneRiego());
                int colorSemaforo = "Verde".equals(semaforo.color) ? VERDE
                        : ("Amarillo".equals(semaforo.color) ? 0xFF956521 : 0xFFB32625);
                texto(resultado, "Riesgo hídrico de la próxima siembra · "
                        + meses[posicion], 15, OSCURO, true);
                texto(resultado, "SEMÁFORO " + semaforo.color.toUpperCase(Locale.ROOT),
                        22, colorSemaforo, true);
                texto(resultado, "Según la regla del modelo: " + semaforo.estado,
                        13, OSCURO, false);
                texto(resultado, "Humedad estimada en la zona: " + f(humedad, 3)
                        + " m³/m³", 15, OSCURO, true);
                if (datoMes.has("lluvia_mm")) {
                    texto(resultado, "Lluvia mensual de referencia: "
                            + f(datoMes.optDouble("lluvia_mm"), 1) + " mm", 13, GRIS, false);
                }
                texto(resultado, "Es una proyección mensual basada en condiciones "
                        + "históricas, NO un pronóstico del día de siembra ni "
                        + "una garantía para tu parcela.", 13, GRIS, false);

                mostrarRotacionesParaProximaSiembra(listado, opcionesAuditorias, semaforo);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    /** Busca el mes real indicado en el JSON; no presupone que esté ordenado. */
    private JSONObject buscarMes(JSONArray serie, int mes) {
        if (serie == null) return null;
        for (int i = 0; i < serie.length(); i++) {
            JSONObject item = serie.optJSONObject(i);
            if (item != null && item.optInt("mes", -1) == mes) return item;
        }
        return null;
    }

    /** Ranking, factores económicos y margen: traducción fiel de semaforo.py. */
    private void mostrarRotacionesParaProximaSiembra(LinearLayout destino,
                                                      JSONArray opciones,
                                                      MotorSemaforo.Resultado semaforo) {
        if (opciones == null || opciones.length() == 0) {
            bloqueEn(destino, "Alternativas no disponibles", "Faltan las recomendaciones "
                    + "del escenario neutro para tu cantón y condición de riego.");
            return;
        }
        for (int i = 0; i < opciones.length(); i++) {
            JSONObject opcion = opciones.optJSONObject(i);
            if (opcion == null) continue;
            String cultivo = opcion.optString("cultivo", "Opción agrícola");
            String estado = opcion.optString("estado", "Sin clasificación");
            double ha = parcela.getHectareas();
            double kgHa = opcion.optDouble("ahorro_urea_kg_ha", Double.NaN);
            double costoHa = opcion.optDouble("costo_insumos_ha", Double.NaN);
            double ingresoHa = opcion.optDouble("ingreso_bruto_ha", Double.NaN);
            LinearLayout card = tarjeta(destino);
            texto(card, (i + 1) + ". " + cultivo, 16, OSCURO, true);
            int estadoColor = "Óptimo".equalsIgnoreCase(estado) ? VERDE
                    : ("No recomendado".equalsIgnoreCase(estado)
                        ? 0xFFAE4132 : 0xFF986B22);
            texto(card, "Compatibilidad histórica: " + estado, 14, estadoColor, true);
            if (opcion.has("score_compatibilidad")) {
                texto(card, "Puntaje de compatibilidad: "
                        + f(opcion.optDouble("score_compatibilidad"), 1), 12, GRIS, false);
            }
            texto(card, opcion.optString("nota_agronomica",
                    "Consulta las condiciones del terreno antes de elegir."),
                    13, GRIS, false);
            if (ha <= 0 || !Double.isFinite(ha)
                    || !Double.isFinite(kgHa) || !Double.isFinite(costoHa)
                    || !Double.isFinite(ingresoHa)) {
                texto(card, "No hay datos suficientes para calcular "
                        + "las estimaciones económicas de esta alternativa.",
                        13, GRIS, false);
                continue;
            }
            double ahorroKg = kgHa * ha;
            texto(card, "Ahorro potencial de urea: " + f(ahorroKg, 1)
                    + " kg para " + f(ha, 2) + " ha", 13, GRIS, false);
            if (semaforo != null) {
                MotorSemaforo.Economia economia = MotorSemaforo.calcular(
                        costoHa, ingresoHa, kgHa, ha, semaforo);
                texto(card, "Posible ahorro en urea: $" + f(economia.ahorroUreaUsd, 2),
                        14, VERDE, true);
                texto(card, "Capital en riesgo estimado (" + semaforo.color + ", "
                        + f(semaforo.factorRiesgo * 100, 0) + " %): $"
                        + f(economia.capitalEnRiesgoUsd, 2),
                        14, 0xFF865C21, true);
                texto(card, "Insumos estimados: $" + f(economia.costoTotalUsd, 2)
                        + " · Margen referencial: $" + f(economia.margenNetoUsd, 2),
                        13, GRIS, false);
                boton(card, "¿Por qué este riesgo y este ahorro?", false,
                        () -> new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle(cultivo)
                                .setMessage("Regla de Arianna para " + semaforo.color + ": "
                                        + f(semaforo.factorRiesgo * 100, 0)
                                        + " % del costo de insumos expuesto.\n"
                                        + "Insumos: $" + f(economia.costoTotalUsd, 2)
                                        + " → Capital en riesgo: $"
                                        + f(economia.capitalEnRiesgoUsd, 2)
                                        + "\n\nAhorro de urea: " + f(kgHa, 1)
                                        + " kg/ha × " + f(ha, 2) + " ha × $0,76/kg = $"
                                        + f(economia.ahorroUreaUsd, 2)
                                        + "\n\nMargen: (ingreso bruto - insumos) × "
                                        + "hectáreas + ahorro de urea = $"
                                        + f(economia.margenNetoUsd, 2)
                                        + "\n\nSon estimaciones basadas en datos "
                                        + "históricos. No significan pérdidas, "
                                        + "ganancias ni ahorro garantizados.")
                                .setPositiveButton("Entendido", null).show());
            } else {
                texto(card, "Capital en riesgo: sin calcular (faltan datos "
                        + "del mes elegido).", 13, GRIS, false);
            }
            texto(card, "Consulta drenaje, disponibilidad de agua y costos locales "
                    + "antes de decidir.", 12, GRIS, false);
        }
    }

    private void pasado() {
        JSONObject cantonEnso = objetoCanton(enso,"cantones");
        if (cantonEnso == null) { bloque("Sin datos ENSO", "Falta el histórico para tu cantón."); return; }
        texto(contenido,"¿Cómo respondió mi zona en otros años?",18,OSCURO,true);
        texto(contenido,"Escenarios registrados de 2005 a 2025. Son antecedentes, no pronósticos futuros.",13,GRIS,false);
        texto(contenido,"Selecciona un escenario histórico",16,OSCURO,true);
        String[] opciones={"Año normal · Neutro","El Niño · Lluvias intensas","La Niña · Menos lluvia"};
        String[] claves={"neutro","nino","nina"};
        Spinner selector=new Spinner(this);
        selector.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,opciones));
        contenido.addView(selector,new LinearLayout.LayoutParams(-1,dp(50)));
        LinearLayout resultado=new LinearLayout(this);
        resultado.setOrientation(LinearLayout.VERTICAL);
        contenido.addView(resultado,new LinearLayout.LayoutParams(-1,-2));
        selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> p,View v,int pos,long id){
                resultado.removeAllViews();
                JSONObject escenarios=cantonEnso.optJSONObject("escenarios");
                JSONObject e=escenarios==null?null:escenarios.optJSONObject(claves[pos]);
                if(e==null){texto(resultado,"Sin información para este escenario.",13,GRIS,false);return;}
                JSONArray anios=e.optJSONArray("anios_referencia");
                JSONObject indicadores=e.optJSONObject("indicadores_historicos");
                bloqueEn(resultado,"Registros de referencia: "+(anios==null?"Sin años":anios.toString()),
                        e.optString("resumen_impacto_suelo"));
                if(indicadores!=null) bloqueEn(resultado,"Promedios de los eventos históricos",
                        "Lluvia: "+f(indicadores.optDouble("lluvia_mensual_prom_mm"),1)+" mm/mes\n"
                        +"Humedad radicular: "+f(indicadores.optDouble("humedad_raiz_prom"),3)+" m³/m³\n"
                        +"Temperatura máxima: "+f(indicadores.optDouble("temp_max_prom_c"),1)+" °C");
                JSONArray perfil=e.optJSONArray("perfil_mensual");
                if(perfil!=null){
                    texto(resultado,"Lluvia histórica · 12 meses",16,OSCURO,true);
                    resultado.addView(crearGrafico(perfil,"lluvia_mm",0xFF438CB6,true),new LinearLayout.LayoutParams(-1,dp(195)));
                    texto(resultado,"Humedad radicular · 12 meses",16,OSCURO,true);
                    resultado.addView(crearGrafico(perfil,"humedad_raiz",0xFF67A83C,true),new LinearLayout.LayoutParams(-1,dp(195)));
                }
                texto(resultado,"Rotaciones en un escenario como este",16,OSCURO,true);
                JSONObject r=e.optJSONObject("recomendacion_por_parcela");
                mostrarRotacionesEn(resultado,r==null?null:r.optJSONArray(parcela.isTieneRiego()?"con_riego":"sin_riego"));
                bloqueEn(resultado,"Referencia de monocultivo", "Continuar con el mismo cultivo: ahorro de urea por rotación = $0,00. Cifras alternativas referenciales, no rendimientos garantizados.");
                texto(resultado,"Fuentes: NASA POWER/SMAP · NOAA CPC (ONI) · INIAP. Comparaciones regionales, verificación de campo necesaria.",12,GRIS,false);
                boton(resultado, "Compartir resumen histórico", false,
                        () -> compartirResumenEnso(e,
                                opciones[pos],
                                indicadores,
                                r == null ? null : r.optJSONArray(
                                        parcela.isTieneRiego() ? "con_riego" : "sin_riego")));
            }
            @Override public void onNothingSelected(AdapterView<?> p){}
        });
        boton(contenido,"Preguntarle a Muyu sobre los escenarios  ›",true,()->mostrar(MODE_MUYU));
    }
    private void mostrarRotacionesEn(LinearLayout padre,JSONArray opciones){
        if(opciones==null){texto(padre,"Sin ranking disponible.",13,GRIS,false);return;}
        for(int i=0;i<opciones.length();i++){
            JSONObject x=opciones.optJSONObject(i);if(x==null)continue;
            double ahorro=x.optDouble("ahorro_urea_kg_ha",0)*parcela.getHectareas()*0.76;
            bloqueEn(padre,(i+1)+". "+x.optString("cultivo")+" · "+x.optString("estado"),
                    "Ahorro referencial de urea para tu superficie: $"+f(ahorro,2)+"\n"+x.optString("nota_agronomica"));
        }
    }
    /** Comparte únicamente datos históricos presentes en el JSON local, sin crear pronósticos. */
    private void compartirResumenEnso(JSONObject escenario, String nombreEscenario,
                                       JSONObject indicadores, JSONArray rotaciones) {
        StringBuilder mensaje = new StringBuilder();
        mensaje.append("Kallpa · AgroByte\n")
                .append("Parcela: ").append(parcela.getNombre()).append("\n")
                .append("Cantón: ").append(nombreCanton()).append("\n")
                .append("Superficie: ").append(f(parcela.getHectareas(), 2)).append(" ha\n")
                .append("Disponibilidad de riego: ")
                .append(parcela.isTieneRiego() ? "Sí" : "No").append("\n\n")
                .append("ESCENARIO HISTÓRICO: ").append(nombreEscenario).append("\n")
                .append("Años de referencia: ")
                .append(escenario.optJSONArray("anios_referencia")).append("\n\n");
        if (indicadores != null) {
            mensaje.append("Lluvia mensual promedio: ")
                    .append(f(indicadores.optDouble("lluvia_mensual_prom_mm"), 1))
                    .append(" mm\n")
                    .append("Humedad radicular promedio: ")
                    .append(f(indicadores.optDouble("humedad_raiz_prom"), 3))
                    .append(" m³/m³\n")
                    .append("Temperatura máxima promedio: ")
                    .append(f(indicadores.optDouble("temp_max_prom_c"), 1))
                    .append(" °C\n\n");
        }
        if (rotaciones != null) {
            mensaje.append("ALTERNATIVAS DE ROTACIÓN\n");
            for (int i = 0; i < rotaciones.length(); i++) {
                JSONObject cultivo = rotaciones.optJSONObject(i);
                if (cultivo == null) continue;
                double kgHa = cultivo.optDouble("ahorro_urea_kg_ha", Double.NaN);
                mensaje.append(i + 1).append(". ")
                        .append(cultivo.optString("cultivo"))
                        .append(" — ").append(cultivo.optString("estado"));
                if (Double.isFinite(kgHa)) {
                    double ahorro = kgHa * parcela.getHectareas() * 0.76;
                    mensaje.append(" · Ahorro referencial de urea: $")
                            .append(f(ahorro, 2));
                }
                mensaje.append("\n");
            }
        }
        mensaje.append("\nMonocultivo de referencia: $0 de ahorro "
                        + "de urea por rotación.\n")
                .append("Fuentes: NASA POWER / SMAP, NOAA, INIAP y MAG SIPA.\n")
                .append("Datos históricos 2005–2025. No es un pronóstico "
                        + "del clima futuro ni una garantía de resultados. "
                        + "Contrasta la recomendación con experiencia de campo.");
        Intent compartir = new Intent(Intent.ACTION_SEND);
        compartir.setType("text/plain");
        compartir.putExtra(Intent.EXTRA_TEXT, mensaje.toString());
        startActivity(Intent.createChooser(compartir, "Compartir información agrícola"));
    }

    private void muyu(){
        texto(contenido,"¡Hola! Soy Muyu 🌱",21,OSCURO,true);
        texto(contenido,"Tu acompañante agrícola sin conexión. Elige una pregunta; mis respuestas se basan en los archivos del piloto.",14,GRIS,false);
        boton(contenido,"¿Qué significa NDVI?",false,()->respuesta("NDVI y cobertura",
                "El NDVI compara cómo la vegetación refleja la luz. El archivo de NASA MODIS resume la cobertura regional; un valor aislado no demuestra el estado exacto de una finca. Resolución aproximada: 250 m. Fuente: NASA MODIS MOD13Q1."));
        boton(contenido,"¿Qué me dice SMAP?",false,()->respuesta("Humedad de la zona",
                "SMAP estima humedad en la capa superficial a escala regional de aproximadamente 9 km. No sustituye medir la humedad real en tu parcela. Fuente: NASA SMAP SPL3SMP_E."));
        boton(contenido,"¿Por qué cambiar de cultivo?",false,()->respuesta("Cuidar el suelo",
                "Alternar con leguminosas como soya, fréjol caupí, maní o mucuna puede ayudar al ciclo del nitrógeno. La especie adecuada depende del riego, drenaje, época y experiencia local. Fuentes: INIAP y FAO."));
        boton(contenido,"¿Cuánto puedo ahorrar en urea?",false,()->respuesta("Ahorro aproximado",
                "La referencia del equipo es $0,76 por kg de urea (saco de 50 kg a $38). La app multiplica los kg potencialmente evitados por tus "+f(parcela.getHectareas(),2)+" ha. No garantiza un ahorro real. Fuente: MAG SIPA/INIAP."));
        boton(contenido,"¿Qué significa El Niño?",false,()->respuesta("Historial ENSO",
                "Los escenarios muestran lluvias y humedad de años anteriores (2005–2025) usando NASA y NOAA. No indican con certeza qué pasará esta semana ni en una fecha futura."));
        boton(contenido,"¿Qué significa Ridge?",false,()->respuesta("Predicción base",
                "Ridge es el modelo estadístico que el equipo de datos evaluó con información histórica. Su R² de prueba fue 0,9561; esta métrica no garantiza que prediga con exactitud una finca específica."));
        bloque("Mi contexto",parcela.getNombre()+" · "+nombreCanton()+" · "
                +f(parcela.getHectareas(),2)+" ha · Riego: "+(parcela.isTieneRiego()?"Sí":"No"));
        texto(contenido,"Muyu opera aquí como guía de preguntas frecuentes offline. Las consultas libres con IA requieren una integración en línea separada que aún no está incluida.",12,GRIS,false);
        boton(contenido,"Consultar diagnóstico satelital  ›",true,()->mostrar(MODE_PRESENT));
    }
    private void respuesta(String titulo,String mensaje){
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(titulo).setMessage(mensaje)
                .setPositiveButton("Entendido",null).show();
    }
    private LinearLayout tarjeta(LinearLayout padre){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16),dp(15),dp(16),dp(15));card.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(12);lp.bottomMargin=dp(6);padre.addView(card,lp);
        return card;
    }
    private void bloque(String cabecera,String cuerpo){bloqueEn(contenido,cabecera,cuerpo);}
    private void bloqueEn(LinearLayout padre,String cabecera,String cuerpo){
        LinearLayout c=tarjeta(padre);texto(c,cabecera,15,OSCURO,true);texto(c,cuerpo,14,GRIS,false);
    }
    private TextView texto(LinearLayout padre,String valor,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(valor);t.setTextSize(size);t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);t.setLineSpacing(dp(3),1.0f);
        t.setPadding(0,dp(4),0,dp(4));padre.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;
    }
    private Button boton(LinearLayout padre,String texto,boolean principal,Runnable click){
        Button b=new Button(this);b.setText(texto);b.setAllCaps(false);
        b.setTextColor(principal?Color.WHITE:VERDE);
        b.setBackgroundResource(principal?R.drawable.bg_button_primary:R.drawable.bg_button_outline);
        b.setTextSize(14);b.setOnClickListener(v->click.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(53));lp.topMargin=dp(10);
        padre.addView(b,lp);return b;
    }
    private View crearGrafico(JSONArray arr,String campo,int color,boolean meses){
        ArrayList<Float> serie=new ArrayList<>();
        if(arr!=null)for(int i=0;i<arr.length();i++){
            JSONObject x=arr.optJSONObject(i);if(x!=null)serie.add((float)x.optDouble(campo,0));
        }
        SeriesView v=new SeriesView(serie,color);
        if(!meses) v.setPadding(dp(12),dp(12),dp(12),dp(12));
        return v;
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private class SeriesView extends View{
        private final ArrayList<Float> datos;private final Paint p=new Paint(3);private final int color;
        SeriesView(ArrayList<Float> valores,int color){super(ScienceActivity.this);this.datos=valores;this.color=color;
            setBackgroundResource(R.drawable.bg_card);setMinimumHeight(dp(190));}
        @Override protected void onMeasure(int w,int h){super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(dp(190),View.MeasureSpec.EXACTLY));}
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(datos.size()<2)return;
            float l=dp(25),r=getWidth()-dp(16),t=dp(19),b=getHeight()-dp(31);
            float min=Float.MAX_VALUE,max=-Float.MAX_VALUE;
            for(float v:datos){min=Math.min(min,v);max=Math.max(max,v);}
            if(max-min<.01f){max+=.01f;min-=.01f;}
            p.setColor(0xFFCBD8C1);p.setStrokeWidth(dp(1));p.setStyle(Paint.Style.STROKE);
            c.drawLine(l,b,r,b,p);c.drawLine(l,t,l,b,p);
            Path path=new Path();for(int i=0;i<datos.size();i++){
                float x=l+(r-l)*i/(datos.size()-1f);
                float y=b-(datos.get(i)-min)/(max-min)*(b-t);
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            p.setColor(color);p.setStrokeWidth(dp(3));c.drawPath(path,p);
            p.setStyle(Paint.Style.FILL);p.setTextSize(dp(10));p.setColor(GRIS);
            c.drawText(f(max,2),dp(3),t+dp(6),p);c.drawText(f(min,2),dp(3),b,p);
            c.drawText("Inicio",l,b+dp(17),p);c.drawText("Fin",r-dp(16),b+dp(17),p);
        }
    }
    @Override protected void onResume(){super.onResume();if(mapa!=null)mapa.onResume();}
    @Override protected void onPause(){if(mapa!=null)mapa.onPause();super.onPause();}
    @Override protected void onDestroy(){if(mapa!=null)mapa.onDetach();super.onDestroy();}
}
