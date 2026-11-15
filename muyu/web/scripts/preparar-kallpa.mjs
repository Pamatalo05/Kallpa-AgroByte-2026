import {readFileSync,copyFileSync,mkdirSync} from "node:fs";
const origen = new URL("../../../app/src/main/assets/datos/paquete_canton.json",import.meta.url);
const datos = JSON.parse(readFileSync(origen,"utf8"));
if (!datos.cantones || !datos.ml_metadata) throw new Error("Paquete Kallpa incompleto");
for (const ruta of ["../public/datos/","../public/datos/nuevo/"]) {
  const destino = new URL(ruta,import.meta.url);
  mkdirSync(destino,{recursive:true});
  copyFileSync(origen,new URL("paquete_canton.json",destino));
}
console.log("Datos web sincronizados con los assets de Android");
