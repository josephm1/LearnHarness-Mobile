package ai.learnharness.android;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class LearningContractTest {
    @Test public void acceptsFencedJson() throws Exception { assertEquals(1, LearningContract.object("```json\n{\"n\":1}\n```").getInt("n")); }
    @Test(expected=JSONException.class) public void rejectsEmptyPractice() throws Exception { LearningContract.questions(new JSONArray()); }
    @Test(expected=JSONException.class) public void rejectsMissingAnswer() throws Exception { LearningContract.questions(new JSONArray("[{\"question\":\"Why?\",\"hint\":\"Think\"}]")); }
    @Test(expected=JSONException.class) public void rejectsInvalidPremises() throws Exception { LearningContract.claims(new JSONObject("{\"claims\":[{\"title\":\"A\",\"statement\":\"B\",\"premises\":[42]}]}")); }
    @Test public void validatesFullPracticeBeforeUse() throws Exception { assertEquals(1, LearningContract.questions(new JSONArray("[{\"question\":\"Why?\",\"answer\":\"Because\",\"hint\":\"Think\"}]")).length()); }
    @Test(expected=JSONException.class) public void rejectsDesktopBackup() throws Exception { Records.validate(new JSONObject("{\"version\":1,\"sessions\":[]}")); }
    @Test public void acceptsEmptyAndroidBackup() throws Exception { Records.validate(Records.empty()); }
    @Test(expected=JSONException.class) public void rejectsDuplicateIds() throws Exception {
        JSONObject backup = Records.empty(); JSONObject note = new JSONObject("{\"id\":\"a\",\"title\":\"A\",\"statement\":\"B\",\"premises\":[]}");
        backup.getJSONArray("knowledge").put(note).put(note); Records.validate(backup);
    }
    @Test public void folderReferencesRoundTripWithoutDocumentText()throws Exception{JSONObject backup=Records.empty().put("sourceTrees",new JSONArray().put(new JSONObject().put("id","folder").put("title","Books").put("uri","content://example.documents/tree/books")));Records.validate(new JSONObject(backup.toString()));assertFalse(backup.toString().contains("text"));}
    @Test(expected=JSONException.class) public void folderReferencesRejectOrdinaryFileUris()throws Exception{Records.validate(Records.empty().put("sourceTrees",new JSONArray().put(new JSONObject().put("id","folder").put("title","Books").put("uri","file:///books"))));}
}
