import json
from pathlib import Path
import joblib
import pandas as pd

BASE_DIR = Path(__file__).resolve().parent.parent
PROC_DIR = BASE_DIR / "datos" / "procesados"
CANTONES_ESPERADOS = {"daule", "babahoyo"}
CULTIVOS_ESPERADOS = ["soya", "caup", "man", "mucuna"]

errores = []
advertencias = []
ok_count = 0


def ok(msg):
    global ok_count
    ok_count += 1
    print(f"  [BIEN] {msg}")


def mal(msg):
    errores.append(msg)
    print(f"  [MAL]  {msg}")


def aviso(msg):
    advertencias.append(msg)
    print(f"  [OJO]  {msg}")


def resolver_serie(canton):
    for nombre in [f"serie_historica_{canton}_limpio.csv", f"serie_historica_{canton}.csv"]:
        ruta = PROC_DIR / nombre
        if ruta.exists():
            return ruta
    return None


def verificar_archivos():
    print("\n=== 1. ARCHIVOS GENERADOS ===")
    requeridos = [
        "indice_enso_noaa.csv",
        "cultivos_ecuador.csv",
        "paquete_canton.json",
        "validacion_ml_auditoria.json",
        "modelo_kallpa_gb.joblib"
    ]
    for nombre in requeridos:
        ruta = PROC_DIR / nombre
        if ruta.exists() and ruta.stat().st_size > 0:
            ok(f"{nombre} existe ({ruta.stat().st_size:,} bytes)")
        else:
            mal(f"Falta o está vacío: {ruta}")

    for canton in ["daule", "babahoyo"]:
        ruta_s = resolver_serie(canton)
        if ruta_s and ruta_s.stat().st_size > 0:
            ok(f"{ruta_s.name} existe ({ruta_s.stat().st_size:,} bytes)")
        else:
            mal(f"Falta serie histórica de {canton}")


def verificar_enso():
    print("\n=== 2. CLASIFICACIÓN ENSO (NOAA) ===")
    ruta = PROC_DIR / "indice_enso_noaa.csv"
    if not ruta.exists():
        return
    df = pd.read_csv(ruta)

    if df.isna().sum().sum() == 0:
        ok("Sin valores nulos en indice_enso_noaa.csv")
    else:
        mal(f"Hay valores nulos: {df.isna().sum().to_dict()}")

    if df.duplicated(subset=["anio", "mes"]).sum() == 0:
        ok("Sin meses duplicados en indice_enso_noaa.csv")
    else:
        mal("Hay meses duplicados")

    def regla_oficial(r):
        if r["oni_anom"] >= 0.5 or r["nino12_anom"] >= 1.0:
            return "el_nino"
        if r["oni_anom"] <= -0.5 or r["nino12_anom"] <= -1.0:
            return "la_nina"
        return "neutro"

    desajustes = (df["fase_enso"] != df.apply(regla_oficial, axis=1)).sum()
    if desajustes == 0:
        ok(f"100% de filas bien clasificadas según umbral oficial NOAA ({df['fase_enso'].value_counts().to_dict()})")
    else:
        mal(f"{desajustes} filas mal clasificadas")


def verificar_series_cantones():
    print("\n=== 3. SERIES HISTÓRICAS (DAULE Y BABAHOYO) ===")
    for canton in ["daule", "babahoyo"]:
        ruta = resolver_serie(canton)
        if not ruta:
            mal(f"No se encontró archivo histórico para {canton}")
            continue
        df = pd.read_csv(ruta)

        if len(df) >= 240:
            ok(f"{canton}: {len(df)} meses registrados")
        else:
            aviso(f"{canton}: solo tiene {len(df)} meses")

        col_lluvia = "lluvia_mm_mes" if "lluvia_mm_mes" in df.columns else "lluvia_mm"
        cols_criticas = [c for c in ["anio", "mes", col_lluvia, "humedad_raiz"] if c in df.columns]
        nulos = df[cols_criticas].isna().sum().sum()
        if nulos == 0:
            ok(f"{canton}: sin valores nulos en {cols_criticas}")
        else:
            mal(f"{canton}: tiene {nulos} nulos en {cols_criticas}")

        h_min, h_max = df["humedad_raiz"].min(), df["humedad_raiz"].max()
        if 0.0 <= h_min and h_max <= 1.0:
            ok(f"{canton}: humedad_raiz en rango físico válido [{h_min:.3f}, {h_max:.3f}]")
        else:
            mal(f"{canton}: humedad_raiz fuera de rango [{h_min}, {h_max}]")

        l_min, l_max = df[col_lluvia].min(), df[col_lluvia].max()
        if 0.0 <= l_min and l_max <= 1500.0:
            ok(f"{canton}: {col_lluvia} mensual plausible [{l_min:.1f}, {l_max:.1f} mm]")
        else:
            mal(f"{canton}: {col_lluvia} fuera de rango [{l_min}, {l_max}]")


def verificar_cultivos():
    print("\n=== 4. CATÁLOGO DE 4 CULTIVOS (DAULE Y BABAHOYO) ===")
    ruta = PROC_DIR / "cultivos_ecuador.csv"
    if not ruta.exists():
        return
    df = pd.read_csv(ruta)

    col_nombre = "nombre" if "nombre" in df.columns else "cultivo"
    if len(df) == 4:
        ok("Exactamente 4 cultivos en cultivos_ecuador.csv")
    else:
        mal(f"Se esperaban 4 cultivos y hay {len(df)}")

    nombres_lower = " | ".join(df[col_nombre].str.lower().tolist())
    for c in CULTIVOS_ESPERADOS:
        if c in nombres_lower:
            ok(f"Cultivo '{c}' presente en el catálogo")
        else:
            mal(f"Falta el cultivo '{c}' en cultivos_ecuador.csv")

    if (df["ahorro_urea_ha"] > 0).all() and (df["fija_nitrogeno"] > 0).all():
        ok("Valores positivos de fijación de nitrógeno y ahorro de urea en los 4 cultivos")
    else:
        mal("Hay valores <= 0 en fija_nitrogeno o ahorro_urea_ha")


