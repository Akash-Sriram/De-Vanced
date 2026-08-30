package app.morphe.extension.shared.patches;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;

public class PhenotypeFlagManager {

    private static final String PREF_NAME = "com.google.android.apps.photos.phenotype";
    private static final String MORPHE_SETTINGS_PILL_TAG = "morphe_phenotype_settings_pill";
    private static final String SEEDED_MARKER = "_morphe_flags_seeded";

    private static final int COLOR_PRIMARY = 0xFF1A73E8;
    private static final int COLOR_ACCENT = 0xFF0B8043;
    private static final int COLOR_DANGER = 0xFFD93025;
    private static final int COLOR_TEXT_PRIMARY = 0xFF202124;
    private static final int COLOR_TEXT_SECONDARY = 0xFF5F6368;
    private static final int COLOR_BG_LIGHT = 0xFFF1F3F4;
    private static final int COLOR_BG_BLUE_TINT = 0xFFE8F0FE;
    private static final int COLOR_BG_GREEN_TINT = 0xFFE6F4EA;
    private static final int COLOR_BG_RED_TINT = 0xFFFCE8E6;

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void injectSettingsCard(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        activity.runOnUiThread(() -> {
            try {
                View decor = activity.getWindow().getDecorView();
                decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        try {
                            FrameLayout content = activity.findViewById(android.R.id.content);
                            if (content != null && content.findViewWithTag(MORPHE_SETTINGS_PILL_TAG) == null) {
                                LinearLayout pill = createFloatingPill(activity);
                                content.addView(pill);
                                Logger.printInfo(() -> "Morphe Phenotype Flags floating pill injected into SettingsActivity");
                            }
                        } catch (Exception e) {
                            Logger.printException(() -> "Could not inject Morphe pill into SettingsActivity", e);
                        }
                    }
                });
            } catch (Exception e) {
                Logger.printException(() -> "Error registering layout listener for SettingsActivity", e);
            }
        });
    }

    private static LinearLayout createFloatingPill(Activity activity) {
        LinearLayout pill = new LinearLayout(activity);
        pill.setTag(MORPHE_SETTINGS_PILL_TAG);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER);
        pill.setClickable(true);
        pill.setFocusable(true);
        pill.setElevation(16f);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.END;
        float density = activity.getResources().getDisplayMetrics().density;
        int marginEnd = (int) (20 * density);
        int marginBottom = (int) (32 * density);
        params.setMargins(0, 0, marginEnd, marginBottom);
        pill.setLayoutParams(params);

        int padH = (int) (18 * density);
        int padV = (int) (12 * density);
        pill.setPadding(padH, padV, padH, padV);

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(32 * density);
        background.setColor(COLOR_PRIMARY);
        pill.setBackground(background);

        TextView icon = new TextView(activity);
        icon.setText("🛠️");
        icon.setTextSize(16);
        icon.setPadding(0, 0, (int) (8 * density), 0);
        pill.addView(icon);

        TextView label = new TextView(activity);
        label.setText("Morphe Flags");
        label.setTextSize(14);
        label.setTextColor(0xFFFFFFFF);
        label.setTypeface(null, Typeface.BOLD);
        pill.addView(label);

        pill.setOnClickListener(v -> show(activity));

        return pill;
    }

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        activity.runOnUiThread(() -> {
            try {
                showDialog(activity);
            } catch (Throwable t) {
                Logger.printException(() -> "Could not open PhenotypeFlagManager", t);
                Toast.makeText(activity, "Error: " + t.getClass().getSimpleName() + " - " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private static Button createStyledButton(Activity activity, String text, int textColor, int bgColor) {
        float density = activity.getResources().getDisplayMetrics().density;
        Button btn = new Button(activity);
        btn.setText(text);
        btn.setTextSize(11);
        btn.setTextColor(textColor);
        btn.setTypeface(null, Typeface.BOLD);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(16 * density);
        bg.setColor(bgColor);
        btn.setBackground(bg);

        int padH = (int) (8 * density);
        int padV = (int) (6 * density);
        btn.setPadding(padH, padV, padH, padV);
        return btn;
    }

    private static void showDialog(Activity activity) {
        SharedPreferences prefs = getPrefs(activity);
        float density = activity.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int padH = (int) (20 * density);
        int padV = (int) (14 * density);
        root.setPadding(padH, padV, padH, padV);

        // Header Title + Reset to Defaults Button
        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 0, 0, (int) (10 * density));

        TextView title = new TextView(activity);
        title.setText("🛠️ Phenotype Flags");
        title.setTextSize(17);
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleLp);
        headerRow.addView(title);

        Button btnRestoreDefaults = new Button(activity);
        btnRestoreDefaults.setText("⚡ Reset Defaults");
        btnRestoreDefaults.setTextSize(12);
        btnRestoreDefaults.setTextColor(COLOR_PRIMARY);
        btnRestoreDefaults.setTypeface(null, Typeface.BOLD);
        btnRestoreDefaults.setBackgroundColor(0x00000000);
        headerRow.addView(btnRestoreDefaults);
        root.addView(headerRow);

        // Pinned Action Toolbar (4 Distinct Colored Pills)
        LinearLayout toolBar = new LinearLayout(activity);
        toolBar.setOrientation(LinearLayout.HORIZONTAL);
        toolBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tbLp.setMargins(0, 0, 0, (int) (8 * density));
        toolBar.setLayoutParams(tbLp);

        Button btnAdd = createStyledButton(activity, "➕ Add", COLOR_PRIMARY, COLOR_BG_BLUE_TINT);
        LinearLayout.LayoutParams bLp1 = new LinearLayout.LayoutParams(0, (int) (38 * density), 1f);
        bLp1.setMargins(0, 0, (int) (3 * density), 0);
        btnAdd.setLayoutParams(bLp1);
        toolBar.addView(btnAdd);

        Button btnImport = createStyledButton(activity, "📥 Import", COLOR_PRIMARY, COLOR_BG_BLUE_TINT);
        LinearLayout.LayoutParams bLp2 = new LinearLayout.LayoutParams(0, (int) (38 * density), 1f);
        bLp2.setMargins((int) (3 * density), 0, (int) (3 * density), 0);
        btnImport.setLayoutParams(bLp2);
        toolBar.addView(btnImport);

        Button btnExport = createStyledButton(activity, "📤 Export", COLOR_ACCENT, COLOR_BG_GREEN_TINT);
        LinearLayout.LayoutParams bLp3 = new LinearLayout.LayoutParams(0, (int) (38 * density), 1f);
        bLp3.setMargins((int) (3 * density), 0, (int) (3 * density), 0);
        btnExport.setLayoutParams(bLp3);
        toolBar.addView(btnExport);

        Button btnClear = createStyledButton(activity, "🗑️ Clear", COLOR_DANGER, COLOR_BG_RED_TINT);
        LinearLayout.LayoutParams bLp4 = new LinearLayout.LayoutParams(0, (int) (38 * density), 1f);
        bLp4.setMargins((int) (3 * density), 0, 0, 0);
        btnClear.setLayoutParams(bLp4);
        toolBar.addView(btnClear);

        root.addView(toolBar);

        // Search Bar (Always visible at top)
        EditText searchBar = new EditText(activity);
        searchBar.setHint("Search flags by ID or key...");
        searchBar.setTextSize(13);
        searchBar.setTextColor(COLOR_TEXT_PRIMARY);
        searchBar.setHintTextColor(COLOR_TEXT_SECONDARY);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.setMargins(0, (int) (4 * density), 0, (int) (6 * density));
        searchBar.setLayoutParams(searchLp);
        root.addView(searchBar);

        // Scrollable Flags Container
        ScrollView scroll = new ScrollView(activity);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (320 * density));
        scroll.setLayoutParams(scrollLp);

        LinearLayout flagsListContainer = new LinearLayout(activity);
        flagsListContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(flagsListContainer);
        root.addView(scroll);

        Runnable[] refreshFlagsListHolder = new Runnable[1];
        Runnable refreshFlagsList = () -> {
            flagsListContainer.removeAllViews();
            Map<String, ?> all = prefs.getAll();
            List<String> keys = new ArrayList<>();
            for (String k : all.keySet()) {
                if (!k.startsWith("_")) {
                    keys.add(k);
                }
            }
            Collections.sort(keys);
            String query = searchBar.getText().toString().toLowerCase().trim();

            if (keys.isEmpty()) {
                TextView tvEmpty = new TextView(activity);
                tvEmpty.setText("No flags configured.\nTap '➕ Add' or '⚡ Reset Defaults'.");
                tvEmpty.setTextSize(14);
                tvEmpty.setTextColor(COLOR_TEXT_SECONDARY);
                tvEmpty.setPadding((int) (8 * density), (int) (36 * density), (int) (8 * density), (int) (36 * density));
                tvEmpty.setGravity(Gravity.CENTER);
                flagsListContainer.addView(tvEmpty);
                return;
            }

            for (String key : keys) {
                if (!query.isEmpty() && !key.toLowerCase().contains(query)) continue;

                Object val = all.get(key);
                String valStr = val == null ? "null" : val.toString();
                String typeStr = val == null ? "String" : val.getClass().getSimpleName();

                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.VERTICAL);
                int rPadV = (int) (9 * density);
                row.setPadding((int) (6 * density), rPadV, (int) (6 * density), rPadV);
                row.setClickable(true);
                row.setFocusable(true);

                TextView tvKey = new TextView(activity);
                tvKey.setText(key);
                tvKey.setTextSize(14);
                tvKey.setTextColor(COLOR_PRIMARY);
                tvKey.setTypeface(null, Typeface.BOLD);
                row.addView(tvKey);

                TextView tvVal = new TextView(activity);
                tvVal.setText(typeStr + ": " + valStr);
                tvVal.setTextSize(12);
                tvVal.setTextColor(COLOR_TEXT_SECONDARY);
                row.addView(tvVal);

                View divider = new View(activity);
                LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
                divLp.setMargins(0, (int) (8 * density), 0, 0);
                divider.setLayoutParams(divLp);
                divider.setBackgroundColor(0xFFE0E0E0);
                row.addView(divider);

                row.setOnClickListener(v -> showEditFlagDialog(activity, prefs, key, refreshFlagsListHolder[0]));
                flagsListContainer.addView(row);
            }
        };
        refreshFlagsListHolder[0] = refreshFlagsList;

        refreshFlagsList.run();

        btnAdd.setOnClickListener(v -> showAddFlagDialog(activity, prefs, refreshFlagsList));
        btnImport.setOnClickListener(v -> showImportDialog(activity, prefs, refreshFlagsList));
        btnExport.setOnClickListener(v -> showExportDialog(activity, prefs));

        btnClear.setOnClickListener(v -> {
            AlertDialog clearDialog = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                    .setTitle("🗑️ Clear All Flags")
                    .setMessage("Are you sure you want to remove all configured phenotype flags?")
                    .setPositiveButton("Clear All", (d, w) -> {
                        prefs.edit().clear().putBoolean(SEEDED_MARKER, true).apply();
                        Toast.makeText(activity, "All flags cleared", Toast.LENGTH_SHORT).show();
                        refreshFlagsList.run();
                    })
                    .setNegativeButton("Cancel", null)
                    .create();

            clearDialog.setOnShowListener(d -> {
                Button pos = clearDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (pos != null) {
                    pos.setTextColor(COLOR_DANGER);
                    pos.setTypeface(null, Typeface.BOLD);
                }
                Button neg = clearDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                if (neg != null) {
                    neg.setTextColor(COLOR_TEXT_SECONDARY);
                    neg.setTypeface(null, Typeface.BOLD);
                }
            });

            clearDialog.show();
        });

        btnRestoreDefaults.setOnClickListener(v -> {
            prefs.edit().clear()
                    .putBoolean(SEEDED_MARKER, true)
                    .putBoolean("45743215", true)
                    .putLong("45802110", 2L)
                    .putLong("45762698", 2L)
                    .putBoolean("45732792", true)
                    .putBoolean("3024", true)
                    .putBoolean("4311", true)
                    .putLong("3013", 1L)
                    .putBoolean("3026", true)
                    .putBoolean("3023", true)
                    .putBoolean("2892", true)
                    .putBoolean("4306", true)
                    .putBoolean("3611", true)
                    .putBoolean("2675", true)
                    .putBoolean("3606", true)
                    .apply();
            Toast.makeText(activity, "Default preset restored!", Toast.LENGTH_SHORT).show();
            refreshFlagsList.run();
        });

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshFlagsList.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        AlertDialog dialog = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                .setView(root)
                .setPositiveButton("Apply & Restart", (d, w) -> restartApp(activity))
                .setNegativeButton("Close", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (pos != null) {
                pos.setTextColor(COLOR_ACCENT);
                pos.setTypeface(null, Typeface.BOLD);
            }
            Button neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (neg != null) {
                neg.setTextColor(COLOR_TEXT_SECONDARY);
                neg.setTypeface(null, Typeface.BOLD);
            }
        });

        dialog.show();
    }

    private static void showImportDialog(Activity activity, SharedPreferences prefs, Runnable onImported) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (20 * density);
        layout.setPadding(p, (int) (12 * density), p, (int) (12 * density));

        TextView tvHelp = new TextView(activity);
        tvHelp.setText("Paste JSON, Key=Value lines, or XML tags:");
        tvHelp.setTextSize(13);
        tvHelp.setTextColor(COLOR_TEXT_SECONDARY);
        tvHelp.setPadding(0, 0, 0, (int) (8 * density));
        layout.addView(tvHelp);

        EditText etInput = new EditText(activity);
        etInput.setHint("e.g.\n45743215=true\n45802110=2\n45732792=true");
        etInput.setTextSize(13);
        etInput.setTextColor(COLOR_TEXT_PRIMARY);
        etInput.setHintTextColor(COLOR_TEXT_SECONDARY);
        etInput.setMinLines(5);
        etInput.setMaxLines(12);
        etInput.setGravity(Gravity.TOP);
        layout.addView(etInput);

        Button btnPaste = new Button(activity);
        btnPaste.setText("📋 Paste from Clipboard");
        btnPaste.setTextColor(COLOR_PRIMARY);
        btnPaste.setBackgroundColor(COLOR_BG_LIGHT);
        btnPaste.setOnClickListener(v -> {
            try {
                ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence clipText = cm.getPrimaryClip().getItemAt(0).getText();
                    if (clipText != null) {
                        etInput.setText(clipText);
                        Toast.makeText(activity, "Pasted from clipboard", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (Exception e) {
                Toast.makeText(activity, "Error pasting: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(btnPaste);

        AlertDialog dialog = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                .setTitle("📥 Batch Import Flags")
                .setView(layout)
                .setPositiveButton("Import All", (d, w) -> {
                    String input = etInput.getText().toString().trim();
                    if (input.isEmpty()) return;

                    int count = parseAndApplyFlags(prefs, input);
                    Toast.makeText(activity, "Successfully imported " + count + " flags!", Toast.LENGTH_LONG).show();
                    if (onImported != null) onImported.run();
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (pos != null) {
                pos.setTextColor(COLOR_PRIMARY);
                pos.setTypeface(null, Typeface.BOLD);
            }
            Button neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (neg != null) {
                neg.setTextColor(COLOR_TEXT_SECONDARY);
                neg.setTypeface(null, Typeface.BOLD);
            }
        });

        dialog.show();
    }

    private static int parseAndApplyFlags(SharedPreferences prefs, String input) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(SEEDED_MARKER, true);
        int count = 0;

        if (input.startsWith("{") && input.endsWith("}")) {
            try {
                JSONObject json = new JSONObject(input);
                Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String k = keys.next().trim();
                    if (k.startsWith("_")) continue;
                    Object v = json.get(k);
                    if (v instanceof Boolean) {
                        editor.putBoolean(k, (Boolean) v);
                    } else if (v instanceof Integer || v instanceof Long) {
                        editor.putLong(k, ((Number) v).longValue());
                    } else if (v instanceof Double || v instanceof Float) {
                        editor.putFloat(k, ((Number) v).floatValue());
                    } else {
                        editor.putString(k, String.valueOf(v));
                    }
                    count++;
                }
                editor.apply();
                return count;
            } catch (Exception ignored) {}
        }

        Pattern xmlPattern = Pattern.compile("<(boolean|long|int|float|string)\\s+name=\"([^\"]+)\"\\s+value=\"([^\"]+)\"");
        Matcher xmlMatcher = xmlPattern.matcher(input);
        boolean foundXml = false;
        while (xmlMatcher.find()) {
            foundXml = true;
            String tag = xmlMatcher.group(1);
            String k = xmlMatcher.group(2);
            String v = xmlMatcher.group(3);
            if (k.startsWith("_")) continue;
            if ("boolean".equals(tag)) {
                editor.putBoolean(k, Boolean.parseBoolean(v));
            } else if ("long".equals(tag) || "int".equals(tag)) {
                try { editor.putLong(k, Long.parseLong(v)); } catch (Exception ignored) {}
            } else if ("float".equals(tag)) {
                try { editor.putFloat(k, Float.parseFloat(v)); } catch (Exception ignored) {}
            } else {
                editor.putString(k, v);
            }
            count++;
        }
        if (foundXml) {
            editor.apply();
            return count;
        }

        String[] lines = input.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;

            int eq = line.indexOf('=');
            if (eq == -1) eq = line.indexOf(':');
            if (eq == -1) continue;

            String k = line.substring(0, eq).trim().replace("\"", "").replace("'", "");
            String v = line.substring(eq + 1).trim().replace("\"", "").replace("'", "").replace(",", "");

            if (k.isEmpty() || v.isEmpty() || k.startsWith("_")) continue;

            if ("true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v)) {
                editor.putBoolean(k, Boolean.parseBoolean(v));
            } else {
                try {
                    long longVal = Long.parseLong(v);
                    editor.putLong(k, longVal);
                } catch (NumberFormatException nfe) {
                    try {
                        float floatVal = Float.parseFloat(v);
                        editor.putFloat(k, floatVal);
                    } catch (NumberFormatException nfe2) {
                        editor.putString(k, v);
                    }
                }
            }
            count++;
        }

        editor.apply();
        return count;
    }

    private static void showExportDialog(Activity activity, SharedPreferences prefs) {
        float density = activity.getResources().getDisplayMetrics().density;
        Map<String, ?> all = prefs.getAll();
        List<String> keys = new ArrayList<>();
        for (String k : all.keySet()) {
            if (!k.startsWith("_")) {
                keys.add(k);
            }
        }
        Collections.sort(keys);

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            Object v = all.get(k);
            sb.append("  \"").append(k).append("\": ");
            if (v instanceof String) {
                sb.append("\"").append(v).append("\"");
            } else {
                sb.append(v);
            }
            if (i < keys.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("}");

        final String exportedJson = sb.toString();

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (20 * density);
        layout.setPadding(p, (int) (12 * density), p, (int) (12 * density));

        TextView tvHelp = new TextView(activity);
        tvHelp.setText("Exported " + keys.size() + " active phenotype flags:");
        tvHelp.setTextSize(13);
        tvHelp.setTextColor(COLOR_TEXT_SECONDARY);
        tvHelp.setPadding(0, 0, 0, (int) (8 * density));
        layout.addView(tvHelp);

        EditText etPreview = new EditText(activity);
        etPreview.setText(exportedJson);
        etPreview.setTextSize(12);
        etPreview.setTextColor(COLOR_TEXT_PRIMARY);
        etPreview.setMinLines(6);
        etPreview.setMaxLines(14);
        etPreview.setGravity(Gravity.TOP);
        layout.addView(etPreview);

        AlertDialog dialog = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                .setTitle("📤 Export Flags")
                .setView(layout)
                .setPositiveButton("📋 Copy to Clipboard", (d, w) -> {
                    try {
                        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            ClipData clip = ClipData.newPlainText("Morphe Phenotype Flags", exportedJson);
                            cm.setPrimaryClip(clip);
                            Toast.makeText(activity, "Copied " + keys.size() + " flags to clipboard!", Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(activity, "Error copying: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Close", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (pos != null) {
                pos.setTextColor(COLOR_PRIMARY);
                pos.setTypeface(null, Typeface.BOLD);
            }
            Button neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (neg != null) {
                neg.setTextColor(COLOR_TEXT_SECONDARY);
                neg.setTypeface(null, Typeface.BOLD);
            }
        });

        dialog.show();
    }

    private static void showAddFlagDialog(Activity activity, SharedPreferences prefs, Runnable onSaved) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (20 * density);
        layout.setPadding(p, (int) (12 * density), p, (int) (12 * density));

        EditText etKey = new EditText(activity);
        etKey.setHint("Flag ID / Key (e.g. 45743215)");
        etKey.setTextColor(COLOR_TEXT_PRIMARY);
        etKey.setHintTextColor(COLOR_TEXT_SECONDARY);
        layout.addView(etKey);

        Spinner spinner = new Spinner(activity);
        String[] types = new String[] { "Boolean", "Long / Integer", "String", "Float" };
        spinner.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, types));
        layout.addView(spinner);

        EditText etValue = new EditText(activity);
        etValue.setHint("Value (e.g. true or 2)");
        etValue.setTextColor(COLOR_TEXT_PRIMARY);
        etValue.setHintTextColor(COLOR_TEXT_SECONDARY);
        layout.addView(etValue);

        AlertDialog dialog = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                .setTitle("Add Phenotype Flag")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String key = etKey.getText().toString().trim();
                    String valStr = etValue.getText().toString().trim();
                    String type = (String) spinner.getSelectedItem();
                    if (key.isEmpty() || valStr.isEmpty() || key.startsWith("_")) return;

                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(SEEDED_MARKER, true);
                    try {
                        if ("Boolean".equals(type)) {
                            editor.putBoolean(key, Boolean.parseBoolean(valStr));
                        } else if ("Long / Integer".equals(type)) {
                            long val = Long.parseLong(valStr);
                            editor.putLong(key, val);
                        } else if ("Float".equals(type)) {
                            editor.putFloat(key, Float.parseFloat(valStr));
                        } else {
                            editor.putString(key, valStr);
                        }
                        editor.apply();
                        Toast.makeText(activity, "Flag saved: " + key + " = " + valStr, Toast.LENGTH_SHORT).show();
                        if (onSaved != null) onSaved.run();
                    } catch (Exception e) {
                        Toast.makeText(activity, "Error parsing value: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (pos != null) {
                pos.setTextColor(COLOR_PRIMARY);
                pos.setTypeface(null, Typeface.BOLD);
            }
            Button neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (neg != null) {
                neg.setTextColor(COLOR_TEXT_SECONDARY);
                neg.setTypeface(null, Typeface.BOLD);
            }
        });

        dialog.show();
    }

    private static void showEditFlagDialog(Activity activity, SharedPreferences prefs, String key, Runnable onUpdated) {
        float density = activity.getResources().getDisplayMetrics().density;
        Object currentVal = prefs.getAll().get(key);
        String valStr = currentVal == null ? "" : currentVal.toString();
        String typeName = currentVal == null ? "String" : currentVal.getClass().getSimpleName();

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (20 * density);
        layout.setPadding(p, (int) (12 * density), p, (int) (12 * density));

        TextView tvType = new TextView(activity);
        tvType.setText("Type: " + typeName);
        tvType.setTextColor(COLOR_TEXT_SECONDARY);
        tvType.setTextSize(13);
        tvType.setPadding(0, (int) (4 * density), 0, (int) (8 * density));
        layout.addView(tvType);

        EditText etValue = new EditText(activity);
        etValue.setText(valStr);
        etValue.setTextColor(COLOR_TEXT_PRIMARY);
        etValue.setTextSize(15);
        layout.addView(etValue);

        AlertDialog dialog = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                .setTitle("Edit Flag: " + key)
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String newVal = etValue.getText().toString().trim();
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(SEEDED_MARKER, true);
                    try {
                        if (currentVal instanceof Number) {
                            long v = Long.parseLong(newVal);
                            editor.putLong(key, v);
                        } else if (currentVal instanceof Boolean || "true".equalsIgnoreCase(newVal) || "false".equalsIgnoreCase(newVal)) {
                            editor.putBoolean(key, Boolean.parseBoolean(newVal));
                        } else if (currentVal instanceof Float) {
                            editor.putFloat(key, Float.parseFloat(newVal));
                        } else {
                            editor.putString(key, newVal);
                        }
                        editor.apply();
                        Toast.makeText(activity, "Updated: " + key, Toast.LENGTH_SHORT).show();
                        if (onUpdated != null) onUpdated.run();
                    } catch (Exception e) {
                        Toast.makeText(activity, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Delete", (d, w) -> {
                    prefs.edit().remove(key).apply();
                    Toast.makeText(activity, "Deleted: " + key, Toast.LENGTH_SHORT).show();
                    if (onUpdated != null) onUpdated.run();
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (pos != null) {
                pos.setTextColor(COLOR_PRIMARY);
                pos.setTypeface(null, Typeface.BOLD);
            }
            Button neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (neg != null) {
                neg.setTextColor(COLOR_TEXT_SECONDARY);
                neg.setTypeface(null, Typeface.BOLD);
            }
            Button neu = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (neu != null) {
                neu.setTextColor(COLOR_DANGER);
                neu.setTypeface(null, Typeface.BOLD);
            }
        });

        dialog.show();
    }

    public static void restartApp(Context context) {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(intent);
                Runtime.getRuntime().exit(0);
            }
        } catch (Exception e) {
            Logger.printException(() -> "Could not restart app", e);
        }
    }
}
