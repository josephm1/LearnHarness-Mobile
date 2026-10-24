package ai.learnharness.android;
import android.content.Context;import android.database.Cursor;import android.net.Uri;import android.provider.DocumentsContract;import org.json.*;import java.io.*;import java.util.*;
/** On-demand directory metadata only. No file bodies or recursive index are read. */
public final class DocumentTrees {
 private DocumentTrees(){}
 public static JSONArray list(Context context,Uri tree,String parent)throws Exception{
  if(!"content".equals(tree.getScheme()) || !DocumentsContract.isTreeUri(tree))throw new IOException("Select a folder through Android Files.");
  Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent);ArrayList<JSONObject> entries=new ArrayList<>();
  try(Cursor cursor=context.getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
   if(cursor==null)throw new IOException("The folder provider could not list its files.");while(cursor.moveToNext()){
    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Folder browsing cancelled.");if(entries.size()==500)throw new IOException("This folder has more than 500 entries. Select a smaller subfolder in Android Files.");String id=cursor.getString(0),name=cursor.getString(1),mime=cursor.getString(2);entries.add(new JSONObject().put("id",id).put("title",name==null?"Untitled":name).put("directory",DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)).put("mime",mime==null?"":mime).put("uri",DocumentsContract.buildDocumentUriUsingTree(tree,id).toString()));
   }
  }
  entries.sort((a,b)->{int folders=Boolean.compare(b.optBoolean("directory"),a.optBoolean("directory"));return folders!=0?folders:a.optString("title").compareToIgnoreCase(b.optString("title"));});JSONArray result=new JSONArray();for(JSONObject entry:entries)result.put(entry);return result;
 }
 public static String rootId(Uri tree){return DocumentsContract.getTreeDocumentId(tree);}
 public static String title(Context context,Uri tree)throws Exception{try(Cursor cursor=context.getContentResolver().query(DocumentsContract.buildDocumentUriUsingTree(tree,rootId(tree)),new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){if(cursor!=null && cursor.moveToFirst() && cursor.getString(0)!=null)return cursor.getString(0);}return "Book folder";}
}
