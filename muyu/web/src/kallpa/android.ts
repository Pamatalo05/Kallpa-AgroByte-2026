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
