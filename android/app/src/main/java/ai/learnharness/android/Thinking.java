package ai.learnharness.android;

import org.json.*;
import java.io.IOException;
import java.util.*;

/** Explicit wire settings. Default omits every override; never changes billing or model. */
public final class Thinking {
    private Thinking() {}
    public static String[] options(String provider, String model) {
        if(provider.equals("Anthropic")) return new String[]{"default","low","medium","high","xhigh","max","budget:1024","budget:4096","budget:8192"};
        if(provider.equals("Gemini")) {
            if(model.startsWith("gemini-2.5")) return model.contains("pro")
                ? new String[]{"default","budget:-1","budget:1024","budget:4096","budget:8192"}
                : new String[]{"default","budget:0","budget:-1","budget:1024","budget:4096","budget:8192"};
            return new String[]{"default","minimal","low","medium","high"};
        }
        if(provider.equals("DeepSeek"))return new String[]{"default","none","low","high","max"};
        if(model.startsWith("gpt-6.1") || model.startsWith("gpt-6-astra"))return new String[]{"default","low","medium","high","xhigh","max"};
        if(model.startsWith("gpt-6"))return new String[]{"default","none","low","medium","high","xhigh","max"};
        if(model.startsWith("o1") || model.startsWith("o3") || model.startsWith("o4"))return new String[]{"default","low","medium","high"};
        return new String[]{"default","none","minimal","low","medium","high","xhigh","max"};
    }
    public static String label(String value) {
        if(value.equals("default"))return "Model default";
        if(value.equals("none") || value.equals("budget:0"))return "Off";
        if(value.equals("budget:-1"))return "Dynamic budget";
        if(value.startsWith("budget:"))return "Thinking budget · "+value.substring(7)+" tokens";
        if(value.equals("xhigh"))return "Extra high";
        return Character.toUpperCase(value.charAt(0))+value.substring(1);
    }
    public static void apply(JSONObject body,String provider,String model,String value) throws Exception {
        if(value.equals("default"))return;
        if(!Arrays.asList(options(provider,model)).contains(value))throw new IOException("Choose a thinking setting for the selected model, or use Model default.");
        switch(provider) {
            case "ChatGPT": body.put("reasoning",new JSONObject().put("effort",value));break;
            case "Anthropic":
                body.put("max_tokens",16384);
                if(value.startsWith("budget:"))body.put("thinking",new JSONObject().put("type","enabled").put("budget_tokens",Integer.parseInt(value.substring(7))));
                else body.put("thinking",new JSONObject().put("type","adaptive")).put("output_config",new JSONObject().put("effort",value));
                break;
            case "Gemini":
                JSONObject thinking=new JSONObject();
                if(value.startsWith("budget:"))thinking.put("thinkingBudget",Integer.parseInt(value.substring(7)));
                else thinking.put("thinkingLevel",value);
                body.getJSONObject("generationConfig").put("thinkingConfig",thinking);break;
            case "OpenRouter":body.put("reasoning",new JSONObject().put("effort",value));break;
            case "DeepSeek":
                body.put("thinking",new JSONObject().put("type",value.equals("none")?"disabled":"enabled"));
                if(!value.equals("none"))body.put("reasoning_effort",value);break;
            default:body.put("reasoning_effort",value);
        }
    }
}
