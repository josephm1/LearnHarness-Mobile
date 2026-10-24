package ai.learnharness.android;

import android.content.Context;
import android.os.*;
import androidx.javascriptengine.*;
import com.google.common.util.concurrent.ListenableFuture;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Runs the unchanged deterministic upstream core in Android's out-of-process JS sandbox. */
public final class OriginalCore implements AutoCloseable {
    private static ListenableFuture<JavaScriptSandbox> sharedConnection;private static int users;
    private static synchronized ListenableFuture<JavaScriptSandbox> acquire(Context context){if(sharedConnection==null)sharedConnection=JavaScriptSandbox.createConnectedInstanceAsync(context);users++;return sharedConnection;}
    private static void release(ListenableFuture<JavaScriptSandbox> connection){synchronized(OriginalCore.class){users--;if(users!=0)return;}connection.addListener(()->{synchronized(OriginalCore.class){if(users==0 && sharedConnection==connection){try{connection.get().close();}catch(Exception ignored){}sharedConnection=null;}}},Runnable::run);}
    private final ListenableFuture<JavaScriptSandbox> connection;
    private final Context context;
    private JavaScriptSandbox sandbox;
    private JavaScriptIsolate isolate;
    private volatile boolean closed;
    public OriginalCore(Context context){
        this.context=context.getApplicationContext();
        if(!JavaScriptSandbox.isSupported())throw new IllegalStateException("Update Android System WebView to use the original learning core.");
        connection=acquire(this.context);
    }
    public synchronized JSONObject call(JSONObject request)throws Exception{
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Run the original core on a worker thread");
        if(closed)throw new IOException("Core closed");
        if(isolate==null){
            sandbox=connection.get(15,TimeUnit.SECONDS);isolate=sandbox.createIsolate();
            try(InputStream input=context.getAssets().open("core/original-core.js")){
                String source=new String(ProviderClient.readBounded(input,4_000_000),StandardCharsets.UTF_8);
                isolate.evaluateJavaScriptAsync(source+"; 'ready';").get(15,TimeUnit.SECONDS);
            }
        }
        // JSON is encoded as a JS string literal; no user text is evaluated as code.
        JSONObject payload=new JSONObject(request.toString());
        if(request.optString("operation").equals("graphMerge")){byte[] bytes=new byte[8192];new java.security.SecureRandom().nextBytes(bytes);StringBuilder hex=new StringBuilder();for(byte value:bytes)hex.append(String.format(java.util.Locale.ROOT,"%02x",value&255));payload.put("_entropyHex",hex.toString());}
        String encoded="JSON.parse("+JSONObject.quote(payload.toString())+")";
        String script=request.optString("operation").equals("graphTool")?"LearnHarnessCore.asyncHandle("+encoded+").then(value=>JSON.stringify(value))":"JSON.stringify(LearnHarnessCore.handle("+encoded+"))";
        return new JSONObject(isolate.evaluateJavaScriptAsync(script).get(10,TimeUnit.SECONDS));
    }
    public void callAsync(JSONObject request,Executor executor,Consumer<JSONObject> done){
        Handler ui=new Handler(Looper.getMainLooper());executor.execute(()->{JSONObject result;try{result=call(request);}catch(Exception e){result=error(e.getMessage());}JSONObject answer=result;ui.post(()->done.accept(answer));});
    }
    private static JSONObject error(String message){try{return new JSONObject().put("ok",false).put("error",message==null?"Original core unavailable":message);}catch(JSONException impossible){throw new IllegalStateException(impossible);}}
    @Override public synchronized void close(){if(closed)return;closed=true;if(isolate!=null)isolate.close();release(connection);}
}
