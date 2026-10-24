package ai.learnharness.android;
import org.json.*;

/** Canonical local tool history with provider-native reasoning/signature replay. */
public final class ToolProtocol {
    private ToolProtocol(){}
    public static JSONObject call(String id,String name,String arguments)throws JSONException{return new JSONObject().put("id",id).put("type","function").put("function",new JSONObject().put("name",name).put("arguments",arguments));}
    public static JSONArray declarations(JSONArray definitions,String protocol)throws JSONException{
        JSONArray result=new JSONArray();for(int i=0;definitions!=null && i<definitions.length();i++){JSONObject d=definitions.getJSONObject(i),f=new JSONObject().put("name",d.getString("name")).put("description",d.getString("description"));
            f.put(protocol.equals("Anthropic")?"input_schema":protocol.equals("Gemini")?"parametersJsonSchema":"parameters",d.getJSONObject("parameters"));
            if(protocol.equals("Responses"))f.put("type","function").put("strict",false);
            result.put(protocol.equals("Chat")?new JSONObject().put("type","function").put("function",f):f);
        }return result;
    }
    public static JSONObject assistant(ProviderClient.Reply reply)throws JSONException{
        JSONObject m=new JSONObject().put("role","assistant").put("content",reply.text());if(reply.toolCalls().length()>0)m.put("tool_calls",reply.toolCalls());if(reply.nativeOutput()!=null)m.put("_native",reply.nativeOutput());return m;
    }
    public static JSONObject output(JSONObject call,Object value)throws JSONException{JSONObject output=new JSONObject().put("role","tool").put("tool_call_id",call.getString("id")).put("name",call.getJSONObject("function").getString("name")).put("content",String.valueOf(value));if(call.has("_geminiId"))output.put("_geminiId",call.getString("_geminiId"));return output;}
    public static JSONArray messages(JSONArray history,String protocol)throws JSONException{
        JSONArray result=new JSONArray();for(int i=0;i<history.length();i++){JSONObject m=history.getJSONObject(i);String role=m.getString("role"),text=m.optString("content");JSONArray calls=m.optJSONArray("tool_calls");JSONObject nativeValue=m.optJSONObject("_native");
            if(protocol.equals("Responses")){
                if(role.equals("tool")){result.put(new JSONObject().put("type","function_call_output").put("call_id",m.getString("tool_call_id")).put("output",text));continue;}
                if(nativeValue!=null && nativeValue.has("responses") && nativeValue.getJSONArray("responses").length()>0){JSONArray items=nativeValue.getJSONArray("responses");for(int j=0;j<items.length();j++)result.put(items.get(j));continue;}
                if(!text.isEmpty())result.put(new JSONObject().put("role",role).put("content",text));
                for(int j=0;calls!=null && j<calls.length();j++){JSONObject c=calls.getJSONObject(j),f=c.getJSONObject("function");result.put(new JSONObject().put("type","function_call").put("call_id",c.getString("id")).put("name",f.getString("name")).put("arguments",f.getString("arguments")));}
            }else if(protocol.equals("Anthropic")){
                JSONArray parts=new JSONArray();String actualRole=role.equals("tool")?"user":role;
                if(role.equals("tool"))parts.put(new JSONObject().put("type","tool_result").put("tool_use_id",m.getString("tool_call_id")).put("content",text));
                else if(nativeValue!=null && nativeValue.has("anthropic"))parts=nativeValue.getJSONArray("anthropic");
                else{if(!text.isEmpty())parts.put(new JSONObject().put("type","text").put("text",text));for(int j=0;calls!=null && j<calls.length();j++){JSONObject c=calls.getJSONObject(j),f=c.getJSONObject("function");parts.put(new JSONObject().put("type","tool_use").put("id",c.getString("id")).put("name",f.getString("name")).put("input",new JSONObject(f.getString("arguments"))));}}
                if(result.length()>0 && result.getJSONObject(result.length()-1).getString("role").equals(actualRole)){JSONArray previous=result.getJSONObject(result.length()-1).getJSONArray("content");for(int j=0;j<parts.length();j++)previous.put(parts.get(j));}else result.put(new JSONObject().put("role",actualRole).put("content",parts));
            }else if(protocol.equals("Gemini")){
                JSONArray parts=new JSONArray();String actualRole=role.equals("assistant")?"model":"user";
                if(role.equals("tool")){Object value;try{value=new JSONObject(text);}catch(JSONException e){value=new JSONObject().put("result",text);}JSONObject response=new JSONObject().put("name",m.getString("name")).put("response",value);if(m.has("_geminiId"))response.put("id",m.getString("_geminiId"));parts.put(new JSONObject().put("functionResponse",response));}
                else if(nativeValue!=null && nativeValue.has("gemini"))parts=nativeValue.getJSONArray("gemini");
                else{if(!text.isEmpty())parts.put(new JSONObject().put("text",text));for(int j=0;calls!=null && j<calls.length();j++){JSONObject f=calls.getJSONObject(j).getJSONObject("function");parts.put(new JSONObject().put("functionCall",new JSONObject().put("name",f.getString("name")).put("args",new JSONObject(f.getString("arguments")))));}}
                result.put(new JSONObject().put("role",actualRole).put("parts",parts));
            }else{JSONObject clean=new JSONObject().put("role",role).put("content",text);if(role.equals("tool"))clean.put("tool_call_id",m.getString("tool_call_id"));if(calls!=null){JSONArray cleanCalls=new JSONArray();for(int j=0;j<calls.length();j++){JSONObject call=new JSONObject(calls.getJSONObject(j).toString());call.remove("_geminiId");cleanCalls.put(call);}clean.put("tool_calls",cleanCalls);}result.put(clean);}
        }return result;
    }
}
