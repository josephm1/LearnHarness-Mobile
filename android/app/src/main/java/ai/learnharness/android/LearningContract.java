package ai.learnharness.android;

import org.json.*;

/** Validate complete model output before changing any local learning record. */
public final class LearningContract {
    private LearningContract() {}
    public static JSONObject object(String output) throws JSONException {
        String clean = output.trim();
        if (clean.startsWith("```")) {
            int start = clean.indexOf('\n');
            int end = clean.lastIndexOf("```");
            if (start < 0 || end <= start) throw new JSONException("Incomplete JSON response");
            clean = clean.substring(start + 1, end).trim();
        }
        return new JSONObject(clean);
    }
    public static JSONArray questions(JSONArray questions) throws JSONException {
        if (questions.length() == 0 || questions.length() > 30) throw new JSONException("Expected 1–30 questions.");
        for (int i = 0; i < questions.length(); i++) {
            JSONObject q = questions.getJSONObject(i);
            required(q, "question"); required(q, "answer"); required(q, "hint");
        }
        return questions;
    }
    public static JSONArray claims(JSONObject output) throws JSONException {
        JSONArray claims = output.getJSONArray("claims");
        if (claims.length() == 0 || claims.length() > 40) throw new JSONException("Expected 1–40 knowledge claims.");
        for (int i = 0; i < claims.length(); i++) {
            JSONObject c = claims.getJSONObject(i);
            required(c, "title"); required(c, "statement");
            JSONArray premises = c.getJSONArray("premises");
            for (int j = 0; j < premises.length(); j++) if (!(premises.get(j) instanceof String)) throw new JSONException("Premises must be text.");
        }
        return claims;
    }
    private static void required(JSONObject value, String key) throws JSONException {
        if (value.getString(key).trim().isEmpty()) throw new JSONException("Missing " + key);
    }
}
