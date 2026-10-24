package ai.learnharness.android;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Isolated original researcher prompt/tool loop. Only its completed brief returns to the tutor. */
public final class ResearchAgent {
    public interface Progress {void update(String activity,long inputTokens,long outputTokens,int steps)throws Exception;}
    public interface Web {JSONArray search(String query,int count)throws Exception;ResearchWeb.Page fetch(String url)throws Exception;}
    private final Context context;
    public ResearchAgent(Context context){this.context=context;}
    public JSONObject run(JSONObject task,LessonAgent.Model model,Web web,Progress progress)throws Exception{
        try(OriginalCore core=new OriginalCore(context)){
            JSONObject setup=core.call(new JSONObject().put("operation","researchSetup"));if(!setup.optBoolean("ok"))throw new IOException(setup.optString("error"));String prompt="Research question: "+task.getString("question")+(task.optString("context").trim().isEmpty()?"":"\n\nContext (what this is needed for): "+task.getString("context"))+"\n\nProduce your final brief in the required format.";
            JSONArray history=new JSONArray().put(new JSONObject().put("role","user").put("content",prompt)),tools=setup.getJSONArray("tools");long in=0,out=0;ArrayList<String> fetched=new ArrayList<>();
            for(int step=0;step<12;step++){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();ProviderClient.Reply reply=model.completeRole("researcher",setup.getString("prompt"),history,tools);in+=reply.inputTokens();out+=reply.outputTokens();if(progress!=null)progress.update("Research step "+(step+1),in,out,step+1);history.put(ToolProtocol.assistant(reply));
                if(reply.toolCalls().length()==0){if(reply.text().trim().isEmpty())throw new IOException("The researcher produced no brief.");return new JSONObject().put("brief",reply.text().trim()).put("sources",sources(reply.text().trim(),fetched)).put("inputTokens",in).put("outputTokens",out).put("steps",step+1);}
                for(int i=0;i<reply.toolCalls().length();i++){
                    JSONObject call=reply.toolCalls().getJSONObject(i),function=call.getJSONObject("function");String name=function.getString("name");Object output;
                    try{
                        JSONObject args=new JSONObject(function.getString("arguments"));if(progress!=null)progress.update(name+": "+(name.equals("web_search")?args.optString("query"):args.optString("url")),in,out,step+1);
                        if(name.equals("web_search"))output=web.search(args.getString("query"),Math.max(1,Math.min(10,args.optInt("count",5))));
                        else if(name.equals("web_fetch")){
                            ResearchWeb.Page page=web.fetch(args.getString("url"));fetched.add(page.url());String source="Source: "+page.url()+" (HTTP "+page.status()+")";
                            if(page.mime().contains("application/pdf"))output="web_fetch: this URL is a PDF document. PDF research reading is not yet available in this Android researcher; report this gap or find an HTML source.";
                            else if(page.mime().contains("html")){JSONObject extracted=core.call(new JSONObject().put("operation","webExtract").put("html",page.text()));if(!extracted.optBoolean("ok")){ResearchWeb.Page reader=web.fetch("https://r.jina.ai/"+page.url());String body=reader.text();int marker=body.indexOf("Markdown Content:");if(marker<0)throw new IOException("Could not extract readable content.");output=truncate(body.substring(marker+"Markdown Content:".length()).trim());}else output=(extracted.optString("title").isEmpty()?"":"# "+extracted.getString("title")+"\n\n")+source+"\n\n---\n\n"+extracted.getString("value");}
                            else output=source+"\n\n"+truncate(page.text());
                        }else output="Unknown research tool: "+name;
                    }catch(Exception error){if(error instanceof InterruptedException)throw error;output=name+" failed: "+(error.getMessage()==null?"request failed":error.getMessage());}
                    history.put(ToolProtocol.output(call,output));
                }
            }
        }
        throw new IOException("The researcher reached its original 12-step limit without a final brief.");
    }
    private static String truncate(String value){return value.length()<=40000?value:value.substring(0,40000)+"\n\n[Truncated at 40000 characters]";}
    public static JSONArray sources(String brief,List<String> fetched)throws Exception{
        JSONArray sources=new JSONArray();Set<String> seen=new HashSet<>();Matcher links=Pattern.compile("\\[([^\\]]+)\\]\\((https?://[^)\\s]+)\\)").matcher(brief);while(links.find() && sources.length()<10){String title=links.group(1).trim(),url=links.group(2);if(!title.isEmpty() && seen.add(url))sources.put(new JSONObject().put("title",title).put("url",url));}if(sources.length()==0)for(String url:fetched){if(sources.length()==10)break;if(seen.add(url)){String title;try{title=new java.net.URI(url).getHost();}catch(Exception ignored){title=url.substring(0,Math.min(40,url.length()));}sources.put(new JSONObject().put("title",title).put("url",url));}}return sources;
    }
}
