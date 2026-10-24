import fs from 'node:fs';import vm from 'node:vm';import {Type} from 'typebox';
export function sourceDeclarations(root){
 const limits={ls:500,find:1000,grep:100,read:2000};const tools=[];
 for(const name of ['ls','find','grep','read']){
  const source=fs.readFileSync(new URL(`src/agent/tools/${name}.ts`,root),'utf8');const start=source.indexOf(`const ${name}Input =`),end=source.indexOf('\n});',start);if(start<0||end<0)throw Error('Upstream source tool schema changed: '+name);
  const declared=source.slice(start,end+4).replace(`const ${name}Input =`,'globalThis.schema =');const toolStart=source.indexOf(`export const ${name}Tool`),descriptionStart=source.indexOf('description:',toolStart),descriptionEnd=source.indexOf(',\n\tparameters:',descriptionStart);if(descriptionStart<0||descriptionEnd<0)throw Error('Upstream source tool description changed: '+name);
  const context=vm.createContext({Type,DEFAULT_LIMIT:limits[name],MAX_OUTPUT_BYTES:50*1024,GREP_MAX_LINE_LENGTH:500,formatSize:bytes=>`${(bytes/1024).toFixed(1)}KB`});vm.runInContext(declared+'\nglobalThis.description = '+source.slice(descriptionStart+'description:'.length,descriptionEnd),context);tools.push({name,description:context.description,parameters:context.schema});
 }
 return tools;
}
