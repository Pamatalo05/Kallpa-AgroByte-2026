import json
import shutil
from pathlib import Path
import pandas as pd

BASE_DIR = Path(__file__).resolve().parent.parent
PROC_DIR = BASE_DIR / "datos" / "procesados"
PUBLIC_DATOS_DIR = BASE_DIR / "public" / "datos"

CANTONES = {
    "daule": {
        "nombre": "Daule",
        "provincia": "Guayas",
        "archivo_serie": "serie_historica_daule.csv",
        "relieve": "Llanura aluvial baja (5–7 msnm), drenaje lento en zonas bajas"
    },
    "babahoyo": {
        "nombre": "Babahoyo",
        "provincia": "Los Ríos",
        "archivo_serie": "serie_historica_babahoyo.csv",
        "relieve": "Llanura fluvial de alta recarga (6–8 msnm), alta vulnerabilidad a crecidas en El Niño"
    }
}

MESES_ES = {
    1: "Enero", 2: "Febrero", 3: "Marzo", 4: "Abril",
    5: "Mayo", 6: "Junio", 7: "Julio", 8: "Agosto",
    9: "Septiembre", 10: "Octubre", 11: "Noviembre", 12: "Diciembre"
}


def _obtener_valor(row: pd.Series, candidatos: list, default):
    for col in candidatos:
        if col in row.index and pd.notna(row[col]):
            return row[col]
    return default


def _float_flexible(row: pd.Series, candidatos: list, default: float) -> float:
    raw = _obtener_valor(row, candidatos, default)
    val = pd.to_numeric(raw, errors="coerce")
    return float(default if pd.isna(val) else val)


def _str_flexible(row: pd.Series, candidatos: list, default: str) -> str:
    raw = _obtener_valor(row, candidatos, default)
    return str(raw).strip()


