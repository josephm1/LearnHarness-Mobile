package ai.learnharness.android;
import android.content.Context;
import org.json.*;
import java.io.IOException;

/** A background job's explicitly chosen billing route; never changes connection on an error. */
public final class NativeModels implements LessonAgent.Model,AutoCloseable {
    private final Context context;private final JSONObject route;private final boolean planSearch;private final ProviderClient provider=new ProviderClient();private final OpenAiHttp openai=new OpenAiHttp();
    public NativeModels(Context context,JSONObject route)throws Exception{this(context,route,false);}
    public NativeModels(Context context,JSONObject route,boolean planSearch)throws Exception{this.context=context.getApplicationContext();this.route=RoleConnections.validate(new JSONObject(route.toString()));this.planSearch=planSearch;if(planSearch && !this.route.getString("billing").equals("plan"))throw new IOException("ChatGPT search requires a ChatGPT plan connection for the researcher. Select it under Models by role. No API fallback was used.");}
    public static JSONObject active(Context context)throws Exception{
        var prefs=context.getSharedPreferences("settings",0);if(prefs.getBoolean("useChatGpt",false)){JSONObject account=ChatGptAccounts.get(context).active();if(account==null)throw new IOException("Connect a ChatGPT account first.");return new JSONObject().put("billing","plan").put("account",account.getString("client_id")).put("model",account.optString("model")).put("thinking",prefs.getString("thinking.ChatGPT|"+account.getString("client_id")+"|"+account.optString("model"),"default"));}
        String name=prefs.getString("provider","OpenAI compatible"),endpoint=prefs.getString("endpoint","https://api.openai.com/v1"),model=prefs.getString("model","");return new JSONObject().put("billing","api").put("provider",name).put("endpoint",endpoint).put("model",model).put("thinking",prefs.getString("thinking."+name+"|"+endpoint+"|"+model,"default"));
    }
    public static JSONObject forRole(Context context,String role)throws Exception{var prefs=context.getSharedPreferences("settings",0);JSONObject routes=new JSONObject(prefs.getString("roleConnections","{}"));JSONObject selected=routes.optJSONObject(role);if(selected==null)selected=routes.optJSONObject(RoleConnections.slot(role));return selected==null?RoleConnections.validate(active(context)):RoleConnections.validate(new JSONObject(selected.toString()));}
    @Override public ProviderClient.Reply complete(String prompt,JSONArray history,JSONArray tools)throws Exception{
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();if(route.getString("billing").equals("plan")){ChatGptAccounts accounts=ChatGptAccounts.get(context);if(planSearch)return WebCitations.readable(openai.webSearch(route.getString("model"),accounts.token(route.getString("account"),openai),prompt,history,route.optString("thinking","default"),tools,history.length()==1));return openai.responses(route.getString("model"),accounts.token(route.getString("account"),openai),prompt,history,route.optString("thinking","default"),null,tools);}
        String name=route.getString("provider");return provider.complete(new ProviderClient.Config(name,route.getString("endpoint"),route.getString("model"),new SecretStore(context).get(name),route.optString("thinking","default")),prompt,history,null,tools);
    }
    @Override public void close(){provider.cancel();openai.cancel();}
}
