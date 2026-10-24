package ai.learnharness.android;
import android.content.*;
import android.webkit.WebView;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
public class InteractiveDeviceTest {
    Context context;String id;boolean eink;
    @Before public void setup()throws Exception{context=InstrumentationRegistry.getInstrumentation().getTargetContext();eink=context.getSharedPreferences("settings",0).getBoolean("eink",false);context.getSharedPreferences("settings",0).edit().putBoolean("eink",false).commit();JSONObject template=InteractiveTemplates.sample();template.remove("shortcut");id=template.getString("id");new InteractiveTemplates(context).save(template,false);}
    @After public void cleanup()throws Exception{new InteractiveTemplates(context).delete(id);context.getSharedPreferences("settings",0).edit().putBoolean("eink",eink).commit();}
    private Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    @Test public void copiedAgoraHostBecomesReadyAndKeepsGeneratedFrameOpaque()throws Exception{
        try(ActivityScenario<InteractiveActivity> scene=ActivityScenario.launch(new Intent(context,InteractiveActivity.class).putExtra("template",id))){
            boolean[] ready={false};for(int i=0;i<100 && !ready[0];i++){scene.onActivity(a->{try{ready[0]=(boolean)field(a,"ready");}catch(Exception e){throw new AssertionError(e);}});if(!ready[0])Thread.sleep(100);}
            assertTrue("Renderer did not signal ready",ready[0]);CountDownLatch latch=new CountDownLatch(1);String[] result={""};scene.onActivity(a->{try{WebView view=(WebView)field(a,"browser");assertFalse(view.getSettings().getAllowFileAccess());assertFalse(view.getSettings().getAllowContentAccess());view.evaluateJavascript("JSON.stringify({frame:!!document.querySelector('iframe'),opaque:document.querySelector('iframe').contentDocument===null,sandbox:document.querySelector('iframe').getAttribute('sandbox')})",value->{result[0]=value;latch.countDown();});}catch(Exception e){throw new AssertionError(e);}});assertTrue(latch.await(5,TimeUnit.SECONDS));assertTrue(result[0].contains("opaque"));assertTrue(result[0].contains("true"));assertFalse(result[0].contains("allow-same-origin"));
        }
    }
    @Test public void catalogueImportRejectsConflictsWithoutPartialWrite()throws Exception{
        InteractiveTemplates store=new InteractiveTemplates(context);int before=store.list().length();JSONObject newTemplate=InteractiveTemplates.sample();newTemplate.remove("shortcut");JSONArray conflict=new JSONArray().put(newTemplate).put(store.get(id));
        try{store.importCatalogue(conflict.toString());fail();}catch(java.io.IOException expected){}
        assertEquals(before,store.list().length());assertEquals("Probability explorer",store.get(id).getString("name"));
    }
}
