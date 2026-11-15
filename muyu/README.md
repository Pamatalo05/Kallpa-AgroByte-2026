# Muyu dentro de Kallpa
Fuente: Pamatalo05/MUYU-Chatbot-Agrobyte (main).
El botón flotante existente abre el chat animado empaquetado en Android.
Usa la parcela activa y permite escribir "abre mis parcelas", "abre siembra",
"abre clima", "abre mi parcela", "abre bitácora" y "volver al inicio".

## Compilar
Desde muyu/web: npm ci && npm run build:kallpa
Los assets compilados se incluyen en el repo para compilar Android sin Node.
Los datos se copian desde app/src/main/assets/datos/paquete_canton.json.

## Gemini
Sin servidor configurado responde en modo campo, sin clave en Android.
Para habilitar preguntas libres con Gemini, despliega muyu/ en Netlify con
GEMINI_API_KEY solo en el servidor y recompila con
VITE_MUYU_CHAT_URL=https://TU-SITIO.netlify.app/api/chat npm run build:kallpa.
El servidor debe permitir CORS para https://appassets.androidplatform.net.
No uses una URL temporal de Codespaces como servidor de producción.

Versión original: 25ee2504c4b12ce465d9768a2487c18b84860ee1.
Validación: 73 pruebas del chat aprobadas. APK pendiente: falta SDK Android en Codespaces.
