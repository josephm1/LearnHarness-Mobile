package ai.learnharness.android;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.appbar.AppBarLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends AppCompatActivity {
    private final BroadcastReceiver jobUpdates=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){if(records!=null && !busy)try{boolean delivered=deliverJobs();if(delivered){captureDraft();render();JSONObject lesson=records.find("sessions",selectedSession);if(lesson!=null && screen.equals("Learn") && (lesson.optJSONObject("pendingQuestion")==null || lesson.getJSONObject("pendingQuestion").optString("kind").equals("problem")))tutor(lesson);}}catch(Exception error){error(error);}}};
    private Records records;
    private SecretStore secrets;
    private ChatGptAccounts chatGpt;
    private final OpenAiHttp chatGptHttp = new OpenAiHttp();
    private android.content.SharedPreferences prefs;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ProviderClient provider = new ProviderClient();
    private LinearLayout root, content, composerHost, preferenceGroup;
    private View lastPreference;
    private int preferenceCount;
    private View nav;
    private MaterialToolbar toolbar;
    private String settingsPage="";
    private int confidencePercent=50;
    private String documentTarget="";
    private Future<?> documentTask;
    private boolean confidenceTouched;
    private final Map<Integer,Runnable> toolbarActions=new HashMap<>();
    private TextView status,liveReply;
    private io.noties.markwon.Markwon markdown;
    private final java.util.concurrent.ExecutorService mathWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private NestedScrollView lessonScroll;
    private String streamText="",streamSession="",streamPractice="";
    private int streamQuestion;
    private final Handler streamHandler=new Handler(Looper.getMainLooper());
    private volatile String pendingStream="";
    private volatile boolean streamPosted;
    private volatile int streamGeneration;
    private String screen = "Learn", selectedSession = "", selectedPractice = "";
    private androidx.appcompat.app.AlertDialog creationDialog;
    private com.google.android.material.textfield.TextInputEditText creationGoal;
    private String creationKind="";
    private int questionIndex = 0;
    private boolean eink, busy, readingExpanded;
    private String renderedLesson="",followLatestSession="";
    private final Map<String,Integer> readingPositions=new HashMap<>();
    private final Set<String> readingEnds=new HashSet<>();
    private volatile int requestGeneration = 0;
    private String operation = "", draft = "";
    private int ink, accent, paper, fontSize, surface, subtle;
    private EditText composer;
    private AndroidVoiceTyping voiceTyping;
    private ApiVoiceTyping apiVoiceTyping;
    private androidx.appcompat.app.AlertDialog voiceDialog;
    private final androidx.activity.result.ActivityResultLauncher<String> voicePermission=registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),granted->{if(granted)dictate();else Toast.makeText(this,"Microphone permission is needed for voice typing. Your draft is unchanged.",Toast.LENGTH_LONG).show();});
    private static final int IMPORT_NOTES = 11, RESTORE = 12, EXPORT = 13, ADD_DOCUMENT=15, IMPORT_TEMPLATE=16, OPEN_TEMPLATE=17, OPEN_GRAPH=18, ADD_TREE=19;
    private static final String[] PROVIDERS = {"OpenAI compatible", "Anthropic", "Gemini", "OpenRouter", "DeepSeek"};


    private final Runnable paintStream=()->{
        streamPosted=false;
        if(isDestroyed() || !busy || streamGeneration!=requestGeneration)return;
        streamText=pendingStream;
        if(liveReply!=null && (screen.equals("Learn") && selectedSession.equals(streamSession) || screen.equals("Practice") && selectedPractice.equals(streamPractice) && questionIndex==streamQuestion)){
            boolean follow=lessonScroll!=null && (!followLatestSession.isEmpty() && followLatestSession.equals(streamSession) || !lessonScroll.canScrollVertically(1));
            markdown.setMarkdown(liveReply,streamText);
            if(follow){NestedScrollView target=lessonScroll;target.post(()->target.fullScroll(View.FOCUS_DOWN));}
        }
    };
    @Override public void onCreate(Bundle state) {
        if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("eink", BuildConfig.DEFAULT_EINK)) setTheme(R.style.Theme_LearnHarness_Eink);
        else DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(state);
        androidx.core.content.ContextCompat.registerReceiver(this,jobUpdates,new IntentFilter(JobService.CHANGED),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        getTheme().applyStyle(R.style.WhiteSurfaces,true);
        prefs=getSharedPreferences("settings",MODE_PRIVATE);
        markdown=io.noties.markwon.Markwon.builder(this)
            .usePlugin(io.noties.markwon.inlineparser.MarkwonInlineParserPlugin.create())
            .usePlugin(io.noties.markwon.ext.latex.JLatexMathPlugin.create(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,prefs.getInt("fontSize",16),getResources().getDisplayMetrics()),builder->builder.inlinesEnabled(true).executorService(mathWorker)))
            .usePlugin(new io.noties.markwon.AbstractMarkwonPlugin(){@Override public String processMarkdown(String text){return MarkdownMath.normalize(text);}})
            .usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(this))
            .usePlugin(io.noties.markwon.ext.strikethrough.StrikethroughPlugin.create())
            .usePlugin(new io.noties.markwon.AbstractMarkwonPlugin(){
                @Override public void configureTheme(io.noties.markwon.core.MarkwonTheme.Builder builder){builder.codeBackgroundColor(Color.WHITE).codeBlockBackgroundColor(Color.WHITE).headingTextSizeMultipliers(new float[]{1.35f,1.25f,1.15f,1.1f,1f,1f});}
                @Override public void configureConfiguration(io.noties.markwon.MarkwonConfiguration.Builder builder){builder.linkResolver((view,link)->{android.net.Uri uri=android.net.Uri.parse(link);if("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(ActivityNotFoundException ignored){}});}
            }).build();
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        prefs = getSharedPreferences("settings", MODE_PRIVATE); secrets = new SecretStore(this);
        try { chatGpt = ChatGptAccounts.get(this); } catch(Exception e) { Toast.makeText(this,"ChatGPT credentials could not be opened. API providers remain available.",Toast.LENGTH_LONG).show(); }
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                captureDraft();
                if(readingExpanded){setReadingExpanded(false);return;}
                if(screen.equals("Settings") && !settingsPage.isEmpty())settingsPage="";
                else if (screen.equals("Learn") && !selectedSession.isEmpty()){JSONObject current=records.find("sessions",selectedSession);if(current!=null && current.optString("kind").equals("practice"))screen="Practice";selectedSession="";}
                else if (screen.equals("Practice") && !selectedPractice.isEmpty()) selectedPractice = "";
                else if (!screen.equals("Learn")) screen = "Learn";
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); return; }
                render();
            }
        });
        if (state != null) {
            followLatestSession=state.getString("followLatestSession","");readingExpanded=state.getBoolean("readingExpanded",false);screen = state.getString("screen", "Learn"); selectedSession = state.getString("session", "");
            selectedPractice = state.getString("practice", ""); draft = state.getString("draft", "");
            if(!selectedSession.isEmpty()){readingPositions.put(selectedSession,state.getInt("readingY",0));if(state.getBoolean("readingEnd",true))readingEnds.add(selectedSession);}
            documentTarget=state.getString("documentTarget","");settingsPage=state.getString("settingsPage","");confidencePercent=state.getInt("confidencePercent",50);confidenceTouched=state.getBoolean("confidenceTouched",false);
            questionIndex = state.getInt("question", 0);
        }
        if(getIntent().getData()!=null && BuildConfig.SIGN_IN_SCHEME.equals(getIntent().getData().getScheme()) && "signin-complete".equals(getIntent().getData().getHost())){screen="Settings";settingsPage="AI connection";getIntent().setData(null);}
        try { records = new Records(this); render();boolean incomingShare=receiveSharedContent(getIntent());if(!incomingShare && state!=null && !state.getString("creationKind","").isEmpty()){String restoredKind=state.getString("creationKind"),restoredGoal=state.getString("creationGoal","");new Handler(Looper.getMainLooper()).post(()->{if(!isFinishing() && !isDestroyed()){if(restoredKind.equals("practice"))showSessionCreator(true,restoredGoal);else{newSession();JSONObject restoredSession=records.find("sessions",selectedSession);if(restoredSession!=null)try{draft=restoredGoal;restoredSession.put("draft",draft);save();render();}catch(Exception error){error(error);}}}});} }
        catch (Exception e) {
            TextView text = new TextView(this); text.setPadding(28, 60, 28, 28);
            text.setText("Your learning file could not be read. It has been preserved. Close the app and recover learning.json from app storage before continuing.\n\n" + e.getClass().getSimpleName()); setContentView(text);
        }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        captureDraft();out.putString("creationKind",creationKind);out.putString("creationGoal",creationGoal==null?"":creationGoal.getText().toString());super.onSaveInstanceState(out);
        if(lessonScroll!=null && !renderedLesson.isEmpty()){out.putInt("readingY",lessonScroll.getScrollY());out.putBoolean("readingEnd",!lessonScroll.canScrollVertically(1));}
        out.putString("followLatestSession",followLatestSession);out.putBoolean("readingExpanded",readingExpanded);out.putString("screen", screen); out.putString("session", selectedSession); out.putString("practice", selectedPractice);
        out.putString("documentTarget",documentTarget);out.putString("settingsPage",settingsPage);out.putInt("confidencePercent",confidencePercent);out.putBoolean("confidenceTouched",confidenceTouched);
        out.putString("draft", draft); out.putInt("question", questionIndex);
    }
    @Override protected void onDestroy() {cancelVoiceTyping(); if(creationDialog!=null)creationDialog.dismiss();unregisterReceiver(jobUpdates);requestGeneration++; provider.cancel(); chatGptHttp.cancel(); worker.shutdownNow();mathWorker.shutdownNow();streamHandler.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) { super.onConfigurationChanged(config); captureDraft(); render(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void captureDraft() {
        if (composer != null) {
            draft = composer.getText().toString();
            if (records != null) {
                JSONObject session = records.find("sessions", selectedSession);
                if (session != null) try { session.put("draft", draft); } catch(JSONException ignored) {}
            }
        }
    }
    private void render() {
        cancelVoiceTyping();
        if(lessonScroll!=null && !renderedLesson.isEmpty()){readingPositions.put(renderedLesson,lessonScroll.getScrollY());if(!lessonScroll.canScrollVertically(1))readingEnds.add(renderedLesson);else readingEnds.remove(renderedLesson);}
        composer=null;nav=null;liveReply=null;lessonScroll=null;preferenceGroup=null;lastPreference=null;preferenceCount=0;toolbarActions.clear();
        eink=prefs.getBoolean("eink",BuildConfig.DEFAULT_EINK);fontSize=prefs.getInt("fontSize",16);
        ink=eink?Color.BLACK:MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnSurface,Color.BLACK);
        paper=Color.WHITE;
        accent=eink?Color.BLACK:MaterialColors.getColor(this,androidx.appcompat.R.attr.colorPrimary,Color.rgb(49,94,83));
        surface=Color.WHITE;
        subtle=eink?Color.BLACK:MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnSurfaceVariant,Color.DKGRAY);
        root=column();root.setBackgroundColor(paper);setContentView(root);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets)->{
            var bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            var keyboard=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,keyboard.bottom));
            if(nav!=null)nav.setVisibility(keyboard.bottom>bars.bottom || screen.equals("Learn") && !selectedSession.isEmpty()?View.GONE:View.VISIBLE);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });
        if(Build.VERSION.SDK_INT>=27)getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        toolbar=new MaterialToolbar(this);toolbar.setTitleTextAppearance(this,R.style.CompactToolbarTitle);toolbar.setSubtitleTextAppearance(this,R.style.CompactToolbarSubtitle);toolbar.setTitleTextColor(ink);toolbar.setSubtitleTextColor(subtle);toolbar.setBackgroundColor(paper);
        JSONObject session=records.find("sessions",selectedSession),practice=records.find("practice",selectedPractice);
        boolean lesson=screen.equals("Learn") && session!=null;renderedLesson=lesson?selectedSession:"";if(lesson)prefs.edit().putLong("recent."+selectedSession,System.currentTimeMillis()).apply();if(!lesson)readingExpanded=false;
        boolean subpage=lesson || screen.equals("Practice")&&practice!=null || screen.equals("Settings")&&!settingsPage.isEmpty();
        if(eink){toolbar.setTitleTextAppearance(this,screen.equals("Settings")?R.style.EinkSettingsTitle:R.style.EinkToolbarTitle);}
        toolbar.setTitle(lesson?session.optString("title"):screen.equals("Practice")&&practice!=null?practice.optString("title"):screen.equals("Settings")&&!settingsPage.isEmpty()?settingsPage:screen.equals("Learn")?"LearnHarness":screen);
        if(subpage){toolbar.setNavigationIcon(R.drawable.ic_back);toolbar.setNavigationIconTint(ink);toolbar.setNavigationContentDescription("Back");toolbar.setNavigationOnClickListener(v->getOnBackPressedDispatcher().onBackPressed());}
        toolbar.setOverflowIcon(androidx.appcompat.content.res.AppCompatResources.getDrawable(this,R.drawable.ic_more));
        if(toolbar.getOverflowIcon()!=null)toolbar.getOverflowIcon().setTint(ink);
        toolbar.setOnMenuItemClickListener(item->{Runnable action=toolbarActions.get(item.getItemId());if(action!=null){captureDraft();action.run();return true;}return false;});
        CoordinatorLayout readingArea=new CoordinatorLayout(this);readingArea.setBackgroundColor(Color.WHITE);root.addView(readingArea,new LinearLayout.LayoutParams(-1,0,1));
        AppBarLayout appBar=new AppBarLayout(this);appBar.setId(R.id.lesson_app_bar);appBar.setBackgroundColor(Color.WHITE);appBar.setElevation(0);appBar.setStateListAnimator(null);appBar.setLiftOnScroll(false);
        LinearLayout header=column();header.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(56)));
        if(busy){status=label(operation,13,false);status.setPadding(dp(20),0,dp(20),dp(4));header.addView(status);menuAction("Cancel request",()->{requestGeneration++;provider.cancel();chatGptHttp.cancel();if(documentTask!=null)documentTask.cancel(true);busy=false;streamText="";render();});}
        AppBarLayout.LayoutParams headerParams=new AppBarLayout.LayoutParams(-1,-2);headerParams.setScrollFlags(lesson?AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL|AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS|(eink?0:AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP):0);appBar.addView(header,headerParams);
        readingArea.addView(appBar,new CoordinatorLayout.LayoutParams(-1,-2));
        NestedScrollView scroll=new NestedScrollView(this);scroll.setId(R.id.reading_scroll);lessonScroll=scroll;scroll.setFillViewport(true);scroll.setSmoothScrollingEnabled(!eink);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        CoordinatorLayout.LayoutParams scrollParams=new CoordinatorLayout.LayoutParams(-1,-1);scrollParams.setBehavior(new AppBarLayout.ScrollingViewBehavior());
        content=column();content.setPadding(dp(eink?16:12),dp(eink?12:4),dp(eink?16:12),dp(eink?16:8));scroll.addView(content);readingArea.addView(scroll,0,scrollParams);
        composerHost=column();
        if(lesson){
            composerHost.setPadding(dp(8),dp(8),dp(8),dp(4));composerHost.setBackgroundColor(Color.WHITE);root.addView(composerHost);
        }
        BottomNavigationView navigation=new BottomNavigationView(this);navigation.setBackgroundColor(Color.WHITE);navigation.setElevation(0);navigation.setItemIconSize(dp(22));navigation.setItemTextAppearanceActive(R.style.CompactNavigationLabel);navigation.setItemTextAppearanceInactive(R.style.CompactNavigationLabel);navigation.setId(R.id.bottom_navigation);navigation.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);navigation.setItemHorizontalTranslationEnabled(false);
        String[] destinations={"Learn","Practice","Library","Settings"};int[] destinationIds={R.id.nav_learn,R.id.nav_practice,R.id.nav_library,R.id.nav_settings};int[] icons={R.drawable.ic_learn,R.drawable.ic_practice,R.drawable.ic_library,R.drawable.ic_settings};
        for(int i=0;i<destinations.length;i++){navigation.getMenu().add(Menu.NONE,destinationIds[i],i,destinations[i]).setIcon(icons[i]);if(screen.equals(destinations[i]))navigation.setSelectedItemId(destinationIds[i]);}
        if(eink){navigation.setBackgroundColor(paper);navigation.setElevation(0);navigation.setItemActiveIndicatorColor(ColorStateList.valueOf(Color.BLACK));navigation.setItemActiveIndicatorShapeAppearance(ShapeAppearanceModel.builder().setAllCornerSizes(dp(24)).build());navigation.setItemIconTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Color.WHITE,Color.BLACK}));navigation.setItemTextColor(ColorStateList.valueOf(Color.BLACK));navigation.setItemRippleColor(ColorStateList.valueOf(Color.TRANSPARENT));}
        navigation.setOnItemSelectedListener(item->{captureDraft();save();screen=destinations[item.getOrder()];settingsPage="";render();return true;});
        navigation.setOnItemReselectedListener(item->{captureDraft();if(screen.equals("Learn"))selectedSession="";if(screen.equals("Practice"))selectedPractice="";settingsPage="";render();});
        nav=navigation;root.addView(navigation,new LinearLayout.LayoutParams(-1,-2));if(lesson)navigation.setVisibility(View.GONE);
        try{switch(screen){case "Learn":learn();break;case "Practice":practice();break;case "Library":library();break;default:settings();}}catch(Exception e){error(e);}
        if(eink && lesson)lessonReadingControls();
        if(eink && !lesson){
            LinearLayout turns=row();MaterialButton previous=button("↑",()->stepReading(scroll,-1));MaterialButton next=button("↓",()->stepReading(scroll,1));
            previous.setContentDescription("Previous screenful");next.setContentDescription("Next screenful");previous.setTextSize(24);next.setTextSize(24);previous.setStrokeWidth(0);next.setStrokeWidth(0);
            turns.addView(previous,new LinearLayout.LayoutParams(0,dp(48),1));turns.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(turns,root.indexOfChild(navigation),new LinearLayout.LayoutParams(-1,dp(48)));
            Runnable availability=()->{boolean canUp=scroll.canScrollVertically(-1),canDown=scroll.canScrollVertically(1);previous.setEnabled(canUp);next.setEnabled(canDown);turns.setVisibility(canUp||canDown?View.VISIBLE:View.GONE);};
            scroll.setOnScrollChangeListener((View.OnScrollChangeListener)(view,x,y,oldX,oldY)->availability.run());scroll.post(availability);
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root);
    }
    private void menuAction(String title,Runnable action){int id=9000+toolbarActions.size();toolbarActions.put(id,action);toolbar.getMenu().add(Menu.NONE,id,Menu.NONE,title).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);}
    private void preference(String title,String description,int icon,Runnable action){
        LinearLayout item=row();item.setPadding(dp(4),dp(12),dp(4),dp(12));item.setMinimumHeight(dp(72));
        if(icon!=0){ImageView image=new ImageView(this);image.setImageResource(icon);image.setImageTintList(ColorStateList.valueOf(subtle));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(24),dp(24));p.setMargins(0,0,dp(20),0);item.addView(image,p);}
        LinearLayout copy=column();TextView headline=label(title,16,!eink);if(eink){headline.setFontVariationSettings("'wght' 500");headline.setPadding(0,0,0,0);}copy.addView(headline);if(!description.isEmpty()){TextView detail=label(description,14,false);detail.setTextColor(subtle);if(eink)detail.setPadding(0,dp(3),0,0);copy.addView(detail);}item.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        ImageView chevron=new ImageView(this);chevron.setImageResource(R.drawable.ic_chevron);chevron.setImageTintList(ColorStateList.valueOf(subtle));if(!eink)item.addView(chevron,new LinearLayout.LayoutParams(dp(20),dp(20)));
        item.setContentDescription(title+". "+description);item.setFocusable(true);item.setClickable(true);item.setOnClickListener(v->action.run());
        if(eink){item.setPadding(dp(16),dp(14),dp(16),dp(14));item.setBackground(preferenceShape(true,true));}else{android.util.TypedValue value=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackground,value,true);item.setBackgroundResource(value.resourceId);}
        LinearLayout.LayoutParams itemParams=new LinearLayout.LayoutParams(-1,-2);
        if(eink && preferenceGroup!=null){
            if(lastPreference!=null)lastPreference.setBackground(preferenceShape(preferenceCount==1,false));
            item.setBackground(preferenceShape(preferenceCount==0,true));itemParams.setMargins(0,0,0,dp(2));
            preferenceGroup.addView(item,itemParams);lastPreference=item;preferenceCount++;
        }else{if(eink)itemParams.setMargins(0,0,0,dp(10));content.addView(item,itemParams);}
    }
    private GradientDrawable preferenceShape(boolean first,boolean last){
        GradientDrawable shape=new GradientDrawable();shape.setColor(paper);shape.setStroke(dp(1),ink);
        float top=dp(first?24:5),bottom=dp(last?24:5);
        shape.setCornerRadii(new float[]{top,top,top,top,bottom,bottom,bottom,bottom});return shape;
    }
    private void settingsGroup(String title){
        if(!eink)return;
        TextView heading=label(title,13,true);heading.setPadding(dp(16),dp(preferenceGroup==null?12:24),dp(16),dp(12));content.addView(heading);
        preferenceGroup=column();lastPreference=null;preferenceCount=0;content.addView(preferenceGroup,new LinearLayout.LayoutParams(-1,-2));
    }
    private MaterialButton primaryButton(String title,Runnable action){MaterialButton b=button(title,action);b.setBackgroundTintList(ColorStateList.valueOf(eink?paper:accent));int foreground=eink?Color.BLACK:MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnPrimary,Color.WHITE);b.setTextColor(foreground);b.setIconTint(ColorStateList.valueOf(foreground));return b;}
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private GradientDrawable outline(int width){GradientDrawable d=new GradientDrawable();d.setColor(eink?paper:surface);d.setStroke(dp(eink?width:0),eink?ink:subtle);d.setCornerRadius(dp(eink?20:8));return d;}
    private TextView richLabel(String text,int size,boolean strong){TextView view=label("",size,strong);markdown.setMarkdown(view,text);return view;}
    private TextView label(String text,int size,boolean strong){TextView view=new com.google.android.material.textview.MaterialTextView(this);view.setText(text);view.setTextSize(size);view.setTextColor(ink);view.setLineSpacing(dp(3),1.06f);view.setPadding(0,dp(4),0,dp(4));if(eink){view.setTypeface(getResources().getFont(R.font.eink_outfit),strong?Typeface.BOLD:Typeface.NORMAL);view.setLineSpacing(0,1.45f);}else if(strong)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return view;}
    private MaterialButton button(String title,Runnable action){MaterialButton b=new MaterialButton(this);b.setText(title);b.setTextSize(14);b.setAllCaps(false);b.setMinHeight(dp(48));b.setTextColor(accent);b.setIconTint(ColorStateList.valueOf(accent));b.setBackgroundTintList(ColorStateList.valueOf(eink?paper:Color.TRANSPARENT));b.setStrokeWidth(eink?dp(1):0);b.setStrokeColor(ColorStateList.valueOf(accent));b.setElevation(0);if(eink){b.setCornerRadius(dp(24));b.setTypeface(getResources().getFont(R.font.eink_outfit),Typeface.BOLD);b.setStateListAnimator(null);b.setRippleColor(ColorStateList.valueOf(Color.TRANSPARENT));}b.setOnClickListener(v->action.run());return b;}
    private void styleComposerIcon(MaterialButton button,boolean outlined){
        if(!eink)return;
        button.setInsetTop(0);button.setInsetBottom(0);button.setPadding(dp(12),0,dp(12),0);
        button.setIconSize(dp(24));button.setCornerRadius(dp(24));button.setStrokeWidth(outlined?dp(1):0);
    }
    private void action(String title,Runnable action){if(screen.equals("Settings"))preference(title,"",0,action);else content.addView(button(title,action));}
    private LinearLayout card(String title,CharSequence body){
        MaterialCardView frame=new MaterialCardView(this);frame.setCardBackgroundColor(surface);frame.setCardElevation(0);frame.setRadius(eink?0:dp(8));frame.setStrokeWidth(0);frame.setStrokeColor(ink);
        LinearLayout box=column();box.setPadding(dp(eink?0:8),dp(eink?8:6),dp(eink?0:8),dp(eink?12:6));frame.addView(box);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(6));content.addView(frame,p);
        if(!title.isEmpty())box.addView(label(title,eink?16:14,true));if(body.length()>0){TextView copy=label("",fontSize,false);markdown.setParsedMarkdown(copy,body instanceof android.text.Spanned?(android.text.Spanned)body:markdown.toMarkdown(body.toString()));copy.setTextIsSelectable(true);copy.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());box.addView(copy);}return box;
    }
    private EditText input(String hint, String value, boolean multiline) {
        EditText v = new EditText(this); v.setTextColor(ink); v.setHintTextColor(eink ? ink : Color.DKGRAY); v.setTextSize(fontSize);
        v.setHint(hint); v.setText(value); v.setPadding(dp(12), dp(10), dp(12), dp(10)); v.setBackground(outline(1));
        v.setInputType(InputType.TYPE_CLASS_TEXT | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES : 0));
        v.setMinLines(multiline ? 3 : 1); v.setGravity(Gravity.TOP); return v;
    }
    private ArrayAdapter<String> spinnerAdapter(String[] choices) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, choices) {
            private View style(View view) { if (view instanceof TextView) { ((TextView)view).setTextColor(ink); view.setBackgroundColor(paper); } return view; }
            @Override public View getView(int p, View v, android.view.ViewGroup group) { return style(super.getView(p,v,group)); }
            @Override public View getDropDownView(int p, View v, android.view.ViewGroup group) { return style(super.getDropDownView(p,v,group)); }
        };
    }
    private com.google.android.material.dialog.MaterialAlertDialogBuilder materialDialog(){com.google.android.material.dialog.MaterialAlertDialogBuilder builder=new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);if(eink)builder.setBackground(outline(1));else{GradientDrawable background=new GradientDrawable();background.setColor(Color.WHITE);background.setCornerRadius(dp(28));builder.setBackground(background);}return builder;}
    private void error(Exception e) {
        androidx.appcompat.app.AlertDialog.Builder dialog=materialDialog().setTitle("Could not complete action")
            .setMessage(e instanceof OpenAiHttp.Failure?((OpenAiHttp.Failure)e).diagnostic():e instanceof JSONException?"The response or file has an unsupported structure. Your existing records have been kept.":(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage())).setPositiveButton("OK",null);
        if(prefs!=null && prefs.getBoolean("useChatGpt",false))dialog.setNeutralButton("Manage usage",(d,w)->openUsage());
        dialog.show();
    }
    private boolean save() { try { records.save(); return true; } catch (Exception e) { error(e); return false; } }
    private void confirm(String message, Runnable action) { materialDialog().setTitle("Please confirm").setMessage(message).setNegativeButton("Cancel", null).setPositiveButton("Continue", (d,w) -> action.run()).show(); }
    private void learn() throws Exception {
        JSONObject session = records.find("sessions", selectedSession);
        if (session == null) {
            content.setPadding(dp(16),dp(12),dp(16),dp(16));TextView heading=label("Start with a question",22,false);androidx.core.widget.TextViewCompat.setTextAppearance(heading,com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall);content.addView(heading);
            TextView introduction=label("Choose something you want to understand.",14,false);introduction.setTextColor(subtle);content.addView(introduction);
            MaterialButton create=primaryButton("New learning session",this::newSession);create.setIconResource(R.drawable.ic_add);create.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);create.setEnabled(!busy);LinearLayout.LayoutParams actionParams=new LinearLayout.LayoutParams(-1,dp(56));actionParams.setMargins(0,dp(12),0,dp(20));content.addView(create,actionParams);
            JSONArray sessions=records.rows("sessions");int lessons=0;for(int i=0;i<sessions.length();i++)if(visibleLesson(sessions.getJSONObject(i)))lessons++;
            TextView section=label("Lessons · "+lessons,14,true);section.setPadding(0,dp(4),0,dp(12));content.addView(section);if(lessons==0){TextView empty=label("Your saved lessons will appear here.",14,false);empty.setTextColor(subtle);content.addView(empty);}
            for(int i=sessions.length()-1;i>=0;i--){JSONObject saved=sessions.getJSONObject(i);if(!visibleLesson(saved))continue;lessonListEntry(saved);}
            return;
        }

        JSONArray messages = session.getJSONArray("messages");
        if(messages.length()==0)card("Tutor","What do you want to learn?");
        else {
            for (int i = 0; i < messages.length(); i++) {
                JSONObject message=messages.getJSONObject(i);
                if(eink)einkMessage(message);else card(message.getString("role").equals("user")?"Your thinking":"Tutor",markdown.toMarkdown(message.getString("content")));
            }
        }
        if(busy && selectedSession.equals(streamSession)){
            LinearLayout streaming=card("Tutor · writing…","");liveReply=label("",fontSize,false);markdown.setMarkdown(liveReply,streamText.isEmpty()?"Waiting for the first words…":streamText);streaming.addView(liveReply);
        }
        if(messages.length()>0 && !busy && session.optJSONObject("pendingQuestion")==null && !sessionConnectionReady(session)){
            LinearLayout setup=card("Connect your tutor","Your messages are saved. Connect ChatGPT or choose an API model to continue.");setup.addView(primaryButton("Set up AI connection",()->{captureDraft();screen="Settings";settingsPage="AI connection";render();}));
        }
        if(session.optJSONObject("pendingQuestion")!=null && session.optJSONObject("pendingQuestion").optString("kind").equals("source"))renderSourceRequest(session);
        else if(session.optJSONObject("pendingQuestion")!=null && !session.optString("kind").equals("practice"))renderQuestion(session);
        if(session.optString("kind").equals("practice")){
            if(session.optJSONObject("pendingQuestion")!=null && !session.getJSONObject("pendingQuestion").optString("kind").equals("source"))menuAction("Dismiss this problem",()->dismissPractice(session));
            else if(messages.length()>1)menuAction("Next problem",()->{try{sendTurn(session,practiceMarker("next"));}catch(Exception error){error(error);}});
        }
        if(session.optJSONObject("planTree")!=null)menuAction("Learning plan",()->showPlan(session));
        draft=session.optString("draft",draft);
        MaterialCardView composerSurface=new MaterialCardView(this);composerSurface.setId(R.id.composer_surface);composerSurface.setCardBackgroundColor(Color.WHITE);composerSurface.setRadius(dp(eink?28:24));composerSurface.setStrokeWidth(dp(1));composerSurface.setStrokeColor(eink?ink:MaterialColors.getColor(this,com.google.android.material.R.attr.colorOutline,Color.DKGRAY));composerSurface.setCardElevation(0);composerSurface.setContentPadding(dp(8),dp(4),dp(8),dp(4));
        LinearLayout panel=column();composerSurface.addView(panel);composerHost.addView(composerSurface,new LinearLayout.LayoutParams(-1,-2));
        if(!session.optString("approvedExcerpts").isEmpty()){TextView ready=label("Approved passages ready · tap to remove",12,false);ready.setTextColor(accent);ready.setOnClickListener(v->{session.remove("approvedExcerpts");save();captureDraft();render();});panel.addView(ready);}
        TextInputLayout field=new TextInputLayout(this);field.setHintEnabled(false);field.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_NONE);
        composer=new TextInputEditText(field.getContext());composer.setHint(session.optJSONObject("pendingQuestion")==null?"Message…":session.getJSONObject("pendingQuestion").optBoolean("needsReveal") && !session.getJSONObject("pendingQuestion").optBoolean("revealed")?"Thoughts while reading…":session.getJSONObject("pendingQuestion").optString("kind").equals("quiz")?"Reasoning (optional)…":"Your thinking…");composer.setText(draft);composer.setTextSize(fontSize);if(eink)composer.setTypeface(getResources().getFont(R.font.eink_outfit));composer.setTextColor(ink);composer.setHintTextColor(eink?Color.BLACK:subtle);composer.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);composer.setMinLines(1);composer.setMaxLines(4);composer.setMinHeight(dp(48));composer.setPadding(dp(12),dp(8),dp(12),dp(8));composer.setBackground(null);field.addView(composer,new LinearLayout.LayoutParams(-1,-2));panel.addView(field,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout controls=row();controls.setPadding(0,dp(4),0,dp(4));
        MaterialButton attach=button("",()->attachmentMenu(session));attach.setIconResource(R.drawable.ic_attach);attach.setContentDescription("Attach book or document");androidx.core.view.ViewCompat.setStateDescription(attach,(session.optJSONArray("documents")==null?0:session.optJSONArray("documents").length())+" linked; permission required before use");styleComposerIcon(attach,false);attach.setMinWidth(0);attach.setIconPadding(0);attach.setEnabled(!busy);controls.addView(attach,new LinearLayout.LayoutParams(dp(48),dp(48)));
        if(messages.length()>0 && eink){
            MaterialButton confidenceChip=button(confidenceTouched?"Confidence · "+confidencePercent+"%":"Confidence",()->{});
            confidenceChip.setTextSize(12);confidenceChip.setContentDescription("Optional confidence; tap to choose. Nothing is sent until adjusted.");
            confidenceChip.setEnabled(!busy);confidenceChip.setOnClickListener(view->showConfidencePicker(confidenceChip));
            LinearLayout.LayoutParams chipParams=new LinearLayout.LayoutParams(0,dp(48),1);chipParams.setMargins(dp(4),0,dp(8),0);controls.addView(confidenceChip,chipParams);
        }else if(messages.length()>0){
        TextView confidenceLabel=label(confidenceTouched?confidencePercent+"%":"Confidence",11,false);confidenceLabel.setGravity(Gravity.CENTER_VERTICAL);confidenceLabel.setContentDescription("Optional confidence; nothing is sent until adjusted");controls.addView(confidenceLabel,new LinearLayout.LayoutParams(dp(64),dp(48)));
        com.google.android.material.slider.Slider confidence=new com.google.android.material.slider.Slider(this);confidence.setContentDescription("Optional confidence in your answer; adjust to include it");confidence.setValueFrom(0);confidence.setValueTo(100);confidence.setStepSize(10);confidence.setValue(confidencePercent);confidence.setTickVisible(false);confidence.setTrackHeight(dp(2));confidence.setThumbRadius(dp(6));confidence.setHaloRadius(dp(12));confidence.setLabelFormatter(value->Math.round(value)+"%");confidence.setEnabled(!busy);
        if(eink){confidence.setTickTintList(ColorStateList.valueOf(Color.BLACK));confidence.setTrackTintList(ColorStateList.valueOf(Color.BLACK));confidence.setTrackActiveTintList(ColorStateList.valueOf(Color.BLACK));confidence.setTrackInactiveTintList(ColorStateList.valueOf(Color.BLACK));confidence.setThumbTintList(ColorStateList.valueOf(Color.BLACK));confidence.setHaloTintList(ColorStateList.valueOf(Color.TRANSPARENT));}
        confidence.addOnChangeListener((slider,value,fromUser)->{if(fromUser){confidenceTouched=true;confidencePercent=Math.round(value);confidenceLabel.setText(confidencePercent+"%");}});controls.addView(confidence,new LinearLayout.LayoutParams(0,dp(48),1));
        }else controls.addView(new android.widget.Space(this),new LinearLayout.LayoutParams(0,dp(48),1));
        MaterialButton mic=button("",this::dictate);mic.setIconResource(R.drawable.ic_mic);mic.setContentDescription("Dictate your thinking");mic.setEnabled(!busy);styleComposerIcon(mic,true);mic.setMinWidth(0);mic.setIconPadding(0);controls.addView(mic,new LinearLayout.LayoutParams(dp(48),dp(48)));
        MaterialButton send=primaryButton("",()->{if(busy){requestGeneration++;provider.cancel();chatGptHttp.cancel();if(documentTask!=null)documentTask.cancel(true);busy=false;streamText="";captureDraft();render();return;}submitReply(session);});send.setIconResource(busy?R.drawable.ic_stop:R.drawable.ic_up);send.setContentDescription(busy?"Stop response":messages.length()==1 && !session.has("agentHistory") && session.optJSONObject("pendingQuestion")==null?"Send saved goal":"Send reply");styleComposerIcon(send,true);send.setMinWidth(0);send.setIconPadding(0);controls.addView(send,new LinearLayout.LayoutParams(dp(48),dp(48)));panel.addView(controls);
        if(prefs.getBoolean("useChatGpt",false))menuAction("ChatGPT plan · Usage",this::openUsage);
        else menuAction(prefs.getString("provider",PROVIDERS[0])+" · Connection",()->{captureDraft();screen="Settings";settingsPage="AI connection";render();});
        menuAction("Continue or retry lesson",()->tutor(session));
        menuAction("Lesson stage · "+session.optString("phase","Explore"),()->materialDialog().setTitle("Lesson stage").setItems(new String[]{"Explore","Plan","Teach","Practice","Complete"},(d,w)->{try{session.put("phase",new String[]{"Explore","Plan","Teach","Practice","Complete"}[w]);save();render();}catch(Exception e){error(e);}}).show());
        if(!session.optString("kind").equals("practice"))menuAction("New lesson",this::newSession);
        menuAction("Extract knowledge",()->{if(messages.length()<2){error(new IOException("Complete a tutor turn before extracting knowledge."));return;}extract(session);});
        menuAction("Background jobs",this::showJobs);
        menuAction("Find in session",()->findInSession(session));
        menuAction("Export lesson",()->exportLesson(session));
        menuAction("Rename session",()->renameSession(session));
        menuAction("Delete session",()->deleteSession(session));
    }
    private void showConfidencePicker(MaterialButton chip){
        LinearLayout choices=column();choices.setPadding(dp(16),dp(8),dp(16),dp(8));
        androidx.appcompat.app.AlertDialog dialog=materialDialog().setTitle("How confident are you?").setView(choices).setNegativeButton("Cancel",null).create();
        for(int rowIndex=0;rowIndex<2;rowIndex++){
            LinearLayout line=row();
            for(int columnIndex=0;columnIndex<6;columnIndex++){
                int choice=rowIndex*6+columnIndex;boolean unset=choice==11;
                MaterialButton option=button(unset?"Not\nset":choice*10+"%",()->{
                    confidenceTouched=!unset;confidencePercent=unset?50:choice*10;chip.setText(confidenceTouched?"Confidence · "+confidencePercent+"%":"Confidence");dialog.dismiss();
                });
                option.setTextSize(12);option.setMinWidth(0);option.setMinimumWidth(0);option.setPadding(0,0,0,0);option.setInsetTop(dp(2));option.setInsetBottom(dp(2));option.setMaxLines(2);option.setCornerRadius(dp(12));
                boolean selected=unset?!confidenceTouched:confidenceTouched && confidencePercent==choice*10;option.setSelected(selected);
                option.setBackgroundTintList(ColorStateList.valueOf(selected?ink:paper));option.setTextColor(selected?paper:ink);
                option.setContentDescription(unset?"Confidence not set":choice*10+" percent confidence");
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(56),1);params.setMargins(dp(2),dp(2),dp(2),dp(2));line.addView(option,params);
            }
            choices.addView(line,new LinearLayout.LayoutParams(-1,-2));
        }
        dialog.show();
    }
    private void exportLesson(JSONObject session){
        StringBuilder text=new StringBuilder(session.optString("title")+"\n\n");JSONArray messages=session.optJSONArray("messages");
        for(int i=0;messages!=null && i<messages.length();i++){JSONObject m=messages.optJSONObject(i);text.append(m.optString("role")).append("\n").append(m.optString("content")).append("\n\n");}export(text.toString(),"lesson.txt","text/plain");
    }
    private void renameSession(JSONObject session){EditText name=input("Session title",session.optString("title"),false);materialDialog().setTitle("Rename session").setView(name).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{try{if(!name.getText().toString().trim().isEmpty())session.put("title",name.getText().toString().trim());save();render();}catch(Exception e){error(e);}}).show();}
    private void einkMessage(JSONObject message)throws JSONException{
        if(!message.getString("role").equals("user")){card("",markdown.toMarkdown(message.getString("content")));return;}
        MaterialCardView bubble=new MaterialCardView(this);bubble.setCardBackgroundColor(paper);bubble.setCardElevation(0);bubble.setRadius(dp(24));bubble.setStrokeWidth(dp(1));bubble.setStrokeColor(ink);
        TextView text=richLabel(message.getString("content"),fontSize,false);text.setPadding(dp(16),dp(12),dp(16),dp(12));text.setTextIsSelectable(true);bubble.addView(text);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(Math.max(dp(160),dp((int)(getResources().getConfiguration().screenWidthDp*.84f))),-2);params.gravity=Gravity.END;params.setMargins(0,dp(8),0,dp(16));content.addView(bubble,params);
    }
    private void setReadingExpanded(boolean expanded){
        readingExpanded=expanded;
        View bar=findViewById(R.id.lesson_app_bar);if(bar!=null)bar.setVisibility(expanded?View.GONE:View.VISIBLE);
        if(composerHost!=null)composerHost.setVisibility(expanded?View.GONE:View.VISIBLE);
        MaterialButton toggle=findViewById(R.id.reading_expand);if(toggle!=null){toggle.setIconResource(expanded?R.drawable.ic_fullscreen_exit:R.drawable.ic_fullscreen);toggle.setContentDescription(expanded?"Exit reading mode":"Expand reading area");}
    }
    private int readingStep(NestedScrollView scroll){
        android.graphics.Rect visible=new android.graphics.Rect();int viewport=scroll.getGlobalVisibleRect(visible)?visible.height():scroll.getHeight();
        int line=Math.round(fontSize*getResources().getDisplayMetrics().scaledDensity*1.45f);
        return EinkReading.step(viewport,line);
    }
    private void stepReading(NestedScrollView scroll,int direction){
        stopFollowingLatest();
        int delta=direction*readingStep(scroll);int[] consumed=new int[2];
        scroll.startNestedScroll(androidx.core.view.ViewCompat.SCROLL_AXIS_VERTICAL,androidx.core.view.ViewCompat.TYPE_NON_TOUCH);
        scroll.dispatchNestedPreScroll(0,delta,consumed,null,androidx.core.view.ViewCompat.TYPE_NON_TOUCH);
        int before=scroll.getScrollY();scroll.scrollBy(0,delta-consumed[1]);int moved=scroll.getScrollY()-before;
        scroll.dispatchNestedScroll(0,moved,0,delta-consumed[1]-moved,null,androidx.core.view.ViewCompat.TYPE_NON_TOUCH);scroll.stopNestedScroll(androidx.core.view.ViewCompat.TYPE_NON_TOUCH);
    }
    private MaterialButton readingIcon(int icon,String description,Runnable action){
        MaterialButton button=button("",action);button.setIconResource(icon);button.setContentDescription(description);button.setIconSize(dp(24));button.setIconPadding(0);button.setMinWidth(0);button.setStrokeWidth(0);button.setInsetTop(0);button.setInsetBottom(0);button.setPadding(dp(12),0,dp(12),0);return button;
    }
    private void stopFollowingLatest(){followLatestSession="";updateFollowingLatest();}
    private void updateFollowingLatest(){
        MaterialButton latest=findViewById(R.id.reading_latest);if(latest==null)return;
        boolean following=!followLatestSession.isEmpty() && followLatestSession.equals(selectedSession);
        latest.setSelected(following);latest.setStrokeWidth(following?dp(1):0);
        latest.setContentDescription(following?"Move to end; following new text":"Move to end");androidx.core.view.ViewCompat.setStateDescription(latest,following?"Following new text":"Follow off");
    }
    private void lessonReadingControls(){
        NestedScrollView scroll=lessonScroll;LinearLayout turns=row();turns.setPadding(dp(12),0,dp(12),0);
        MaterialButton expand=readingIcon(R.drawable.ic_fullscreen,"Expand reading area",()->{captureDraft();setReadingExpanded(!readingExpanded);});expand.setId(R.id.reading_expand);
        MaterialButton latest=readingIcon(R.drawable.ic_latest,"Move to end",()->{followLatestSession=selectedSession;updateFollowingLatest();AppBarLayout bar=findViewById(R.id.lesson_app_bar);if(bar!=null)bar.setExpanded(false,false);scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));});latest.setId(R.id.reading_latest);
        MaterialButton up=readingIcon(R.drawable.ic_up,"Previous screenful",()->stepReading(scroll,-1));
        MaterialButton down=readingIcon(R.drawable.ic_down,"Next screenful",()->stepReading(scroll,1));
        for(MaterialButton button:new MaterialButton[]{expand,latest,up,down})turns.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(turns,root.indexOfChild(composerHost),new LinearLayout.LayoutParams(-1,dp(48)));
        Runnable availability=()->{up.setEnabled(scroll.canScrollVertically(-1));down.setEnabled(scroll.canScrollVertically(1));updateFollowingLatest();};
        scroll.setOnScrollChangeListener((View.OnScrollChangeListener)(view,x,y,oldX,oldY)->availability.run());
        final float[] downY={0};final int slop=ViewConfiguration.get(this).getScaledTouchSlop();
        scroll.setOnTouchListener((view,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN)downY[0]=event.getY();else if(event.getActionMasked()==MotionEvent.ACTION_MOVE && Math.abs(event.getY()-downY[0])>slop)stopFollowingLatest();return false;});
        scroll.setOnGenericMotionListener((view,event)->{if(event.getActionMasked()==MotionEvent.ACTION_SCROLL)stopFollowingLatest();return false;});
        scroll.setOnKeyListener((view,key,event)->{if(event.getAction()==KeyEvent.ACTION_DOWN && (key==KeyEvent.KEYCODE_PAGE_UP || key==KeyEvent.KEYCODE_PAGE_DOWN || key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN))stopFollowingLatest();return false;});
        setReadingExpanded(readingExpanded);updateFollowingLatest();
        View.OnLayoutChangeListener followLayout=(view,l,t,r,b,ol,ot,or,ob)->{if(selectedSession.equals(followLatestSession))scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));};
        scroll.addOnLayoutChangeListener(followLayout);content.addOnLayoutChangeListener(followLayout);
        scroll.post(()->{if(selectedSession.equals(followLatestSession) || !readingPositions.containsKey(selectedSession) || readingEnds.contains(selectedSession))scroll.fullScroll(View.FOCUS_DOWN);else scroll.scrollTo(0,readingPositions.get(selectedSession));availability.run();});
    }
    private void renderSourceRequest(JSONObject session)throws Exception{
        JSONObject pending=session.getJSONObject("pendingQuestion"),args=pending.getJSONObject("request");LinearLayout panel=card("Use a source?",pending.getString("tool")+" · "+args.optString("path")+(args.optString("pattern").isEmpty()?"":"\nFind: "+args.optString("pattern"))+"\n\nNo book content has been read or sent. Review a local passage and approve it, or continue without this source.");
        panel.addView(button("Choose and review a passage",()->{try{JSONArray candidates=pending.getJSONArray("candidates");String[] names=new String[candidates.length()];for(int i=0;i<names.length;i++)names[i]=candidates.getJSONObject(i).getString("title");materialDialog().setTitle("Choose a source").setItems(names,(d,index)->searchDocument(candidates.optJSONObject(index),session.optString("id"),args)).setNegativeButton("Cancel",null).show();}catch(Exception error){error(error);}}));
        panel.addView(button("Continue without source",()->resolveSourceRequest(session,"",true)));
    }
    private void resolveSourceRequest(JSONObject session,String excerpt,boolean declined){
        if(!connectionIdle())return;try{JSONObject pending=session.getJSONObject("pendingQuestion");if(!pending.optString("kind").equals("source"))return;if(excerpt.length()>6000)throw new IOException("Trim the source excerpt to 6,000 characters.");JSONObject before=new JSONObject(records.root.toString());
            try{JSONObject output=ToolProtocol.output(pending.getJSONObject("call"),declined?"The learner declined source access. No document content was read or supplied.":"The learner approved a bounded source excerpt for this turn.");if(!declined)session.put("approvedExcerpts",excerpt);session.getJSONArray("agentHistory").put(output);session.remove("pendingQuestion");if(pending.has("retainedProblem"))session.put("pendingQuestion",pending.getJSONObject("retainedProblem"));session.getJSONArray("messages").put(new JSONObject().put("role","user").put("content",declined?"Source request declined.":"Source passage approved for this turn."));records.save();}
            catch(Exception error){records.root=before;throw error;}
            captureDraft();render();tutor(session);
        }catch(Exception error){error(error);}
    }
    private void renderQuestion(JSONObject session)throws Exception{
        JSONObject question=session.getJSONObject("pendingQuestion");
        if(question.optBoolean("needsReveal") && !question.optBoolean("revealed")){LinearLayout reveal=card("Check your understanding ›","");reveal.setClickable(true);reveal.setFocusable(true);reveal.setContentDescription("Open check your understanding");reveal.setOnClickListener(v->{try{captureDraft();question.put("readingThinking",session.optString("draft"));question.put("revealed",true);draft="";session.put("draft","");save();render();}catch(Exception error){error(error);}});return;}
        LinearLayout card=card(question.optString("kind").equals("ask")?"Question":"Check your understanding",markdown.toMarkdown(question.getString("question")));
        if(!question.optString("context").isEmpty()){TextView detail=label("",fontSize,false);markdown.setParsedMarkdown(detail,markdown.toMarkdown(question.optString("context")));card.addView(detail);}
        JSONArray options=question.getJSONArray("options");boolean multi=question.optString("mode").equals("multi-select");JSONArray selected=question.optJSONArray("selectedValues");if(selected==null){selected=new JSONArray();question.put("selectedValues",selected);}final JSONArray values=selected;
        LinearLayout choices=multi?column():new RadioGroup(this);if(!multi)((RadioGroup)choices).setOrientation(LinearLayout.VERTICAL);
        for(int i=0;i<options.length();i++){JSONObject option=options.getJSONObject(i);String value=option.getString("value");CompoundButton button=multi?new com.google.android.material.checkbox.MaterialCheckBox(this):new com.google.android.material.radiobutton.MaterialRadioButton(this);button.setId(View.generateViewId());markdown.setParsedMarkdown(button,markdown.toMarkdown(option.getString("label")));button.setTextSize(fontSize);if(eink){button.setTypeface(getResources().getFont(R.font.eink_outfit));button.setLineSpacing(0,1.35f);button.setPadding(dp(4),dp(8),dp(8),dp(8));}button.setTextColor(ink);button.setMinHeight(dp(48));button.setButtonTintList(ColorStateList.valueOf(accent));button.setEnabled(!busy);if(eink){button.setBackground(null);button.setStateListAnimator(null);}
            for(int j=0;j<values.length();j++)if(value.equals(values.optString(j)))button.setChecked(true);
            button.setOnCheckedChangeListener((v,checked)->{try{if(!multi && checked){while(values.length()>0)values.remove(0);}if(checked)values.put(value);else for(int j=values.length()-1;j>=0;j--)if(value.equals(values.optString(j)))values.remove(j);question.put("selectedValues",values);if(eink)v.jumpDrawablesToCurrentState();save();}catch(JSONException ignored){}});choices.addView(button,new LinearLayout.LayoutParams(-1,-2));
        }card.addView(choices);
        MaterialButton submit=primaryButton("Submit answer",()->submitQuestion(session,false));submit.setEnabled(!busy);LinearLayout.LayoutParams submitParams=new LinearLayout.LayoutParams(-1,dp(48));submitParams.setMargins(0,dp(8),0,0);card.addView(submit,submitParams);
        if(!question.optString("kind").equals("ask"))menuAction("I don't know this answer",()->submitQuestion(session,true));
    }
    private void submitQuestion(JSONObject session,boolean dontKnow){
        if(busy)return;JSONObject question=session.optJSONObject("pendingQuestion");if(question==null)return;
        if(question.optBoolean("needsReveal") && !question.optBoolean("revealed")){composer.setError("Open Check your understanding to answer");return;}
        try{
            String text=composer==null?"":composer.getText().toString().trim();JSONArray values=question.optJSONArray("selectedValues");if(values==null)values=new JSONArray();
            String kind=question.getString("kind");if(!dontKnow && values.length()==0 && (text.isEmpty() || kind.equals("quiz"))){composer.setError(kind.equals("quiz")?"Choose an answer first":"Write an answer or choose an option");return;}
            JSONObject request=new JSONObject().put("values",new JSONArray(values.toString())).put("dontKnow",dontKnow);
            if(!question.optString("readingThinking").isEmpty())request.put("readingThinking",question.getString("readingThinking"));
            if(!text.isEmpty())request.put(kind.equals("quiz")?"thinking":question.getJSONArray("options").length()>0?"other":"text",text);
            if(confidenceTouched)request.put("confidence",confidencePercent);
            captureDraft();busy=true;operation="Checking your answer";int generation=++requestGeneration;render();
            worker.submit(()->{
                try(OriginalCore core=new OriginalCore(this)){
                    JSONObject result=core.call(new JSONObject().put("operation","answer").put("question",question).put("answer",request));
                    if(!result.optBoolean("ok"))throw new IOException(result.optString("error"));
                    runOnUiThread(()->{if(isDestroyed() || generation!=requestGeneration)return;busy=false;try{
                        JSONObject graded=result.getJSONObject("value");JSONObject before=new JSONObject(records.root.toString());
                        try{
                            session.getJSONArray("agentHistory").put(ToolProtocol.output(question.getJSONObject("call"),result.getJSONObject("model")));
                            StringBuilder display=new StringBuilder(question.getString("question")).append("\n\n");JSONArray answers=graded.getJSONArray("answers");for(int i=0;i<answers.length();i++)display.append("• ").append(answers.getJSONObject(i).getString("label")).append('\n');
                            if(dontKnow)display.append("I don't know.\n");if(!text.isEmpty())display.append(text).append('\n');if(graded.has("confidence"))display.append("Confidence: ").append(graded.getInt("confidence")).append("%\n");
                            session.getJSONArray("messages").put(new JSONObject().put("role","user").put("content",display.toString()));
                            String reveal=kind.equals("quiz")?(graded.optBoolean("correct")?"Correct\n\n":"Not yet\n\n")+graded.optString("explanation"):kind.equals("explain")?"Reference answer\n\n"+graded.optString("committed"):"";
                            if(!reveal.isEmpty())session.getJSONArray("messages").put(new JSONObject().put("role","assistant").put("content",reveal).put("origin","tool"));
                            JSONArray answered=session.optJSONArray("questionAnswers");if(answered==null){answered=new JSONArray();session.put("questionAnswers",answered);}answered.put(new JSONObject().put("question",question).put("answer",graded));
                            session.remove("pendingQuestion");session.put("draft","");records.save();
                        }catch(Exception e){records.root=before;throw e;}
                        draft="";confidenceTouched=false;confidencePercent=50;render();tutor(session);
                    }catch(Exception error){render();error(error);}});
                }catch(Exception error){runOnUiThread(()->{if(!isDestroyed() && generation==requestGeneration){busy=false;render();error(error);}});}
            });
        }catch(Exception error){error(error);}
    }
    private void findInSession(JSONObject session){
        EditText query=input("Find displayed text","",false);materialDialog().setTitle("Find in session").setView(query).setNegativeButton("Cancel",null).setPositiveButton("Find",(d,w)->{
            String term=query.getText().toString();if(term.trim().isEmpty())return;try{JSONArray messages=session.getJSONArray("messages"),displayed=new JSONArray();for(int i=0;i<messages.length();i++)displayed.put(markdown.toMarkdown(messages.getJSONObject(i).getString("content")).toString());
                documentWork("Finding in session",()->{JSONArray found=new JSONArray();try(OriginalCore core=new OriginalCore(this)){for(int i=0;i<displayed.length();i++){String text=displayed.getString(i);JSONObject result=core.call(new JSONObject().put("operation","sessionFind").put("text",text).put("query",term));if(!result.optBoolean("ok"))throw new IOException(result.optString("error"));JSONArray matches=result.getJSONArray("value");for(int j=0;j<matches.length();j++){JSONObject match=matches.getJSONObject(j);int start=match.getInt("start"),end=match.getInt("end");found.put(new JSONObject().put("message",i).put("preview",text.substring(Math.max(0,start-50),Math.min(text.length(),end+80))));if(found.length()>=100)return found;}}}return found;},found->{try{if(found.length()==0){Toast.makeText(this,"No matching displayed text",Toast.LENGTH_LONG).show();return;}String[] labels=new String[found.length()];for(int i=0;i<found.length();i++)labels[i]=found.getJSONObject(i).getString("preview");materialDialog().setTitle(found.length()+" match(es)"+(found.length()==100?" · first 100":"")).setItems(labels,(dialog,index)->{JSONObject hit=found.optJSONObject(index);int messageIndex=hit.optInt("message");JSONObject message=messages.optJSONObject(messageIndex);showSourcePassage(session,message.optString("id","m_"+session.optString("id")+"_"+messageIndex));}).setNegativeButton("Close",null).show();}catch(Exception error){error(error);}});
            }catch(Exception error){error(error);}
        }).show();
    }
    private void showSourcePassage(JSONObject session,String messageId){
        JSONArray messages=session.optJSONArray("messages");for(int i=0;messages!=null && i<messages.length();i++){JSONObject message=messages.optJSONObject(i);String id=message.optString("id","m_"+session.optString("id")+"_"+i);if(id.equals(messageId)){TextView passage=label("",fontSize,false);markdown.setParsedMarkdown(passage,markdown.toMarkdown(message.optString("content")));passage.setTextIsSelectable(true);passage.setPadding(dp(24),dp(12),dp(24),dp(12));ScrollView scroll=new ScrollView(this);scroll.addView(passage);materialDialog().setTitle("Source passage").setView(scroll).setPositiveButton("Done",null).show();return;}}
        Toast.makeText(this,"The original passage is no longer present in this session.",Toast.LENGTH_LONG).show();
    }
    private void graphMutation(String title,JSONObject arguments,Runnable complete){
        if(!connectionIdle())return;try{JSONObject request=new JSONObject(arguments.toString()).put("graph",GraphRecords.snapshot(records)).put("sessions",new JSONArray(records.rows("sessions").toString()));
            documentWork(title,()->{try(OriginalCore core=new OriginalCore(this)){JSONObject result=core.call(request);if(!result.optBoolean("ok"))throw new IOException(result.optString("error"));return result;}},result->{try{JSONObject before=new JSONObject(records.root.toString());try{GraphRecords.apply(records,result.getJSONObject("state"),result.getJSONObject("value"));records.root.put("graphSnapshot",result.getJSONObject("snapshot"));complete.run();records.save();}catch(Exception error){records.root=before;throw error;}render();}catch(Exception error){error(error);}});
        }catch(Exception error){error(error);}
    }
    private void deleteClaim(JSONObject claim){
        try{if(claim.optBoolean("graphNode") || records.root.has("graph"))graphMutation("Deleting proposition",new JSONObject().put("operation","graphDeleteNode").put("nodeId",claim.getString("id")),()->records.remove("knowledge",claim.optString("id")));else{records.remove("knowledge",claim.getString("id"));save();render();}}catch(Exception error){error(error);}
    }
    private void deleteSession(JSONObject session){
        if(!connectionIdle())return;materialDialog().setTitle("Delete session").setItems(new String[]{"Delete session; keep extracted knowledge","Delete session and its graph contributions"},(d,index)->{
            try{JSONObject request=new JSONObject().put("operation","graphDeleteSession").put("sourceId",session.getString("id")).put("mode",index==0?"preserve-graph":"delete-nodes");graphMutation("Deleting session",request,()->{records.remove("sessions",session.optString("id"));selectedSession="";draft="";});}catch(Exception error){error(error);}
        }).setNegativeButton("Cancel",null).show();
    }
    private void showPlan(JSONObject session){
        try{JSONObject tree=new JSONObject(session.getJSONObject("planTree").toString());JSONArray history=session.optJSONArray("agentHistory");JSONArray snapshot=history==null?new JSONArray():new JSONArray(history.toString());
            documentWork("Opening learning plan",()->{try(OriginalCore core=new OriginalCore(this)){return core.call(new JSONObject().put("operation","planStatuses").put("tree",tree).put("history",snapshot));}},result->{try{
                if(!result.optBoolean("ok"))throw new IOException(result.optString("error"));JSONArray nodes=tree.getJSONArray("nodes"),edges=tree.getJSONArray("edges"),visited=result.getJSONArray("visited");Set<String> seen=new HashSet<>();for(int i=0;i<visited.length();i++)seen.add(visited.getString(i));Map<String,String> titles=new HashMap<>();for(int i=0;i<nodes.length();i++)titles.put(nodes.getJSONObject(i).getString("id"),nodes.getJSONObject(i).getString("title"));
                StringBuilder text=new StringBuilder();for(int i=0;i<nodes.length();i++){JSONObject node=nodes.getJSONObject(i);String id=node.getString("id");text.append(node.getString("title")).append(id.equals(result.optString("current"))?" · current":seen.contains(id)?" · visited":" · upcoming").append('\n');for(int j=0;j<edges.length();j++){JSONObject edge=edges.getJSONObject(j);if(edge.getString("to").equals(id))text.append("  Prerequisite: ").append(titles.get(edge.getString("from"))).append('\n');}text.append('\n');}TextView view=label(text.toString(),fontSize,false);view.setPadding(dp(24),dp(8),dp(24),dp(8));ScrollView scroll=new ScrollView(this);scroll.addView(view);materialDialog().setTitle("Learning plan").setView(scroll).setPositiveButton("Done",null).show();
            }catch(Exception error){error(error);}});
        }catch(Exception error){error(error);}
    }
    private boolean visibleLesson(JSONObject session){return !session.optString("kind").equals("practice") && (session.optJSONArray("messages")!=null && session.optJSONArray("messages").length()>0 || !session.optString("draft").trim().isEmpty() || session.optJSONArray("documents")!=null && session.optJSONArray("documents").length()>0);}
    private void lessonListEntry(JSONObject session)throws Exception{
        MaterialCardView frame=new MaterialCardView(this);frame.setCardBackgroundColor(Color.WHITE);frame.setCardElevation(0);frame.setRadius(dp(eink?20:12));frame.setStrokeWidth(dp(1));frame.setStrokeColor(eink?Color.BLACK:MaterialColors.getColor(this,com.google.android.material.R.attr.colorOutlineVariant,subtle));LinearLayout line=row();line.setPadding(dp(16),dp(12),dp(16),dp(12));line.setMinimumHeight(dp(80));LinearLayout copy=column();TextView title=label(session.getString("title"),16,false);androidx.core.widget.TextViewCompat.setTextAppearance(title,com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(title);int count=session.getJSONArray("messages").length();TextView detail=label(count==0?"Draft":count+(count==1?" message · ":" messages · ")+session.optString("phase","Explore"),14,false);detail.setTextColor(subtle);copy.addView(detail);line.addView(copy,new LinearLayout.LayoutParams(0,-2,1));ImageView chevron=new ImageView(this);chevron.setImageResource(R.drawable.ic_chevron);chevron.setImageTintList(ColorStateList.valueOf(subtle));line.addView(chevron,new LinearLayout.LayoutParams(dp(24),dp(24)));frame.addView(line);frame.setClickable(true);frame.setFocusable(true);frame.setContentDescription("Open session: "+session.getString("title"));frame.setOnClickListener(v->{selectedSession=session.optString("id");draft="";render();});LinearLayout.LayoutParams spacing=new LinearLayout.LayoutParams(-1,-2);spacing.setMargins(0,0,0,dp(8));content.addView(frame,spacing);
    }
    private void newSession(){
        if(!connectionIdle())return;captureDraft();try{JSONObject session=saveNewSession("",false);screen="Learn";selectedSession=session.getString("id");selectedPractice="";draft="";confidenceTouched=false;confidencePercent=50;render();}catch(Exception error){error(error);}
    }
    private void showSessionCreator(boolean practice,String restored){
        if(creationDialog!=null && creationDialog.isShowing())return;creationKind=practice?"practice":"lesson";
        LinearLayout body=column();body.setPadding(dp(24),dp(8),dp(24),0);
        com.google.android.material.textfield.TextInputLayout field=new com.google.android.material.textfield.TextInputLayout(this,null,com.google.android.material.R.attr.textInputOutlinedStyle);field.setId(R.id.creation_goal_layout);field.setHint(practice?"Practice topic":"Learning goal");field.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);field.setBoxBackgroundColor(Color.WHITE);field.setBoxCornerRadii(dp(12),dp(12),dp(12),dp(12));field.setErrorEnabled(true);
        if(eink){field.setBoxStrokeColor(Color.BLACK);field.setDefaultHintTextColor(ColorStateList.valueOf(Color.BLACK));field.setHintTextColor(ColorStateList.valueOf(Color.BLACK));}
        com.google.android.material.textfield.TextInputEditText goal=new com.google.android.material.textfield.TextInputEditText(field.getContext());goal.setId(R.id.creation_goal);goal.setText(restored);goal.setTextSize(16);goal.setTextColor(ink);goal.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);goal.setMinLines(2);goal.setMaxLines(4);goal.setGravity(Gravity.TOP);goal.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);goal.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence text,int start,int count,int after){}public void onTextChanged(CharSequence text,int start,int before,int count){if(field.getError()!=null && (!text.toString().trim().isEmpty() || practice && records.rows("knowledge").length()>0))field.setError(null);}public void afterTextChanged(android.text.Editable text){}});field.addView(goal,new LinearLayout.LayoutParams(-1,-2));body.addView(field,new LinearLayout.LayoutParams(-1,-2));
        TextView help=label(practice?"Enter a topic, or leave it blank to practise knowledge from your library.":"A topic or a question is enough. You can refine it with your tutor.",14,false);help.setTextColor(subtle);body.addView(help);ScrollView scroll=new ScrollView(this);scroll.addView(body);
        androidx.appcompat.app.AlertDialog dialog=materialDialog().setTitle(practice?"New practice session":"New lesson").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton(practice?"Start practice":"Create lesson",null).create();creationDialog=dialog;creationGoal=goal;
        dialog.setOnDismissListener(d->{if(creationDialog==dialog){creationDialog=null;creationGoal=null;creationKind="";}});
        dialog.show();{android.widget.Button create=dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);create.setOnClickListener(v->{if(!dialog.isShowing() || !create.isEnabled())return;String title=goal.getText().toString().trim();if(title.isEmpty() && (!practice || records.rows("knowledge").length()==0)){field.setError(practice?"Enter a topic or add knowledge to your library first.":"Enter a topic or question to create your lesson.");goal.requestFocus();return;}field.setError(null);create.setEnabled(false);
            try{JSONObject session=saveNewSession(title,practice);android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);keyboard.hideSoftInputFromWindow(goal.getWindowToken(),0);dialog.dismiss();creationDialog=null;creationGoal=null;creationKind="";captureDraft();screen="Learn";selectedSession=session.getString("id");selectedPractice="";draft="";render();if(sessionConnectionReady(session))tutor(session);}
            catch(Exception failure){create.setEnabled(true);field.setError("Could not save this "+(practice?"practice session":"lesson")+". Your text is kept; try again.");}
        });goal.setOnEditorActionListener((view,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){create.performClick();return true;}return false;});goal.requestFocus();if(dialog.getWindow()!=null)dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);}
    }
    private JSONObject saveNewSession(String title,boolean practice)throws Exception{
        JSONObject before=new JSONObject(records.root.toString());try{
            JSONObject session=new JSONObject().put("id",Records.id()).put("title",title.isEmpty()?(practice?"Library practice":"New lesson"):title).put("created",System.currentTimeMillis()).put("phase",practice?"Practice":"Explore");
            if(practice){session.put("kind","practice");JSONArray nodes=new JSONArray();StringBuilder context=new StringBuilder(title);if(title.isEmpty()){JSONArray knowledge=records.rows("knowledge");for(int i=0;i<knowledge.length();i++){JSONObject claim=knowledge.getJSONObject(i);nodes.put(new JSONObject().put("id",claim.getString("id")).put("name",claim.getString("title")));context.append("\n[").append(claim.getString("id")).append("] ").append(claim.getString("title")).append(": ").append(claim.getString("statement"));}}session.put("practiceNodes",nodes).put("messages",oneMessage(context.toString()));}
            else session.put("messages",title.isEmpty()?new JSONArray():new JSONArray().put(new JSONObject().put("role","user").put("content","I want to understand: "+title)));
            records.rows("sessions").put(session);records.save();return session;
        }catch(Exception failure){records.root=before;throw failure;}
    }
    private boolean sessionConnectionReady(JSONObject session){try{
        if(chatGpt!=null && chatGpt.isSigningIn())return false;String[] roles=session.optString("kind").equals("practice")?new String[]{""}:session.optString("phase").equals("Explore")?new String[]{"prober","teacher"}:new String[]{"teacher"};
        for(String role:roles){JSONObject route=role.isEmpty()?RoleConnections.validate(NativeModels.active(this)):NativeModels.forRole(this,role);if(route.getString("billing").equals("api")){if(secrets.get(route.getString("provider")).trim().isEmpty())return false;}else{boolean connected=false;JSONArray accounts=chatGpt==null?new JSONArray():chatGpt.accounts();for(int i=0;i<accounts.length();i++){JSONObject account=accounts.getJSONObject(i);if(account.getString("client_id").equals(route.getString("account")) && account.optBoolean("connected") && account.optBoolean("plan"))connected=true;}if(!connected)return false;}}
        return true;
    }catch(Exception unavailable){return false;}}
    private void submitReply(JSONObject session){
        if(session.optJSONObject("pendingQuestion")!=null && session.optJSONObject("pendingQuestion").optString("kind").equals("source")){composer.setError("Review or decline the source request first");return;}
        if(session.optJSONObject("pendingQuestion")!=null && !session.optString("kind").equals("practice")){submitQuestion(session,false);return;}
        String answer=composer.getText().toString().trim();if(answer.isEmpty()){if(session.optJSONArray("messages").length()==1 && !session.has("agentHistory")){tutor(session);return;}composer.setError(session.optJSONArray("messages").length()==0?"Tell me what you want to learn":"Write a message first");return;}
        String text=answer+(confidenceTouched?"\n\nConfidence: "+confidencePercent+"% (selected with the confidence slider)":"");
        try{JSONArray templates=new InteractiveTemplates(this).list();for(int i=0;i<templates.length();i++){JSONObject template=templates.getJSONObject(i);if(!template.optString("shortcut").isEmpty() && answer.equals("/"+template.getString("shortcut"))){captureDraft();startActivityForResult(new Intent(this,InteractiveActivity.class).putExtra("template",template.getString("id")),OPEN_TEMPLATE);return;}}}catch(Exception e){error(e);return;}
        boolean mentionsSource=java.util.regex.Pattern.compile("(?i)\\b(book|textbook|chapter|document|pdf|epub|source)\\b").matcher(answer).find();
        JSONArray linked=session.optJSONArray("documents");
        if(mentionsSource && linked!=null && linked.length()>0 && session.optString("approvedExcerpts").isEmpty()){
            captureDraft();materialDialog().setTitle("Use a book for this reply?").setMessage("No book content has been sent. Search and approve a passage, or send this question without book material.").setPositiveButton("Find a passage",(d,w)->attachmentMenu(session)).setNegativeButton("Send without book",(d,w)->sendTurn(session,text)).setNeutralButton("Cancel",null).show();
        }else sendTurn(session,text);
    }
    private void sendTurn(JSONObject session, String text) {
        if(busy)return;captureDraft();String unsent=draft;
        try{JSONObject before=new JSONObject(records.root.toString());try{
            if(session.getJSONArray("messages").length()==0 && !session.optString("kind").equals("practice")){String title=text.replaceAll("\\s+"," ").trim();if(title.codePointCount(0,title.length())>80)title=title.substring(0,title.offsetByCodePoints(0,80))+"…";session.put("title",title);}
            JSONObject message=new JSONObject().put("role","user").put("content",text);session.getJSONArray("messages").put(message);
            if(session.has("agentHistory")){JSONObject input=new JSONObject(message.toString());if(session.optString("kind").equals("practice") && session.optJSONObject("pendingQuestion")!=null)input.put("content",practiceMarker("thinking")+":\n"+text);session.getJSONArray("agentHistory").put(input);}
            session.put("draft","");records.save();
        }catch(Exception failure){records.root=before;draft=unsent;render();throw failure;}
            draft="";confidenceTouched=false;confidencePercent=50;render();tutor(session);
        }catch(Exception error){error(error);}
    }
    private String knowledgeContext() {
        StringBuilder text = new StringBuilder(); JSONArray rows = records.rows("knowledge");
        // Bound the context; storage itself keeps all records. Most recent reviewed claims first.
        for (int i = rows.length()-1; i >= 0 && text.length() < 18000; i--) {
            JSONObject k = rows.optJSONObject(i);
            text.append("\n").append(k.optString("title")).append(": ").append(k.optString("statement")).append(" [").append(k.optString("status", "Unreviewed")).append("]");
        }
        return text.toString();
    }
    private String originalPrompt(String role)throws IOException{
        try(InputStream input=getAssets().open("prompts/"+role+".txt")){return new String(ProviderClient.readBounded(input,200000),StandardCharsets.UTF_8);}
    }
    private void tutor(JSONObject session) {
        try {
            if(session.optJSONObject("pendingQuestion")!=null && (!session.optString("kind").equals("practice") || session.getJSONObject("pendingQuestion").optString("kind").equals("source")))throw new IOException("Answer the open question before continuing the lesson.");
            if(session.getJSONArray("messages").length()==0){if(composer!=null)composer.setError("Tell me what you want to learn first");return;}
            if(!sessionConnectionReady(session)){captureDraft();render();return;}
            deliverJobs();
            JSONArray messages = session.has("agentHistory")?session.getJSONArray("agentHistory"):session.getJSONArray("messages");
            // Keep history intact locally; fail explicitly rather than silently lose a very long context.
            if (messages.toString().length() > 200000) throw new IOException("This lesson is too long for one request. Extract its knowledge and start a new session.");
            JSONArray request = new JSONArray(messages.toString());
            if (request.length() > 0 && request.getJSONObject(request.length()-1).optString("role").equals("assistant")) request.put(new JSONObject().put("role", "user").put("content", "Continue with the next reasoning step and check my understanding."));
            request=SourceInput.forTurn(request,session.optString("approvedExcerpts"));
            run("Preparing your lesson", originalPrompt(session.optString("kind").equals("practice")?"practice":session.optString("phase").equals("Explore")?"prober":"teacher"), request, session, reply -> {});
        } catch (Exception e) { error(e); }
    }
    private interface Completion { void accept(ProviderClient.Reply reply) throws Exception; }
    private void run(String title, String system, JSONArray messages, Completion complete) {
        run(title,system,messages,null,complete);
    }
    private void run(String title,String system,JSONArray messages,JSONObject lesson,Completion complete){
        if (busy || chatGpt!=null && chatGpt.isSigningIn()) { if(!busy)error(new IOException("Finish or cancel ChatGPT sign-in before starting an AI request.")); return; }
        try {
            boolean usePlan=prefs.getBoolean("useChatGpt",false);
            JSONObject account=chatGpt==null?null:chatGpt.active();
            String accountId=account==null?"":account.optString("client_id"), planModel=account==null?"":account.optString("model");
            String selected = prefs.getString("provider", PROVIDERS[0]);
            ProviderClient.Config config = usePlan?null:new ProviderClient.Config(selected, prefs.getString("endpoint", endpoint(selected)), prefs.getString("model", ""), secrets.get(selected), thinkingValue(selected,prefs.getString("endpoint",endpoint(selected)),prefs.getString("model","")));
            JSONObject activeRoute=new JSONObject().put("billing",usePlan?"plan":"api").put("model",usePlan?planModel:config.model()).put("thinking",usePlan?thinkingValue("ChatGPT",accountId,planModel):config.thinking());
            if(usePlan)activeRoute.put("account",accountId);else activeRoute.put("provider",config.provider()).put("endpoint",config.endpoint());
            JSONObject roleRoutes=new JSONObject(prefs.getString("roleConnections","{}"));Map<String,String> roleKeys=new HashMap<>();for(String providerName:PROVIDERS)roleKeys.put(providerName,secrets.get(providerName));
            String[] neededRoles=lesson==null || lesson.optString("kind").equals("practice")?new String[]{""}:lesson.optString("_operation").equals("extraction")?new String[]{"extractor","auditor"}:lesson.optString("phase").equals("Explore")?new String[]{"prober","teacher"}:new String[]{"teacher"};
            JSONArray accounts=chatGpt==null?new JSONArray():chatGpt.accounts();for(String role:neededRoles){JSONObject route=role.isEmpty()?RoleConnections.validate(new JSONObject(activeRoute.toString())):RoleConnections.resolve(roleRoutes,role,activeRoute);
                if(route.getString("billing").equals("api")){if(roleKeys.getOrDefault(route.getString("provider"),"").trim().isEmpty())throw new IOException("Save the API key for "+route.getString("provider")+" before using "+(role.isEmpty()?"this connection":role)+". Your session is saved.");}
                else{boolean allowed=false;for(int i=0;i<accounts.length();i++){JSONObject row=accounts.getJSONObject(i);if(row.getString("client_id").equals(route.getString("account")) && row.optBoolean("connected") && row.optBoolean("plan"))allowed=true;}if(!allowed)throw new IOException("Connect the selected ChatGPT account and grant plan permission for "+(role.isEmpty()?"this connection":role)+". No API fallback was used.");}
            }
            if(lesson!=null && lesson.has("approvedExcerpts")){lesson.remove("approvedExcerpts");records.save();}
            busy = true; operation = title; int generation = ++requestGeneration;
            streamSession=screen.equals("Learn") && title.equals("Preparing your lesson")?selectedSession:"";streamText="";pendingStream="";
            streamPractice=screen.equals("Practice") && title.equals("Reviewing your reasoning")?selectedPractice:"";streamQuestion=questionIndex;
            boolean showStream=!streamSession.isEmpty() || !streamPractice.isEmpty();
            java.util.function.Consumer<String> preview=showStream?text->queueStream(generation,text):null;
            captureDraft(); render();
            if(showStream && lessonScroll!=null && (!eink || streamSession.isEmpty() || followLatestSession.equals(streamSession) || readingEnds.contains(streamSession) || !readingPositions.containsKey(streamSession))){NestedScrollView target=lessonScroll;target.post(()->target.fullScroll(View.FOCUS_DOWN));}
            JSONObject lessonSnapshot=lesson==null?null:new JSONObject(lesson.toString());
            if(lessonSnapshot!=null && !lessonSnapshot.optString("_operation").equals("extraction")){lessonSnapshot.put("graphToolsEnabled",prefs.getBoolean("graphToolsEnabled",true)).put("graphContext",GraphRecords.snapshot(records)).put("graphSessions",new JSONArray(records.rows("sessions").toString()));}
            if(lessonSnapshot!=null && !lessonSnapshot.optString("_operation").equals("extraction")){JSONArray files=new JSONArray(),linked=lessonSnapshot.optJSONArray("documents"),all=records.rows("documents");for(int i=0;i<all.length();i++)for(int j=0;linked!=null && j<linked.length();j++)if(all.getJSONObject(i).getString("id").equals(linked.getString(j)))files.put(new JSONObject(all.getJSONObject(i).toString()));lessonSnapshot.put("sourceFiles",files);}
            if(lessonSnapshot!=null && !lessonSnapshot.optString("_operation").equals("extraction")){lessonSnapshot.put("searchAvailable",SearchSettings.available(this));}
            boolean freeRecall=prefs.getBoolean("freeRecall",false);
            worker.submit(() -> {
                try {
                    LessonAgent.Model inference=new LessonAgent.Model(){
                        @Override public ProviderClient.Reply complete(String prompt,JSONArray history,JSONArray tools)throws Exception{return invoke(activeRoute,prompt,history,tools);}
                        @Override public ProviderClient.Reply completeRole(String role,String prompt,JSONArray history,JSONArray tools)throws Exception{return invoke(RoleConnections.resolve(roleRoutes,role,activeRoute),prompt,history,tools);}
                        private ProviderClient.Reply invoke(JSONObject route,String prompt,JSONArray history,JSONArray tools)throws Exception{
                            if(generation!=requestGeneration || Thread.currentThread().isInterrupted())throw new InterruptedException();
                            if(route.getString("billing").equals("plan")){String client=route.getString("account");return chatGptHttp.responses(route.getString("model"),chatGpt.token(client,chatGptHttp),prompt,history,route.optString("thinking","default"),preview,tools);}
                            String roleProvider=route.getString("provider");return provider.complete(new ProviderClient.Config(roleProvider,route.getString("endpoint"),route.getString("model"),roleKeys.getOrDefault(roleProvider,""),route.optString("thinking","default")),prompt,history,preview,tools);
                        }
                    };
                    boolean extractionTask=lessonSnapshot!=null && lessonSnapshot.optString("_operation").equals("extraction");
                    LessonAgent.Result agentResult=lessonSnapshot==null || extractionTask?null:new LessonAgent(this).run(lessonSnapshot,messages,freeRecall,inference);
                    ProviderClient.Reply reply=extractionTask?new ExtractionAgent(this).run(lessonSnapshot,inference,(graph,view,usage,batch,total)->saveExtractionCheckpoint(generation,graph,view,usage,batch,total)):agentResult!=null?agentResult.reply():usePlan
                        ? chatGptHttp.responses(planModel,chatGpt.token(accountId,chatGptHttp),system,messages,thinkingValue("ChatGPT",accountId,planModel),preview)
                        : provider.complete(config, system, messages,preview);
                    runOnUiThread(() -> {
                        if (isDestroyed() || generation != requestGeneration) return;
                        busy = false;streamText="";streamHandler.removeCallbacks(paintStream);streamPosted=false;
                        try {
                            // Roll back all in-memory record changes if validation or persistence fails.
                            JSONObject before = new JSONObject(records.root.toString());
                            try {
                                if(agentResult!=null){
                                    JSONArray history=SourceInput.forStorage(agentResult.history());
                                    lesson.put("agentHistory",history).put("phase",agentResult.phase());lesson.remove("pendingQuestion");if(agentResult.pending()!=null)lesson.put("pendingQuestion",agentResult.pending());if(agentResult.tree()!=null)lesson.put("planTree",agentResult.tree());if(agentResult.assessment()!=null)lesson.put("assessment",agentResult.assessment());
                                    for(int i=0;i<agentResult.transcript().length();i++)lesson.getJSONArray("messages").put(agentResult.transcript().get(i));
                                }
                                complete.accept(reply);records.save();

                            }
                            catch (Exception e) { records.root = before; throw e; }
                            try{if(new JobStore(this).activate(records.rows("sessions")))JobService.wake(this);}catch(Exception jobError){error(jobError);}
                            prefs.edit().putLong("inputTokens", prefs.getLong("inputTokens", 0) + reply.inputTokens()).putLong("outputTokens", prefs.getLong("outputTokens", 0) + reply.outputTokens()).apply();
                            captureDraft(); render();
                        } catch (Exception e) { render(); error(e); }
                    });
                } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed() && generation == requestGeneration) { busy = false;streamText="";streamHandler.removeCallbacks(paintStream);streamPosted=false; captureDraft(); render(); error(e); } }); }
            });
        } catch (Exception e) { error(e); }
    }
    private void queueStream(int generation,String text){
        // Coalesce on the main thread, so queued previews never retain every token snapshot.
        if(generation!=requestGeneration)return;
        synchronized(this){pendingStream=text;streamGeneration=generation;if(streamPosted)return;streamPosted=true;}
        streamHandler.postDelayed(paintStream,eink?1000:100);
    }
    private JSONArray oneMessage(String text) throws JSONException { return new JSONArray().put(new JSONObject().put("role", "user").put("content", text)); }
    private void saveExtractionCheckpoint(int generation,JSONObject graph,JSONObject view,JSONObject usage,int batch,int total)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);Exception[] failure={null};runOnUiThread(()->{try{
            if(isDestroyed() || generation!=requestGeneration)throw new InterruptedException();JSONObject before=new JSONObject(records.root.toString());try{GraphRecords.apply(records,graph,view);JSONArray runs=records.root.optJSONArray("extractionRuns");if(runs==null){runs=new JSONArray();records.root.put("extractionRuns",runs);}runs.put(usage);records.save();}catch(Exception error){records.root=before;throw error;}
            operation="Extracting knowledge · "+batch+" / "+total;if(status!=null)status.setText(operation);
        }catch(Exception error){failure[0]=error;}finally{latch.countDown();}});if(!latch.await(15,TimeUnit.SECONDS))throw new IOException("Could not save the completed extraction batch.");if(failure[0]!=null)throw failure[0];
    }
    private void extractNote(JSONObject note){
        if(!connectionIdle())return;try{JSONObject task=new JSONObject().put("_operation","extraction").put("notes",new JSONObject().put("id","src_note_"+note.getString("id")).put("title",note.getString("title")).put("text",note.getString("statement"))).put("sessions",new JSONArray(records.rows("sessions").toString())).put("graph",GraphRecords.snapshot(records)).put("budget",prefs.getInt("extractionChunkTokens",4096));run("Extracting knowledge","",new JSONArray(),task,reply->{if(reply.nativeOutput()!=null){GraphRecords.apply(records,reply.nativeOutput().getJSONObject("graph"),reply.nativeOutput().getJSONObject("view"));records.root.put("graphSnapshot",reply.nativeOutput().getJSONObject("snapshot"));}Toast.makeText(this,reply.text(),Toast.LENGTH_LONG).show();});}catch(Exception error){error(error);}
    }
    private void extract(JSONObject session) {
        try{
            JSONObject task=new JSONObject().put("_operation","extraction").put("session",new JSONObject(session.toString())).put("sessions",new JSONArray(records.rows("sessions").toString())).put("graph",GraphRecords.snapshot(records)).put("budget",prefs.getInt("extractionChunkTokens",4096));
            run("Extracting knowledge","",new JSONArray(),task,reply->{if(reply.nativeOutput()!=null){GraphRecords.apply(records,reply.nativeOutput().getJSONObject("graph"),reply.nativeOutput().getJSONObject("view"));records.root.put("graphSnapshot",reply.nativeOutput().getJSONObject("snapshot"));}Toast.makeText(this,reply.text(),Toast.LENGTH_LONG).show();});
        }catch(Exception error){error(error);}
    }
    private void practice() throws Exception {
        JSONObject set = records.find("practice", selectedPractice);
        if (set == null) {
            card("Make understanding usable", "Work through one problem at a time. Share your thinking, ask for a hint, and discuss the verdict before moving on.");
            content.addView(primaryButton("New practice session",this::newPractice));
            JSONArray sessions=records.rows("sessions");for(int i=sessions.length()-1;i>=0;i--){JSONObject practice=sessions.getJSONObject(i);if(!practice.optString("kind").equals("practice"))continue;LinearLayout entry=card(practice.getString("title"),practice.optJSONObject("pendingQuestion")==null?"Practice session":"Problem in progress");entry.setClickable(true);entry.setFocusable(true);entry.setOnClickListener(v->{screen="Learn";selectedSession=practice.optString("id");draft="";render();});}
            JSONArray sets = records.rows("practice");
            if (sets.length() == 0) card("No practice sets yet", "Save questions once and revisit them offline. AI feedback requires your provider connection.");
            for (int i = sets.length()-1; i >= 0; i--) {
                JSONObject s = sets.getJSONObject(i);
                LinearLayout c = card(s.getString("title"), s.getJSONArray("questions").length() + " questions");
                c.setOnClickListener(v->{selectedPractice=s.optString("id");questionIndex=0;draft="";render();});c.setClickable(true);c.setFocusable(true);c.setContentDescription("Open practice: "+s.optString("title"));
            }
            return;
        }

        JSONArray questions = set.getJSONArray("questions");
        questionIndex = Math.max(0, Math.min(questionIndex, questions.length()-1));
        JSONObject q = questions.getJSONObject(questionIndex);

        card("QUESTION " + (questionIndex+1) + " / " + questions.length(), q.getString("question"));
        EditText answer = input("Your answer and reasoning…", q.optString("response"), true); content.addView(answer);
        answer.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { try { q.put("response", s.toString()); } catch(Exception ignored) {} }
            public void afterTextChanged(android.text.Editable s) {}
        });

        LinearLayout help=row();help.addView(button(q.optBoolean("showHint")?"Hide hint":"Hint",()->{try{q.put("showHint",!q.optBoolean("showHint"));save();render();}catch(Exception e){error(e);}}),new LinearLayout.LayoutParams(0,-2,1));
        help.addView(button(q.optBoolean("revealed")?"Hide answer":"Model answer",()->{try{q.put("revealed",!q.optBoolean("revealed"));save();render();}catch(Exception e){error(e);}}),new LinearLayout.LayoutParams(0,-2,1));content.addView(help);
        if (q.optBoolean("showHint")) card("Hint", q.getString("hint"));

        if (q.optBoolean("revealed")) card("Model answer · check critically", q.getString("answer"));
        MaterialButton feedback = primaryButton("Check my reasoning", () -> {
            String response = answer.getText().toString().trim();
            if (response.isEmpty()) { answer.setError("Write an answer first"); return; }
            try {
                save();
                JSONObject discussion=legacyPracticeDiscussion(set,q,response);
                run("Reviewing your reasoning",originalPrompt("practice"),discussion.getJSONArray("agentHistory"),discussion,reply->{StringBuilder feedbackText=new StringBuilder();JSONArray turns=discussion.getJSONArray("messages");for(int i=2;i<turns.length();i++)if(turns.getJSONObject(i).optString("role").equals("assistant"))feedbackText.append(turns.getJSONObject(i).optString("content")).append("\n\n");q.put("feedback",feedbackText.toString().trim());});
            } catch(Exception e) { error(e); }
        }); feedback.setEnabled(!busy); content.addView(feedback);
        if (!q.optString("feedback").isEmpty()) card("Feedback", q.getString("feedback"));
        if(busy && selectedPractice.equals(streamPractice) && questionIndex==streamQuestion){LinearLayout preview=card("Feedback · writing…","");liveReply=label("",fontSize,false);markdown.setMarkdown(liveReply,streamText.isEmpty()?"Waiting for the first words…":streamText);preview.addView(liveReply);}
        preference("Self review",q.optString("review","Not reviewed"),0,()->materialDialog().setTitle("Review your understanding").setItems(new String[]{"Needs work","Understood"},(d,w)->{try{q.put("review",new String[]{"Needs work","Understood"}[w]);save();render();}catch(Exception e){error(e);}}).show());
        LinearLayout pages = row();
        MaterialButton previous = button("← Previous", () -> { save(); questionIndex--; render(); }); previous.setEnabled(questionIndex > 0);
        MaterialButton next = button("Next →", () -> { save(); questionIndex++; render(); }); next.setEnabled(questionIndex < questions.length()-1);
        pages.addView(previous, new LinearLayout.LayoutParams(0, -2, 1)); pages.addView(next, new LinearLayout.LayoutParams(0, -2, 1)); content.addView(pages);
        menuAction("Delete practice set", () -> confirm("Delete this practice set and saved answers?", () -> { if (busy) { error(new IOException("Wait for or cancel the active request first.")); return; } records.remove("practice", selectedPractice); selectedPractice = ""; save(); render(); }));
    }
    private JSONObject legacyPracticeDiscussion(JSONObject set,JSONObject question,String response)throws Exception{
        JSONObject args=new JSONObject().put("statement",question.getString("question")).put("mode","recall").put("modelSolution",question.getString("answer")),call=ToolProtocol.call("legacy_problem_"+Records.id(),"problem",args.toString());
        JSONObject active=new JSONObject(args.toString()).put("id",call.getString("id")).put("kind","problem").put("call",call);
        JSONArray history=oneMessage(set.optString("title"));history.put(ToolProtocol.assistant(new ProviderClient.Reply("",0,0,new JSONArray().put(call),null))).put(ToolProtocol.output(call,practiceMarker("posed"))).put(new JSONObject().put("role","user").put("content",practiceMarker("thinking")+":\n"+response));
        return new JSONObject().put("kind","practice").put("phase","Practice").put("pendingQuestion",active).put("agentHistory",history).put("messages",new JSONArray().put(new JSONObject().put("role","assistant").put("content",question.getString("question"))).put(new JSONObject().put("role","user").put("content",response)));
    }
    private String practiceMarker(String name)throws Exception{
        try(InputStream in=getAssets().open("core/practice-markers.json")){return new JSONObject(new String(ProviderClient.readBounded(in,20000),StandardCharsets.UTF_8)).getString(name);}
    }
    private void dismissPractice(JSONObject session){
        if(busy)return;try{JSONObject active=session.getJSONObject("pendingQuestion"),call=ToolProtocol.call("call_conc_"+active.getString("id"),"conclude",new JSONObject().put("outcome","dismissed").toString());
            JSONArray history=session.getJSONArray("agentHistory");history.put(ToolProtocol.assistant(new ProviderClient.Reply("",0,0,new JSONArray().put(call),null)));history.put(ToolProtocol.output(call,practiceMarker("conclude")));
            session.getJSONArray("messages").put(new JSONObject().put("role","user").put("content","Problem dismissed").put("practiceDismissal",active.getString("id")));session.remove("pendingQuestion");captureDraft();save();render();
        }catch(Exception error){error(error);}
    }
    private void newPractice(){if(connectionIdle())showSessionCreator(true,"");}
    private void library() throws Exception {
        content.addView(primaryButton("Add to library",()->materialDialog().setTitle("Add to library").setItems(new String[]{"Link book or document","Link a book folder","Write a knowledge note"},(d,w)->{if(w==0)pickDocument("");else if(w==1)pickTree();else noteDialog("","");}).show()));
        JSONArray trees=records.root.optJSONArray("sourceTrees");for(int i=0;trees!=null && i<trees.length();i++){JSONObject tree=trees.getJSONObject(i);preference(tree.getString("title"),"Folder · browse files when needed",R.drawable.ic_book,()->treeMenu(tree));}
        JSONArray documents=records.rows("documents");
        for(int i=0;i<documents.length();i++){JSONObject doc=documents.getJSONObject(i);preference(doc.getString("title"),doc.getString("kind")+" · file reference · read only when approved",R.drawable.ic_book,()->documentMenu(doc));}
        if(documents.length()==0)content.addView(label("Link PDF, EPUB, text or Markdown. Books stay in Files; search or read selected pages when needed.",14,false));
        preference("Interactive templates","OpenIntelligentUI · import or open reusable interfaces",R.drawable.ic_learn,this::templateMenu);
        preference("Knowledge map","Propositions, reasoning chains and source passages",R.drawable.ic_learn,()->{if(connectionIdle())startActivityForResult(new Intent(this,GraphActivity.class),OPEN_GRAPH);});
        content.addView(label("Knowledge claims",14,true));
        menuAction("Import notes",()->chooseFile(IMPORT_NOTES,"*/*"));
        EditText search = input("Search claims and premises", "", false); content.addView(search);
        LinearLayout list = column(); content.addView(list);
        Runnable update = () -> {
            list.removeAllViews(); String query = search.getText().toString().toLowerCase(Locale.ROOT);
            JSONArray rows = records.rows("knowledge"); int shown = 0;
            for (int i = rows.length()-1; i >= 0; i--) {
                JSONObject k = rows.optJSONObject(i);
                if (!(k.optString("title") + " " + k.optString("statement") + " " + k.optJSONArray("premises")).toLowerCase(Locale.ROOT).contains(query)) continue;
                shown++;
                LinearLayout c = column(); c.setPadding(dp(14), dp(12), dp(14), dp(12)); c.setBackground(outline(1));
                LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(-1, -2); margin.setMargins(0, dp(10), 0, dp(4)); list.addView(c, margin);
                c.addView(richLabel(k.optString("title"), 19, true));
                c.addView(richLabel(k.optString("statement"), fontSize, false));
                JSONArray premises = k.optJSONArray("premises");
                if (premises != null && premises.length() > 0) {
                    c.addView(label("DEPENDS ON", 12, true));
                    for (int j = 0; j < premises.length(); j++) {
                        String premise = premises.optString(j); c.addView(richLabel("↳ " + premise, fontSize, false));
                        for (int n = 0; n < rows.length(); n++) {
                            JSONObject candidate = rows.optJSONObject(n);
                            if (!candidate.optString("id").equals(k.optString("id")) && (candidate.optString("statement").equalsIgnoreCase(premise) || candidate.optString("title").equalsIgnoreCase(premise))) {
                                c.addView(button("Find premise: " + candidate.optString("title"), () -> search.setText(candidate.optString("title")))); break;
                            }
                        }
                    }
                }
                TextView state=label(k.optString("status","Unreviewed"),12,true);state.setTextColor(subtle);c.addView(state);
                MaterialButton options=button("Claim options",()->materialDialog().setTitle(k.optString("title")).setItems(new String[]{"Review claim","Open source session","Edit claim","Delete claim","Extract this note into the map"},(d,w)->{
                    if(w==0)materialDialog().setTitle("Review this claim").setItems(new String[]{"Unreviewed","Reviewed","Needs correction"},(dialog,index)->{try{k.put("status",new String[]{"Unreviewed","Reviewed","Needs correction"}[index]);save();render();}catch(Exception e){error(e);}}).show();
                    if(w==1){JSONObject source=records.find("sessions",k.optString("source"));if(source==null){error(new IOException("The source session is not available."));return;}selectedSession=source.optString("id");screen="Learn";draft="";render();}
                    if(w==2)editClaim(k);
                    if(w==4)extractNote(k);
                    if(w==3)confirm("Delete this knowledge claim?",()->{deleteClaim(k);});
                }).show());options.setIconResource(R.drawable.ic_more);c.addView(options);
            }
            if (shown == 0) list.addView(label(query.isEmpty() ? "Your knowledge library is empty. Extract claims from a lesson or add notes." : "No matching claims.", fontSize, false));
        };
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { update.run(); }
            public void afterTextChanged(android.text.Editable s) {}
        }); update.run();
    }
    private void noteDialog(String title, String text) {
        LinearLayout form = column(); form.setPadding(dp(20), dp(8), dp(20), 0);
        EditText name = input("Note title", title, false), body = input("Claim or imported notes", text, true); form.addView(name); form.addView(body);
        materialDialog().setTitle("Add knowledge").setView(form).setNegativeButton("Cancel", null).setPositiveButton("Save locally", (d,w) -> {
            try {
                if (body.getText().toString().trim().isEmpty()) return;
                records.rows("knowledge").put(new JSONObject().put("id", Records.id()).put("title", name.getText().toString().trim().isEmpty() ? "Imported note" : name.getText().toString().trim())
                    .put("statement", body.getText().toString().trim()).put("premises", new JSONArray()).put("status", "Unreviewed").put("source", "")); save(); render();
            } catch(Exception e) { error(e); }
        }).show();
    }
    private void editClaim(JSONObject claim) {
        LinearLayout form = column(); form.setPadding(dp(20), dp(8), dp(20), 0);
        EditText title = input("Claim title", claim.optString("title"), false);
        EditText statement = input("Statement", claim.optString("statement"), true);
        StringBuilder premises = new StringBuilder(); JSONArray previous = claim.optJSONArray("premises");
        for (int i = 0; previous != null && i < previous.length(); i++) premises.append(previous.optString(i)).append('\n');
        EditText dependencies = input("Premises, one per line", premises.toString().trim(), true);
        form.addView(title); form.addView(statement); form.addView(dependencies);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        materialDialog().setTitle("Edit knowledge claim").setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Save", (d,w) -> {
            try {
                if (title.getText().toString().trim().isEmpty() || statement.getText().toString().trim().isEmpty()) return;
                JSONArray values = new JSONArray(); for (String line : dependencies.getText().toString().split("\n")) if (!line.trim().isEmpty()) values.put(line.trim());
                if(!connectionIdle())return;
                JSONObject before=new JSONObject(records.root.toString());try{GraphRecords.edit(records,claim,title.getText().toString().trim(),statement.getText().toString().trim(),values);records.save();}catch(Exception error){records.root=before;throw error;}render();
            } catch(Exception e) { error(e); }
        }).show();
    }
    private boolean deliverJobs()throws Exception{
        if(records==null || busy)return false;JobStore jobs=new JobStore(this);JSONArray rows=jobs.list();boolean changed=false,wakeSelected=false;JSONObject before=new JSONObject(records.root.toString());ArrayList<String> delivered=new ArrayList<>();
        try{for(int i=0;i<rows.length();i++){JSONObject job=rows.getJSONObject(i);if(job.optBoolean("delivered") || job.optJSONObject("steer")==null)continue;JSONObject lesson=records.find("sessions",job.optString("chatId"));if(lesson==null)continue;JSONObject pending=lesson.optJSONObject("pendingQuestion");if(pending!=null && !pending.optString("kind").equals("problem"))continue;
            JSONArray messages=lesson.getJSONArray("messages");boolean exists=false;for(int j=0;j<messages.length();j++)if(messages.getJSONObject(j).optString("jobId").equals(job.getString("id")))exists=true;if(!exists){JSONObject steer=job.getJSONObject("steer");JSONArray history=lesson.optJSONArray("agentHistory");if(history==null){history=new JSONArray(messages.toString());lesson.put("agentHistory",history);}history.put(new JSONObject().put("role","user").put("content",steer.getString("content")).put("jobId",job.getString("id")));String brief=job.optJSONObject("result")==null?job.optString("activity"):job.getJSONObject("result").optString("brief");messages.put(new JSONObject().put("role","assistant").put("content",brief).put("origin","tool").put("jobId",job.getString("id")));changed=true;if(lesson.optString("id").equals(selectedSession))wakeSelected=true;}delivered.add(job.getString("id"));}
            if(changed)records.save();
        }catch(Exception error){records.root=before;throw error;}
        for(String id:delivered)jobs.update(id,new JSONObject().put("delivered",true));return wakeSelected;
    }
    private void showJobs(){
        try{JSONArray jobs=new JobStore(this).list();if(jobs.length()==0){materialDialog().setTitle("Background jobs").setMessage("No jobs yet. Configure Web research to let the tutor dispatch sourced research when needed.").setPositiveButton("Done",null).show();return;}String[] labels=new String[jobs.length()];for(int i=0;i<labels.length;i++){JSONObject job=jobs.getJSONObject(i);labels[i]=job.optString("kind")+" · "+job.optString("status")+"\n"+job.optString("activity");}
            materialDialog().setTitle("Background jobs").setItems(labels,(dialog,index)->{JSONObject job=jobs.optJSONObject(index);String status=job.optString("status");androidx.appcompat.app.AlertDialog.Builder detail=materialDialog().setTitle(job.optString("kind")+" · "+status).setMessage(job.optString("activity")+"\nInput tokens: "+job.optLong("inputTokens")+" · output: "+job.optLong("outputTokens")+"\n\n"+(job.optJSONObject("result")==null?"":job.optJSONObject("result").optString("brief"))).setNegativeButton("Close",null);
                if(status.equals("running") || status.equals("queued"))detail.setPositiveButton("Cancel job",(d,w)->startService(new Intent(this,JobService.class).setAction(JobService.CANCEL).putExtra("job",job.optString("id"))));
                else if(status.equals("error") || status.equals("interrupted") || status.equals("cancelled"))detail.setPositiveButton("Retry explicitly",(d,w)->{try{new JobStore(this).retry(job.getString("id"));JobService.wake(this);}catch(Exception error){error(error);}});
                detail.setNeutralButton("Remove record",(d,w)->{try{new JobStore(this).remove(job.getString("id"));showJobs();}catch(Exception error){error(error);}}).show();
            }).setNegativeButton("Close",null).show();
        }catch(Exception error){error(error);}
    }
    private void searchSettings()throws Exception{
        card("Web research","ChatGPT plan search uses the researcher's selected ChatGPT account and model, with no separate search key. Availability depends on the model and account policy. Research stays isolated and uses the original instructions. A failure never switches to another provider.");
        String selected=SearchSettings.engine(prefs);preference("Active search engine",SearchSettings.label(selected),0,()->materialDialog().setTitle("Search engine").setItems(new String[]{"ChatGPT plan · no separate key","Disabled","Tavily · search API key","Brave · search API key"},(d,index)->{if(!connectionIdle())return;try{String engine=new String[]{"chatgpt","","tavily","brave"}[index];if(engine.equals("chatgpt") && !NativeModels.forRole(this,"researcher").getString("billing").equals("plan"))throw new IOException("Choose a ChatGPT plan connection for Researcher under AI connection → Models by role first.");prefs.edit().putString("searchEngine",engine).apply();render();}catch(Exception error){error(error);}}).show());
        if(selected.equals("chatgpt")){card("ChatGPT plan search",SearchSettings.available(this)?"Ready to use the researcher’s ChatGPT plan connection. Search and model work use that account’s allowance.":"Choose a connected ChatGPT plan account for Researcher under AI connection → Models by role.");}
        content.addView(button("Other search providers",()->materialDialog().setTitle("Optional search provider").setItems(new String[]{"Tavily","Brave"},(d,index)->{String engine=new String[]{"tavily","brave"}[index];try{EditText key=input(engine+" API key",secrets.get("search."+engine),false);key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);materialDialog().setTitle("Optional "+engine+" search").setMessage("Search-provider API billing is separate from ChatGPT plan usage. The key stays encrypted.").setView(key).setNegativeButton("Cancel",null).setPositiveButton("Save and use",(dialog,w)->{if(!connectionIdle())return;try{String value=key.getText().toString().trim();secrets.set("search."+engine,value);prefs.edit().putString("searchEngine",value.isEmpty()?"":engine).apply();render();}catch(Exception error){error(error);}}).show();}catch(Exception error){error(error);}}).setNegativeButton("Close",null).show()));
    }

    private void roleModelSettings()throws Exception{
        JSONObject routes=new JSONObject(prefs.getString("roleConnections","{}"));card("Models by role","Role overrides take precedence over their slot. Unset slots use your active connection. API selections use that provider’s saved encrypted key and separate API billing.");
        for(String slot:new String[]{"strong","cheap","vision"})preference(slot.substring(0,1).toUpperCase(Locale.ROOT)+slot.substring(1)+" slot",RoleConnections.label(routes.optJSONObject(slot)),0,()->chooseRoleConnection(slot));
        for(String role:RoleConnections.ROLES)preference(role,RoleConnections.label(routes.optJSONObject(role))+" · "+RoleConnections.slot(role)+" slot",0,()->chooseRoleConnection(role));
    }
    private void chooseRoleConnection(String role){
        if(!connectionIdle())return;try{
            ArrayList<String> labels=new ArrayList<>();ArrayList<JSONObject> configs=new ArrayList<>();labels.add("Inherit connection");configs.add(null);
            if(chatGpt!=null){JSONObject account=chatGpt.active();if(account!=null && account.optBoolean("connected") && account.optBoolean("plan") && !account.optString("model").isEmpty()){labels.add("ChatGPT plan · selected account");configs.add(new JSONObject().put("billing","plan").put("account",account.getString("client_id")).put("model",account.getString("model")).put("thinking",thinkingValue("ChatGPT",account.getString("client_id"),account.getString("model"))));}}
            for(String providerName:PROVIDERS){labels.add(providerName+" · separate API billing");String savedModel=prefs.getString("model."+providerName,"");String savedEndpoint=prefs.getString("endpoint."+providerName,endpoint(providerName));configs.add(new JSONObject().put("billing","api").put("provider",providerName).put("endpoint",savedEndpoint).put("model",savedModel).put("thinking",thinkingValue(providerName,savedEndpoint,savedModel)));}
            materialDialog().setTitle(role+" connection").setItems(labels.toArray(new String[0]),(d,index)->{try{JSONObject config=configs.get(index);if(config==null){JSONObject routes=new JSONObject(prefs.getString("roleConnections","{}"));routes.remove(role);prefs.edit().putString("roleConnections",routes.toString()).apply();render();}else editRoleConnection(role,config);}catch(Exception error){error(error);}}).setNegativeButton("Cancel",null).show();
        }catch(Exception error){error(error);}
    }
    private void editRoleConnection(String role,JSONObject config){
        LinearLayout form=column();form.setPadding(dp(20),dp(8),dp(20),dp(8));EditText model=input("Model ID",config.optString("model"),false);form.addView(model);form.addView(label(config.optString("billing").equals("plan")?"Uses the selected ChatGPT account’s allowance. Choose a model available to that account.":"Uses the saved "+config.optString("provider")+" API key. API billing is separate from your ChatGPT subscription.",13,false));
        materialDialog().setTitle(role+" model").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Thinking mode",(d,w)->{try{String id=model.getText().toString().trim();if(id.isEmpty())throw new IOException("Enter a model ID.");config.put("model",id);String providerName=config.optString("billing").equals("plan")?"ChatGPT":config.getString("provider");String[] modes=Thinking.options(providerName,id),labels=new String[modes.length];for(int i=0;i<modes.length;i++)labels[i]=Thinking.label(modes[i]);materialDialog().setTitle(role+" thinking mode").setItems(labels,(dialog,index)->{try{if(!connectionIdle())return;config.put("thinking",modes[index]);RoleConnections.validate(config);if(config.optString("billing").equals("api") && secrets.get(config.getString("provider")).trim().isEmpty())throw new IOException("Save this provider’s API key first in API provider settings.");JSONObject routes=new JSONObject(prefs.getString("roleConnections","{}"));routes.put(role,config);prefs.edit().putString("roleConnections",routes.toString()).apply();render();}catch(Exception error){error(error);}}).setNegativeButton("Cancel",null).show();}catch(Exception error){error(error);}}).show();
    }
    private static String endpoint(String provider) {
        switch(provider) {
            case "Anthropic": return "https://api.anthropic.com/v1";
            case "Gemini": return "https://generativelanguage.googleapis.com/v1beta";
            case "OpenRouter": return "https://openrouter.ai/api/v1";
            case "DeepSeek": return "https://api.deepseek.com/v1";
            default: return "https://api.openai.com/v1";
        }
    }
    private void settings() throws Exception {
        if(eink)content.setPadding(dp(24),0,dp(24),dp(16));
        if(settingsPage.isEmpty()) {
            String connection="Set up ChatGPT or an API provider";
            JSONObject account=chatGpt==null?null:chatGpt.active();
            if(account!=null && account.optBoolean("connected"))connection=prefs.getBoolean("useChatGpt",false)?"ChatGPT plan selected":"ChatGPT connected · choose billing";
            settingsGroup("Services");
            preference("Background jobs","Research activity, results and cancellation",R.drawable.ic_info,this::showJobs);
            preference("Web research","Tavily or Brave search API",R.drawable.ic_settings,()->settingsPage("Web research"));
            preference("AI connection",connection,R.drawable.ic_account,()->settingsPage("AI connection"));
            settingsGroup("Learning");
            preference("Knowledge map access",prefs.getBoolean("graphToolsEnabled",true)?"Probe and practice may look up the map":"Map stays local",R.drawable.ic_library,()->{prefs.edit().putBoolean("graphToolsEnabled",!prefs.getBoolean("graphToolsEnabled",true)).apply();render();});
            preference("Question style",prefs.getBoolean("freeRecall",false)?"Explain in your own words":"Multiple choice",R.drawable.ic_practice,()->materialDialog().setTitle("Question style").setItems(new String[]{"Multiple choice","Explain in your own words"},(d,w)->{prefs.edit().putBoolean("freeRecall",w==1).apply();render();}).show());
            settingsGroup("Personalisation");
            preference("Voice typing",voiceEngine(),R.drawable.ic_mic,()->settingsPage("Voice typing"));
            preference("Appearance",eink?"E-ink mode · "+fontSize+" sp":"Material You · "+fontSize+" sp",R.drawable.ic_palette,()->settingsPage("Appearance"));
            settingsGroup("Your data");
            preference("Data & backup","Notes, exports and local learning records",R.drawable.ic_backup,()->settingsPage("Data & backup"));
            preference("About","LearnHarness for Android",R.drawable.ic_info,()->settingsPage("About"));
            return;
        }
        switch(settingsPage) {
            case "AI connection":
                settingsGroup("Connection");
                preference("Active connection",prefs.getBoolean("useChatGpt",false)?"ChatGPT plan":prefs.getString("provider",PROVIDERS[0])+" · API",0,this::chooseConnection);
                chatGptSettings();
                settingsGroup("Providers and models");
                preference("API provider settings","Optional · separate API billing",R.drawable.ic_settings,()->settingsPage("API provider"));
                preference("Models by role","Strong, cheap and vision slots; individual role overrides",R.drawable.ic_settings,()->settingsPage("Models by role"));
                break;
            case "API provider": apiProviderSettings();break;
            case "Models by role":roleModelSettings();break;
            case "Web research":searchSettings();break;
            case "Appearance": appearanceSettings();break;
            case "Voice typing": voiceSettings();break;
            case "Data & backup":
                settingsGroup("Learning records");
                preference("Export learning backup","Sessions, knowledge and practice · excludes credentials",R.drawable.ic_backup,()->export(records.root.toString(),"learnharness-backup.json","application/json"));
                preference("Restore learning backup","Replace records from an Android backup",R.drawable.ic_backup,()->{if(connectionIdle())chooseFile(RESTORE,"application/json");});
                preference("Import notes","UTF-8 text or Markdown",R.drawable.ic_library,()->chooseFile(IMPORT_NOTES,"*/*"));
                preferenceGroup=null;card("Recorded token usage","Input: "+prefs.getLong("inputTokens",0)+"\nOutput: "+prefs.getLong("outputTokens",0)+"\nCompleted requests on this installation. Provider billing may also include cancelled requests.");
                break;
            default:
                card("LearnHarness","Version "+BuildConfig.VERSION_NAME+"\nBased on LearnHarness by Amos Blomqvist.\nAGPL-3.0-only");
                preference("Feature coverage","Android capabilities and remaining desktop features",R.drawable.ic_info,()->materialDialog().setTitle("Android edition").setMessage("Adaptive tutoring, practice, knowledge claims, notes, dictation and local backups.\n\nOriginal structured questions, grading, probe handoff and plan validation are supported. PDF/EPUB/text search and Agora interactive templates are available. Research agents, demo generation, images, full graph extraction and durable background jobs remain in development. AI generation requires an eligible ChatGPT account or a configured API provider.").setPositiveButton("OK",null).show());
                preference("Project and licence","Open upstream LearnHarness",R.drawable.ic_info,()->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/amosblomqvist/learnharness"))));
        }
    }
    private void settingsPage(String page){captureDraft();settingsPage=page;render();}
    private void appearanceSettings(){
        LinearLayout explanation=card("Display","Black text, white paper and a continuous chat. Reading arrows keep three lines of overlap between screenfuls.");
        MaterialSwitch toggle=new MaterialSwitch(this);toggle.setText("E-ink compatible mode");toggle.setTextColor(ink);toggle.setChecked(eink);toggle.setMinimumHeight(dp(56));toggle.setEnabled(!busy && (chatGpt==null || !chatGpt.isSigningIn()));
        if(eink){toggle.setThumbTintList(ColorStateList.valueOf(ink));toggle.setTrackTintList(ColorStateList.valueOf(paper));toggle.setTrackDecorationTintList(ColorStateList.valueOf(ink));}
        toggle.setOnCheckedChangeListener((button,checked)->{captureDraft();prefs.edit().putBoolean("eink",checked).apply();recreate();});explanation.addView(toggle);
        settingsGroup("Reading");
        preference("Reading size",fontSize+" sp",0,()->materialDialog().setTitle("Reading text size").setItems(new String[]{"16 sp","18 sp","20 sp","24 sp","28 sp"},(d,w)->{prefs.edit().putInt("fontSize",new int[]{16,18,20,24,28}[w]).apply();recreate();}).show());
        if(!eink)content.addView(label("Colours follow your device’s Material You palette when supported.",14,false));
    }
    private String voiceEngine(){return prefs.getString("voiceEngine",prefs.getBoolean("voiceOffline",false)?"On-device only":"Android speech");}
    private String voiceBase(String engine){return prefs.getString("voiceBase."+engine,engine.equals("OpenAI")?"https://api.openai.com/v1":engine.equals("Groq")?"https://api.groq.com/openai/v1":engine.equals("Deepgram")?"https://api.deepgram.com/v1":"");}
    private String voiceModel(String engine){return prefs.getString("voiceModel."+engine,engine.equals("OpenAI")?"gpt-4o-mini-transcribe":engine.equals("Groq")?"whisper-large-v3-turbo":engine.equals("Deepgram")?"nova-3":"whisper-1");}
    private boolean voiceUsesApi(){return java.util.Arrays.asList("OpenAI","Groq","Deepgram","Compatible API").contains(voiceEngine());}
    private void voiceSettings(){
        card("Voice typing","Tap the microphone to dictate into your draft. Review the text before sending.");
        settingsGroup("Recognition");
        String engine=voiceEngine();String[] engines={"Android speech","On-device only","OpenAI","Groq","Deepgram","Compatible API"};
        preference("Speech engine",engine,R.drawable.ic_mic,()->materialDialog().setTitle("Speech engine").setSingleChoiceItems(engines,java.util.Arrays.asList(engines).indexOf(engine),(dialog,which)->{prefs.edit().putString("voiceEngine",engines[which]).apply();dialog.dismiss();render();}).setNegativeButton("Cancel",null).show());
        preference("Language",prefs.getString("voiceLanguage","").isEmpty()?"Device language / automatic":prefs.getString("voiceLanguage",""),0,()->{
            EditText language=input("Language tag, e.g. en-IE",prefs.getString("voiceLanguage",""),false);
            materialDialog().setTitle("Speech language").setMessage("Leave blank for the device language (Android) or automatic detection (API). API transcription uses the language code, such as en.").setView(language).setPositiveButton("Save",(dialog,which)->{String tag=language.getText().toString().trim();if(tag.length()>35 || !tag.isEmpty() && java.util.Locale.forLanguageTag(tag).getLanguage().isEmpty()){error(new IOException("Enter a language tag such as en-IE."));return;}prefs.edit().putString("voiceLanguage",tag).apply();render();}).setNegativeButton("Cancel",null).show();
        });
        if(engine.equals("Deepgram"))preference("Deepgram data use",prefs.getBoolean("deepgramOptOut",true)?"Excluded from model improvement":"Provider defaults",0,()->materialDialog().setTitle("Deepgram data use").setMessage("Deepgram may apply different pricing when recordings are excluded from model improvement.").setSingleChoiceItems(new String[]{"Exclude recordings from model improvement","Use provider defaults"},prefs.getBoolean("deepgramOptOut",true)?0:1,(dialog,which)->{prefs.edit().putBoolean("deepgramOptOut",which==0).apply();dialog.dismiss();render();}).setNegativeButton("Cancel",null).show());
        preferenceGroup=null;
        if(voiceUsesApi()){
            content.addView(label("Transcription API",16,true));
            content.addView(label("Only your recorded audio is uploaded to this provider. API transcription is billed separately from your ChatGPT plan.",14,false));
            content.addView(label("HTTPS base URL",13,true));EditText base=input("https://provider.example/v1",voiceBase(engine),false);content.addView(base);
            content.addView(label("Transcription model",13,true));EditText model=input("Model ID",voiceModel(engine),false);content.addView(model);
            String stored="";try{stored=secrets.get("Voice "+engine);}catch(Exception error){error(error);}
            content.addView(label("Voice API key",13,true));EditText key=input(engine.equals("Compatible API")?"Optional for unauthenticated endpoints":"API key",stored,false);key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);content.addView(key);
            content.addView(primaryButton("Save voice API settings",()->{try{
                VoiceApi.url(base.getText().toString(),engine.equals("Deepgram"));String name=model.getText().toString().trim();if(name.isEmpty() || name.length()>200)throw new IOException("Enter a transcription model ID.");
                secrets.set("Voice "+engine,key.getText().toString().trim());prefs.edit().putString("voiceBase."+engine,base.getText().toString().trim()).putString("voiceModel."+engine,name).apply();Toast.makeText(this,"Voice API settings saved",Toast.LENGTH_SHORT).show();render();
            }catch(Exception error){error(error);}}));
        }else content.addView(label(engine.equals("On-device only")?"Requires Android 12+ and an installed offline speech service and language model. It never falls back to online speech.":"Uses your configured Android speech service, which may process speech online.",14,false));
    }
    private void apiProviderSettings() throws Exception {
        content.addView(label("API usage is billed separately from ChatGPT. Saving these credentials does not change your selected billing path.",14,false));
        String selected=prefs.getString("provider",PROVIDERS[0]);
        TextInputLayout providerField=new TextInputLayout(this,null,com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);providerField.setHint("Provider");
        com.google.android.material.textfield.MaterialAutoCompleteTextView select=new com.google.android.material.textfield.MaterialAutoCompleteTextView(providerField.getContext());select.setInputType(InputType.TYPE_NULL);select.setAdapter(spinnerAdapter(PROVIDERS));select.setText(selected,false);select.setTextColor(ink);providerField.addView(select);content.addView(providerField);
        EditText base = input("HTTPS API base URL", prefs.getString("endpoint", endpoint(selected)), false); content.addView(base);
        EditText model = input("Model ID from your provider", prefs.getString("model", ""), false); content.addView(model);
        EditText key = input("API key (stored encrypted)", secrets.get(selected), false); key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); key.setTextSize(16); content.addView(key);
        select.setOnItemClickListener((parent,view,position,id)->{
            String next=PROVIDERS[position];
            base.setText(prefs.getString("endpoint."+next,endpoint(next)));model.setText(prefs.getString("model."+next,""));
            try{key.setText(secrets.get(next));}catch(Exception e){key.setText("");error(e);}
        });
        action("Use saved API provider instead", () -> {
            if(busy || chatGpt!=null && chatGpt.isSigningIn()){error(new IOException("Finish or cancel the current request or sign-in first."));return;}
            prefs.edit().putBoolean("useChatGpt",false).apply();Toast.makeText(this,"API provider selected",Toast.LENGTH_SHORT).show();captureDraft();render();
        });
        content.addView(primaryButton("Save provider settings", () -> {
            if(!connectionIdle())return;
            try {
                String p = select.getText().toString(), url = base.getText().toString().trim(), m = model.getText().toString().trim();
                java.net.URI parsed = new java.net.URI(url);
                if (!"https".equals(parsed.getScheme()) || parsed.getHost() == null || parsed.getUserInfo() != null || parsed.getQuery() != null || parsed.getFragment() != null) throw new IOException("Enter an HTTPS base URL without credentials, query, or fragment.");
                secrets.set(p, key.getText().toString().trim());
                prefs.edit().putString("provider", p).putString("endpoint", url).putString("model", m).putString("endpoint."+p, url).putString("model."+p, m).apply();
                Toast.makeText(this, "Provider settings saved", Toast.LENGTH_SHORT).show();render();
            } catch(Exception e) { error(e); }
        }));
        MaterialButton load=button("Browse provider models",()->{});
        load.setOnClickListener(v->{
            if(!connectionIdle())return;
            String p=select.getText().toString(),url=base.getText().toString().trim();
            ProviderClient.Config config=new ProviderClient.Config(p,url,"",key.getText().toString().trim());
            busy=true;operation="Loading provider models";int generation=++requestGeneration;load.setEnabled(false);load.setText("Loading models…");
            worker.submit(()->{
                try {
                    JSONArray catalog=provider.models(config);
                    runOnUiThread(()->{
                        if(isDestroyed() || generation!=requestGeneration)return;
                        busy=false;load.setEnabled(true);load.setText("Browse provider models");
                        if(!model.isAttachedToWindow() || !p.equals(select.getText().toString()) || !url.equals(base.getText().toString().trim()))return;
                        String[] labels=new String[catalog.length()];for(int i=0;i<labels.length;i++){JSONObject item=catalog.optJSONObject(i);labels[i]=item.optString("name")+"\n"+item.optString("id");}
                        materialDialog().setTitle("Provider models").setItems(labels,(d,w)->model.setText(catalog.optJSONObject(w).optString("id"))).setNegativeButton("Cancel",null).show();
                    });
                }catch(Exception e){runOnUiThread(()->{if(!isDestroyed() && generation==requestGeneration){busy=false;load.setEnabled(true);load.setText("Browse provider models");error(e);}});}
            });
        });content.addView(load);
        String savedModel=prefs.getString("model","");
        if(!savedModel.isEmpty())thinkingPreference(selected,prefs.getString("endpoint",endpoint(selected)),savedModel);
        content.addView(label("Save a model to configure its thinking. Catalog entries may include models without text generation; check your provider’s model capabilities. Model IDs are editable so you can use models available to your account. OpenAI-compatible services use the Chat Completions API. No API keys are bundled.", 14, false));
        action("Test saved provider connection", () -> { try { if(prefs.getBoolean("useChatGpt",false))throw new IOException("Select the saved API provider before testing it. ChatGPT plan is currently active.");run("Testing provider connection", "Reply briefly.", oneMessage("Say: Connection successful."), reply -> materialDialog().setTitle("Provider connected").setMessage(reply.text()).setPositiveButton("OK", null).show()); } catch(Exception e) { error(e); } });
    }

    @Override protected void onStart() {
        super.onStart();
        if(chatGpt!=null)chatGpt.observe(() -> { if(!isDestroyed() && records!=null) { captureDraft();render();showChatGptNotice(); } });
    }
    @Override protected void onResume() { super.onResume();if(records!=null)try{if(!JobService.isLive())new JobStore(this).interrupted();deliverJobs();if(new JobStore(this).activate(records.rows("sessions")))JobService.wake(this);}catch(Exception error){error(error);} if(chatGpt!=null && records!=null) { captureDraft();render();showChatGptNotice(); } }
    @Override protected void onStop() { cancelVoiceTyping();if(chatGpt!=null)chatGpt.observe(null); super.onStop(); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent);if(intent.getData()!=null && BuildConfig.SIGN_IN_SCHEME.equals(intent.getData().getScheme()) && "signin-complete".equals(intent.getData().getHost())){screen="Settings";settingsPage="AI connection";intent.setData(null);if(records!=null)render();}if(records!=null)receiveSharedContent(intent); }
    private void showChatGptNotice() {
        String notice=chatGpt.takeNotice();
        if(chatGpt.takeWelcome()) {
            materialDialog().setTitle("You’re using your ChatGPT plan")
                .setMessage("Eligible requests can use your ChatGPT plan once you choose a model and enable it below. Manage app access and usage in ChatGPT settings. Plus usage is shared across apps.\n\n"+notice)
                .setPositiveButton("Got it",null).setNeutralButton("Manage usage",(d,w)->openUsage()).show();
        } else if(!notice.isEmpty())materialDialog().setTitle("ChatGPT connection").setMessage(notice).setPositiveButton("OK",null).show();
    }
    private void openUsage() { try { startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(OpenAiHttp.USAGE))); } catch(ActivityNotFoundException e) { error(new IOException("Install a browser to view ChatGPT usage settings.")); } }
    private boolean connectionIdle() {
        if(busy || chatGpt!=null && chatGpt.isSigningIn()) { error(new IOException("Finish or cancel the active request or sign-in first.")); return false; } return true;
    }
    private void startChatGptSignIn(String client,boolean consent) {
        if(!connectionIdle())return;
        try {
            String url=chatGpt.startSignIn(client,consent);
            try { startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url))); }
            catch(ActivityNotFoundException e) { chatGpt.cancelSignIn(); throw new IOException("Install a browser to sign in with ChatGPT."); }
        } catch(Exception e) { error(new IOException(ChatGptAccounts.safeError(e))); }
    }
    private interface AccountWork { String run() throws Exception; }
    private void accountWork(String title,AccountWork work) {
        if(!connectionIdle())return;
        busy=true;operation=title;int generation=++requestGeneration;captureDraft();render();
        worker.submit(() -> {
            try {
                String message=work.run();
                runOnUiThread(() -> { if(!isDestroyed() && generation==requestGeneration) { busy=false;captureDraft();render();if(!message.isEmpty())materialDialog().setTitle("ChatGPT connection").setMessage(message).setPositiveButton("OK",null).show(); } });
            } catch(Exception e) { runOnUiThread(() -> { if(!isDestroyed() && generation==requestGeneration) { busy=false;captureDraft();render();error(new IOException(ChatGptAccounts.safeError(e))); } }); }
        });
    }
    private void chatGptSettings() throws Exception {
        if(chatGpt==null){card("ChatGPT unavailable","Credential storage could not be opened on this device.");return;}
        JSONObject active=chatGpt.active();
        boolean connected=active!=null && active.optBoolean("connected"),plan=connected && active.optBoolean("plan");
        card(connected?"ChatGPT connected":"Connect ChatGPT",connected?active.optString("label"):"Use eligible requests from your existing ChatGPT plan. Your plan’s normal usage limits apply.");
        if(chatGpt.isSigningIn()) {
            card("Waiting for your browser","Complete sign-in and return to LearnHarness. The connection result will appear here.");
            content.addView(primaryButton("Cancel sign-in",chatGpt::cancelSignIn));
        } else if(!connected) {
            String retry=chatGpt.interruptedClient();
            MaterialButton signIn=primaryButton(retry.isEmpty()?"Continue with ChatGPT":"Retry ChatGPT sign-in",()->startChatGptSignIn(!retry.isEmpty()?retry:active==null?"":active.optString("client_id"),false));signIn.setEnabled(!busy);content.addView(signIn);
        } else if(!plan)content.addView(primaryButton("Enable ChatGPT plan access",()->startChatGptSignIn(active.optString("client_id"),true)));
        if(plan) {
            String id=active.getString("client_id");JSONArray models=active.getJSONArray("models");String chosen=active.optString("model"),display="Choose an available model";
            for(int i=0;i<models.length();i++)if(models.getJSONObject(i).optString("slug").equals(chosen))display=models.getJSONObject(i).optString("display_name");
            preference("Model",display,0,()->{
                if(!connectionIdle())return;
                if(models.length()==0){accountWork("Loading available models",()->{chatGpt.refreshModels(id,chatGptHttp);return "Models loaded. Tap Model to choose one.";});return;}
                String[] labels=new String[models.length()];for(int i=0;i<labels.length;i++)labels[i]=models.optJSONObject(i).optString("display_name");
                materialDialog().setTitle("ChatGPT model").setItems(labels,(d,w)->{try{chatGpt.model(id,models.getJSONObject(w).getString("slug"));render();}catch(Exception e){error(e);}}).show();
            });
            if(!chosen.isEmpty())thinkingPreference("ChatGPT",id,chosen);
            MaterialSwitch use=new MaterialSwitch(this);use.setText("Use ChatGPT plan");use.setTextColor(ink);use.setChecked(prefs.getBoolean("useChatGpt",false));use.setMinimumHeight(dp(64));use.setEnabled(!busy && !chatGpt.isSigningIn());
            if(eink){use.setThumbTintList(ColorStateList.valueOf(ink));use.setTrackTintList(ColorStateList.valueOf(paper));use.setTrackDecorationTintList(ColorStateList.valueOf(ink));}
            use.setOnCheckedChangeListener((button,enabled)->{if(enabled && chosen.isEmpty()){use.setChecked(false);error(new IOException("Choose a ChatGPT model first."));return;}prefs.edit().putBoolean("useChatGpt",enabled).apply();render();});content.addView(use);
        }
        if(chatGpt.accounts().length()>0)preference("Manage account",connected?"Accounts, model refresh and sign-out":"Choose another saved account",0,this::manageChatGptAccount);
        preference("Usage and limits","Manage this app’s access in ChatGPT",0,this::openUsage);
        preference("Connection details","Last sign-in result · no credentials included",0,()->{
            TextView text=label(chatGpt.diagnosticSummary(),13,false);text.setTextIsSelectable(true);text.setPadding(dp(20),dp(12),dp(20),dp(12));ScrollView scroll=new ScrollView(this);scroll.addView(text);
            materialDialog().setTitle("ChatGPT connection history").setView(scroll).setPositiveButton("Done",null).setNeutralButton("Copy details",(d,w)->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("LearnHarness connection details",chatGpt.diagnosticSummary()));}).show();
        });
        content.addView(label(prefs.getBoolean("useChatGpt",false)?"ChatGPT plan is selected. If it fails, requests stop here.":"API provider is selected. Enable ChatGPT plan after connecting to use your subscription.",13,false));
    }
    private void chooseConnection(){
        String api=prefs.getString("provider",PROVIDERS[0]);
        materialDialog().setTitle("Active connection").setSingleChoiceItems(new String[]{"ChatGPT plan",api+" · API"},prefs.getBoolean("useChatGpt",false)?0:1,(d,w)->{
            if(!connectionIdle())return;
            try {
                if(w==0){JSONObject a=chatGpt==null?null:chatGpt.active();if(a==null || !a.optBoolean("connected") || !a.optBoolean("plan") || a.optString("model").isEmpty())throw new IOException("Connect ChatGPT and choose a model first.");}
                else if(prefs.getString("model","").isEmpty() || secrets.get(api).isEmpty())throw new IOException("Save an API provider, model and key in API provider settings first.");
                prefs.edit().putBoolean("useChatGpt",w==0).apply();d.dismiss();render();
            }catch(Exception e){error(e);}
        }).setNegativeButton("Cancel",null).show();
    }
    private String thinkingKey(String provider,String connection,String model){return "thinking."+provider+"|"+connection+"|"+model;}
    private String thinkingValue(String provider,String connection,String model){return prefs.getString(thinkingKey(provider,connection,model),"default");}
    private void thinkingPreference(String provider,String connection,String model){
        String current=thinkingValue(provider,connection,model);
        preference("Thinking mode",Thinking.label(current)+" · "+model,0,()->{
            if(!connectionIdle())return;
            String[] values=Thinking.options(provider,model),labels=new String[values.length];int selected=0;
            for(int i=0;i<values.length;i++){labels[i]=Thinking.label(values[i]);if(values[i].equals(current))selected=i;}
            materialDialog().setTitle("Thinking mode").setSingleChoiceItems(labels,selected,(d,w)->{
                prefs.edit().putString(thinkingKey(provider,connection,model),values[w]).apply();d.dismiss();render();
            }).setNegativeButton("Cancel",null).setNeutralButton("About thinking",(d,w)->materialDialog().setTitle("Thinking controls").setMessage("Saved separately for each connection and model. Model default sends no override. Higher effort or budgets can take longer and use more of your allowance. Availability varies by model; if the provider rejects an override, choose Model default. Anthropic effort uses adaptive thinking; budget choices use extended thinking.").setPositiveButton("OK",null).show()).show();
        });
    }
    private void manageChatGptAccount(){
        materialDialog().setTitle("ChatGPT account").setItems(new String[]{"Choose saved account","Add another account","Refresh models","Reconnect account","Sign out"},(d,w)->{
            if(!connectionIdle())return;
            try {
                JSONObject account=chatGpt.active();String id=account==null?"":account.optString("client_id");
                if(w==0){JSONArray saved=chatGpt.accounts();String[] labels=new String[saved.length()];for(int i=0;i<labels.length;i++)labels[i]=saved.getJSONObject(i).getString("label")+(saved.getJSONObject(i).optBoolean("connected")?"":" · signed out");materialDialog().setTitle("Saved accounts").setItems(labels,(dialog,index)->{try{chatGpt.select(saved.getJSONObject(index).getString("client_id"));render();}catch(Exception e){error(e);}}).show();}
                if(w==1)startChatGptSignIn("",false);
                if(w==2)accountWork("Refreshing ChatGPT models",()->{chatGpt.refreshModels(id,chatGptHttp);return "Available models refreshed.";});
                if(w==3)startChatGptSignIn(id,false);
                if(w==4)confirm("Sign out on this device? Your learning records will stay.",()->accountWork("Signing out of ChatGPT",()->chatGpt.signOut(id,chatGptHttp)?"Signed out.":"Signed out locally. Remote revocation was not confirmed; disconnect the app in ChatGPT settings."));
            }catch(Exception e){error(e);}
        }).show();
    }

    private void templateMenu(){
        try{
            JSONArray rows=new InteractiveTemplates(this).list();String[] labels=new String[rows.length()+3];labels[0]="Import Agora template / catalogue";labels[1]="Add probability explorer example";labels[2]="Export template catalogue";for(int i=0;i<rows.length();i++)labels[i+3]=rows.getJSONObject(i).getString("name");
            materialDialog().setTitle("Interactive templates").setItems(labels,(d,w)->{try{
                if(w==0)chooseFile(IMPORT_TEMPLATE,"application/json");
                else if(w==1){new InteractiveTemplates(this).save(InteractiveTemplates.sample(),false);templateMenu();}
                else if(w==2)export(new InteractiveTemplates(this).list().toString(2),"learnharness-templates.json","application/json");
                else templateOptions(rows.getJSONObject(w-3));
            }catch(Exception e){error(e);}}).show();
        }catch(Exception e){error(e);}
    }
    private void templateOptions(JSONObject template){
        materialDialog().setTitle(template.optString("name")).setItems(new String[]{"Open interactive","Rename / shortcut","Export template","Delete template"},(d,w)->{try{
            if(w==0){captureDraft();startActivityForResult(new Intent(this,InteractiveActivity.class).putExtra("template",template.getString("id")),OPEN_TEMPLATE);}
            if(w==1){LinearLayout form=column();EditText name=input("Name",template.optString("name"),false),shortcut=input("Shortcut without /",template.optString("shortcut"),false);form.addView(name);form.addView(shortcut);materialDialog().setTitle("Template details").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save",(dialog,index)->{try{template.put("name",name.getText().toString().trim());String command=shortcut.getText().toString().trim();if(command.isEmpty())template.remove("shortcut");else template.put("shortcut",command);new InteractiveTemplates(this).save(template,true);}catch(Exception e){error(e);}}).show();}
            if(w==2)export(template.toString(2),"interactive-template.json","application/json");
            if(w==3)confirm("Delete this template?",()->{try{new InteractiveTemplates(this).delete(template.getString("id"));}catch(Exception e){error(e);}});
        }catch(Exception e){error(e);}}).show();
    }
    private void pickTree(){
        if(!connectionIdle())return;captureDraft();documentTarget="";try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION),ADD_TREE);}catch(ActivityNotFoundException error){error(new IOException("No Android folder picker is available."));}
    }
    private void registerTree(Intent result)throws Exception{
        android.net.Uri uri=result.getData();if(uri==null || !"content".equals(uri.getScheme()) || !android.provider.DocumentsContract.isTreeUri(uri))throw new IOException("Select a folder through Android Files.");getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        documentWork("Linking folder",()->DocumentTrees.title(this,uri),title->{try{JSONArray trees=records.root.optJSONArray("sourceTrees");if(trees==null){trees=new JSONArray();records.root.put("sourceTrees",trees);}JSONObject selected=null;for(int i=0;i<trees.length();i++)if(trees.getJSONObject(i).optString("uri").equals(uri.toString()))selected=trees.getJSONObject(i);if(selected==null){selected=new JSONObject().put("id",Records.id()).put("title",title).put("uri",uri.toString());trees.put(selected);}records.save();render();browseTree(selected,new ArrayList<>());}catch(Exception error){error(error);}});
    }
    private void treeMenu(JSONObject tree){materialDialog().setTitle(tree.optString("title")).setItems(new String[]{"Browse folder","Remove folder reference"},(d,index)->{if(index==0)browseTree(tree,new ArrayList<>());else confirm("Remove this folder reference? Existing linked books stay available; the original files stay in Files.",()->{try{JSONArray trees=records.root.getJSONArray("sourceTrees");for(int i=trees.length()-1;i>=0;i--)if(trees.getJSONObject(i).optString("id").equals(tree.optString("id")))trees.remove(i);records.save();render();}catch(Exception error){error(error);}});}).setNegativeButton("Close",null).show();}
    private void browseTree(JSONObject tree,ArrayList<String> parents){
        if(!connectionIdle())return;android.net.Uri uri=android.net.Uri.parse(tree.optString("uri"));String parent=parents.isEmpty()?DocumentTrees.rootId(uri):parents.get(parents.size()-1);
        documentWork("Browsing folder",()->DocumentTrees.list(this,uri,parent),entries->{try{String[] titles=new String[entries.length()];for(int i=0;i<titles.length;i++){JSONObject entry=entries.getJSONObject(i);titles[i]=(entry.optBoolean("directory")?"Folder · ":"")+entry.getString("title");}androidx.appcompat.app.AlertDialog.Builder dialog=materialDialog().setTitle(tree.optString("title")+" · "+entries.length()+" entries").setItems(titles,(d,index)->{JSONObject entry=entries.optJSONObject(index);if(entry.optBoolean("directory")){ArrayList<String> next=new ArrayList<>(parents);next.add(entry.optString("id"));browseTree(tree,next);}else{materialDialog().setTitle(entry.optString("title")).setMessage("Link this file to Library? Contents are read only when you choose search/read and are sent to the AI only after passage approval.").setNegativeButton("Cancel",(cancel,w)->browseTree(tree,parents)).setPositiveButton("Link file",(ok,w)->{try{documentTarget="";registerDocument(new Intent().setData(android.net.Uri.parse(entry.optString("uri"))).putExtra("treeGranted",true));browseTree(tree,parents);}catch(Exception error){error(error);}}).show();}}).setNegativeButton("Done",null);if(!parents.isEmpty())dialog.setNeutralButton("Up",(d,w)->{ArrayList<String> next=new ArrayList<>(parents);next.remove(next.size()-1);browseTree(tree,next);});dialog.show();}catch(Exception error){error(error);}});
    }
    private void pickDocument(String target){
        if(!connectionIdle())return;captureDraft();documentTarget=target;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","application/epub+zip","text/plain","text/markdown","text/x-markdown"}).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(intent,ADD_DOCUMENT);}catch(ActivityNotFoundException e){error(new IOException("No Android document picker is available."));}
    }
    private void registerDocument(Intent result)throws Exception{
        android.net.Uri uri=result.getData();String title="Document";
        try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null && c.moveToFirst())title=c.getString(0);}
        String lower=title.toLowerCase(Locale.ROOT),mime=getContentResolver().getType(uri),kind;
        if(lower.endsWith(".pdf") || "application/pdf".equals(mime))kind="PDF";
        else if(lower.endsWith(".epub") || "application/epub+zip".equals(mime))kind="EPUB";
        else if(lower.matches(".*\\.(txt|md|markdown)$") || mime!=null && mime.startsWith("text/"))kind="Text";
        else throw new IOException("Choose a PDF, EPUB, text or Markdown document.");
        if(!"content".equals(uri.getScheme()))throw new IOException("Choose a file through Android Files.");
        if(!result.getBooleanExtra("treeGranted",false))getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        String id="";JSONArray docs=records.rows("documents");for(int i=0;i<docs.length();i++)if(docs.getJSONObject(i).getString("uri").equals(uri.toString()))id=docs.getJSONObject(i).getString("id");
        if(id.isEmpty()){id=Records.id();docs.put(new JSONObject().put("id",id).put("title",title).put("kind",kind).put("uri",uri.toString()));}
        JSONObject session=records.find("sessions",documentTarget);if(session!=null){JSONArray refs=session.optJSONArray("documents");if(refs==null)refs=new JSONArray();boolean present=false;for(int i=0;i<refs.length();i++)if(refs.getString(i).equals(id))present=true;if(!present)refs.put(id);session.put("documents",refs);}
        documentTarget="";save();render();Toast.makeText(this,"File linked. Its contents are not sent until you approve passages.",Toast.LENGTH_LONG).show();
    }
    private void attachmentMenu(JSONObject session){
        if(!connectionIdle())return;captureDraft();
        materialDialog().setTitle("Books & documents").setItems(new String[]{"Link a file from Android Files","Choose from Library","Search / read attached sources","Remove approved passages","Interactive templates"},(d,w)->{
            if(w==0)pickDocument(session.optString("id"));
            if(w==1)chooseDocuments(session);
            if(w==2){try{JSONArray all=records.rows("documents"),refs=session.optJSONArray("documents");ArrayList<JSONObject> attached=new ArrayList<>();for(int i=0;i<all.length();i++)for(int j=0;refs!=null && j<refs.length();j++)if(all.getJSONObject(i).getString("id").equals(refs.getString(j)))attached.add(all.getJSONObject(i));if(attached.isEmpty())throw new IOException("Link a file or choose one from Library first.");String[] titles=new String[attached.size()];for(int i=0;i<titles.length;i++)titles[i]=attached.get(i).optString("title");materialDialog().setTitle("Choose a source").setItems(titles,(dialog,index)->searchDocument(attached.get(index),session.optString("id"))).show();}catch(Exception e){error(e);}}
            if(w==3){session.remove("approvedExcerpts");save();render();}
            if(w==4)templateMenu();
        }).show();
    }
    private void chooseDocuments(JSONObject session){
        try{
            JSONArray docs=records.rows("documents"),previous=session.optJSONArray("documents");if(docs.length()==0){pickDocument(session.optString("id"));return;}
            String[] labels=new String[docs.length()];boolean[] chosen=new boolean[docs.length()];for(int i=0;i<labels.length;i++){labels[i]=docs.getJSONObject(i).getString("title");for(int j=0;previous!=null && j<previous.length();j++)if(previous.getString(j).equals(docs.getJSONObject(i).getString("id")))chosen[i]=true;}
            materialDialog().setTitle("Available sources · no content sent").setMultiChoiceItems(labels,chosen,(d,w,on)->chosen[w]=on).setNegativeButton("Cancel",null).setPositiveButton("Done",(d,w)->{try{JSONArray selected=new JSONArray();for(int i=0;i<chosen.length;i++)if(chosen[i])selected.put(docs.getJSONObject(i).getString("id"));session.put("documents",selected);save();render();}catch(Exception e){error(e);}}).show();
        }catch(Exception e){error(e);}
    }
    private void documentMenu(JSONObject doc){
        materialDialog().setTitle(doc.optString("title")).setItems(new String[]{"Search / grep / read pages","Use in a new lesson","Open original file","Remove file reference"},(d,w)->{
            if(w==0)searchDocument(doc,"");
            if(w==1){EditText goal=input("What do you want to learn from this source?","",true);materialDialog().setTitle("Study this source").setView(goal).setNegativeButton("Cancel",null).setPositiveButton("Create lesson",(dialog,index)->{try{if(goal.getText().toString().trim().isEmpty())return;JSONObject lesson=new JSONObject().put("id",Records.id()).put("title",goal.getText().toString().trim()).put("phase","Explore").put("documents",new JSONArray().put(doc.getString("id"))).put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content",goal.getText().toString().trim())));records.rows("sessions").put(lesson);selectedSession=lesson.getString("id");screen="Learn";draft="";save();render();searchDocument(doc,selectedSession);}catch(Exception e){error(e);}}).show();}
            if(w==2)try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(doc.optString("uri"))).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(ActivityNotFoundException e){error(new IOException("No reader is available for this document."));}
            if(w==3)confirm("Remove this reference from Library? The original file stays in Files.",()->{if(!connectionIdle())return;records.remove("documents",doc.optString("id"));try{getContentResolver().releasePersistableUriPermission(android.net.Uri.parse(doc.optString("uri")),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}save();render();});
        }).show();
    }
    private interface DocumentWork<T>{T read()throws Exception;}
    private <T>void documentWork(String title,DocumentWork<T> work,java.util.function.Consumer<T> done){
        if(!connectionIdle())return;captureDraft();busy=true;operation=title;streamSession="";streamPractice="";int generation=++requestGeneration;render();
        documentTask=worker.submit(()->{try{T result=work.read();runOnUiThread(()->{if(isDestroyed() || generation!=requestGeneration)return;busy=false;render();done.accept(result);});}catch(Exception e){runOnUiThread(()->{if(isDestroyed() || generation!=requestGeneration)return;busy=false;render();error(e instanceof SecurityException?new IOException("File access has expired. Link this document again through Android Files."):e);});}});
    }
    private void searchDocument(JSONObject doc,String sessionId){searchDocument(doc,sessionId,new JSONObject());}
    private void searchDocument(JSONObject doc,String sessionId,JSONObject requested){
        LinearLayout form=column();form.setPadding(dp(20),dp(8),dp(20),0);
        EditText query=input("Find text or a regular expression",requested.optString("pattern"),false),range=input(doc.optString("kind").equals("Text")?"100-line blocks, e.g. 1-10 · blank = all":"Pages / chapters, e.g. 10-30 · blank = all","",false);form.addView(query);form.addView(range);
        MaterialSwitch regex=new MaterialSwitch(this);regex.setText("Regular expression (RE2)");regex.setChecked(requested.has("pattern") && !requested.optBoolean("literal"));regex.setTextColor(ink);form.addView(regex);
        if(requested.has("offset"))form.addView(label("Tutor requested line "+requested.optInt("offset")+(requested.has("limit")?" · up to "+requested.optInt("limit")+" lines":"")+". Choose the page, chapter or text block to review below.",13,false));
        form.addView(label("Search reads the original file on demand. Matching passages stay local until you approve them. A scan stops at 12 hits, 2,000 units or 20 seconds; narrow the scope when needed.",13,false));
        androidx.appcompat.app.AlertDialog dialog=materialDialog().setTitle(doc.optString("title")).setView(form).setNegativeButton("Cancel",null).setPositiveButton("Search",null).setNeutralButton("Read page / chapter",null).create();
        dialog.setOnShowListener(v->{
            dialog.getButton(-1).setOnClickListener(button->{try{if(query.getText().toString().trim().isEmpty()){query.setError("Enter search text");return;}com.google.re2j.Pattern pattern=BookDocuments.pattern(query.getText().toString(),regex.isChecked());String scope=range.getText().toString();BookDocuments.scope(scope);dialog.dismiss();documentWork("Searching source",()->BookDocuments.search(this,android.net.Uri.parse(doc.optString("uri")),doc.optString("kind"),pattern,scope,12),result->{
                    if(result.hits().isEmpty()){showPassages(doc,sessionId,result.summary()+"\n\nNo matching passages in the scanned scope. No source material will be sent.",false);return;}
                    String[] matches=new String[result.hits().size()];for(int i=0;i<matches.length;i++){BookDocuments.Hit hit=result.hits().get(i);matches[i]=hit.locator()+"\n"+hit.text().replace('\n',' ').substring(0,Math.min(100,hit.text().length()));}
                    materialDialog().setTitle("Matches · local only").setItems(matches,(d,w)->{BookDocuments.Hit hit=result.hits().get(w);showPassages(doc,sessionId,"[Source: "+doc.optString("title")+" — "+hit.locator()+"]\n"+hit.text(),true);}).setNegativeButton("Close",null).setNeutralButton("Search scope",(d,w)->materialDialog().setMessage(result.summary()).setPositiveButton("OK",null).show()).show();
                });}catch(Exception e){error(e);}});
            dialog.getButton(-3).setOnClickListener(button->{try{int[] selected=BookDocuments.scope(range.getText().toString());int page=selected[0];dialog.dismiss();documentWork("Reading selected page",()->BookDocuments.readUnit(this,android.net.Uri.parse(doc.optString("uri")),doc.optString("kind"),page),text->showPassages(doc,sessionId,"[Source: "+doc.optString("title")+"]\n"+text,true));}catch(Exception e){error(e);}});
        });dialog.show();
    }
    private void showPassages(JSONObject doc,String sessionId,String text,boolean hasMatches){
        TextView preview=label(text,fontSize,false);preview.setTextIsSelectable(true);preview.setPadding(dp(20),dp(12),dp(20),dp(12));ScrollView scroll=new ScrollView(this);scroll.addView(preview);
        androidx.appcompat.app.AlertDialog.Builder review=materialDialog().setTitle("Source preview · local only").setView(scroll).setPositiveButton("Done",null);
        if(hasMatches)review.setNeutralButton("Use this passage",(d,w)->{
            if(sessionId.isEmpty()){Toast.makeText(this,"Choose Use in a new lesson from the source menu to send approved passages.",Toast.LENGTH_LONG).show();return;}
            // Let the learner select a bounded excerpt, instead of silently including every hit.
            EditText excerpt=input("Keep only the passage you want the tutor to use",text.length()>6000?text.substring(0,6000):text,true);
            ScrollView editor=new ScrollView(this);editor.addView(excerpt);
            materialDialog().setTitle("Approve for the next reply").setMessage("Only this excerpt will be sent to the selected AI connection. Edit or trim it first (maximum 6,000 characters). Approval is used once.").setView(editor).setNegativeButton("Cancel",null).setPositiveButton("Approve excerpt",(dialog,index)->{try{String approved=excerpt.getText().toString().trim();if(approved.isEmpty())return;if(approved.length()>6000)throw new IOException("Trim the excerpt to 6,000 characters.");JSONObject lesson=records.find("sessions",sessionId);if(lesson==null)throw new IOException("The lesson is no longer available.");if(lesson.optJSONObject("pendingQuestion")!=null && lesson.getJSONObject("pendingQuestion").optString("kind").equals("source")){resolveSourceRequest(lesson,approved,false);}else{lesson.put("approvedExcerpts",approved);save();render();Toast.makeText(this,"Passage approved. Send your next question when ready.",Toast.LENGTH_LONG).show();}}catch(Exception e){error(e);}}).show();
        });
        review.show();
    }
    private void chooseFile(int code, String mime) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(intent, code); } catch(ActivityNotFoundException e) { error(new IOException("No document picker is available on this device.")); }
    }
    private void export(String text, String filename, String mime) {
        try (OutputStream out = new FileOutputStream(new File(getCacheDir(), "pending-export.txt"))) { out.write(text.getBytes(StandardCharsets.UTF_8)); }
        catch (IOException e) { error(e); return; }
        try { startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE, filename), EXPORT); }
        catch(ActivityNotFoundException e) { new File(getCacheDir(), "pending-export.txt").delete(); error(new IOException("No document picker is available on this device.")); }
    }
    private boolean receiveSharedContent(Intent intent){
        String id=intent.getStringExtra(SharedContent.EXTRA);if(id==null)return false;intent.removeExtra(SharedContent.EXTRA);
        try{
            SharedContent inbox=new SharedContent(this);JSONObject share=inbox.load(id);if(share==null || share.optBoolean("consumed"))return true;
            captureDraft();JSONObject before=new JSONObject(records.root.toString());JSONObject target;
            try{target=ShareDestinations.attach(records.root,share,inbox.documents(share),share.optString("destination"),share.optBoolean("practice"),System.currentTimeMillis());if(target==null)throw new IOException("The selected conversation is no longer available.");records.save();}catch(Exception failure){records.root=before;throw failure;}
            try{share.put("consumed",true);inbox.save(share);}catch(Exception ignored){}
            if(creationDialog!=null){creationDialog.dismiss();creationDialog=null;creationGoal=null;creationKind="";}
            selectedSession=target.getString("id");selectedPractice="";screen="Learn";settingsPage="";draft=target.optString("draft");confidenceTouched=false;confidencePercent=50;render();
            Toast.makeText(this,"Shared content added. Review your draft before sending.",Toast.LENGTH_LONG).show();
        }catch(Exception failure){error(failure);}
        return true;
    }
    private void cancelVoiceTyping(){
        if(voiceTyping!=null){voiceTyping.cancel();voiceTyping=null;}
        if(apiVoiceTyping!=null){apiVoiceTyping.cancel();apiVoiceTyping=null;}
        if(voiceDialog!=null){voiceDialog.dismiss();voiceDialog=null;}
    }
    private void dictate() {
        if(composer==null || busy)return;
        if(androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){voicePermission.launch(android.Manifest.permission.RECORD_AUDIO);return;}
        cancelVoiceTyping();captureDraft();
        EditText target=composer;String expected=target.getText().toString();
        int start=Math.max(0,Math.min(target.getSelectionStart(),target.getSelectionEnd()));
        int end=Math.max(start,Math.max(target.getSelectionStart(),target.getSelectionEnd()));
        LinearLayout panel=column();panel.setPadding(dp(24),dp(8),dp(24),dp(12));
        TextView preview=label("Listening…",fontSize,false);panel.addView(preview);
        voiceDialog=materialDialog().setTitle("Voice typing").setView(panel).setNegativeButton("Cancel",(dialog,which)->cancelVoiceTyping()).setPositiveButton("Finish",null).create();
        voiceDialog.setOnCancelListener(dialog->cancelVoiceTyping());voiceDialog.show();
        AndroidVoiceTyping.Listener listener=new AndroidVoiceTyping.Listener(){
            public void partial(String text){if(!text.isEmpty())preview.setText(text);}
            public void finishing(){if(voiceDialog!=null){voiceDialog.setTitle("Finishing…");voiceDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);}}
            public void error(String message){cancelVoiceTyping();MainActivity.this.error(new IOException(message));}
            public void result(String text){
                cancelVoiceTyping();
                if(composer!=target || !expected.equals(target.getText().toString()) || Math.min(target.getSelectionStart(),target.getSelectionEnd())!=start || Math.max(target.getSelectionStart(),target.getSelectionEnd())!=end){Toast.makeText(MainActivity.this,"Draft changed; speech was not inserted.",Toast.LENGTH_LONG).show();return;}
                String insertion=VoiceInsertion.text(expected,start,end,text);target.getText().replace(start,end,insertion);target.setSelection(start+insertion.length());captureDraft();save();
            }
        };
        voiceDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{if(voiceTyping!=null)voiceTyping.stop();else if(apiVoiceTyping!=null)apiVoiceTyping.stop();});
        if(voiceUsesApi()){
            preview.setText("Recording · tap Finish to transcribe\n"+voiceEngine()+" · up to 2 minutes");
            String language=prefs.getString("voiceLanguage","");if(!language.isEmpty())language=java.util.Locale.forLanguageTag(language).getLanguage();
            try{apiVoiceTyping=new ApiVoiceTyping(listener);apiVoiceTyping.start(this,voiceBase(voiceEngine()),voiceModel(voiceEngine()),language,secrets.get("Voice "+voiceEngine()),voiceEngine().equals("Compatible API"),voiceEngine().equals("Deepgram"),prefs.getBoolean("deepgramOptOut",true));}catch(Exception error){cancelVoiceTyping();error(error);}
        }else{voiceTyping=new AndroidVoiceTyping(listener);voiceTyping.start(this,voiceEngine().equals("On-device only"),prefs.getString("voiceLanguage",""));}
    }
    @Override protected void onActivityResult(int code, int result, Intent data) {
        if(code==OPEN_GRAPH && result==RESULT_OK && data!=null){String id=data.getStringExtra("session"),message=data.getStringExtra("message");JSONObject target=records.find("sessions",id);if(target!=null){screen="Learn";selectedSession=id;draft="";render();if(message!=null && !message.isEmpty())showSourcePassage(target,message);}return;}
        super.onActivityResult(code, result, data);
        if (result != RESULT_OK || data == null) { if (code == EXPORT) new File(getCacheDir(), "pending-export.txt").delete(); return; }
        try {
            if(code==OPEN_TEMPLATE){String followup=data.getStringExtra("text");if(followup!=null && !followup.isEmpty()){draft=draft.isEmpty()?followup:draft+"\n\n"+followup;JSONObject session=records.find("sessions",selectedSession);if(session!=null)session.put("draft",draft);save();render();}return;}
            if (data.getData() == null) return;
            if(code==ADD_TREE){registerTree(data);return;}
            if(code==ADD_DOCUMENT){registerDocument(data);return;}
            if(code==IMPORT_TEMPLATE){try(InputStream in=getContentResolver().openInputStream(data.getData())){if(in==null)throw new IOException("Template file could not be opened.");new InteractiveTemplates(this).importCatalogue(new String(ProviderClient.readBounded(in,16_000_000),StandardCharsets.UTF_8));}render();Toast.makeText(this,"Templates imported",Toast.LENGTH_LONG).show();return;}
            if (code == EXPORT) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt")) {
                    if (out == null) throw new IOException("Could not open the selected destination.");
                    try (InputStream source = new FileInputStream(new File(getCacheDir(), "pending-export.txt"))) { byte[] bytes = new byte[8192]; int length; while ((length = source.read(bytes)) != -1) out.write(bytes, 0, length); }
                }
                new File(getCacheDir(), "pending-export.txt").delete(); Toast.makeText(this, "Export saved", Toast.LENGTH_LONG).show(); return;
            }
            String text;
            try (InputStream in = getContentResolver().openInputStream(data.getData())) {
                if (in == null) throw new IOException("Could not open the selected file.");
                text = new String(ProviderClient.readBounded(in, 16_000_000), StandardCharsets.UTF_8);
            }
            if (code == IMPORT_NOTES) {
                if (text.length() > 100000 || text.indexOf('\0') >= 0 || text.startsWith("%PDF")) throw new IOException("Import a UTF-8 text or Markdown file of up to 100,000 characters. PDF and binary documents are not yet supported.");
                noteDialog("Imported notes", text);
            } else if (code == RESTORE) {
                JSONObject replacement = new JSONObject(text); Records.validate(replacement);
                confirm("Replace current learning records with " + replacement.getJSONArray("sessions").length() + " sessions, " + replacement.getJSONArray("knowledge").length() + " knowledge claims and " + replacement.getJSONArray("practice").length() + " practice sets? Export your current backup first if needed.", () -> {
                    if (busy) { error(new IOException("Wait for or cancel the active request before restoring.")); return; }
                    JSONObject before = records.root; records.root = replacement;
                    if (!save()) records.root = before;
                    selectedSession = ""; selectedPractice = ""; draft = ""; render();
                });
            }
        } catch(Exception e) { error(e); }
    }
    @Override protected void onPause() { captureDraft(); if (records != null) save(); super.onPause(); }
}
