package com.kallpa.app.view;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Cifras y palabras sencillas para el agricultor.
 * Todo lo que Kallpa calcula es una referencia: por eso el dinero se redondea y se dice "unos".
 */
public final class Formato {
    private static final Locale ECUADOR = Locale.forLanguageTag("es-EC");

    private Formato() { }

    /** Número redondeado con separador de miles: 5320 -> "5.320". */
    public static String numero(double valor) {
        NumberFormat nf = NumberFormat.getIntegerInstance(ECUADOR);
        nf.setGroupingUsed(true);
        return nf.format(Math.round(valor));
    }

    /** Dinero redondeado para que no parezca exacto: 5318.4 -> "$5.320". */
    public static String dinero(double usd) {
        double abs = Math.abs(usd);
        double redondeado = abs < 100 ? Math.round(usd) : Math.round(usd / 10.0) * 10.0;
        return (usd < 0 ? "-$" : "$") + numero(Math.abs(redondeado));
    }

    /** "unos $5.320": la palabra deja claro que es aproximado. */
    public static String unos(double usd) {
        return "unos " + dinero(usd);
    }

    public static String kilos(double kg) {
        return numero(kg) + " kg";
    }

    /** 2.0 -> "2 ha", 2.5 -> "2,5 ha". */
    public static String hectareas(double ha) {
        NumberFormat nf = NumberFormat.getNumberInstance(ECUADOR);
        nf.setMaximumFractionDigits(1);
        return nf.format(ha) + " ha";
    }

    /** Humedad del modelo (0–1) como porcentaje: 0.668 -> "67 %". */
    public static String porcentaje(double fraccion) {
        return Math.round(fraccion * 100) + " %";
    }

    public static String unDecimal(double valor) {
        NumberFormat nf = NumberFormat.getNumberInstance(ECUADOR);
        nf.setMaximumFractionDigits(1);
        nf.setMinimumFractionDigits(0);
        return nf.format(valor);
    }

    /** Cómo se siente el suelo según la humedad estimada en la raíz. */
    public static String humedadEnPalabras(double humedad) {
        if (humedad < 0.48) return "muy seco";
        if (humedad < 0.55) return "algo seco";
        if (humedad <= 0.78) return "con buena humedad";
        if (humedad <= 0.84) return "muy húmedo";
        return "con exceso de agua";
    }

    /** Verde / Amarillo / Rojo del semáforo en palabras de campo. */
    public static String semaforo(String color) {
        if ("Verde".equalsIgnoreCase(color)) return "Buen momento para sembrar";
        if ("Amarillo".equalsIgnoreCase(color)) return "Puedes sembrar, pero con cuidado";
        return "Mejor espera o prepara bien el terreno";
    }

    /** Evaluación del simulador ("Óptimo", "No recomendado"...) dicha de forma sencilla. */
    public static String evaluacionCultivo(String estado) {
        if (estado == null) return "Sin evaluación";
        if (estado.toLowerCase(Locale.ROOT).startsWith("óptimo")) return "Le fue muy bien en años parecidos";
        if (estado.toLowerCase(Locale.ROOT).startsWith("no recomendado")) return "No te la recomendamos para tu caso";
        return "Puede funcionar, con cuidado";
    }

    /** NDVI (0–1) como "verdor de 0 a 100". */
    public static String verdor(double ndvi) {
        return Math.round(Math.max(0, ndvi) * 100) + " de 100";
    }

    public static final String NOTA_APROXIMADO =
            "Son cifras aproximadas: dependen de tu suelo, del clima y de los precios del momento.";
}
