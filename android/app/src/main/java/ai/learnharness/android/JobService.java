package ai.learnharness.android;
import android.app.*;
import android.content.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Foreground research jobs with durable results; interrupted runs require an explicit retry. */
public final class JobService extends Service {
    public static final String CHANGED="ai.learnharness.android.JOBS_CHANGED",CANCEL="cancel",CHANNEL="learning-jobs";private static volatile boolean live;
    private final ExecutorService pool=Executors.newFixedThreadPool(3),cleanup=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());private final Map<String,Control> running=new HashMap<>();private JobStore store;private boolean destroyed;
    private static final class Control {volatile boolean cancelled;volatile NativeModels models;volatile ResearchWeb web;Future<?> future;}
    public static boolean isLive(){return live;}
    public static void wake(Context context){try{androidx.core.content.ContextCompat.startForegroundService(context,new Intent(context,JobService.class));}catch(RuntimeException ignored){/* Queued work stays durable until the app is foregrounded. */}}
    @Override public void onCreate(){super.onCreate();live=true;store=new JobStore(this);NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Learning jobs",NotificationManager.IMPORTANCE_LOW));PendingIntent open=PendingIntent.getActivity(this,61,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);PendingIntent cancel=PendingIntent.getService(this,62,new Intent(this,JobService.class).setAction(CANCEL),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);startForeground(61,new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_book).setContentTitle("LearnHarness background work").setContentText("Research runs separately from your lesson. Tap to return.").setContentIntent(open).setOngoing(true).setSilent(true).addAction(0,"Cancel jobs",cancel).build());try{store.interrupted();}catch(Exception ignored){} }
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(intent!=null && CANCEL.equals(intent.getAction())){cancel(intent.getStringExtra("job"));return START_NOT_STICKY;}startQueued();return START_NOT_STICKY;}
    private void startQueued(){if(destroyed)return;try{JSONArray jobs=store.list();for(int i=0;i<jobs.length();i++){JSONObject job=jobs.getJSONObject(i);String id=job.getString("id");if(!job.optString("status").equals("queued") || running.containsKey(id))continue;Control control=new Control();running.put(id,control);control.future=pool.submit(()->run(job,control));}if(running.isEmpty())stopSelf();}catch(Exception error){stopSelf();}}
    private void run(JSONObject job,Control control){String id=job.optString("id");try{
        if(control.cancelled || !store.transition(id,"queued",new JSONObject().put("status","running").put("activity","Researching")))return;changed();
        String engine=job.optString("searchEngine",SearchSettings.engine(getSharedPreferences("settings",0)));
        try(NativeModels models=new NativeModels(this,job.getJSONObject("route"),engine.equals("chatgpt"));ResearchWeb web=new ResearchWeb()){
            control.models=models;control.web=web;JSONObject input=job.getJSONObject("input");String key=engine.equals("chatgpt")?"":new SecretStore(this).get("search."+engine);
            JSONObject result=new ResearchAgent(this).run(input,models,new ResearchAgent.Web(){@Override public JSONArray search(String query,int count)throws Exception{if(control.cancelled)throw new InterruptedException();return web.search(engine,key,query,count);}@Override public ResearchWeb.Page fetch(String url)throws Exception{if(control.cancelled)throw new InterruptedException();return web.fetch(url);}},(activity,in,out,steps)->{if(control.cancelled)throw new InterruptedException();store.update(id,new JSONObject().put("activity",activity).put("inputTokens",in).put("outputTokens",out).put("steps",steps));changed();});
            if(control.cancelled)return;JSONObject steer=steer(job,result,false);store.transition(id,"running",new JSONObject().put("status","done").put("activity","Research complete").put("result",result).put("steer",steer));
        }
    }catch(Exception error){try{if(!control.cancelled){Thread.interrupted();String reason=error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();JSONObject result=new JSONObject().put("brief","Research failed: "+reason).put("sources",new JSONArray());store.transition(id,"running",new JSONObject().put("status","error").put("activity","Research failed").put("error",reason).put("result",result).put("steer",steer(job,result,false)));}}catch(Exception ignored){}
    }finally{main.post(()->{running.remove(id);changed();startQueued();});}}
    private JSONObject steer(JSONObject job,JSONObject result,boolean aborted)throws Exception{try(OriginalCore core=new OriginalCore(this)){JSONObject value=core.call(new JSONObject().put("operation","jobSteer").put("job",job).put("result",result).put("aborted",aborted));if(!value.optBoolean("ok"))throw new java.io.IOException(value.optString("error"));return value.getJSONObject("value");}}
    private void cancel(String selected){try{JSONArray jobs=store.list();for(int i=0;i<jobs.length();i++){JSONObject job=jobs.getJSONObject(i);String id=job.getString("id"),status=job.optString("status");if(selected!=null && !id.equals(selected) || !status.equals("queued") && !status.equals("running"))continue;Control control=running.remove(id);if(control!=null){control.cancelled=true;if(control.models!=null)control.models.close();if(control.web!=null)control.web.cancel();if(control.future!=null)control.future.cancel(true);}store.update(id,new JSONObject().put("status","cancelled").put("activity","Cancelled by learner"));cleanup.submit(()->{try{store.update(id,new JSONObject().put("steer",steer(job,new JSONObject(),true)));changed();}catch(Exception ignored){}});}changed();if(running.isEmpty())stopSelf();}catch(Exception ignored){stopSelf();}}
    private void changed(){sendBroadcast(new Intent(CHANGED).setPackage(getPackageName()));}
    @Override public void onTimeout(int startId,int type){cancel(null);stopSelf();}
    @Override public void onDestroy(){destroyed=true;live=false;for(Control c:running.values()){c.cancelled=true;if(c.models!=null)c.models.close();if(c.web!=null)c.web.close();if(c.future!=null)c.future.cancel(true);}running.clear();pool.shutdownNow();cleanup.shutdown();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
