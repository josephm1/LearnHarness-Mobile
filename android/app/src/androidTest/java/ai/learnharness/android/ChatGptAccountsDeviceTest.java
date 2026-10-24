package ai.learnharness.android;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Isolated preferences + actual Android Keystore; no live account or network access. */
@RunWith(AndroidJUnit4.class)
public class ChatGptAccountsDeviceTest {
    Context context; SecretStore vault;
    @Before public void isolatedStorage() {
        String prefix="oauth-test-"+UUID.randomUUID()+"-";
        context=new ContextWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext()) {
            @Override public SharedPreferences getSharedPreferences(String name,int mode){ return super.getSharedPreferences(prefix+name,mode); }
        };
        vault=new SecretStore(context);
    }
    private JSONObject row(String id,long expires) throws Exception {
        return new JSONObject().put("client_id",id).put("subject","learner-"+id).put("email","test@example.invalid")
            .put("access_token","qa-access-"+id).put("refresh_token","qa-refresh-"+id).put("id_token","qa-id-"+id)
            .put("scope",OAuthProtocol.SCOPES).put("expires_at",expires).put("model","model-test")
            .put("models",new JSONArray().put(new JSONObject().put("slug","model-test").put("display_name","Test model")));
    }
    private ChatGptAccounts seed(JSONObject... rows) throws Exception {
        JSONArray all=new JSONArray();for(JSONObject row:rows)all.put(row);
        vault.set("chatgpt-accounts",new JSONObject().put("accounts",all).put("active",rows[0].getString("client_id")).toString());
        return new ChatGptAccounts(context);
    }
    private static class FakeHttp extends OpenAiHttp {
        AtomicInteger calls=new AtomicInteger();Map<String,String> last;
        Failure failure; boolean revokeFailure;
        @Override public JSONObject form(String url,Map<String,String> values) throws Exception {
            calls.incrementAndGet();last=new HashMap<>(values);
            if(failure!=null)throw failure;
            if(url.endsWith("/revoke")) { if(revokeFailure)throw new java.io.IOException("offline");return new JSONObject(); }
            return new JSONObject().put("access_token","qa-new-access").put("refresh_token","qa-new-refresh").put("token_type","Bearer").put("expires_in",3600);
        }
        @Override public JSONObject get(String url,String token) throws Exception {
            if(url.endsWith("openid-configuration"))return new JSONObject().put("issuer",AUTH).put("revocation_endpoint",AUTH+"/api/accounts/oauth/revoke");
            return new JSONObject().put("models",new JSONArray().put(new JSONObject().put("slug","visible").put("display_name","Available model").put("visibility","list"))
                .put(new JSONObject().put("slug","hidden").put("visibility","hidden")));
        }
    }
    @Test public void encryptsAndRotatesCredentialsTogether() throws Exception {
        ChatGptAccounts accounts=seed(row("client-one",1));FakeHttp http=new FakeHttp();
        assertEquals("qa-new-access",accounts.token("client-one",http));assertEquals("client-one",http.last.get("client_id"));assertFalse(http.last.containsKey("scope"));
        JSONObject saved=new JSONObject(vault.get("chatgpt-accounts")).getJSONArray("accounts").getJSONObject(0);
        assertEquals("qa-new-refresh",saved.getString("refresh_token"));assertEquals("model-test",saved.getString("model"));
        assertFalse(context.getSharedPreferences("secrets",0).getString("chatgpt-accounts","").contains("qa-new-access"));
        assertFalse(accounts.accounts().toString().contains("qa-new-refresh"));
    }
    @Test public void serializesConcurrentRefresh() throws Exception {
        ChatGptAccounts accounts=seed(row("client-one",1));FakeHttp http=new FakeHttp();ExecutorService workers=Executors.newFixedThreadPool(2);
        try {
            Future<String> first=workers.submit(()->accounts.token("client-one",http)),second=workers.submit(()->accounts.token("client-one",http));
            assertEquals("qa-new-access",first.get(10,TimeUnit.SECONDS));assertEquals("qa-new-access",second.get(10,TimeUnit.SECONDS));assertEquals(1,http.calls.get());
        }finally{workers.shutdownNow();}
    }
    @Test public void terminalRefreshClearsOnlySelectedCredentials() throws Exception {
        ChatGptAccounts accounts=seed(row("client-one",1),row("client-two",Long.MAX_VALUE));FakeHttp http=new FakeHttp();http.failure=new OpenAiHttp.Failure(400,"invalid_grant","","","OAuth error");
        try{accounts.token("client-one",http);fail();}catch(OpenAiHttp.Failure expected){}
        assertFalse(accounts.active().getBoolean("connected"));assertEquals(2,accounts.accounts().length());
        assertEquals("qa-access-client-two",accounts.token("client-two",new FakeHttp()));
    }
    @Test public void temporaryFailurePreservesTokens() throws Exception {
        ChatGptAccounts accounts=seed(row("client-one",1));FakeHttp http=new FakeHttp();http.failure=new OpenAiHttp.Failure(503,"","","","detail");
        try{accounts.token("client-one",http);fail();}catch(OpenAiHttp.Failure expected){}
        assertTrue(accounts.active().getBoolean("connected"));assertTrue(vault.get("chatgpt-accounts").contains("qa-refresh-client-one"));
    }
    @Test public void noPlanScopeStopsBeforeNetwork() throws Exception {
        ChatGptAccounts accounts=seed(row("client-one",1).put("scope","openid email"));FakeHttp http=new FakeHttp();
        try{accounts.token("client-one",http);fail();}catch(java.io.IOException expected){}
        assertEquals(0,http.calls.get());assertTrue(accounts.active().getBoolean("connected"));assertFalse(accounts.active().getBoolean("plan"));
    }
    @Test public void signOutRevokesRefreshAndRetainsRegistration() throws Exception {
        ChatGptAccounts accounts=seed(row("client-one",Long.MAX_VALUE));FakeHttp http=new FakeHttp();
        assertTrue(accounts.signOut("client-one",http));assertEquals("qa-refresh-client-one",http.last.get("token"));assertEquals("refresh_token",http.last.get("token_type_hint"));
        assertFalse(accounts.active().getBoolean("connected"));assertEquals("client-one",accounts.active().getString("client_id"));assertFalse(vault.get("chatgpt-accounts").contains("qa-access-client-one"));
    }
    @Test public void localSignOutStillClearsTokensWhenRemoteFails() throws Exception {
        ChatGptAccounts accounts=seed(row("client-one",Long.MAX_VALUE));FakeHttp http=new FakeHttp();http.revokeFailure=true;
        assertFalse(accounts.signOut("client-one",http));assertFalse(accounts.active().getBoolean("connected"));
    }
    @Test public void catalogAndModelsStayWithSelectedAccount() throws Exception {
        ChatGptAccounts accounts=seed(row("client-one",Long.MAX_VALUE),row("client-two",Long.MAX_VALUE));
        accounts.refreshModels("client-one",new FakeHttp());assertEquals(1,accounts.active().getJSONArray("models").length());assertEquals("",accounts.active().getString("model"));
        accounts.model("client-one","visible");accounts.select("client-two");assertEquals("model-test",accounts.active().getString("model"));
        accounts.select("client-one");assertEquals("visible",accounts.active().getString("model"));
    }

    private static class LoginHttp extends OpenAiHttp {
        String nonce;boolean noPlan=false,badNonce=false,expiredCode=false;java.security.KeyPair pair;JSONObject jwks;
        LoginHttp() throws Exception {
            java.security.KeyPairGenerator gen=java.security.KeyPairGenerator.getInstance("RSA");gen.initialize(2048);pair=gen.generateKeyPair();
            java.security.interfaces.RSAPublicKey pub=(java.security.interfaces.RSAPublicKey)pair.getPublic();
            jwks=new JSONObject().put("keys",new JSONArray().put(new JSONObject().put("kid","test").put("kty","RSA").put("alg","RS256").put("n",OAuthProtocol.base64(unsigned(pub.getModulus().toByteArray()))).put("e",OAuthProtocol.base64(unsigned(pub.getPublicExponent().toByteArray())))));
        }
        byte[] unsigned(byte[] bytes){return bytes[0]==0?Arrays.copyOfRange(bytes,1,bytes.length):bytes;}
        @Override public JSONObject form(String url,Map<String,String> values) throws Exception {
            if(expiredCode)throw new OpenAiHttp.Failure(400,"invalid_grant","","","OAuth error");
            assertEquals("authorization_code",values.get("grant_type"));assertEquals("test-client-issued",values.get("client_id"));assertEquals("mock-code",values.get("code"));
            String header=OAuthProtocol.base64("{\"alg\":\"RS256\",\"kid\":\"test\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            JSONObject claims=new JSONObject().put("iss",AUTH).put("aud","test-client-issued").put("sub","qa-learner").put("email","learner@example.invalid").put("exp",System.currentTimeMillis()/1000+3600).put("nonce",badNonce?"wrong":nonce);
            String payload=OAuthProtocol.base64(claims.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.security.Signature signature=java.security.Signature.getInstance("SHA256withRSA");signature.initSign(pair.getPrivate());signature.update((header+"."+payload).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return new JSONObject().put("access_token","qa-login-access").put("refresh_token","qa-login-refresh").put("id_token",header+"."+payload+"."+OAuthProtocol.base64(signature.sign())).put("token_type","Bearer").put("expires_in",3600).put("scope",noPlan?"openid email":OAuthProtocol.SCOPES);
        }
        @Override public JSONObject get(String url,String token) throws Exception {
            if(url.endsWith("openid-configuration"))return new JSONObject().put("issuer",AUTH).put("jwks_uri",AUTH+"/.well-known/jwks.json");
            if(url.endsWith("jwks.json"))return jwks;
            assertEquals("qa-login-access",token);
            return new JSONObject().put("models",new JSONArray().put(new JSONObject().put("slug","qa-model").put("display_name","QA model").put("visibility","list")));
        }
    }
    private void completeLocalCallback(ChatGptAccounts accounts,LoginHttp http) throws Exception {
        String authorize=accounts.startSignIn("",false);
        Map<String,String> params=OAuthProtocol.query(new java.net.URI(authorize).getRawQuery());http.nonce=params.get("nonce");
        java.net.URI callback=new java.net.URI(params.get("redirect_uri"));
        try(java.net.Socket socket=new java.net.Socket("127.0.0.1",callback.getPort())) {
            String request="GET /auth/callback?code=mock-code&state="+params.get("state")+"&client_id=test-client-issued HTTP/1.1\r\nHost: 127.0.0.1:"+callback.getPort()+"\r\n\r\n";
            socket.setSoTimeout(5000);socket.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            ProviderClient.readBounded(socket.getInputStream(),16000);
        }
        long deadline=System.currentTimeMillis()+15000;while(accounts.isSigningIn() && System.currentTimeMillis()<deadline)Thread.sleep(50);
        assertFalse("Sign-in should settle",accounts.isSigningIn());
    }
    @Test public void completeSignInValidatesIdentityAndLoadsCatalog() throws Exception {
        LoginHttp http=new LoginHttp();ChatGptAccounts accounts=new ChatGptAccounts(context,http);completeLocalCallback(accounts,http);
        assertTrue(accounts.active().getBoolean("connected"));assertTrue(accounts.active().getBoolean("plan"));assertEquals("qa-model",accounts.active().getJSONArray("models").getJSONObject(0).getString("slug"));
        assertEquals("",accounts.active().getString("model"));assertTrue(accounts.takeWelcome());assertFalse(accounts.takeWelcome());
        assertFalse(context.getSharedPreferences("secrets",0).getString("chatgpt-accounts","").contains("qa-login-refresh"));
    }
    @Test public void invalidNewIdentityCannotReplaceActiveAccount() throws Exception {
        seed(row("client-one",Long.MAX_VALUE));LoginHttp http=new LoginHttp();http.badNonce=true;
        ChatGptAccounts accounts=new ChatGptAccounts(context,http);completeLocalCallback(accounts,http);
        assertEquals("client-one",accounts.active().getString("client_id"));assertEquals(1,accounts.accounts().length());
    }
    @Test public void validIdentityWithoutPlanScopeIsSavedButDisabled() throws Exception {
        LoginHttp http=new LoginHttp();http.noPlan=true;ChatGptAccounts accounts=new ChatGptAccounts(context,http);completeLocalCallback(accounts,http);
        assertTrue(accounts.active().getBoolean("connected"));assertFalse(accounts.active().getBoolean("plan"));assertFalse(accounts.takeWelcome());
        FakeHttp inference=new FakeHttp();try{accounts.token("test-client-issued",inference);fail();}catch(java.io.IOException expected){}assertEquals(0,inference.calls.get());
    }

    @Test public void failedExchangeRetainsIssuedClientForRetryWithoutActivatingIt() throws Exception {
        LoginHttp http=new LoginHttp();http.expiredCode=true;ChatGptAccounts accounts=new ChatGptAccounts(context,http);completeLocalCallback(accounts,http);
        assertNull(accounts.active());assertEquals(0,accounts.accounts().length());assertEquals("test-client-issued",accounts.interruptedClient());
        Map<String,String> retry=OAuthProtocol.query(new java.net.URI(accounts.startSignIn(accounts.interruptedClient(),false)).getRawQuery());
        assertEquals("test-client-issued",retry.get("client_id"));assertFalse(retry.containsKey("agent_name_hint"));accounts.cancelSignIn();
    }
    @Test public void welcomeIsNotRepeatedAfterLaterSignIn() throws Exception {
        LoginHttp http=new LoginHttp();ChatGptAccounts accounts=new ChatGptAccounts(context,http);completeLocalCallback(accounts,http);assertTrue(accounts.takeWelcome());
        completeLocalCallback(accounts,http);assertFalse(accounts.takeWelcome());
    }
}
