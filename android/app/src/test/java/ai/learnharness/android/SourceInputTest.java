package ai.learnharness.android;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class SourceInputTest {
    @Test public void noApprovalAddsNoBookText()throws Exception{JSONArray history=new JSONArray("[{\"role\":\"user\",\"content\":\"Question\"}]");assertEquals(history.toString(),SourceInput.forTurn(history,"").toString());}
    @Test public void approvedPassageIsOnlyInThisRequestAndHistoryIsUnchanged()throws Exception{JSONArray history=new JSONArray("[{\"role\":\"user\",\"content\":\"Question\"}]");JSONArray input=SourceInput.forTurn(history,"[Source: Book — Page 2] A passage");assertTrue(input.getJSONObject(0).getString("content").contains("A passage"));assertEquals("Question",history.getJSONObject(0).getString("content"));assertEquals("user",input.getJSONObject(0).getString("role"));}
    @Test(expected=java.io.IOException.class)public void rejectsUnboundedExcerpt()throws Exception{StringBuilder text=new StringBuilder();for(int i=0;i<6001;i++)text.append('x');SourceInput.forTurn(new JSONArray("[{\"role\":\"user\",\"content\":\"Question\"}]"),text.toString());}
}
