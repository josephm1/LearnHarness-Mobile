package ai.learnharness.android;

import org.json.*;
import java.util.*;

/** Draft-only routing: sharing never creates model history or starts a request. */
final class ShareDestinations {
    static List<JSONObject> recent(JSONArray sessions,boolean practice,Map<String,Long> visits)throws JSONException{
        List<JSONObject> rows=new ArrayList<>();Map<String,Integer> order=new HashMap<>();
        for(int i=0;i<sessions.length();i++){
            JSONObject row=sessions.getJSONObject(i);order.put(row.getString("id"),i);
            if(row.optString("kind").equals("practice")==practice)rows.add(row);
        }
        rows.sort((a,b)->{long at=visits.getOrDefault(a.optString("id"),a.optLong("updated",a.optLong("created"))),bt=visits.getOrDefault(b.optString("id"),b.optLong("updated",b.optLong("created")));int byTime=Long.compare(bt,at);return byTime!=0?byTime:Integer.compare(order.get(b.optString("id")),order.get(a.optString("id")));});return rows;
    }
    static JSONObject attach(JSONObject root,JSONObject share,JSONArray documents,String destination,boolean practice,long now)throws JSONException{
        JSONArray handled=root.optJSONArray("receivedShares");if(handled==null)handled=new JSONArray();
        JSONArray sessions=root.getJSONArray("sessions");
        for(int i=0;i<handled.length();i++)if(share.getString("id").equals(handled.getJSONObject(i).getString("id")))return find(sessions,handled.getJSONObject(i).getString("session"));
        JSONObject target=destination.isEmpty()?null:find(sessions,destination);
        if(!destination.isEmpty() && target==null)throw new JSONException("The selected conversation no longer exists.");
        if(target!=null && target.optString("kind").equals("practice")!=practice)throw new JSONException("Choose a conversation in the selected tab.");
        if(target==null){
            String title=share.optString("title").trim();if(title.isEmpty())title=practice?"New practice":"New lesson";if(title.length()>80)title=title.substring(0,80);
            target=new JSONObject().put("id",UUID.randomUUID().toString()).put("title",title).put("created",now).put("phase",practice?"Practice":"Explore").put("messages",new JSONArray());
            if(practice)target.put("kind","practice").put("practiceNodes",new JSONArray());sessions.put(target);
        }
        String text=share.optString("text").trim(),draft=target.optString("draft");if(!text.isEmpty())target.put("draft",draft.isEmpty()?text:draft+"\n\n"+text);
        JSONArray library=root.getJSONArray("documents"),refs=target.optJSONArray("documents");if(refs==null)refs=new JSONArray();
        for(int i=0;i<documents.length();i++){
            JSONObject doc=documents.getJSONObject(i);String id=doc.getString("id");boolean exists=false;
            for(int j=0;j<library.length();j++)if(library.getJSONObject(j).getString("uri").equals(doc.getString("uri"))){id=library.getJSONObject(j).getString("id");exists=true;break;}
            if(!exists)library.put(doc);boolean linked=false;for(int j=0;j<refs.length();j++)if(id.equals(refs.getString(j)))linked=true;if(!linked)refs.put(id);
        }
        target.put("documents",refs).put("updated",now);handled.put(new JSONObject().put("id",share.getString("id")).put("session",target.getString("id")));while(handled.length()>200)handled.remove(0);root.put("receivedShares",handled);return target;
    }
    private static JSONObject find(JSONArray rows,String id){for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null && row.optString("id").equals(id))return row;}return null;}
}
