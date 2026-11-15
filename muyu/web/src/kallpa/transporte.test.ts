import {afterEach,beforeEach,describe,expect,it,vi} from "vitest";
import {API_NATIVA,fetchMuyu} from "./transporte";
import type {PuenteKallpa} from "./puente";
let bus: EventTarget;
let solicitar: ReturnType<typeof vi.fn>;
let cancelar: ReturnType<typeof vi.fn>;
beforeEach(() => {
  bus = new EventTarget();
  solicitar = vi.fn(); cancelar = vi.fn();
  vi.stubGlobal("window", {
    KallpaAndroid: {contexto:() => "{}",solicitar,cancelar} as PuenteKallpa,
    addEventListener: bus.addEventListener.bind(bus),
    removeEventListener: bus.removeEventListener.bind(bus),
  });
});
afterEach(() => {vi.unstubAllGlobals();vi.useRealTimers();});
function respuesta(id:string,status=200,cuerpo='{"text":"Respuesta"}',error=false) {
  const event = new Event("muyu-respuesta");
  Object.assign(event,{detail:{id,status,cuerpo,error}});
  bus.dispatchEvent(event);
}
describe("transporte Android de Muyu",() => {
 it("entrega la respuesta nativa sin fetch del navegador",async () => {
   const navegador=vi.fn();vi.stubGlobal("fetch",navegador);
   const p=fetchMuyu(API_NATIVA,{method:"POST",body:'{"pregunta":"Hola"}'});
   const id=solicitar.mock.calls[0][0]; respuesta("otra");respuesta(id);
   expect((await (await p).json()).text).toBe("Respuesta");
   expect(navegador).not.toHaveBeenCalled();
 });
 it("HEAD comprueba salud sin invocar Gemini",async () => {
   const p=fetchMuyu(API_NATIVA,{method:"HEAD"});
   const [id,metodo,cuerpo]=solicitar.mock.calls[0];
   expect([metodo,cuerpo]).toEqual(["HEAD",""]);respuesta(id,405,"");
   expect((await p).status).toBe(405);
 });
 it("cancela el trabajo Android y descarta resultados tardíos",async () => {
   const c=new AbortController();const p=fetchMuyu(API_NATIVA,{method:"POST",signal:c.signal});
   const id=solicitar.mock.calls[0][0];c.abort();respuesta(id);
   await expect(p).rejects.toMatchObject({name:"AbortError"});
   expect(cancelar).toHaveBeenCalledWith(id);
 });
 it("no inicia una petición ya cancelada",async () => {
   const c=new AbortController();c.abort();
   await expect(fetchMuyu(API_NATIVA,{method:"POST",signal:c.signal})).rejects.toMatchObject({name:"AbortError"});
   expect(solicitar).not.toHaveBeenCalled();
 });
 it("limita la espera aunque Android nunca responda",async () => {
   vi.useFakeTimers();
   const p=fetchMuyu(API_NATIVA,{method:"POST"});
   const rechazado=expect(p).rejects.toMatchObject({name:"AbortError"});
   await vi.advanceTimersByTimeAsync(25000); await rechazado;
   expect(cancelar).toHaveBeenCalled();
 });
 it("permite fallback a campo ante un error de red nativo",async () => {
   const p=fetchMuyu(API_NATIVA,{method:"POST"});
   respuesta(solicitar.mock.calls[0][0],502,"",true);
   await expect(p).rejects.toThrow("conectar");
 });
 it("otros destinos usan fetch normal",async () => {
   const navegador=vi.fn().mockResolvedValue(new Response("ok"));vi.stubGlobal("fetch",navegador);
   await fetchMuyu("/datos/paquete_canton.json");
   expect(navegador).toHaveBeenCalled();expect(solicitar).not.toHaveBeenCalled();
 });
});
