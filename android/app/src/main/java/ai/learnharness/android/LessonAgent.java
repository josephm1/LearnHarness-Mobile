package ai.learnharness.android;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Original question/plan contracts and prompts, with a native question pause boundary. */
public final class LessonAgent {
    public interface Model {ProviderClient.Reply complete(String prompt,JSONArray messages,JSONArray tools)throws Exception; default ProviderClient.Reply completeRole(String role,String prompt,JSONArray messages,JSONArray tools)throws Exception{return complete(prompt,messages,tools);}}
    public record Result(ProviderClient.Reply reply,JSONArray history,JSONArray transcript,JSONObject pending,JSONObject tree,JSONObject assessment,String phase){}
    private final Context context;
    public LessonAgent(Context context){this.context=context;}
    private String asset(String name)throws IOException{try(InputStream in=context.getAssets().open(name)){return new String(ProviderClient.readBounded(in,2_000_000),StandardCharsets.UTF_8);}}
    public Result run(JSONObject session,JSONArray input,boolean freeRecall,Model model)throws Exception{
        if(session.optString("kind").equals("practice"))return new PracticeAgent(context).run(session,input,model);
        JSONArray history=new JSONArray(input.toString()),transcript=new JSONArray(),all=new JSONArray(asset("core/tool-definitions.json"));
        JSONObject tree=session.optJSONObject("planTree"),assessment=session.optJSONObject("assessment"),pending=null;String phase=session.optString("phase","Explore");
        long in=0,out=0;boolean handoff=false;StringBuilder visible=new StringBuilder();
        try(OriginalCore core=new OriginalCore(context)){
            if(session.optBoolean("searchAvailable"))all.put(core.call(new JSONObject().put("operation","jobDefinition")).getJSONObject("value"));
            JSONArray graphDefinitions=session.optBoolean("graphToolsEnabled")?core.call(new JSONObject().put("operation","graphDefinitions")).getJSONArray("value"):new JSONArray();
            for(int turn=0;turn<12;turn++){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                boolean probing=phase.equals("Explore");Set<String> names=probing?new HashSet<>(Arrays.asList(freeRecall?"recall":"probe","ask_user_question","probe_done")):new HashSet<>(Arrays.asList(freeRecall?"explain":"quiz","ask_user_question","plan_init"));
                if(session.optBoolean("searchAvailable"))names.add("research");
                if(session.optJSONArray("sourceFiles")!=null && session.getJSONArray("sourceFiles").length()>0)names.addAll(SourceTools.NAMES);
                JSONArray tools=new JSONArray();for(int i=0;i<all.length();i++)if(names.contains(all.getJSONObject(i).getString("name")))tools.put(all.getJSONObject(i));
                if(probing)for(int i=0;i<graphDefinitions.length();i++){JSONObject definition=graphDefinitions.getJSONObject(i);tools.put(definition);names.add(definition.getString("name"));}
                ProviderClient.Reply reply=model.completeRole(probing?"prober":"teacher",asset("prompts/"+(probing?"prober":"teacher")+".txt"),history,tools);in+=reply.inputTokens();out+=reply.outputTokens();history.put(ToolProtocol.assistant(reply));
                if(!reply.text().trim().isEmpty()){transcript.put(new JSONObject().put("role","assistant").put("content",reply.text()));if(visible.length()>0)visible.append("\n\n");visible.append(reply.text());}
                if(reply.toolCalls().length()==0){
                    if(handoff){handoff=false;phase="Plan";history.put(new JSONObject().put("role","user").put("content",core.call(new JSONObject().put("operation","handoff")).getString("value")));continue;}
                    return new Result(new ProviderClient.Reply(visible.toString(),in,out),history,transcript,pending,tree,assessment,phase);
                }
                for(int i=0;i<reply.toolCalls().length();i++){
                    JSONObject call=reply.toolCalls().getJSONObject(i),function=call.getJSONObject("function");String name=function.getString("name");JSONObject result;
                    try{
                        if(!names.contains(name))throw new IOException("This tool is unavailable in the current phase.");
                        JSONObject args=new JSONObject(function.getString("arguments"));
                        if(name.equals("research")){JSONObject validated=core.call(new JSONObject().put("operation","validate").put("name",name).put("input",args));if(!validated.optBoolean("ok"))throw new IOException(validated.optString("error"));JSONObject route=NativeModels.forRole(context,"researcher"),job=new JobStore(context).create(session.getString("id"),call.getString("id"),"research",validated.getJSONObject("value"),route);history.put(ToolProtocol.output(call,core.call(new JSONObject().put("operation","jobAck").put("id",job.getString("id"))).getString("value")));continue;}
                        if(SourceTools.NAMES.contains(name)){
                            JSONObject validated=core.call(new JSONObject().put("operation","validate").put("name",name).put("input",args));if(!validated.optBoolean("ok"))throw new IOException(validated.optString("error"));args=validated.getJSONObject("value");
                            if(name.equals("ls") || name.equals("find")){history.put(ToolProtocol.output(call,SourceTools.executeMetadata(session,name,args)));continue;}
                            if(pending!=null)throw new IOException("A question or source request is already open. Wait for the learner.");pending=SourceTools.pending(session,call,args);continue;
                        }
                        if(name.equals("list_sessions") || name.equals("list_propositions")){result=core.call(new JSONObject().put("operation","graphTool").put("name",name).put("input",args).put("graph",session.optJSONObject("graphContext")).put("sessions",session.optJSONArray("graphSessions")));history.put(ToolProtocol.output(call,result.optBoolean("ok")?result.get("value"):result));continue;}
                        if(Arrays.asList("quiz","explain").contains(name)){
                            JSONObject validated=core.call(new JSONObject().put("operation","validate").put("name",name).put("input",args));if(!validated.optBoolean("ok"))throw new IOException(validated.optString("error"));args=validated.getJSONObject("value");
                            boolean valid=false;JSONArray nodes=tree==null?null:tree.optJSONArray("nodes");for(int j=0;nodes!=null && j<nodes.length();j++)if(nodes.getJSONObject(j).getString("id").equals(args.optString("nodeId")))valid=true;
                            if(!valid)throw new IOException("nodeId must match an exact id in this session's plan_init. Write the plan first if it is missing.");
                        }
                        if(Arrays.asList("probe","quiz","recall","explain","ask_user_question").contains(name)){
                            if(handoff)throw new IOException("The probe is concluded. Say your closing sentence and end the turn.");
                            if(pending!=null)throw new IOException("A question is already open. Wait for the learner's answer.");
                            result=core.call(new JSONObject().put("operation","question").put("name",name).put("input",args).put("id",call.getString("id")));
                            if(result.optBoolean("ok")){pending=result.getJSONObject("value");pending.put("call",call);if(name.equals("quiz") || name.equals("explain"))phase="Learn";continue;}
                        }else if(name.equals("plan_init")){
                            result=core.call(new JSONObject().put("operation","plan").put("input",args));
                            if(result.optBoolean("ok")){tree=result.getJSONObject("value");JSONArray ids=new JSONArray(),nodes=tree.getJSONArray("nodes");StringBuilder map=new StringBuilder("Learning plan\n\n");for(int j=0;j<nodes.length();j++){JSONObject node=nodes.getJSONObject(j);ids.put(node.getString("id"));map.append("• ").append(node.getString("title")).append('\n');}transcript.put(new JSONObject().put("role","assistant").put("content",map.toString()).put("origin","tool"));result=new JSONObject().put("ok",true).put("nodeIds",ids).put("reminder","These are the plan's node ids — the only valid nodeId values for every quiz and explain call from here on. Reference them exactly.");}
                        }else{
                            if(assessment!=null)throw new IOException("The probe is already concluded — probe_done fires exactly once.");
                            if(pending!=null)throw new IOException("A question is still open. Resolve it before concluding the probe.");
                            result=core.call(new JSONObject().put("operation","validate").put("name",name).put("input",args));
                            if(result.optBoolean("ok")){assessment=args;handoff=true;transcript.put(new JSONObject().put("role","assistant").put("content","Learner assessment\n\n"+assessmentText(args)).put("origin","tool"));result=new JSONObject().put("ok",true).put("text",core.call(new JSONObject().put("operation","handoff")).getString("ack"));}
                        }
                    }catch(Exception error){result=new JSONObject().put("ok",false).put("error",error.getMessage()==null?"Invalid tool arguments":error.getMessage());}
                    history.put(ToolProtocol.output(call,result));
                }
                if(pending!=null)return new Result(new ProviderClient.Reply(visible.toString(),in,out),history,transcript,pending,tree,assessment,phase);
            }
        }
        throw new IOException("The agent reached 12 tool steps without a question or final reply. Retry the lesson; no partial agent state was saved.");
    }
    private static String assessmentText(JSONObject args){JSONObject assessment=args.optJSONObject("assessment");if(assessment==null)assessment=args;StringBuilder text=new StringBuilder(assessment.optString("summary"));JSONArray strands=assessment.optJSONArray("strands");for(int i=0;strands!=null && i<strands.length();i++){JSONObject strand=strands.optJSONObject(i);if(strand!=null)text.append("\n\n").append(strand.optString("strand")).append(": ").append(strand.optString("assessment"));}return text.toString().trim();}
}
