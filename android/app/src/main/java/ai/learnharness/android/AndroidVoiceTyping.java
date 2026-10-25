package ai.learnharness.android;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.Locale;

/** One-utterance Android recognition, adapted from Agora's AndroidVoiceRecognizer. */
final class AndroidVoiceTyping implements RecognitionListener {
    interface Listener {
        void partial(String text);
        void finishing();
        void result(String text);
        void error(String message);
    }
    private SpeechRecognizer recognizer;
    private final Listener listener;
    private boolean active;
    AndroidVoiceTyping(Listener listener){this.listener=listener;}
    void start(Context context,boolean offline,String language){
        cancel();
        if(offline && (Build.VERSION.SDK_INT<31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context))){listener.error("On-device speech recognition is unavailable. Install an offline speech service or choose Android speech in Voice typing settings.");return;}
        if(!offline && !SpeechRecognizer.isRecognitionAvailable(context)){listener.error("No Android speech service is available. Install or enable a speech recognition service in Android settings.");return;}
        try{
            recognizer=offline && Build.VERSION.SDK_INT>=31?SpeechRecognizer.createOnDeviceSpeechRecognizer(context):SpeechRecognizer.createSpeechRecognizer(context);
            active=true;recognizer.setRecognitionListener(this);
            recognizer.startListening(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE,language.trim().isEmpty()?Locale.getDefault().toLanguageTag():language.trim())
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true));
        }catch(Exception error){cancel();listener.error("Speech recognition could not start. Check your Android speech service and microphone permission.");}
    }
    void stop(){if(active)try{listener.finishing();recognizer.stopListening();}catch(Exception error){cancel();listener.error("Speech recognition could not finish. Your typed draft has been kept.");}}
    void cancel(){active=false;if(recognizer!=null){try{recognizer.cancel();recognizer.destroy();}catch(Exception ignored){}recognizer=null;}}
    private String text(Bundle bundle){ArrayList<String> words=bundle==null?null:bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);return words==null || words.isEmpty()?"":words.get(0).trim();}
    @Override public void onResults(Bundle results){if(!active)return;String value=text(results);cancel();if(value.isEmpty())listener.error("No speech was recognised. Try again.");else listener.result(value);}
    @Override public void onPartialResults(Bundle results){if(active)listener.partial(text(results));}
    @Override public void onEndOfSpeech(){if(active)listener.finishing();}
    @Override public void onError(int code){if(!active)return;cancel();listener.error(code==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS?"Allow microphone access to use voice typing.":code==SpeechRecognizer.ERROR_NO_MATCH || code==SpeechRecognizer.ERROR_SPEECH_TIMEOUT?"No speech was recognised. Try again.":"Speech recognition failed. Check your speech service, language and network connection.");}
    @Override public void onReadyForSpeech(Bundle params){}
    @Override public void onBeginningOfSpeech(){}
    @Override public void onRmsChanged(float rms){}
    @Override public void onBufferReceived(byte[] buffer){}
    @Override public void onEvent(int type,Bundle params){}
}
