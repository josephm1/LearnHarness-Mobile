package ai.learnharness.android;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.JSONObject;

/** Agora's speech-only multipart protocol. Never uploads conversation history. */
final class VoiceApi {
    static final long MAX_AUDIO_BYTES=20L*1024*1024;
    static HttpUrl url(String base){
        return url(base,false);
    }
    static HttpUrl url(String base,boolean deepgram){
        HttpUrl url=HttpUrl.get(base.trim());
        if(!url.isHttps() || !url.username().isEmpty() || !url.password().isEmpty() || url.query()!=null || url.fragment()!=null)throw new IllegalArgumentException("Use a clean HTTPS transcription URL.");
        String path=url.encodedPath().replaceAll("/+$","");
        String suffix=deepgram?"/listen":"/audio/transcriptions";
        return path.endsWith(suffix)?url:url.newBuilder().encodedPath(path+suffix).build();
    }
    static Request request(File audio,String base,String model,String language,String key){
        return request(audio,base,model,language,key,false);
    }
    static Request request(File audio,String base,String model,String language,String key,boolean deepgram){
        return request(audio,base,model,language,key,deepgram,true);
    }
    static Request request(File audio,String base,String model,String language,String key,boolean deepgram,boolean optOut){
        if(audio.length()<1 || audio.length()>MAX_AUDIO_BYTES)throw new IllegalArgumentException("Recording is empty or too large.");
        if(model.trim().isEmpty() || model.length()>200 || language.length()>35)throw new IllegalArgumentException("Check your transcription model and language.");
        if(deepgram){
            if(key.trim().isEmpty())throw new IllegalArgumentException("Save a Deepgram API key first.");
            HttpUrl.Builder endpoint=url(base,true).newBuilder().addQueryParameter("model",model.trim()).addQueryParameter("smart_format","true");
            if(optOut)endpoint.addQueryParameter("mip_opt_out","true");
            if(language.trim().isEmpty())endpoint.addQueryParameter("detect_language","true");else endpoint.addQueryParameter("language",language.trim());
            return new Request.Builder().url(endpoint.build()).header("Authorization","Token "+key.trim()).post(RequestBody.create(MediaType.get("audio/mp4"),audio)).build();
        }
        MultipartBody.Builder body=new MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model",model.trim()).addFormDataPart("response_format","json")
            .addFormDataPart("file","dictation.m4a",RequestBody.create(MediaType.get("audio/mp4"),audio));
        if(!language.trim().isEmpty())body.addFormDataPart("language",language.trim());
        Request.Builder request=new Request.Builder().url(url(base)).post(body.build());
        if(!key.trim().isEmpty())request.header("Authorization","Bearer "+key.trim());
        return request.build();
    }
    static String response(Response response)throws Exception{
        return response(response,false);
    }
    static String response(Response response,boolean deepgram)throws Exception{
        if(!response.isSuccessful())throw new IOException("Transcription failed (HTTP "+response.code()+"). Check the voice API settings.");
        if(response.body()==null)throw new IOException("Empty transcription response.");
        JSONObject json=new JSONObject(new String(ProviderClient.readBounded(response.body().byteStream(),1024*1024),java.nio.charset.StandardCharsets.UTF_8));
        Object value=deepgram?json.getJSONObject("results").getJSONArray("channels").getJSONObject(0).getJSONArray("alternatives").getJSONObject(0).opt("transcript"):json.opt("text");
        if(!(value instanceof String))throw new IOException("Invalid transcription response.");String text=((String)value).trim();
        if(text.isEmpty())throw new IOException("No speech was recognised.");return text;
    }
    static OkHttpClient client(){return new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(2,TimeUnit.MINUTES).build();}
}
