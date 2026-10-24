package ai.learnharness.android;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** One native HTTPS transport; no computer, proxy, Node process, or hosted app required. */
public final class ProviderClient {
    public record Config(String provider, String endpoint, String model, String key, String thinking) {
        public Config(String provider,String endpoint,String model,String key){this(provider,endpoint,model,key,"default");}
    }
    public record Reply(String text,long inputTokens,long outputTokens,JSONArray toolCalls,JSONObject nativeOutput) {
        public Reply(String text,long inputTokens,long outputTokens){this(text,inputTokens,outputTokens,new JSONArray(),null);}
    }
    private volatile HttpURLConnection active;
    public void cancel() { HttpURLConnection c = active; if (c != null) c.disconnect(); }
    /** Read-only catalog. Manual IDs remain available for services without a catalog. */
    public JSONArray models(Config config) throws Exception {
        URI uri=new URI(config.endpoint().replaceAll("/+$", ""));
        if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)throw new IOException("Enter an HTTPS base URL without credentials or query parameters.");
        if(config.key().trim().isEmpty())throw new IOException("Enter an API key to load this provider's models.");
        JSONArray shown=new JSONArray();String cursor="";
        for(int page=0;page<20;page++) {
            String query=cursor.isEmpty()?"":config.provider().equals("Gemini")?"?pageToken="+URLEncoder.encode(cursor,"UTF-8"):"?after_id="+URLEncoder.encode(cursor,"UTF-8");
            HttpURLConnection c=(HttpURLConnection)new URL(uri+"/models"+query).openConnection();active=c;
            try {
                c.setInstanceFollowRedirects(false);c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setRequestMethod("GET");
                if(config.provider().equals("Anthropic")){c.setRequestProperty("x-api-key",config.key());c.setRequestProperty("anthropic-version","2023-06-01");}
                else if(config.provider().equals("Gemini"))c.setRequestProperty("x-goog-api-key",config.key());
                else c.setRequestProperty("Authorization","Bearer "+config.key());
                if(c.getResponseCode()!=200)throw new IOException("Model catalog returned HTTP "+c.getResponseCode()+". Check the key and endpoint, or enter a model ID manually.");
                JSONObject response;try(InputStream in=c.getInputStream()){response=new JSONObject(new String(readBounded(in,4_000_000),StandardCharsets.UTF_8));}
                JSONArray rows=response.getJSONArray(config.provider().equals("Gemini")?"models":"data");
                for(int i=0;i<rows.length();i++) {
                    JSONObject row=rows.getJSONObject(i);String id=row.optString(config.provider().equals("Gemini")?"name":"id").replaceFirst("^models/","");
                    if(config.provider().equals("Gemini") && (row.optJSONArray("supportedGenerationMethods")==null || !row.optJSONArray("supportedGenerationMethods").toString().contains("generateContent")))continue;
                    if(!id.isEmpty())shown.put(new JSONObject().put("id",id).put("name",row.optString("display_name",row.optString("displayName",row.optString("name",id)))));
                }
                String next=config.provider().equals("Gemini")?response.optString("nextPageToken"):config.provider().equals("Anthropic") && response.optBoolean("has_more")?response.optString("last_id"):"";
                if(next.isEmpty() || next.equals(cursor))break;cursor=next;
            }finally{c.disconnect();if(active==c)active=null;}
        }
        if(shown.length()==0)throw new IOException("No models were returned. You can still enter a model ID manually.");
        return shown;
    }
    public Reply complete(Config config, String system, JSONArray messages) throws Exception {
        return complete(config,system,messages,null);
    }
    public Reply complete(Config config,String system,JSONArray messages,java.util.function.Consumer<String> preview) throws Exception {
        return complete(config,system,messages,preview,null);
    }
    public Reply complete(Config config,String system,JSONArray messages,java.util.function.Consumer<String> preview,JSONArray tools) throws Exception {
        if (config.key().trim().isEmpty()) throw new IOException("Add your provider API key in Settings first.");
        if (config.model().trim().isEmpty()) throw new IOException("Enter a model ID in Settings first.");
        String base = config.endpoint().replaceAll("/+$", "");
        URI uri = new URI(base);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IOException("The provider endpoint must be an HTTPS base URL without credentials or query parameters.");
        JSONObject body = new JSONObject();
        String path;
        switch (config.provider()) {
            case "Anthropic":
                path = "/messages";
                body.put("model", config.model()).put("max_tokens", 4096).put("system", system).put("messages", ToolProtocol.messages(messages,"Anthropic"));
                break;
            case "Gemini":
                if (!config.model().matches("[A-Za-z0-9._-]+")) throw new IOException("Enter the Gemini model ID without a models/ prefix.");
                path = "/models/" + config.model() + ":generateContent";
                JSONArray contents=ToolProtocol.messages(messages,"Gemini");
                body.put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", system))))
                    .put("contents", contents).put("generationConfig", new JSONObject().put("maxOutputTokens", 8192));
                break;
            default:
                path = "/chat/completions";
                JSONArray all = new JSONArray().put(new JSONObject().put("role", "system").put("content", system));
                JSONArray chatMessages=ToolProtocol.messages(messages,"Chat");
                for(int i=0;i<chatMessages.length();i++){JSONObject message=chatMessages.getJSONObject(i),nativeValue=messages.getJSONObject(i).optJSONObject("_native");if(nativeValue!=null && config.provider().equals(nativeValue.optString("provider"))){if(nativeValue.has("reasoning_content"))message.put("reasoning_content",nativeValue.get("reasoning_content"));if(nativeValue.has("reasoning_details"))message.put("reasoning_details",nativeValue.get("reasoning_details"));}all.put(message);}
                body.put("model", config.model()).put("messages", all).put("stream", false);
        }
        if(tools!=null && tools.length()>0){
            if(config.provider().equals("Gemini"))body.put("tools",new JSONArray().put(new JSONObject().put("functionDeclarations",ToolProtocol.declarations(tools,"Gemini"))));
            else body.put("tools",ToolProtocol.declarations(tools,config.provider().equals("Anthropic")?"Anthropic":"Chat"));
        }
        if(preview!=null || tools!=null){
            if(config.provider().equals("Gemini"))path=path.replace(":generateContent",":streamGenerateContent")+"?alt=sse";
            else body.put("stream",true);
            if(!config.provider().equals("Gemini") && !config.provider().equals("Anthropic"))body.put("stream_options",new JSONObject().put("include_usage",true));
        }
        Thinking.apply(body,config.provider(),config.model(),config.thinking());
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        active = c;
        try {
            c.setInstanceFollowRedirects(false); c.setRequestMethod("POST"); c.setConnectTimeout(20000); c.setReadTimeout(180000);
            c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json");
            if (config.provider().equals("Anthropic")) { c.setRequestProperty("x-api-key", config.key()); c.setRequestProperty("anthropic-version", "2023-06-01"); }
            else if (config.provider().equals("Gemini")) c.setRequestProperty("x-goog-api-key", config.key());
            else c.setRequestProperty("Authorization", "Bearer " + config.key());
            try (OutputStream out = c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            int status = c.getResponseCode();
            if (status < 200 || status >= 300) {
                // Provider error bodies may echo sensitive request content. Keep errors actionable and local.
                String reason = status == 401 || status == 403 ? "Check your API key and model access." : status == 429 ? "Rate or credit limit reached. Try again later." : "Check the endpoint and model ID. If you changed thinking, try Model default; support varies by model.";
                throw new IOException("Provider returned HTTP " + status + ". " + reason);
            }
            if(preview!=null || tools!=null){try(InputStream in=c.getInputStream()){return ProviderStream.read(in,config.provider(),preview);}}
            JSONObject response;
            try (InputStream in = c.getInputStream()) { response = new JSONObject(new String(readBounded(in, 4_000_000), StandardCharsets.UTF_8)); }
            StringBuilder text = new StringBuilder(); long input = 0, output = 0;
            if (config.provider().equals("Anthropic")) {
                JSONArray parts = response.getJSONArray("content");
                for (int i = 0; i < parts.length(); i++) if (parts.getJSONObject(i).optString("type").equals("text")) text.append(parts.getJSONObject(i).optString("text"));
                JSONObject usage = response.optJSONObject("usage");
                if (usage != null) { input = usage.optLong("input_tokens"); output = usage.optLong("output_tokens"); }
            } else if (config.provider().equals("Gemini")) {
                JSONArray parts = response.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts");
                for (int i = 0; i < parts.length(); i++) if (!parts.getJSONObject(i).optBoolean("thought")) text.append(parts.getJSONObject(i).optString("text"));
                JSONObject usage = response.optJSONObject("usageMetadata");
                if (usage != null) { input = usage.optLong("promptTokenCount"); output = usage.optLong("candidatesTokenCount"); }
            } else {
                text.append(response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"));
                JSONObject usage = response.optJSONObject("usage");
                if (usage != null) { input = usage.optLong("prompt_tokens"); output = usage.optLong("completion_tokens"); }
            }
            if (text.toString().trim().isEmpty()) throw new IOException("The model returned no text. Try a text-capable model.");
            return new Reply(text.toString(), input, output);
        } finally { c.disconnect(); if (active == c) active = null; }
    }
    public static byte[] readBounded(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] chunk = new byte[8192]; int n;
        while ((n = in.read(chunk)) != -1) { if (out.size() + n > max) throw new IOException("File or response is too large."); out.write(chunk, 0, n); }
        return out.toByteArray();
    }
}
