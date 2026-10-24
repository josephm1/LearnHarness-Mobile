package ai.learnharness.android;
import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Agora-compatible portable JSON templates; preserve fields when round-tripping. */
public final class InteractiveTemplates {
    private final AtomicFile file;
    public InteractiveTemplates(Context context){file=new AtomicFile(new File(context.getFilesDir(),"interactive-templates.json"));}
    public static JSONObject validate(JSONObject template)throws Exception{
        if(!template.optString("kind").equals("agora.interactive-template") || template.optInt("version",1)!=1)throw new IOException("Choose an Agora interactive template or catalogue.");
        if(!UUID.fromString(template.getString("id")).toString().equals(template.getString("id")))throw new IOException("Invalid template ID.");
        String name=template.getString("name");if(name.trim().isEmpty() || name.length()>200)throw new IOException("Template name must contain 1–200 characters.");
        if(template.isNull("shortcut"))template.remove("shortcut");
        String shortcut=template.optString("shortcut","");if(!shortcut.isEmpty() && !shortcut.matches("[a-z][a-z0-9-]{0,63}"))throw new IOException("Invalid template shortcut.");
        JSONObject answer=template.getJSONObject("answer");if(!answer.optString("kind","agora.interactive-answer").equals("agora.interactive-answer") || answer.optInt("version",1)!=1)throw new IOException("Unsupported interactive answer.");
        for(String key:new String[]{"title","description","fallback","html"})if(answer.optString(key).trim().isEmpty())throw new IOException("Interactive answer is missing "+key+".");
        if(answer.toString().getBytes(StandardCharsets.UTF_8).length>1024*1024 || answer.optJSONArray("jsExpressions")!=null && answer.getJSONArray("jsExpressions").length()>100)throw new IOException("Interactive answer is too large.");
        return template;
    }
    public synchronized JSONArray list()throws Exception{
        if(!file.getBaseFile().exists())return new JSONArray();
        try(InputStream in=file.openRead()){JSONArray rows=new JSONArray(new String(ProviderClient.readBounded(in,16_000_000),StandardCharsets.UTF_8));for(int i=0;i<rows.length();i++)validate(rows.getJSONObject(i));return rows;}
    }
    public synchronized JSONObject get(String id)throws Exception{JSONArray rows=list();for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(id))return rows.getJSONObject(i);throw new IOException("Template not found.");}
    private void write(JSONArray rows)throws Exception{byte[] bytes=rows.toString(2).getBytes(StandardCharsets.UTF_8);if(bytes.length>16_000_000)throw new IOException("Template catalogue exceeds 16 MB.");FileOutputStream out=null;try{out=file.startWrite();out.write(bytes);file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}}
    public synchronized void save(JSONObject template,boolean replace)throws Exception{
        validate(template);JSONArray rows=list();int existing=-1;
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.getString("id").equals(template.getString("id")))existing=i;else if(!template.optString("shortcut").isEmpty() && row.optString("shortcut").equals(template.optString("shortcut")))throw new IOException("Another template already uses that shortcut.");}
        if(existing>=0 && !replace)throw new IOException("This template already exists. Rename or delete it before importing a replacement.");if(existing<0)rows.put(template);else rows.put(existing,template);write(rows);
    }
    public synchronized void importCatalogue(String text)throws Exception{
        Object value=new JSONTokener(text).nextValue();JSONArray incoming=value instanceof JSONArray?(JSONArray)value:new JSONArray().put((JSONObject)value);JSONArray merged=list();Set<String> ids=new HashSet<>(),shortcuts=new HashSet<>();for(int i=0;i<merged.length();i++){JSONObject row=merged.getJSONObject(i);ids.add(row.getString("id"));if(!row.optString("shortcut").isEmpty())shortcuts.add(row.getString("shortcut"));}
        for(int i=0;i<incoming.length();i++){JSONObject row=validate(incoming.getJSONObject(i));if(!ids.add(row.getString("id")))throw new IOException("Template ID conflict: "+row.getString("name")+". Nothing was imported.");if(!row.optString("shortcut").isEmpty() && !shortcuts.add(row.getString("shortcut")))throw new IOException("Template shortcut conflict. Nothing was imported.");merged.put(row);}write(merged);
    }
    public synchronized void delete(String id)throws Exception{JSONArray rows=list();for(int i=rows.length()-1;i>=0;i--)if(rows.getJSONObject(i).getString("id").equals(id))rows.remove(i);write(rows);}
    public static JSONObject sample()throws Exception{
        JSONObject answer=new JSONObject().put("kind","agora.interactive-answer").put("version",1).put("title","Probability explorer").put("description","Explore a probability with a slider").put("fallback","A probability ranges from 0 to 100 percent. At 50 percent, the two outcomes are equally likely.").put("eInkAware",true)
            .put("html","<main><h1>Probability explorer</h1><p>Adjust the probability and compare the two outcomes.</p><label for='chance'>Probability</label><input id='chance' type='range' min='0' max='100' value='50'><p id='value' aria-live='polite'>50% chance</p><svg viewBox='0 0 300 42' role='img' aria-label='Probability bar'><rect x='1' y='1' width='298' height='40' fill='white' stroke='black'/><rect id='bar' x='2' y='2' width='148' height='38' fill='black'/></svg></main>")
            .put("css","body{background:white;color:black;font:18px sans-serif;padding:16px}main{max-width:650px;margin:auto}input{width:100%;min-height:48px}svg{width:100%}")
            .put("jsFunctions","const control=document.getElementById('chance');control.addEventListener('input',()=>{document.getElementById('value').textContent=control.value+'% chance';document.getElementById('bar').setAttribute('width',2.96*control.value);});");
        return new JSONObject().put("kind","agora.interactive-template").put("version",1).put("id",UUID.randomUUID().toString()).put("name","Probability explorer").put("description","Explore probability visually").put("shortcut","probability").put("answer",answer);
    }
}
