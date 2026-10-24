package ai.learnharness.android;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/** Agent-facing virtual paths over explicitly linked file references. This class never opens a document. */
public final class SourceTools {
    private SourceTools(){}
    public static final Set<String> NAMES=new HashSet<>(Arrays.asList("ls","find","grep","read"));
    public static String path(JSONObject document){return document.optString("id")+"/"+document.optString("title").replace('/','_').replace('\\','_').replace('\n',' ').replace('\r',' ');}
    public static JSONArray eligible(JSONObject session,String requested)throws Exception{
        String path=requested==null?"":requested.trim().replaceFirst("^\\./","");JSONArray files=session.optJSONArray("sourceFiles"),eligible=new JSONArray();boolean root=path.isEmpty() || path.equals("/") || path.equals(".") || path.equals("~") || path.equals("/library");
        for(int i=0;files!=null && i<files.length();i++){JSONObject file=files.getJSONObject(i);String virtual=path(file);if(root || path.equals(virtual) || path.equals("/"+virtual) || path.equals(file.getString("id")) || path.equals(file.getString("title")))eligible.put(file);}return eligible;
    }
    public static Object executeMetadata(JSONObject session,String name,JSONObject input)throws Exception{
        JSONArray files=eligible(session,input.optString("path"));if(files.length()==0)return "No linked sources at this path. The learner can link a document with the attachment button.";
        ArrayList<String> paths=new ArrayList<>();Pattern glob=name.equals("find")?glob(input.getString("pattern")):null;for(int i=0;i<files.length();i++){JSONObject doc=files.getJSONObject(i);String path=path(doc);if(glob==null || glob.matcher(path).matches() || glob.matcher(doc.getString("title")).matches())paths.add(path);}paths.sort(String.CASE_INSENSITIVE_ORDER);
        int limit=Math.max(1,Math.min(name.equals("find")?1000:500,input.optInt("limit",name.equals("find")?1000:500)));StringBuilder result=new StringBuilder();for(int i=0;i<Math.min(paths.size(),limit);i++)result.append(paths.get(i)).append('\n');if(paths.size()>limit)result.append("[Entry limit reached]");return result.length()==0?"No linked file matches this pattern.":result.toString();
    }
    public static JSONObject pending(JSONObject session,JSONObject call,JSONObject input)throws Exception{
        JSONArray matches=eligible(session,input.optString("path"));if(matches.length()==0)throw new IOException("The requested file is not linked to this session. Ask the learner to attach it first.");return new JSONObject().put("kind","source").put("id",call.getString("id")).put("call",call).put("tool",call.getJSONObject("function").getString("name")).put("request",input).put("candidates",matches);
    }
    private static Pattern glob(String value)throws IOException{if(value.length()>500)throw new IOException("File pattern is too long.");StringBuilder regex=new StringBuilder("^");for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c=='*')regex.append(".*");else if(c=='?')regex.append('.');else regex.append(Pattern.quote(String.valueOf(c)));}return Pattern.compile(regex.append('$').toString(),Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);}
}
