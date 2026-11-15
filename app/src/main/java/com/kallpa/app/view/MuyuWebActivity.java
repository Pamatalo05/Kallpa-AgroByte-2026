package com.kallpa.app.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.webkit.WebViewAssetLoader;

import com.kallpa.app.R;
import com.kallpa.app.controller.ControladorAutenticacion;
import com.kallpa.app.model.Parcela;
import com.kallpa.app.model.RepositorioParcelasLocal;

import org.json.JSONObject;

import java.io.InputStream;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Muyu completo (animado, con Gemini y guardrails) dentro de Kallpa.
 *
 * La web de Muyu se compila con `npm run build:kallpa` en el repo MUYU-Chatbot-Agrobyte
 * y queda en assets/muyu/. Se abre con WebViewAssetLoader (https://appassets.androidplatform.net),
 * que es la forma segura de cargar archivos locales en un WebView.
 *
 * Puente "KallpaAndroid" para la web:
 *  - contexto(): parcela activa en JSON
 *  - hablar(texto) / callar(): voz del teléfono (el WebView no trae voz)
 *  - cerrar(): vuelve a Kallpa
 */
public class MuyuWebActivity extends ActividadBase {
    private static final String DOMINIO = "appassets.androidplatform.net";
    private static final String INICIO = "https://" + DOMINIO + "/assets/muyu/index.html";

    private WebView web;
    private MuyuApi api;
    private TextToSpeech voz;
    private boolean vozLista;

    /** true si la app trae a Muyu compilado en assets/muyu/. */
    public static boolean disponible(Context contexto) {
        try (InputStream ignorado = contexto.getAssets().open("muyu/index.html")) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundResource(R.drawable.bg_screen);
        web = new WebView(this);
        raiz.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(raiz);

        WebSettings ajustes = web.getSettings();
        ajustes.setJavaScriptEnabled(true);
        ajustes.setDomStorageEnabled(true);
        // Seguridad: la página solo lee archivos a través del cargador de assets.
        ajustes.setAllowFileAccess(false);
        ajustes.setAllowContentAccess(false);
        ajustes.setMediaPlaybackRequiresUserGesture(false);

        WebViewAssetLoader cargador = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView vista, WebResourceRequest pedido) {
                return cargador.shouldInterceptRequest(pedido.getUrl());
            }

            @Override public boolean shouldOverrideUrlLoading(WebView vista, WebResourceRequest pedido) {
                Uri url = pedido.getUrl();
                if ("https".equals(url.getScheme()) && DOMINIO.equals(url.getHost()) && url.getPath() != null && url.getPath().startsWith("/assets/muyu/")) return false;
                if (!"https".equals(url.getScheme()) && !"http".equals(url.getScheme())) return true;
                // Cualquier enlace externo se abre en el navegador, nunca dentro de Muyu.
                startActivity(new Intent(Intent.ACTION_VIEW, url));
                return true;
            }
        });
        api = new MuyuApi((id, estado, cuerpo, error) -> {
            JSONObject dato = new JSONObject();
            try {
                dato.put("id", id); dato.put("status", estado);
                dato.put("cuerpo", cuerpo); dato.put("error", error);
            } catch (Exception ignored) { return; }
            runOnUiThread(() -> {
                if (web != null && !isFinishing() && !isDestroyed())
                    web.evaluateJavascript("window.dispatchEvent(new CustomEvent('muyu-respuesta',{detail:" + dato.toString() + "}))", null);
            });
        });
        web.addJavascriptInterface(new Puente(), "KallpaAndroid");

        voz = new TextToSpeech(this, estado -> {
            if (estado != TextToSpeech.SUCCESS) return;
            int r = voz.setLanguage(Locale.forLanguageTag("es-EC"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                voz.setLanguage(Locale.forLanguageTag("es"));
            }
            voz.setPitch(1.15f);
            voz.setSpeechRate(0.95f);
            vozLista = true;
        });

        voz.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { notificarVoz(true); }
            @Override public void onDone(String id) { notificarVoz(false); }
            @Override public void onError(String id) { notificarVoz(false); }
            @Override public void onStop(String id, boolean interrumpida) { notificarVoz(false); }
        });
        web.loadUrl(INICIO);
    }

    private void notificarVoz(boolean hablando) {
        runOnUiThread(() -> {
            if (web != null && !isFinishing() && !isDestroyed())
                web.evaluateJavascript("window.dispatchEvent(new CustomEvent('muyu-voz',{detail:" + hablando + "}))", null);
        });
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override protected void onPause() {
        if (voz != null) voz.stop();
        notificarVoz(false);
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (api != null) api.close();
        if (voz != null) voz.shutdown();
        if (web != null) {
            web.removeJavascriptInterface("KallpaAndroid");
            web.destroy();
        }
        super.onDestroy();
    }

    /** Parcela activa, la misma que muestra Inicio. */
    private JSONObject contextoParcela() {
        JSONObject json = new JSONObject();
        try {
            ControladorAutenticacion auth = new ControladorAutenticacion(this);
            if (!auth.sesionActiva() || auth.usuarioActual() == null) return json;
            String correo = auth.usuarioActual().getCorreo();
            List<Parcela> parcelas = new RepositorioParcelasLocal(this).obtenerPorUsuario(correo);
            if (parcelas.isEmpty()) return json;
            String id = getSharedPreferences("kallpa_seleccion", MODE_PRIVATE)
                    .getString("parcela_activa_" + correo.toLowerCase(Locale.ROOT), "");
            Parcela activa = parcelas.get(0);
            for (Parcela p : parcelas) if (p.getId().equals(id)) { activa = p; break; }
            json.put("cantonId", activa.getCanton() == null ? "" : activa.getCanton());
            json.put("cultivo", activa.getCultivoActual());
            json.put("nombreParcela", activa.getNombre());
            json.put("hectareas", activa.getHectareas());
            json.put("conRiego", activa.isTieneRiego());
            // Mes de siembra elegido en "Siembra" (si no, el mes actual).
            int elegido = getSharedPreferences("kallpa_ui", MODE_PRIVATE).getInt("mes_siembra", 0);
            json.put("mes", elegido >= 1 && elegido <= 12 ? elegido : Calendar.getInstance().get(Calendar.MONTH) + 1);
        } catch (Exception ignorado) {
            // Sin contexto, Muyu usa su parcela por defecto.
        }
        return json;
    }

    /** Lo que la web de Muyu puede pedirle a Kallpa. Solo se expone a la página local. */
    private final class Puente {
        @JavascriptInterface public void solicitar(String id, String metodo, String cuerpo) {
            if (api != null) api.solicitar(id, metodo, cuerpo);
        }
        @JavascriptInterface public void cancelar(String id) {
            if (api != null) api.cancelar(id);
        }

        @JavascriptInterface public String contexto() {
            return contextoParcela().toString();
        }

        @JavascriptInterface public void hablar(String texto) {
            if (!vozLista || texto == null) return;
            runOnUiThread(() -> voz.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "muyu"));
        }

        @JavascriptInterface public void callar() {
            if (voz != null) runOnUiThread(voz::stop);
        }

        @JavascriptInterface public void navegar(String destino) {
            runOnUiThread(() -> {
                Intent intent;
                if (destino == null) return;
                switch (destino) {
                    case "inicio": intent = new Intent(MuyuWebActivity.this, HomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP); break;
                    case "parcelas": intent = new Intent(MuyuWebActivity.this, MyParcelsActivity.class); break;
                    case "parcela": intent = new Intent(MuyuWebActivity.this, ScienceActivity.class)
                        .putExtra(ScienceActivity.EXTRA_MODE, ScienceActivity.MODE_PRESENT); break;
                    case "siembra": intent = new Intent(MuyuWebActivity.this, ScienceActivity.class)
                        .putExtra(ScienceActivity.EXTRA_MODE, ScienceActivity.MODE_FUTURE); break;
                    case "clima": intent = new Intent(MuyuWebActivity.this, SupportActivity.class)
                        .putExtra(SupportActivity.EXTRA_MODE, SupportActivity.MODE_ALERTS); break;
                    case "bitacora": intent = new Intent(MuyuWebActivity.this, CompanionActivity.class)
                        .putExtra(CompanionActivity.EXTRA_MODE, CompanionActivity.MODE_LOG); break;
                    default: return;
                }
                if (voz != null) voz.stop();
                startActivity(intent);
                finish();
            });
        }

        @JavascriptInterface public void cerrar() {
            runOnUiThread(MuyuWebActivity.this::finish);
        }
    }
}
