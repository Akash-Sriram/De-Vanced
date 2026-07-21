package app.morphe.extension.shared.updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import app.morphe.extension.shared.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class GitHubReleaseChecker {

    private static final String REPO_RELEASES_URL = "https://api.github.com/repos/Akash-Sriram/GooglePhotos-Patched/releases/latest";
    private static boolean hasCheckedThisSession = false;

    public static void checkUpdateOnStartup(final Context context) {
        if (hasCheckedThisSession || context == null) {
            return;
        }
        hasCheckedThisSession = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(REPO_RELEASES_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "GooglePhotos-Patched-App");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    if (conn.getResponseCode() != 200) {
                        return;
                    }

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject releaseJson = new JSONObject(sb.toString());
                    String tagName = releaseJson.optString("tag_name", "");
                    final String latestVersion = tagName.replaceAll("[^0-9.]", "").replaceAll("^\\.|\\.$", "");

                    String downloadUrl = null;
                    JSONArray assets = releaseJson.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String name = asset.optString("name", "");
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", null);
                                break;
                            }
                        }
                    }

                    if (downloadUrl == null) {
                        return;
                    }

                    PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                    String currentVersion = pInfo.versionName.replaceAll("[^0-9.]", "").replaceAll("^\\.|\\.$", "");

                    if (isNewerVersion(latestVersion, currentVersion)) {
                        final String finalDownloadUrl = downloadUrl;
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                showUpdateDialog(context, latestVersion, finalDownloadUrl);
                            }
                        });
                    }
                } catch (Exception e) {
                    Logger.printException(() -> "Error checking for updates from GitHub", e);
                }
            }
        }).start();
    }

    private static boolean isNewerVersion(String latest, String current) {
        if (latest.isEmpty() || current.isEmpty()) return false;
        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");

        int minLength = Math.min(latestParts.length, currentParts.length);
        for (int i = 0; i < minLength; i++) {
            int l = parseSafeInt(latestParts[i]);
            int c = parseSafeInt(currentParts[i]);
            if (l > c) return true;
            if (l < c) return false;
        }

        // If they are equal up to the minimum length:
        // If latest is longer (e.g. 7.84.0.948508402 vs 7.84.0), ignore the build number suffix
        // if the core version (major.minor.patch) is already identical (minLength >= 3).
        if (latestParts.length > currentParts.length && minLength < 3) {
            for (int i = minLength; i < latestParts.length; i++) {
                if (parseSafeInt(latestParts[i]) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int parseSafeInt(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void showUpdateDialog(final Context context, final String newVersion, final String downloadUrl) {
        if (!(context instanceof Activity) || ((Activity) context).isFinishing()) {
            return;
        }

        new AlertDialog.Builder(context)
                .setTitle("Update Available")
                .setMessage("A new patched version of Google Photos (v" + newVersion + ") is available. Would you like to download and install it?")
                .setPositiveButton("Update", (dialog, which) -> downloadAndInstallApk(context, newVersion, downloadUrl))
                .setNegativeButton("Later", null)
                .setCancelable(true)
                .show();
    }

    private static void downloadAndInstallApk(final Context context, String version, String downloadUrl) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle("Google Photos Update (v" + version + ")");
            request.setDescription("Downloading updated patched Google Photos build...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "GooglePhotos-v" + version + "-patched.apk");

            final DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                final long downloadId = manager.enqueue(request);

                BroadcastReceiver onComplete = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context ctxt, Intent intent) {
                        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                        if (id == downloadId) {
                            try {
                                ctxt.unregisterReceiver(this);
                            } catch (Exception ignored) {}

                            Uri apkUri = manager.getUriForDownloadedFile(downloadId);
                            if (apkUri != null) {
                                installApk(ctxt, apkUri);
                            }
                        }
                    }
                };

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED);
                } else {
                    context.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
                }
            }
        } catch (Exception e) {
            Logger.printException(() -> "Error downloading update APK", e);
        }
    }

    private static void installApk(Context context, Uri apkUri) {
        try {
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(installIntent);
        } catch (Exception e) {
            Logger.printException(() -> "Error triggering package installer", e);
        }
    }
}
