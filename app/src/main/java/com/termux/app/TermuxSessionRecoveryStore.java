package com.termux.app;

import android.content.Context;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Persists terminal session metadata and explicitly pinned command recovery choices. */
final class TermuxSessionRecoveryStore {

    static final class SessionRecord {
        final String id;
        final String name;
        final String workingDirectory;
        final String executable;
        final boolean failsafe;
        @Nullable final String pinnedCommand;
        @Nullable final String pinnedWorkingDirectory;

        SessionRecord(JSONObject json) throws JSONException {
            id = json.getString("id");
            name = json.optString("name", "");
            workingDirectory = json.optString("cwd", "/");
            executable = json.optString("executable", "");
            failsafe = json.optBoolean("failsafe", false);
            pinnedCommand = json.has("pinnedCommand") ? json.optString("pinnedCommand", null) : null;
            pinnedWorkingDirectory = json.has("pinnedCwd") ? json.optString("pinnedCwd", null) : null;
        }

        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("name", name);
            json.put("cwd", workingDirectory);
            json.put("executable", executable);
            json.put("failsafe", failsafe);
            if (pinnedCommand != null) {
                json.put("pinnedCommand", pinnedCommand);
                json.put("pinnedCwd", pinnedWorkingDirectory);
            }
            return json;
        }
    }

    static final class CommandState {
        final String workingDirectory;
        @Nullable final String command;

        CommandState(String workingDirectory, @Nullable String command) {
            this.workingDirectory = workingDirectory;
            this.command = command;
        }
    }

    private final File mSessionsFile;
    private final File mSelectedSessionFile;
    private final File mCommandStateDirectory;

    TermuxSessionRecoveryStore(@NonNull Context context) {
        File filesDirectory = context.getFilesDir();
        mSessionsFile = new File(filesDirectory, "termux-session-recovery.json");
        mSelectedSessionFile = new File(filesDirectory, "termux-selected-session");
        mCommandStateDirectory = new File(filesDirectory, "termux-command-state");
        if (!mCommandStateDirectory.isDirectory()) mCommandStateDirectory.mkdirs();
    }

    synchronized List<SessionRecord> getSessions() {
        List<SessionRecord> records = new ArrayList<>();
        try {
            JSONArray sessions = readSessions();
            for (int i = 0; i < sessions.length(); i++)
                records.add(new SessionRecord(sessions.getJSONObject(i)));
        } catch (Exception ignored) {
            // A damaged recovery file must not prevent Termux from starting.
        }
        return records;
    }

    synchronized void saveSession(@NonNull String id, @Nullable String name, @Nullable String cwd,
                                  @Nullable String executable, boolean failsafe) {
        updateSession(id, json -> {
            json.put("name", name == null ? "" : name);
            if (cwd != null && !cwd.isEmpty()) json.put("cwd", cwd);
            json.put("executable", executable == null ? "" : executable);
            json.put("failsafe", failsafe);
        });
    }

    synchronized void setPinnedCommand(@NonNull String id, @NonNull String command, @NonNull String cwd) {
        updateSession(id, json -> {
            json.put("pinnedCommand", command);
            json.put("pinnedCwd", cwd);
        });
    }

    synchronized void clearPinnedCommand(@NonNull String id) {
        updateSession(id, json -> {
            json.remove("pinnedCommand");
            json.remove("pinnedCwd");
        });
    }

    synchronized void removeSession(@NonNull String id) {
        JSONArray existing = readSessions();
        JSONArray updated = new JSONArray();
        for (int i = 0; i < existing.length(); i++) {
            JSONObject item = existing.optJSONObject(i);
            if (item != null && !id.equals(item.optString("id"))) updated.put(item);
        }
        writeSessions(updated);
        File stateFile = getCommandStateFile(id);
        if (stateFile.exists()) stateFile.delete();
    }

    synchronized void clear() {
        writeSessions(new JSONArray());
        mSelectedSessionFile.delete();
        File[] stateFiles = mCommandStateDirectory.listFiles();
        if (stateFiles != null) {
            for (File file : stateFiles) file.delete();
        }
    }

    synchronized void setSelectedSessionId(@Nullable String id) {
        if (id == null) {
            mSelectedSessionFile.delete();
            return;
        }
        File temporary = new File(mSelectedSessionFile.getPath() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(id.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
            android.system.Os.rename(temporary.getAbsolutePath(), mSelectedSessionFile.getAbsolutePath());
        } catch (Exception ignored) {
            temporary.delete();
        }
    }

    @Nullable
    synchronized String getSelectedSessionId() {
        if (!mSelectedSessionFile.isFile()) return null;
        try {
            return new String(readFile(mSelectedSessionFile), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    File getCommandStateFile(@NonNull String id) {
        return new File(mCommandStateDirectory, id + ".state");
    }

    @Nullable
    CommandState readCommandState(@NonNull String id) {
        File stateFile = getCommandStateFile(id);
        if (!stateFile.isFile()) return null;
        try {
            byte[] contents = readFile(stateFile);
            String[] lines = new String(contents, StandardCharsets.UTF_8).split("\\n", -1);
            if (lines.length < 2) return null;
            String cwd = decodeBase64(lines[0]);
            String command = decodeBase64(lines[1]);
            return new CommandState(cwd, command.isEmpty() ? null : command);
        } catch (Exception ignored) {
            return null;
        }
    }

    private interface SessionUpdate {
        void apply(JSONObject json) throws JSONException;
    }

    private void updateSession(String id, SessionUpdate update) {
        JSONArray existing = readSessions();
        JSONArray updated = new JSONArray();
        JSONObject target = null;
        for (int i = 0; i < existing.length(); i++) {
            JSONObject item = existing.optJSONObject(i);
            if (item == null) continue;
            if (id.equals(item.optString("id"))) target = item;
            updated.put(item);
        }
        try {
            if (target == null) {
                target = new JSONObject();
                target.put("id", id);
                target.put("name", "");
                target.put("cwd", "/");
                target.put("executable", "");
                updated.put(target);
            }
            update.apply(target);
            writeSessions(updated);
        } catch (JSONException ignored) {
            // Keep the last valid snapshot if serialization fails.
        }
    }

    private JSONArray readSessions() {
        if (!mSessionsFile.isFile()) return new JSONArray();
        try {
            String json = new String(readFile(mSessionsFile), StandardCharsets.UTF_8);
            return new JSONArray(json);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private void writeSessions(JSONArray sessions) {
        File temporary = new File(mSessionsFile.getPath() + ".tmp");
        try {
            byte[] bytes = sessions.toString().getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes);
                output.getFD().sync();
            }
            android.system.Os.rename(temporary.getAbsolutePath(), mSessionsFile.getAbsolutePath());
        } catch (Exception ignored) {
            temporary.delete();
        }
    }

    private static String decodeBase64(String value) {
        return new String(Base64.decode(value, Base64.DEFAULT), StandardCharsets.UTF_8);
    }

    private static byte[] readFile(File file) throws java.io.IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytes;
            while ((bytes = input.read(buffer)) != -1) output.write(buffer, 0, bytes);
            return output.toByteArray();
        }
    }
}
