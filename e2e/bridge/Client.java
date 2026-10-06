package bridge.client;

import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

/** External caller. The two APKs have different package names and signing keys. */
public final class Client extends Activity {
    private static final Uri URI = Uri.parse("content://dev.agneswd.stillpoint.migrate/backup");
    private final StringBuilder report = new StringBuilder();
    private interface Attempt { void run() throws Exception; }

    private void rejected(String name, Class<? extends Exception> expected, Attempt attempt) throws Exception {
        try { attempt.run(); }
        catch (Exception error) {
            if (!expected.isInstance(error)) throw new AssertionError(name + ": " + error, error);
            report.append("PASS ").append(name).append(": ").append(error.getClass().getSimpleName()).append('\n');
            return;
        }
        throw new AssertionError(name + " was accepted");
    }

    private byte[] read(InputStream input) throws Exception {
        try (InputStream in = input; java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            if (in == null) throw new AssertionError("No stream");
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            try {
                ContentResolver resolver = getContentResolver();
                if (getIntent().getBooleanExtra("denied", false)) {
                    rejected("wrong signing key read", SecurityException.class, () -> read(resolver.openInputStream(URI)));
                    rejected("wrong signing key asset", SecurityException.class, () -> resolver.openAssetFileDescriptor(URI, "r"));
                    rejected("wrong signing key typed asset", SecurityException.class, () -> resolver.openTypedAssetFileDescriptor(URI, "application/json", null));
                } else {
                    byte[] data = read(resolver.openInputStream(URI));
                    try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "bridge.json"))) { out.write(data); }
                    if (!"application/json".equals(resolver.getType(URI))) throw new AssertionError("Wrong MIME type");
                    report.append("PASS same signing key read and MIME type\n");
                    byte[] typed = read(resolver.openTypedAssetFileDescriptor(URI, "application/json", null).createInputStream());
                    if (!java.util.Arrays.equals(data, typed)) throw new AssertionError("Typed stream differs");
                    report.append("PASS typed stream\n");
                    for (String mode : new String[]{"w", "wt", "wa", "rw", "rwt"}) {
                        rejected("write mode " + mode, SecurityException.class, () -> resolver.openFileDescriptor(URI, mode));
                    }
                    for (String suffix : new String[]{"/other", "?x=1", "#fragment"}) {
                        rejected("unknown URI " + suffix, java.io.FileNotFoundException.class,
                            () -> resolver.openFileDescriptor(Uri.parse(URI.toString() + suffix), "r"));
                    }
                    rejected("query", UnsupportedOperationException.class, () -> resolver.query(URI, null, null, null, null));
                    rejected("insert", UnsupportedOperationException.class, () -> resolver.insert(URI, new ContentValues()));
                    rejected("update", UnsupportedOperationException.class, () -> resolver.update(URI, new ContentValues(), null, null));
                    rejected("delete", UnsupportedOperationException.class, () -> resolver.delete(URI, null, null));
                    rejected("bulk insert", UnsupportedOperationException.class, () -> resolver.bulkInsert(URI, new ContentValues[0]));
                    rejected("call", UnsupportedOperationException.class, () -> resolver.call(URI, "export", null, null));
                    rejected("canonicalize", UnsupportedOperationException.class, () -> resolver.canonicalize(URI));
                    rejected("uncanonicalize", UnsupportedOperationException.class, () -> resolver.uncanonicalize(URI));
                    rejected("refresh", UnsupportedOperationException.class, () -> resolver.refresh(URI, null, null));
                    ArrayList<ContentProviderOperation> batch = new ArrayList<>();
                    batch.add(ContentProviderOperation.newDelete(URI).build());
                    rejected("batch", UnsupportedOperationException.class, () -> resolver.applyBatch(URI.getAuthority(), batch));
                    // Android can ignore a grant request. The runner checks the recipient again afterwards.
                    try { grantUriPermission("dev.agneswd.bridge.denied", URI, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                    catch (SecurityException expected) { report.append("PASS URI grant rejected\n"); }
                    resolver.openFileDescriptor(URI, "r").close();
                    read(resolver.openInputStream(URI));
                    report.append("PASS early reader close and next read\n");
                }
            } catch (Throwable error) {
                report.append("FAIL ").append(android.util.Log.getStackTraceString(error));
            }
            try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "result.txt"))) {
                out.write(report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception error) { throw new RuntimeException(error); }
            runOnUiThread(this::finish);
        }).start();
    }
}
