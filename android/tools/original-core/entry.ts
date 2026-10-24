import './runtime.ts';
import './dom.ts';
import {buildSubagentSteer,buildAbortSteer} from '../../../src/core/contracts/jobs.ts';
import {researchInputSchema,RESEARCH_DESCRIPTION,RESEARCH_ACK,INSTRUCTIONS,htmlToMarkdown,truncate,createWebTools} from './generated-research.ts';
import sourceTools from '../../app/src/main/assets/core/source-tools.json';
import {findSessionTextMatches} from '../../../src/core/session-find.ts';
import {derivePlanStatuses} from '../../../src/core/contracts/plan.ts';
import {graphOperation,graphTool,graphDefinitions} from './graph-adapter.ts';
import practiceSchemas from '../../app/src/main/assets/core/practice-schemas.json';
import {healEncodedArguments} from '../../../src/core/argument-heal.ts';
import {resolveTargetId} from '../../../src/core/chains.ts';
// Adaptation boundary only. Domain validation, grading and plan parsing are upstream code.
import * as questions from '../../../src/core/question-core.ts';
import {HANDOFF_TEXT,CONCLUDE_ACK_TEXT,NEXT_PROBLEM_TEXT,THINKING_LABEL} from '../../../src/core/contracts/chat.ts';
import {PROBE_DONE_ACK_TEXT} from '../../../src/core/contracts/probe.ts';
import {probeDoneInputSchema} from '../../../src/core/contracts/probe.ts';
import {parseMermaidPlan} from '../../../src/agent/tools/mermaid-plan.ts';
import {createTree} from '../../../src/core/plan-tree.ts';
import {Value} from 'typebox/value';
export const schemas={research:researchInputSchema,...Object.fromEntries(sourceTools.map(tool=>[tool.name,tool.parameters])),...practiceSchemas,probe:questions.probeQuizInputSchema,quiz:questions.quizInputObject,recall:questions.explainInputBase,explain:questions.explainInputObject,ask_user_question:questions.askInputSchema,probe_done:probeDoneInputSchema,plan_init:{type:'object',properties:{mermaid:{type:'string',minLength:1}},required:['mermaid']}};
export function handle(request:any):any{
  try {
    const {operation,name,input}=request;
    if(operation==='jobSteer')return {ok:true,value:request.aborted?buildAbortSteer(request.job):buildSubagentSteer(request.job,request.result)};
    if(operation==='jobDefinition')return {ok:true,value:{name:'research',description:RESEARCH_DESCRIPTION,parameters:researchInputSchema}};
    if(operation==='jobAck')return {ok:true,value:`Dispatched (job ${request.id}). ${RESEARCH_ACK}`};
    if(operation==='researchSetup')return {ok:true,prompt:INSTRUCTIONS,tools:Object.values(createWebTools(()=>null)).map((tool:any)=>({name:tool.name,description:tool.description,parameters:tool.parameters}))};
    if(operation==='webExtract'){const article=htmlToMarkdown(request.html);return article?{ok:true,title:article.title,value:truncate(article.content)}:{ok:false,error:'No readable article content was found.'};}
    if(operation==='sessionFind')return {ok:true,value:findSessionTextMatches(request.text,request.query)};
    if(operation==='planStatuses'){
      const messages=(request.history??[]).map((message:any)=>({parts:(message.tool_calls??[]).map((call:any)=>{try{return {type:'tool-'+call.function.name,input:JSON.parse(call.function.arguments)};}catch{return {type:'invalid'};}})}));const statuses=derivePlanStatuses(request.tree,messages);return {ok:true,current:statuses.current,visited:[...statuses.visited]};
    }
    if(operation==='graphDefinitions')return {ok:true,value:graphDefinitions(request.auditor??false)};
    if(operation.startsWith('graph') || operation.startsWith('extraction'))return graphOperation(request);
    if(operation==='practiceMarkers')return {ok:true,conclude:CONCLUDE_ACK_TEXT,next:NEXT_PROBLEM_TEXT,thinking:THINKING_LABEL,posed:"Problem posed — the turn ends here; the learner's thinking traces and answer commits arrive as labeled messages."};
    if(operation==='handoff')return {ok:true,value:HANDOFF_TEXT,ack:PROBE_DONE_ACK_TEXT};
    if(operation==='schemas')return {ok:true,value:schemas};
    if(operation==='plan'){
      const parsed=parseMermaidPlan(input.mermaid);if(!parsed.ok)return {ok:false,error:parsed.problems.join('\n')};
      return {ok:true,value:createTree(parsed.plan!.nodes,parsed.plan!.edges)};
    }
    if(operation==='validate' || operation==='question'){
      const schema=(schemas as any)[name];if(!schema)return {ok:false,error:'Unknown tool'};
      let converted=Value.Convert(schema,healEncodedArguments(input,schema));
      if(name==='problem'){
        if(Array.isArray(converted.target) && converted.target.length===0){converted={...converted};delete converted.target;}
        if(Array.isArray(converted.target) && request.nodes){converted={...converted,target:converted.target.map((target:any)=>typeof target==='string'?(resolveTargetId(target,request.nodes)??target):target)};
          if(converted.target.some((target:string)=>resolveTargetId(target,request.nodes)===null))return {ok:false,error:'target is not a known proposition — use exact session proposition ids, or omit target for a topic problem.'};}
      }if(converted.options!==undefined)converted={...converted,options:questions.parseOptionsIfString(converted.options)};
      if(!Value.Check(schema,converted))return {ok:false,error:'Arguments do not match the original tool schema.'};
      const problems=name==='quiz'||name==='probe'?questions.quizInputProblems(converted):name==='explain'||name==='recall'?questions.explainInputProblems(converted):name==='ask_user_question'?questions.askOptionProblems(converted.options??[]):[];
      if(problems.length)return {ok:false,error:problems.join('\n')};
      if(operation==='validate')return {ok:true,value:converted};
      const kind=name==='probe'||name==='quiz'?'quiz':name==='recall'||name==='explain'?'explain':'ask';
      let options=questions.normalizeOptions(converted.options);if(kind==='quiz')options=questions.shuffleOptions(options,questions.seededRandom(request.id));
      const resolved=kind==='quiz'?questions.resolveCorrect(converted.correctAnswer,options):{indices:[]};if(resolved.error)return {ok:false,error:resolved.error};
      return {ok:true,value:{id:request.id,kind,tool:name,questionType:converted.type,needsReveal:questions.questionNeedsReveal(name,converted,'teach'),question:converted.question,context:converted.details,mode:options.length?(converted.multiSelect?'multi-select':'single-select'):'text',options,correctIndices:resolved.indices,committedAnswer:kind==='explain'?converted.correctAnswer:undefined,explanation:converted.explanation,nodeId:converted.nodeId,createdAt:new Date().toISOString()}};
    }
    if(operation==='answer'){
      const answer=questions.buildAnswer(request.question,request.answer,request.question.tool);
      return {ok:true,value:answer,model:questions.compactAnswerForModel(answer)};
    }
    return {ok:false,error:'Unsupported core operation'};
  }catch(error){return {ok:false,error:error instanceof Error?error.message:String(error)};}
}

export async function asyncHandle(request:any){return request.operation==='graphTool'?graphTool(request):handle(request);}
