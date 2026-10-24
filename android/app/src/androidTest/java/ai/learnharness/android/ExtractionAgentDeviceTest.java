package ai.learnharness.android;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;import org.junit.Test;import java.io.*;import java.util.regex.*;import static org.junit.Assert.*;
public class ExtractionAgentDeviceTest {
 private final android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
 private JSONObject task()throws Exception{JSONObject session=new JSONObject().put("id","extract_qa").put("title","Pairs").put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content","Learner misconception is not evidence")).put(new JSONObject().put("id","teacher_qa").put("role","assistant").put("content","A pair contains two objects. SOURCE_ONLY_MARKER")));return new JSONObject().put("session",session).put("sessions",new JSONArray().put(session)).put("graph",new JSONObject());}
 private ProviderClient.Reply tool(String name,JSONObject args)throws Exception{return new ProviderClient.Reply("",3,2,new JSONArray().put(ToolProtocol.call("call_"+name,name,args.toString())),null);}
 private boolean has(JSONArray tools,String name)throws Exception{for(int i=0;i<tools.length();i++)if(tools.getJSONObject(i).getString("name").equals(name))return true;return false;}
 private ProviderClient.Reply fake(String prompt,JSONArray history,JSONArray tools)throws Exception{
  if(has(tools,"submit_findings")){assertTrue(prompt.startsWith("You are the extraction stage"));assertFalse(history.toString().contains("Learner misconception"));Matcher match=Pattern.compile("TEACHER \\[([^]]+)\\]").matcher(history.getJSONObject(0).getString("content"));assertTrue(match.find());return tool("submit_findings",new JSONObject().put("propositions",new JSONArray().put(new JSONObject().put("id","p1").put("name","Pair").put("statement","A pair contains two objects.").put("messageId",match.group(1)))).put("chains",new JSONArray()));}
  assertTrue(prompt.startsWith("You are the linking stage"));assertFalse(history.toString().contains("SOURCE_ONLY_MARKER"));assertFalse(history.toString().contains("messageId"));if(history.length()==1)return tool("list_sessions",new JSONObject());assertEquals("tool",history.getJSONObject(history.length()-1).getString("role"));return tool("submit_review",new JSONObject().put("links",new JSONArray()));
 }
 @Test public void originalExtractAuditMergeAndCoverageExecuteOnAndroid()throws Exception{
  JSONObject[] saved={null};int[] count={0};var reply=new ExtractionAgent(context).run(task(),this::fake,(graph,view,usage,batch,total)->{saved[0]=new JSONObject(graph.toString());count[0]++;assertEquals(1,view.getJSONArray("nodes").length());assertEquals(3,usage.getJSONArray("stages").length());});assertEquals(1,count[0]);assertEquals(9,reply.inputTokens());assertTrue(reply.nativeOutput().getJSONObject("view").getJSONArray("nodes").getJSONObject(0).getString("id").startsWith("n_"));assertEquals("teacher_qa",reply.nativeOutput().getJSONObject("view").getJSONArray("nodes").getJSONObject(0).getJSONArray("provenance").getJSONObject(0).getString("messageId"));
  var retry=new ExtractionAgent(context).run(task().put("graph",saved[0]),(p,h,t)->{fail("Covered source triggered another model request");return null;},null);assertEquals(0,retry.inputTokens());assertTrue(retry.text().contains("Nothing was sent"));
 }
 @Test public void cancelledCheckpointKeepsSuccessfulBatchAndLeavesRemainderUncovered()throws Exception{
  JSONObject task=task().put("budget",5);task.getJSONObject("session").getJSONArray("messages").put(new JSONObject().put("id","teacher_2").put("role","assistant").put("content","Two pairs contain four objects. Another separate source passage."));JSONObject[] saved={null};
  try{new ExtractionAgent(context).run(task,this::fake,(graph,view,usage,batch,total)->{assertEquals(2,total);saved[0]=graph;throw new IOException("test cancellation after committed batch");});fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("test cancellation"));}
  try(OriginalCore core=new OriginalCore(context)){JSONObject remaining=core.call(new JSONObject().put("operation","extractionBatches").put("graph",saved[0]).put("session",task.getJSONObject("session")));assertEquals(1,remaining.getInt("coveredCount"));assertEquals(1,remaining.getInt("uncoveredCount"));}
 }
 @Test public void originalGraphLookupUsesPromiseReturnAndTextualIdentityView()throws Exception{
  var reply=new ExtractionAgent(context).run(task(),this::fake,null);try(OriginalCore core=new OriginalCore(context)){
   JSONObject result=core.call(new JSONObject().put("operation","graphTool").put("auditor",true).put("name","find").put("input",new JSONObject().put("query","pair")).put("graph",reply.nativeOutput().getJSONObject("graph")).put("sessions",task().getJSONArray("sessions")));assertTrue(result.toString(),result.getBoolean("ok"));assertTrue(result.getString("value").contains("A pair contains two objects."));
  }
 }

 @Test public void stagesUseTheirOwnModelRoleRouting()throws Exception{
  java.util.ArrayList<String> roles=new java.util.ArrayList<>();new ExtractionAgent(context).run(task(),new LessonAgent.Model(){
   @Override public ProviderClient.Reply complete(String p,JSONArray h,JSONArray t){throw new AssertionError("Stage used unqualified active connection");}
   @Override public ProviderClient.Reply completeRole(String role,String p,JSONArray h,JSONArray t)throws Exception{roles.add(role);return fake(p,h,t);}
  },null);assertEquals(java.util.List.of("extractor","auditor","auditor"),roles);
 }

 @Test public void personalNotesUseOriginalNotesPromptWithoutPassageAnchors()throws Exception{
  JSONObject task=task();task.remove("session");task.put("notes",new JSONObject().put("id","src_note_qa").put("title","My pairs note").put("text","A pair contains two objects."));int[] calls={0};
  var reply=new ExtractionAgent(context).run(task,(prompt,history,tools)->{calls[0]++;if(has(tools,"submit_findings")){assertTrue(prompt.contains("author's personal notes"));assertFalse(tools.toString().contains("messageId"));assertTrue(history.toString().contains("SOURCE (the author's personal notes): My pairs note"));return tool("submit_findings",new JSONObject("{propositions:[{id:'p1',name:'Pair',statement:'A pair contains two objects.',messageId:'must-be-stripped'}],chains:[]}"));}return tool("submit_review",new JSONObject().put("links",new JSONArray()));},null);
  assertEquals(2,calls[0]);JSONObject graph=reply.nativeOutput().getJSONObject("graph");assertEquals("",graph.getJSONArray("nodes").getJSONObject(0).getJSONArray("provenance").getJSONObject(0).getString("messageId"));assertEquals("My pairs note",graph.getJSONArray("sources").getJSONObject(0).getString("filePath"));
  new ExtractionAgent(context).run(task.put("graph",graph),(p,h,t)->{fail("Unchanged note was extracted twice");return null;},null);
 }
}
