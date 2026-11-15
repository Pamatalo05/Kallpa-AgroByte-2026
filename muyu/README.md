# Muyu actualizado dentro de Kallpa

Fuente integrada: MUYU-Chatbot-Agrobyte main, revisión 2f208d73728a491eee0f06cd57a0e6fc4930c49f.
Se incorporan los componentes de Rita: memoria de conversación y de intención de
ahorro de urea, respuestas conceptuales y seguimientos, sugerencias, preferencias
de lenguaje y extensión, explicación del semáforo, detección de red, plazos,
cancelación y reintento manual. Se conserva navegación por mensajes y por el menú
desplegable de secciones.

## Compilar

Desde la raíz de Kallpa:
- npm --prefix muyu/web ci
- npm --prefix muyu/web test
- npm --prefix muyu/web run build:kallpa

El build sincroniza los dos consumidores web con el paquete canónico de Android,
valida TypeScript y regenera app/src/main/assets/muyu. Se eliminan bundles
anteriores. El chat compilado lee ../datos/paquete_canton.json para ambos motores;
las estimaciones no se duplican dentro de assets/muyu.

## App Android y servidor

El botón flotante existente abre MuyuWebActivity con la parcela activa. El puente
entrega cantón, cultivo, mes, nombre, hectáreas y disponibilidad de riego.
La pantalla no asigna estimaciones de Daule a una parcela fuera de cobertura.

El endpoint predeterminado de la compilación Android es
https://muyu-agrobyte.netlify.app/api/chat. VITE_MUYU_API_BASE permite configurar
otra base y VITE_MUYU_CHAT_URL mantiene compatibilidad con una URL completa.

MuyuApi ejecuta HEAD y POST por HTTPS al único endpoint nativo permitido. Esto
permite conectar desde WebView aunque el servidor publicado todavía no tenga
habilitado CORS para appassets.androidplatform.net. No admite URL arbitraria,
credenciales ni redirecciones. No incluye la clave Gemini en el teléfono.
HEAD solo verifica conectividad. POST envía pregunta, historial y contexto para
responder. La interfaz de transporte identifica cada solicitud, limita la espera,
cancela conexiones y descarta respuestas tardías. Al destruir la actividad se
cierran las conexiones y el ejecutor.

Sin red o ante un error del servidor, el modo campo conserva los datos locales y
los componentes conversacionales. Las preferencias de respuesta se guardan
localmente. La memoria conversacional dura la sesión; no se promete historial
persistente entre reaperturas. La voz usa TextToSpeech de Android y comunica sus
estados al personaje. El reconocimiento de voz depende de las capacidades del
dispositivo; escribir preguntas siempre está disponible.

## Verificación de esta integración

- Build TypeScript/Vite y empaquetado Android: correctos.
- 210 pruebas en 22 archivos: aprobadas, incluidas 7 de transporte nativo.
- MuyuApi compilado con javac; HEAD real devuelve 405 y POST real devuelve 200
  con una respuesta válida. HEAD 405 es el resultado esperado del endpoint.
- Vista del contenido empaquetado probada en navegador en modo Kallpa, con parcela
  de prueba de Babahoyo y maíz: definición de urea y seguimiento para arroz,
  sugerencias y semáforo presentes, sin errores JavaScript capturados.
- Pendiente: compilar el APK y validar WebView, teclado, navegación y voz en un
  teléfono/emulador. El Codespace no dispone de Android SDK.

El servidor de producción conserva su despliegue existente. Esta integración
actualiza Kallpa; no realiza un nuevo despliegue de Netlify.
