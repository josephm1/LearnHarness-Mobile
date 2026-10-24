import fs from 'node:fs';
export function researchSource(root,output){
 const web=fs.readFileSync(new URL('src/agent/tools/web.ts',root),'utf8'),research=fs.readFileSync(new URL('src/agent/subagents/researcher.ts',root),'utf8');const start=research.indexOf('const INSTRUCTIONS ='),end=research.indexOf('\nexport interface',start);if(start<0||end<0)throw Error('Original researcher instructions changed');
 const jobs=fs.readFileSync(new URL('src/agent/tools/jobs.ts',root),'utf8'),schemaStart=jobs.indexOf('export const researchInputSchema'),schemaEnd=jobs.indexOf('export const demoInputSchema'),descStart=jobs.indexOf('const RESEARCH_DESCRIPTION'),descEnd=jobs.indexOf('const DEMO_DESCRIPTION'),ackStart=jobs.indexOf('const RESEARCH_ACK'),ackEnd=jobs.indexOf('const DEMO_ACK');
 const declarations=jobs.slice(schemaStart,schemaEnd)+jobs.slice(descStart,descEnd)+jobs.slice(ackStart,ackEnd);
 const content="import {Type} from 'typebox';\nimport {Readability} from '@mozilla/readability';\nimport {parseHTML} from 'linkedom';\nimport TurndownService from 'turndown';\nimport {serverExecute} from './server-execute.ts';\n"+declarations+'\n'+research.slice(start,end)+'\n'+web.slice(web.indexOf('const USER_AGENT ='))+'\nexport {RESEARCH_DESCRIPTION,RESEARCH_ACK,INSTRUCTIONS,htmlToMarkdown,truncate,MAX_CONTENT_CHARS,MAX_RESPONSE_BYTES};\n';fs.writeFileSync(output,content);
}