def evaluar_cultivos_por_escenario(df_cultivos: pd.DataFrame, fase_corta: str, humedad_prom: float) -> dict:
    resultados = {"con_riego": [], "sin_riego": []}

    for tiene_riego in [True, False]:
        clave_riego = "con_riego" if tiene_riego else "sin_riego"
        ranking = []

        for _, row in df_cultivos.iterrows():
            nombre = _str_flexible(row, ["nombre", "cultivo", "especie"], "Cultivo")
            nombre_low = nombre.lower()

            # Defaults agronómicos diferenciados por cultivo si faltara alguna columna
            if "soya" in nombre_low:
                def_ciclo, def_n, def_urea, def_dem, def_ench = 105, 85.0, 140.0, "media", "baja"
            elif "caup" in nombre_low or "frejol" in nombre_low or "fréjol" in nombre_low:
                def_ciclo, def_n, def_urea, def_dem, def_ench = 75, 70.0, 115.0, "baja", "media"
            elif "man" in nombre_low:
                def_ciclo, def_n, def_urea, def_dem, def_ench = 110, 65.0, 105.0, "baja", "baja"
            else:  # Mucuna
                def_ciclo, def_n, def_urea, def_dem, def_ench = 120, 140.0, 220.0, "baja", "alta"

            demanda = _str_flexible(row, ["demanda_agua", "requerimiento_hidrico", "necesidad_agua"], def_dem).lower()
            tol_encharc = _str_flexible(row, ["tolera_encharcamiento", "tolerancia_humedad", "drenaje"], def_ench).lower()
            h_min = _float_flexible(row, ["humedad_optima_min", "humedad_min", "h_min"], 0.25)
            h_max = _float_flexible(row, ["humedad_optima_max", "humedad_max", "h_max"], 0.48)
            fijacion_n = _float_flexible(row, ["fijacion_n_kg_ha", "nitrogeno_fijado_kg_ha", "fijacion_nitrogeno"], def_n)
            ahorro_urea = _float_flexible(row, ["ahorro_urea_kg_ha", "urea_ahorrada_kg_ha", "ahorro_urea"], def_urea)
            costo_insumos = _float_flexible(row, ["costo_insumos_ha", "costo_produccion_ha", "costo_ha"], 450.0)
            ingreso_bruto = _float_flexible(row, ["ingreso_bruto_ha", "ingreso_estimado_ha", "ingreso_ha"], 1100.0)
            ciclo_dias = int(_float_flexible(row, ["ciclo_dias", "dias_ciclo", "duracion_dias"], def_ciclo))

            score = 68.0
            nota_finca = ""

            if fase_corta == "nino":
                if tol_encharc in ["alta", "si", "sí"]:
                    score += 22
                    nota_finca = "Alta cobertura y tolerancia a suelos muy húmedos registrados en El Niño."
                elif tol_encharc == "media":
                    score += 12 if tiene_riego else 10
                    nota_finca = "Ciclo corto viable en El Niño asegurando surcos de drenaje."
                else:
                    score -= 26
                    nota_finca = "Alto riesgo de pudrición radicular por saturación histórica en El Niño."
            elif fase_corta == "nina":
                if tiene_riego:
                    if demanda == "media":
                        score += 19
                        nota_finca = "Aprovecha el riego disponible en la parcela para sostener alta rentabilidad en La Niña."
                    else:
                        score += 14
                        nota_finca = "Viable con riego suplementario durante periodos secos de La Niña."
                else:
                    if demanda == "baja":
                        score += 22
                        nota_finca = "Excelente resiliencia en secano aprovechando la humedad residual sin requerir riego."
                    else:
                        score -= 28
                        nota_finca = "Sin riego sufre déficit hídrico severo en años secos como La Niña."
            else:  # neutro
                if h_min <= humedad_prom <= h_max:
                    score += 12
                if tiene_riego:
                    score += 12 if "soya" in nombre_low or "man" in nombre_low else 8
                    nota_finca = "Condiciones históricas óptimas con respaldo de riego en la parcela."
                else:
                    if demanda == "baja":
                        score += 11
                        nota_finca = "Se adapta bien en secano con la humedad residual post-cosecha."
                    else:
                        score -= 5
                        nota_finca = "Viable en años normales sembrando inmediatamente tras la salida de lluvias."

            score += (fijacion_n * 0.06)
            score = round(min(max(score, 15.0), 98.5), 1)

            estado = "Óptimo" if score >= 75 else ("Precaución" if score >= 55 else "No recomendado")
            sacos_urea_ahorrados = round(ahorro_urea / 50.0, 1)
            ahorro_usd_ha = round(sacos_urea_ahorrados * 38.0, 2)

            ranking.append({
                "cultivo": nombre,
                "score_compatibilidad": score,
                "estado": estado,
                "ciclo_dias": ciclo_dias,
                "fijacion_n_kg_ha": fijacion_n,
                "ahorro_urea_kg_ha": ahorro_urea,
                "ahorro_urea_usd_ha": ahorro_usd_ha,
                "costo_insumos_ha": costo_insumos,
                "ingreso_bruto_ha": ingreso_bruto,
                "nota_agronomica": nota_finca
            })

        ranking.sort(key=lambda x: x["score_compatibilidad"], reverse=True)
        resultados[clave_riego] = ranking

    return resultados


