package ai.learnharness.android;
import org.json.*;
import java.util.*;

/** Validate persisted ledgers on backup/import boundaries, including references in reasoning chains. */
public final class GraphContract {
    private GraphContract(){}
    public static void validate(JSONObject graph)throws JSONException{
        Set<String> nodes=new HashSet<>();JSONArray lines=array(graph,"nodes");for(int i=0;i<lines.length();i++){JSONObject node=lines.getJSONObject(i);nodes.add(nonempty(node,"id"));nonempty(node,"name");nonempty(node,"statement");node.getString("createdAt");strings(node.getJSONArray("aliases"));provenance(node.getJSONArray("provenance"));}
        Map<String,String> identities=new HashMap<>();JSONArray chains=array(graph,"chains");for(int i=0;i<chains.length();i++){JSONObject chain=chains.getJSONObject(i);String id=nonempty(chain,"id"),conclusion=nonempty(chain,"conclusion");JSONArray premises=chain.getJSONArray("premises");if(premises.length()==0 || !nodes.contains(conclusion))throw new JSONException("Invalid chain conclusion or empty premises.");Set<String> unique=new TreeSet<>();for(int j=0;j<premises.length();j++){String premise=premises.getString(j);if(!nodes.contains(premise) || premise.equals(conclusion) || !unique.add(premise))throw new JSONException("Invalid chain premise.");}String identity=unique.toString()+">"+conclusion;if(identities.containsKey(id) && !identities.get(id).equals(identity))throw new JSONException("A chain ID names different arguments.");identities.put(id,identity);chain.getString("createdAt");provenance(chain.getJSONArray("provenance"));}
        JSONArray coverage=array(graph,"coverage");for(int i=0;i<coverage.length();i++){JSONObject row=coverage.getJSONObject(i);nonempty(row,"chatId");if(!row.getString("fp").matches("[0-9a-f]{64}") || row.getInt("tokens")<0)throw new JSONException("Invalid extraction coverage.");row.getString("foldedAt");}
        JSONArray checkpoints=array(graph,"checkpoints");for(int i=0;i<checkpoints.length();i++){JSONObject row=checkpoints.getJSONObject(i);nonempty(row,"chatId");row.getString("foldedAt");}
        JSONArray sources=array(graph,"sources");for(int i=0;i<sources.length();i++){JSONObject row=sources.getJSONObject(i);nonempty(row,"id");row.getString("filePath");row.getString("importedAt");}
        if(graph.has("legacyIds"))strings(graph.getJSONArray("legacyIds"));if(graph.has("positions")){JSONObject positions=graph.getJSONObject("positions");Iterator<String> keys=positions.keys();while(keys.hasNext()){JSONObject p=positions.getJSONObject(keys.next());if(!Double.isFinite(p.getDouble("x")) || !Double.isFinite(p.getDouble("y")))throw new JSONException("Invalid graph position.");}}
    }
    private static JSONArray array(JSONObject graph,String key)throws JSONException{if(!graph.has(key))return new JSONArray();JSONArray value=graph.getJSONArray(key);if(value.length()>200000)throw new JSONException("Graph ledger exceeds supported size.");return value;}
    private static void provenance(JSONArray points)throws JSONException{for(int i=0;i<points.length();i++){JSONObject point=points.getJSONObject(i);nonempty(point,"chatId");point.getString("messageId");}}
    private static void strings(JSONArray values)throws JSONException{for(int i=0;i<values.length();i++)if(!(values.get(i) instanceof String))throw new JSONException("Expected text values.");}
    private static String nonempty(JSONObject value,String key)throws JSONException{String text=value.getString(key);if(text.trim().isEmpty())throw new JSONException("Missing graph "+key);return text;}
}
