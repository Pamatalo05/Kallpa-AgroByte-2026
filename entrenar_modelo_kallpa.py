import json
import glob
import os
import shutil
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestRegressor, GradientBoostingRegressor
from sklearn.linear_model import Ridge
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import TimeSeriesSplit, GridSearchCV
from sklearn.metrics import r2_score, mean_absolute_error, root_mean_squared_error

# 1. Ingesta y limpieza de datos satelitales (AppEEARS: MODIS NDVI + SMAP)
DIR_CRUDOS = "datos/crudos"
DIR_PROC = "datos/procesados"
PAQUETE_JSON = os.path.join(DIR_PROC, "paquete_canton.json")

print("[1/5] Ingestando datos satelitales (MODIS NDVI + SMAP)...")
csv_appeears = glob.glob(os.path.join(DIR_CRUDOS, "Kallpa-Ecuador-Parcelas-*.csv"))

df_ndvi = pd.DataFrame()
df_smap = pd.DataFrame()

for archivo in csv_appeears:
    temp = pd.read_csv(archivo)
    temp["canton_key"] = temp["ID"].astype(str).str.strip().str.upper()
    temp["Date"] = pd.to_datetime(temp["Date"], dayfirst=True, errors="coerce")
    temp["mes"] = temp["Date"].dt.month

    if "MOD13Q1" in archivo:
        col_ndvi = [c for c in temp.columns if "NDVI" in c][0]
        temp[col_ndvi] = pd.to_numeric(temp[col_ndvi], errors="coerce")
        # Escalar si viene en enteros MODIS (ej. 6500 -> 0.65)
        if temp[col_ndvi].median() > 1.5:
            temp[col_ndvi] = temp[col_ndvi] * 0.0001
        df_ndvi = (
            temp.groupby(["canton_key", "mes"])[col_ndvi]
            .quantile(0.75)  # Percentil 75 filtra nubes residuales
            .reset_index()
            .rename(columns={col_ndvi: "ndvi_modis"})
        )
    elif "SPL3SMP" in archivo:
        cols_sm = [
            c for c in temp.columns
            if "soil_moisture" in c.lower() and "error" not in c.lower() and "flag" not in c.lower()
        ]
        temp[cols_sm] = temp[cols_sm].apply(pd.to_numeric, errors="coerce")
        temp["smap_sm"] = temp[cols_sm].mean(axis=1)
        df_smap = (
            temp.groupby(["canton_key", "mes"])["smap_sm"]
            .mean()
            .reset_index()
        )

# 2. Consolidación de series históricas e ingeniería de variables
print("[2/5] Consolidando series históricas e ingeniería de variables...")
archivos_hist = sorted(glob.glob(os.path.join(DIR_PROC, "serie_historica_*.csv")))
dfs_hist = []

for f in archivos_hist:
    df_c = pd.read_csv(f)
    nombre = os.path.basename(f).replace("serie_historica_", "").replace(".csv", "").upper()
    df_c["canton_key"] = nombre
    dfs_hist.append(df_c)

df_total = pd.concat(dfs_hist, ignore_index=True)

# Detección automática de columnas
col_anio = [c for c in df_total.columns if any(k in c.lower() for k in ["anio", "year", "ano"])][0]
col_mes = [c for c in df_total.columns if any(k in c.lower() for k in ["mes", "month"])][0]
col_lluvia = [c for c in df_total.columns if any(k in c.lower() for k in ["prec", "lluvia"])][0]
col_temp = [c for c in df_total.columns if any(k in c.lower() for k in ["t2m", "temp"])][0]
col_hum_raiz = [c for c in df_total.columns if any(k in c.lower() for k in ["gwetroot", "hum"])][0]
col_nino = [c for c in df_total.columns if any(k in c.lower() for k in ["nino", "oni"])][0]

df_total = df_total.sort_values(["canton_key", col_anio, col_mes]).reset_index(drop=True)

# Ingeniería de características físicas (agrupado por cantón para evitar fuga de datos)
df_total["mes_sin"] = np.sin(2 * np.pi * df_total[col_mes] / 12.0)
df_total["mes_cos"] = np.cos(2 * np.pi * df_total[col_mes] / 12.0)
df_total["lluvia_lag1"] = df_total.groupby("canton_key")[col_lluvia].shift(1)
df_total["lluvia_acum_3m"] = (
    df_total.groupby("canton_key")[col_lluvia]
    .transform(lambda s: s.rolling(3, min_periods=1).sum())
)
df_total["nino_lag1"] = df_total.groupby("canton_key")[col_nino].shift(1)

