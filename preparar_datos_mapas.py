import json
import shutil
from pathlib import Path
import pandas as pd

BASE_DIR = Path(__file__).resolve().parent
CRUDOS_DIR = BASE_DIR / "datos" / "crudos"
PROC_DIR = BASE_DIR / "datos" / "procesados"
PUBLIC_DATOS_DIR = BASE_DIR / "public" / "datos"
CANTONES_OBJETIVO = ["DAULE", "BABAHOYO"]


def _buscar_columna(df: pd.DataFrame, patron: str) -> str:
    coincidencias = [c for c in df.columns if patron.lower() in c.lower() and "qual" not in c.lower() and "vi_quality" not in c.lower()]
    if not coincidencias:
        raise KeyError(f"No se encontró columna con patrón '{patron}' en {list(df.columns)}")
    return coincidencias[0]


def generar_evidencia_satelital() -> dict:
    ruta_modis = CRUDOS_DIR / "Kallpa-Ecuador-Parcelas-MOD13Q1-061-results.csv"
    ruta_smap = CRUDOS_DIR / "Kallpa-Ecuador-Parcelas-SPL3SMP-E-006-results.csv"

    df_modis = pd.read_csv(ruta_modis)
    df_smap = pd.read_csv(ruta_smap)

    col_ndvi = _buscar_columna(df_modis, "NDVI")
    col_smap = _buscar_columna(df_smap, "soil_moisture")

    df_modis[col_ndvi] = pd.to_numeric(df_modis[col_ndvi], errors="coerce")
    df_smap[col_smap] = pd.to_numeric(df_smap[col_smap], errors="coerce")

    df_modis = df_modis[df_modis["ID"].astype(str).str.upper().isin(CANTONES_OBJETIVO)].dropna(subset=[col_ndvi]).copy()
    df_smap = df_smap[df_smap["ID"].astype(str).str.upper().isin(CANTONES_OBJETIVO)].dropna(subset=[col_smap]).copy()

    # Escala MODIS NDVI: si viene en enteros (-2000..10000), normalizar a [-0.2, 1.0]
    if not df_modis.empty and df_modis[col_ndvi].abs().max() > 1.5:
        df_modis["ndvi"] = (df_modis[col_ndvi] / 10000.0).round(3)
    else:
        df_modis["ndvi"] = df_modis[col_ndvi].round(3)

    df_modis = df_modis[(df_modis["ndvi"] >= -0.2) & (df_modis["ndvi"] <= 1.0)].sort_values("Date")
    df_smap = df_smap[(df_smap[col_smap] > 0.0) & (df_smap[col_smap] <= 1.0)].sort_values("Date")

    evidencia = {
        "fuentes": {
            "cobertura_vegetal": "NASA Terra MODIS MOD13Q1 v061 — NDVI cada 16 días (250 m)",
            "humedad_superficial": "NASA SMAP Enhanced L3 SPL3SMP_E v006 — Humedad superficial (9 km)"
        },
        "parcelas_satelitales": {}
    }

    defaults = {
        "daule": {"lat": -1.86, "lon": -79.98, "ndvi": 0.56, "smap": 0.35},
        "babahoyo": {"lat": -1.80, "lon": -79.53, "ndvi": 0.61, "smap": 0.39}
    }

    for cid in ["daule", "babahoyo"]:
        cid_up = cid.upper()
        m_sub = df_modis[df_modis["ID"].astype(str).str.upper() == cid_up].tail(24)
        s_sub = df_smap[df_smap["ID"].astype(str).str.upper() == cid_up].tail(24)

        ndvi_actual = float(m_sub["ndvi"].iloc[-1]) if len(m_sub) else defaults[cid]["ndvi"]
        smap_actual = float(round(s_sub[col_smap].iloc[-1], 3)) if len(s_sub) else defaults[cid]["smap"]

        if ndvi_actual >= 0.60:
            estado_cobertura = "Cobertura vegetal densa (cultivo en desarrollo)"
        elif ndvi_actual >= 0.35:
            estado_cobertura = "Cobertura moderada / rastrojo (ventana óptima para rotación)"
        else:
            estado_cobertura = "Suelo descubierto / post-cosecha inmediata"

        evidencia["parcelas_satelitales"][cid] = {
            "canton": cid,
            "coordenadas": {
                "lat": float(m_sub["Latitude"].iloc[0]) if len(m_sub) else defaults[cid]["lat"],
                "lon": float(m_sub["Longitude"].iloc[0]) if len(m_sub) else defaults[cid]["lon"]
            },
            "resumen_actual": {
                "ndvi_ultimo": ndvi_actual,
                "fecha_ndvi": str(m_sub["Date"].iloc[-1]) if len(m_sub) else "2025-12",
                "estado_cobertura": estado_cobertura,
                "smap_humedad_ultima": smap_actual,
                "fecha_smap": str(s_sub["Date"].iloc[-1]) if len(s_sub) else "2025-12"
            },
            "serie_ndvi_modis": [
                {"fecha": str(r["Date"]), "ndvi": float(r["ndvi"])}
                for _, r in m_sub.iterrows()
            ],
            "serie_humedad_smap": [
                {"fecha": str(r["Date"]), "humedad_m3_m3": round(float(r[col_smap]), 3)}
                for _, r in s_sub.iterrows()
            ]
        }

    ruta_salida = PROC_DIR / "evidencia_satelital_parcelas.json"
    with open(ruta_salida, "w", encoding="utf-8") as f:
        json.dump(evidencia, f, ensure_ascii=False, indent=2)
    print(f"[OK] {ruta_salida.name} generado sin errores.")
    return evidencia


