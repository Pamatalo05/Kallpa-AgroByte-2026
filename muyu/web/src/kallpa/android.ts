import { CONTEXTO_INICIAL, type MuyuContexto } from "../knowledge/contexto";
declare global {
  interface Window { KallpaAndroid?: { contexto(): string; navegar(destino: string): void; cerrar(): void; hablar(texto: string): void; callar(): void } }
}
export const android = typeof window === "undefined" ? undefined : window.KallpaAndroid;
export function contextoAndroid(): MuyuContexto {
  if (!android) return CONTEXTO_INICIAL;
  try {
    const c = JSON.parse(android.contexto());
    const cultivo = String(c.cultivo ?? "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
    return { cantonId: String(c.cantonId ?? "").trim().toLowerCase(),
      cultivoPrevioId: cultivo.includes("maiz") ? "maiz_duro" : cultivo.includes("arroz") ? "arroz" : cultivo,
      escenario: "neutro", mes: c.mes || new Date().getMonth()+1 };
  } catch { return { ...CONTEXTO_INICIAL, cantonId: "" }; }
}
export function destinoPedido(texto: string): string | null {
  const t = texto.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim();
  if (!/^(abre|abrir|ir a|ve a|llevame|quiero (ir|ver|abrir)|muestrame|volver|regresar)/.test(t)) return null;
  if (/bitacora|diario/.test(t)) return "bitacora";
  if (/mis parcelas|mapa/.test(t)) return "parcelas";
  if (/siembra|rotacion/.test(t)) return "siembra";
  if (/clima|alertas|nino|nina/.test(t)) return "clima";
  if (/mi parcela|suelo/.test(t)) return "parcela";
  if (/inicio|casa/.test(t)) return "inicio";
  return null;
}