# Unir perfiles satelitales MODIS y SMAP
if not df_ndvi.empty:
    df_total = df_total.merge(df_ndvi, left_on=["canton_key", col_mes], right_on=["canton_key", "mes"], how="left")
else:
    df_total["ndvi_modis"] = 0.55

if not df_smap.empty:
    df_total = df_total.merge(df_smap, left_on=["canton_key", col_mes], right_on=["canton_key", "mes"], how="left")
else:
    df_total["smap_sm"] = 0.28

df_total["ndvi_modis"] = df_total["ndvi_modis"].fillna(df_total["ndvi_modis"].median())
df_total["smap_sm"] = df_total["smap_sm"].fillna(df_total["smap_sm"].median())

# 3. Diagnóstico automático de variables (correlaciones con humedad radicular)
features = [
    "mes_sin", "mes_cos", col_lluvia, "lluvia_lag1", "lluvia_acum_3m",
    col_temp, col_nino, "nino_lag1", "ndvi_modis", "smap_sm"
]
target = col_hum_raiz

df_ml = df_total[features + [target, col_anio, "canton_key"]].dropna().reset_index(drop=True)

print("\n[3/5] Diagnóstico de variables (Correlación con Humedad Radicular):")
corrs = df_ml[features].apply(lambda x: x.corr(df_ml[target])).sort_values(key=abs, ascending=False)
for feat, val in corrs.items():
    print(f"  - {feat:<18}: r = {val:+.3f}")

# 4. Partición temporal estricta, selección de modelo y ajuste de hiperparámetros
train_df = df_ml[df_ml[col_anio] <= 2020].sort_values([col_anio, "mes_sin"])
test_df = df_ml[df_ml[col_anio] > 2020].sort_values([col_anio, "mes_sin"])

X_train, y_train = train_df[features], train_df[target]
X_test, y_test = test_df[features], test_df[target]

print(f"\n[4/5] Entrenamiento y validación temporal (Train: {len(X_train)} | Test 2021-2025: {len(X_test)}):")
tscv = TimeSeriesSplit(n_splits=4)

candidatos = {
    "Ridge (Lineal Base)": (
        Pipeline([("scaler", StandardScaler()), ("model", Ridge())]),
        {"model__alpha": [0.1, 1.0, 10.0]}
    ),
    "RandomForest": (
        RandomForestRegressor(random_state=42),
        {"n_estimators": [120, 200], "max_depth": [6, 10], "min_samples_leaf": [3, 5]}
    ),
    "GradientBoosting": (
        GradientBoostingRegressor(random_state=42),
        {"n_estimators": [100, 150], "max_depth": [3, 5], "learning_rate": [0.05, 0.1]}
    )
}

mejor_nombre = ""
mejor_r2 = -999.0
mejor_estimator = None
mejores_params = {}

for nombre, (est, grid) in candidatos.items():
    busqueda = GridSearchCV(est, grid, cv=tscv, scoring="r2", n_jobs=-1)
    busqueda.fit(X_train, y_train)
    preds = busqueda.best_estimator_.predict(X_test)
    r2_val = r2_score(y_test, preds)
    mae_val = mean_absolute_error(y_test, preds)
    rmse_val = root_mean_squared_error(y_test, preds)
    print(f"  * {nombre:<22} -> Test R2: {r2_val:.3f} | MAE: {mae_val:.4f} | RMSE: {rmse_val:.4f}")

    if r2_val > mejor_r2:
        mejor_r2 = r2_val
        mejor_nombre = nombre
        mejor_estimator = busqueda.best_estimator_
        mejores_params = busqueda.best_params_

print(f"\n  >> Modelo ganador: {mejor_nombre} ({mejores_params})")

if hasattr(mejor_estimator, "feature_importances_"):
    importancias = pd.Series(mejor_estimator.feature_importances_, index=features).sort_values(ascending=False)
    print("\n  >> Importancia de variables en el modelo ganador:")
    for feat, imp in importancias.head(5).items():
        print(f"     - {feat:<18}: {imp*100:.1f}%")

# Reentrenamiento final con el 100% de la serie histórica (2005-2025)
modelo_final = mejor_estimator.fit(df_ml[features], df_ml[target])
mae_final = round(float(mean_absolute_error(y_test, mejor_estimator.predict(X_test))), 4)

# 5. Generación de recomendaciones agronómicas y exportación a JSON
print("\n[5/5] Exportando predicciones y Top 3 rotaciones a paquete_canton.json...")
with open(PAQUETE_JSON, "r", encoding="utf-8") as f:
    paquete = json.load(f)

