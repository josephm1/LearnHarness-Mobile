package ai.learnharness.android;

import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MaterialUiDeviceTest {
    Context context;String session;boolean originalEink;
    @Before public void prepare() throws Exception {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        originalEink=context.getSharedPreferences("settings",0).getBoolean("eink",false);context.getSharedPreferences("settings",0).edit().putBoolean("eink",false).commit();
        Records records=new Records(context);session=Records.id();records.rows("sessions").put(new JSONObject().put("id",session).put("title","Material UI QA").put("phase","Explore").put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content","A test goal")).put(new JSONObject().put("role","assistant").put("content","A saved tutor explanation."))));records.save();
    }
    @After public void clean() throws Exception { Records records=new Records(context);records.remove("sessions",session);records.save();context.getSharedPreferences("settings",0).edit().putBoolean("eink",originalEink).commit(); }
    private <T extends View>T find(View root,Class<T> type){if(type.isInstance(root))return type.cast(root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){T result=find(((ViewGroup)root).getChildAt(i),type);if(result!=null)return result;}return null;}
    private TextView text(View root,String title){if(root instanceof TextView && ((TextView)root).getText().toString().equals(title))return (TextView)root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextView result=text(((ViewGroup)root).getChildAt(i),title);if(result!=null)return result;}return null;}
    private void click(View view){assertNotNull(view);while(!view.hasOnClickListeners() && view.getParent() instanceof View)view=(View)view.getParent();assertTrue(view.performClick());}
    private View description(View root,String value){if(value.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View result=description(((ViewGroup)root).getChildAt(i),value);if(result!=null)return result;}return null;}
    private int countButtons(View root){int result=root instanceof MaterialButton?1:0;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)result+=countButtons(((ViewGroup)root).getChildAt(i));return result;}
    @Test public void navigationUsesFourNativeIconDestinations(){try(ActivityScenario<MainActivity> scene=ActivityScenario.launch(MainActivity.class)){scene.onActivity(a->{BottomNavigationView nav=a.findViewById(R.id.bottom_navigation);assertNotNull(nav);assertEquals(4,nav.getMenu().size());for(int i=0;i<4;i++)assertNotNull(nav.getMenu().getItem(i).getIcon());nav.setSelectedItemId(R.id.nav_settings);assertEquals(R.id.nav_settings,((BottomNavigationView)a.findViewById(R.id.bottom_navigation)).getSelectedItemId());});}}
    @Test public void settingsStartsWithCategoriesInsteadOfCredentials(){try(ActivityScenario<MainActivity> scene=ActivityScenario.launch(MainActivity.class)){scene.onActivity(a->{((BottomNavigationView)a.findViewById(R.id.bottom_navigation)).setSelectedItemId(R.id.nav_settings);View root=a.getWindow().getDecorView();assertNotNull(text(root,"AI connection"));assertNotNull(text(root,"Appearance"));assertNotNull(text(root,"Data & backup"));assertNull(find(root,EditText.class));click(text(root,"AI connection"));assertNotNull(text(a.getWindow().getDecorView(),"Connection details"));assertNull(find(a.getWindow().getDecorView(),EditText.class));});}}
    @Test public void lessonKeepsSecondaryActionsOutOfReplyAreaAndPreservesDraft(){try(ActivityScenario<MainActivity> scene=ActivityScenario.launch(MainActivity.class)){scene.onActivity(a->{click(text(a.getWindow().getDecorView(),"Material UI QA"));View root=a.getWindow().getDecorView();assertNotNull(description(root,"Send reply"));assertEquals(View.GONE,a.findViewById(R.id.bottom_navigation).getVisibility());assertNull(text(root,"Unsure"));assertNull(text(root,"Delete session"));assertNull(text(root,"Rename session"));assertTrue(countButtons(root)<=4);TextInputEditText composer=find(root,TextInputEditText.class);assertNotNull(composer);for(android.view.ViewParent parent=composer.getParent();parent!=null;parent=parent.getParent()){assertFalse(parent instanceof ScrollView);assertFalse(parent instanceof androidx.core.widget.NestedScrollView);}composer.setText("An unsent reasoning draft");((BottomNavigationView)a.findViewById(R.id.bottom_navigation)).setSelectedItemId(R.id.nav_settings);((BottomNavigationView)a.findViewById(R.id.bottom_navigation)).setSelectedItemId(R.id.nav_learn);assertEquals("An unsent reasoning draft",find(a.getWindow().getDecorView(),TextInputEditText.class).getText().toString());MaterialToolbar bar=find(a.getWindow().getDecorView(),MaterialToolbar.class);boolean delete=false;for(int i=0;i<bar.getMenu().size();i++)if(bar.getMenu().getItem(i).getTitle().toString().equals("Delete session"))delete=true;assertTrue(delete);});}}
    @Test public void einkKeepsNativeNavigationAndMonochromeIndicator(){context.getSharedPreferences("settings",0).edit().putBoolean("eink",true).commit();try(ActivityScenario<MainActivity> scene=ActivityScenario.launch(MainActivity.class)){scene.onActivity(a->{BottomNavigationView nav=a.findViewById(R.id.bottom_navigation);assertEquals(android.graphics.Color.BLACK,nav.getItemActiveIndicatorColor().getDefaultColor());assertEquals(4,nav.getMenu().size());});}}
    @Test public void connectionHistoryDoesNotExposeCredentials(){AuthDiagnostics log=new AuthDiagnostics(new ContextWrapper(context){@Override public SharedPreferences getSharedPreferences(String n,int m){return super.getSharedPreferences("qa-log-"+n,m);}});log.event("token_exchange_failed",new OpenAiHttp.Failure(503,"subscription_sharing_usage_unavailable","","req-test","detail"));String summary=log.summary();assertTrue(summary.contains("503"));assertTrue(summary.contains("req-test"));assertFalse(summary.contains("access_token"));assertFalse(summary.contains("refresh_token"));}
    @Test public void einkReadingControlsStayVisibleWithLongText() throws Exception {
        context.getSharedPreferences("settings",0).edit().putBoolean("eink",true).commit();
        Records records=new Records(context);JSONObject lesson=records.find("sessions",session);StringBuilder longText=new StringBuilder();for(int i=0;i<35;i++)longText.append("A longer explanation with a question to check your understanding.\n\n");lesson.getJSONArray("messages").getJSONObject(1).put("content",longText.toString());records.save();
        try(ActivityScenario<MainActivity> scene=ActivityScenario.launch(MainActivity.class)) {
            scene.onActivity(a->click(text(a.getWindow().getDecorView(),"Material UI QA")));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            for(int i=0;i<4;i++){scene.onActivity(a->{View previous=description(a.getWindow().getDecorView(),"Previous screenful");assertNotNull(previous);android.graphics.Rect bounds=new android.graphics.Rect();assertTrue(previous.getGlobalVisibleRect(bounds));assertEquals(previous.getHeight(),bounds.height());if(previous.isEnabled())previous.performClick();});InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
        }
    }
    @Test public void markdownAndWhiteSurfacesRenderWithoutLiteralMarkers() throws Exception {
        Records records=new Records(context);records.find("sessions",session).getJSONArray("messages").getJSONObject(1).put("content","## A heading\n\n**Bold idea** with `code`.\n\n- First item\n- Second item");records.save();
        try(ActivityScenario<MainActivity> scene=ActivityScenario.launch(MainActivity.class)){scene.onActivity(a->{
            click(text(a.getWindow().getDecorView(),"Material UI QA"));
            TextView body=findContaining(a.getWindow().getDecorView(),"Bold idea");assertNotNull(body);assertFalse(body.getText().toString().contains("**"));assertTrue(body.getText() instanceof android.text.Spanned);
            android.text.Spanned spans=(android.text.Spanned)body.getText();assertTrue(spans.getSpans(0,spans.length(),io.noties.markwon.core.spans.StrongEmphasisSpan.class).length>0);
            assertEquals(16f,body.getTextSize()/a.getResources().getDisplayMetrics().scaledDensity,.1f);
            View nav=a.findViewById(R.id.bottom_navigation);assertEquals(android.graphics.Color.WHITE,((android.graphics.drawable.ColorDrawable)nav.getBackground()).getColor());
        });}
    }
    private TextView findContaining(View root,String part){if(root instanceof TextView && ((TextView)root).getText().toString().contains(part))return (TextView)root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextView result=findContaining(((ViewGroup)root).getChildAt(i),part);if(result!=null)return result;}return null;}
    @Test public void streamPreviewUpdatesExistingViewAndRejectsStaleCallbacks() throws Exception {
        try(ActivityScenario<MainActivity> scene=ActivityScenario.launch(MainActivity.class)){
            scene.onActivity(a->{try{
                click(text(a.getWindow().getDecorView(),"Material UI QA"));
                set(a,"busy",true);set(a,"streamSession",session);set(a,"requestGeneration",42);
                java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(a);
                set(a,"pendingStream","**Live answer**");set(a,"streamGeneration",42);
                java.lang.reflect.Field paint=MainActivity.class.getDeclaredField("paintStream");paint.setAccessible(true);((Runnable)paint.get(a)).run();
                TextView live=findContaining(a.getWindow().getDecorView(),"Live answer");assertNotNull(live);assertEquals("Live answer",live.getText().toString());
                set(a,"pendingStream","Stale answer");set(a,"streamGeneration",41);((Runnable)paint.get(a)).run();assertEquals("Live answer",live.getText().toString());
                assertEquals(2,new Records(context).find("sessions",session).getJSONArray("messages").length());
            }catch(Exception e){throw new AssertionError(e);}});
        }
    }
    private void set(Object target,String field,Object value)throws Exception{java.lang.reflect.Field f=target.getClass().getDeclaredField(field);f.setAccessible(true);f.set(target,value);}
    @Test public void confidenceSliderDefaultsToMidpointAndSurvivesNavigation(){
        try(ActivityScenario<MainActivity> scene=ActivityScenario.launch(MainActivity.class)){scene.onActivity(a->{
            click(text(a.getWindow().getDecorView(),"Material UI QA"));
            com.google.android.material.slider.Slider slider=find(a.getWindow().getDecorView(),com.google.android.material.slider.Slider.class);assertNotNull(slider);assertEquals(50f,slider.getValue(),0f);assertNotNull(text(a.getWindow().getDecorView(),"Confidence"));
            slider.setValue(80);assertNotNull(text(a.getWindow().getDecorView(),"Confidence"));
            slider.setValue(50);slider.requestFocus();slider.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));slider.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT));assertNotNull(text(a.getWindow().getDecorView(),"60%"));
            find(a.getWindow().getDecorView(),TextInputEditText.class).setText("My unfinished answer");
            ((BottomNavigationView)a.findViewById(R.id.bottom_navigation)).setSelectedItemId(R.id.nav_settings);
            ((BottomNavigationView)a.findViewById(R.id.bottom_navigation)).setSelectedItemId(R.id.nav_learn);
            assertEquals(60f,find(a.getWindow().getDecorView(),com.google.android.material.slider.Slider.class).getValue(),0f);
            assertEquals("My unfinished answer",find(a.getWindow().getDecorView(),TextInputEditText.class).getText().toString());
            assertNull(find(a.getWindow().getDecorView(),MaterialToolbar.class).getSubtitle());
        });}
    }
    @Test public void untouchedConfidenceIsOmittedAndUsedConfidenceResetsAfterSending()throws Exception{
        SharedPreferences settings=context.getSharedPreferences("settings",0);String oldProvider=settings.getString("provider","OpenAI compatible");boolean oldPlan=settings.getBoolean("useChatGpt",false);
        // Deliberately missing credentials: exercise local sending with no inference possible.
        settings.edit().putString("provider","qa-provider-without-key").putBoolean("useChatGpt",false).commit();
        try(ActivityScenario<MainActivity> scene=ActivityScenario.launch(MainActivity.class)){
            scene.onActivity(a->{click(text(a.getWindow().getDecorView(),"Material UI QA"));find(a.getWindow().getDecorView(),TextInputEditText.class).setText("An answer without confidence");click(description(a.getWindow().getDecorView(),"Send reply"));});
            Records first=new Records(context);assertEquals("An answer without confidence",first.find("sessions",session).getJSONArray("messages").getJSONObject(2).getString("content"));
            scene.onActivity(a->{try{
                set(a,"confidenceTouched",true);set(a,"confidencePercent",80);
                find(a.getWindow().getDecorView(),TextInputEditText.class).setText("An answer with confidence");click(description(a.getWindow().getDecorView(),"Send reply"));
                assertNotNull(text(a.getWindow().getDecorView(),"Confidence"));assertEquals(50f,find(a.getWindow().getDecorView(),com.google.android.material.slider.Slider.class).getValue(),0f);
            }catch(Exception e){throw new AssertionError(e);}});
            assertTrue(new Records(context).find("sessions",session).getJSONArray("messages").getJSONObject(3).getString("content").contains("Confidence: 80%"));
        }finally{settings.edit().putString("provider",oldProvider).putBoolean("useChatGpt",oldPlan).commit();}
    }
    @Test public void lessonAppBarCollapsesOnNestedScrollWhileComposerStaysPinned()throws Exception{
        Records records=new Records(context);StringBuilder text=new StringBuilder();for(int i=0;i<60;i++)text.append("A paragraph for scrolling through a long lesson.\n\n");records.find("sessions",session).getJSONArray("messages").getJSONObject(1).put("content",text.toString());records.save();
        try(ActivityScenario<MainActivity> scene=ActivityScenario.launch(MainActivity.class)){
            scene.onActivity(a->click(text(a.getWindow().getDecorView(),"Material UI QA")));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scene.onActivity(a->{
                com.google.android.material.appbar.AppBarLayout bar=a.findViewById(R.id.lesson_app_bar);androidx.core.widget.NestedScrollView reading=a.findViewById(R.id.reading_scroll);
                TextInputEditText editor=find(a.getWindow().getDecorView(),TextInputEditText.class);int[] before=new int[2];editor.getLocationOnScreen(before);
                assertTrue(bar.getTotalScrollRange()>0);assertTrue(reading.startNestedScroll(androidx.core.view.ViewCompat.SCROLL_AXIS_VERTICAL,androidx.core.view.ViewCompat.TYPE_TOUCH));
                int[] consumed=new int[2];reading.dispatchNestedPreScroll(0,bar.getTotalScrollRange(),consumed,null,androidx.core.view.ViewCompat.TYPE_TOUCH);
                assertTrue(consumed[1]>0);assertEquals(-bar.getTotalScrollRange(),bar.getTop());int[] after=new int[2];editor.getLocationOnScreen(after);assertEquals(before[1],after[1]);
                reading.dispatchNestedPreScroll(0,-bar.getTotalScrollRange(),new int[2],null,androidx.core.view.ViewCompat.TYPE_TOUCH);assertEquals(0,bar.getTop());reading.stopNestedScroll(androidx.core.view.ViewCompat.TYPE_TOUCH);
                com.google.android.material.card.MaterialCardView surface=a.findViewById(R.id.composer_surface);assertNotNull(surface);assertTrue(surface.getStrokeWidth()>0);assertNotNull(description(a.getWindow().getDecorView(),"Attach book or document"));
                com.google.android.material.textfield.TextInputLayout field=find(a.getWindow().getDecorView(),com.google.android.material.textfield.TextInputLayout.class);assertEquals(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_NONE,field.getBoxBackgroundMode());
            });
        }
    }

    @Test public void structuredQuestionGradesLocallyWithoutDefaultConfidence()throws Exception{
        SharedPreferences settings=context.getSharedPreferences("settings",0);String oldProvider=settings.getString("provider","OpenAI compatible");boolean oldPlan=settings.getBoolean("useChatGpt",false);settings.edit().putString("provider","qa-provider-without-key").putBoolean("useChatGpt",false).commit();
        Records store=new Records(context);JSONObject lesson=store.find("sessions",session);JSONObject call=ToolProtocol.call("qa_question","probe","{}");JSONObject question=new JSONObject("{id:'qa_question',kind:'quiz',tool:'probe',question:'What is two plus two?',mode:'single-select',options:[{label:'Four',value:'four'},{label:'Five',value:'five'}],correctIndices:[1],explanation:'Two pairs contain four objects.'}").put("call",call);
        lesson.put("pendingQuestion",question).put("agentHistory",new JSONArray().put(new JSONObject().put("role","user").put("content","Arithmetic")).put(new JSONObject().put("role","assistant").put("content","").put("tool_calls",new JSONArray().put(call))));store.save();
        try(ActivityScenario<MainActivity> scene=ActivityScenario.launch(MainActivity.class)){
            scene.onActivity(a->{click(text(a.getWindow().getDecorView(),"Material UI QA"));assertNotNull(text(a.getWindow().getDecorView(),"What is two plus two?"));((CompoundButton)text(a.getWindow().getDecorView(),"Four")).performClick();click(description(a.getWindow().getDecorView(),"Send reply"));});
            JSONObject updated=null;for(int i=0;i<50;i++){updated=new Records(context).find("sessions",session);if(!updated.has("pendingQuestion"))break;Thread.sleep(100);}
            assertNotNull(updated);assertFalse("Question was not committed",updated.has("pendingQuestion"));JSONObject answer=updated.getJSONArray("questionAnswers").getJSONObject(0).getJSONObject("answer");assertTrue(answer.getBoolean("correct"));assertFalse(answer.has("confidence"));assertEquals("tool",updated.getJSONArray("agentHistory").getJSONObject(2).getString("role"));assertFalse(updated.getJSONArray("agentHistory").getJSONObject(2).getString("content").contains("explanation"));
        }finally{settings.edit().putString("provider",oldProvider).putBoolean("useChatGpt",oldPlan).commit();}
    }
    @Test public void nativeMathRendersInlineAndDisplayWithoutChangingStoredText()throws Exception{
        String source="Equation $x^2$ is positive.\n\n$$\n\\frac{1}{2} + \\frac{1}{2} = 1\n$$";Records records=new Records(context);records.find("sessions",session).getJSONArray("messages").getJSONObject(1).put("content",source);records.save();
        try(ActivityScenario<MainActivity> scene=ActivityScenario.launch(MainActivity.class)){scene.onActivity(a->click(text(a.getWindow().getDecorView(),"Material UI QA")));boolean[] ready={false};for(int attempt=0;attempt<80 && !ready[0];attempt++){scene.onActivity(a->{TextView equation=findContaining(a.getWindow().getDecorView(),"Equation");assertNotNull(equation);android.text.Spanned content=(android.text.Spanned)equation.getText();io.noties.markwon.image.AsyncDrawableSpan[] spans=content.getSpans(0,content.length(),io.noties.markwon.image.AsyncDrawableSpan.class);assertEquals(2,spans.length);ready[0]=true;for(var span:spans)ready[0]&=span.getDrawable().hasResult();});if(!ready[0])Thread.sleep(100);}assertTrue("LaTeX drawables did not render",ready[0]);assertEquals(source,new Records(context).find("sessions",session).getJSONArray("messages").getJSONObject(1).getString("content"));}
    }
}
