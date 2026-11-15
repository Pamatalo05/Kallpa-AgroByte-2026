# Kallpa — AgroByte 2026

Documentación del proyecto. Revisión: 15 de noviembre de 2026.

## 1. Qué es Kallpa

Kallpa es una aplicación de apoyo a decisiones agrícolas desarrollada por AgroByte en el contexto del NASA Space Challenge 2026. Busca facilitar la gestión de parcelas, la consulta de información ambiental y la planificación agrícola mediante una interfaz sencilla y el asistente virtual Muyu.

El enfoque del proyecto son pequeños y medianos agricultores de Ecuador. La propuesta prioriza la resiliencia del suelo y la rotación de cultivos, con información territorial y escenarios climáticos. La cobertura efectiva de cada versión depende del contenido de los paquetes de datos incluidos; no debe asumirse cobertura nacional por el solo hecho de mostrar un mapa de Ecuador.

Este repositorio contiene una aplicación **Android nativa con Java y vistas XML**, junto con un chatbot web incrustado y herramientas Python. La versión aquí documentada no debe confundirse con una PWA independiente.

## 2. Objetivo y alcance

- Centralizar la información de las parcelas del agricultor.
- Facilitar la consulta del estado de la parcela y de información por cantón.
- Apoyar la comparación de alternativas de siembra y escenarios ENSO.
- Presentar información cartográfica y evidencia satelital disponible.
- Acompañar la consulta mediante Muyu, con respuestas agrícolas y accesos a módulos.
- Mantener datos y funciones locales para situaciones con conectividad limitada.

Las recomendaciones son apoyo informativo. Los valores mostrados dependen de las fuentes, fechas, metodología y cobertura del paquete; una predicción no equivale a una medición directa de la parcela.

## 3. Módulos de la aplicación

| Módulo | Responsabilidad | Referencias de implementación |
| --- | --- | --- |
| Acceso y usuarios | Registro, inicio de sesión y datos de usuario mediante componentes locales. | `LoginActivity`, `RegisterActivity`, `RepositorioUsuarioLocal`, `ControladorAutenticacion` |
| Inicio | Punto de entrada a las funciones principales. | `HomeActivity` |
| Mis parcelas | Registro, listado, selección y consulta de parcelas. | `MyParcelsActivity`, `ParcelFormActivity`, `ParcelDetailsActivity`, `RepositorioParcelasLocal` |
| Ubicación y límites | Selección de ubicación y pantallas de delimitación del terreno. | `ManualMapActivity`, `LocationPermissionActivity`, `LimitesParcela` |
| Información agrícola | Presentación de información actual y planificación de siembra. | `ScienceActivity`, `PanelCantonal`, `MotorSemaforo` |
| Mapa satelital | Consulta cartográfica asociada a las parcelas. La disponibilidad de imágenes depende del proveedor y la conexión. | `SatelliteParcelActivity`, `MAPA_SATELITAL_CAMBIOS.md` |
| Clima y alertas | Pantallas de apoyo y consulta de alertas. | `SupportActivity` |
| Bitácora | Pantalla de acompañamiento y registro agrícola. | `CompanionActivity` |
| Muyu | Chat animado integrado en una WebView, contexto de parcela y órdenes de navegación. | `MuyuWebActivity`, `muyu/web/src` |

La presencia de una pantalla no acredita por sí sola una validación integral en un dispositivo. Consultar el estado de verificación al final del documento.

## 4. Arquitectura y tecnologías

La aplicación organiza su código en paquetes de vista, modelo y controlador. Los repositorios locales gestionan usuarios, parcelas y ubicación. Los recursos y datos empaquetados permiten acceder a información sin descargarla en cada consulta.

| Capa | Tecnologías |
| --- | --- |
| Aplicación móvil | Java 17, Android SDK, XML, View Binding, AndroidX AppCompat |
| Cartografía | osmdroid |
| Chat integrado | React 18, TypeScript, Vite 5, CSS, Vitest |
| Carga segura del chat | AndroidX WebKit y `WebViewAssetLoader` |
| Respuestas generativas | Gemini mediante función de servidor en `muyu/netlify/functions` |
| Procesamiento de datos | Python, pandas, NumPy, scikit-learn y joblib |

