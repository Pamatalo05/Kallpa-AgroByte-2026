import os
import pandas as pd
import requests

# 1. Crear directorios necesarios
os.makedirs("datos/crudos", exist_ok=True)
os.makedirs("datos/procesados", exist_ok=True)

# 2. Descargar o leer el archivo de índices ENSO reales de NOAA CPC
url_noaa = "https://www.cpc.ncep.noaa.gov/data/indices/ersst5.nino.mth.91-20.ascii"
print("Descargando índices ENSO desde NOAA CPC...")

try:
    resp_noaa = requests.get(url_noaa, timeout=30)
    resp_noaa.raise_for_status()
    with open("datos/crudos/noaa_nino.txt", "w", encoding="utf-8") as f:
        f.write(resp_noaa.text)
    input_path = "datos/crudos/noaa_nino.txt"
except Exception as e:
    print(f"No se pudo descargar en línea ({e}), usando archivo local 'noaa_nino.txt'...")
    input_path = "noaa_nino.txt"

# 3. Procesar el archivo con pandas (maneja nombres de columnas duplicados automáticamente como ANOM, ANOM.1, etc.)
df_noaa = pd.read_csv(input_path, sep=r"\s+")

# Identificamos las columnas de interés:
# YR = anio, MON = mes, ANOM (la primera) = nino12_anom, ANOM.3 (la cuarta anomalía) = oni_anom
df_procesado = pd.DataFrame()
df_procesado["anio"] = df_noaa["YR"]
df_procesado["mes"] = df_noaa["MON"]
df_procesado["nino12_anom"] = df_noaa["ANOM"]      # Anomalía de Niño 1+2
df_procesado["oni_anom"] = df_noaa["ANOM.3"]      # Anomalía de Niño 3.4 (ONI)

# 4. Función de clasificación técnica para la Costa ecuatoriana (Daule y Babahoyo)
def clasificar_enso_ecuador(row):
    """Clasifica las fases ENSO evitando solapamientos entre El Niño y La Niña
    mediante años análogos extremos y umbrales conjuntos de Niño 1+2 y ONI.
    """
    anio = row["anio"]
    anom = row["nino12_anom"]
    oni = row["oni_anom"]

    # Años análogos extremos validados para la cuenca del Guayas / Costa ecuatoriana
    anios_nina_extrema = [2007, 2008, 2010, 2011, 2020, 2021, 2022]
    anios_nino_extremo = [2009, 2015, 2016, 2023]

    # Regla 1: Años análogos históricos documentados
    if anio in anios_nino_extremo and anom >= 0.4:
        return "el_nino"
    elif anio in anios_nina_extrema and anom <= -0.4:
        return "la_nina"
    
    # Regla 2: Umbrales oceánicos estándar (Niño 1+2 y ONI)
    elif anom >= 1.0 or oni >= 0.5:
        return "el_nino"
    elif anom <= -1.0 or oni <= -0.5:
        return "la_nina"
        
    return "neutro"

# Aplicar clasificación
df_procesado["fase_enso"] = df_procesado.apply(clasificar_enso_ecuador, axis=1)

# 5. Exportar resultado limpio
output_path = "datos/procesados/indice_enso_noaa.csv"
df_procesado.to_csv(output_path, index=False)

print(f"¡Proceso exitoso! Archivo guardado en '{output_path}'.")
print("\nResumen de fases detectadas:")
print(df_procesado["fase_enso"].value_counts())