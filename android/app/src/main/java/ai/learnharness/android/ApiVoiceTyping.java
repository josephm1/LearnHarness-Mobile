package ai.learnharness.android;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.*;
import java.io.*;
import okhttp3.*;

/** Explicit recording then transcription, adapted from Agora's ApiVoiceRecorder. */
final class ApiVoiceTyping {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AndroidVoiceTyping.Listener listener;
    private MediaRecorder recorder;
    private File audio;
    private Call call;
    private int generation;
    private String base,model,language,key;
    private boolean deepgram,optOut;
    ApiVoiceTyping(AndroidVoiceTyping.Listener listener){this.listener=listener;}
    void start(Context context,String base,String model,String language,String key,boolean optionalKey,boolean deepgram,boolean optOut){
        cancel();
        try{
            VoiceApi.url(base,deepgram);if(model.trim().isEmpty() || (!optionalKey && key.trim().isEmpty()))throw new IllegalArgumentException();
            this.optOut=optOut;this.deepgram=deepgram;this.base=base;this.model=model;this.language=language;this.key=key;
            int token=generation;
            audio=File.createTempFile("dictation-",".m4a",context.getCacheDir());
            recorder=Build.VERSION.SDK_INT>=31?new MediaRecorder(context):new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(1);recorder.setAudioSamplingRate(16000);recorder.setAudioEncodingBitRate(64000);recorder.setMaxDuration(120000);recorder.setMaxFileSize(VoiceApi.MAX_AUDIO_BYTES);recorder.setOutputFile(audio.getAbsolutePath());
            recorder.setOnInfoListener((r,what,extra)->{if(token==generation && (what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED))stop();});
            recorder.setOnErrorListener((r,what,extra)->{if(token==generation){cancel();listener.error("Recording failed. Your draft is unchanged.");}});
            recorder.prepare();recorder.start();
        }catch(Exception error){cancel();listener.error("Could not start recording. Check microphone access and Voice typing API settings.");}
    }
    void stop(){
        if(recorder==null)return;
        MediaRecorder recording=recorder;recorder=null;
        try{recording.stop();}catch(Exception error){cancel();listener.error("No usable recording. Speak for longer and try again.");return;}finally{try{recording.release();}catch(Exception ignored){}}
        listener.finishing();int token=generation;File clip=audio;
        try{
            Request request=VoiceApi.request(clip,base,model,language,key,deepgram,optOut);key="";
            call=VoiceApi.client().newCall(request);call.enqueue(new Callback(){
                public void onFailure(Call call,IOException error){clip.delete();main.post(()->{if(token==generation){cancel();listener.error("Transcription could not connect. Check the voice endpoint and network connection.");}});}
                public void onResponse(Call call,Response response){
                    String text=null,message=null;
                    try(Response closed=response){text=VoiceApi.response(closed,deepgram);}catch(Exception error){message=error instanceof IOException?error.getMessage():"Invalid transcription response.";}finally{clip.delete();}
                    String result=text,failure=message;
                    main.post(()->{if(token!=generation)return;cancel();if(failure!=null)listener.error(failure);else listener.result(result);});
                }
            });
        }catch(Exception error){cancel();listener.error("Check your transcription endpoint, model, language and API key.");}
    }
    void cancel(){
        generation++;if(call!=null){call.cancel();call=null;}
        if(recorder!=null){try{recorder.reset();recorder.release();}catch(Exception ignored){}recorder=null;}
        if(audio!=null){audio.delete();audio=null;}key="";
    }
}
