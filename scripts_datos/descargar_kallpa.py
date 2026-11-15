import os
import json
import time
import requests
import pandas as pd

# 1. Crear estructura ordenada dentro de 'datos/' y 'public/data/'
os.makedirs("datos/crudos", exist_ok=True)
os.makedirs("datos/procesados", exist_ok=True)
os.makedirs("public/data", exist_ok=True)

# 2. Función para obtener elevación y pendiente real derivada de SRTM 30m DEM
def obtener_terreno_srtm(lat, lon):
    delta = 0.0045  # ~500 metros de separación para calcular pendiente local
    puntos = [
        {"latitude": lat, "longitude": lon},
        {"latitude": lat + delta, "longitude": lon},
        {"latitude": lat - delta, "longitude": lon},
        {"latitude": lat, "longitude": lon + delta},
        {"latitude": lat, "longitude": lon - delta},
    ]
    try:
        r = requests.post(
            "https://api.open-elevation.com/api/v1/lookup",
            json={"locations": puntos},
            timeout=25
        )
        r.raise_for_status()
        elevs = [item["elevation"] for item in r.json()["results"]]
        elev_centro = elevs[0]
        desnivel_max = max(abs(e - elev_centro) for e in elevs[1:])
        pendiente_pct = round((desnivel_max / 500.0) * 100.0, 2)
        return {
            "elevacion_msnm": elev_centro,
            "pendiente_srtm_pct": pendiente_pct,
            "fuente_dem": "SRTM 30m (Open-Elevation)"
        }
    except Exception as e:
        print(f"   [Aviso DEM] Error SRTM en ({lat}, {lon}): {e}")
        return {
            "elevacion_msnm": None,
            "pendiente_srtm_pct": None,
            "fuente_dem": "SRTM pendiente de validación"
        }

# 3. Función para descargar NDVI (NASA MODIS MOD13Q1) en bloques semestrales
def obtener_ndvi_nasa_modis(lat, lon):
    rangos = [
        ("A2023001", "A2023180"),
        ("A2023181", "A2023365"),
        ("A2024001", "A2024180"),
        ("A2024181", "A2024366"),
        ("A2025001", "A2025180"),
        ("A2025181", "A2025365"),
    ]
    serie = []
    for inicio, fin in rangos:
        url = (
            f"https://modis.ornl.gov/rst/api/v1/MOD13Q1/subset"
            f"?latitude={lat}&longitude={lon}"
            f"&band=250m_16_days_NDVI"
            f"&startDate={inicio}&endDate={fin}"
            f"&kmAboveBelow=0&kmLeftRight=0"
        )
        try:
            r = requests.get(url, headers={"Accept": "application/json"}, timeout=30)
            if r.status_code == 400:
                continue
            r.raise_for_status()
            subset = r.json().get("subset", [])
            for item in subset:
                val_raw = item["data"][0]
                if val_raw > -2000:
                    ndvi = round(val_raw * 0.0001, 3)
                    serie.append({"fecha": item["calendar_date"], "ndvi": ndvi})
            time.sleep(0.4)
        except Exception as e:
            print(f"   [Aviso NDVI] Bloque {inicio}-{fin} en ({lat}, {lon}): {e}")
    return serie

# 4. Cantones exclusivos del proyecto piloto: Daule y Babahoyo
zonas = {
    "daule": {"nombre": "Daule, Guayas", "region": "costa_humeda", "lat": -1.86, "lon": -79.98, "suelo": "Franco-arcilloso aluvial (llanura arrocera)"},
    "babahoyo": {"nombre": "Babahoyo, Los Ríos", "region": "costa_humeda", "lat": -1.80, "lon": -79.53, "suelo": "Franco-arcilloso de llanura"}
}

# Cargar datos de ENSO generados por enso.py
df_enso_nino = None
enso_path = "datos/procesados/indice_enso_noaa.csv"
if os.path.exists(enso_path):
    df_enso_nino = pd.read_csv(enso_path)

