package ai.learnharness.android;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;import org.json.*;import static org.junit.Assert.*;
public class LessonAgentDeviceTest {
 private final android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
 private JSONObject session()throws Exception{return new JSONObject().put("phase","Explore");}
 private JSONArray input()throws Exception{return new JSONArray().put(new JSONObject().put("role","user").put("content","Teach me arithmetic"));}
 private ProviderClient.Reply call(String name,JSONObject args)throws Exception{return new ProviderClient.Reply("",2,1,new JSONArray().put(ToolProtocol.call("call_"+name,name,args.toString())),null);}
 private JSONObject question()throws Exception{return new JSONObject("{question:'What is two plus two?',options:[{label:'Four',value:'four'},{label:'Five',value:'five'}],explanation:'Two pairs make four.',correctAnswer:'four'}");}
 @Test public void aStructuredQuestionPausesWithoutAnotherModelRequest()throws Exception{
  int[] calls={0};var result=new LessonAgent(context).run(session(),input(),false,(prompt,history,tools)->{calls[0]++;assertTrue(prompt.contains("probe"));assertTrue(tools.toString().contains("probe_done"));return call("probe",question());});assertEquals(1,calls[0]);assertNotNull(result.pending());assertEquals("quiz",result.pending().getString("kind"));assertEquals(2,result.history().length());assertFalse(result.pending().toString().contains("selectedValues"));
 }
 @Test public void invalidQuestionReturnsErrorToModelBeforeRepair()throws Exception{
  int[] calls={0};var result=new LessonAgent(context).run(session(),input(),false,(prompt,history,tools)->{if(calls[0]++==0)return call("probe",question().put("correctAnswer","missing"));assertEquals("tool",history.getJSONObject(history.length()-1).getString("role"));assertTrue(history.getJSONObject(history.length()-1).getString("content").contains("does not match"));return call("probe",question());});assertEquals(2,calls[0]);assertNotNull(result.pending());assertEquals(4,result.reply().inputTokens());
 }
 @Test public void probeDoneAutomaticallyHandsOffUsingOriginalTeacherPromptAndPlanContract()throws Exception{
  int[] calls={0};var result=new LessonAgent(context).run(session(),input(),false,(prompt,history,tools)->{
   switch(calls[0]++){
    case 0:return call("probe_done",new JSONObject("{assessment:{strands:[{strand:'Counting',assessment:'Can count pairs; addition is the next step.'}]}}"));
    case 1:assertTrue(history.toString().contains("Assessment recorded"));return new ProviderClient.Reply("We have a starting point.",2,1);
    case 2:assertTrue(history.getJSONObject(history.length()-1).getString("content").startsWith("Next: plan the full teaching arc"));assertTrue(tools.toString().contains("plan_init"));assertFalse(tools.toString().contains("probe_done"));return call("plan_init",new JSONObject().put("mermaid","graph TD\ncount[Counting] --> add[Addition]"));
    default:return new ProviderClient.Reply("Here is our plan. Shall we begin?",2,1);
   }
  });assertEquals(4,calls[0]);assertEquals("Plan",result.phase());assertEquals(2,result.tree().getJSONArray("nodes").length());assertNotNull(result.assessment());assertNull(result.pending());assertEquals(8,result.reply().inputTokens());
 }
 @Test public void teacherRejectsOffMapQuestionAndFreeRecallUsesOriginalCommitment()throws Exception{
  JSONObject session=session().put("phase","Learn").put("planTree",new JSONObject("{nodes:[{id:'add',title:'Addition'}],edges:[],version:1}"));int[] calls={0};
  var result=new LessonAgent(context).run(session,input(),true,(prompt,history,tools)->{assertTrue(tools.toString().contains("explain"));assertFalse(tools.toString().contains("\"name\":\"quiz\""));JSONObject args=new JSONObject("{type:'check',question:'Explain adding pairs',nodeId:'missing',correctAnswer:'Two pairs contain four objects.'}");if(calls[0]++>0){assertTrue(history.getJSONObject(history.length()-1).getString("content").contains("nodeId"));args.put("nodeId","add");}return call("explain",args);});assertEquals(2,calls[0]);assertEquals("Two pairs contain four objects.",result.pending().getString("committedAnswer"));
 }

 @Test public void proberReadsOriginalNamesOnlyGraphToolsWithoutInjectingStatements()throws Exception{
  JSONObject graph=new JSONObject("{nodes:[{id:'n_pairs',name:'Pairs',statement:'PRIVATE_GRAPH_STATEMENT',aliases:[],createdAt:'2026-10-24T00:00:00Z',provenance:[{chatId:'prior',messageId:'m'}]}],chains:[]}");
  JSONObject session=session().put("graphToolsEnabled",true).put("graphContext",graph).put("graphSessions",new JSONArray().put(new JSONObject().put("id","prior").put("title","Earlier counting")));int[] calls={0};
  var result=new LessonAgent(context).run(session,input(),false,(prompt,history,tools)->{assertFalse(prompt.contains("PRIVATE_GRAPH_STATEMENT"));switch(calls[0]++){
   case 0:assertTrue(tools.toString().contains("list_sessions"));assertFalse(history.toString().contains("PRIVATE_GRAPH_STATEMENT"));return call("list_sessions",new JSONObject());
   case 1:assertTrue(history.toString().contains("Earlier counting"));return call("list_propositions",new JSONObject().put("session","prior"));
   default:assertFalse(history.toString().contains("PRIVATE_GRAPH_STATEMENT"));assertTrue(history.toString().contains("Pairs"));return call("probe",question());
  }});assertEquals(3,calls[0]);assertNotNull(result.pending());
 }

 @Test public void originalReadAndGrepRequestsPauseBeforeAnySourceContentIsRead()throws Exception{
  for(String name:java.util.List.of("read","grep")){int[] count={0};JSONObject session=session().put("sourceFiles",new JSONArray().put(new JSONObject("{id:'qa_source',title:'Physics.pdf',kind:'PDF',uri:'content://unavailable/never-open'}")));
   var result=new LessonAgent(context).run(session,input(),false,(prompt,history,tools)->{count[0]++;assertTrue(tools.toString().contains("\"name\":\""+name+"\""));assertFalse(history.toString().contains("content://"));return call(name,name.equals("read")?new JSONObject().put("path","Physics.pdf"):new JSONObject().put("pattern","force").put("path","Physics.pdf"));});
   assertEquals(1,count[0]);assertEquals("source",result.pending().getString("kind"));assertEquals(2,result.history().length());assertFalse(result.history().toString().contains("content://unavailable"));assertEquals("assistant",result.history().getJSONObject(1).getString("role"));
  }
 }
}
