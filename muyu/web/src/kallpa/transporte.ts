/** Only the fixed Muyu endpoint uses Android transport. Other requests keep browser fetch. */
export const API_NATIVA = "https://muyu-agrobyte.netlify.app/api/chat";
export const fetchMuyu: typeof fetch = async (input, init) => {
  const puente = typeof window === "undefined" ? undefined : window.KallpaAndroid;
  if (!puente?.solicitar || String(input) !== API_NATIVA) return fetch(input, init);
  const metodo = (init?.method ?? "GET").toUpperCase();
  if (metodo !== "POST" && metodo !== "HEAD") throw new Error("Método no admitido");
  if (init?.signal?.aborted) throw new DOMException("Cancelada", "AbortError");
  const id = crypto.randomUUID();
  return new Promise<Response>((resolve, reject) => {
    let terminado = false;
    const limpiar = () => {
      terminado = true;
      window.removeEventListener("muyu-respuesta", recibir);
      init?.signal?.removeEventListener("abort", cancelar);
      clearTimeout(limite);
    };
    const cancelar = () => {
      if (terminado) return;
      limpiar();
      puente.cancelar?.(id);
      reject(new DOMException("Cancelada", "AbortError"));
    };
    const recibir = (event: Event) => {
      const d = (event as CustomEvent<{id:string;status:number;cuerpo:string;error:boolean}>).detail;
      if (!d || d.id !== id || terminado) return;
      limpiar();
      if (d.error) reject(new Error("No pude conectar con Muyu"));
      else resolve(new Response(metodo === "HEAD" ? null : d.cuerpo, {status:d.status}));
    };
    const limite = setTimeout(cancelar, 25000);
    window.addEventListener("muyu-respuesta", recibir);
    init?.signal?.addEventListener("abort", cancelar, {once:true});
    try { puente.solicitar!(id, metodo, typeof init?.body === "string" ? init.body : ""); }
    catch { limpiar(); reject(new Error("No pude iniciar la consulta a Muyu")); }
  });
};
