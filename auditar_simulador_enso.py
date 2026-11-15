import json
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
PUBLIC_DATOS = BASE_DIR / "public" / "datos"


def auditar():
    errores = []
    advertencias = []

    ruta_sim = PUBLIC_DATOS / "simulador_enso_historico.json"
    ruta_sat = PUBLIC_DATOS / "evidencia_satelital_parcelas.json"
    ruta_geo = PUBLIC_DATOS / "parcelas_piloto.geojson"

    for r in [ruta_sim, ruta_sat, ruta_geo]:
        if not r.exists():
            errores.append(f"Falta archivo en public/datos/: {r.name}")

    if errores:
        for e in errores:
            print("[ERROR]", e)
        return

    sim = json.loads(ruta_sim.read_text(encoding="utf-8"))
    sat = json.loads(ruta_sat.read_text(encoding="utf-8"))
    geo = json.loads(ruta_geo.read_text(encoding="utf-8"))

    # 1. Verificar cantones exactos
    cantones = set(sim.get("cantones", {}).keys())
    if cantones != {"daule", "babahoyo"}:
        errores.append(f"Cantones inesperados en simulador: {cantones}")

    # 2. Verificar escenarios, 12 meses y diferenciación agronómica
    for cid, cdata in sim.get("cantones", {}).items():
        escenarios = cdata.get("escenarios", {})
        for esc in ["neutro", "nino", "nina"]:
            if esc not in escenarios:
                errores.append(f"{cid}: falta escenario '{esc}'")
                continue
            ed = escenarios[esc]
            perfil = ed.get("perfil_mensual", [])
            if len(perfil) != 12:
                errores.append(f"{cid}/{esc}: tiene {len(perfil)} meses en lugar de 12")

            rec = ed.get("recomendacion_por_parcela", {})
            con_r = rec.get("con_riego", [])
            sin_r = rec.get("sin_riego", [])
            if len(con_r) != 4 or len(sin_r) != 4:
                errores.append(f"{cid}/{esc}: deben existir 4 cultivos en con_riego y sin_riego")

            # Verificar que no haya valores idénticos pegados en todos los cultivos
            ciclos = {x["ciclo_dias"] for x in con_r}
            ahorros = {x["ahorro_urea_kg_ha"] for x in con_r}
            if len(ciclos) == 1 or len(ahorros) == 1:
                errores.append(f"{cid}/{esc}: ciclo_dias o ahorro_urea son idénticos en todos los cultivos")

            # Verificar coherencia agronómica en La Niña sin riego
            if esc == "nina":
                for item in sin_r:
                    if "riego suplementario" in item["nota_agronomica"].lower():
                        errores.append(f"{cid}/nina/sin_riego: nota contradice ausencia de riego en {item['cultivo']}")

    # 3. Verificar mapas y evidencia satelital
    if len(geo.get("features", [])) != 2:
        errores.append(f"parcelas_piloto.geojson debe tener 2 polígonos piloto, tiene {len(geo.get('features', []))}")

    for cid in ["daule", "babahoyo"]:
        if cid not in sat.get("parcelas_satelitales", {}):
            errores.append(f"evidencia_satelital_parcelas.json: falta '{cid}'")

    print("=== RESULTADO AUDITORÍA KALLPA ===")
    if not errores:
        print("[APROBADO] 0 errores. Simulador ENSO (12 meses, Daule/Babahoyo, riego/secano) y datos de mapas 100% validados.")
    else:
        for e in errores:
            print("[FALLO]", e)
    for a in advertencias:
        print("[AVISO]", a)


if __name__ == "__main__":
    auditar()