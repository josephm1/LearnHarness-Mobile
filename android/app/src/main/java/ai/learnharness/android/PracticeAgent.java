package ai.learnharness.android;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Upstream practice doctrine and problem/conclude contracts; never invents a JSON-question prompt. */
public final class PracticeAgent {
    private final Context context;
    public PracticeAgent(Context context){this.context=context;}
    private String asset(String path)throws IOException{try(InputStream in=context.getAssets().open(path)){return new String(ProviderClient.readBounded(in,2_000_000),StandardCharsets.UTF_8);}}
    public LessonAgent.Result run(JSONObject session,JSONArray input,LessonAgent.Model model)throws Exception{
        JSONArray history=new JSONArray(input.toString()),transcript=new JSONArray(),definitions=new JSONArray(asset("core/tool-definitions.json")),tools=new JSONArray();
        for(int i=0;i<definitions.length();i++){JSONObject d=definitions.getJSONObject(i);if(d.getString("name").equals("problem") || d.getString("name").equals("conclude") || session.optJSONArray("sourceFiles")!=null && session.getJSONArray("sourceFiles").length()>0 && SourceTools.NAMES.contains(d.getString("name")))tools.put(d);}
        JSONObject active=session.optJSONObject("pendingQuestion");boolean concluded=false;long in=0,out=0;StringBuilder visible=new StringBuilder();
        try(OriginalCore core=new OriginalCore(context)){
            if(session.optBoolean("searchAvailable"))tools.put(core.call(new JSONObject().put("operation","jobDefinition")).getJSONObject("value"));
            if(session.optBoolean("graphToolsEnabled")){JSONArray readers=core.call(new JSONObject().put("operation","graphDefinitions")).getJSONArray("value");for(int i=0;i<readers.length();i++)tools.put(readers.get(i));}
            JSONObject markers=core.call(new JSONObject().put("operation","practiceMarkers"));
            for(int step=0;step<12;step++){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                ProviderClient.Reply reply=model.complete(asset("prompts/practice.txt"),history,concluded?new JSONArray():tools);in+=reply.inputTokens();out+=reply.outputTokens();history.put(ToolProtocol.assistant(reply));
                if(!reply.text().trim().isEmpty()){transcript.put(new JSONObject().put("role","assistant").put("content",reply.text()));if(visible.length()>0)visible.append("\n\n");visible.append(reply.text());}
                if(reply.toolCalls().length()==0)return result(visible,in,out,history,transcript,active);
                boolean posed=false;JSONObject sourcePending=null;
                for(int i=0;i<reply.toolCalls().length();i++){
                    JSONObject call=reply.toolCalls().getJSONObject(i),f=call.getJSONObject("function");String name=f.getString("name");Object output;
                    try{
                        if(posed || concluded || sourcePending!=null)throw new IOException("The practice turn has ended. Wait for the learner's next input.");
                        if(name.equals("research") && session.optBoolean("searchAvailable")){JSONObject valid=core.call(new JSONObject().put("operation","validate").put("name",name).put("input",new JSONObject(f.getString("arguments"))));if(!valid.optBoolean("ok"))throw new IOException(valid.optString("error"));JSONObject job=new JobStore(context).create(session.getString("id"),call.getString("id"),"research",valid.getJSONObject("value"),NativeModels.forRole(context,"researcher"));history.put(ToolProtocol.output(call,core.call(new JSONObject().put("operation","jobAck").put("id",job.getString("id"))).getString("value")));continue;}
                        if(SourceTools.NAMES.contains(name) && session.optJSONArray("sourceFiles")!=null && session.getJSONArray("sourceFiles").length()>0){JSONObject checked=core.call(new JSONObject().put("operation","validate").put("name",name).put("input",new JSONObject(f.getString("arguments"))));if(!checked.optBoolean("ok"))throw new IOException(checked.optString("error"));JSONObject args=checked.getJSONObject("value");if(name.equals("ls") || name.equals("find")){history.put(ToolProtocol.output(call,SourceTools.executeMetadata(session,name,args)));continue;}if(sourcePending!=null)throw new IOException("A source request is already open. Wait for approval.");sourcePending=SourceTools.pending(session,call,args);if(active!=null)sourcePending.put("retainedProblem",active);continue;}
                        if(session.optBoolean("graphToolsEnabled") && (name.equals("list_sessions") || name.equals("list_propositions"))){JSONObject result=core.call(new JSONObject().put("operation","graphTool").put("name",name).put("input",new JSONObject(f.getString("arguments"))).put("graph",session.optJSONObject("graphContext")).put("sessions",session.optJSONArray("graphSessions")));history.put(ToolProtocol.output(call,result.optBoolean("ok")?result.get("value"):result));continue;}
                        if(!name.equals("problem") && !name.equals("conclude"))throw new IOException("Unknown practice tool.");
                        if(name.equals("problem") && active!=null)throw new IOException("A problem is already active — call conclude on it before posing the next one.");
                        if(name.equals("conclude") && active==null)throw new IOException("No active problem to conclude — pose one first.");
                        JSONObject validated=core.call(new JSONObject().put("operation","validate").put("name",name).put("input",new JSONObject(f.getString("arguments"))).put("nodes",session.optJSONArray("practiceNodes")==null?new JSONArray():session.getJSONArray("practiceNodes")));
                        if(!validated.optBoolean("ok"))throw new IOException(validated.optString("error"));JSONObject args=validated.getJSONObject("value");
                        if(name.equals("problem")){
                            active=new JSONObject(args.toString()).put("kind","problem").put("id",call.getString("id")).put("call",call);posed=true;output=markers.getString("posed");
                            transcript.put(new JSONObject().put("role","assistant").put("content",args.getString("statement")).put("practiceProblem",call.getString("id")));
                        }else{
                            transcript.put(new JSONObject().put("role","assistant").put("content",args.getString("verdict")+"\n\nWorked solution\n\n"+active.getString("modelSolution")).put("practiceConclusion",new JSONObject().put("problem",active).put("result",args)));
                            active=null;concluded=true;output=markers.getString("conclude");
                        }
                    }catch(Exception error){output=new JSONObject().put("ok",false).put("error",error.getMessage()==null?"Invalid practice arguments":error.getMessage());}
                    history.put(ToolProtocol.output(call,output));
                }
                if(sourcePending!=null)return result(visible,in,out,history,transcript,sourcePending);
                if(posed)return result(visible,in,out,history,transcript,active);
            }
        }
        throw new IOException("Practice reached 12 tool steps without a complete turn. No partial practice state was saved.");
    }
    private static LessonAgent.Result result(StringBuilder visible,long in,long out,JSONArray history,JSONArray transcript,JSONObject active){return new LessonAgent.Result(new ProviderClient.Reply(visible.toString(),in,out),history,transcript,active,null,null,"Practice");}
}
