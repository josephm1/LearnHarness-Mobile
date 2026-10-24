package ai.learnharness.android;
import org.junit.Test;import org.json.*;import static org.junit.Assert.*;
public class RoleConnectionsTest {
 private JSONObject api(String model)throws Exception{return new JSONObject().put("billing","api").put("provider","Anthropic").put("endpoint","https://api.anthropic.com/v1").put("model",model).put("thinking","default");}
 @Test public void resolvesRoleBeforeSlotBeforeActiveWithoutMutatingSettings()throws Exception{JSONObject routes=new JSONObject().put("cheap",api("cheap-model")).put("extractor",api("extract-model")),active=api("active");assertEquals("extract-model",RoleConnections.resolve(routes,"extractor",active).getString("model"));assertEquals("cheap-model",RoleConnections.resolve(routes,"auditor",active).getString("model"));assertEquals("active",RoleConnections.resolve(routes,"teacher",active).getString("model"));assertEquals("cheap-model",routes.getJSONObject("cheap").getString("model"));}
 @Test public void usesOriginalSlotMapping(){assertEquals("strong",RoleConnections.slot("prober"));assertEquals("cheap",RoleConnections.slot("demoBuilder"));assertEquals("vision",RoleConnections.slot("visualMaker"));assertEquals("vision",RoleConnections.slot("pdfReader"));}
 @Test(expected=java.io.IOException.class)public void rejectsEmbeddedSecrets()throws Exception{RoleConnections.validate(api("model").put("key","must-never-be-in-settings"));}
 @Test(expected=java.io.IOException.class)public void rejectsCredentialUrl()throws Exception{RoleConnections.validate(api("model").put("endpoint","https://secret@example.com/v1"));}
 @Test public void planBillingRemainsExplicit()throws Exception{JSONObject plan=new JSONObject().put("billing","plan").put("account","registration").put("model","chosen-model");assertEquals("plan",RoleConnections.validate(plan).getString("billing"));assertTrue(RoleConnections.label(plan).startsWith("ChatGPT plan"));}
}
