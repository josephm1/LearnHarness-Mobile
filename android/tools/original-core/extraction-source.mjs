// Package exact upstream schemas, validation helpers and prompts without importing its desktop runner.
import fs from 'node:fs';
export function extractionSource(root,output){
 const read=path=>fs.readFileSync(new URL(path,root),'utf8'),source=read('src/agent/subagents/extraction.ts'),submit=read('src/agent/subagents/submit-agent.ts');
 const slice=(s,a,b)=>{const start=s.indexOf(a),end=s.indexOf(b,start);if(start<0||end<0)throw Error('Upstream extraction declaration changed: '+a);return s.slice(start,end).replace(/^export /gm,'');};
 const auditStart=source.indexOf('`Proposed findings from the extractor'),auditEnd=source.indexOf(',\n\t\t\tsignal,',auditStart);if(auditStart<0||auditEnd<0)throw Error('Upstream audit input changed');
 const auditInput='\nfunction auditInput(findings:any,graph:any){return '+source.slice(auditStart,auditEnd)+';}\n';
 const text="import {Type} from 'typebox';\n"+slice(source,'const propositionProps','// The notes front door')+slice(source,'function fillFindings','// ── the extractor\'s system prompt')+slice(source,'export const SESSION_MESSAGE_ID_RULE','// ── the same-source digest')+slice(source,'export function sourceDigest','/** The extractor over ONE session transcript')+slice(source,'const AUDITOR_INSTRUCTIONS','/** The auditor —')+slice(submit,'export function healStringifiedArrays','export async function runSubmitAgent')+slice(source,'\tfunction notesView','\n\tasync function notesExtract')+auditInput+'\nexport {notesView,chunkNotes,auditInput,sessionFindingsSchema,findingsSchema,auditSchema,fillFindings,fillNotesFindings,fillAudit,validateFindingsChains,validateSessionAnchors,sourceDigest,EXTRACTOR_INSTRUCTIONS,NOTES_EXTRACTOR_INSTRUCTIONS,AUDITOR_INSTRUCTIONS,healStringifiedArrays};\n';
 fs.writeFileSync(output,text);
}
