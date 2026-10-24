package ai.learnharness.android;
import org.junit.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** Real request construction and response parsing against an in-memory HTTPS connection. */
public class ProviderClientTest {
    private static FakeConnection last;
    private static String response;
    private static int status;
    @BeforeClass public static void interceptHttps() {
        URL.setURLStreamHandlerFactory(protocol -> protocol.equals("https") ? new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) { return last = new FakeConnection(url); }
        } : null);
    }
    @Before public void setup() { status = 200; }
    private static class FakeConnection extends HttpURLConnection {
        final ByteArrayOutputStream request = new ByteArrayOutputStream();
        boolean disconnected;
        FakeConnection(URL url) { super(url); }
        public void disconnect() { disconnected = true; }
        public boolean usingProxy() { return false; }
        public void connect() {}
        public OutputStream getOutputStream() { return request; }
        public int getResponseCode() { return status; }
        public InputStream getInputStream() { return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)); }
        public InputStream getErrorStream() { return new ByteArrayInputStream((response==null?"":response).getBytes(StandardCharsets.UTF_8)); }
        public String getHeaderField(String name) { return name.equals("x-request-id")?"req-test":null; }
        JSONObject body() throws Exception { return new JSONObject(request.toString(StandardCharsets.UTF_8)); }
    }
    private ProviderClient.Reply call(String provider) throws Exception {
        return new ProviderClient().complete(new ProviderClient.Config(provider, "https://example.test/v1", "model-test", "test-key"), "Teach", new JSONArray("[{\"role\":\"user\",\"content\":\"Hello\"}]"));
    }
    @Test public void openAiRequestAndUsage() throws Exception {
        response = "{\"choices\":[{\"message\":{\"content\":\"Lesson\"}}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":7}}";
        var reply = call("OpenAI compatible"); assertEquals("Lesson", reply.text()); assertEquals(12, reply.inputTokens()); assertEquals(7, reply.outputTokens());
        assertEquals("/v1/chat/completions", last.getURL().getPath()); assertEquals("Bearer test-key", last.getRequestProperty("Authorization"));
        assertEquals("system", last.body().getJSONArray("messages").getJSONObject(0).getString("role")); assertFalse(last.getInstanceFollowRedirects()); assertTrue(last.disconnected);
    }
    @Test public void anthropicSystemAndHeaders() throws Exception {
        response = "{\"content\":[{\"type\":\"text\",\"text\":\"Lesson\"}],\"usage\":{\"input_tokens\":9,\"output_tokens\":4}}";
        assertEquals(9, call("Anthropic").inputTokens()); assertEquals("Teach", last.body().getString("system"));
        assertEquals("test-key", last.getRequestProperty("x-api-key")); assertEquals("/v1/messages", last.getURL().getPath());
    }
    @Test public void geminiExcludesInternalThoughts() throws Exception {
        response = "{\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"private\"},{\"text\":\"Lesson\"}]}}],\"usageMetadata\":{\"promptTokenCount\":11,\"candidatesTokenCount\":3}}";
        assertEquals("Lesson", call("Gemini").text()); assertEquals("test-key", last.getRequestProperty("x-goog-api-key"));
        assertEquals("/v1/models/model-test:generateContent", last.getURL().getPath());
    }
    @Test public void providerErrorDoesNotEchoCredentials() throws Exception {
        status = 401;
        try { call("OpenAI compatible"); fail(); } catch(IOException e) { assertTrue(e.getMessage().contains("401")); assertFalse(e.getMessage().contains("test-key")); }
        assertTrue(last.disconnected);
    }
    @Test(expected=IOException.class) public void refusesCleartext() throws Exception {
        new ProviderClient().complete(new ProviderClient.Config("OpenAI compatible", "http://example.test/v1", "model", "key"), "", new JSONArray());
    }
    @Test(expected=IOException.class) public void boundedReadsRejectOversizedData() throws Exception {
        ProviderClient.readBounded(new ByteArrayInputStream(new byte[200]), 100);
    }

    @Test public void planUsesOnlyDocumentedResponsesRoute() throws Exception {
        response="data: {\"type\":\"response.completed\",\"response\":{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Lesson\"}]}]}}\n\n";
        assertEquals("Lesson",new OpenAiHttp().responses("account-model","oauth-secret","Teach",new JSONArray("[{\"role\":\"user\",\"content\":\"Hi\"}]")).text());
        assertEquals("https://api.openai.com/v1/responses",last.getURL().toString());assertEquals("Bearer oauth-secret",last.getRequestProperty("Authorization"));
        JSONObject body=last.body();assertTrue(body.getBoolean("stream"));assertFalse(body.getBoolean("store"));assertEquals("Teach",body.getString("instructions"));assertTrue(body.get("input") instanceof JSONArray);
        assertFalse(body.has("max_output_tokens"));assertFalse(body.has("temperature"));assertFalse(body.has("previous_response_id"));assertFalse(body.toString().contains("oauth-secret"));
    }
    @Test public void codeExchangeUsesIssuedClientAndFormBody() throws Exception {
        response="{}";new OpenAiHttp().form(OpenAiHttp.AUTH+"/api/accounts/oauth/token",java.util.Map.of("client_id","issued-client","code","secret-code","code_verifier","secret-verifier","redirect_uri","http://127.0.0.1:12345/auth/callback"));
        var form=OAuthProtocol.query(last.request.toString(StandardCharsets.UTF_8));assertEquals("issued-client",form.get("client_id"));assertEquals("secret-code",form.get("code"));assertNull(last.getURL().getQuery());assertFalse(last.getInstanceFollowRedirects());
    }
    @Test public void directAdmissionErrorsStayDistinctAndRedacted() throws Exception {
        status=503;response="{\"detail\":\"private diagnostic text\"}";
        try{new OpenAiHttp().get(OpenAiHttp.API+"/models","oauth-secret");fail();}
        catch(OpenAiHttp.Failure e){assertEquals(503,e.status);assertEquals("detail",e.shape);assertEquals("req-test",e.requestId);assertFalse(e.diagnostic().contains("private diagnostic text"));assertFalse(e.diagnostic().contains("oauth-secret"));}
    }
    @Test public void usageErrorPreservesCodeAndRequestId() throws Exception {
        status=429;response="{\"error\":{\"code\":\"subscription_sharing_usage_limit_exceeded\",\"param\":\"model\"}}";
        try{new OpenAiHttp().get(OpenAiHttp.API+"/models","oauth-secret");fail();}
        catch(OpenAiHttp.Failure e){assertEquals("subscription_sharing_usage_limit_exceeded",e.code);assertEquals("model",e.param);assertEquals("req-test",e.requestId);assertTrue(e.getMessage().contains("Manage usage"));}
    }
    @Test(expected=IOException.class) public void neverSendsOauthTokenToCustomProvider() throws Exception { new OpenAiHttp().get("https://other-provider.example/models","oauth-secret"); }
    @Test public void thinkingDefaultsNeverChangeRequest() throws Exception {
        for(String p:new String[]{"ChatGPT","OpenAI compatible","Anthropic","Gemini","DeepSeek","OpenRouter"}) {
            JSONObject body=new JSONObject().put("generationConfig",new JSONObject());String before=body.toString();
            Thinking.apply(body,p,"any-model","default");assertEquals(before,body.toString());
        }
    }
    @Test public void providerThinkingUsesNativeSchemas() throws Exception {
        JSONObject anthropic=new JSONObject();Thinking.apply(anthropic,"Anthropic","claude-model","high");
        assertEquals("adaptive",anthropic.getJSONObject("thinking").getString("type"));assertEquals("high",anthropic.getJSONObject("output_config").getString("effort"));
        JSONObject extended=new JSONObject();Thinking.apply(extended,"Anthropic","claude-model","budget:4096");assertEquals(4096,extended.getJSONObject("thinking").getInt("budget_tokens"));assertTrue(extended.getInt("max_tokens")>4096);
        JSONObject gemini=new JSONObject().put("generationConfig",new JSONObject());Thinking.apply(gemini,"Gemini","gemini-3-flash","low");assertEquals("low",gemini.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"));
        JSONObject budget=new JSONObject().put("generationConfig",new JSONObject());Thinking.apply(budget,"Gemini","gemini-2.5-flash","budget:0");assertEquals(0,budget.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getInt("thinkingBudget"));
        JSONObject deep=new JSONObject();Thinking.apply(deep,"DeepSeek","deepseek-model","none");assertEquals("disabled",deep.getJSONObject("thinking").getString("type"));assertFalse(deep.has("reasoning_effort"));
        JSONObject router=new JSONObject();Thinking.apply(router,"OpenRouter","other/model","high");assertEquals("high",router.getJSONObject("reasoning").getString("effort"));
    }
    @Test public void planThinkingPreservesBillingAndStreaming() throws Exception {
        response="data: {\"type\":\"response.completed\",\"response\":{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Lesson\"}]}]}}\n\n";
        new OpenAiHttp().responses("gpt-6.1-sol","oauth-secret","Teach",new JSONArray(),"high");
        assertEquals("high",last.body().getJSONObject("reasoning").getString("effort"));assertTrue(last.body().getBoolean("stream"));assertFalse(last.body().getBoolean("store"));assertEquals("Bearer oauth-secret",last.getRequestProperty("Authorization"));
    }
    @Test public void apiThinkingReachesWire() throws Exception {
        response="{\"choices\":[{\"message\":{\"content\":\"Lesson\"}}]}";
        new ProviderClient().complete(new ProviderClient.Config("OpenAI compatible","https://example.test/v1","reasoning-model","key","low"),"Teach",new JSONArray());
        assertEquals("low",last.body().getString("reasoning_effort"));
    }
    @Test public void modelCatalogUsesProviderKeyWithoutGeneration() throws Exception {
        response="{\"data\":[{\"id\":\"a-model\",\"display_name\":\"A model\"}]}";
        JSONArray models=new ProviderClient().models(new ProviderClient.Config("Anthropic","https://example.test/v1","","test-key"));
        assertEquals("a-model",models.getJSONObject(0).getString("id"));assertEquals("GET",last.getRequestMethod());assertEquals("test-key",last.getRequestProperty("x-api-key"));assertEquals("/v1/models",last.getURL().getPath());assertEquals(0,last.request.size());assertTrue(last.disconnected);
    }
    @Test public void geminiCatalogFiltersModelsAndStripsPrefix() throws Exception {
        response="{\"models\":[{\"name\":\"models/gemini-test\",\"supportedGenerationMethods\":[\"generateContent\"]},{\"name\":\"models/embedding\",\"supportedGenerationMethods\":[\"embedContent\"]}]}";
        JSONArray models=new ProviderClient().models(new ProviderClient.Config("Gemini","https://example.test/v1beta","","test-key"));
        assertEquals(1,models.length());assertEquals("gemini-test",models.getJSONObject(0).getString("id"));assertEquals("test-key",last.getRequestProperty("x-goog-api-key"));
    }
    @Test(expected=IOException.class) public void rejectsThinkingNotSupportedByAdapter() throws Exception {Thinking.apply(new JSONObject(),"ChatGPT","gpt-6.1-sol","none");}
    @Test(expected=IOException.class) public void refusesCleartextCatalog() throws Exception {new ProviderClient().models(new ProviderClient.Config("OpenAI compatible","http://example.test","","secret"));}
    @Test public void apiStreamingRequestsSseAndDeliversPreview()throws Exception{
        response="data: {\"choices\":[{\"delta\":{\"content\":\"Live\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n";
        java.util.List<String> seen=new java.util.ArrayList<>();
        var reply=new ProviderClient().complete(new ProviderClient.Config("OpenAI compatible","https://example.test/v1","model","key"),"Teach",new JSONArray(),seen::add);
        assertTrue(last.body().getBoolean("stream"));assertTrue(last.body().getJSONObject("stream_options").getBoolean("include_usage"));assertEquals("Live",seen.get(0));assertEquals("Live",reply.text());
    }
    @Test public void geminiStreamingUsesSseRoute()throws Exception{
        response="data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Live\"}]},\"finishReason\":\"STOP\"}]}\n\n";
        new ProviderClient().complete(new ProviderClient.Config("Gemini","https://example.test/v1beta","gemini-test","key"),"Teach",new JSONArray(),text->{});
        assertEquals("/v1beta/models/gemini-test:streamGenerateContent",last.getURL().getPath());assertEquals("alt=sse",last.getURL().getQuery());assertFalse(last.body().has("stream"));
    }
}
