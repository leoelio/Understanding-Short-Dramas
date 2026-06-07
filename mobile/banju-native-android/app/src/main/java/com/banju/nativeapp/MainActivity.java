package com.banju.nativeapp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String PREFS = "banju_native_prefs";
    private static final String KEY_BASE_URL = "base_url";
    private static final String DEFAULT_BASE_URL = "https://hopes-founded-economies-respondent.trycloudflare.com";

    private EditText baseUrlInput;
    private TextView statusTitle;
    private TextView statusDetail;
    private Button checkButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        setContentView(buildContent());
        runHealthCheck();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.rgb(246, 249, 254));
    }

    private View buildContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(34), dp(22), dp(28));
        root.setBackground(pageBackground());
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.MATCH_PARENT
        ));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(24), dp(26), dp(24), dp(24));
        card.setBackground(cardBackground());
        root.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.banju_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(88), dp(88));
        logoParams.bottomMargin = dp(18);
        card.addView(logo, logoParams);

        TextView title = new TextView(this);
        title.setText("半句");
        title.setTextColor(Color.rgb(18, 20, 26));
        title.setTextSize(38);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Android 原生迁移版");
        subtitle.setTextColor(Color.rgb(78, 86, 105));
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = dp(6);
        card.addView(subtitle, subtitleParams);

        TextView explanation = new TextView(this);
        explanation.setText("第一步只验证手机能连接电脑上的 FastAPI 服务端。播放器、登录和高光互动在后续阶段接入。");
        explanation.setTextColor(Color.rgb(104, 112, 130));
        explanation.setTextSize(14);
        explanation.setGravity(Gravity.CENTER);
        explanation.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams explanationParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        explanationParams.topMargin = dp(16);
        card.addView(explanation, explanationParams);

        baseUrlInput = new EditText(this);
        baseUrlInput.setSingleLine(true);
        baseUrlInput.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        baseUrlInput.setText(loadBaseUrl());
        baseUrlInput.setTextColor(Color.rgb(18, 20, 26));
        baseUrlInput.setTextSize(14);
        baseUrlInput.setHint("服务端地址");
        baseUrlInput.setPadding(dp(16), 0, dp(16), 0);
        baseUrlInput.setBackground(inputBackground());
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        inputParams.topMargin = dp(24);
        card.addView(baseUrlInput, inputParams);

        checkButton = new Button(this);
        checkButton.setText("检查服务端连接");
        checkButton.setTextColor(Color.WHITE);
        checkButton.setTextSize(15);
        checkButton.setAllCaps(false);
        checkButton.setBackground(buttonBackground());
        checkButton.setOnClickListener(v -> runHealthCheck());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        buttonParams.topMargin = dp(14);
        card.addView(checkButton, buttonParams);

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        statusCard.setBackground(statusBackground());
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = dp(18);
        card.addView(statusCard, statusParams);

        statusTitle = new TextView(this);
        statusTitle.setText("等待检查");
        statusTitle.setTextColor(Color.rgb(18, 20, 26));
        statusTitle.setTextSize(18);
        statusTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusCard.addView(statusTitle);

        statusDetail = new TextView(this);
        statusDetail.setText("如果使用 USB 调试，可以先执行 adb reverse tcp:8000 tcp:8000。");
        statusDetail.setTextColor(Color.rgb(104, 112, 130));
        statusDetail.setTextSize(13);
        statusDetail.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        detailParams.topMargin = dp(8);
        statusCard.addView(statusDetail, detailParams);

        TextView footer = new TextView(this);
        footer.setText("当前阶段：Native Stage 1 · Health Check");
        footer.setTextColor(Color.rgb(122, 130, 148));
        footer.setTextSize(12);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        footerParams.topMargin = dp(18);
        root.addView(footer, footerParams);

        return scrollView;
    }

    private void runHealthCheck() {
        String baseUrl = normalizeBaseUrl(baseUrlInput.getText().toString());
        if (baseUrl.isEmpty()) {
            setStatus("地址为空", "请输入电脑服务端或公网隧道地址。", false);
            return;
        }
        saveBaseUrl(baseUrl);
        checkButton.setEnabled(false);
        setStatus("正在连接", baseUrl + "/api/health", true);

        new Thread(() -> {
            try {
                String body = httpGet(baseUrl + "/api/health");
                JSONObject json = new JSONObject(body);
                boolean ok = json.optBoolean("ok", false);
                String requestId = json.optString("request_id", UUID.randomUUID().toString());
                runOnUiThread(() -> {
                    checkButton.setEnabled(true);
                    if (ok) {
                        setStatus("连接成功", "服务端已响应。request_id: " + requestId, true);
                    } else {
                        setStatus("连接异常", "服务端返回了响应，但 ok 不是 true。", false);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    checkButton.setEnabled(true);
                    setStatus("连接失败", error.getClass().getSimpleName() + ": " + error.getMessage(), false);
                });
            }
        }).start();
    }

    private String httpGet(String urlString) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(6000);
        connection.setRequestProperty("Accept", "application/json");
        int status = connection.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream(),
                StandardCharsets.UTF_8
        ));
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        reader.close();
        connection.disconnect();
        if (status < 200 || status >= 400) {
            throw new IllegalStateException("HTTP " + status + " " + body);
        }
        return body.toString();
    }

    private void setStatus(String title, String detail, boolean good) {
        statusTitle.setText(title);
        statusTitle.setTextColor(good ? Color.rgb(10, 132, 80) : Color.rgb(210, 54, 70));
        statusDetail.setText(detail);
    }

    private String loadBaseUrl() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL);
    }

    private void saveBaseUrl(String baseUrl) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_BASE_URL, baseUrl)
                .apply();
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private GradientDrawable pageBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(246, 249, 254),
                        Color.rgb(238, 243, 251),
                        Color.rgb(255, 247, 239)
                }
        );
    }

    private GradientDrawable cardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(224, 255, 255, 255));
        drawable.setCornerRadius(dp(30));
        drawable.setStroke(dp(1), Color.argb(190, 255, 255, 255));
        return drawable;
    }

    private GradientDrawable inputBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(210, 255, 255, 255));
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), Color.argb(40, 20, 26, 38));
        return drawable;
    }

    private GradientDrawable buttonBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(10, 102, 255), Color.rgb(0, 71, 198)}
        );
        drawable.setCornerRadius(dp(18));
        return drawable;
    }

    private GradientDrawable statusBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(148, 246, 249, 254));
        drawable.setCornerRadius(dp(22));
        drawable.setStroke(dp(1), Color.argb(50, 20, 26, 38));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
