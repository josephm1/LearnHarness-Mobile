package ai.learnharness.android;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class InteractiveTemplatesTest {
    @Test public void acceptsAgoraFormatAndRetainsTemplateMetadata()throws Exception{JSONObject template=InteractiveTemplates.sample().put("linkedSkill","study.md").put("resourceFiles",new JSONArray().put("book.pdf"));assertEquals("study.md",InteractiveTemplates.validate(template).getString("linkedSkill"));assertEquals("book.pdf",template.getJSONArray("resourceFiles").getString(0));}
    @Test(expected=java.io.IOException.class)public void requiresReadableFallback()throws Exception{JSONObject template=InteractiveTemplates.sample();template.getJSONObject("answer").remove("fallback");InteractiveTemplates.validate(template);}
    @Test(expected=java.io.IOException.class)public void rejectsDifferentTemplateKind()throws Exception{InteractiveTemplates.validate(InteractiveTemplates.sample().put("kind","other"));}
    @Test(expected=java.io.IOException.class)public void rejectsInvalidShortcut()throws Exception{InteractiveTemplates.validate(InteractiveTemplates.sample().put("shortcut","../../secret"));}
    @Test public void rendererUsesExactAssetAndNetworkAllowlist(){assertEquals("host.js",InteractiveActivity.asset("https://appassets.androidplatform.net/interactive/host.js"));assertNull(InteractiveActivity.asset("https://appassets.androidplatform.net/interactive/../private.xml"));assertNull(InteractiveActivity.asset("https://other.test/interactive/host.js"));assertFalse(InteractiveActivity.allowedNetwork("file:///private"));assertFalse(InteractiveActivity.allowedNetwork("https://api.openai.com/v1/responses"));assertTrue(InteractiveActivity.allowedNetwork("https://cdn.jsdelivr.net/npm/example"));}
}
