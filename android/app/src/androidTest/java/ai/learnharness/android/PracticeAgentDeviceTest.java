package ai.learnharness.android;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;import org.junit.Test;import static org.junit.Assert.*;
public class PracticeAgentDeviceTest {
 private final android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
 private JSONObject session()throws Exception{return new JSONObject().put("kind","practice").put("phase","Practice");}
 private JSONArray input()throws Exception{return new JSONArray().put(new JSONObject().put("role","user").put("content","Practice addition"));}
 private ProviderClient.Reply tool(String name,String args)throws Exception{return new ProviderClient.Reply("",2,1,new JSONArray().put(ToolProtocol.call("call_"+name,name,args)),null);}
 private String problem(){return "{statement:'How many objects are in two pairs?',mode:'recall',modelSolution:'Two plus two equals four.'}";}
 @Test public void poseEndsTurnAndHidesCommittedSolution()throws Exception{
  int[] count={0};var result=new PracticeAgent(context).run(session(),input(),(prompt,history,tools)->{count[0]++;assertTrue(prompt.contains("no problem is ever conversation"));assertTrue(tools.toString().contains("modelSolution"));return tool("problem",problem());});assertEquals(1,count[0]);assertNotNull(result.pending());assertEquals("problem",result.pending().getString("kind"));assertFalse(result.transcript().toString().contains("Two plus two equals four"));assertEquals("tool",result.history().getJSONObject(2).getString("role"));assertTrue(result.history().getJSONObject(2).getString("content").startsWith("Problem posed"));
 }
 @Test public void thinkingMayContinueWithoutConcludingOrRevealing()throws Exception{
  var posed=new PracticeAgent(context).run(session(),input(),(p,h,t)->tool("problem",problem()));JSONObject state=session().put("pendingQuestion",posed.pending());JSONArray history=posed.history().put(new JSONObject().put("role","user").put("content","Your thinking:\nI can count one pair. Can I have a hint?"));var hint=new PracticeAgent(context).run(state,history,(p,h,t)->new ProviderClient.Reply("What happens if you count the second pair as well?",2,1));assertNotNull(hint.pending());assertFalse(hint.transcript().toString().contains("Two plus two equals four"));
 }
 @Test public void concludeRevealsOriginalCommitmentAndBlocksNextProblemInSameTurn()throws Exception{
  var posed=new PracticeAgent(context).run(session(),input(),(p,h,t)->tool("problem",problem()));int[] count={0};var result=new PracticeAgent(context).run(session().put("pendingQuestion",posed.pending()),posed.history(),(p,h,t)->{switch(count[0]++){case 0:return tool("conclude","{outcome:'solved',verdict:'Correctly counted both pairs.',rung:null,missClass:null,retest:false}");default:assertEquals(0,t.length());return new ProviderClient.Reply("Ready when you want another problem.",2,1);}});assertEquals(2,count[0]);assertNull(result.pending());assertTrue(result.transcript().toString().contains("Two plus two equals four"));assertTrue(result.history().toString().contains("explicit go-ahead"));
 }
 @Test public void activeProblemGateReturnsRepairErrorAndKeepsOriginalProblem()throws Exception{
  var posed=new PracticeAgent(context).run(session(),input(),(p,h,t)->tool("problem",problem()));int[] count={0};var result=new PracticeAgent(context).run(session().put("pendingQuestion",posed.pending()),posed.history(),(p,h,t)->{if(count[0]++==0)return tool("problem",problem());assertTrue(h.getJSONObject(h.length()-1).getString("content").contains("already active"));return new ProviderClient.Reply("Continue with the current problem.",2,1);});assertNotNull(result.pending());assertEquals(2,count[0]);
 }
 @Test public void originalArgumentHealAndTargetMembershipRunOnAndroid()throws Exception{
  try(OriginalCore core=new OriginalCore(context)){
   JSONObject input=new JSONObject(problem()).put("mode","\"recall\"").put("target",new JSONArray().put("Addition"));JSONObject valid=core.call(new JSONObject().put("operation","validate").put("name","problem").put("input",input).put("nodes",new JSONArray().put(new JSONObject().put("id","n_add").put("name","Addition"))));assertTrue(valid.toString(),valid.getBoolean("ok"));assertEquals("n_add",valid.getJSONObject("value").getJSONArray("target").getString(0));assertEquals("recall",valid.getJSONObject("value").getString("mode"));
  }
 }

 @Test public void sourceRequestRetainsTheActivePracticeProblem()throws Exception{
  var posed=new PracticeAgent(context).run(session(),input(),(p,h,t)->tool("problem",problem()));JSONObject session=session().put("pendingQuestion",posed.pending()).put("sourceFiles",new JSONArray().put(new JSONObject("{id:'book',title:'Pairs.txt',kind:'Text',uri:'content://unavailable/never-open'}")));
  var result=new PracticeAgent(context).run(session,posed.history(),(p,h,t)->tool("read","{path:'Pairs.txt'}"));assertEquals("source",result.pending().getString("kind"));assertEquals("Two plus two equals four.",result.pending().getJSONObject("retainedProblem").getString("modelSolution"));assertFalse(result.transcript().toString().contains("Two plus two equals four"));
 }
}
