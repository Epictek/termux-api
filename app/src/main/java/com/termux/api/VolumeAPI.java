package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.JsonWriter;
import android.util.SparseArray;

import com.termux.api.util.ResultReturner;

import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;

public class VolumeAPI {
    private static final int STREAM_UNKNOWN = -1;

    // string representations for each of the available audio streams
    private static SparseArray<String> streamMap = new SparseArray<>();
    static {
        streamMap.append(AudioManager.STREAM_ALARM,         "alarm");
        streamMap.append(AudioManager.STREAM_MUSIC,         "music");
        streamMap.append(AudioManager.STREAM_NOTIFICATION,  "notification");
        streamMap.append(AudioManager.STREAM_RING,          "ring");
        streamMap.append(AudioManager.STREAM_SYSTEM,        "system");
        streamMap.append(AudioManager.STREAM_VOICE_CALL,    "call");
    }


    static void onReceive(final Context context, final JSONObject opts) {
        final AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        String action = opts.optString("action");

        if ("set-volume".equals(action)) {
            final String streamName = opts.optString("stream");
            final int stream = getAudioStream(streamName);

            if (stream == STREAM_UNKNOWN) {
                String error = "ERROR: Unknown stream: " + streamName;
                printError(context, error);
            } else {
                int volume = opts.optInt("volume", -1);
                setStreamVolume(audioManager, stream, volume);
                ResultReturner.noteDone(context);
            }
        } else {
            printAllStreamInfo(context, audioManager);
        }
    }

    /**
     * Prints error to console
     */
    private static void printError(Context context, final String error) {
        ResultReturner.returnData(context, new ResultReturner.ResultWriter() {
            @Override
            public void writeResult(PrintWriter out) {
                out.append(error + "\n");
                out.flush();
                out.close();
            }
        });
    }

    /**
     * Set volume for the specified audio stream
     */
    private static void setStreamVolume(AudioManager audioManager, int stream, int volume ) {
        int maxVolume = audioManager.getStreamMaxVolume(stream);
        if (volume == -1 ){
            volume = audioManager.getStreamVolume(stream);
        }
        if (volume <= 0) {
            volume = 0;
        } else if (volume >= maxVolume) {
            volume = maxVolume;
        }
        audioManager.setStreamVolume(stream, volume, 0);
    }

    /**
     * Print information about all available audio streams
     */
    private static void printAllStreamInfo(Context context, final AudioManager audioManager) {
        ResultReturner.returnData(context, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                getStreamsInfo(audioManager, out);
                out.close();
            }
        });
    }

    /**
     * Get info for all streams
     */
    private static void getStreamsInfo(AudioManager audioManager, JsonWriter out) throws IOException {
        out.beginArray();

        for (int j = 0; j < streamMap.size(); ++j) {
            int stream = streamMap.keyAt(j);
            getStreamInfo(audioManager, out, stream);
        }
        out.endArray();
    }

    /**
     * Get info for specific stream
     */
    protected static void getStreamInfo(AudioManager audioManager, JsonWriter out, int stream) throws IOException {
        out.beginObject();

        out.name("stream").value(streamMap.get(stream));
        out.name("volume").value(audioManager.getStreamVolume(stream));
        out.name("max_volume").value(audioManager.getStreamMaxVolume(stream));

        out.endObject();
    }

    /**
     * Get proper audio stream based on String type
     */
    protected static int getAudioStream(String type) {
        switch (type == null ? "" : type) {
            case "alarm":           return AudioManager.STREAM_ALARM;
            case "call":            return AudioManager.STREAM_VOICE_CALL;
            case "notification":    return AudioManager.STREAM_NOTIFICATION;
            case "ring":            return AudioManager.STREAM_RING;
            case "system":          return AudioManager.STREAM_SYSTEM;
            case "music":           return AudioManager.STREAM_MUSIC;
            default:                return STREAM_UNKNOWN;
        }
    }
}
