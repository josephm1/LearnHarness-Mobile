package ai.learnharness.android;
import android.content.Context;import android.content.SharedPreferences;
/** Explicit search selection. A plan search error never selects or charges another provider. */
public final class SearchSettings {
 private SearchSettings(){}
 public static String engine(SharedPreferences prefs){return prefs.getString("searchEngine",prefs.getBoolean("useChatGpt",false)?"chatgpt":"");}
 public static String label(String engine){return engine.equals("chatgpt")?"ChatGPT plan":engine.equals("tavily")?"Tavily":engine.equals("brave")?"Brave":"Disabled";}
 public static boolean available(Context context)throws Exception{var prefs=context.getSharedPreferences("settings",0);String engine=engine(prefs);if(engine.equals("chatgpt")){try{return NativeModels.forRole(context,"researcher").getString("billing").equals("plan");}catch(Exception unavailable){return false;}}return !engine.isEmpty() && !new SecretStore(context).get("search."+engine).isEmpty();}
}
