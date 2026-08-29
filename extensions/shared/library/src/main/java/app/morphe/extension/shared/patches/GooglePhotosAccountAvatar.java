/**
 * Copyright 2026 De-Vanced
 * https://github.com/RookieEnough/De-Vanced/pull/114
 */

package app.morphe.extension.shared.patches;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Supplies the active Google account avatar to Google Photos' OneGoogle UI.
 *
 * <p>GmsCore stores the account avatar, but the newer OneGoogle APIs used by Photos do not
 * currently expose it. This bridge obtains the authenticated user-info picture, keeps a separate
 * cache for each account and updates the toolbar/account-sheet views without changing account
 * authentication itself.</p>
 */
final class GooglePhotosAccountAvatar {
    private static final String ACCOUNT_TYPE = "app.revanced";
    private static final String RESOURCE_PACKAGE_NAME = "com.google.android.apps.photos";
    private static final String PROFILE_TOKEN_TYPE =
            "oauth2:openid";
    private static final String USER_INFO_URL =
            "https://www.googleapis.com/oauth2/v3/userinfo";

    private static final String PREFS_NAME = "morphe_google_photos_avatar";
    private static final String PREF_SELECTED_ACCOUNT = "selected_account";
    private static final String CACHE_FILE_PREFIX = "google_account_profile_avatar_";
    private static final long CACHE_MAX_AGE_MILLIS = 6L * 60L * 60L * 1000L;
    private static final long WINDOW_SCAN_THROTTLE_MILLIS = 250L;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
            Pattern.CASE_INSENSITIVE
    );
    private static final String ACCOUNT_AVATAR_OVERLAY_TAG =
            "morphe_google_photos_account_avatar";

    private static final AtomicReference<String> FETCHING_ACCOUNT = new AtomicReference<>();
    private static final AtomicBoolean WINDOW_SCAN_ERROR_LOGGED = new AtomicBoolean();
    private static final Map<ImageView, String> SCHEDULED_TOOLBAR_AVATARS = new WeakHashMap<>();
    private static final Map<View, String> SCHEDULED_ACCOUNT_SHEETS = new WeakHashMap<>();
    private static final Set<View> OBSERVED_WINDOW_ROOTS =
            Collections.newSetFromMap(new WeakHashMap<>());

    @Nullable
    private static volatile Bitmap avatar;
    @Nullable
    private static volatile String avatarAccountName;
    @Nullable
    private static volatile String selectedAccountName;
    private static volatile long lastWindowScanUptime;

    private GooglePhotosAccountAvatar() {
    }

    static void install(Activity activity) {
        Logger.printInfo(() -> "Installing the Google Photos account avatar bridge");
        View root = activity.getWindow().getDecorView();
        observeWindowRoot(activity, root);
        scanWindowRoots(activity, true);
        refresh(activity, root);

        // A few bounded follow-up scans catch windows created immediately after HomeActivity.
        // Later account-sheet windows are discovered through layout/focus callbacks instead of
        // polling WindowManagerGlobal for the entire lifetime of the app.
        Utils.runOnMainThreadDelayed(() -> scanWindowRoots(activity, true), 250);
        Utils.runOnMainThreadDelayed(() -> scanWindowRoots(activity, true), 1_000);
        Utils.runOnMainThreadDelayed(() -> scanWindowRoots(activity, true), 2_500);
    }

    private static void observeWindowRoot(Activity activity, View root) {
        if (!OBSERVED_WINDOW_ROOTS.add(root)) return;

        ViewTreeObserver observer = root.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(() -> {
            refresh(activity, root);
            scanWindowRoots(activity, false);
        });
        observer.addOnWindowFocusChangeListener(hasFocus -> {
            refresh(activity, root);
            scanWindowRoots(activity, true);
        });
        refresh(activity, root);
    }

    private static void scanWindowRoots(Activity activity, boolean force) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        long now = SystemClock.uptimeMillis();
        if (!force && now - lastWindowScanUptime < WINDOW_SCAN_THROTTLE_MILLIS) return;
        lastWindowScanUptime = now;

        try {
            Class<?> windowManagerGlobalClass = Class.forName("android.view.WindowManagerGlobal");
            Object windowManagerGlobal = windowManagerGlobalClass
                    .getMethod("getInstance")
                    .invoke(null);
            java.lang.reflect.Field viewsField =
                    windowManagerGlobalClass.getDeclaredField("mViews");
            viewsField.setAccessible(true);
            Object roots = viewsField.get(windowManagerGlobal);
            if (roots instanceof List<?>) {
                for (Object root : (List<?>) roots) {
                    if (root instanceof View) observeWindowRoot(activity, (View) root);
                }
            }
        } catch (Exception exception) {
            if (WINDOW_SCAN_ERROR_LOGGED.compareAndSet(false, true)) {
                Logger.printException(
                        () -> "Could not inspect Google Photos account-panel windows",
                        exception
                );
            }
        }
    }

    private static void refresh(Activity activity, View root) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        boolean signedOutInUi = isToolbarDiscShowingSignedOut(activity, root);
        if (signedOutInUi) {
            avatar = null;
            avatarAccountName = null;
            selectedAccountName = null;
            activity.getSharedPreferences(PREFS_NAME, 0)
                    .edit()
                    .remove(PREF_SELECTED_ACCOUNT)
                    .apply();
        }

        Account account = signedOutInUi ? null : resolveSelectedAccount(activity, root);
        if (account != null) {
            activateAccount(activity, account.name);
        } else if (!signedOutInUi) {
            Account[] registered = AccountManager.get(activity).getAccountsByType(ACCOUNT_TYPE);
            if (registered.length == 0) {
                avatar = null;
                avatarAccountName = null;
                selectedAccountName = null;
            }
        }

        applyAvatar(activity, root);
        applyAvailableAccountsAvatars(activity, root);

        if (account != null
                && (avatar == null || !sameAccount(account.name, avatarAccountName))) {
            requestProfileToken(activity, root, account);
        }
    }

    private static boolean isToolbarDiscShowingSignedOut(Activity activity, View root) {
        int selectedAccountId = getResourceId(activity, "selected_account_disc");
        if (selectedAccountId != 0) {
            View disc = root.findViewById(selectedAccountId);
            if (disc != null && disc.isShown()) {
                String email = findEmailInViewAndParents(disc);
                return email == null;
            }
        }
        int toolbarAvatarId = getResourceId(activity, "og_apd_internal_image_view");
        if (toolbarAvatarId != 0) {
            View avatarView = root.findViewById(toolbarAvatarId);
            if (avatarView != null && avatarView.isShown()) {
                String email = findEmailInViewAndParents(avatarView);
                return email == null;
            }
        }
        return false;
    }

    @Nullable
    private static Account resolveSelectedAccount(Activity activity, View root) {
        if (isToolbarDiscShowingSignedOut(activity, root)) {
            return null;
        }

        String accountFromUi = findSelectedAccountName(activity, root);
        AccountManager accountManager = AccountManager.get(activity);
        Account[] accounts = accountManager.getAccountsByType(ACCOUNT_TYPE);

        if (accountFromUi != null) {
            Account visibleAccount = findAccount(accounts, accountFromUi);
            return visibleAccount != null
                    ? visibleAccount
                    : new Account(accountFromUi, ACCOUNT_TYPE);
        }

        Account current = findAccount(accounts, selectedAccountName);
        if (current != null) return current;

        SharedPreferences preferences = activity.getSharedPreferences(PREFS_NAME, 0);
        String rememberedName = preferences.getString(PREF_SELECTED_ACCOUNT, null);
        Account remembered = findAccount(accounts, rememberedName);
        if (remembered != null) return remembered;

        return null;
    }

    @Nullable
    private static Account findAccount(Account[] accounts, @Nullable String accountName) {
        if (accountName == null) return null;
        for (Account account : accounts) {
            if (sameAccount(account.name, accountName)) return account;
        }
        return null;
    }

    @Nullable
    private static String findSelectedAccountName(Activity activity, View root) {
        int selectedAccountId = getResourceId(activity, "selected_account_disc");
        if (selectedAccountId != 0) {
            String email = findEmailInViewAndParents(root.findViewById(selectedAccountId));
            if (email != null) return email;
        }

        int toolbarAvatarId = getResourceId(activity, "og_apd_internal_image_view");
        if (toolbarAvatarId != 0) {
            String email = findEmailInViewAndParents(root.findViewById(toolbarAvatarId));
            if (email != null) return email;
        }

        int accountSheetAvatarId = getResourceId(activity, "og_bento_selected_account_avatar");
        if (accountSheetAvatarId != 0) {
            return findEmailInViewAndParents(root.findViewById(accountSheetAvatarId));
        }

        return null;
    }

    private static int getResourceId(Activity activity, String name) {
        int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
        if (id != 0) return id;
        return activity.getResources().getIdentifier(name, "id", RESOURCE_PACKAGE_NAME);
    }

    @Nullable
    private static String findEmailInViewAndParents(@Nullable View view) {
        View current = view;
        for (int depth = 0; current != null && depth < 5; depth++) {
            String email = extractEmail(current.getContentDescription());
            if (email != null) return email;

            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    @Nullable
    private static String extractEmail(@Nullable CharSequence text) {
        if (text == null) return null;
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }



    private static void requestProfileToken(Activity activity, View root, Account account) {
        final String accountName = account.name;
        if (avatar != null && sameAccount(accountName, avatarAccountName)) return;
        if (!FETCHING_ACCOUNT.compareAndSet(null, accountName)) return;

        try {
            Logger.printInfo(() -> "Requesting the Google Photos avatar token");

            Bundle options = new Bundle();
            options.putString("androidPackageName", activity.getPackageName());
            AccountManagerFuture<Bundle> future = AccountManager.get(activity).getAuthToken(
                    account,
                    PROFILE_TOKEN_TYPE,
                    options,
                    false,
                    null,
                    null
            );

            Utils.runOnBackgroundThread(() -> {
                boolean refreshDifferentAccount = false;
                try {
                    Bundle result = future.getResult();
                    String token = result.getString(AccountManager.KEY_AUTHTOKEN);
                    if (token == null || token.isEmpty()) {
                        throw new IllegalStateException("Google auth token was empty");
                    }

                    Bitmap downloadedAvatar = downloadAvatar(token);
                    if (downloadedAvatar == null) {
                        throw new IllegalStateException("Google user-info returned no avatar");
                    }

                    writeCachedAvatar(activity, accountName, downloadedAvatar);

                    avatar = downloadedAvatar;
                    avatarAccountName = accountName;
                    selectedAccountName = accountName;
                    Logger.printInfo(() -> "Google Photos account avatar loaded successfully!");
                    Utils.runOnMainThread(() -> applyAvatar(activity, root));
                } catch (Exception exception) {
                    Logger.printException(
                            () -> "Could not load the Google Photos account avatar",
                            exception
                    );
                    refreshDifferentAccount = selectedAccountName != null
                            && !sameAccount(accountName, selectedAccountName);
                } finally {
                    FETCHING_ACCOUNT.compareAndSet(accountName, null);
                    if (refreshDifferentAccount) {
                        Utils.runOnMainThread(() -> refresh(activity, root));
                    }
                }
            });
        } catch (Exception exception) {
            FETCHING_ACCOUNT.compareAndSet(accountName, null);
            Logger.printException(
                    () -> "Could not request the Google Photos profile token",
                    exception
            );
        }
    }

    @Nullable
    private static Bitmap downloadAvatar(String token) throws Exception {
        HttpURLConnection userInfoConnection = openConnection(USER_INFO_URL);
        userInfoConnection.setRequestProperty("Authorization", "Bearer " + token);

        try {
            int status = userInfoConnection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("Google user-info HTTP status " + status);
            }

            JSONObject response;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    userInfoConnection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                response = new JSONObject(sb.toString());
            }

            String pictureUrl = response.optString("picture", null);
            if (pictureUrl == null || pictureUrl.isEmpty()) return null;

            HttpURLConnection imageConnection = openConnection(pictureUrl);
            try {
                if (imageConnection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
                try (InputStream stream = new BufferedInputStream(imageConnection.getInputStream())) {
                    return getCircularBitmap(BitmapFactory.decodeStream(stream));
                }
            } finally {
                imageConnection.disconnect();
            }
        } finally {
            userInfoConnection.disconnect();
        }
    }

    private static HttpURLConnection openConnection(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/json,image/*");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)");
        return connection;
    }

    private static void activateAccount(Activity activity, String accountName) {
        selectedAccountName = accountName;
        activity.getSharedPreferences(PREFS_NAME, 0)
                .edit()
                .putString(PREF_SELECTED_ACCOUNT, accountName)
                .apply();

        if (avatar == null || !sameAccount(accountName, avatarAccountName)) {
            Bitmap cachedAvatar = readCachedAvatar(activity, accountName);
            if (cachedAvatar != null) {
                avatar = cachedAvatar;
                avatarAccountName = accountName;
                Logger.printInfo(() -> "Google Photos avatar loaded from disk cache");
            }
        }
    }

    private static void applyAvatar(Activity activity, View root) {
        Bitmap currentAvatar = avatar;
        String currentAccount = avatarAccountName;
        if (currentAvatar == null
                || currentAccount == null
                || activity.isFinishing()
                || activity.isDestroyed()) {
            return;
        }

        // Toolbar avatar (og_apd_internal_image_view is an ImageView).
        int toolbarAvatarId = getResourceId(activity, "og_apd_internal_image_view");
        if (toolbarAvatarId != 0) {
            View toolbarAvatar = root.findViewById(toolbarAvatarId);
            if (toolbarAvatar instanceof ImageView) {
                updateImageView((ImageView) toolbarAvatar, currentAvatar, currentAccount);
            }
        }

        // Account sheet avatar (og_bento_selected_account_avatar is a FrameLayout container;
        // the actual ImageView is a child of it).
        int accountSheetContainerId = getResourceId(activity, "og_bento_selected_account_avatar");
        if (accountSheetContainerId != 0) {
            View accountSheetContainer = root.findViewById(accountSheetContainerId);
            if (accountSheetContainer instanceof ImageView) {
                applyBentoAvatar((ImageView) accountSheetContainer, currentAvatar);
            } else if (accountSheetContainer instanceof ViewGroup) {
                ImageView inner = findFirstImageView((ViewGroup) accountSheetContainer);
                if (inner != null) applyBentoAvatar(inner, currentAvatar);
            }
        }
    }

    private static final String[] AVAILABLE_ACCOUNT_AVATAR_IDS = new String[] {
            "og_bento_available_account_avatar",
            "og_available_account_avatar",
            "og_bento_header_account_avatar",
            "og_compact_header_avatar",
            "og_bento_card_avatar_image"
    };

    private static void applyAvailableAccountsAvatars(Activity activity, View root) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        AccountManager accountManager = AccountManager.get(activity);
        Account[] accounts = accountManager.getAccountsByType(ACCOUNT_TYPE);
        if (accounts.length == 0) return;

        List<View> avatarViews = new ArrayList<>();
        for (String idName : AVAILABLE_ACCOUNT_AVATAR_IDS) {
            int id = getResourceId(activity, idName);
            if (id != 0) {
                findAllViewsWithId(root, id, avatarViews);
            }
        }

        for (View avatarView : avatarViews) {
            String email = findEmailInViewAndParents(avatarView);
            Account targetAccount = null;
            if (email != null) {
                targetAccount = findAccount(accounts, email);
            } else if (accounts.length == 1) {
                targetAccount = accounts[0];
            }

            if (targetAccount == null) continue;

            Bitmap cachedBmp = readCachedAvatar(activity, targetAccount.name);
            if (cachedBmp != null) {
                if (avatarView instanceof ImageView) {
                    applyBentoAvatar((ImageView) avatarView, cachedBmp);
                } else if (avatarView instanceof ViewGroup) {
                    ImageView inner = findFirstImageView((ViewGroup) avatarView);
                    if (inner != null) applyBentoAvatar(inner, cachedBmp);
                }
            } else {
                requestProfileToken(activity, root, targetAccount);
            }
        }
    }

    private static void findAllViewsWithId(View view, int targetId, List<View> outList) {
        if (view == null) return;
        if (view.getId() == targetId) {
            outList.add(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                findAllViewsWithId(vg.getChildAt(i), targetId, outList);
            }
        }
    }

    /**
     * Applies the avatar bitmap to the large account-sheet ImageView,
     * scaled to fit its measured pixel size.
     */
    private static void applyBentoAvatar(ImageView imageView, Bitmap bitmap) {
        int viewSize = Math.min(imageView.getWidth(), imageView.getHeight());
        Bitmap scaled = (viewSize > 0 && (bitmap.getWidth() != viewSize || bitmap.getHeight() != viewSize))
                ? Bitmap.createScaledBitmap(bitmap, viewSize, viewSize, true)
                : bitmap;
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setImageBitmap(scaled);
        // Retry after layout in case view wasn't measured yet.
        imageView.postDelayed(() -> {
            int sz = Math.min(imageView.getWidth(), imageView.getHeight());
            if (sz > 0 && sz != scaled.getWidth()) {
                imageView.setImageBitmap(Bitmap.createScaledBitmap(bitmap, sz, sz, true));
            } else {
                imageView.setImageBitmap(scaled);
            }
        }, 200);
    }

    /**
     * Recursively finds the first ImageView descendant of the given ViewGroup.
     */
    @Nullable
    private static ImageView findFirstImageView(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ImageView) return (ImageView) child;
            if (child instanceof ViewGroup) {
                ImageView found = findFirstImageView((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Nullable
    private static Bitmap getCircularBitmap(@Nullable Bitmap src) {
        if (src == null) return null;
        int width = src.getWidth();
        int height = src.getHeight();
        int size = Math.min(width, height);

        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);
        paint.setColor(0xFF000000);

        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        Rect srcRect = new Rect(
                (width - size) / 2,
                (height - size) / 2,
                (width + size) / 2,
                (height + size) / 2
        );
        Rect dstRect = new Rect(0, 0, size, size);
        canvas.drawBitmap(src, srcRect, dstRect, paint);
        paint.setXfermode(null);

        Paint ringPaint = new Paint();
        ringPaint.setAntiAlias(true);
        ringPaint.setStyle(Paint.Style.STROKE);
        float strokeWidth = Math.max(2f, size * 0.025f);
        ringPaint.setStrokeWidth(strokeWidth);
        ringPaint.setColor(0x25000000);
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - (strokeWidth / 2f), ringPaint);

        return output;
    }

    private static void updateImageView(ImageView imageView, Bitmap bitmap, String accountName) {
        // Scale the circular bitmap to exactly fit the view so it never overflows the ring boundary.
        int viewSize = Math.min(imageView.getWidth(), imageView.getHeight());
        Bitmap scaled = (viewSize > 0 && (bitmap.getWidth() != viewSize || bitmap.getHeight() != viewSize))
                ? Bitmap.createScaledBitmap(bitmap, viewSize, viewSize, true)
                : bitmap;

        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setImageBitmap(scaled);

        String previouslyScheduledAccount = SCHEDULED_TOOLBAR_AVATARS.put(imageView, accountName);
        if (!sameAccount(accountName, previouslyScheduledAccount)) {
            imageView.postDelayed(() -> imageView.setImageBitmap(scaled), 100);
            imageView.postDelayed(() -> imageView.setImageBitmap(scaled), 500);
            imageView.postDelayed(() -> imageView.setImageBitmap(scaled), 1_500);
        }
    }

    private static boolean isCurrentAvatar(Bitmap bitmap, String accountName) {
        return bitmap == avatar
                && sameAccount(accountName, avatarAccountName)
                && sameAccount(accountName, selectedAccountName);
    }

    private static boolean sameAccount(@Nullable String first, @Nullable String second) {
        return first != null && second != null && first.equalsIgnoreCase(second);
    }

    @Nullable
    private static Bitmap readCachedAvatar(Activity activity, String accountName) {
        File cacheFile = getCacheFile(activity, accountName);
        if (!cacheFile.isFile()) return null;
        if (System.currentTimeMillis() - cacheFile.lastModified() > CACHE_MAX_AGE_MILLIS) {
            //noinspection ResultOfMethodCallIgnored
            cacheFile.delete();
            return null;
        }

        try (InputStream stream = new FileInputStream(cacheFile)) {
            return getCircularBitmap(BitmapFactory.decodeStream(stream));
        } catch (Exception exception) {
            Logger.printException(() -> "Could not read the cached Google account avatar", exception);
            return null;
        }
    }

    private static void writeCachedAvatar(Activity activity, String accountName, Bitmap bitmap) {
        File cacheFile = getCacheFile(activity, accountName);
        try (FileOutputStream output = new FileOutputStream(cacheFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (Exception exception) {
            Logger.printException(() -> "Could not cache the Google account avatar", exception);
        }
    }

    private static File getCacheFile(Activity activity, String accountName) {
        return new File(
                activity.getCacheDir(),
                CACHE_FILE_PREFIX + hashAccountName(accountName) + ".png"
        );
    }

    private static String hashAccountName(String accountName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    accountName.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return hex.toString();
        } catch (Exception exception) {
            // SHA-256 is required by Android; keep a deterministic fallback for completeness.
            return Integer.toHexString(accountName.toLowerCase(Locale.ROOT).hashCode());
        }
    }
}
