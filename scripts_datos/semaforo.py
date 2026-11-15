import json
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
PAQUETE_PATH = BASE_DIR / "datos" / "procesados" / "paquete_canton.json"
SIMULADOR_PATH = BASE_DIR / "datos" / "procesados" / "simulador_enso_historico.json"

PRECIO_UREA_USD_KG = 38.0 / 50.0  # $0.76/kg (Referencia MAG SIPA: saco 50 kg = $38.00)

def modulo_3_semaforo_futuro(canton: str, mes_siembra: int, hectareas: float, tipo_riego: str) -> dict:
    canton_lower = canton.strip().lower()
    riego_key = tipo_riego.strip().lower()  # "con_riego" o "sin_riego"

    with open(PAQUETE_PATH, "r", encoding="utf-8") as f:
        paquete = json.load(f)
    with open(SIMULADOR_PATH, "r", encoding="utf-8") as f:
        simulador = json.load(f)

    # 1. Leer humedad radicular predicha (escenario neutro)
    serie_meses = paquete["cantones"][canton_lower]["serie_satelital_ml"]
    dato_mes = next(m for m in serie_meses if m["mes"] == mes_siembra)
    humedad_raiz_pred = float(dato_mes["humedad_raiz_predicha"]["neutro"])

    # 2. Regla unificada del Semáforo Día Cero + Factor de Riesgo Financiero
    if 0.55 <= humedad_raiz_pred <= 0.78:
        semaforo, estado, factor_riesgo = "Verde", "Óptimo para siembra segura", 0.0
    elif (0.48 <= humedad_raiz_pred < 0.55) or (0.78 < humedad_raiz_pred <= 0.84):
        if humedad_raiz_pred < 0.55 and riego_key == "con_riego":
            semaforo, estado, factor_riesgo = "Verde", "Óptimo (respaldado por riego)", 0.0
        else:
            semaforo, estado, factor_riesgo = "Amarillo", "Precaución (requiere drenaje o riego)", 0.30
    else:
        semaforo, estado, factor_riesgo = "Rojo", "No recomendado (riesgo hídrico severo)", 1.0

    # 3. Ranking diferenciado por riego desde el escenario base ("neutro")
    cultivos_escenario = simulador["cantones"][canton_lower]["escenarios"]["neutro"]["recomendacion_por_parcela"][riego_key]

    ranking = []
    for c in cultivos_escenario:
        costo_total = c["costo_insumos_ha"] * hectareas
        ahorro_usd = round(c["ahorro_urea_kg_ha"] * PRECIO_UREA_USD_KG * hectareas, 2)
        margen_neto = round((c["ingreso_bruto_ha"] - c["costo_insumos_ha"]) * hectareas + ahorro_usd, 2)

        ranking.append({
            "cultivo": c["cultivo"],
            "score_compatibilidad": c["score_compatibilidad"],
            "estado_cultivo": c["estado"],
            "capital_en_riesgo_usd": round(costo_total * factor_riesgo, 2),
            "ahorro_urea_usd": ahorro_usd,
            "margen_neto_usd": margen_neto,
        })

    return {
        "canton": canton.capitalize(),
        "mes_siembra": mes_siembra,
        "humedad_radicular_predicha_ridge": round(humedad_raiz_pred, 3),
        "semaforo_dia_cero": semaforo,
        "estado_riesgo": estado,
        "tipo_riego": riego_key,
        "superficie_ha": hectareas,
        "ranking_mejores_cultivos": ranking,
    }

if __name__ == "__main__":
    print(modulo_3_semaforo_futuro(canton="daule", mes_siembra=3, hectareas=3.0, tipo_riego="sin_riego"))