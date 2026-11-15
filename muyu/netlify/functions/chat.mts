/**
 * POST /api/chat — Muyu en vivo con Gemini (plan gratis).
 * La clave GEMINI_API_KEY vive solo en las variables de entorno de Netlify;
 * nunca llega al navegador. La lógica está en web/src/agent/manejarChat.ts.
 */
import { manejarChat } from "../../web/src/agent/manejarChat";

declare const process: { env: Record<string, string | undefined> };

export default async (req: Request): Promise<Response> => {
  const origin = req.headers.get("Origin");
  const headers: Record<string,string> = {"Content-Type":"application/json"};
  if (origin === "https://appassets.androidplatform.net" || origin === new URL(req.url).origin) {
    headers["Access-Control-Allow-Origin"] = origin;
    headers["Access-Control-Allow-Methods"] = "POST, OPTIONS";
    headers["Access-Control-Allow-Headers"] = "Content-Type";
    headers["Vary"] = "Origin";
  }
  if (req.method === "OPTIONS") return new Response(null, {status:204, headers});
  const { status, cuerpo } =
    req.method === "POST"
      ? await manejarChat(await req.text(), process.env)
      : { status: 405, cuerpo: { error: "Usa POST" } };
  return new Response(JSON.stringify(cuerpo), { status, headers });
};

export const config = { path: "/api/chat" };
