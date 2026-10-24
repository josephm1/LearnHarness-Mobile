package ai.learnharness.android;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Provider SSE text only; thinking/tool payloads never enter the visible answer. */
public final class ProviderStream {
    public static ProviderClient.Reply read(InputStream input,String provider,Consumer<String> preview) throws Exception {
        BufferedReader reader=new BufferedReader(new InputStreamReader(new FilterInputStream(input){
            int count;
            @Override public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);if(n>0 && (count+=n)>8_000_000)throw new IOException("Response is too large.");return n;}
        },StandardCharsets.UTF_8));
        JSONArray calls=new JSONArray(),blocks=new JSONArray(),geminiParts=new JSONArray();java.util.TreeMap<Integer,JSONObject> chatCalls=new java.util.TreeMap<>();java.util.Map<Integer,StringBuilder> arguments=new java.util.HashMap<>();
        JSONArray reasoningDetails=new JSONArray();StringBuilder reasoning=new StringBuilder();
        StringBuilder event=new StringBuilder(),text=new StringBuilder();String line;long in=0,out=0;boolean finished=false;
        while((line=reader.readLine())!=null){
            if(!line.isEmpty()){if(line.startsWith("data:")){if(event.length()>0)event.append('\n');event.append(line.substring(5).trim());}continue;}
            if(event.length()==0)continue;
            String payload=event.toString();event.setLength(0);
            if(payload.equals("[DONE]")){if(!finished)throw new IOException("The provider did not finish the answer. Try again.");break;}
            JSONObject data=new JSONObject(payload);
            if(data.has("error") || data.optString("type").equals("error"))throw new IOException("The provider reported a streaming error. The partial answer has not been saved.");
            if(provider.equals("Anthropic")){
                String type=data.optString("type");JSONObject delta=data.optJSONObject("delta");
                if(type.equals("message_start")){JSONObject usage=data.getJSONObject("message").optJSONObject("usage");if(usage!=null)in=usage.optLong("input_tokens");}
                int index=data.optInt("index");
                if(type.equals("content_block_start")){JSONObject block=data.getJSONObject("content_block");blocks.put(index,block);if(block.optString("type").equals("tool_use"))arguments.put(index,new StringBuilder());}
                if(type.equals("content_block_delta") && delta!=null){
                    JSONObject block=blocks.optJSONObject(index);String kind=delta.optString("type");
                    if(kind.equals("text_delta")){text.append(delta.optString("text"));if(block!=null)block.put("text",block.optString("text")+delta.optString("text"));}
                    if(kind.equals("input_json_delta") && arguments.containsKey(index))arguments.get(index).append(delta.optString("partial_json"));
                    if(kind.equals("thinking_delta") && block!=null)block.put("thinking",block.optString("thinking")+delta.optString("thinking"));
                    if(kind.equals("signature_delta") && block!=null)block.put("signature",block.optString("signature")+delta.optString("signature"));
                }
                if(type.equals("content_block_stop") && arguments.containsKey(index)){JSONObject block=blocks.getJSONObject(index);String value=arguments.get(index).toString();block.put("input",new JSONObject(value.isEmpty()?"{}":value));calls.put(ToolProtocol.call(block.getString("id"),block.getString("name"),block.getJSONObject("input").toString()));}
                if(type.equals("message_delta")){
                    String stop=delta==null?"":delta.optString("stop_reason");
                    if(!stop.isEmpty() && !stop.equals("end_turn") && !stop.equals("stop_sequence") && !stop.equals("tool_use"))throw new IOException("The provider stopped before finishing the answer. Try a lower thinking setting.");
                    JSONObject usage=data.optJSONObject("usage");if(usage!=null)out=usage.optLong("output_tokens");
                }
                if(type.equals("message_stop")){finished=true;break;}
            }else if(provider.equals("Gemini")){
                JSONArray candidates=data.optJSONArray("candidates");
                if(candidates!=null && candidates.length()>0){JSONObject candidate=candidates.getJSONObject(0),content=candidate.optJSONObject("content");JSONArray parts=content==null?null:content.optJSONArray("parts");
                    for(int i=0;parts!=null && i<parts.length();i++){JSONObject part=parts.getJSONObject(i);geminiParts.put(part);if(!part.optBoolean("thought"))text.append(part.optString("text"));if(part.has("functionCall")){JSONObject call=part.getJSONObject("functionCall");JSONObject canonical=ToolProtocol.call(call.optString("id","gemini_"+java.util.UUID.randomUUID()),call.getString("name"),call.optJSONObject("args")==null?"{}":call.getJSONObject("args").toString());if(call.has("id"))canonical.put("_geminiId",call.getString("id"));calls.put(canonical);}}
                    String stop=candidate.optString("finishReason");if(!stop.isEmpty()){if(!stop.equals("STOP"))throw new IOException("Gemini did not complete the answer. The partial answer has not been saved.");finished=true;}
                }
                JSONObject usage=data.optJSONObject("usageMetadata");if(usage!=null){in=usage.optLong("promptTokenCount");out=usage.optLong("candidatesTokenCount");}
            }else{
                JSONArray choices=data.optJSONArray("choices");if(choices!=null && choices.length()>0){JSONObject choice=choices.getJSONObject(0),delta=choice.optJSONObject("delta");if(delta!=null){text.append(delta.optString("content",""));reasoning.append(delta.optString("reasoning_content",""));JSONArray detail=delta.optJSONArray("reasoning_details");for(int r=0;detail!=null && r<detail.length();r++){
                        JSONObject fragment=detail.getJSONObject(r),previous=reasoningDetails.optJSONObject(reasoningDetails.length()-1);String type=fragment.optString("type"),field=type.equals("reasoning.text")?"text":type.equals("reasoning.summary")?"summary":"";
                        boolean same=previous!=null && !field.isEmpty() && type.equals(previous.optString("type")) && (fragment.optString("id").isEmpty() || previous.optString("id").isEmpty() || fragment.optString("id").equals(previous.optString("id")));
                        if(same){previous.put(field,previous.optString(field)+fragment.optString(field));if(fragment.has("signature"))previous.put("signature",previous.optString("signature")+fragment.optString("signature"));if(fragment.has("id"))previous.put("id",fragment.get("id"));if(fragment.has("format"))previous.put("format",fragment.get("format"));}else reasoningDetails.put(fragment);
                    }JSONArray fragments=delta.optJSONArray("tool_calls");for(int i=0;fragments!=null && i<fragments.length();i++){JSONObject fragment=fragments.getJSONObject(i);int index=fragment.getInt("index");JSONObject call=chatCalls.get(index);if(call==null){call=ToolProtocol.call("","","");chatCalls.put(index,call);}if(fragment.has("id"))call.put("id",fragment.getString("id"));JSONObject f=fragment.optJSONObject("function"),target=call.getJSONObject("function");if(f!=null){target.put("name",target.optString("name")+f.optString("name"));target.put("arguments",target.optString("arguments")+f.optString("arguments"));}}}
                    String stop=choice.optString("finish_reason","");if(!stop.isEmpty() && !stop.equals("null")){if(!stop.equals("stop") && !stop.equals("tool_calls"))throw new IOException("The model stopped before finishing the answer. The partial answer has not been saved.");finished=true;}
                }
                JSONObject usage=data.optJSONObject("usage");if(usage!=null){in=usage.optLong("prompt_tokens");out=usage.optLong("completion_tokens");}
            }
            if(preview!=null && text.length()>0)preview.accept(text.toString());
        }
        for(JSONObject call:chatCalls.values())calls.put(call);
        if(!finished || calls.length()==0 && text.toString().trim().isEmpty())throw new IOException("The connection ended before a complete text answer arrived. Try again.");
        JSONObject nativeValue=new JSONObject().put("provider",provider);if(reasoning.length()>0)nativeValue.put("reasoning_content",reasoning.toString());if(reasoningDetails.length()>0)nativeValue.put("reasoning_details",reasoningDetails);if(provider.equals("Anthropic"))nativeValue.put("anthropic",blocks);else if(provider.equals("Gemini"))nativeValue.put("gemini",geminiParts);
        return new ProviderClient.Reply(text.toString(),in,out,calls,nativeValue);
    }
}
