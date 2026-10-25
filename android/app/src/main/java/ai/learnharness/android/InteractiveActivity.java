package ai.learnharness.android;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.webkit.*;
import android.graphics.Color;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import org.json.*;
import java.io.*;
import java.net.URI;
import java.util.*;

/** Agora's trusted host + opaque-origin Websandbox iframe, adapted to native Activities. */
public final class InteractiveActivity extends AppCompatActivity {
    private static final String ORIGIN="https://appassets.androidplatform.net",HOST=ORIGIN+"/interactive/host.html";
    private static final Set<String> ASSETS=new HashSet<>(Arrays.asList("host.html","host.css","host.js","design-system.js","websandbox.js","eink-profile.js","scroll-profile.js"));
    private static final Set<String> NETWORK=new HashSet<>(Arrays.asList("cdnjs.cloudflare.com","esm.sh","cdn.jsdelivr.net","unpkg.com","basemap.nationalmap.gov","upload.wikimedia.org","thumb.wikimedia.org"));
    private WebView browser;private WebMessagePort port;private JSONObject answer;private boolean connected,ready,eink;private TextView status;private final Handler handler=new Handler(Looper.getMainLooper());
    private static boolean safeHttps(String url){try{URI uri=new URI(url);return "https".equals(uri.getScheme()) && uri.getUserInfo()==null && (uri.getPort()==-1 || uri.getPort()==443);}catch(Exception e){return false;}}
    public static boolean allowedNetwork(String url){try{return safeHttps(url) && NETWORK.contains(new URI(url).getHost());}catch(Exception e){return false;}}
    public static String asset(String url){try{URI uri=new URI(url);String path=uri.getPath(),name=path.startsWith("/interactive/")?path.substring(13):"";return safeHttps(url) && "appassets.androidplatform.net".equals(uri.getHost()) && uri.getRawQuery()==null && ASSETS.contains(name)?name:null;}catch(Exception e){return null;}}
    @Override public void onCreate(Bundle state){
        eink=getSharedPreferences("settings",0).getBoolean("eink",BuildConfig.DEFAULT_EINK);if(eink)setTheme(R.style.Theme_LearnHarness_Eink);super.onCreate(state);getTheme().applyStyle(R.style.WhiteSurfaces,true);
        try{JSONObject template=new InteractiveTemplates(this).get(getIntent().getStringExtra("template"));answer=template.getJSONObject("answer");}catch(Exception e){finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.WHITE);setContentView(root);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets)->{var bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return androidx.core.view.WindowInsetsCompat.CONSUMED;});androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        MaterialToolbar toolbar=new MaterialToolbar(this);if(eink)toolbar.setTitleTextAppearance(this,R.style.EinkToolbarTitle);toolbar.setTitle(answer.optString("title"));toolbar.setNavigationIcon(R.drawable.ic_back);toolbar.setNavigationContentDescription("Back");toolbar.setNavigationOnClickListener(v->finish());toolbar.getMenu().add("Readable fallback").setOnMenuItemClickListener(item->{fallback();return true;});root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(56)));
        status=new TextView(this);status.setText("Opening interactive…");status.setPadding(20,8,20,8);root.addView(status);
        browser=new WebView(this);browser.setBackgroundColor(Color.WHITE);root.addView(browser,new LinearLayout.LayoutParams(-1,0,1));
        WebSettings settings=browser.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setMediaPlaybackRequiresUserGesture(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser,false);
        browser.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){String url=request.getUrl().toString(),name=asset(url);if(name!=null)try{return new WebResourceResponse(name.endsWith(".js")?"text/javascript":name.endsWith(".css")?"text/css":"text/html","UTF-8",getAssets().open("interactive/"+name));}catch(IOException ignored){}if(allowedNetwork(url))return null;return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){String url=request.getUrl().toString().split("#",2)[0];return request.isForMainFrame() || !(url.equals("about:blank") || url.equals("about:srcdoc"));}
            @Override public void onPageFinished(WebView view,String url){if(connected || !HOST.equals(url))return;connected=true;WebMessagePort[] channel=view.createWebMessageChannel();port=channel[0];port.setWebMessageCallback(new WebMessagePort.WebMessageCallback(){@Override public void onMessage(WebMessagePort received,WebMessage message){bridge(message.getData());}});try{JSONObject payload=new JSONObject().put("answer",answer).put("inline",false).put("eInkMode",eink).put("fontScale",getResources().getConfiguration().fontScale);view.postWebMessage(new WebMessage(payload.toString(),new WebMessagePort[]{channel[1]}),Uri.parse(ORIGIN));}catch(Exception e){fallback();}}
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){view.destroy();browser=null;fallback();return true;}
        });
        if(eink && !answer.optBoolean("eInkAware")){status.setText("Static readable fallback · this template has no e-ink profile");fallback();}
        else{browser.loadUrl(HOST);handler.postDelayed(()->{if(!ready && !isFinishing()){status.setText("The interactive could not finish loading. Use Readable fallback.");}},15000);}
        if(eink && answer.optBoolean("eInkAware")){LinearLayout turns=new LinearLayout(this);Button prev=new Button(this),next=new Button(this);prev.setText("← Previous");next.setText("Next →");prev.setOnClickListener(v->page("previous"));next.setOnClickListener(v->page("next"));turns.addView(prev,new LinearLayout.LayoutParams(0,dp(48),1));turns.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(turns);}
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void page(String direction){if(port!=null)try{port.postMessage(new WebMessage(new JSONObject().put("type","page").put("direction",direction).toString()));}catch(Exception ignored){}}
    private void reply(int id,boolean okay,boolean cancelled,Object result){if(port==null || isFinishing())return;try{JSONObject value=new JSONObject().put("id",id).put("ok",okay).put("cancelled",cancelled);if(result!=null)value.put("result",result);if(!okay)value.put("error","Action was not approved or is unavailable in this integration.");port.postMessage(new WebMessage(value.toString()));}catch(Exception ignored){}}
    private void bridge(String raw){
        if(raw==null || raw.length()>16000 || isFinishing())return;
        try{JSONObject data=new JSONObject(raw);String method=data.optString("method");int id=data.optInt("id",-1);
            switch(method){
                case "ready":ready=true;status.setVisibility(android.view.View.GONE);break;
                case "error":status.setText("Interactive rendering failed. Readable fallback is available.");break;
                case "sendPrompt":{
                    Object value=data.opt("value");String text=value instanceof JSONObject?((JSONObject)value).optString("text"):data.optString("value");
                    if(text.trim().isEmpty() || text.length()>4000){reply(id,false,false,null);return;}
                    new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Use this follow-up?").setMessage(text).setNegativeButton("Cancel",(d,w)->reply(id,false,true,null)).setPositiveButton("Place in reply field",(d,w)->{reply(id,true,false,null);setResult(RESULT_OK,new Intent().putExtra("text",text));finish();}).show();break;
                }
                case "openLink":{String url=data.optString("value");if(!safeHttps(url)){reply(id,false,false,null);return;}new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Open this link?").setMessage(url).setNegativeButton("Cancel",(d,w)->reply(id,false,true,null)).setPositiveButton("Open",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));reply(id,true,false,null);}catch(ActivityNotFoundException e){reply(id,false,false,null);}}).show();break;}
                case "listReferenceFiles":reply(id,true,false,new JSONArray());break;
                case "listDirectory":case "readTextFile":case "readFile":
                    // File access must be resolved through the native Library approval flow, never the artifact.
                    new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Template requests a file").setMessage("This template wants document access. Use the lesson’s Attach → Search / read flow to choose and approve a passage; it is not granted automatically.").setPositiveButton("OK",(d,w)->reply(id,false,true,null)).show();break;
                default:break;
            }
        }catch(Exception ignored){}
    }
    private void fallback(){if(answer==null)return;TextView text=new TextView(this);text.setText(answer.optString("fallback"));if(eink){text.setTypeface(getResources().getFont(R.font.eink_outfit));text.setLineSpacing(0,1.45f);}text.setTextColor(Color.BLACK);text.setTextSize(getSharedPreferences("settings",0).getInt("fontSize",16));text.setPadding(24,24,24,24);text.setTextIsSelectable(true);ScrollView scroll=new ScrollView(this);scroll.addView(text);new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(answer.optString("title")).setView(scroll).setPositiveButton("Done",null).show();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(port!=null)port.close();if(browser!=null){browser.stopLoading();browser.destroy();}super.onDestroy();}
}
