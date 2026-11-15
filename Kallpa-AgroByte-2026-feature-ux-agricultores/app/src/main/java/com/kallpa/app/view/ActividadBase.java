package com.kallpa.app.view;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.kallpa.app.R;

/**
 * Base de todas las pantallas de Kallpa:
 * - deja espacio para la barra de estado y la de navegación del teléfono
 *   (desde Android 15 la app se dibuja por debajo de ellas),
 * - muestra avisos y explicaciones con el estilo de Kallpa,
 * - maneja la barra inferior común.
 */
public abstract class ActividadBase extends AppCompatActivity {
    protected static final int SECCION_INICIO = 0;
    protected static final int SECCION_PARCELA = 1;
    protected static final int SECCION_SIEMBRA = 2;
    protected static final int SECCION_CLIMA = 3;
    protected static final int SECCION_MUYU = 4;

    private static final int[] IDS_BARRA = {
            R.id.navInicio, R.id.navParcela, R.id.navSiembra, R.id.navClima };
    private static final String PREFS_UI = "kallpa_ui";
    private static final long DURACION_AVISO_MS = 4000;

    private static final int AVISO_INFO = 0, AVISO_EXITO = 1, AVISO_ERROR = 2;

    private View avisoActual;

    // Si la pantalla se cierra justo después de un aviso ("¡Cuenta creada!" y luego otra pantalla),
    // la siguiente pantalla lo muestra para que no se pierda.
    private static String avisoPendiente;
    private static int tipoPendiente;
    private static long momentoPendiente;
    private static int duenioPendiente;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // La app ocupa toda la pantalla; los íconos del sistema se ven oscuros sobre el fondo crema.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat barras =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        barras.setAppearanceLightStatusBars(true);
        barras.setAppearanceLightNavigationBars(true);
    }

    /** Se llama después de cada setContentView: reserva el espacio de las barras del sistema. */
    @Override public void onContentChanged() {
        super.onContentChanged();
        ViewGroup contenido = findViewById(android.R.id.content);
        if (contenido == null || contenido.getChildCount() == 0) return;
        View raiz = contenido.getChildAt(0);
        View barra = raiz.findViewById(R.id.barraInferior);
        final int izq = raiz.getPaddingLeft(), arriba = raiz.getPaddingTop();
        final int der = raiz.getPaddingRight(), abajo = raiz.getPaddingBottom();
        final int barraAbajo = barra == null ? 0 : barra.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(raiz, (v, insets) -> {
            Insets sistema = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            int teclado = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            boolean conTeclado = teclado > 0;
            if (barra != null) {
                // Con el teclado abierto la barra se oculta para dar espacio a lo que se escribe.
                barra.setVisibility(conTeclado ? View.GONE : View.VISIBLE);
                barra.setPadding(barra.getPaddingLeft(), barra.getPaddingTop(),
                        barra.getPaddingRight(), barraAbajo + (conTeclado ? 0 : sistema.bottom));
            }
            int espacioAbajo = conTeclado ? teclado : (barra != null ? 0 : sistema.bottom);
            v.setPadding(izq + sistema.left, arriba + sistema.top,
                    der + sistema.right, abajo + espacioAbajo);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(raiz);
    }

    /** Alterna el campo sin perder la posición del cursor. */
    protected void alternarClave(android.widget.EditText campo) {
        boolean oculta = campo.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod;
        campo.setTransformationMethod(oculta ? null : android.text.method.PasswordTransformationMethod.getInstance());
        campo.setSelection(campo.length());
    }

    // ---------------------------------------------------------------- Avisos

    /** Aviso amarillo: falta un dato o hay algo que revisar. */
    protected void mensaje(String texto) { avisar(texto, AVISO_INFO); }

    /** Aviso verde: algo salió bien. */
    protected void avisoExito(String texto) { avisar(texto, AVISO_EXITO); }

    /** Aviso rojo suave: algo no se pudo hacer. */
    protected void avisoError(String texto) { avisar(texto, AVISO_ERROR); }

    @Override protected void onResume() {
        super.onResume();
        boolean reciente = System.currentTimeMillis() - momentoPendiente < 2500;
        if (avisoPendiente != null && reciente && duenioPendiente != System.identityHashCode(this)) {
            String texto = avisoPendiente;
            avisoPendiente = null;
            avisar(texto, tipoPendiente);
        }
    }

    private void avisar(String texto, int tipo) {
        avisoPendiente = texto;
        tipoPendiente = tipo;
        momentoPendiente = System.currentTimeMillis();
        duenioPendiente = System.identityHashCode(this);
        if (tipo == AVISO_EXITO) {
            mostrarAviso(texto, R.drawable.bg_aviso_ok, R.drawable.ic_aviso_ok, Color.WHITE);
        } else if (tipo == AVISO_ERROR) {
            mostrarAviso(texto, R.drawable.bg_aviso_error, R.drawable.ic_aviso_error,
                    getColor(R.color.aviso_error_text));
        } else {
            mostrarAviso(texto, R.drawable.bg_aviso_info, R.drawable.ic_aviso_info,
                    getColor(R.color.aviso_info_text));
        }
    }

    private void mostrarAviso(String texto, int fondo, int icono, int colorTexto) {
        ViewGroup contenido = findViewById(android.R.id.content);
        if (contenido == null) return;
        if (avisoActual != null) contenido.removeView(avisoActual);

        View aviso = LayoutInflater.from(this).inflate(R.layout.view_aviso, contenido, false);
        aviso.setBackgroundResource(fondo);
        ((ImageView) aviso.findViewById(R.id.avisoIcono)).setImageResource(icono);
        TextView mensaje = aviso.findViewById(R.id.avisoTexto);
        mensaje.setText(texto);
        mensaje.setTextColor(colorTexto);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        lp.setMargins(px(16), margenSuperior(contenido), px(16), 0);
        contenido.addView(aviso, lp);
        avisoActual = aviso;
        // En una pantalla recién abierta el espacio de la barra de estado se conoce un instante después.
        aviso.post(() -> {
            FrameLayout.LayoutParams actual = (FrameLayout.LayoutParams) aviso.getLayoutParams();
            actual.topMargin = margenSuperior(contenido);
            aviso.setLayoutParams(actual);
        });

        aviso.setAlpha(0f);
        aviso.setTranslationY(-px(24));
        aviso.animate().alpha(1f).translationY(0).setDuration(220).start();
        aviso.announceForAccessibility(texto);
        aviso.setOnClickListener(v -> ocultarAviso(aviso));
        aviso.postDelayed(() -> ocultarAviso(aviso), DURACION_AVISO_MS);
    }

    private int margenSuperior(View vista) {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(vista);
        int barraEstado = insets == null ? 0
                : insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
        return barraEstado + px(10);
    }

    private void ocultarAviso(View aviso) {
        if (aviso.getParent() == null) return;
        aviso.animate().alpha(0f).translationY(-px(24)).setDuration(200).withEndAction(() -> {
            ViewGroup padre = (ViewGroup) aviso.getParent();
            if (padre != null) padre.removeView(aviso);
            if (avisoActual == aviso) {
                avisoActual = null;
                avisoPendiente = null;
            }
        }).start();
    }

    // ---------------------------------------------------------------- Explicaciones

    /** Ventana de explicación: título, texto grande y un único botón "Entendido". */
    protected void explicar(String titulo, String texto) {
        View vista = LayoutInflater.from(this).inflate(R.layout.view_dialogo, null, false);
        ((TextView) vista.findViewById(R.id.dialogoTitulo)).setText(titulo);
        ((TextView) vista.findViewById(R.id.dialogoMensaje)).setText(texto);
        AlertDialog dialogo = new AlertDialog.Builder(this).setView(vista).create();
        vista.findViewById(R.id.dialogoBoton).setOnClickListener(v -> dialogo.dismiss());
        dialogo.show();
        // Si el texto es largo, se desplaza dentro de la ventana y el botón sigue a la vista.
        View scroll = vista.findViewById(R.id.dialogoScroll);
        scroll.post(() -> {
            int limite = (int) (getResources().getDisplayMetrics().heightPixels * 0.5f);
            if (scroll.getHeight() > limite) {
                ViewGroup.LayoutParams lp = scroll.getLayoutParams();
                lp.height = limite;
                scroll.setLayoutParams(lp);
            }
        });
    }

    // ---------------------------------------------------------------- Barra inferior

    /**
     * Activa la barra inferior de la pantalla (si su layout la incluye).
     * @param seccion sección que se resalta
     * @param exacta  true si esta pantalla ES la de esa sección (tocarla otra vez no hace nada)
     */
    protected void activarBarraInferior(int seccion, boolean exacta) {
        View barra = findViewById(R.id.barraInferior);
        if (barra == null) return;
        for (int i = 0; i < IDS_BARRA.length; i++) {
            LinearLayout item = barra.findViewById(IDS_BARRA[i]);
            ImageView icono = (ImageView) item.getChildAt(0);
            TextView texto = (TextView) item.getChildAt(1);
            boolean activa = i == seccion;
            icono.setBackgroundResource(activa ? R.drawable.bg_nav_pill : 0);
            icono.setColorFilter(getColor(activa ? R.color.forest : R.color.nav_inactive));
            texto.setTextColor(getColor(activa ? R.color.forest_dark : R.color.nav_inactive));
            texto.setTypeface(null, activa ? Typeface.BOLD : Typeface.NORMAL);
            item.setSelected(activa);
            final int destino = i;
            item.setOnClickListener(v -> {
                if (destino == seccion && exacta) return;
                irASeccion(destino);
            });
        }
        // Muyu flota sobre todas las pantallas con barra, salvo en su propia pantalla.
        if (seccion != SECCION_MUYU) mostrarMuyuFlotante();
    }

    // ---------------------------------------------------------------- Muyu flotante

    /**
     * Botón redondo de Muyu que flota sobre la pantalla.
     * Un toque abre a Muyu; si lo arrastras, se mueve y se pega al borde más cercano.
     * La posición se recuerda entre pantallas.
     */
    private void mostrarMuyuFlotante() {
        ViewGroup contenido = findViewById(android.R.id.content);
        if (contenido == null || contenido.findViewById(R.id.muyuFlotante) != null) return;
        View muyu = LayoutInflater.from(this).inflate(R.layout.view_muyu_flotante, contenido, false);
        muyu.setAlpha(0f);
        contenido.addView(muyu);
        // Abre a Muyu encima de esta pantalla: al volver, sigues donde estabas.
        muyu.setOnClickListener(v -> abrirMuyu());
        muyu.setOnTouchListener(new ArrastreMuyu());
        // Se ubica cuando la pantalla ya conoce su tamaño.
        contenido.post(() -> ubicarMuyu(muyu, contenido, false));
    }

    private void ubicarMuyu(View muyu, ViewGroup contenido, boolean guardar) {
        int ancho = contenido.getWidth(), alto = contenido.getHeight();
        if (ancho == 0 || alto == 0 || muyu.getWidth() == 0) {
            muyu.post(() -> ubicarMuyu(muyu, contenido, guardar));
            return;
        }
        int[] limites = limitesVerticales(contenido, muyu);
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_UI, MODE_PRIVATE);
        if (guardar) {
            boolean derecha = muyu.getX() + muyu.getWidth() / 2f > ancho / 2f;
            float fraccion = (muyu.getY() - limites[0]) / Math.max(1f, limites[1] - limites[0]);
            prefs.edit().putBoolean("muyu_derecha", derecha).putFloat("muyu_altura", fraccion).apply();
        }
        boolean derecha = prefs.getBoolean("muyu_derecha", true);
        float fraccion = Math.max(0f, Math.min(1f, prefs.getFloat("muyu_altura", 1f)));
        float x = derecha ? ancho - muyu.getWidth() - px(14) : px(14);
        float y = limites[0] + fraccion * (limites[1] - limites[0]);
        muyu.animate().x(x).y(y).alpha(1f).setDuration(guardar ? 180 : 150).start();
    }

    /** Muyu no tapa la barra de estado ni la barra inferior. */
    private int[] limitesVerticales(ViewGroup contenido, View muyu) {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(contenido);
        int arriba = (insets == null ? 0 : insets.getInsets(WindowInsetsCompat.Type.systemBars()).top) + px(12);
        View barra = findViewById(R.id.barraInferior);
        int abajoLibre = barra != null && barra.getVisibility() == View.VISIBLE ? barra.getHeight()
                : (insets == null ? 0 : insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
        int abajo = contenido.getHeight() - abajoLibre - muyu.getHeight() - px(14);
        return new int[]{arriba, Math.max(arriba, abajo)};
    }

    /** Distingue un toque (abre Muyu) de un arrastre (lo mueve). */
    private class ArrastreMuyu implements View.OnTouchListener {
        private float inicioX, inicioY, origenX, origenY;
        private boolean arrastrando;

        @Override public boolean onTouch(View v, android.view.MotionEvent e) {
            ViewGroup padre = (ViewGroup) v.getParent();
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    inicioX = e.getRawX(); inicioY = e.getRawY();
                    origenX = v.getX(); origenY = v.getY();
                    arrastrando = false;
                    v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start();
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - inicioX, dy = e.getRawY() - inicioY;
                    if (!arrastrando && Math.hypot(dx, dy) > px(8)) arrastrando = true;
                    if (arrastrando) {
                        int[] limites = limitesVerticales(padre, v);
                        v.setX(Math.max(0, Math.min(padre.getWidth() - v.getWidth(), origenX + dx)));
                        v.setY(Math.max(limites[0], Math.min(limites[1], origenY + dy)));
                    }
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    if (arrastrando) ubicarMuyu(v, padre, true);
                    else v.performClick();
                    return true;
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    if (arrastrando) ubicarMuyu(v, padre, true);
                    return true;
                default:
                    return false;
            }
        }
    }

    private void irASeccion(int seccion) {
        Intent intent;
        switch (seccion) {
            case SECCION_INICIO:
                intent = new Intent(this, HomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                break;
            case SECCION_PARCELA:
                intent = new Intent(this, ScienceActivity.class)
                        .putExtra(ScienceActivity.EXTRA_MODE, ScienceActivity.MODE_PRESENT);
                break;
            case SECCION_SIEMBRA:
                intent = new Intent(this, ScienceActivity.class)
                        .putExtra(ScienceActivity.EXTRA_MODE, ScienceActivity.MODE_FUTURE);
                break;
            case SECCION_CLIMA:
                intent = new Intent(this, SupportActivity.class)
                        .putExtra(SupportActivity.EXTRA_MODE, SupportActivity.MODE_ALERTS);
                break;
            default:
                intent = new Intent(this, CompanionActivity.class)
                        .putExtra(CompanionActivity.EXTRA_MODE, CompanionActivity.MODE_MUYU);
                break;
        }
        startActivity(intent);
        overridePendingTransition(0, 0);
        // Inicio queda siempre debajo: "atrás" desde cualquier sección vuelve a Inicio.
        if (!(this instanceof HomeActivity)) {
            finish();
            overridePendingTransition(0, 0);
        }
    }

    /**
     * Abre a Muyu: el chat completo (web de Muyu dentro de la app) si viene incluido;
     * si no, las preguntas frecuentes sin conexión.
     */
    protected void abrirMuyu() {
        if (MuyuWebActivity.disponible(this)) {
            startActivity(new Intent(this, MuyuWebActivity.class));
        } else {
            startActivity(new Intent(this, CompanionActivity.class)
                    .putExtra(CompanionActivity.EXTRA_MODE, CompanionActivity.MODE_MUYU));
        }
    }

    protected void irA(Class<?> destino) {
        startActivity(new Intent(this, destino));
    }

    private int px(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