### Flujo de Muyu

1. El usuario abre Muyu desde la aplicación.
2. `MuyuWebActivity` carga los archivos web incluidos en `app/src/main/assets/muyu`.
3. El puente `KallpaAndroid` permite consultar el contexto de parcela y solicitar navegación nativa.
4. Muyu utiliza el paquete agrícola y el modo campo para sus respuestas locales.
5. Si se configura un servicio remoto, las preguntas compatibles pueden enviarse al backend de Gemini.
6. La navegación se traduce a destinos conocidos de la app; no es ejecución arbitraria de instrucciones.

## 5. Estructura del repositorio

| Ruta | Contenido |
| --- | --- |
| `app/src/main/java/com/kallpa/app/view/` | Activities y componentes de presentación. |
| `app/src/main/java/com/kallpa/app/model/` | Entidades, repositorios locales y lógica de datos. |
| `app/src/main/java/com/kallpa/app/controller/` | Controladores de autenticación y ubicación. |
| `app/src/main/res/` | Diseños XML, textos, estilos e imágenes Android. |
| `app/src/main/assets/datos/` | Datos incluidos en la aplicación Android. |
| `app/src/main/assets/muyu/` | Versión compilada del chatbot que utiliza Android. |
| `muyu/web/` | Código fuente, recursos, scripts y pruebas del chatbot. |
| `muyu/netlify/functions/` | Funciones de servidor para la conexión generativa. |
| `muyu/netlify.toml` | Configuración de ejecución y despliegue de Muyu en Netlify. |
| `datos/crudos/` | Insumos originales de datos. |
| `datos/procesados/` | Paquetes, indicadores y artefactos procesados. |
| `scripts_datos/` | Preparación, entrenamiento, simulación, semáforo y auditoría. |
| `public/datos/` | Copias de datos para recursos web. |
| `requirements.txt` | Dependencias Python. |
| `build.gradle`, `app/build.gradle`, `settings.gradle` | Configuración del proyecto Android. |

## 6. Requisitos

### Android

- Android Studio con Android SDK instalado.
- JDK 17, conforme a la configuración Java del proyecto.
- SDK de compilación 35 y SDK objetivo 35.
- Android 8.0/API 26 o superior como versión mínima.
- Android Gradle Plugin 8.10.1, declarado en la raíz.
- Gradle 8.11.1, versión indicada en la configuración del wrapper.

### Chatbot y datos

- Node.js 20 LTS o una versión compatible con las dependencias del chatbot.
- npm para instalar con el archivo de bloqueo.
- Python 3 con entorno virtual y las dependencias de `requirements.txt`, si se procesarán datos.
- Internet para instalar dependencias, obtener imágenes remotas o usar Gemini.

## 7. Ejecutar Kallpa en Android

1. Clonar el repositorio y abrir su raíz en Android Studio.
2. Instalar el SDK requerido y seleccionar JDK 17 para Gradle.
3. Sincronizar el proyecto y resolver cualquier incompatibilidad de Gradle informada por Android Studio.
4. Seleccionar un emulador o conectar un teléfono con depuración USB.
5. Ejecutar el módulo `app`.

```bash
git clone https://github.com/Pamatalo05/Kallpa-AgroByte-2026.git
cd Kallpa-AgroByte-2026
# Con Gradle compatible instalado y SDK configurado:
gradle :app:assembleDebug
```

El APK de depuración se genera normalmente en `app/build/outputs/apk/debug/`. Si se usa la terminal, configurar `ANDROID_HOME` o el valor `sdk.dir` de `local.properties`. Este archivo es específico del equipo y no debe contener rutas de otra persona.

Los archivos de Muyu ya están empaquetados en el repositorio; Node no es necesario para abrirlos desde Android. Sí es necesario al modificar o regenerar el chatbot.

## 8. Trabajar con Muyu

### Instalar, probar y compilar

```bash
cd muyu/web
npm ci
npm test
npm run build
npm run build:kallpa
```

