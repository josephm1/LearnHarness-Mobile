package ai.learnharness.android;
import org.json.*;
import java.time.Instant;
import java.util.*;

/** Adapts Android atomic records to the original append-only graph ledgers. */
public final class GraphRecords {
    private GraphRecords(){}
    public static JSONObject snapshot(Records records)throws Exception{
        JSONObject graph=records.root.optJSONObject("graph");graph=graph==null?new JSONObject():new JSONObject(graph.toString());for(String key:new String[]{"nodes","chains","coverage","checkpoints","sources","legacyIds"})if(!graph.has(key))graph.put(key,new JSONArray());if(!graph.has("positions"))graph.put("positions",new JSONObject());
        Set<String> imported=new HashSet<>();JSONArray ids=graph.getJSONArray("legacyIds");for(int i=0;i<ids.length();i++)imported.add(ids.getString(i));JSONArray knowledge=records.rows("knowledge");
        for(int i=0;i<knowledge.length();i++){JSONObject claim=knowledge.getJSONObject(i);String id=claim.getString("id");if(claim.optBoolean("graphNode") || imported.contains(id))continue;String source=claim.optString("source");if(source.isEmpty())source="src_android_notes";
            graph.getJSONArray("nodes").put(new JSONObject().put("id",id).put("name",claim.getString("title")).put("statement",claim.getString("statement")).put("aliases",new JSONArray()).put("createdAt",Instant.now().toString()).put("provenance",new JSONArray().put(new JSONObject().put("chatId",source).put("messageId",""))));ids.put(id);imported.add(id);
        }
        boolean hasSource=false;for(int i=0;i<graph.getJSONArray("sources").length();i++)if(graph.getJSONArray("sources").getJSONObject(i).optString("id").equals("src_android_notes"))hasSource=true;
        if(!hasSource)graph.getJSONArray("sources").put(new JSONObject().put("id","src_android_notes").put("filePath","Android notes").put("importedAt",Instant.now().toString()));return graph;
    }
    public static void edit(Records records,JSONObject claim,String title,String statement,JSONArray premises)throws Exception{
        JSONObject graph=records.root.optJSONObject("graph");
        if(graph!=null && claim.optBoolean("graphNode")){
            JSONArray lines=graph.getJSONArray("nodes");for(int i=0;i<lines.length();i++){JSONObject line=lines.getJSONObject(i);if(line.optString("id").equals(claim.getString("id"))){line.put("name",title).put("statement",statement);break;}}
            // Keep exact joint-premise chains unless the learner explicitly edits the premise list.
            if(!premises.toString().equals(claim.getJSONArray("premises").toString())){
                JSONArray chains=graph.getJSONArray("chains");for(int i=chains.length()-1;i>=0;i--)if(chains.getJSONObject(i).getString("conclusion").equals(claim.getString("id")))chains.remove(i);
                JSONArray ids=new JSONArray();for(int i=0;i<premises.length();i++){String text=premises.getString(i),id=null;JSONArray knowledge=records.rows("knowledge");for(int j=0;j<knowledge.length();j++){JSONObject candidate=knowledge.getJSONObject(j);if(candidate.optString("id").equals(claim.getString("id")))continue;if(candidate.optString("statement").equalsIgnoreCase(text) || candidate.optString("title").equalsIgnoreCase(text)){id=candidate.getString("id");break;}}
                    if(id==null){id="n_"+UUID.randomUUID().toString().replace("-","").substring(0,12);lines.put(new JSONObject().put("id",id).put("name",text).put("statement",text).put("aliases",new JSONArray()).put("createdAt",Instant.now().toString()).put("provenance",new JSONArray().put(new JSONObject().put("chatId","src_android_notes").put("messageId",""))));}boolean exists=false;for(int j=0;j<ids.length();j++)if(ids.getString(j).equals(id))exists=true;if(!exists)ids.put(id);
                }
                if(ids.length()>0)chains.put(new JSONObject().put("id","arg_"+UUID.randomUUID().toString().replace("-","").substring(0,12)).put("premises",ids).put("conclusion",claim.getString("id")).put("createdAt",Instant.now().toString()).put("provenance",new JSONArray().put(new JSONObject().put("chatId","src_android_notes").put("messageId",""))));
            }
            records.root.remove("graphSnapshot");records.root.remove("graphView");GraphContract.validate(graph);
        }
        claim.put("title",title).put("statement",statement).put("premises",premises);
    }
    public static void apply(Records records,JSONObject graph,JSONObject view)throws Exception{
        records.root.put("graph",new JSONObject(graph.toString())).put("graphView",new JSONObject(view.toString()));JSONArray nodes=view.getJSONArray("nodes"),chains=view.getJSONArray("chains");Map<String,JSONObject> byId=new HashMap<>();for(int i=0;i<nodes.length();i++)byId.put(nodes.getJSONObject(i).getString("id"),nodes.getJSONObject(i));
        JSONArray knowledge=records.rows("knowledge");for(int i=knowledge.length()-1;i>=0;i--){JSONObject claim=knowledge.getJSONObject(i);if(claim.optBoolean("graphNode") && !byId.containsKey(claim.getString("id")))knowledge.remove(i);}
        for(int i=0;i<nodes.length();i++){
            JSONObject node=nodes.getJSONObject(i);String id=node.getString("id");JSONObject claim=records.find("knowledge",id);if(claim==null){claim=new JSONObject().put("id",id).put("status","Unreviewed");records.rows("knowledge").put(claim);}
            JSONArray premises=new JSONArray();Set<String> seen=new HashSet<>();for(int j=0;j<chains.length();j++){JSONObject chain=chains.getJSONObject(j);if(!chain.getString("conclusion").equals(id))continue;JSONArray ids=chain.getJSONArray("premises");for(int k=0;k<ids.length();k++){JSONObject premise=byId.get(ids.getString(k));if(premise!=null && seen.add(premise.getString("id")))premises.put(premise.getString("statement"));}}
            JSONArray provenance=node.optJSONArray("provenance");claim.put("title",node.getString("name")).put("statement",node.getString("statement")).put("premises",premises).put("graphNode",true).put("source",provenance==null || provenance.length()==0?"":provenance.getJSONObject(0).optString("chatId"));
        }
    }
}