paquete_final = {
    "meta": {
        "proyecto": "Kallpa",
        "descripcion": "Plataforma offline-first de rotación de cultivos con observaciones NASA para Daule y Babahoyo",
        "fuentes_datos": [
            "NASA POWER Agroclimatology (2005-2025)",
            "NASA MODIS MOD13Q1 NDVI 250m",
            "SRTM 30m Digital Elevation Model",
            "INIAP / MAG SIPA Ecuador"
        ],
        "periodo_analizado": "2005-2025"
    },
    "cantones": {}
}

print("Descargando series NASA POWER, NDVI MODIS y SRTM exclusivamente para Daule y Babahoyo...")
for key, info in zonas.items():
    print(f"   -> Procesando {info['nombre']}...")
    url_power = (
        f"https://power.larc.nasa.gov/api/temporal/monthly/point"
        f"?parameters=PRECTOTCORR,GWETROOT,GWETTOP,T2M_MAX,T2M_MIN,ALLSKY_SFC_SW_DWN,RH2M"
        f"&community=AG"
        f"&longitude={info['lon']}&latitude={info['lat']}"
        f"&start=2005&end=2025"
        f"&format=JSON"
    )
    r = requests.get(url_power, timeout=45)
    r.raise_for_status()
    data_power = r.json()

    with open(f"datos/crudos/nasa_power_{key}.json", "w", encoding="utf-8") as f:
        json.dump(data_power, f, indent=2)

    params = data_power["properties"]["parameter"]
    registros = []
    for ym, lluvia_dia in params["PRECTOTCORR"].items():
        anio = int(ym[:4])
        mes = int(ym[4:6])
        if mes == 13 or lluvia_dia < -90:
            continue
        registros.append({
            "anio": anio,
            "mes": mes,
            "lluvia_mm_mes": round(lluvia_dia * 30.0, 1),
            "humedad_raiz": round(params["GWETROOT"].get(ym, 0.5), 3),
            "humedad_superficial": round(params["GWETTOP"].get(ym, 0.5), 3),
            "temp_max_c": round(params["T2M_MAX"].get(ym, 28.0), 1),
            "temp_min_c": round(params["T2M_MIN"].get(ym, 18.0), 1),
            "radiacion_mj_m2": round(params["ALLSKY_SFC_SW_DWN"].get(ym, 16.0), 2),
            "humedad_relativa_pct": round(params["RH2M"].get(ym, 75.0), 1)
        })

    df_zona = pd.DataFrame(registros)
    if df_enso_nino is not None and not df_enso_nino.empty:
        df_zona = df_zona.merge(df_enso_nino, on=["anio", "mes"], how="left")
        df_zona["fase_enso"] = df_zona["fase_enso"].fillna("neutro")
    else:
        df_zona["fase_enso"] = "neutro"

    df_zona.to_csv(f"datos/procesados/serie_historica_{key}.csv", index=False)

    escenarios = {}
    for fase in ["neutro", "el_nino", "la_nina"]:
        sub = df_zona[df_zona["fase_enso"] == fase]
        if sub.empty:
            sub = df_zona
        mensual = (
            sub.groupby("mes")[["lluvia_mm_mes", "humedad_raiz", "humedad_superficial", "temp_max_c", "temp_min_c", "radiacion_mj_m2", "humedad_relativa_pct"]]
            .mean()
            .round(2)
            .reset_index()
            .to_dict(orient="records")
        )
        escenarios[fase] = mensual

    terreno = obtener_terreno_srtm(info["lat"], info["lon"])
    ndvi_serie = obtener_ndvi_nasa_modis(info["lat"], info["lon"])
    if ndvi_serie:
        pd.DataFrame(ndvi_serie).to_csv(f"datos/procesados/ndvi_modis_{key}.csv", index=False)

    time.sleep(0.5)

    paquete_final["cantones"][key] = {
        "nombre": info["nombre"],
        "region": info["region"],
        "coordenadas": {"lat": info["lat"], "lon": info["lon"]},
        "terreno": terreno,
        "suelo_referencia": info["suelo"],
        "escenarios_climaticos": escenarios,
        "ndvi_historico": ndvi_serie
    }

