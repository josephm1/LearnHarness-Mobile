package ai.learnharness.android;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Single writer on the UI thread. A failed write leaves the last committed file intact. */
public final class Records {
    private final AtomicFile file;
    public JSONObject root;
    public Records(Context context) throws Exception {
        file = new AtomicFile(new File(context.getFilesDir(), "learning.json"));
        if (file.getBaseFile().exists()) {
            try (InputStream in = file.openRead()) { root = new JSONObject(new String(ProviderClient.readBounded(in, 16_000_000), StandardCharsets.UTF_8)); }
            validate(root);
            if(!root.has("documents"))root.put("documents",new JSONArray());
        } else root = empty();
    }
    public static JSONObject empty() throws JSONException {
        return new JSONObject().put("format", "learnharness-android").put("version", 1)
            .put("sessions", new JSONArray()).put("knowledge", new JSONArray()).put("practice", new JSONArray()).put("documents",new JSONArray());
    }
    public static void validate(JSONObject value) throws JSONException {
        if (!"learnharness-android".equals(value.optString("format")) || value.optInt("version") != 1)
            throw new JSONException("This is not a supported LearnHarness Android backup.");
        for (String key : new String[]{"sessions", "knowledge", "practice"}) {
            JSONArray rows = value.getJSONArray(key);
            if (rows.length() > 20000) throw new JSONException("Backup contains too many records.");
            java.util.HashSet<String> ids = new java.util.HashSet<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (row.getString("id").trim().isEmpty() || !ids.add(row.getString("id"))) throw new JSONException("Missing or duplicate record ID.");
                if (key.equals("sessions")) {
                    row.getString("title");
                    JSONArray turns = row.getJSONArray("messages");
                    for (int j = 0; j < turns.length(); j++) {
                        JSONObject turn = turns.getJSONObject(j);
                        if (!java.util.Arrays.asList("user", "assistant").contains(turn.getString("role"))) throw new JSONException("Invalid message role.");
                        turn.getString("content");
                    }
                } else if (key.equals("knowledge")) {
                    row.getString("title"); row.getString("statement"); row.getJSONArray("premises");
                } else {
                    row.getString("title");
                    LearningContract.questions(row.getJSONArray("questions"));
                }
            }
        }
        if(value.has("sourceTrees")){JSONArray trees=value.getJSONArray("sourceTrees");if(trees.length()>1000)throw new JSONException("Too many source folders.");java.util.HashSet<String> treeIds=new java.util.HashSet<>();for(int i=0;i<trees.length();i++){JSONObject tree=trees.getJSONObject(i);if(tree.getString("id").trim().isEmpty() || !treeIds.add(tree.getString("id")))throw new JSONException("Invalid source folder ID.");tree.getString("title");if(!tree.getString("uri").startsWith("content://") || !tree.getString("uri").contains("/tree/"))throw new JSONException("Source folders must reference Android Files trees.");}}
        if(value.has("graph"))GraphContract.validate(value.getJSONObject("graph"));
        if(value.has("documents") && !(value.get("documents") instanceof JSONArray))throw new JSONException("Invalid document references.");
        JSONArray documents=value.optJSONArray("documents");java.util.HashSet<String> documentIds=new java.util.HashSet<>();
        for(int i=0;documents!=null && i<documents.length();i++){JSONObject doc=documents.getJSONObject(i);String id=doc.getString("id");if(id.trim().isEmpty() || !documentIds.add(id))throw new JSONException("Invalid document ID.");doc.getString("title");if(!doc.getString("uri").startsWith("content://"))throw new JSONException("Documents must reference Android document providers.");if(!java.util.Arrays.asList("PDF","EPUB","Text").contains(doc.getString("kind")))throw new JSONException("Unsupported document type.");}
    }
    public void save() throws Exception {
        byte[] bytes = root.toString(2).getBytes(StandardCharsets.UTF_8);
        if(bytes.length>16_000_000)throw new IOException("Learning records exceed the 16 MB backup limit. Export and archive older sessions before continuing.");
        FileOutputStream out = null;
        try { out = file.startWrite(); out.write(bytes); file.finishWrite(out); }
        catch (Exception e) { if (out != null) file.failWrite(out); throw e; }
    }
    public JSONArray rows(String name) { if(name.equals("documents") && !root.has(name))try{root.put(name,new JSONArray());}catch(JSONException impossible){throw new IllegalStateException(impossible);}return root.optJSONArray(name); }
    public JSONObject find(String name, String id) {
        JSONArray rows = rows(name);
        for (int i = 0; i < rows.length(); i++) if (rows.optJSONObject(i).optString("id").equals(id)) return rows.optJSONObject(i);
        return null;
    }
    public void remove(String name, String id) {
        JSONArray rows = rows(name);
        for (int i = rows.length() - 1; i >= 0; i--) if (rows.optJSONObject(i).optString("id").equals(id)) rows.remove(i);
    }
    public static String id() { return UUID.randomUUID().toString(); }
    public static String text(JSONObject row, String key) { return row == null ? "" : row.optString(key, ""); }
}