`build` comprueba TypeScript y genera `dist`. `build:kallpa` utiliza rutas relativas y ejecuta el script de copia a los assets Android. Después de modificar el chat, regenerar esos assets antes de compilar la aplicación; editar solo el código fuente no actualiza la versión incrustada.

Para desarrollo web aislado:

```bash
cd muyu/web
npm run dev
```

El servidor de Vite permite revisar el frontend. La navegación Android y su puente requieren ejecutar Kallpa; abrir la web en un navegador no prueba las Activities nativas. El chat generativo también requiere el backend correspondiente.

### Modo campo

La versión Android inicia en modo campo cuando no existe `VITE_MUYU_CHAT_URL`. Ese modo utiliza información agrícola local y no debe presentarse como una conexión activa con Gemini.

Ejemplos de órdenes contempladas por las pruebas de navegación:

- “Abre mis parcelas”.
- “Muéstrame mi parcela”.
- “Abre siembra”.
- “Ve a clima”.
- “Llévame a la bitácora”.
- “Volver al inicio”.

### Conectar Gemini

1. Publicar el backend de Muyu y configurar `GEMINI_API_KEY` únicamente en el servidor.
2. Obtener la URL HTTPS real del endpoint de chat.
3. Compilar el frontend Android con `VITE_MUYU_CHAT_URL`.
4. Comprobar que el backend acepte el origen utilizado por la WebView y responda al formato esperado.
5. Recompilar Kallpa y probar conversación, recuperación ante fallos y modo campo en un dispositivo.

```bash
cd muyu/web
# Ejemplo: reemplazar por una URL real; no contiene una clave secreta.
VITE_MUYU_CHAT_URL=https://TU-SITIO.netlify.app/api/chat npm run build:kallpa
```

La configuración `muyu/netlify.toml` contempla publicar `web/dist`, compilar el frontend y servir funciones desde `netlify/functions`. Al desplegar este subproyecto, configurar la raíz/base de trabajo como `muyu` y verificar las rutas en el proveedor.

No introducir `GEMINI_API_KEY` en variables `VITE_*`, en JavaScript del cliente, en assets Android ni en commits. Un endpoint configurado no basta: probar una respuesta real del servicio antes de declarar Gemini operativo.

### Voz

El proyecto incluye componentes de voz y un puente Android. Su disponibilidad depende del dispositivo, sus servicios de voz y la implementación nativa. Validar lectura en voz alta, interrupción al salir y cualquier entrada de voz ofrecida por la interfaz en un teléfono; las pruebas unitarias del chat no sustituyen esa comprobación.

## 9. Datos y modelos

Entre los artefactos procesados se encuentran `paquete_canton.json`, `simulador_enso_historico.json`, `evidencia_satelital_parcelas.json`, `validacion_ml_auditoria.json`, series históricas, índices ENSO y un modelo serializado con joblib.

Los assets Android incluyen además archivos GeoJSON de parcelas y límites. La información disponible debe interpretarse con la fecha, resolución y metodología de su fuente. Un mapa satelital de fondo y una serie de evidencia procesada son recursos diferentes.

Scripts disponibles en `scripts_datos`:

| Archivo | Finalidad indicada por el proyecto |
| --- | --- |
| `preparar_datos_mapas.py` | Preparación de datos cartográficos. |
| `entrenar_modelo_kallpa.py` | Entrenamiento del modelo agrícola. |
| `generar_simulador_enso.py` | Generación de recursos del simulador ENSO. |
| `semaforo.py` | Procesamiento relacionado con el semáforo de riesgo. |
| `auditar_kallpa.py` | Auditoría de los recursos de Kallpa. |
| `auditar_simulador_enso.py` | Auditoría del simulador ENSO. |

Preparar el entorno de datos desde la raíz:

```bash
python -m venv .venv
# Linux/macOS:
source .venv/bin/activate
# Windows PowerShell: .venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
```

Antes de regenerar resultados, revisar entradas, rutas y parámetros de cada script. No ejecutar todo el procesamiento como requisito para abrir la app. Tras actualizar datos, comprobar las copias consumidas por Android, la web y Muyu para evitar versiones diferentes del mismo paquete.