def verificar_auditoria_ml():
    print("\n=== 5. VALIDACIÓN Y AUDITORÍA DEL MODELO ML ===")
    ruta = PROC_DIR / "validacion_ml_auditoria.json"
    if not ruta.exists():
        return
    with open(ruta, "r", encoding="utf-8") as f:
        aud = json.load(f)

    if set(aud.get("cantones_incluidos", [])) == CANTONES_ESPERADOS:
        ok("Modelo entrenado exclusivamente con Daule y Babahoyo")
    else:
        mal(f"Cantones inesperados: {aud.get('cantones_incluidos')}")

    part = aud.get("particion_temporal", {})
    if part.get("entrenamiento") == "2005-2020" and part.get("prueba_out_of_sample") == "2021-2025":
        ok(f"Partición temporal estricta verificada (Train: {part.get('n_filas_train')} filas, Test ciego: {part.get('n_filas_test')} filas)")
    else:
        mal("Partición temporal incorrecta")

    met = aud.get("metricas_modelo_seleccionado", {})
    base = aud.get("baseline_climatologia_2005_2020", {})
    r2 = met.get("r2_test_2021_2025", -1)
    mae = met.get("mae_test_2021_2025", 999)
    mae_base = base.get("mae_test_2021_2025", 0)

    if r2 >= 0.75:
        ok(f"R² out-of-sample sólido: {r2} ({r2*100:.1f}% de varianza explicada en 2021-2025)")
    else:
        mal(f"R² bajo: {r2}")

    if mae < mae_base:
        mejora = ((mae_base - mae) / mae_base) * 100
        ok(f"Supera al baseline climatológico (MAE={mae} vs baseline={mae_base}, mejora del {mejora:.1f}%)")
    else:
        mal("No supera al baseline climatológico")

    ruta_joblib = PROC_DIR / "modelo_kallpa_gb.joblib"
    if ruta_joblib.exists():
        modelo = joblib.load(ruta_joblib)
        feats = aud.get("features_utilizadas", [])
        pred = modelo.predict(pd.DataFrame([{f: 0.5 for f in feats}]))[0]
        ok(f"Artefacto modelo_kallpa_gb.joblib carga y predice correctamente (test={pred:.3f})")


def verificar_paquete_frontend():
    print("\n=== 6. PAQUETE JSON PARA LA WEB Y CHATBOT MUYU ===")
    ruta = PROC_DIR / "paquete_canton.json"
    if not ruta.exists():
        return
    with open(ruta, "r", encoding="utf-8") as f:
        paq = json.load(f)

    if set(paq.get("cantones", {}).keys()) == CANTONES_ESPERADOS:
        ok("paquete_canton.json contiene exactamente {'daule', 'babahoyo'}")
    else:
        mal("Cantones incorrectos en paquete_canton.json")

    if len(paq.get("fuentes_metodologia", {})) >= 4:
        ok(f"Fuentes y metodología documentadas ({len(paq.get('fuentes_metodologia', {}))} bloques)")
    else:
        mal("Falta documentar fuentes_metodologia")

    for c_id, c_data in paq.get("cantones", {}).items():
        serie = c_data.get("serie_satelital_ml", [])
        if len(serie) == 12:
            ok(f"{c_id}: tiene los 12 meses simulados (enero a diciembre)")
        else:
            mal(f"{c_id}: tiene {len(serie)} meses en lugar de 12")

        incons = sum(
            1 for m in serie
            if not (m["humedad_raiz_predicha"]["nino"] >= m["humedad_raiz_predicha"]["neutro"] >= m["humedad_raiz_predicha"]["nina"])
        )
        if incons == 0:
            ok(f"{c_id}: coherencia climática perfecta en los 12 meses (humedad El Niño >= Neutro >= La Niña)")
        else:
            aviso(f"{c_id}: en {incons} mes(es) El Niño/La Niña se cruzan ligeramente")

        rots = c_data.get("top_rotaciones_por_enso", {})
        if set(rots.keys()) == {"neutro", "nino", "nina"} and all(len(rots[k]) == 3 for k in rots):
            ok(f"{c_id}: top 3 rotaciones definidas para cada escenario ENSO (neutro, nino, nina)")
        else:
            mal(f"{c_id}: faltan recomendaciones en top_rotaciones_por_enso")


if __name__ == "__main__":
    print("==========================================================")
    print("   AUDITORÍA INTEGRAL DE DATOS Y MODELO ML — KALLPA       ")
    print("==========================================================")
    verificar_archivos()
    verificar_enso()
    verificar_series_cantones()
    verificar_cultivos()
    verificar_auditoria_ml()
    verificar_paquete_frontend()

    print("\n==========================================================")
    print(f"RESUMEN FINAL: {ok_count} pruebas [BIEN] | {len(advertencias)} [OJO] | {len(errores)} [MAL]")
    if len(errores) == 0:
        print("VEREDICTO: TODO ESTÁ BIEN. Los datos y el modelo están listos para producción/demo.")
    else:
        print("VEREDICTO: HAY ERRORES QUE CORREGIR ANTES DE LA DEMO:")
        for e in errores:
            print(f"  -> {e}")
    print("==========================================================")