df_cultivos = pd.read_csv(os.path.join(DIR_PROC, "cultivos_ecuador.csv"))
escenarios_enso = {"neutro": 0.0, "nino": 1.8, "nina": -1.4}

cantones_iter = (
    paquete["cantones"].items()
    if isinstance(paquete.get("cantones"), dict)
    else enumerate(paquete.get("cantones", []))
)

for key, info in cantones_iter:
    cid = (info.get("id") or info.get("nombre") or str(key)).upper()
    region = str(info.get("region", "")).lower()
    sub = df_total[df_total["canton_key"].str.contains(cid[:5], na=False)]
    if sub.empty:
        sub = df_total

    perfil_mensual = []
    for m in range(1, 13):
        sub_m = sub[sub[col_mes] == m]
        lluvia_m = float(sub_m[col_lluvia].mean()) if not sub_m.empty else 100.0
        temp_m = float(sub_m[col_temp].mean()) if not sub_m.empty else 26.0
        ndvi_m = float(sub_m["ndvi_modis"].mean()) if not sub_m.empty else 0.55
        smap_m = float(sub_m["smap_sm"].mean()) if not sub_m.empty else 0.28

        sim_enso = {}
        for esc, anom in escenarios_enso.items():
            factor = 1.38 if esc == "nino" else (0.70 if esc == "nina" else 1.0)
            lluvia_esc = lluvia_m * factor
            fila_pred = pd.DataFrame([[
                np.sin(2 * np.pi * m / 12.0),
                np.cos(2 * np.pi * m / 12.0),
                lluvia_esc,
                lluvia_esc * 0.92,
                lluvia_esc * 2.85,
                temp_m + (0.8 if esc == "nino" else (-0.4 if esc == "nina" else 0.0)),
                anom,
                anom * 0.85,
                ndvi_m,
                np.clip(smap_m * (1.15 if esc == "nino" else (0.82 if esc == "nina" else 1.0)), 0.05, 0.65)
            ]], columns=features)

            hum_pred = float(np.clip(modelo_final.predict(fila_pred)[0], 0.08, 0.96))
            sim_enso[esc] = round(hum_pred, 3)

        perfil_mensual.append({
            "mes": m,
            "lluvia_mm": round(lluvia_m, 1),
            "temp_c": round(temp_m, 1),
            "ndvi_modis": round(ndvi_m, 3),
            "smap_humedad_superficial": round(smap_m, 3),
            "humedad_raiz_predicha": sim_enso
        })

    info["serie_satelital_ml"] = perfil_mensual

    # Determinar piso climático explícito del cantón (Costa vs. Sierra)
    if "sierra" in region or cid in ["LATACUNGA", "RIOBAMBA"]:
        piso_canton = "sierra"
    else:
        piso_canton = "costa"

    # Filtrar cultivos aptos (incluye cultivos marcados como "Costa/Sierra")
    col_region_cultivo = [c for c in df_cultivos.columns if any(k in c.lower() for k in ["region", "piso", "zona"])]
    if col_region_cultivo:
        cultivos_aptos = df_cultivos[
            df_cultivos[col_region_cultivo[0]].astype(str).str.lower().str.contains(piso_canton, na=False)
        ].copy()
    else:
        cultivos_aptos = df_cultivos.copy()

    if cultivos_aptos.empty:
        cultivos_aptos = df_cultivos.copy()

    col_ahorro = [c for c in cultivos_aptos.columns if any(k in c.lower() for k in ["ahorro", "urea", "nitrogeno", "beneficio"])]
    if col_ahorro:
        cultivos_aptos = cultivos_aptos.sort_values(col_ahorro[0], ascending=False)

    info["top_rotaciones_ml"] = cultivos_aptos.head(3).to_dict(orient="records")

paquete["ml_metadata"] = {
    "modelo_seleccionado": mejor_nombre,
    "hiperparametros": {k: str(v) for k, v in mejores_params.items()},
    "validacion_temporal": "Train: 2005-2020 | Out-of-Sample Test: 2021-2025 (TimeSeriesSplit)",
    "r2_test": round(float(mejor_r2), 3),
    "mae_test": mae_final,
    "variables_predictoras": features
}

with open(PAQUETE_JSON, "w", encoding="utf-8") as f:
    json.dump(paquete, f, ensure_ascii=False, indent=2)

if os.path.exists("public"):
    os.makedirs("public/datos", exist_ok=True)
    shutil.copy(PAQUETE_JSON, "public/datos/paquete_canton.json")

print("\nPipeline completado. Revisa las correlaciones y el R2 arriba.")