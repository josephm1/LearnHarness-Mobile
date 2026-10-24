package ai.learnharness.android;
import org.junit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
public class ResponseStreamTest {
    private ProviderClient.Reply read(String s) throws Exception { return ResponseStream.read(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8))); }
    @Test public void completesWithUsage() throws Exception {
        var reply=read(": keepalive\n\nevent: response.output_text.delta\ndata: {\"type\":\"response.output_text.delta\",\"delta\":\"Hello\"}\n\ndata: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"usage\":{\"input_tokens\":8,\"output_tokens\":2}}}\n\n");
        assertEquals("Hello",reply.text());assertEquals(8,reply.inputTokens());assertEquals(2,reply.outputTokens());
    }
    @Test public void completedOutputDoesNotDuplicateDeltas() throws Exception {
        assertEquals("Hello",read("data: {\"type\":\"response.output_text.delta\",\"delta\":\"Hello\"}\n\ndata: {\"type\":\"response.completed\",\"response\":{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Hello\"}]}]}}\n\n").text());
    }
    @Test(expected=IOException.class) public void rejectsInterruptedPartialReply() throws Exception { read("data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\n"); }
    @Test(expected=IOException.class) public void rejectsDoneWithoutCompleted() throws Exception { read("data: [DONE]\n\n"); }
    @Test(expected=IOException.class) public void rejectsIncomplete() throws Exception { read("data: {\"type\":\"response.incomplete\"}\n\n"); }
    @Test public void preservesUsageErrorAfterPartialStream() throws Exception {
        try { read("data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\ndata: {\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"subscription_sharing_usage_limit_exceeded\"}}}\n\n");fail(); }
        catch(OpenAiHttp.Failure e) { assertEquals("subscription_sharing_usage_limit_exceeded",e.code);assertTrue(e.getMessage().contains("Manage usage")); }
    }
    @Test public void multilineDataIsSupported() throws Exception { assertEquals("A",read("data: {\"type\":\"response.completed\",\n"+"data: \"response\":{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"A\"}]}]}}\n\n").text()); }
    @Test public void diagnosticNeverEchoesUnsafeFields() { var e=new OpenAiHttp.Failure(403,"token=secret", "unsafe\nparam", "Bearer secret", "error object");assertFalse(e.diagnostic().contains("secret"));assertTrue(e.diagnostic().contains("403")); }
    @Test public void retainsCompletedStreamItemsWhenFinalResponseOmitsOutput()throws Exception{
        String events="data: {\"type\":\"response.output_item.done\",\"output_index\":0,\"item\":{\"type\":\"web_search_call\",\"status\":\"completed\",\"action\":{\"type\":\"search\"}}}\n\ndata: {\"type\":\"response.output_item.done\",\"output_index\":1,\"item\":{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Evidence\",\"annotations\":[{\"type\":\"url_citation\",\"url\":\"https://example.org\"}]}]}}\n\ndata: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"usage\":{\"input_tokens\":1,\"output_tokens\":2}}}\n\n";
        var reply=read(events);assertEquals("Evidence",reply.text());assertEquals(2,reply.nativeOutput().getJSONArray("responses").length());assertEquals("web_search_call",reply.nativeOutput().getJSONArray("responses").getJSONObject(0).getString("type"));assertEquals(1,reply.nativeOutput().getJSONArray("responses").getJSONObject(1).getJSONArray("content").getJSONObject(0).getJSONArray("annotations").length());
    }
}
