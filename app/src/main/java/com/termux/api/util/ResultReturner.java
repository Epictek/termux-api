package com.termux.api.util;

import android.app.Activity;
import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.BroadcastReceiver.PendingResult;
import android.content.Context;
import android.content.Intent;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.JsonWriter;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public abstract class ResultReturner {

    /**
     * String which specifies a linux abstract namespace socket address where output from the API
     * call should be written.
     */
    private static final String SOCKET_OUTPUT_ADDRESS = "termux-output";
    /**
     * String which specifies a linux abstract namespace socket address where input to the API call
     * can be read from.
     */
    private static final String SOCKET_INPUT_ADDRESS = "termux-input";

    public interface ResultWriter {
        void writeResult(PrintWriter out) throws Exception;
    }

    /**
     * Possible subclass of {@link ResultWriter} when input is to be read from stdin.
     */
    public static abstract class WithInput implements ResultWriter {
        protected InputStream in;

        public void setInput(InputStream inputStream) throws Exception {
            this.in = inputStream;
        }
    }

    /**
     * Possible marker interface for a {@link ResultWriter} when input is to be read from stdin.
     */
    public static abstract class WithStringInput extends WithInput {
        protected String inputString;

        protected boolean trimInput() {
            return true;
        }

        @Override
        public final void setInput(InputStream inputStream) throws Exception {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int l;
            while ((l = inputStream.read(buffer)) > 0) {
                baos.write(buffer, 0, l);
            }
            inputString = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            if (trimInput()) inputString = inputString.trim();
        }
    }

    public static abstract class ResultJsonWriter implements ResultWriter {
        @Override
        public final void writeResult(PrintWriter out) throws Exception {
            JsonWriter writer = new JsonWriter(out);
            writer.setIndent("  ");
            writeJson(writer);
            out.println(); // To add trailing newline.
        }

        public abstract void writeJson(JsonWriter out) throws Exception;
    }

    /**
     * Just tell termux-api.c that we are done.
     */
    public static void noteDone(Context context) {
        returnData(context,null);
    }

    /**
     * Run in a separate thread, unless the context is an IntentService.
     */
    public static void returnData(Object context, final ResultWriter resultWriter) {
        final PendingResult asyncResult = (context instanceof BroadcastReceiver) ? ((BroadcastReceiver) context)
                .goAsync() : null;
        final Activity activity = (Activity) ((context instanceof Activity) ? context : null);

        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    try (LocalSocket outputSocket = new LocalSocket()) {
                        String outputSocketAdress = SOCKET_OUTPUT_ADDRESS;
                        outputSocket.connect(new LocalSocketAddress(outputSocketAdress));
                        try (PrintWriter writer = new PrintWriter(outputSocket.getOutputStream())) {
                            if (resultWriter != null) {
                                if (resultWriter instanceof WithInput) {
                                    try (LocalSocket inputSocket = new LocalSocket()) {
                                        String inputSocketAdress = SOCKET_INPUT_ADDRESS;
                                        inputSocket.connect(new LocalSocketAddress(inputSocketAdress));
                                        ((WithInput) resultWriter).setInput(inputSocket.getInputStream());
                                        resultWriter.writeResult(writer);
                                    }
                                } else {
                                    resultWriter.writeResult(writer);
                                }
                            }
                        }
                    }

                    if (asyncResult != null) {
                        asyncResult.setResultCode(0);
                    } else if (activity != null) {
                        activity.setResult(0);
                    }
                } catch (Exception e) {
                    TermuxApiLogger.error("Error in ResultReturner", e);
                    if (asyncResult != null) {
                        asyncResult.setResultCode(1);
                    } else if (activity != null) {
                        activity.setResult(1);
                    }
                } finally {
                    if (asyncResult != null) {
                        asyncResult.finish();
                    } else if (activity != null) {
                        activity.finish();
                    }
                }
            }
        };

        if (context instanceof IntentService) {
            runnable.run();
        } else {
            new Thread(runnable).start();
        }
    }

}
