# Integración de mapa satelital — Kallpa

Se añadió `SatelliteParcelActivity.java`, su layout XML, el registro en AndroidManifest.xml y el botón «Ver mapa satelital» a cada tarjeta de Mis parcelas. Se reutiliza osmdroid 6.1.20; no se agregaron dependencias.

## Prueba
Abrir la carpeta que contiene `settings.gradle` en Android Studio, sincronizar Gradle y ejecutar. Iniciar sesión, entrar en Mis parcelas y pulsar «Ver mapa satelital». Comprobar ubicación y carga de imágenes con internet.

## Limitaciones
El marcador indica solo la ubicación registrada, no el perímetro. Esri World Imagery es un mapa base, no análisis de cobertura NASA HLS. Requiere conexión, disponibilidad y condiciones de uso del proveedor. Verificar términos de uso y atribución antes de publicar. No se ha compilado ni probado en dispositivo desde este entorno.
