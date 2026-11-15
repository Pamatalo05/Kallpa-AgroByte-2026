package com.kallpa.app.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Traducción Java de scripts/semaforo.py del equipo AgroByte.
 * La lectura de JSON corresponde a ScienceActivity; este motor no usa red ni Android.
 * Importante: no modificar umbrales, factores ni fórmula de margen sin validar
 * los cambios contra el script original de Arianna.
 */
public final class MotorSemaforo {
    private MotorSemaforo() { }

    public static final double PRECIO_UREA_USD_KG = 38.0 / 50.0;

    public static final class Resultado {
        public final String color;
        public final String estado;
        public final double factorRiesgo;

        private Resultado(String color, String estado, double factorRiesgo) {
            this.color = color;
            this.estado = estado;
            this.factorRiesgo = factorRiesgo;
        }
    }

    /** Reglas e intervalos tomados exactamente de modulo_3_semaforo_futuro(). */
    public static Resultado evaluar(double humedadRaiz, boolean conRiego) {
        if (!Double.isFinite(humedadRaiz)) {
            throw new IllegalArgumentException("La humedad estimada no es válida");
        }
        if (0.55 <= humedadRaiz && humedadRaiz <= 0.78) {
            return new Resultado("Verde", "Óptimo para siembra segura", 0.0);
        } else if ((0.48 <= humedadRaiz && humedadRaiz < 0.55)
                || (0.78 < humedadRaiz && humedadRaiz <= 0.84)) {
            if (humedadRaiz < 0.55 && conRiego) {
                return new Resultado("Verde", "Óptimo (respaldado por riego)", 0.0);
            } else {
                return new Resultado("Amarillo", "Precaución (requiere drenaje o riego)", 0.30);
            }
        } else {
            return new Resultado("Rojo", "No recomendado (riesgo hídrico severo)", 1.0);
        }
    }

    public static final class Economia {
        public final double costoTotalUsd;
        public final double capitalEnRiesgoUsd;
        public final double ahorroUreaUsd;
        public final double margenNetoUsd;

        private Economia(double costo, double riesgo, double ahorro, double margen) {
            this.costoTotalUsd = costo;
            this.capitalEnRiesgoUsd = riesgo;
            this.ahorroUreaUsd = ahorro;
            this.margenNetoUsd = margen;
        }
    }

    /**
     * Fórmulas de semaforo.py:
     * - capital en riesgo = costo_insumos_ha * hectareas * factor
     * - ahorro = ahorro_urea_kg_ha * (38/50) * hectareas
     * - margen = (ingreso_bruto_ha - costo_insumos_ha) * hectareas + ahorro
     * Son referencias, NO ganancias o pérdidas garantizadas.
     */
    public static Economia calcular(double costoInsumosHa, double ingresoBrutoHa,
                                    double ahorroUreaKgHa, double hectareas,
                                    Resultado semaforo) {
        if (semaforo == null || !(hectareas > 0)
                || !Double.isFinite(costoInsumosHa)
                || !Double.isFinite(ingresoBrutoHa)
                || !Double.isFinite(ahorroUreaKgHa)
                || !Double.isFinite(hectareas)) {
            throw new IllegalArgumentException("Faltan datos económicos válidos");
        }
        double costoTotal = costoInsumosHa * hectareas;
        double ahorroUsd = redondear2(ahorroUreaKgHa * PRECIO_UREA_USD_KG * hectareas);
        double margenUsd = redondear2(
                (ingresoBrutoHa - costoInsumosHa) * hectareas + ahorroUsd);
        return new Economia(redondear2(costoTotal),
                redondear2(costoTotal * semaforo.factorRiesgo),
                ahorroUsd, margenUsd);
    }

    private static double redondear2(double numero) {
        return BigDecimal.valueOf(numero).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
    }
}
