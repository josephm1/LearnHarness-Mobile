package ai.learnharness.android;

import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Android share destination chooser. Choosing a row prepares a draft, never sends it. */
public final class ShareActivity extends AppCompatActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private String id=UUID.randomUUID().toString();private boolean practice,eink,dispatched;
    private SharedContent inbox;private JSONObject share;private JSONArray sessions;
    @Override public void onCreate(Bundle state){
        eink=getSharedPreferences("settings",0).getBoolean("eink",BuildConfig.DEFAULT_EINK);if(eink)setTheme(R.style.Theme_LearnHarness_Eink);super.onCreate(state);getTheme().applyStyle(R.style.WhiteSurfaces,true);
        if(state!=null){id=state.getString("share",id);practice=state.getBoolean("practice",false);}inbox=new SharedContent(this);
        TextView loading=text("Preparing shared content…",16,false);loading.setPadding(dp(24),dp(64),dp(24),dp(24));setContentView(loading);
        worker.submit(()->{try{JSONObject received=inbox.receive(id,getIntent());JSONArray rows=new Records(this).rows("sessions");runOnUiThread(()->{if(isFinishing() || isDestroyed()){if(!dispatched)inbox.delete(id);return;}share=received;sessions=rows;render();});}catch(Exception error){runOnUiThread(()->{if(!isFinishing() && !isDestroyed())new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Could not import shared content").setMessage(error.getMessage()).setPositiveButton("Close",(dialog,which)->finish()).setOnCancelListener(dialog->finish()).show();});}});
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,boolean strong){TextView view=new TextView(this);view.setText(value);view.setTextColor(Color.BLACK);view.setTextSize(size);view.setTypeface(eink?getResources().getFont(R.font.eink_outfit):Typeface.DEFAULT,strong?Typeface.BOLD:Typeface.NORMAL);view.setLineSpacing(0,1.35f);return view;}
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private GradientDrawable outline(){GradientDrawable shape=new GradientDrawable();shape.setColor(Color.WHITE);shape.setStroke(dp(1),Color.BLACK);shape.setCornerRadius(dp(24));return shape;}
    private void render(){
        LinearLayout root=column();root.setBackgroundColor(Color.WHITE);setContentView(root);androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets)->{var bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return androidx.core.view.WindowInsetsCompat.CONSUMED;});
        MaterialToolbar bar=new MaterialToolbar(this);bar.setTitle("Add to conversation");bar.setTitleTextColor(Color.BLACK);bar.setNavigationIcon(R.drawable.ic_back);bar.setNavigationIconTint(Color.BLACK);bar.setNavigationContentDescription("Cancel share");bar.setNavigationOnClickListener(view->finish());if(eink)bar.setTitleTextAppearance(this,R.style.EinkToolbarTitle);root.addView(bar,new LinearLayout.LayoutParams(-1,dp(56)));
        LinearLayout summary=column();summary.setPadding(dp(24),dp(12),dp(24),dp(12));
        String heading=share.optString("title");if(heading.isEmpty())heading=share.optString("text").replace('\n',' ');if(heading.length()>160)heading=heading.substring(0,160)+"…";
        TextView excerpt=text(heading,16,true);excerpt.setMaxLines(3);excerpt.setEllipsize(android.text.TextUtils.TruncateAt.END);summary.addView(excerpt);
        int count=share.optJSONArray("files")==null?0:share.optJSONArray("files").length();TextView detail=text((count==0?"Shared text":count+(count==1?" document":" documents"))+" · choose a destination. Nothing is sent yet.",13,false);detail.setPadding(0,dp(8),0,0);summary.addView(detail);root.addView(summary);
        TabLayout tabs=new TabLayout(this);tabs.setTabMode(TabLayout.MODE_FIXED);tabs.setTabTextColors(Color.BLACK,Color.BLACK);tabs.setSelectedTabIndicatorColor(Color.BLACK);tabs.setSelectedTabIndicatorHeight(0);tabs.setTabRippleColor(ColorStateList.valueOf(Color.TRANSPARENT));tabs.setBackgroundColor(Color.WHITE);tabs.addTab(tabs.newTab().setText("Learn"));tabs.addTab(tabs.newTab().setText("Practice"));tabs.getTabAt(practice?1:0).select();
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener(){public void onTabSelected(TabLayout.Tab tab){boolean next=tab.getPosition()==1;if(next!=practice){practice=next;render();}}public void onTabUnselected(TabLayout.Tab tab){}public void onTabReselected(TabLayout.Tab tab){}});root.addView(tabs);
        ScrollView scroll=new ScrollView(this);scroll.setSmoothScrollingEnabled(!eink);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);LinearLayout list=column();list.setPadding(dp(24),dp(16),dp(24),dp(24));scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        MaterialButton create=new MaterialButton(this);create.setText(practice?"New practice conversation":"New learning conversation");create.setAllCaps(false);create.setTextColor(Color.BLACK);create.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));create.setStrokeWidth(dp(1));create.setStrokeColor(ColorStateList.valueOf(Color.BLACK));create.setCornerRadius(dp(24));create.setIconResource(R.drawable.ic_add);create.setIconTint(ColorStateList.valueOf(Color.BLACK));create.setMinimumHeight(dp(56));create.setOnClickListener(view->choose(""));if(eink){create.setStateListAnimator(null);create.setRippleColor(ColorStateList.valueOf(Color.TRANSPARENT));}list.addView(create,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView label=text("Recent conversations",13,true);label.setPadding(0,dp(24),0,dp(12));list.addView(label);
        Map<String,Long> visits=new HashMap<>();for(Map.Entry<String,?> entry:getSharedPreferences("settings",0).getAll().entrySet())if(entry.getKey().startsWith("recent.") && entry.getValue() instanceof Long)visits.put(entry.getKey().substring(7),(Long)entry.getValue());
        try{
            List<JSONObject> rows=ShareDestinations.recent(sessions,practice,visits);
            if(rows.isEmpty())list.addView(text(practice?"No practice conversations yet.":"No learning conversations yet.",14,false));
            for(JSONObject session:rows){
                LinearLayout item=column();item.setPadding(dp(16),dp(16),dp(16),dp(16));item.setBackground(outline());item.setMinimumHeight(dp(72));TextView title=text(session.optString("title"),16,true);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);item.addView(title);
                long recent=visits.getOrDefault(session.optString("id"),session.optLong("updated",session.optLong("created")));if(recent>0){TextView when=text(android.text.format.DateUtils.getRelativeTimeSpanString(recent).toString(),13,false);when.setPadding(0,dp(4),0,0);item.addView(when);}
                item.setClickable(true);item.setFocusable(true);item.setContentDescription("Add to "+session.optString("title"));item.setOnClickListener(view->choose(session.optString("id")));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,0,0,dp(8));list.addView(item,params);
            }
        }catch(Exception error){list.addView(text("The conversation list could not be opened.",14,false));}
        if(eink){LinearLayout turns=new LinearLayout(this);for(int direction:new int[]{-1,1}){MaterialButton button=new MaterialButton(this);button.setText(direction<0?"↑":"↓");button.setContentDescription(direction<0?"Previous screenful":"Next screenful");button.setTextColor(Color.BLACK);button.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));button.setStateListAnimator(null);button.setRippleColor(ColorStateList.valueOf(Color.TRANSPARENT));button.setOnClickListener(view->scroll.scrollBy(0,direction*EinkReading.step(scroll.getHeight(),dp(24))));turns.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));}root.addView(turns);}
        androidx.core.view.ViewCompat.requestApplyInsets(root);
    }
    private void choose(String destination){
        if(dispatched)return;
        try{share.put("destination",destination).put("practice",practice);inbox.save(share);startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(SharedContent.EXTRA,id));dispatched=true;finish();}
        catch(Exception error){new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Could not open conversation").setMessage(error.getMessage()).setPositiveButton("OK",null).show();}
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);if(share!=null)render();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("share",id);state.putBoolean("practice",practice);super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){worker.shutdownNow();if(isFinishing() && !dispatched && inbox!=null)inbox.delete(id);super.onDestroy();}
}
