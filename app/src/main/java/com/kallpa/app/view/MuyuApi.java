package com.kallpa.app.view;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native transport for one fixed HTTPS backend. Never accepts arbitrary URLs or credentials. */
final class MuyuApi implements AutoCloseable {
    interface Respuesta { void recibir(String id, int estado, String cuerpo, boolean error); }
    private final Respuesta respuesta;
    private final ExecutorService tareas = Executors.newFixedThreadPool(2);
    private final ConcurrentHashMap<String, HttpURLConnection> conexiones = new ConcurrentHashMap<>();
    private final Set<String> pendientes = ConcurrentHashMap.newKeySet();
    private volatile boolean cerrado;

    MuyuApi(Respuesta respuesta) { this.respuesta = respuesta; }

    void solicitar(String id, String metodo, String cuerpo) {
        if (cerrado || id == null || !id.matches("[a-zA-Z0-9-]{1,80}")) return;
        if (!"POST".equals(metodo) && !"HEAD".equals(metodo)) return;
        if (cuerpo == null || cuerpo.getBytes(StandardCharsets.UTF_8).length > 16000) {
            respuesta.recibir(id, 413, "", true); return;
        }
        if (!pendientes.add(id)) return;
        tareas.execute(() -> {
            HttpURLConnection c = null;
            try {
                if (cerrado || !pendientes.contains(id)) return;
                c = (HttpURLConnection) new URL("https://muyu-agrobyte.netlify.app/api/chat").openConnection();
                c.setInstanceFollowRedirects(false);
                c.setRequestMethod(metodo);
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                c.setRequestProperty("Accept", "application/json");
                conexiones.put(id, c);
                if (cerrado || !pendientes.contains(id)) return;
                if ("POST".equals(metodo)) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
                    c.setFixedLengthStreamingMode(bytes.length);
                    try (java.io.OutputStream out = c.getOutputStream()) { out.write(bytes); }
                }
                int estado = c.getResponseCode();
                String texto = "";
                if (!"HEAD".equals(metodo)) {
                    InputStream fuente = estado >= 400 ? c.getErrorStream() : c.getInputStream();
                    if (fuente != null) try (InputStream in = fuente; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[4096]; int n;
                        while ((n = in.read(buffer)) != -1) {
                            if (out.size() + n > 131072) throw new IllegalStateException("Respuesta demasiado grande");
                            out.write(buffer, 0, n);
                        }
                        texto = new String(out.toByteArray(), StandardCharsets.UTF_8);
                    }
                }
                if (!cerrado && pendientes.remove(id)) respuesta.recibir(id, estado, texto, false);
            } catch (Exception e) {
                if (!cerrado && pendientes.remove(id)) respuesta.recibir(id, 502, "", true);
            } finally {
                pendientes.remove(id);
                conexiones.remove(id);
                if (c != null) c.disconnect();
            }
        });
    }
    void cancelar(String id) {
        if (id == null) return;
        pendientes.remove(id);
        HttpURLConnection c = conexiones.remove(id);
        if (c != null) c.disconnect();
    }
    @Override public void close() {
        cerrado = true;
        pendientes.clear();
        for (HttpURLConnection c : conexiones.values()) c.disconnect();
        conexiones.clear();
        tareas.shutdownNow();
    }
}
