import { useCallback, useMemo, useEffect, useState } from "react";
import { textoParaVoz } from "./textoParaVoz";
import type { Voz } from "./useVoz";

/**
 * Dentro de Kallpa (WebView de Android) el navegador no trae voz: se usa la voz
 * nativa del teléfono a través del puente. Fuera de Kallpa se usa la voz web.
 */
export function useVozKallpa(vozWeb: Voz): Voz {
  const puente = typeof window !== "undefined" ? window.KallpaAndroid : undefined;
  const nativa = Boolean(puente?.hablar);
  const [hablando, setHablando] = useState(false);
  useEffect(() => {
    if (!nativa) return;
    const actualizar = (event: Event) => setHablando(Boolean((event as CustomEvent<boolean>).detail));
    window.addEventListener("muyu-voz", actualizar);
    return () => {
      window.removeEventListener("muyu-voz", actualizar);
      puente?.callar?.();
    };
  }, [nativa, puente]);

  const hablar = useCallback((texto: string) => puente?.hablar?.(textoParaVoz(texto)), [puente]);
  const callar = useCallback(() => puente?.callar?.(), [puente]);

  return useMemo(
    () => (nativa ? { disponible: true, hablando, nombreVoz: "Voz del teléfono", hablar, callar } : vozWeb),
    [nativa, hablando, hablar, callar, vozWeb],
  );
}
