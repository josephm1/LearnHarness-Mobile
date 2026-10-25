package ai.learnharness.android;

import android.content.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

/** Local fixtures only: capture all e-ink surfaces and check draft/voice navigation. */
@RunWith(AndroidJUnit4.class)
public class EinkUiDeviceTest {
    Context context;JSONObject before;Map<String,?> preferences;String session="eink-visual-fixture";
    @Before public void setup()throws Exception{
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();Records store=new Records(context);before=new JSONObject(store.root.toString());preferences=new HashMap<>(context.getSharedPreferences("settings",0).getAll());
        context.getSharedPreferences("settings",0).edit().putBoolean("eink",true).putBoolean("useChatGpt",false).putString("model","").commit();
        store.root=Records.empty();
        store.rows("sessions").put(new JSONObject().put("id",session).put("title","Tell me about Maxwell equations").put("phase","Explore").put("messages",new JSONArray().put(new JSONObject().put("role","assistant").put("content","**Correct.**\n\nA current is moving electric charge. A steady current produces a magnetic field around the wire and a compass can turn in response to that field. The wire need not have a net electric charge to produce this magnetic field.")))
            .put("pendingQuestion",new JSONObject("{kind:'quiz',question:'Now imagine there is no wire nearby. An electric field in otherwise empty space changes with time. Can this be associated with a magnetic field?',mode:'single-select',options:[{label:'Only if the region contains a net electric charge.',value:'charge'},{label:'Yes, even without a current of charged particles in that region.',value:'yes'},{label:'No, a magnetic field requires a current of charged particles.',value:'no'}]}")));
        store.save();
    }
    @After public void cleanup()throws Exception{
        Records store=new Records(context);store.root=before;store.save();SharedPreferences.Editor edit=context.getSharedPreferences("settings",0).edit().clear();
        for(Map.Entry<String,?> entry:preferences.entrySet()){Object value=entry.getValue();String key=entry.getKey();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);}edit.commit();
    }
    private void screen(MainActivity activity,String page,String lesson,String settings)throws Exception{
        for(String[] field:new String[][]{{"screen",page},{"selectedSession",lesson},{"settingsPage",settings}}){var declared=MainActivity.class.getDeclaredField(field[0]);declared.setAccessible(true);declared.set(activity,field[1]);}
        var render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(activity);
    }
    private TextView text(View view,String text){if(view instanceof TextView && ((TextView)view).getText().toString().equals(text))return (TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=text(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;}return null;}
    private void capture(String name)throws Exception{
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(300);
        File directory=new File(context.getExternalFilesDir(null),"eink-qa");directory.mkdirs();Bitmap screenshot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(screenshot);try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();
    }
    @Test public void einkScreensAndVoiceSettingsPreserveTheDraft()throws Exception{
        try(ActivityScenario<MainActivity> scene=ActivityScenario.launch(MainActivity.class)){
            capture("learn");
            scene.onActivity(activity->{try{screen(activity,"Learn",session,"");MaterialCardView composer=activity.findViewById(R.id.composer_surface);assertTrue(composer.getRadius()>0);assertTrue(composer.getStrokeWidth()>0);assertNotNull(text(activity.getWindow().getDecorView(),"Confidence"));}catch(Exception error){throw new AssertionError(error);}});capture("lesson");
            scene.onActivity(activity->{try{var field=MainActivity.class.getDeclaredField("composer");field.setAccessible(true);((EditText)field.get(activity)).setText("Keep this unsent reasoning");((BottomNavigationView)activity.findViewById(R.id.bottom_navigation)).setSelectedItemId(R.id.nav_settings);assertNotNull(text(activity.getWindow().getDecorView(),"Services"));assertNotNull(text(activity.getWindow().getDecorView(),"Personalisation"));}catch(Exception error){throw new AssertionError(error);}});capture("settings");
            scene.onActivity(activity->{try{screen(activity,"Settings",session,"Appearance");}catch(Exception error){throw new AssertionError(error);}});capture("appearance");
            scene.onActivity(activity->{try{context.getSharedPreferences("settings",0).edit().putString("voiceEngine","OpenAI").commit();screen(activity,"Settings",session,"Voice typing");assertNotNull(text(activity.getWindow().getDecorView(),"Voice API key"));}catch(Exception error){throw new AssertionError(error);}});capture("voice-api");
            scene.onActivity(activity->{try{screen(activity,"Practice","","");}catch(Exception error){throw new AssertionError(error);}});capture("practice");
            scene.onActivity(activity->{try{screen(activity,"Library","","");}catch(Exception error){throw new AssertionError(error);}});capture("library");
            assertEquals("Keep this unsent reasoning",new Records(context).find("sessions",session).getString("draft"));
        }
    }
}
