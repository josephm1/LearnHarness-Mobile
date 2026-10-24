package ai.learnharness.android;
import org.json.*;import java.net.URI;import java.util.*;
/** Make native search citations visible/clickable without changing the original provider history. */
public final class WebCitations {
 private WebCitations(){}
 public static JSONArray sources(ProviderClient.Reply reply)throws Exception{
  JSONArray output=reply.nativeOutput()==null?null:reply.nativeOutput().optJSONArray("responses");LinkedHashMap<String,JSONObject> links=new LinkedHashMap<>();
  for(int i=0;output!=null && i<output.length();i++){JSONArray content=output.getJSONObject(i).optJSONArray("content");for(int j=0;content!=null && j<content.length();j++){JSONArray annotations=content.getJSONObject(j).optJSONArray("annotations");for(int k=0;annotations!=null && k<annotations.length();k++){JSONObject citation=annotations.getJSONObject(k);if(!citation.optString("type").equals("url_citation"))continue;String url=citation.optString("url");try{URI uri=new URI(url);if(!Arrays.asList("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null)continue;}catch(Exception invalid){continue;}if(links.size()<100)links.putIfAbsent(url,new JSONObject().put("url",url).put("title",citation.optString("title",url)));}}}
  JSONArray result=new JSONArray();for(JSONObject link:links.values())result.put(link);return result;
 }
 public static ProviderClient.Reply readable(ProviderClient.Reply reply)throws Exception{
  JSONArray links=sources(reply);if(links.length()==0)return reply;String text=reply.text().replaceAll("\uE200cite\uE202[^\uE201]*\uE201","").trim();StringBuilder result=new StringBuilder(text);result.append("\n\nSources:\n");for(int i=0;i<links.length();i++){JSONObject link=links.getJSONObject(i);String title=link.optString("title").replace('\\',' ').replace('[','(').replace(']',')').replace('\n',' ').replace('\r',' ');if(title.trim().isEmpty())title="Source "+(i+1);String url=link.getString("url").replace("(","%28").replace(")","%29");result.append("\n- [").append(title).append("](").append(url).append(')');}return new ProviderClient.Reply(result.toString(),reply.inputTokens(),reply.outputTokens(),reply.toolCalls(),reply.nativeOutput());
 }
}
