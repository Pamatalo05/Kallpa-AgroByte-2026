import {describe,it,expect} from "vitest";
import {destinoPedido} from "./android";
describe("navegación Kallpa", () => {
  it.each([["abre mis parcelas","parcelas"],["llévame a la bitácora","bitacora"],["abre siembra","siembra"],["ve a clima","clima"],["muestrame mi parcela","parcela"],["volver al inicio","inicio"]])("%s abre %s",(q,d) => expect(destinoPedido(q)).toBe(d));
  it.each(["¿Qué siembro después del arroz?","¿Cómo está mi parcela?","borra mis parcelas","abre mi banco"])("no navega con %s",q => expect(destinoPedido(q)).toBeNull());
});
