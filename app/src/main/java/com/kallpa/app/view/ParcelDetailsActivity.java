package com.kallpa.app.view;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.kallpa.app.controller.ControladorAutenticacion;
import com.kallpa.app.databinding.ActivityParcelDetailsBinding;
import com.kallpa.app.model.Parcela;
import com.kallpa.app.model.RepositorioParcelasLocal;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parcela: cartografía de referencia y límites declarados, separados de MODIS regional. */
public class ParcelDetailsActivity extends ActividadBase {
    public static final String EXTRA_ID = "detalle_parcela_id";
    private static final int GREEN = 0xFF10512A;
    private static final String ATTRIBUTION = "Esri · Maxar · Earthstar Geographics · GIS User Community";
    private ActivityParcelDetailsBinding binding;
    private Parcela parcela;
    private String correo;
    private boolean editing, satellite = true;
    private final ArrayList<GeoPoint> points = new ArrayList<>();
    private final ArrayList<Overlay> parcelOverlays = new ArrayList<>();
    private JSONArray observations = new JSONArray();
    private JSONObject summary, sources;
    private int firstIndex, lastIndex;
    private SharedPreferences preferences;
    private String limitsKey;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ControladorAutenticacion auth = new ControladorAutenticacion(this);
        if (!auth.sesionActiva() || auth.usuarioActual() == null) {
            startActivity(new Intent(this, LoginActivity.class)); finish(); return;
        }
        correo = auth.usuarioActual().getCorreo();
        String id = getIntent().getStringExtra(EXTRA_ID);
        List<Parcela> parcelas = new RepositorioParcelasLocal(this).obtenerPorUsuario(correo);
        for (Parcela p : parcelas) if (p.getId().equals(id)) { parcela = p; break; }
        if (parcela == null) { mensaje("No encontramos esa parcela."); finish(); return; }
        preferences = getSharedPreferences("kallpa_limites_parcelas", MODE_PRIVATE);
        limitsKey = correo.trim().toLowerCase(Locale.ROOT) + ":" + parcela.getId();
        initializeMapStorage();
        binding = ActivityParcelDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.txtName.setText(parcela.getNombre() + " · " + nombreCanton(parcela.getCanton()));
        configureMap();
        readLimits(preferences.getString(limitsKey, "[]"));
        if (state != null) {
            readLimits(state.getString("draft", "[]"));
            editing = state.getBoolean("editing");
            satellite = state.getBoolean("satellite", true);
        }
        setMapSource();
        redrawLimits();
        centerMap();
        if (state != null) {
            binding.parcelMap.getController().setZoom(state.getDouble("zoom", 16));
            binding.parcelMap.getController().setCenter(new GeoPoint(state.getDouble("lat", parcela.getLatitud()),
                    state.getDouble("lon", parcela.getLongitud())));
        }
        updateEditing();
        loadCoverage();
        firstIndex = state == null ? 0 : state.getInt("first", 0);
        lastIndex = state == null ? observations.length() - 1 : state.getInt("last", observations.length() - 1);
        showComparison();
        standardizeSections();
        binding.btnBack.setOnClickListener(v -> onBackPressed());
        binding.btnLayers.setOnClickListener(v -> chooseLayer());
        binding.btnMapType.setOnClickListener(v -> chooseLayer());
        binding.btnCenterMap.setOnClickListener(v -> centerMap());
        binding.btnZoomIn.setOnClickListener(v -> binding.parcelMap.getController().zoomIn());
        binding.btnZoomOut.setOnClickListener(v -> binding.parcelMap.getController().zoomOut());
        binding.btnEditLimits.setOnClickListener(v -> {
            if (editing) confirmDiscard();
            else {
                editing = true; updateEditing();
                binding.parcelScroll.smoothScrollTo(0, binding.mapFrame.getTop());
                mensaje("Toca los vértices en orden alrededor de tu terreno.");
            }
        });
        binding.btnUndoPoint.setOnClickListener(v -> {
            if (!points.isEmpty()) { points.remove(points.size() - 1); redrawLimits(); updateEditing(); }
        });
        binding.btnClearPoints.setOnClickListener(v -> {
            new DialogoKallpa(this).setTitle("Volver a dibujar")
                    .setMessage("Se limpiará el dibujo en edición. El límite guardado se conserva hasta que pulses Guardar.")
                    .setNegativeButton("Volver", null).setPositiveButton("Reiniciar", (d,w) -> {
                        points.clear(); redrawLimits(); updateEditing();
                    }).show();
        });
        binding.btnSaveLimits.setOnClickListener(v -> saveLimits());
        binding.btnCompareDates.setOnClickListener(v -> chooseDates());
        binding.btnCoverageSources.setOnClickListener(v -> showSources());
        binding.btnParcelData.setOnClickListener(v -> showParcelData());
        binding.btnSelect.setOnClickListener(v -> {
            if (editing) { mensaje("Guarda o cancela los límites antes de cambiar de parcela."); return; }
            getSharedPreferences("kallpa_seleccion", MODE_PRIVATE).edit()
                    .putString("parcela_activa_" + correo.toLowerCase(Locale.ROOT), parcela.getId()).apply();
            startActivity(new Intent(this, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
    }

    private void initializeMapStorage() {
        File root = new File(getFilesDir(), "osmdroid"); root.mkdirs();
        File cache = new File(getCacheDir(), "osmdroid/tiles"); cache.mkdirs();
        Configuration.getInstance().setOsmdroidBasePath(root);
        Configuration.getInstance().setOsmdroidTileCache(cache);
        Configuration.getInstance().setUserAgentValue(getPackageName() + " Kallpa/1.0");
    }

    private void configureMap() {
        binding.parcelMap.setMultiTouchControls(true);
        binding.parcelMap.setBuiltInZoomControls(false);
        binding.parcelMap.setMinZoomLevel(4.0);
        binding.parcelMap.setMaxZoomLevel(19.0);
        binding.parcelMap.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (!editing) return false;
                if (points.size() >= 60) { mensaje("Máximo 60 vértices por terreno."); return true; }
                if (p.distanceToAsDouble(location()) > 20000) {
                    mensaje("Marca el terreno a menos de 20 km de la ubicación registrada."); return true;
                }
                if (!points.isEmpty() && p.distanceToAsDouble(points.get(points.size()-1)) < 1) return true;
                points.add(p); redrawLimits(); updateEditing(); return true;
            }
            @Override public boolean longPressHelper(GeoPoint p) { return singleTapConfirmedHelper(p); }
        }));
        ScaleBarOverlay scale = new ScaleBarOverlay(binding.parcelMap);
        scale.setAlignBottom(true);
        scale.setScaleBarOffset(dp(16), dp(14));
        scale.setTextPaint(new Paint(Paint.ANTI_ALIAS_FLAG));
        scale.getTextPaint().setColor(Color.WHITE);
        scale.getTextPaint().setTextSize(dp(11));
        scale.getBarPaint().setColor(Color.WHITE);
        binding.parcelMap.getOverlays().add(scale);
        // El mapa captura el gesto, el resto de la pantalla sigue siendo desplazable.
        binding.parcelMap.setOnTouchListener((v,event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(event.getActionMasked() != MotionEvent.ACTION_UP
                    && event.getActionMasked() != MotionEvent.ACTION_CANCEL);
            return false;
        });
    }

    private void setMapSource() {
        if (satellite) {
            binding.parcelMap.setTileSource(new XYTileSource("KallpaEsriWorldImagery", 0, 19, 256, ".jpg",
                    new String[]{"https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"}, ATTRIBUTION) {
                @Override public String getTileURLString(long tileIndex) {
                    return getBaseUrl() + MapTileIndex.getZoom(tileIndex) + "/"
                            + MapTileIndex.getY(tileIndex) + "/" + MapTileIndex.getX(tileIndex);
                }
            });
        } else binding.parcelMap.setTileSource(TileSourceFactory.MAPNIK);
        binding.btnMapType.setText(satellite ? "Vista satelital  ⌄" : "Mapa de caminos  ⌄");
        binding.txtAttribution.setText(satellite ? ATTRIBUTION : "© OpenStreetMap contributors");
    }

    private void chooseLayer() {
        new DialogoKallpa(this).setTitle("Capa del mapa")
                .setSingleChoiceItems(new String[]{"Vista satelital · Esri", "Caminos · OpenStreetMap"}, satellite ? 0 : 1,
                        (d,w) -> { satellite = w == 0; setMapSource(); d.dismiss(); })
                .setNegativeButton("Cerrar",null).show();
    }

    private GeoPoint location() { return new GeoPoint(parcela.getLatitud(), parcela.getLongitud()); }
    private void centerMap() {
        if (points.size() >= 3) {
            binding.parcelMap.post(() -> {
                if (!isFinishing() && !isDestroyed())
                    binding.parcelMap.zoomToBoundingBox(org.osmdroid.util.BoundingBox.fromGeoPoints(points), false, dp(64), 18.0, null);
            });
        } else {
            binding.parcelMap.getController().setZoom(16.5);
            binding.parcelMap.getController().setCenter(location());
        }
    }

    private void redrawLimits() {
        binding.parcelMap.getOverlays().removeAll(parcelOverlays); parcelOverlays.clear();
        binding.txtMapParcel.setVisibility(View.GONE);
        if (points.size() >= 3) {
            Polygon polygon = new Polygon(binding.parcelMap);
            polygon.setPoints(points);
            polygon.getFillPaint().setColor(0x6657A60B);
            polygon.getOutlinePaint().setColor(0xFFB8FF68);
            polygon.getOutlinePaint().setStrokeWidth(dp(3));
            polygon.setOnClickListener((p,m,g) -> false);
            parcelOverlays.add(polygon);
            // Etiqueta anclada al centro del polígono; se desplaza con el mapa.
            double lat=0,lon=0;
            for (GeoPoint p : points) { lat+=p.getLatitude(); lon+=p.getLongitude(); }
            Marker label = new Marker(binding.parcelMap);
            label.setPosition(new GeoPoint(lat/points.size(),lon/points.size()));
            label.setIcon(parcelLabel());
            label.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            label.setOnMarkerClickListener((m,v) -> false);
            parcelOverlays.add(label);
        }
        for (GeoPoint point : points) {
            Marker marker = new Marker(binding.parcelMap);
            marker.setPosition(point);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL); dot.setColor(0xFF71B62C);
            dot.setStroke(dp(3), Color.WHITE); dot.setSize(dp(15),dp(15));
            marker.setIcon(dot); marker.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER);
            marker.setOnMarkerClickListener((m,v) -> false);
            parcelOverlays.add(marker);
        }
        if (points.isEmpty()) {
            Marker pin = new Marker(binding.parcelMap);
            pin.setPosition(location()); pin.setTitle(parcela.getNombre());
            pin.setSnippet("Ubicación registrada · Dibuja los límites para ver el contorno.");
            parcelOverlays.add(pin);
        }
        binding.parcelMap.getOverlays().addAll(parcelOverlays);
        binding.parcelMap.invalidate();
    }

    private BitmapDrawable parcelLabel() {
        String name=parcela.getNombre();
        if(name.length()>22) name=name.substring(0,21)+"…";
        String surface=number(parcela.getHectareas(),2)+" ha declaradas";
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(dp(15));p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        int width=(int)Math.ceil(Math.max(p.measureText(name),p.measureText(surface)))+dp(24);
        Bitmap bitmap=Bitmap.createBitmap(width,dp(60),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bitmap);p.setColor(0x66134622);c.drawRoundRect(0,0,width,dp(60),dp(10),dp(10),p);
        p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setShadowLayer(dp(2),0,dp(1),0x99000000);
        c.drawText(name,width/2f,dp(25),p);c.drawText(surface,width/2f,dp(46),p);
        return new BitmapDrawable(getResources(),bitmap);
    }

    private void updateEditing() {
        binding.editToolbar.setVisibility(editing ? View.VISIBLE : View.GONE);
        binding.btnEditLimits.setText(editing ? "Cancelar edición" : "Editar límites");
        binding.txtMapStatus.setText(editing
                ? "Toca las esquinas en orden · " + points.size() + " vértices · Mínimo 3 para guardar."
                : (points.isEmpty() ? "Marca los límites para ver el contorno de tu terreno. " : "Límites que registraste como referencia de tu terreno. ")
                  + "El fondo satelital necesita internet o imágenes ya consultadas en caché.");
    }

    private void saveLimits() {
        if (!LimitesParcela.validos(coordinates())) {
            mensaje("Dibuja al menos 3 puntos distintos sin cruzar los bordes ni formar una línea."); return;
        }
        if (!preferences.edit().putString(limitsKey, serializePoints()).commit()) {
            mensaje("No se pudieron guardar los límites. Inténtalo de nuevo."); return;
        }
        editing = false; updateEditing(); centerMap(); mensaje("Límites guardados en este teléfono.");
    }

    private double[][] coordinates() {
        double[][] result = new double[points.size()][2];
        for (int i=0;i<points.size();i++) {
            result[i][0]=points.get(i).getLongitude(); result[i][1]=points.get(i).getLatitude();
        }
        return result;
    }
    private String serializePoints() {
        JSONArray result = new JSONArray();
        for (GeoPoint p : points) result.put(new JSONArray().put((Object) p.getLatitude()).put((Object) p.getLongitude()));
        return result.toString();
    }
    private void readLimits(String json) {
        points.clear();
        try {
            JSONArray array = new JSONArray(json);
            if (array.length() > 60) throw new IllegalArgumentException();
            for (int i=0;i<array.length();i++) {
                JSONArray p = array.getJSONArray(i);
                double lat=p.getDouble(0),lon=p.getDouble(1);
                if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -85 || lat > 85 || lon < -180 || lon > 180)
                    throw new IllegalArgumentException();
                points.add(new GeoPoint(lat,lon));
            }
        } catch (Exception e) { points.clear(); }
    }
    private void confirmDiscard() {
        new DialogoKallpa(this).setTitle("¿Descartar los cambios?")
                .setMessage("Se conservarán los últimos límites guardados.")
                .setNegativeButton("Seguir editando",null).setPositiveButton("Descartar",(d,w) -> {
                    readLimits(preferences.getString(limitsKey,"[]")); editing=false; redrawLimits(); updateEditing();
                }).show();
    }
    @Override public void onBackPressed() {
        if (editing) confirmDiscard(); else super.onBackPressed();
    }

    private void loadCoverage() {
        try (InputStream in=getAssets().open("datos/evidencia_satelital_parcelas.json");
             ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[8192]; int n;
            while ((n=in.read(b))!=-1) out.write(b,0,n);
            JSONObject root=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
            sources=root.optJSONObject("fuentes");
            JSONObject zone=root.getJSONObject("parcelas_satelitales")
                    .getJSONObject(parcela.getCanton().toLowerCase(Locale.ROOT).trim());
            summary=zone.getJSONObject("resumen_actual");
            JSONArray raw=zone.optJSONArray("serie_ndvi_modis");
            // Solo registros con fecha y NDVI válidos, ordenados cronológicamente.
            ArrayList<JSONObject> ordered=new ArrayList<>();
            if(raw!=null) for(int i=0;i<raw.length();i++) {
                JSONObject row=raw.optJSONObject(i);
                if(row==null || !Double.isFinite(row.optDouble("ndvi",Double.NaN))) continue;
                try { LocalDate.parse(row.getString("fecha")); ordered.add(row); } catch(Exception ignored) { }
            }
            ordered.sort((a,c)->a.optString("fecha").compareTo(c.optString("fecha")));
            observations=new JSONArray(); for(JSONObject row:ordered) observations.put(row);
            String state=summary.optString("estado_cobertura","");
            String lower=state.toLowerCase(Locale.ROOT);
            String friendly=lower.contains("descubierto") ? "Poca cobertura vegetal"
                    : lower.contains("parcial") ? "Cobertura parcial" : "Cobertura observada";
            binding.txtCoverageState.setText(friendly);
            binding.txtCoverageDescription.setText(state + ".\nLas observaciones se presentan a nivel cantonal para acompañar el cuidado de tu terreno.");
            TextoDato.aplicar(binding.txtObservationDate,"Última observación NDVI: " + date(summary.optString("fecha_ndvi","")),false);
        } catch (Exception error) {
            summary=null;
            binding.txtCoverageState.setText("Sin observación disponible");
            binding.txtCoverageDescription.setText("Consulta la ubicación y edita los límites. Las observaciones cantonales se muestran cuando hay datos disponibles.");
            TextoDato.aplicar(binding.txtObservationDate,"Fecha de observación: pendiente",false);
        }
    }

    private void showSources() {
        String message = summary == null ? "No hay observaciones disponibles para esta zona."
                : "NDVI: " + number(summary.optDouble("ndvi_ultimo"),3)
                + "\nFecha: " + date(summary.optString("fecha_ndvi"))
                + "\nFuente: " + (sources==null?"NASA MODIS MOD13Q1":sources.optString("cobertura_vegetal"))
                + "\nEscala: 250 m.\n\nHumedad superficial: " + number(summary.optDouble("smap_humedad_ultima"),3)
                + " m³/m³\nFecha: " + date(summary.optString("fecha_smap"))
                + "\nFuente: " + (sources==null?"NASA SMAP":sources.optString("humedad_superficial"))
                + "\nEscala regional: aproximadamente 9 km.";
        new DialogoKallpa(this).setTitle("Datos y fuentes").setMessage(message
                + "\n\nAlcance: estimaciones a nivel cantonal.\nMapa base: imágenes de Esri con fecha de captura independiente de las observaciones NASA.\nSuperficie: hectáreas declaradas en el registro.")
                .setPositiveButton("Entendido",null).show();
    }

    private void showComparison() {
        binding.comparisonCards.removeAllViews(); binding.chartContainer.removeAllViews();
        if(observations.length()<2) {
            binding.comparisonCards.addView(label("No hay suficientes observaciones para comparar.",14,GREEN));
            binding.btnCompareDates.setEnabled(false); return;
        }
        firstIndex=Math.max(0,Math.min(firstIndex,observations.length()-2));
        lastIndex=Math.max(firstIndex+1,Math.min(lastIndex,observations.length()-1));
        comparisonCard(observations.optJSONObject(firstIndex));
        TextView arrow=label("→",26,0xFF5A951F); arrow.setPadding(dp(10),0,dp(10),0);
        binding.comparisonCards.addView(arrow);
        comparisonCard(observations.optJSONObject(lastIndex));
        binding.chartContainer.addView(new CoverageChart(), new LinearLayout.LayoutParams(-1,-1));
    }
    private void comparisonCard(JSONObject row) {
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(12),dp(16),dp(12),dp(14));
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFE6F0C9,0xFFC6E48D}); bg.setCornerRadius(dp(16)); card.setBackground(bg);
        TextView caption=label("NDVI · ZONA",10,0xFF49683A); TextoDato.aplicar(caption,caption.getText(),true);
        TextView value=label(number(row.optDouble("ndvi"),3),26,GREEN); value.setTypeface(null,0);
        TextView date=label(date(row.optString("fecha")),11,GREEN); date.setPadding(0,dp(12),0,0);
        card.addView(caption); card.addView(value); card.addView(date);
        binding.comparisonCards.addView(card,new LinearLayout.LayoutParams(0,-2,1));
    }
    private void chooseDates() {
        if(observations.length()<2) return;
        String[] dates=new String[observations.length()];
        for(int i=0;i<dates.length;i++) dates[i]=date(observations.optJSONObject(i).optString("fecha"));
        new DialogoKallpa(this).setTitle("Primera observación")
                .setItems(dates,(d,first)-> {
                    if(first==dates.length-1) { mensaje("Elige una fecha anterior a la última observación."); return; }
                    String[] following=java.util.Arrays.copyOfRange(dates,first+1,dates.length);
                    new DialogoKallpa(this).setTitle("Comparar con")
                            .setItems(following,(next,last)-> { firstIndex=first; lastIndex=first+1+last; showComparison(); })
                            .setNegativeButton("Cancelar",null).show();
                }).setNegativeButton("Cancelar",null).show();
    }

    private class CoverageChart extends View {
        private final Paint paint=new Paint(3);
        CoverageChart(){ super(ParcelDetailsActivity.this); setContentDescription("Serie temporal regional NDVI, con las dos fechas seleccionadas resaltadas."); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float l=dp(30),r=getWidth()-dp(12),t=dp(12),b=getHeight()-dp(28);
            if(r<=l||b<=t) return;
            double min=1,max=-1;
            for(int i=0;i<observations.length();i++){double v=observations.optJSONObject(i).optDouble("ndvi");min=Math.min(min,v);max=Math.max(max,v);}
            if(max-min<.05){max+=.03;min-=.03;}
            paint.setStrokeWidth(dp(1));paint.setColor(0xFFDEE6D3);
            for(int i=0;i<3;i++){float y=t+(b-t)*i/2;c.drawLine(l,y,r,y,paint);}
            paint.setTextSize(dp(9));paint.setColor(0xFF68725C);
            c.drawText(number(max,2),0,t+dp(4),paint);c.drawText(number(min,2),0,b,paint);
            Path path=new Path();
            long start=epoch(0),end=epoch(observations.length()-1);
            for(int i=0;i<observations.length();i++) {
                float x=l+(r-l)*(epoch(i)-start)/Math.max(1f,end-start);
                float y=b-(float)((observations.optJSONObject(i).optDouble("ndvi")-min)/(max-min))*(b-t);
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            paint.setColor(0xFF5B9627);paint.setStrokeWidth(dp(2));paint.setStyle(Paint.Style.STROKE);c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
            for(int i:new int[]{firstIndex,lastIndex}) {
                float x=l+(r-l)*(epoch(i)-start)/Math.max(1f,end-start);
                float y=b-(float)((observations.optJSONObject(i).optDouble("ndvi")-min)/(max-min))*(b-t);
                paint.setColor(Color.WHITE);c.drawCircle(x,y,dp(6),paint);paint.setColor(GREEN);c.drawCircle(x,y,dp(4),paint);
            }
            paint.setColor(0xFF68725C);
            c.drawText(date(observations.optJSONObject(0).optString("fecha")),l,b+dp(20),paint);
            String last=date(observations.optJSONObject(observations.length()-1).optString("fecha"));
            c.drawText(last,r-paint.measureText(last),b+dp(20),paint);
        }
        private long epoch(int i){return LocalDate.parse(observations.optJSONObject(i).optString("fecha")).toEpochDay();}
    }
    private TextView label(String text,int size,int color) {
        TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);return v;
    }
    private void standardizeSections() {
        LinearLayout cobertura=(LinearLayout)binding.txtCoverageState.getParent();
        cobertura.removeViewAt(0);
        SeccionDesplegable.envolver(cobertura,"detalle_cobertura","Cobertura del terreno",true);
        LinearLayout evolucion=(LinearLayout)binding.comparisonCards.getParent();
        evolucion.removeViewAt(0);
        android.view.ViewGroup fila=(android.view.ViewGroup)binding.btnCompareDates.getParent();
        fila.removeView(binding.btnCompareDates);
        LinearLayout.LayoutParams accion=new LinearLayout.LayoutParams(-1,dp(56));accion.topMargin=dp(12);
        evolucion.addView(binding.btnCompareDates,accion);
        SeccionDesplegable.envolver(evolucion,"detalle_evolucion","Evolución de la cobertura",false);
        binding.btnParcelData.setVisibility(View.GONE);
        LinearLayout pagina=(LinearLayout)binding.parcelScroll.getChildAt(0);
        LinearLayout datos=SeccionDesplegable.crear(pagina,"detalle_datos","Datos de tu parcela",parcela.getNombre(),false);
        TextView texto=label("",15,GREEN);TextoDato.aplicar(texto,parcelDataText(),false);texto.setLineSpacing(dp(5),1);
        datos.addView(texto,new LinearLayout.LayoutParams(-1,-2));
    }
    private String parcelDataText() {
        return String.format(Locale.forLanguageTag("es-EC"),
                "Cultivo: %s\nÚltima siembra: %s\nCosecha: %s\nRiego: %s\nSuperficie declarada: %.2f ha\nCantón: %s\nLatitud: %.6f\nLongitud: %.6f",
                parcela.getCultivoActual(),parcela.getFechaSiembra(),parcela.getFechaCosecha(),parcela.isTieneRiego()?"Sí":"No",
                parcela.getHectareas(),nombreCanton(parcela.getCanton()),parcela.getLatitud(),parcela.getLongitud());
    }
    private void showParcelData() {
        new DialogoKallpa(this).setTitle("Datos de tu parcela").setMessage(parcelDataText()).setPositiveButton("Cerrar",null).show();
    }
    private String date(String iso) {
        try{return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy",Locale.forLanguageTag("es-EC")));}
        catch(Exception e){return "Sin fecha";}
    }
    private String number(double n,int decimals){return String.format(Locale.forLanguageTag("es-EC"),"%."+decimals+"f",n);}
    private String nombreCanton(String c) {
        if("daule".equalsIgnoreCase(c))return "Daule, Guayas";
        if("babahoyo".equalsIgnoreCase(c))return "Babahoyo, Los Ríos";
        return "Cantón por confirmar";
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("draft",serializePoints());state.putBoolean("editing",editing);state.putBoolean("satellite",satellite);
        state.putInt("first",firstIndex);state.putInt("last",lastIndex);
        state.putDouble("zoom",binding.parcelMap.getZoomLevelDouble());
        state.putDouble("lat",binding.parcelMap.getMapCenter().getLatitude());state.putDouble("lon",binding.parcelMap.getMapCenter().getLongitude());
    }
    @Override protected void onResume(){super.onResume();if(binding!=null)binding.parcelMap.onResume();}
    @Override protected void onPause(){if(binding!=null)binding.parcelMap.onPause();super.onPause();}
    @Override protected void onDestroy(){if(binding!=null)binding.parcelMap.onDetach();super.onDestroy();}
}
