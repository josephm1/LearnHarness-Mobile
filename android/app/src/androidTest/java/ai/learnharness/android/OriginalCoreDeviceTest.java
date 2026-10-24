package ai.learnharness.android;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class OriginalCoreDeviceTest {
    @Test public void originalQuestionGradingAndPlanValidationRunOnAndroid()throws Exception{
        OriginalCore[] core={null};var instrumentation=InstrumentationRegistry.getInstrumentation();instrumentation.runOnMainSync(()->core[0]=new OriginalCore(instrumentation.getTargetContext()));
        try{
            JSONObject question=core[0].call(new JSONObject("{\"operation\":\"question\",\"name\":\"probe\",\"id\":\"qa\",\"input\":{\"question\":\"Pick two\",\"options\":[{\"label\":\"Two\",\"value\":\"two\"},{\"label\":\"Three\",\"value\":\"three\"}],\"correctAnswer\":\"two\",\"explanation\":\"Two is correct.\"}}"));assertTrue(question.toString(),question.getBoolean("ok"));
            JSONObject answer=core[0].call(new JSONObject().put("operation","answer").put("question",question.getJSONObject("value")).put("answer",new JSONObject("{\"values\":[\"two\"]}")));assertTrue(answer.getJSONObject("value").getBoolean("correct"));assertFalse(answer.getJSONObject("model").has("confidence"));
            JSONObject plan=core[0].call(new JSONObject().put("operation","plan").put("input",new JSONObject().put("mermaid","graph TD\na[A] --> b[B]")));assertTrue(plan.toString(),plan.getBoolean("ok"));
            JSONObject cycle=core[0].call(new JSONObject().put("operation","plan").put("input",new JSONObject().put("mermaid","graph TD\na[A] --> b[B]\nb --> a")));assertFalse(cycle.getBoolean("ok"));
        }finally{instrumentation.runOnMainSync(()->core[0].close());}
    }

    @Test public void originalSessionFindAndPlanStatusesRunLocally()throws Exception{
        try(OriginalCore core=new OriginalCore(InstrumentationRegistry.getInstrumentation().getTargetContext())){
            JSONObject found=core.call(new JSONObject().put("operation","sessionFind").put("text","A [literal] and another [LITERAL].").put("query","[literal]"));assertEquals(2,found.getJSONArray("value").length());assertEquals(2,found.getJSONArray("value").getJSONObject(0).getInt("start"));
            JSONObject statuses=core.call(new JSONObject("{operation:'planStatuses',tree:{nodes:[{id:'a',title:'A'},{id:'b',title:'B'}],edges:[],version:1},history:[{tool_calls:[{function:{name:'quiz',arguments:'{\"nodeId\":\"a\"}'}}]},{tool_calls:[{function:{name:'explain',arguments:'{\"nodeId\":\"b\"}'}}]}]}"));assertEquals("b",statuses.getString("current"));assertEquals("a",statuses.getJSONArray("visited").getString(0));
        }
    }
}
