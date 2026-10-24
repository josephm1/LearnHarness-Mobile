package ai.learnharness.android;
import org.junit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
public class ProviderStreamTest {
    private ProviderClient.Reply read(String provider,String data,List<String> seen)throws Exception{return ProviderStream.read(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)),provider,seen::add);}
    @Test public void openAiStreamsVisibleTextAndUsage()throws Exception{
        List<String> seen=new ArrayList<>();var r=read("OpenAI compatible","data: {\"choices\":[{\"delta\":{\"content\":\"Hello\",\"reasoning_content\":\"private\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\" world\"},\"finish_reason\":\"stop\"}]}\n\ndata: {\"choices\":[],\"usage\":{\"prompt_tokens\":4,\"completion_tokens\":2}}\n\ndata: [DONE]\n\n",seen);
        assertEquals("Hello world",r.text());assertEquals("Hello",seen.get(0));assertEquals(4,r.inputTokens());assertFalse(seen.toString().contains("private"));
    }
    @Test public void anthropicExcludesThinkingAndRequiresStop()throws Exception{
        var r=read("Anthropic","data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":8}}}\n\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"private\"}}\n\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Answer\"}}\n\ndata: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":3}}\n\ndata: {\"type\":\"message_stop\"}\n\n",new ArrayList<>());
        assertEquals("Answer",r.text());assertEquals(8,r.inputTokens());assertEquals(3,r.outputTokens());
    }
    @Test public void geminiUsesOnlyAnswerParts()throws Exception{
        var r=read("Gemini","data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"private\",\"thought\":true},{\"text\":\"Answer\"}]}}]}\n\ndata: {\"candidates\":[{\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":5,\"candidatesTokenCount\":1}}\n\n",new ArrayList<>());
        assertEquals("Answer",r.text());assertEquals(5,r.inputTokens());
    }
    @Test public void previewArrivesBeforeInterruptedStreamFails()throws Exception{
        List<String> seen=new ArrayList<>();try{read("OpenRouter","data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n",seen);fail();}catch(IOException expected){assertEquals(List.of("partial"),seen);}
    }
    @Test(expected=IOException.class)public void rejectsTruncation()throws Exception{read("DeepSeek","data: {\"choices\":[{\"delta\":{\"content\":\"partial\"},\"finish_reason\":\"length\"}]}\n\ndata: [DONE]\n\n",new ArrayList<>());}
    @Test(expected=IOException.class)public void rejectsErrorAfterText()throws Exception{read("Anthropic","data: {\"type\":\"error\",\"error\":{\"message\":\"private\"}}\n\n",new ArrayList<>());}
    @Test public void responsesPreviewIsNotACompletedRecord()throws Exception{
        List<String> seen=new ArrayList<>();try{ResponseStream.read(new ByteArrayInputStream("data: {\"type\":\"response.output_text.delta\",\"delta\":\"**partial**\"}\n\ndata: {\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"failed\"}}}\n\n".getBytes(StandardCharsets.UTF_8)),seen::add);fail();}catch(IOException expected){assertEquals(List.of("**partial**"),seen);}
    }
}
