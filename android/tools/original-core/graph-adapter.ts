import {createHash} from './node-crypto.ts';
import {deleteNodeTraces,deleteChatTraces} from '../../../src/core/graph-deletion.ts';
import * as graphCore from '../../../src/core/graph.ts';
import {coverageOf,batchRuns,messageViewLines} from '../../../src/core/coverage.ts';
import {settleGraphLayout} from '../../../src/core/layout-settle.ts';
import {auditorGraphTools,proberGraphTools} from '../../../src/agent/tools/graph.ts';
import * as extraction from './generated-extraction.ts';
import {healEncodedArguments} from '../../../src/core/argument-heal.ts';
import {Value} from 'typebox/value';

export function ports(request:any){
 const state=structured(request.graph??{});for(const key of ['nodes','chains','coverage','checkpoints','sources'])state[key]??=[];state.positions??={};
 const graphStore:any={readNodes:()=>state.nodes,readChains:()=>state.chains,readCoverage:()=>state.coverage,readCheckpoints:()=>state.checkpoints,readSources:()=>state.sources,readPositions:()=>state.positions,writePositions:(v:any)=>state.positions=v,appendNode:(v:any)=>state.nodes.push(v),appendChain:(v:any)=>state.chains.push(v),appendCoverage:(v:any)=>state.coverage.push(v),appendCheckpoint:(v:any)=>state.checkpoints.push(v),rewriteLedgers:(v:any)=>Object.assign(state,v)};
 const sessions=(request.sessions??[]).map((s:any)=>({...s,createdAt:s.createdAt??new Date(s.created??0).toISOString(),updatedAt:s.updatedAt??new Date(s.created??0).toISOString()}));
 const chatStore:any={getChat:(id:string)=>sessions.find((s:any)=>s.id===id)??null,listChats:()=>sessions};return {state,graphStore,chatStore};
}
const structured=(value:any)=>JSON.parse(JSON.stringify(value));
export function graphDefinitions(auditor:boolean){const p=ports({});return Object.values(auditor?auditorGraphTools(p):proberGraphTools(p)).map((tool:any)=>({name:tool.name,description:tool.description,parameters:tool.parameters}));}
export function graphOperation(request:any):any{
 const p=ports(request),graph=graphCore.getGraph(p.graphStore);
 if(request.operation==='graphDeleteNode' || request.operation==='graphDeleteSession'){
  const stats=request.operation==='graphDeleteNode'?deleteNodeTraces(p.graphStore,request.nodeId):deleteChatTraces(p.graphStore,request.sourceId,request.mode);settleGraphLayout(p.graphStore);return {ok:true,state:p.state,stats,value:graphCore.getGraph(p.graphStore),snapshot:graphCore.graphSnapshot(p)};
 }
 if(request.operation==='graphView')return {ok:true,value:graph,snapshot:graphCore.graphSnapshot(p)};
 if(request.operation==='extractionSetup')return {ok:true,extractor:extraction.EXTRACTOR_INSTRUCTIONS,notes:extraction.NOTES_EXTRACTOR_INSTRUCTIONS,auditor:extraction.AUDITOR_INSTRUCTIONS,schemas:{submit_findings:extraction.sessionFindingsSchema,submit_notes:extraction.findingsSchema,submit_review:extraction.auditSchema},tools:graphDefinitions(true)};
 if(request.operation==='extractionBatches'){
  if(request.notes){
   const note=request.notes,chunks=extraction.chunkNotes(note.text,request.budget??4096),covered=new Set(p.state.coverage.filter((entry:any)=>entry.chatId===note.id).map((entry:any)=>entry.fp));
   const batches=chunks.map((text:string,index:number)=>{const view=extraction.notesView({filePath:note.title,text,part:{i:index+1,n:chunks.length}}),fp=createHash('sha256').update(note.id+'\n'+index+'\n'+text).digest('hex');return {messages:[{id:note.id+'_'+index,fp,tokens:Math.ceil(text.length/4)}],view};});
   return {ok:true,batches:batches.filter((batch:any)=>!covered.has(batch.messages[0].fp)),coveredCount:batches.filter((batch:any)=>covered.has(batch.messages[0].fp)).length,uncoveredCount:batches.filter((batch:any)=>!covered.has(batch.messages[0].fp)).length};
  }
  const session=request.session,native=(session.messages??[]).map((m:any,i:number)=>({...m,id:m.id??`m_${session.id}_${i}`,role:m.origin==='tool'||m.practiceProblem||m.practiceConclusion?'toolResult':m.role}));const coverage=coverageOf(native,p.state.coverage,session.id);
  return {ok:true,coveredCount:coverage.coveredCount,uncoveredCount:coverage.uncoveredCount,batches:batchRuns(coverage.slots,coverage.uncovered,request.budget??4096).map(batch=>({messages:batch.map(slot=>({id:slot.message.id,fp:slot.fp,tokens:slot.tokens})),view:(session.title?`# ${session.title}\n\n`:'')+batch.flatMap(slot=>messageViewLines(slot.message,slot.message.id)).join('\n\n')}))};
 }
 if(request.operation==='extractionAuditInput')return {ok:true,value:extraction.auditInput(request.findings,graph)};
 if(request.operation==='extractionDigest')return {ok:true,value:extraction.sourceDigest(graph,request.sourceId),ids:graph.nodes.map(n=>n.id)};
 if(request.operation==='extractionValidate'){
  const schema=request.name==='submit_review'?extraction.auditSchema:request.notes?extraction.findingsSchema:extraction.sessionFindingsSchema;
  const input=Value.Convert(schema,healEncodedArguments(extraction.healStringifiedArrays(request.input),schema));if(!Value.Check(schema,input))return {ok:false,error:'Arguments do not match the original extraction schema.'};
  if(request.name==='submit_review'){
    const known=new Set(graph.nodes.map(n=>n.id)),submitted=new Set(request.propositionIds??[]);if(input.links.some((l:any)=>!known.has(l.linkTo)||!submitted.has(l.id)))return {ok:false,error:'Every review link must identify a submitted proposition and an existing graph node.'};return {ok:true,value:extraction.fillAudit(input)};
  }
  const filled=request.notes?extraction.fillNotesFindings(input):extraction.fillFindings(input);
  const error=(!request.notes?extraction.validateSessionAnchors(filled,new Set(request.messageIds??[])):null)??extraction.validateFindingsChains(filled,new Set(graph.nodes.map(n=>n.id)));return error?{ok:false,error}:{ok:true,value:filled};
 }
 if(request.operation==='graphMerge'){
  const host=globalThis as any;host.__entropyHex=request._entropyHex??'';host.__entropyOffset=0;
  if(request.notes){const source={id:request.sourceId,filePath:request.notes.title,importedAt:new Date().toISOString()};const index=p.state.sources.findIndex((item:any)=>item.id===source.id);if(index<0)p.state.sources.push(source);else p.state.sources[index]={...p.state.sources[index],...source};}
  const stats=graphCore.mergeFindings(request.sourceId,request.findings,request.review,p,{foldedAt:new Date().toISOString(),...(request.coverage?{coverage:request.coverage}: {})});settleGraphLayout(p.graphStore);return {ok:true,state:p.state,stats,value:graphCore.getGraph(p.graphStore),snapshot:graphCore.graphSnapshot(p)};
 }
 return {ok:false,error:'Unknown graph operation'};
}
export async function graphTool(request:any){try{const p=ports(request),registry=request.auditor?auditorGraphTools(p):proberGraphTools(p),tool=registry[request.name];if(!tool)return {ok:false,error:'Unknown graph tool'};const input=Value.Convert(tool.parameters,healEncodedArguments(request.input,tool.parameters));if(!Value.Check(tool.parameters,input))return {ok:false,error:'Invalid graph tool arguments'};return {ok:true,value:await tool.execute('android',input)};}catch(error){return {ok:false,error:error instanceof Error?error.message:String(error)};}}
