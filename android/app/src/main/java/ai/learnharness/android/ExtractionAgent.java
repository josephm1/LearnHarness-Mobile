package ai.learnharness.android;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.time.Instant;

/** Original extract → identity audit → deterministic merge, with per-batch durable checkpoints. */
public final class ExtractionAgent {
    public interface Checkpoint {void save(JSONObject state,JSONObject view,JSONObject usage,int batch,int total)throws Exception;}
    private final Context context;private long inputTokens,outputTokens;private final JSONArray stages=new JSONArray();
    public ExtractionAgent(Context context){this.context=context;}
    private JSONObject base(JSONObject task,JSONObject graph,String operation)throws Exception{return new JSONObject().put("operation",operation).put("graph",graph).put("sessions",task.getJSONArray("sessions"));}
    public ProviderClient.Reply run(JSONObject task,LessonAgent.Model model,Checkpoint checkpoint)throws Exception{
        JSONObject graph=new JSONObject(task.getJSONObject("graph").toString());boolean notes=task.has("notes");String sourceId=notes?task.getJSONObject("notes").getString("id"):task.getJSONObject("session").getString("id");int nodes=0,chains=0;
        try(OriginalCore core=new OriginalCore(context)){
            JSONObject batchRequest=base(task,graph,"extractionBatches").put("budget",task.optInt("budget",4096));if(notes)batchRequest.put("notes",task.getJSONObject("notes"));else batchRequest.put("session",task.getJSONObject("session"));
            JSONObject setup=checked(core.call(base(task,graph,"extractionSetup"))),batches=checked(core.call(batchRequest));JSONArray runs=batches.getJSONArray("batches");
            for(int batchIndex=0;batchIndex<runs.length();batchIndex++){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();long batchInput=inputTokens,batchOutput=outputTokens;int stageStart=stages.length();JSONObject batch=runs.getJSONObject(batchIndex);JSONArray messageIds=new JSONArray();for(int i=0;i<batch.getJSONArray("messages").length();i++)messageIds.put(batch.getJSONArray("messages").getJSONObject(i).getString("id"));
                JSONObject digest=checked(core.call(base(task,graph,"extractionDigest").put("sourceId",sourceId)));String view=batch.getString("view"),prefix=digest.getString("value");if(!prefix.isEmpty())view=prefix+"\n\n"+view;
                JSONObject findings=submit(core,task,graph,setup,model,false,view,messageIds,null);
                JSONObject auditInput=checked(core.call(base(task,graph,"extractionAuditInput").put("findings",findings)));JSONObject review=submit(core,task,graph,setup,model,true,auditInput.getString("value"),messageIds,findings);
                JSONArray coverage=new JSONArray();String foldedAt=Instant.now().toString();for(int i=0;i<batch.getJSONArray("messages").length();i++){JSONObject slot=batch.getJSONArray("messages").getJSONObject(i);coverage.put(new JSONObject().put("chatId",sourceId).put("fp",slot.getString("fp")).put("tokens",slot.getInt("tokens")).put("foldedAt",foldedAt));}
                JSONObject merge=base(task,graph,"graphMerge").put("sourceId",sourceId).put("findings",findings).put("review",review).put("coverage",coverage);if(notes)merge.put("notes",task.getJSONObject("notes"));JSONObject merged=checked(core.call(merge));graph=merged.getJSONObject("state");nodes+=merged.getJSONObject("stats").optInt("nodes");chains+=merged.getJSONObject("stats").optInt("chains");
                JSONArray batchStages=new JSONArray();for(int i=stageStart;i<stages.length();i++)batchStages.put(stages.get(i));JSONObject usage=new JSONObject().put("sourceId",sourceId).put("at",foldedAt).put("batch",batchIndex+1).put("inputTokens",inputTokens-batchInput).put("outputTokens",outputTokens-batchOutput).put("stages",batchStages);if(checkpoint!=null)checkpoint.save(graph,merged.getJSONObject("value"),usage,batchIndex+1,runs.length());
            }
            JSONObject finalView=checked(core.call(base(task,graph,"graphView")));return new ProviderClient.Reply(runs.length()==0?"No uncovered teacher passages. Nothing was sent to a model.":"Extracted "+runs.length()+" batch(es): "+nodes+" new propositions and "+chains+" chain assertions.",inputTokens,outputTokens,new JSONArray(),new JSONObject().put("graph",graph).put("view",finalView.getJSONObject("value")).put("snapshot",finalView.getJSONObject("snapshot")));
        }
    }
    private JSONObject submit(OriginalCore core,JSONObject task,JSONObject graph,JSONObject setup,LessonAgent.Model model,boolean audit,String prompt,JSONArray messageIds,JSONObject findings)throws Exception{
        String toolName=audit?"submit_review":"submit_findings";JSONArray definitions=new JSONArray();if(audit)for(int i=0;i<setup.getJSONArray("tools").length();i++)definitions.put(setup.getJSONArray("tools").get(i));
        definitions.put(new JSONObject().put("name",toolName).put("description",audit?"Submit the identity verdicts ONLY (links) — never the propositions themselves.":task.has("notes")?"Submit the notes findings.":"Submit the session findings.").put("parameters",setup.getJSONObject("schemas").getJSONObject(!audit && task.has("notes")?"submit_notes":toolName)));
        JSONArray history=new JSONArray().put(new JSONObject().put("role","user").put("content",prompt));int limit=audit?16:4;JSONArray propositionIds=new JSONArray();if(findings!=null)for(int i=0;i<findings.getJSONArray("propositions").length();i++)propositionIds.put(findings.getJSONArray("propositions").getJSONObject(i).getString("id"));
        for(int step=0;step<limit;step++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();ProviderClient.Reply reply=model.completeRole(audit?"auditor":"extractor",setup.getString(audit?"auditor":task.has("notes")?"notes":"extractor"),history,definitions);inputTokens+=reply.inputTokens();outputTokens+=reply.outputTokens();stages.put(new JSONObject().put("stage",audit?"auditor":"extractor").put("step",step+1).put("inputTokens",reply.inputTokens()).put("outputTokens",reply.outputTokens()));history.put(ToolProtocol.assistant(reply));
            if(reply.toolCalls().length()==0)throw new IOException("The "+(audit?"auditor":"extractor")+" finished without submitting its original tool contract. Earlier completed batches remain saved; retry the uncovered passages.");
            for(int i=0;i<reply.toolCalls().length();i++){
                JSONObject call=reply.toolCalls().getJSONObject(i),function=call.getJSONObject("function");String name=function.getString("name");JSONObject result;
                try{
                    JSONObject args=new JSONObject(function.getString("arguments"));
                    if(name.equals(toolName)){result=core.call(base(task,graph,"extractionValidate").put("name",name).put("input",args).put("messageIds",messageIds).put("propositionIds",propositionIds).put("notes",task.has("notes")));if(result.optBoolean("ok"))return result.getJSONObject("value");}
                    else if(audit)result=core.call(base(task,graph,"graphTool").put("auditor",true).put("name",name).put("input",args));
                    else result=new JSONObject().put("ok",false).put("error","Only submit_findings is available to the extractor.");
                }catch(Exception error){result=new JSONObject().put("ok",false).put("error",error.getMessage()==null?"Invalid extraction arguments":error.getMessage());}
                history.put(ToolProtocol.output(call,result.optBoolean("ok")?result.get("value"):result));
            }
        }
        throw new IOException("The "+(audit?"auditor":"extractor")+" reached its original step limit without valid findings. Earlier completed batches remain saved.");
    }
    private static JSONObject checked(JSONObject value)throws IOException{if(!value.optBoolean("ok"))throw new IOException(value.optString("error","Original extraction core failed"));return value;}
}
