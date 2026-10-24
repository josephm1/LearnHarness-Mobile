package ai.learnharness.android;
import org.json.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;

/** Non-secret, explicit role/slot routing. Credentials are resolved separately from encrypted storage. */
public final class RoleConnections {
    public static final String[] ROLES={"prober","teacher","researcher","demoBuilder","visualMaker","pdfReader","extractor","auditor"};
    private RoleConnections(){}
    public static String slot(String role){return role.equals("prober") || role.equals("teacher")?"strong":role.equals("visualMaker") || role.equals("pdfReader")?"vision":"cheap";}
    public static JSONObject resolve(JSONObject routes,String role,JSONObject active)throws Exception{
        JSONObject selected=routes.optJSONObject(role);if(selected==null)selected=routes.optJSONObject(slot(role));if(selected==null)selected=active;return validate(new JSONObject(selected.toString()));
    }
    public static JSONObject validate(JSONObject config)throws Exception{
        Set<String> fields=new HashSet<>(Arrays.asList("billing","provider","endpoint","account","model","thinking"));for(Iterator<String> keys=config.keys();keys.hasNext();)if(!fields.contains(keys.next()))throw new IOException("Role connection contains unsupported fields.");
        if(config.optString("model").trim().isEmpty())throw new IOException("Choose a model for this role or slot.");String billing=config.getString("billing"),provider;
        if(billing.equals("plan")){provider="ChatGPT";if(config.optString("account").isEmpty())throw new IOException("Choose a connected ChatGPT account.");}
        else if(billing.equals("api")){provider=config.getString("provider");URI endpoint=new URI(config.getString("endpoint"));if(!"https".equals(endpoint.getScheme()) || endpoint.getHost()==null || endpoint.getUserInfo()!=null || endpoint.getQuery()!=null || endpoint.getFragment()!=null)throw new IOException("Enter an HTTPS API base URL.");}
        else throw new IOException("Choose explicit ChatGPT plan or API billing.");
        String thinking=config.optString("thinking","default");if(!Arrays.asList(Thinking.options(provider,config.getString("model"))).contains(thinking))throw new IOException("Choose a valid thinking mode for this model.");config.put("thinking",thinking);return config;
    }
    public static String label(JSONObject config){return config==null?"Inherit connection":(config.optString("billing").equals("plan")?"ChatGPT plan":config.optString("provider")+" · API")+" · "+config.optString("model")+" · "+Thinking.label(config.optString("thinking","default"));}
}
