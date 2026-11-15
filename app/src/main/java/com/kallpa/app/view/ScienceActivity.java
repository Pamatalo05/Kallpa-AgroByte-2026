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
            titulo.setText("No pudimos abrir los datos");
            texto(contenido,
                    "Kallpa no encontró la información de tu zona en este teléfono. "
                            + "Prueba cerrando y abriendo la app; si sigue igual, vuelve a instalarla.",
                    16, GRIS, false);
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
        if (MODE_MUYU.equals(destino)) {
            // Hay un solo Muyu en la app: el de la barra inferior.
            startActivity(new Intent(this, CompanionActivity.class)
                    .putExtra(CompanionActivity.EXTRA_MODE, CompanionActivity.MODE_MUYU));
            finish();
            return;
        }
        if (MODE_FUTURE.equals(destino)) activarBarraInferior(SECCION_SIEMBRA, true);
        else if (MODE_PAST.equals(destino)) activarBarraInferior(SECCION_CLIMA, false);
        else activarBarraInferior(SECCION_PARCELA, false);
        if (!"daule".equals(canton) && !"babahoyo".equals(canton)) {
            titulo.setText("Aún no tenemos tu zona");
            bloque("Por ahora solo Daule y Babahoyo", "Kallpa todavía no tiene datos de tu cantón. "
                    + "Si tienes una parcela en Daule o Babahoyo, elígela en \"Mi parcela\".");
            return;
        }
        if (MODE_FUTURE.equals(destino)) { titulo.setText("¿Qué siembro después?"); futuro(); }
        else if (MODE_PAST.equals(destino)) { titulo.setText("El Niño y La Niña"); pasado(); }
        else { modo = MODE_PRESENT; titulo.setText("Mi terreno"); presente(); }
    }
    private String nombreCanton() { return "babahoyo".equals(canton) ? "Babahoyo, Los Ríos" : "Daule, Guayas"; }
    private String f(double v, int decimales) { return String.format(Locale.US, "%1$." + decimales + "f", v); }

    private void presente() {
        JSONObject dato = objetoCanton(satelites, "parcelas_satelitales");
        JSONObject info = dato == null ? null : dato.optJSONObject("resumen_actual");
        JSONObject clima = objetoCanton(paquete, "cantones");
        if (info == null) { bloque("Sin datos del satélite", "Todavía no tenemos fotos del satélite para tu cantón."); return; }
        texto(contenido, "¿Qué ven los satélites en mi zona?", 19, OSCURO, true);
        texto(contenido, "Los satélites miran toda la zona, no cada metro de tu finca. "
                + "Úsalo como una pista y compáralo con lo que ves en tu terreno.", 15, GRIS, false);
        double ndvi = info.optDouble("ndvi_ultimo");
        bloque("🌿 Verdor de las plantas: " + Formato.verdor(ndvi),
                "Mientras más alto, más vegetación verde hay en la zona.\n"
                + info.optString("estado_cobertura") + "\nÚltima foto: " + info.optString("fecha_ndvi"));
        double smap = info.optDouble("smap_humedad_ultima");
        bloque("💧 Humedad en la superficie: " + Formato.porcentaje(smap),
                "Es el agua en la capa de arriba del suelo, medida para toda la zona.\n"
                + "Última medición: " + info.optString("fecha_smap"));
        if (clima != null) bloque("🟫 Tipo de suelo de la zona", clima.optString("suelo_referencia")
                + "\nEs el suelo más común en tu cantón; el tuyo puede ser distinto.");
        // Mapa desactivado TEMPORALMENTE para separar el diagnóstico JSON de
        // los posibles bloqueos del visor OpenStreetMap en dispositivos físicos.
        // crearMapa();
        texto(contenido, "¿Cómo ha cambiado mi zona?", 18, OSCURO, true);
        boton(contenido, "Ver cómo cambió el verdor", false, () -> mostrarSerie(dato,"serie_ndvi_modis","ndvi","Verdor de las plantas en tu zona",0xFF67A83C));
        boton(contenido, "Ver cómo cambió la humedad", false, () -> mostrarSerie(dato,"serie_humedad_smap","humedad_m3_m3","Humedad en la superficie de tu zona",0xFF228B87));
        texto(contenido, "Datos de satélites de la NASA (MODIS y SMAP).", 13, GRIS, false);
        boton(contenido, "¿Qué siembro después?  ›", true, () -> mostrar(MODE_FUTURE));
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
        if (valores == null || valores.length() == 0) { mensaje("Todavía no hay mediciones para mostrar."); return; }
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(label)
                .setView(crearGrafico(valores, valueKey, color, false))
                .setPositiveButton("Cerrar", null).show();
    }
    /** Próxima siembra: traslada la lógica auditada de semaforo.py de AgroByte. */
    private void futuro() {
        JSONObject cantonMl = objetoCanton(paquete, "cantones");
        JSONObject cantonEnso = objetoCanton(enso, "cantones");
        if (cantonMl == null) {
            bloque("Aún no hay datos", "Todavía no tenemos información de siembra para tu cantón.");
            return;
        }

        texto(contenido, "¿Cuándo quieres sembrar?", 19, OSCURO, true);
        texto(contenido, "Elige el mes. Te decimos si el suelo suele estar bien para sembrar "
                + "y qué cultivos te pueden convenir.", 15, GRIS, false);
        bloque("Tu parcela", parcela.getNombre() + " · " + nombreCanton() + "\n"
                + parcela.getCultivoActual() + " · " + Formato.hectareas(parcela.getHectareas())
                + " · " + (parcela.isTieneRiego() ? "Con riego" : "Sin riego"));

        texto(contenido, "1. Mes de siembra", 18, OSCURO, true);
        final String[] meses = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};
        Spinner selector = selectorGrande(contenido, meses);
        LinearLayout resultado = tarjeta(contenido);
        final JSONArray serie = cantonMl.optJSONArray("serie_satelital_ml");

        texto(contenido, "2. Cultivos que te pueden convenir", 18, OSCURO, true);
        texto(contenido, "Son plantas que devuelven nitrógeno a la tierra (leguminosas). "
                + "Van en orden: la primera es la que mejor resultó en años normales "
                + "para una parcela " + (parcela.isTieneRiego() ? "con riego." : "sin riego."),
                15, GRIS, false);

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

        bloque("¿Y si siembro otra vez " + parcela.getCultivoActual().toLowerCase(Locale.ROOT) + "?",
                "Puedes hacerlo, pero no ahorrarías urea: el mismo cultivo seguido "
                + "no le devuelve nitrógeno a la tierra.");

        boton(contenido, "¿De dónde salen estas cifras?", false, () -> explicar(
                "¿De dónde salen estas cifras?",
                "🌧️ El mes: miramos cómo estuvo el suelo de tu zona en ese mes durante "
                + "los últimos 20 años (datos de la NASA) y si tienes riego.\n\n"
                + "🌱 Los cultivos: comparamos soya, fréjol caupí, maní y mucuna con fichas del INIAP "
                + "y de la FAO.\n\n"
                + "💵 El ahorro en urea: calculamos cuánta urea dejarías de poner gracias al "
                + "nitrógeno que deja el cultivo, con el precio del MAG (unos $38 el saco de 50 kg).\n\n"
                + "💰 La ganancia: es lo que podrías vender menos lo que gastarías en semillas e insumos. "
                + "No incluye todos los gastos (por ejemplo, mano de obra o transporte).\n\n"
                + "Todo es aproximado. Es una guía para decidir mejor, no una promesa. "
                + "Antes de invertir, revisa tu terreno y, si puedes, conversa con un técnico."));
        boton(contenido, "Ver cómo fue en años de El Niño y La Niña  ›", true,
                () -> mostrar(MODE_PAST));

        // Actualiza a la vez humedad, semáforo, factor de riesgo y montos de cada cultivo.
        selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                                                  int posicion, long id) {
                resultado.removeAllViews();
                listado.removeAllViews();
                JSONObject datoMes = buscarMes(serie, posicion + 1);
                if (datoMes == null) {
                    texto(resultado, "Todavía no tenemos datos de " + meses[posicion] + ".", 15, GRIS, false);
                    mostrarRotacionesParaProximaSiembra(listado, opcionesAuditorias, null);
                    return;
                }
                JSONObject predicciones = datoMes.optJSONObject("humedad_raiz_predicha");
                double humedad = predicciones == null ? Double.NaN
                        : predicciones.optDouble("neutro", Double.NaN);
                if (!Double.isFinite(humedad)) {
                    texto(resultado, "Todavía no sabemos cómo suele estar el suelo en "
                            + meses[posicion] + ".", 15, GRIS, false);
                    mostrarRotacionesParaProximaSiembra(listado, opcionesAuditorias, null);
                    return;
                }

                MotorSemaforo.Resultado semaforo =
                        MotorSemaforo.evaluar(humedad, parcela.isTieneRiego());
                int colorSemaforo = "Verde".equals(semaforo.color) ? VERDE
                        : ("Amarillo".equals(semaforo.color) ? 0xFF956521 : 0xFFB32625);
                texto(resultado, "Si siembras en " + meses[posicion].toLowerCase(Locale.ROOT) + ":",
                        15, OSCURO, true);
                texto(resultado, Formato.semaforo(semaforo.color), 21, colorSemaforo, true);
                texto(resultado, "En ese mes el suelo de tu zona suele estar "
                        + Formato.humedadEnPalabras(humedad) + " (humedad cerca de "
                        + Formato.porcentaje(humedad) + ")."
                        + (datoMes.has("lluvia_mm") ? " Normalmente llueven unos "
                            + Formato.numero(datoMes.optDouble("lluvia_mm")) + " mm en el mes." : ""),
                        15, OSCURO, false);
                texto(resultado, consejoDelMes(humedad, semaforo), 15, OSCURO, false);
                texto(resultado, "Es lo que suele pasar en ese mes, no el pronóstico del tiempo.",
                        13, GRIS, false);

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
            bloqueEn(destino, "Aún no hay cultivos para mostrar", "Todavía no tenemos opciones "
                    + "para tu cantón con " + (parcela.isTieneRiego() ? "riego." : "esta condición de riego."));
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
            if (i == 0) texto(card, "⭐ La que más te conviene", 13, VERDE, true);
            texto(card, (i + 1) + ". " + cultivo, 18, OSCURO, true);
            int estadoColor = "Óptimo".equalsIgnoreCase(estado) ? VERDE
                    : ("No recomendado".equalsIgnoreCase(estado)
                        ? 0xFFAE4132 : 0xFF986B22);
            texto(card, Formato.evaluacionCultivo(estado), 15, estadoColor, true);
            texto(card, opcion.optString("nota_agronomica",
                    "Revisa tu terreno antes de elegir."),
                    14, GRIS, false);
            if (ha <= 0 || !Double.isFinite(ha)
                    || !Double.isFinite(kgHa) || !Double.isFinite(costoHa)
                    || !Double.isFinite(ingresoHa)) {
                texto(card, "Todavía no tenemos los precios de este cultivo para hacer las cuentas.",
                        14, GRIS, false);
                continue;
            }
            if (semaforo != null) {
                MotorSemaforo.Economia economia = MotorSemaforo.calcular(
                        costoHa, ingresoHa, kgHa, ha, semaforo);
                String enTuParcela = " en tus " + Formato.hectareas(ha);
                texto(card, "💵 Ahorro en urea: " + Formato.unos(economia.ahorroUreaUsd), 16, VERDE, true);
                texto(card, "Dejarías de poner " + Formato.kilos(kgHa * ha) + " de urea"
                        + enTuParcela + ".", 14, OSCURO, false);
                texto(card, "💰 Podrías ganar " + Formato.unos(economia.margenNetoUsd)
                        + enTuParcela + ", después de pagar semillas e insumos (unos "
                        + Formato.dinero(economia.costoTotalUsd) + ").", 14, OSCURO, false);
                texto(card, economia.capitalEnRiesgoUsd <= 0
                        ? "🛡️ Ese mes se ve bien: es poco probable que pierdas lo invertido por falta o exceso de agua."
                        : "⚠️ Si el clima sale mal ese mes, podrías perder " + Formato.unos(economia.capitalEnRiesgoUsd)
                            + " de lo que inviertas.",
                        14, economia.capitalEnRiesgoUsd <= 0 ? VERDE : 0xFF865C21, false);
                texto(card, Formato.NOTA_APROXIMADO, 13, GRIS, false);
                boton(card, "¿Cómo se calculó?", false, () -> explicar(cultivo,
                        "💵 Ahorro en urea: este cultivo deja unos " + Formato.kilos(kgHa)
                        + " de nitrógeno por hectárea que ya no tendrías que comprar como urea. "
                        + "En tus " + Formato.hectareas(ha) + " son " + Formato.kilos(kgHa * ha)
                        + ", a unos $38 el saco de 50 kg: " + Formato.unos(economia.ahorroUreaUsd) + ".\n\n"
                        + "💰 Ganancia: lo que podrías vender, menos semillas e insumos, más el ahorro en urea: "
                        + Formato.unos(economia.margenNetoUsd) + ". No incluye mano de obra ni transporte.\n\n"
                        + "⚠️ Dinero en riesgo: depende del semáforo del mes. En verde casi no hay riesgo; "
                        + "en amarillo podrías perder cerca de un tercio de lo invertido; en rojo, casi todo. "
                        + "Para ti sería " + Formato.unos(economia.capitalEnRiesgoUsd) + ".\n\n"
                        + "Son cuentas aproximadas con datos de años anteriores: no son ganancias "
                        + "ni pérdidas seguras."));
            } else {
                texto(card, "Elige un mes arriba para ver cuánto podrías ahorrar y ganar.",
                        14, GRIS, false);
            }
        }
    }

    /** Qué hacer según cómo suele estar el suelo ese mes. */
    private String consejoDelMes(double humedad, MotorSemaforo.Resultado semaforo) {
        if (humedad > 0.78) return "👉 Revisa que el agua corra bien y no se encharque antes de sembrar.";
        if (humedad < 0.55) return parcela.isTieneRiego()
                ? "👉 Tu riego te ayuda. Revisa que funcione bien antes de sembrar."
                : "👉 Podría faltar agua. Piensa en regar o en un cultivo que aguante la sequía.";
        if (!"Verde".equals(semaforo.color)) return "👉 Revisa tu terreno con calma antes de invertir.";
        return "👉 Igual revisa tu terreno antes de comprar semillas e insumos.";
    }

    /** Selector de opciones con letra grande y fondo claro. */
    private Spinner selectorGrande(LinearLayout padre, String[] opciones) {
        Spinner selector = new Spinner(this);
        ArrayAdapter<String> adaptador = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, opciones) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextSize(17); v.setTextColor(OSCURO); v.setTypeface(null, Typeface.BOLD);
                return v;
            }
            @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                v.setTextSize(17); v.setTextColor(OSCURO); v.setMinHeight(dp(52));
                v.setPadding(dp(18), 0, dp(18), 0);
                return v;
            }
        };
        adaptador.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        selector.setAdapter(adaptador);
        selector.setBackgroundResource(R.drawable.bg_selector);
        selector.setPadding(dp(8), 0, dp(48), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56));
        lp.topMargin = dp(6); lp.bottomMargin = dp(4);
        padre.addView(selector, lp);
        return selector;
    }

    private void pasado() {
        JSONObject cantonEnso = objetoCanton(enso,"cantones");
        if (cantonEnso == null) { bloque("Aún no hay datos", "Todavía no tenemos años anteriores para tu cantón."); return; }
        texto(contenido,"¿Cómo le fue a mi zona en otros años?",19,OSCURO,true);
        texto(contenido,"El Niño suele traer más lluvia y La Niña, menos. Aquí ves qué pasó en tu zona "
                + "entre 2005 y 2025. Sirve para prepararte; no dice qué va a pasar este año.",15,GRIS,false);
        texto(contenido,"Elige qué tipo de año quieres ver",18,OSCURO,true);
        String[] opciones={"🌱 Año normal","🌧️ Año de El Niño (más lluvia)","☀️ Año de La Niña (menos lluvia)"};
        String[] claves={"neutro","nino","nina"};
        Spinner selector=selectorGrande(contenido,opciones);
        LinearLayout resultado=new LinearLayout(this);
        resultado.setOrientation(LinearLayout.VERTICAL);
        contenido.addView(resultado,new LinearLayout.LayoutParams(-1,-2));
        selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> p,View v,int pos,long id){
                resultado.removeAllViews();
                JSONObject escenarios=cantonEnso.optJSONObject("escenarios");
                JSONObject e=escenarios==null?null:escenarios.optJSONObject(claves[pos]);
                if(e==null){texto(resultado,"Todavía no tenemos datos de este tipo de año.",15,GRIS,false);return;}
                JSONArray anios=e.optJSONArray("anios_referencia");
                JSONObject indicadores=e.optJSONObject("indicadores_historicos");
                bloqueEn(resultado,"Lo que pasó en tu zona", e.optString("resumen_impacto_suelo")
                        + (anios==null?"":"\n\nAños que tomamos en cuenta: "+unirAnios(anios)));
                if(indicadores!=null) bloqueEn(resultado,"En un mes promedio de esos años",
                        "🌧️ Lluvia: unos "+Formato.numero(indicadores.optDouble("lluvia_mensual_prom_mm"))+" mm\n"
                        +"💧 Suelo "+Formato.humedadEnPalabras(indicadores.optDouble("humedad_raiz_prom"))
                        +" (humedad cerca de "+Formato.porcentaje(indicadores.optDouble("humedad_raiz_prom"))+")\n"
                        +"🌡️ Calor máximo: unos "+Formato.numero(indicadores.optDouble("temp_max_prom_c"))+" °C");
                JSONArray perfil=e.optJSONArray("perfil_mensual");
                if(perfil!=null){
                    texto(resultado,"Lluvia mes a mes",17,OSCURO,true);
                    texto(resultado,"Mientras más alta la línea, más llovió ese mes.",14,GRIS,false);
                    resultado.addView(crearGrafico(perfil,"lluvia_mm",0xFF438CB6,true),new LinearLayout.LayoutParams(-1,dp(195)));
                    texto(resultado,"Humedad del suelo mes a mes",17,OSCURO,true);
                    texto(resultado,"Mientras más alta la línea, más agua tenía la tierra.",14,GRIS,false);
                    resultado.addView(crearGrafico(perfil,"humedad_raiz",0xFF67A83C,true),new LinearLayout.LayoutParams(-1,dp(195)));
                }
                texto(resultado,"Cultivos que funcionaron en años así",18,OSCURO,true);
                JSONObject r=e.optJSONObject("recomendacion_por_parcela");
                mostrarRotacionesEn(resultado,r==null?null:r.optJSONArray(parcela.isTieneRiego()?"con_riego":"sin_riego"));
                texto(resultado,Formato.NOTA_APROXIMADO+" Datos de la NASA, la NOAA y el INIAP.",13,GRIS,false);
                boton(resultado, "Compartir este resumen", false,
                        () -> compartirResumenEnso(e,
                                opciones[pos],
                                indicadores,
                                r == null ? null : r.optJSONArray(
                                        parcela.isTieneRiego() ? "con_riego" : "sin_riego")));
            }
            @Override public void onNothingSelected(AdapterView<?> p){}
        });
        boton(contenido,"Preguntarle a Muyu  ›",true,()->mostrar(MODE_MUYU));
    }
    private void mostrarRotacionesEn(LinearLayout padre,JSONArray opciones){
        if(opciones==null){texto(padre,"Todavía no hay cultivos para mostrar.",15,GRIS,false);return;}
        for(int i=0;i<opciones.length();i++){
            JSONObject x=opciones.optJSONObject(i);if(x==null)continue;
            double ahorro=x.optDouble("ahorro_urea_kg_ha",0)*parcela.getHectareas()*MotorSemaforo.PRECIO_UREA_USD_KG;
            bloqueEn(padre,(i+1)+". "+x.optString("cultivo"),
                    Formato.evaluacionCultivo(x.optString("estado"))+"\n"
                    +"💵 Ahorro en urea: "+Formato.unos(ahorro)+" en tus "+Formato.hectareas(parcela.getHectareas())+"\n"
                    +x.optString("nota_agronomica"));
        }
    }
    /** Comparte únicamente datos históricos presentes en el JSON local, sin crear pronósticos. */
    private void compartirResumenEnso(JSONObject escenario, String nombreEscenario,
                                       JSONObject indicadores, JSONArray rotaciones) {
        StringBuilder mensaje = new StringBuilder();
        mensaje.append("🌱 Kallpa\n")
                .append("Parcela: ").append(parcela.getNombre()).append(" (")
                .append(nombreCanton()).append(", ")
                .append(Formato.hectareas(parcela.getHectareas())).append(", ")
                .append(parcela.isTieneRiego() ? "con riego" : "sin riego").append(")\n\n")
                .append(nombreEscenario.replaceAll("^\\S+\\s", "")).append(" en tu zona:\n");
        if (indicadores != null) {
            mensaje.append("• Lluvia: unos ")
                    .append(Formato.numero(indicadores.optDouble("lluvia_mensual_prom_mm")))
                    .append(" mm al mes\n")
                    .append("• Suelo ")
                    .append(Formato.humedadEnPalabras(indicadores.optDouble("humedad_raiz_prom")))
                    .append("\n")
                    .append("• Calor máximo: unos ")
                    .append(Formato.numero(indicadores.optDouble("temp_max_prom_c")))
                    .append(" °C\n\n");
        }
        if (rotaciones != null) {
            mensaje.append("Cultivos que funcionaron:\n");
            for (int i = 0; i < rotaciones.length(); i++) {
                JSONObject cultivo = rotaciones.optJSONObject(i);
                if (cultivo == null) continue;
                double kgHa = cultivo.optDouble("ahorro_urea_kg_ha", Double.NaN);
                mensaje.append(i + 1).append(". ").append(cultivo.optString("cultivo"));
                if (Double.isFinite(kgHa)) {
                    double ahorro = kgHa * parcela.getHectareas() * MotorSemaforo.PRECIO_UREA_USD_KG;
                    mensaje.append(" (ahorro en urea: ").append(Formato.unos(ahorro)).append(")");
                }
                mensaje.append("\n");
            }
        }
        mensaje.append("\nCifras aproximadas con datos de 2005 a 2025 (NASA, NOAA, INIAP, MAG). ")
                .append("No es un pronóstico ni una promesa de resultados.");
        Intent compartir = new Intent(Intent.ACTION_SEND);
        compartir.setType("text/plain");
        compartir.putExtra(Intent.EXTRA_TEXT, mensaje.toString());
        startActivity(Intent.createChooser(compartir, "Compartir por…"));
    }

    private String unirAnios(JSONArray anios) {
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < anios.length(); i++) {
            if (i > 0) texto.append(", ");
            texto.append(anios.opt(i));
        }
        return texto.toString();
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
        LinearLayout c=tarjeta(padre);texto(c,cabecera,17,OSCURO,true);texto(c,cuerpo,15,GRIS,false);
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
        b.setTextSize(16);b.setTypeface(null,Typeface.BOLD);b.setStateListAnimator(null);
        b.setOnClickListener(v->click.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);
        b.setMinHeight(dp(54));b.setPadding(dp(16),dp(10),dp(16),dp(10));
        padre.addView(b,lp);return b;
    }
    private View crearGrafico(JSONArray arr,String campo,int color,boolean meses){
        ArrayList<Float> serie=new ArrayList<>();
        if(arr!=null)for(int i=0;i<arr.length();i++){
            JSONObject x=arr.optJSONObject(i);if(x!=null)serie.add((float)x.optDouble(campo,0));
        }
        SeriesView v=new SeriesView(serie,color,meses);
        if(!meses) v.setPadding(dp(12),dp(12),dp(12),dp(12));
        return v;
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private class SeriesView extends View{
        private final ArrayList<Float> datos;private final Paint p=new Paint(3);private final int color;private final boolean meses;
        SeriesView(ArrayList<Float> valores,int color,boolean meses){super(ScienceActivity.this);this.datos=valores;this.color=color;this.meses=meses;
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
            p.setStyle(Paint.Style.FILL);p.setTextSize(dp(12));p.setColor(GRIS);
            c.drawText(f(max,2),dp(3),t+dp(6),p);c.drawText(f(min,2),dp(3),b,p);
            c.drawText(meses?"Ene":"Antes",l,b+dp(18),p);p.setTextAlign(Paint.Align.RIGHT);c.drawText(meses?"Dic":"Ahora",r,b+dp(18),p);p.setTextAlign(Paint.Align.LEFT);
        }
    }
    @Override protected void onResume(){super.onResume();if(mapa!=null)mapa.onResume();}
    @Override protected void onPause(){if(mapa!=null)mapa.onPause();super.onPause();}
    @Override protected void onDestroy(){if(mapa!=null)mapa.onDetach();super.onDestroy();}
}