def construir_simulador():
    df_cultivos = pd.read_csv(PROC_DIR / "cultivos_ecuador.csv")

    simulador = {
        "metadata": {
            "titulo": "Simulador Histórico ENSO — Respuesta de Suelo y Cultivos (2005–2025)",
            "fuentes": [
                "NASA POWER Agroclimatology (2005–2025)",
                "NASA SMAP Soil Moisture",
                "NOAA CPC Oceanic Niño Index (ONI) & Niño 1+2",
                "INIAP Ecuador — Fichas técnicas de leguminosas de rotación"
            ],
            "alcance_nota": "Simulación basada en registros satelitales históricos (escala cantonal ~9–50 km) ajustada a nivel de finca por disponibilidad de riego, cultivo previo y hectáreas."
        },
        "cantones": {}
    }

    mapa_fases = [
        ("neutro", "neutro", "Año Normal (Neutro)"),
        ("el_nino", "nino", "Fenómeno El Niño (Exceso de lluvia)"),
        ("la_nina", "nina", "Fenómeno La Niña (Déficit hídrico)")
    ]

    for cid, meta in CANTONES.items():
        df = pd.read_csv(PROC_DIR / meta["archivo_serie"])
        df["fase_enso"] = df["fase_enso"].astype(str).str.strip().str.lower()
        df["mes"] = pd.to_numeric(df["mes"], errors="coerce")

        escenarios_data = {}
        for fase_csv, clave_corta, etiqueta in mapa_fases:
            sub = df[df["fase_enso"] == fase_csv].copy()
            if sub.empty:
                sub = df.copy()

            lluvia_prom = round(float(sub["lluvia_mm_mes"].mean()), 1)
            humedad_prom = round(float(sub["humedad_raiz"].mean()), 3)
            temp_max_prom = round(float(sub["temp_max_c"].mean()), 1)
            oni_prom = round(float(sub["oni_anom"].mean()), 2)

            anios_reales = sorted(sub["anio"].dropna().unique().tolist())
            anios_destacados = [int(a) for a in anios_reales[-3:]]

            perfil_mensual = []
            for m in range(1, 13):
                sub_m = sub[sub["mes"] == m]
                if sub_m.empty:
                    sub_m = df[df["mes"] == m]
                perfil_mensual.append({
                    "mes": m,
                    "mes_nombre": MESES_ES[m],
                    "lluvia_mm": round(float(sub_m["lluvia_mm_mes"].mean()), 1),
                    "humedad_raiz": round(float(sub_m["humedad_raiz"].mean()), 3),
                    "temp_max_c": round(float(sub_m["temp_max_c"].mean()), 1)
                })

            if clave_corta == "nino":
                impacto_suelo = f"En eventos históricos de El Niño ({', '.join(map(str, anios_destacados))}), la lluvia mensual promedio subió a {lluvia_prom} mm y la humedad radicular alcanzó {humedad_prom}, elevando el riesgo de saturación en zonas bajas."
            elif clave_corta == "nina":
                impacto_suelo = f"En eventos históricos de La Niña ({', '.join(map(str, anios_destacados))}), la lluvia mensual bajó a {lluvia_prom} mm y la humedad radicular cayó a {humedad_prom}, haciendo crítico contar con riego o cultivos rústicos."
            else:
                impacto_suelo = f"En años Neutros ({', '.join(map(str, anios_destacados))}), el suelo mantiene una humedad radicular equilibrada de {humedad_prom} con {lluvia_prom} mm/mes, ideal para rotación rentable."

            bloque = {
                "clave": clave_corta,
                "etiqueta": etiqueta,
                "meses_historicos_analizados": int(len(sub)),
                "anios_referencia": anios_destacados,
                "indicadores_historicos": {
                    "lluvia_mensual_prom_mm": lluvia_prom,
                    "humedad_raiz_prom": humedad_prom,
                    "temp_max_prom_c": temp_max_prom,
                    "anomalia_oni_prom": oni_prom
                },
                "resumen_impacto_suelo": impacto_suelo,
                "perfil_mensual": perfil_mensual,
                "recomendacion_por_parcela": evaluar_cultivos_por_escenario(df_cultivos, clave_corta, humedad_prom)
            }

            escenarios_data[clave_corta] = bloque
            if fase_csv != clave_corta:
                escenarios_data[fase_csv] = bloque

        simulador["cantones"][cid] = {
            "nombre": meta["nombre"],
            "provincia": meta["provincia"],
            "relieve": meta["relieve"],
            "escenarios": escenarios_data
        }

    ruta_proc = PROC_DIR / "simulador_enso_historico.json"
    with open(ruta_proc, "w", encoding="utf-8") as f:
        json.dump(simulador, f, ensure_ascii=False, indent=2)

    PUBLIC_DATOS_DIR.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ruta_proc, PUBLIC_DATOS_DIR / "simulador_enso_historico.json")
    print(f"[OK] {ruta_proc.name} regenerado y sincronizado en public/datos/.")


if __name__ == "__main__":
    construir_simulador()