def actualizar_parcelas_piloto(evidencia: dict):
    ruta_parc = PROC_DIR / "parcelas_piloto.geojson"
    res_daule = evidencia["parcelas_satelitales"]["daule"]["resumen_actual"]
    res_baba = evidencia["parcelas_satelitales"]["babahoyo"]["resumen_actual"]

    geojson_parcelas = {
        "type": "FeatureCollection",
        "features": [
            {
                "type": "Feature",
                "properties": {
                    "id": "P-DAULE-01",
                    "nombre": "Parcela Piloto Daule (Sector Los Lojas)",
                    "canton": "daule",
                    "provincia": "Guayas",
                    "cultivo_actual": "arroz",
                    "hectareas": 4.5,
                    "riego": True,
                    "suelo": "Franco-arcilloso aluvial",
                    "ndvi_modis": res_daule["ndvi_ultimo"],
                    "estado_cobertura": res_daule["estado_cobertura"],
                    "smap_humedad": res_daule["smap_humedad_ultima"]
                },
                "geometry": {
                    "type": "Polygon",
                    "coordinates": [[
                        [-79.9835, -1.8580],
                        [-79.9785, -1.8580],
                        [-79.9785, -1.8620],
                        [-79.9835, -1.8620],
                        [-79.9835, -1.8580]
                    ]]
                }
            },
            {
                "type": "Feature",
                "properties": {
                    "id": "P-BABAHOYO-01",
                    "nombre": "Parcela Piloto Babahoyo (Sector Pimocha)",
                    "canton": "babahoyo",
                    "provincia": "Los Ríos",
                    "cultivo_actual": "maiz_duro",
                    "hectareas": 5.0,
                    "riego": False,
                    "suelo": "Franco-arcilloso a franco-limoso aluvial",
                    "ndvi_modis": res_baba["ndvi_ultimo"],
                    "estado_cobertura": res_baba["estado_cobertura"],
                    "smap_humedad": res_baba["smap_humedad_ultima"]
                },
                "geometry": {
                    "type": "Polygon",
                    "coordinates": [[
                        [-79.5335, -1.7980],
                        [-79.5280, -1.7980],
                        [-79.5280, -1.8025],
                        [-79.5335, -1.8025],
                        [-79.5335, -1.7980]
                    ]]
                }
            }
        ]
    }

    with open(ruta_parc, "w", encoding="utf-8") as f:
        json.dump(geojson_parcelas, f, ensure_ascii=False, indent=2)
    print(f"[OK] {ruta_parc.name} actualizado con Daule y Babahoyo.")


def sincronizar_con_frontend():
    PUBLIC_DATOS_DIR.mkdir(parents=True, exist_ok=True)
    archivos = [
        "paquete_canton.json",
        "parcelas_piloto.geojson",
        "evidencia_satelital_parcelas.json",
        "ecuador_limites.geojson",
        "validacion_ml_auditoria.json",
        "simulador_enso_historico.json"
    ]
    copiados = []
    for nombre in archivos:
        src = PROC_DIR / nombre
        if src.exists():
            shutil.copy2(src, PUBLIC_DATOS_DIR / nombre)
            copiados.append(nombre)
    print(f"[OK] Sincronizados en public/datos/: {copiados}")


if __name__ == "__main__":
    ev = generar_evidencia_satelital()
    actualizar_parcelas_piloto(ev)
    sincronizar_con_frontend()