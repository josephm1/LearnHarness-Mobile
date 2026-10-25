package ai.learnharness.android;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ShareDestinationsTest {
    private JSONObject root()throws Exception{return Records.empty();}
    private JSONObject share(String id,String text)throws Exception{return new JSONObject().put("id",id).put("title","A shared note").put("text",text);}
    @Test public void newLearnAndPracticeTargetsAreDraftsWithoutModelHistory()throws Exception{
        for(boolean practice:new boolean[]{false,true}){
            JSONObject store=root(),target=ShareDestinations.attach(store,share("share","My shared text"),new JSONArray(),"",practice,123);
            assertEquals("My shared text",target.getString("draft"));assertEquals(0,target.getJSONArray("messages").length());assertFalse(target.has("agentHistory"));assertEquals(practice,target.optString("kind").equals("practice"));assertEquals(123,target.getLong("created"));
            Records.validate(store);
        }
    }
    @Test public void existingDraftIsPreservedDocumentsAreLinkedAndReplayIsIdempotent()throws Exception{
        JSONObject store=root(),target=ShareDestinations.attach(store,share("first","Original draft"),new JSONArray(),"",false,1);
        JSONArray docs=new JSONArray().put(new JSONObject().put("id","pdf").put("title","Notes.pdf").put("kind","PDF").put("uri","content://fixture/notes"));JSONObject incoming=share("second","Extra text");
        ShareDestinations.attach(store,incoming,docs,target.getString("id"),false,2);ShareDestinations.attach(store,incoming,docs,target.getString("id"),false,3);
        assertEquals("Original draft\n\nExtra text",target.getString("draft"));assertEquals(1,target.getJSONArray("documents").length());assertEquals(1,store.getJSONArray("documents").length());assertEquals(0,target.getJSONArray("messages").length());Records.validate(store);
    }
    @Test public void chooserPartitionsByTabAndOrdersRecentVisitsFirst()throws Exception{
        JSONObject store=root();JSONObject older=ShareDestinations.attach(store,share("one","A"),new JSONArray(),"",false,1),newer=ShareDestinations.attach(store,share("two","B"),new JSONArray(),"",false,2),practice=ShareDestinations.attach(store,share("three","C"),new JSONArray(),"",true,3);
        assertEquals(newer,ShareDestinations.recent(store.getJSONArray("sessions"),false,Collections.emptyMap()).get(0));
        assertEquals(older,ShareDestinations.recent(store.getJSONArray("sessions"),false,Map.of(older.getString("id"),10L)).get(0));
        assertEquals(List.of(practice),ShareDestinations.recent(store.getJSONArray("sessions"),true,Collections.emptyMap()));
    }
    @Test public void cannotRouteATabToTheWrongKindOfConversation()throws Exception{
        JSONObject store=root(),target=ShareDestinations.attach(store,share("one","A"),new JSONArray(),"",true,1);
        try{ShareDestinations.attach(store,share("two","B"),new JSONArray(),target.getString("id"),false,2);fail();}catch(JSONException expected){}
        assertEquals("A",target.getString("draft"));
    }
    @Test public void incomingDocumentTypesFollowSupportedLibraryKinds()throws Exception{
        assertEquals("PDF",SharedContent.kind("Notes.pdf","application/octet-stream"));assertEquals("Text",SharedContent.kind("note.md",null));assertEquals("EPUB",SharedContent.kind("book","application/epub+zip"));
        try{SharedContent.kind("program.apk","application/vnd.android.package-archive");fail();}catch(java.io.IOException expected){}
    }
}
