package ai.learnharness.android;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import android.view.*;import android.widget.*;
import org.json.*;import org.junit.*;import static org.junit.Assert.*;
public class GraphDeviceTest {
 private final android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();private JSONObject before;
 @Before public void setup()throws Exception{Records records=new Records(context);before=new JSONObject(records.root.toString());}
 @After public void cleanup()throws Exception{Records records=new Records(context);records.root=before;records.save();}
 private JSONObject request(String operation)throws Exception{return new JSONObject().put("operation",operation).put("sourceId","graph_qa").put("sessions",new JSONArray().put(new JSONObject().put("id","graph_qa").put("title","Graph QA").put("created",0)));}
 private JSONObject seed(OriginalCore core)throws Exception{return core.call(request("graphMerge").put("findings",new JSONObject("{propositions:[{id:'p1',name:'Pairs',statement:'A pair has two objects.',aliases:[],messageId:'m1'},{id:'p2',name:'Two pairs',statement:'Two pairs have four objects.',aliases:[],messageId:'m2'}],chains:[{premises:['p1'],conclusion:'p2'}]}" )).put("review",new JSONObject().put("links",new JSONArray())));}
 @Test public void originalDeletionRemovesChainsAndProjectionDoesNotResurrectNode()throws Exception{
  try(OriginalCore core=new OriginalCore(context)){JSONObject seeded=seed(core);assertTrue(seeded.toString(),seeded.getBoolean("ok"));Records records=new Records(context);GraphRecords.apply(records,seeded.getJSONObject("state"),seeded.getJSONObject("value"));String id=seeded.getJSONObject("value").getJSONArray("nodes").getJSONObject(0).getString("id");assertNotNull(records.find("knowledge",id));
   JSONObject removed=core.call(request("graphDeleteNode").put("graph",seeded.getJSONObject("state")).put("nodeId",id));GraphRecords.apply(records,removed.getJSONObject("state"),removed.getJSONObject("value"));assertNull(records.find("knowledge",id));assertEquals(0,removed.getJSONObject("value").getJSONArray("chains").length());GraphContract.validate(GraphRecords.snapshot(records));records.save();assertNull(new Records(context).find("knowledge",id));
  }
 }
 @Test public void graphEditPersistsInLedgerAndProjection()throws Exception{
  try(OriginalCore core=new OriginalCore(context)){JSONObject seeded=seed(core);Records records=new Records(context);GraphRecords.apply(records,seeded.getJSONObject("state"),seeded.getJSONObject("value"));String id=seeded.getJSONObject("value").getJSONArray("nodes").getJSONObject(0).getString("id");JSONObject claim=records.find("knowledge",id);GraphRecords.edit(records,claim,"Pair definition","Exactly two objects form a pair.",claim.getJSONArray("premises"));records.save();JSONObject view=core.call(request("graphView").put("graph",GraphRecords.snapshot(new Records(context))));assertEquals("Exactly two objects form a pair.",view.getJSONObject("value").getJSONArray("nodes").getJSONObject(0).getString("statement"));
  }
 }
 @Test public void sessionDeletionCanPreserveOrRemoveItsGraphContributions()throws Exception{
  try(OriginalCore core=new OriginalCore(context)){JSONObject seeded=seed(core),kept=core.call(request("graphDeleteSession").put("graph",seeded.getJSONObject("state")).put("mode","preserve-graph"));assertEquals(2,kept.getJSONObject("value").getJSONArray("nodes").length());JSONObject removed=core.call(request("graphDeleteSession").put("graph",seeded.getJSONObject("state")).put("mode","delete-nodes"));assertEquals(0,removed.getJSONObject("value").getJSONArray("nodes").length());assertEquals(0,removed.getJSONObject("value").getJSONArray("chains").length());}
 }
 private TextView text(View root,String value){if(root instanceof TextView && ((TextView)root).getText().toString().contains(value))return (TextView)root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextView found=text(((ViewGroup)root).getChildAt(i),value);if(found!=null)return found;}return null;}
 @Test public void nativeMapShowsOriginalPropositionsAndReasoningChains()throws Exception{
  try(OriginalCore core=new OriginalCore(context)){JSONObject seeded=seed(core);Records records=new Records(context);GraphRecords.apply(records,seeded.getJSONObject("state"),seeded.getJSONObject("value"));records.save();}
  try(ActivityScenario<GraphActivity> scene=ActivityScenario.launch(GraphActivity.class)){boolean[] ready={false};for(int i=0;i<70 && !ready[0];i++){scene.onActivity(a->ready[0]=text(a.getWindow().getDecorView(),"2 propositions · 1 reasoning chains")!=null);if(!ready[0])Thread.sleep(100);}assertTrue("Native map did not finish loading",ready[0]);scene.onActivity(a->{assertNotNull(text(a.getWindow().getDecorView(),"A pair has two objects."));assertNotNull(text(a.getWindow().getDecorView(),"Reasoning chains"));});}
 }
}
