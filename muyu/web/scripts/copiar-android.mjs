import {cpSync,mkdirSync,existsSync} from "node:fs";
const destino = new URL("../../../app/src/main/assets/muyu/", import.meta.url);
mkdirSync(destino,{recursive:true});
cpSync(new URL("../dist/",import.meta.url),destino,{recursive:true});
if (!existsSync(new URL("../../../app/src/main/assets/muyu/index.html",import.meta.url))) throw new Error("Falta index de Android");
console.log("Muyu empaquetado en Android");
