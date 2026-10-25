package ai.learnharness.android;

/** Cursor-aware draft insertion, following Agora's VoiceDraftTarget spacing rules. */
final class VoiceInsertion {
    static String text(String draft,int start,int end,String transcript){
        String spoken=transcript.trim();if(spoken.isEmpty())return "";
        String before=start>0 && !Character.isWhitespace(draft.charAt(start-1)) && Character.isLetterOrDigit(spoken.charAt(0))?" ":"";
        String after=end<draft.length() && Character.isLetterOrDigit(draft.charAt(end))?" ":"";
        return before+spoken+after;
    }
}
