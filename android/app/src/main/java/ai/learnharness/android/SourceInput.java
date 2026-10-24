package ai.learnharness.android;
import org.json.*;
/** Approved source material is user input, never a system-prompt modification or implicit history. */
public final class SourceInput {
    private SourceInput(){}
    public static JSONArray forStorage(JSONArray history)throws Exception{
        JSONArray clean=new JSONArray(history.toString());for(int i=0;i<clean.length();i++){JSONObject message=clean.getJSONObject(i);if(message.has("_withoutApprovedSource")){message.put("content",message.getString("_withoutApprovedSource"));message.remove("_withoutApprovedSource");}}return clean;
    }
    public static JSONArray forTurn(JSONArray history,String approved)throws Exception{
        JSONArray input=new JSONArray(history.toString());
        if(!approved.isEmpty()){
            if(approved.length()>6000)throw new java.io.IOException("Approved excerpt exceeds 6,000 characters.");
            JSONObject last=input.getJSONObject(input.length()-1);if(!last.getString("role").equals("user") && !last.getString("role").equals("tool"))throw new java.io.IOException("A source excerpt needs a learner question.");
            last.put("_withoutApprovedSource",last.getString("content"));
            last.put("content",last.getString("content")+"\n\n<approved_source_excerpt>\n"+approved+"\n</approved_source_excerpt>");
        }
        return input;
    }
}