## 10. Validación y estado conocido

Verificación realizada el 15 de noviembre de 2026 sobre la copia integrada de este repositorio:

| Comprobación | Resultado |
| --- | --- |
| Pruebas de Muyu integrado | 73 pruebas aprobadas en 9 archivos. |
| Compilación web y empaquetado Android de Muyu | Correctos mediante `npm run build:kallpa`. |
| Navegación por órdenes | Casos de rutas conocidas y rechazo de órdenes no contempladas cubiertos por pruebas. |
| Compilación APK en el Codespace revisado | Bloqueada por ausencia de Android SDK; no acredita un fallo funcional del código. |
| Gemini dentro de Android | Pendiente de configurar y verificar una URL real del backend. |
| Apertura, navegación, contexto y voz en teléfono | Pendientes de prueba integral en un dispositivo Android. |

La copia de Muyu dentro de Kallpa tiene su propio código y pruebas. Un cambio publicado en el repositorio independiente `MUYU-Chatbot-Agrobyte` no se incorpora automáticamente a Kallpa: requiere integración, revisión y regeneración de assets. No confundir los resultados de pruebas de ambos repositorios.

### Prueba manual recomendada

1. Iniciar sesión, crear o seleccionar una parcela y abrir Muyu desde el botón de la app.
2. Comprobar que la parcela y el cantón coincidan con la selección activa.
3. Consultar siembra, clima y ahorro de urea con los datos disponibles.
4. Enviar preguntas consecutivas y verificar el contexto de conversación.
5. Ejecutar las órdenes de navegación y regresar al chat.
6. Desconectar la red, consultar en modo campo y volver a conectarla.
7. Con el backend configurado, verificar una respuesta de Gemini y el comportamiento ante un error o espera excesiva.
8. Probar la voz disponible, cerrar Muyu y comprobar que no quede audio activo.

## 11. Problemas frecuentes

| Síntoma | Qué revisar |
| --- | --- |
| `SDK location not found` | Instalar Android SDK y configurar `ANDROID_HOME` o `sdk.dir`. |
| Error de versión de Gradle o Java | Compatibilidad de Gradle con el plugin Android y uso de JDK 17. |
| Cambios del chat no aparecen en el APK | Ejecutar `npm run build:kallpa` y volver a compilar Android. |
| Muyu solo responde en modo campo | Configuración de `VITE_MUYU_CHAT_URL` y accesibilidad del backend. |
| Fallo HTTP o CORS en el chat | URL HTTPS, configuración del servidor, origen de la WebView y logs del backend. |
| Mapa sin imágenes | Conectividad, disponibilidad del proveedor y datos de ubicación. |
| Datos distintos entre pantallas | Versiones de los paquetes en `datos/procesados`, `public/datos` y assets. |
| Voz no disponible | Servicios de voz del dispositivo y pruebas del puente nativo. |

## 12. Colaboración y mantenimiento

- Trabajar en ramas con un propósito concreto y revisar diferencias antes de integrar.
- Conservar cambios ajenos y resolver conflictos por comportamiento, evitando descartar archivos completos sin revisión.
- Ejecutar las pruebas de Muyu y regenerar sus assets cuando se modifique el chatbot.
- Validar Android cuando se cambien Activities, recursos o configuración Gradle.
- Documentar cambios de datos, cobertura y configuración de servicios externos.
- No subir claves, credenciales, datos personales reales ni configuraciones privadas del equipo.

## 13. Documentación y repositorios relacionados

- [Repositorio Kallpa](https://github.com/Pamatalo05/Kallpa-AgroByte-2026).
- [Repositorio independiente de Muyu](https://github.com/Pamatalo05/MUYU-Chatbot-Agrobyte).
- [Documentación de la integración de Muyu](muyu/README.md).
- [Cambios del mapa satelital](MAPA_SATELITAL_CAMBIOS.md).

La publicación de un repositorio no implica una licencia de uso libre. Consultar una licencia explícita y las condiciones de las fuentes y proveedores antes de redistribuir código, datos o cartografía.
