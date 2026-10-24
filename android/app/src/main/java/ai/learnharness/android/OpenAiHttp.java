package ai.learnharness.android;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Small HTTPS transport. Credentials are headers/form fields, never URLs or diagnostics. */
public class OpenAiHttp {
    public static final String AUTH = "https://auth.openai.com";
    public static final String API = "https://api.openai.com/v1";
    public static final String USAGE = "https://chatgpt.com/settings/usage";
    private volatile HttpURLConnection active;
    public void cancel() { HttpURLConnection connection = active; if (connection != null) connection.disconnect(); }
    public JSONObject get(String url, String token) throws Exception { return request(url, "GET", "", "", token); }
    public JSONObject form(String url, Map<String,String> values) throws Exception {
        return request(url, "POST", "application/x-www-form-urlencoded", OAuthProtocol.encode(values), "");
    }
    public JSONObject request(String url, String method, String mime, String body, String token) throws Exception {
        HttpURLConnection connection = connect(url, method, mime, body, token);
        try {
            check(connection);
            try (InputStream in = connection.getInputStream()) {
                byte[] bytes = ProviderClient.readBounded(in, 2_000_000);
                return bytes.length == 0 ? new JSONObject() : new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); if (active == connection) active = null; }
    }
    public ProviderClient.Reply responses(String model, String token, String instructions, JSONArray input) throws Exception {
        return responses(model,token,instructions,input,"default");
    }
    public ProviderClient.Reply responses(String model, String token, String instructions, JSONArray input,String thinking) throws Exception {
        return responses(model,token,instructions,input,thinking,null);
    }
    public ProviderClient.Reply responses(String model,String token,String instructions,JSONArray input,String thinking,java.util.function.Consumer<String> preview) throws Exception {
        return responses(model,token,instructions,input,thinking,preview,null);
    }
    public ProviderClient.Reply responses(String model,String token,String instructions,JSONArray input,String thinking,java.util.function.Consumer<String> preview,JSONArray tools)throws Exception{
        JSONObject body = new JSONObject().put("model", model).put("instructions", instructions).put("input", ToolProtocol.messages(input,"Responses")).put("store", false).put("stream", true);
        if(tools!=null)body.put("tools",ToolProtocol.declarations(tools,"Responses")).put("parallel_tool_calls",false).put("include",new JSONArray().put("reasoning.encrypted_content"));
        Thinking.apply(body,"ChatGPT",model,thinking);
        return streamResponses(body,token,preview);
    }
    /** Built-in search uses the same selected plan account and never an API-key fallback. */
    public ProviderClient.Reply webSearch(String model,String token,String instructions,JSONArray input,String thinking,JSONArray tools,boolean required)throws Exception{
        return streamResponses(webSearchBody(model,instructions,input,thinking,tools,required),token,null);
    }
    static JSONObject webSearchBody(String model,String instructions,JSONArray input,String thinking,JSONArray tools,boolean required)throws Exception{
        JSONArray functions=new JSONArray();for(int i=0;tools!=null && i<tools.length();i++){JSONObject tool=tools.getJSONObject(i);if(!tool.optString("name").equals("web_search"))functions.put(tool);}
        JSONArray declarations=ToolProtocol.declarations(functions,"Responses");declarations.put(new JSONObject().put("type","web_search").put("external_web_access",true));
        JSONObject body=new JSONObject().put("model",model).put("instructions",instructions).put("input",ToolProtocol.messages(input,"Responses")).put("store",false).put("stream",true).put("tools",declarations).put("tool_choice",required?"required":"auto").put("include",new JSONArray().put("reasoning.encrypted_content").put("web_search_call.action.sources"));
        Thinking.apply(body,"ChatGPT",model,thinking);return body;
    }
    private ProviderClient.Reply streamResponses(JSONObject body,String token,java.util.function.Consumer<String> preview)throws Exception{
        HttpURLConnection connection = connect(API + "/responses", "POST", "application/json", body.toString(), token);
        try {
            check(connection);
            try (InputStream in = connection.getInputStream()) { return ResponseStream.read(in,preview); }
            catch(Failure error) { throw new Failure(connection.getResponseCode(),error.code,error.param,connection.getHeaderField("x-request-id"),error.shape); }
        } finally { connection.disconnect(); if (active == connection) active = null; }
    }
    private HttpURLConnection connect(String url, String method, String mime, String body, String token) throws Exception {
        URI uri = new URI(url);
        if (!"https".equals(uri.getScheme()) || !("auth.openai.com".equals(uri.getHost()) || "api.openai.com".equals(uri.getHost())) || uri.getUserInfo()!=null || uri.getFragment()!=null || uri.getQuery()!=null || (uri.getPort()!=-1 && uri.getPort()!=443))
            throw new IOException("Refusing an unexpected OpenAI endpoint.");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection(); active = connection;
        try {
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(20000); connection.setReadTimeout(180000); connection.setRequestMethod(method);
            if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
            if (!mime.isEmpty()) connection.setRequestProperty("Content-Type", mime);
            connection.setRequestProperty("Accept", url.endsWith("/responses") ? "text/event-stream" : "application/json");
            if (method.equals("POST")) {
                connection.setDoOutput(true);
                try (OutputStream out = connection.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); }
            }
            return connection;
        } catch (Exception e) { connection.disconnect(); if (active == connection) active=null; throw e; }
    }
    private void check(HttpURLConnection connection) throws Exception {
        int status = connection.getResponseCode();
        if (status >= 200 && status < 300) return;
        String code = "", param = "", shape = "empty";
        try (InputStream in = connection.getErrorStream()) {
            if (in != null) {
                JSONObject body = new JSONObject(new String(ProviderClient.readBounded(in, 32000), StandardCharsets.UTF_8));
                JSONObject error = body.optJSONObject("error");
                if (error != null) { shape="error object"; code=error.optString("code"); param=error.optString("param"); }
                else if (body.opt("error") instanceof String) { shape="OAuth error"; code=body.optString("error"); }
                else shape=body.has("detail") ? "detail" : "JSON object";
            }
        } catch (Exception ignored) { shape="unstructured"; }
        throw new Failure(status, code, param, connection.getHeaderField("x-request-id"), shape);
    }
    public static final class Failure extends IOException {
        public final int status; public final String code, param, requestId, shape;
        public Failure(int status, String code, String param, String requestId, String shape) {
            super(message(status, code)); this.status=status; this.code=safe(code); this.param=safe(param); this.requestId=safe(requestId); this.shape=shape;
        }
        private static String safe(String value) { return value != null && value.matches("[a-zA-Z0-9_.:/-]{1,160}") ? value : ""; }
        private static String message(int status, String code) {
            if ("subscription_sharing_usage_limit_exceeded".equals(code) || status==429) return "ChatGPT plan or app usage limit reached. Review your limits in Manage usage. No API-key fallback was used.";
            if ("subscription_sharing_user_not_eligible".equals(code)) return "ChatGPT plan usage is unavailable for this account, workspace, or policy. You can choose another account or explicitly select your API provider.";
            if ("subscription_sharing_unsupported_capability".equals(code)) return "This model or request capability is not supported by ChatGPT plan usage.";
            if ("invalid_grant".equals(code) || code != null && code.contains("refresh_token")) return "ChatGPT sign-in has expired or was revoked. Continue with the saved account to sign in again.";
            if (status==401) return "ChatGPT authentication was not accepted. Check the selected account and sign in again if access was revoked.";
            if (status==403) return "ChatGPT plan access was not permitted. Check your account, permissions, and region. No other billing path was used.";
            if (status==503) return "ChatGPT plan usage is temporarily unavailable or not enabled for this account. Your sign-in has been kept. Try again later.";
            return "OpenAI returned HTTP " + status + ". Check your ChatGPT connection and try again.";
        }
        public String diagnostic() { return getMessage()+"\n\nHTTP "+status+(code.isEmpty()?"":"\nCode: "+code)+(param.isEmpty()?"":"\nParameter: "+param)+(requestId.isEmpty()?"":"\nRequest ID: "+requestId)+"\nResponse shape: "+shape; }
        public boolean terminalRefresh() { return java.util.Arrays.asList("invalid_grant","invalid_refresh_token","token_expired","refresh_token_expired","refresh_token_invalidated","refresh_token_reused").contains(code); }
    }
}
