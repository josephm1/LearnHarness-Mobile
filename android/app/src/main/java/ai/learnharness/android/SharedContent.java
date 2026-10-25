package ai.learnharness.android;

import android.content.*;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.AtomicFile;
import androidx.core.content.FileProvider;
import androidx.core.content.IntentCompat;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Copies incoming files while their temporary Android grants are still valid. */
final class SharedContent {
    static final String EXTRA="ai.learnharness.android.SHARED_CONTENT";
    static final long FILE_LIMIT=64L*1024*1024,GROUP_LIMIT=128L*1024*1024;
    private final Context context;
    SharedContent(Context context){this.context=context;}
    File folder(String id)throws IOException{
        try{if(!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException();}catch(Exception error){throw new IOException("Invalid shared-content reference.");}
        return new File(context.getFilesDir(),"shared-documents/"+id);
    }
    JSONObject load(String id)throws Exception{
        File file=new File(folder(id),"share.json");if(!file.isFile())return null;
        try(InputStream in=new AtomicFile(file).openRead()){return new JSONObject(new String(ProviderClient.readBounded(in,256000),StandardCharsets.UTF_8));}
    }
    void save(JSONObject share)throws Exception{
        File directory=folder(share.getString("id"));if(!directory.exists() && !directory.mkdirs())throw new IOException("Could not prepare shared content.");
        AtomicFile file=new AtomicFile(new File(directory,"share.json"));FileOutputStream out=null;try{out=file.startWrite();out.write(share.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception error){if(out!=null)file.failWrite(out);throw error;}
    }
    static String kind(String title,String mime)throws IOException{
        String lower=title.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".pdf") || "application/pdf".equals(mime))return "PDF";
        if(lower.endsWith(".epub") || "application/epub+zip".equals(mime))return "EPUB";
        if(lower.matches(".*\\.(txt|md|markdown)$") || mime!=null && mime.startsWith("text/"))return "Text";
        throw new IOException("Share a PDF, EPUB, text or Markdown document.");
    }
    JSONObject receive(String id,Intent intent)throws Exception{
        JSONObject existing=load(id);if(existing!=null)return existing;
        if(!Intent.ACTION_SEND.equals(intent.getAction()) && !Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction()))throw new IOException("Use Android’s Share menu to send content here.");
        CharSequence supplied=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);String text=supplied==null?"":supplied.toString();
        if(text.isEmpty() && intent.getStringExtra(Intent.EXTRA_HTML_TEXT)!=null)text=android.text.Html.fromHtml(intent.getStringExtra(Intent.EXTRA_HTML_TEXT),android.text.Html.FROM_HTML_MODE_COMPACT).toString();
        if(text.isEmpty() && intent.getClipData()!=null){StringBuilder clips=new StringBuilder();for(int i=0;i<intent.getClipData().getItemCount();i++){CharSequence value=intent.getClipData().getItemAt(i).getText();if(value!=null){if(clips.length()>0)clips.append("\n\n");clips.append(value);}}text=clips.toString();}
        if(text.length()>100000)throw new IOException("Share up to 100,000 characters at a time.");
        LinkedHashSet<Uri> uris=new LinkedHashSet<>();
        if(Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())){ArrayList<Uri> streams=IntentCompat.getParcelableArrayListExtra(intent,Intent.EXTRA_STREAM,Uri.class);if(streams!=null)uris.addAll(streams);}
        else{Uri stream=IntentCompat.getParcelableExtra(intent,Intent.EXTRA_STREAM,Uri.class);if(stream!=null)uris.add(stream);}
        if(intent.getClipData()!=null)for(int i=0;i<intent.getClipData().getItemCount();i++){Uri uri=intent.getClipData().getItemAt(i).getUri();if(uri!=null)uris.add(uri);}
        if(uris.isEmpty() && intent.getData()!=null)uris.add(intent.getData());
        if(uris.size()>8)throw new IOException("Share up to eight documents at a time.");
        File directory=folder(id);if(!directory.exists() && !directory.mkdirs())throw new IOException("Could not store shared documents.");
        JSONObject share=new JSONObject().put("id",id).put("text",text).put("title",intent.getStringExtra(Intent.EXTRA_SUBJECT)==null?"":intent.getStringExtra(Intent.EXTRA_SUBJECT));JSONArray files=new JSONArray();long total=0;int index=0;
        try{
            for(Uri uri:uris){
                if(!"content".equals(uri.getScheme()))throw new IOException("Share documents through an Android content provider.");
                String title="Shared document",mime=context.getContentResolver().getType(uri);
                try(android.database.Cursor cursor=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null && cursor.moveToFirst() && cursor.getString(0)!=null)title=cursor.getString(0);}
                String kind=kind(title,mime),name="file-"+(index++)+(kind.equals("PDF")?".pdf":kind.equals("EPUB")?".epub":".txt");File output=new File(directory,name);long length=0;
                try(InputStream input=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(output)){
                    if(input==null)throw new IOException("The sharing app could not open this document.");byte[] buffer=new byte[32768];int count;
                    while((count=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();length+=count;total+=count;if(length>FILE_LIMIT || total>GROUP_LIMIT)throw new IOException("Shared files are limited to 64 MiB each and 128 MiB in total.");out.write(buffer,0,count);}
                }
                files.put(new JSONObject().put("title",title).put("kind",kind).put("file",name));
            }
            if(text.trim().isEmpty() && files.length()==0)throw new IOException("The share did not contain text or documents.");
            if(share.optString("title").isEmpty() && files.length()>0)share.put("title",files.getJSONObject(0).getString("title"));
            share.put("files",files);save(share);return share;
        }catch(Exception error){delete(id);throw error;}
    }
    JSONArray documents(JSONObject share)throws Exception{
        File directory=folder(share.getString("id"));JSONArray files=share.getJSONArray("files"),result=new JSONArray();
        for(int i=0;i<files.length();i++){
            JSONObject item=files.getJSONObject(i);File file=new File(directory,item.getString("file"));if(!file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile()) || !file.isFile())throw new IOException("A shared document is no longer available.");
            Uri uri=FileProvider.getUriForFile(context,context.getPackageName()+".sharedfiles",file);result.put(new JSONObject().put("id",share.getString("id")+"-"+i).put("title",item.getString("title")).put("kind",item.getString("kind")).put("uri",uri.toString()));
        }return result;
    }
    void delete(String id){try{File directory=folder(id);File[] files=directory.listFiles();if(files!=null)for(File file:files)if(file.isFile())file.delete();directory.delete();}catch(Exception ignored){}}
}