# 5. Catálogo agronómico validado (INIAP / MAG SIPA) para Daule y Babahoyo
cultivos = [
    {"id": "arroz", "nombre": "Arroz (INIAP-14 / SFL-11)", "regiones": ["costa_humeda"], "tipo": "cereal", "dias_ciclo": 130, "demanda_agua": "alta", "tolera_encharcamiento": True, "fija_nitrogeno": False, "protege_suelo": 3, "costo_insumos_ha": 980, "ingreso_bruto_ha": 1650, "ahorro_urea_ha": 0, "fuente": "INIAP / MAG SIPA"},
    {"id": "maiz_duro", "nombre": "Maíz duro amarillo (INIAP H-551)", "regiones": ["costa_humeda"], "tipo": "cereal", "dias_ciclo": 120, "demanda_agua": "media", "tolera_encharcamiento": False, "fija_nitrogeno": False, "protege_suelo": 2, "costo_insumos_ha": 850, "ingreso_bruto_ha": 1480, "ahorro_urea_ha": 0, "fuente": "INIAP / MAG SIPA"},
    {"id": "soya", "nombre": "Soya de rotación (INIAP-308)", "regiones": ["costa_humeda"], "tipo": "leguminosa", "dias_ciclo": 105, "demanda_agua": "media", "tolera_encharcamiento": False, "fija_nitrogeno": True, "protege_suelo": 4, "costo_insumos_ha": 620, "ingreso_bruto_ha": 1190, "ahorro_urea_ha": 180, "fuente": "INIAP / FAO GAEZ"},
    {"id": "frejol_caupi", "nombre": "Fréjol caupí / tumbe", "regiones": ["costa_humeda"], "tipo": "leguminosa", "dias_ciclo": 75, "demanda_agua": "baja", "tolera_encharcamiento": False, "fija_nitrogeno": True, "protege_suelo": 4, "costo_insumos_ha": 480, "ingreso_bruto_ha": 960, "ahorro_urea_ha": 160, "fuente": "INIAP Ecuador"},
    {"id": "mucuna", "nombre": "Mucuna / Crotalaria (abono verde)", "regiones": ["costa_humeda"], "tipo": "cobertura", "dias_ciclo": 65, "demanda_agua": "baja", "tolera_encharcamiento": True, "fija_nitrogeno": True, "protege_suelo": 5, "costo_insumos_ha": 220, "ingreso_bruto_ha": 0, "ahorro_urea_ha": 290, "fuente": "FAO Cover Crops"}
]

paquete_final["cultivos"] = cultivos

# 6. Polígonos GeoJSON exclusivos para Daule y Babahoyo
geojson_parcelas = {
    "type": "FeatureCollection",
    "features": [
        {
            "type": "Feature",
            "properties": {"id": "P-DAULE-01", "canton": "daule", "cultivo_actual": "arroz", "hectareas": 4.5, "riego": True},
            "geometry": {"type": "Polygon", "coordinates": [[[-79.985, -1.858], [-79.982, -1.858], [-79.982, -1.861], [-79.985, -1.861], [-79.985, -1.858]]]}
        },
        {
            "type": "Feature",
            "properties": {"id": "P-BABAHOYO-01", "canton": "babahoyo", "cultivo_actual": "arroz", "hectareas": 5.2, "riego": True},
            "geometry": {"type": "Polygon", "coordinates": [[[-79.535, -1.798], [-79.532, -1.798], [-79.532, -1.802], [-79.535, -1.802], [-79.535, -1.798]]]}
        }
    ]
}

with open("datos/procesados/parcelas_piloto.geojson", "w", encoding="utf-8") as f:
    json.dump(geojson_parcelas, f, ensure_ascii=False, indent=2)
with open("public/data/parcelas_piloto.geojson", "w", encoding="utf-8") as f:
    json.dump(geojson_parcelas, f, ensure_ascii=False, indent=2)

with open("datos/procesados/paquete_canton.json", "w", encoding="utf-8") as f:
    json.dump(paquete_final, f, ensure_ascii=False, indent=2)
with open("public/data/paquete_canton.json", "w", encoding="utf-8") as f:
    json.dump(paquete_final, f, ensure_ascii=False, indent=2)

print("¡Descarga de Kallpa adaptada con éxito solo para Daule y Babahoyo!")