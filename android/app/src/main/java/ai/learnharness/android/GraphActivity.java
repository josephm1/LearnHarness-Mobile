package ai.learnharness.android;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Read-only native map and chain table backed by the original graph snapshot and resting layout. */
public final class GraphActivity extends AppCompatActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private LinearLayout body;private JSONObject snapshot;private boolean eink;private TextView detail;
    @Override public void onCreate(Bundle state){eink=getSharedPreferences("settings",0).getBoolean("eink",false);if(eink)setTheme(R.style.Theme_LearnHarness_Eink);super.onCreate(state);getTheme().applyStyle(R.style.WhiteSurfaces,true);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.WHITE);setContentView(root);androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(),false);androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{var bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return androidx.core.view.WindowInsetsCompat.CONSUMED;});
        MaterialToolbar toolbar=new MaterialToolbar(this);toolbar.setTitle("Knowledge map");toolbar.setNavigationIcon(R.drawable.ic_back);toolbar.setNavigationContentDescription("Back");toolbar.setNavigationOnClickListener(v->finish());root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(56)));
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),0,dp(16),dp(24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));text("Opening knowledge map…",16,false);
        worker.submit(()->{try{Records records=new Records(this);try(OriginalCore core=new OriginalCore(this)){JSONObject result=core.call(new JSONObject().put("operation","graphView").put("graph",GraphRecords.snapshot(records)).put("sessions",records.rows("sessions")));if(!result.optBoolean("ok"))throw new java.io.IOException(result.optString("error"));snapshot=result.getJSONObject("snapshot");runOnUiThread(()->{if(!isDestroyed())try{render();}catch(Exception e){showError(e);}});}}catch(Exception e){runOnUiThread(()->showError(e));}});
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,boolean strong){TextView view=new TextView(this);view.setText(value);view.setTextColor(Color.BLACK);view.setTextSize(size);view.setPadding(0,dp(8),0,dp(8));if(strong)view.setTypeface(null,Typeface.BOLD);view.setTextIsSelectable(true);body.addView(view);return view;}
    private void showError(Exception e){if(isDestroyed())return;body.removeAllViews();text(e.getMessage()==null?"The knowledge map could not open.":e.getMessage(),16,false);}
    private void render()throws Exception{
        body.removeAllViews();JSONArray propositions=snapshot.getJSONArray("propositions"),chains=snapshot.getJSONArray("chains");text(propositions.length()+" propositions · "+chains.length()+" reasoning chains",14,false);
        if(propositions.length()==0){text("Extract knowledge from a lesson to build your map. Your notes can also be reviewed in Library.",16,false);return;}
        MapView map=new MapView(snapshot);map.setBackgroundColor(Color.WHITE);body.addView(map,new LinearLayout.LayoutParams(-1,dp(300)));text("Tap a node for its statement. Pinch to zoom and drag to explore. The list below includes the same propositions and sources.",12,false);detail=text("",16,false);
        TextView reset=text("Reset map view",14,true);reset.setClickable(true);reset.setOnClickListener(v->{map.reset();detail.setText("");});
        text("Propositions and sources",18,true);for(int i=0;i<propositions.length();i++){JSONObject node=propositions.getJSONObject(i);TextView item=text(node.getString("name")+"\n"+node.getString("statement"),16,false);item.setClickable(true);item.setFocusable(true);item.setOnClickListener(v->nodeDetails(node));}
        text("Reasoning chains",18,true);Map<String,String> names=new HashMap<>();for(int i=0;i<propositions.length();i++){JSONObject node=propositions.getJSONObject(i);names.put(node.getString("id"),node.getString("name"));}
        for(int i=0;i<chains.length();i++){JSONObject chain=chains.getJSONObject(i);StringBuilder line=new StringBuilder();JSONArray premises=chain.getJSONArray("premises");for(int j=0;j<premises.length();j++){if(j>0)line.append(" ∧ ");line.append(names.getOrDefault(premises.getString(j),premises.getString(j)));}line.append("\n⇒ ").append(names.getOrDefault(chain.getString("conclusion"),chain.getString("conclusion")));TextView item=text(line.toString(),16,false);item.setClickable(true);item.setFocusable(true);item.setOnClickListener(v->sources(chain,"Reasoning chain"));}
    }
    private void nodeDetails(JSONObject node){detail.setText(node.optString("name")+"\n\n"+node.optString("statement"));sources(node,node.optString("name"));}
    private void sources(JSONObject node,String title){try{
        JSONArray provenance=node.optJSONArray("provenance");ArrayList<String> labels=new ArrayList<>();ArrayList<JSONObject> points=new ArrayList<>();JSONObject sessions=snapshot.getJSONObject("sessions"),files=snapshot.getJSONObject("fileSources");
        for(int i=0;provenance!=null && i<provenance.length();i++){JSONObject point=provenance.getJSONObject(i);String id=point.getString("chatId"),message=point.optString("messageId");labels.add(sessions.optString(id,files.optString(id,id))+(message.isEmpty()?"":" · source passage"));points.add(point);}
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(title).setMessage(node.optString("statement","")).setPositiveButton("Sources",(d,w)->{
            if(points.isEmpty()){Toast.makeText(this,"No source anchors recorded",Toast.LENGTH_SHORT).show();return;}
            new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Sources").setItems(labels.toArray(new String[0]),(dialog,index)->{JSONObject point=points.get(index);if(sessions.has(point.optString("chatId"))){setResult(RESULT_OK,new Intent().putExtra("session",point.optString("chatId")).putExtra("message",point.optString("messageId")));finish();}else Toast.makeText(this,labels.get(index),Toast.LENGTH_LONG).show();}).setNegativeButton("Close",null).show();
        }).setNegativeButton("Close",null).show();
    }catch(Exception e){showError(e);}}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    private final class MapView extends View {
        private final JSONArray nodes,edges;private final Map<String,PointF> positions=new HashMap<>();private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);private final ScaleGestureDetector zoom;private float scale=1,offsetX,offsetY,lastX,lastY,downX,downY;private boolean moved;
        MapView(JSONObject graph)throws Exception{super(GraphActivity.this);nodes=graph.getJSONArray("nodes");edges=graph.getJSONArray("edges");JSONObject saved=graph.optJSONObject("positions");for(int i=0;i<nodes.length();i++){String id=nodes.getJSONObject(i).getString("id");JSONObject p=saved==null?null:saved.optJSONObject(id);positions.put(id,p==null?new PointF((float)Math.cos(i*2*Math.PI/nodes.length())*150,(float)Math.sin(i*2*Math.PI/nodes.length())*150):new PointF((float)p.optDouble("x"),(float)p.optDouble("y")));}setContentDescription("Knowledge dependency map; "+nodes.length()+" nodes. Accessible proposition and chain lists follow below.");zoom=new ScaleGestureDetector(GraphActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector detector){scale=Math.max(.5f,Math.min(6,scale*detector.getScaleFactor()));moved=true;invalidate();return true;}});}
        void reset(){scale=1;offsetX=offsetY=0;invalidate();}
        private float base(){float maxX=1,maxY=1;for(PointF p:positions.values()){maxX=Math.max(maxX,Math.abs(p.x));maxY=Math.max(maxY,Math.abs(p.y));}return Math.min((getWidth()-dp(70))/(2*maxX),(getHeight()-dp(60))/(2*maxY));}
        private PointF screen(PointF p){float factor=base()*scale;return new PointF(getWidth()/2f+offsetX+p.x*factor,getHeight()/2f+offsetY+p.y*factor);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(dp(1));pen.setColor(Color.BLACK);canvas.drawRect(1,1,getWidth()-1,getHeight()-1,pen);
            for(int i=0;i<edges.length();i++){JSONObject edge=edges.optJSONObject(i);PointF from=positions.get(edge.optString("from")),to=positions.get(edge.optString("to"));if(from==null||to==null)continue;PointF a=screen(from),b=screen(to);canvas.drawLine(a.x,a.y,b.x,b.y,pen);float angle=(float)Math.atan2(b.y-a.y,b.x-a.x),x=b.x-(float)Math.cos(angle)*dp(10),y=b.y-(float)Math.sin(angle)*dp(10);canvas.drawLine(x,y,x-(float)Math.cos(angle-.5)*dp(8),y-(float)Math.sin(angle-.5)*dp(8),pen);canvas.drawLine(x,y,x-(float)Math.cos(angle+.5)*dp(8),y-(float)Math.sin(angle+.5)*dp(8),pen);}
            for(int i=0;i<nodes.length();i++){JSONObject node=nodes.optJSONObject(i);PointF p=screen(positions.get(node.optString("id")));pen.setStyle(Paint.Style.FILL);pen.setColor(Color.WHITE);canvas.drawCircle(p.x,p.y,dp(9),pen);pen.setStyle(Paint.Style.STROKE);pen.setColor(Color.BLACK);canvas.drawCircle(p.x,p.y,dp(9),pen);pen.setStyle(Paint.Style.FILL);pen.setTextSize(dp(11));String name=node.optString("name");if(name.length()>30)name=name.substring(0,29)+"…";canvas.drawText(name,p.x+dp(12),p.y+dp(4),pen);}
        }
        @Override public boolean onTouchEvent(MotionEvent event){zoom.onTouchEvent(event);switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:downX=lastX=event.getX();downY=lastY=event.getY();moved=false;getParent().requestDisallowInterceptTouchEvent(true);return true;
            case MotionEvent.ACTION_MOVE:if(!zoom.isInProgress()){offsetX+=event.getX()-lastX;offsetY+=event.getY()-lastY;if(Math.hypot(event.getX()-downX,event.getY()-downY)>dp(6))moved=true;invalidate();}lastX=event.getX();lastY=event.getY();return true;
            case MotionEvent.ACTION_UP:if(!moved){float nearest=dp(30);JSONObject selected=null;for(int i=0;i<nodes.length();i++){JSONObject node=nodes.optJSONObject(i);PointF p=screen(positions.get(node.optString("id")));float distance=(float)Math.hypot(event.getX()-p.x,event.getY()-p.y);if(distance<nearest){nearest=distance;selected=node;}}if(selected!=null)nodeDetails(selected);performClick();}getParent().requestDisallowInterceptTouchEvent(false);return true;
            case MotionEvent.ACTION_CANCEL:getParent().requestDisallowInterceptTouchEvent(false);return true;
            default:return true;
        }}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
