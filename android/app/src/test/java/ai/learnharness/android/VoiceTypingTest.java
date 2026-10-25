package ai.learnharness.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import okhttp3.*;
import okio.Buffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceTypingTest {
    @Test public void insertsAtCursorAndReplacesSelectionWithoutJoiningWords(){
        assertEquals(" spoken ",VoiceInsertion.text("helloworld",5,5,"spoken"));
        assertEquals("new ",VoiceInsertion.text("old text",0,4,"new"));
        assertEquals("hello",VoiceInsertion.text("",0,0," hello "));
        assertEquals(".",VoiceInsertion.text("hello",5,5,"."));
        assertEquals("",VoiceInsertion.text("draft",5,5,"  "));
    }
    @Test public void constructsSpeechEndpointWithoutDuplicatingSuffix(){
        assertEquals("https://example.com/v1/audio/transcriptions",VoiceApi.url("https://example.com/v1/").toString());
        assertEquals("https://example.com/v1/audio/transcriptions",VoiceApi.url("https://example.com/v1/audio/transcriptions").toString());
    }
    @Test public void rejectsUnsafeOrAmbiguousEndpoints(){
        for(String value:new String[]{"http://example.com/v1","https://user:secret@example.com/v1","https://example.com/v1?key=x","https://example.com/v1#anchor"}){
            try{VoiceApi.url(value);fail(value);}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void requestContainsAudioAndSpeechSettingsOnly()throws Exception{
        File audio=File.createTempFile("voice-test-",".m4a");
        try{
            try(FileOutputStream out=new FileOutputStream(audio)){out.write("audio-fixture".getBytes(StandardCharsets.UTF_8));}
            Request request=VoiceApi.request(audio,"https://example.com/v1","speech-model","en","test-key");
            assertEquals("Bearer test-key",request.header("Authorization"));
            Buffer buffer=new Buffer();request.body().writeTo(buffer);String body=buffer.readUtf8();
            assertTrue(body.contains("audio-fixture"));assertTrue(body.contains("speech-model"));assertTrue(body.contains("name=\"language\""));
            assertFalse(body.contains("messages"));assertFalse(body.contains("test-key"));
            assertNull(VoiceApi.request(audio,"https://example.com/v1","speech-model","","").header("Authorization"));
        }finally{audio.delete();}
    }
    private Response response(int code,String body){return new Response.Builder().request(new Request.Builder().url("https://example.com/").build()).protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(ResponseBody.create(MediaType.get("application/json"),body)).build();}
    @Test public void deepgramUsesRawAudioTokenAuthAndListenParameters()throws Exception{
        File audio=File.createTempFile("voice-deepgram-",".m4a");
        try{
            try(FileOutputStream out=new FileOutputStream(audio)){out.write("audio-fixture".getBytes(StandardCharsets.UTF_8));}
            Request request=VoiceApi.request(audio,"https://api.deepgram.com/v1","nova-3","en","deepgram-fixture",true);
            assertEquals("/v1/listen",request.url().encodedPath());assertEquals("Token deepgram-fixture",request.header("Authorization"));
            assertEquals("nova-3",request.url().queryParameter("model"));assertEquals("en",request.url().queryParameter("language"));assertNull(request.url().queryParameter("detect_language"));assertEquals("true",request.url().queryParameter("mip_opt_out"));
            assertEquals("audio/mp4",request.body().contentType().toString());Buffer body=new Buffer();request.body().writeTo(body);assertEquals("audio-fixture",body.readUtf8());
            assertEquals("true",VoiceApi.request(audio,"https://api.deepgram.com/v1/listen","nova-3","","key",true).url().queryParameter("detect_language"));
            try{VoiceApi.request(audio,"https://api.deepgram.com/v1","nova-3","","",true);fail();}catch(IllegalArgumentException expected){}
        }finally{audio.delete();}
    }
    @Test public void deepgramParsesItsNestedTranscriptAndRejectsEmptySpeech()throws Exception{
        try(Response response=response(200,"{results:{channels:[{alternatives:[{transcript:'hello from Deepgram'}]}]}}")){assertEquals("hello from Deepgram",VoiceApi.response(response,true));}
        try(Response response=response(200,"{results:{channels:[{alternatives:[{transcript:''}]}]}}")){try{VoiceApi.response(response,true);fail();}catch(IOException expected){}}
    }
    @Test public void parsesSpeechAndRedactsProviderErrorBodies()throws Exception{
        try(Response response=response(200,"{\"text\":\" hello world \"}")){assertEquals("hello world",VoiceApi.response(response));}
        try(Response response=response(401,"sensitive-provider-response")){try{VoiceApi.response(response);fail();}catch(IOException error){assertTrue(error.getMessage().contains("401"));assertFalse(error.getMessage().contains("sensitive"));}}
        try(Response response=response(200,"{\"text\":\" \"}")){try{VoiceApi.response(response);fail();}catch(IOException expected){}}
        assertFalse(VoiceApi.client().followRedirects());assertFalse(VoiceApi.client().followSslRedirects());
    }
}
