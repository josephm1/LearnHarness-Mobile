import fs from 'node:fs';import path from 'node:path';
/** Retain package attribution and licence files for the bundled runtime dependencies. */
export function licenses(here,destination){
 fs.mkdirSync(destination,{recursive:true});const lock=JSON.parse(fs.readFileSync(path.join(here,'package-lock.json'),'utf8'));const notices=[];
 for(const [relative,pkg] of Object.entries(lock.packages)){
  if(!relative || relative.endsWith('/esbuild') || relative.includes('/@esbuild/'))continue;
  const directory=path.join(here,relative);if(!fs.existsSync(directory))continue;
  const manifest=JSON.parse(fs.readFileSync(path.join(directory,'package.json'),'utf8')),name=manifest.name.replace(/[^a-zA-Z0-9.-]/g,'_');const target=path.join(destination,name);fs.mkdirSync(target,{recursive:true});
  const files=fs.readdirSync(directory).filter(file=>/^(licen[cs]e|copying|notice)(\.|$)/i.test(file));if(!files.length)throw Error('Missing runtime licence: '+manifest.name);
  for(const file of files)fs.copyFileSync(path.join(directory,file),path.join(target,file));
  notices.push(`${manifest.name} ${manifest.version} — ${manifest.license??pkg.license??'see included licence'}\n${typeof manifest.repository==='string'?manifest.repository:manifest.repository?.url??''}`);
 }
 fs.writeFileSync(path.join(destination,'DEPENDENCIES.txt'),notices.sort().join('\n\n')+'\n');
}
