package ai.learnharness.android;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Stream preview text; only return a successful, completed response for persistence. */
public final class ResponseStream {
    private ResponseStream() {}
    public static ProviderClient.Reply read(InputStream input) throws Exception {
        return read(input,null);
    }
    public static ProviderClient.Reply read(InputStream input,java.util.function.Consumer<String> preview) throws Exception {
        // Bound total stream bytes, including oversized events, before decoding lines.
        InputStream bounded = new FilterInputStream(input) {
            int count;
            @Override public int read() throws IOException { int n=super.read(); if(n!=-1 && ++count>8_000_000) throw new IOException("ChatGPT response is too large."); return n; }
            @Override public int read(byte[] b,int o,int len) throws IOException { int n=in.read(b,o,len); if(n>0 && (count+=n)>8_000_000) throw new IOException("ChatGPT response is too large."); return n; }
        };
        BufferedReader reader = new BufferedReader(new InputStreamReader(bounded, StandardCharsets.UTF_8));
        StringBuilder data=new StringBuilder(), text=new StringBuilder(); String line;
        java.util.TreeMap<Integer,JSONObject> completedItems=new java.util.TreeMap<>();java.util.LinkedHashSet<String> eventTypes=new java.util.LinkedHashSet<>();
        while ((line=reader.readLine())!=null) {
            if (line.isEmpty()) {
                if (data.length()==0) continue;
                String payload=data.toString(); data.setLength(0);
                if (payload.equals("[DONE]")) continue;
                JSONObject event=new JSONObject(payload); String type=event.optString("type");if(eventTypes.size()<100)eventTypes.add(type);
                if(type.equals("response.output_item.done")){int index=event.optInt("output_index",completedItems.size());if(index<0 || index>10000)throw new IOException("Invalid response output index.");completedItems.put(index,event.getJSONObject("item"));}
                if (type.equals("response.output_text.delta")) { text.append(event.optString("delta"));if(preview!=null)preview.accept(text.toString()); }
                else if (type.equals("response.completed")) {
                    JSONObject response=event.getJSONObject("response");
                    if (!response.optString("status", "completed").equals("completed")) throw new IOException("ChatGPT did not complete the response. Try again.");
                    JSONArray items=response.optJSONArray("output");if(items==null || items.length()==0){items=new JSONArray();for(JSONObject item:completedItems.values())items.put(item);}
                    String finalText=outputText(items);
                    if (!finalText.isEmpty()) { text.setLength(0); text.append(finalText); }
                    JSONArray calls=new JSONArray();
                    for(int i=0;items!=null && i<items.length();i++){JSONObject item=items.getJSONObject(i);if(item.optString("type").equals("function_call"))calls.put(ToolProtocol.call(item.getString("call_id"),item.getString("name"),item.getString("arguments")));}
                    if (calls.length()==0 && text.toString().trim().isEmpty()) throw new IOException("ChatGPT completed without a text reply. Choose a text-capable model.");
                    JSONObject usage=response.optJSONObject("usage");
                    return new ProviderClient.Reply(text.toString(),usage==null?0:usage.optLong("input_tokens"),usage==null?0:usage.optLong("output_tokens"),calls,new JSONObject().put("responses",items).put("streamEventTypes",new JSONArray(eventTypes)));
                } else if (type.equals("response.failed") || type.equals("error")) {
                    JSONObject response=event.optJSONObject("response");
                    JSONObject error=response==null?event.optJSONObject("error"):response.optJSONObject("error");
                    if(error==null) error=event;
                    throw new OpenAiHttp.Failure(0,error.optString("code"),error.optString("param"),"","stream error");
                } else if (type.equals("response.incomplete")) throw new IOException("ChatGPT stopped before completing the reply. The partial response has not been saved; try again.");
            } else if (line.startsWith("data:")) { if(data.length()>0)data.append('\n'); String value=line.substring(5); data.append(value.startsWith(" ")?value.substring(1):value); }
        }
        throw new IOException("ChatGPT connection ended before the response completed. Your records are unchanged; try again.");
    }
    private static String outputText(JSONArray output) {
        StringBuilder result=new StringBuilder();
        for(int i=0;output!=null && i<output.length();i++) {
            JSONObject item=output.optJSONObject(i); if(item==null || !item.optString("type").equals("message"))continue;
            JSONArray parts=item.optJSONArray("content");
            for(int j=0;parts!=null && j<parts.length();j++) {
                JSONObject part=parts.optJSONObject(j);
                if(part!=null && part.optString("type").equals("output_text")) { if(result.length()>0)result.append('\n'); result.append(part.optString("text")); }
            }
        }
        return result.toString();
    }
}
