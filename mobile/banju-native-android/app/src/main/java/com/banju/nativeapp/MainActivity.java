package com.banju.nativeapp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String PREFS = "banju_native_prefs";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:8000";

    private EditText baseUrlInput;
    private EditText usernameInput;
    private EditText passwordInput;
    private TextView messageText;
    private Button loginButton;
    private LinearLayout dramaList;
    private VideoView activeVideoView;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;
    private LinearLayout activeHighlightPanel;
    private TextView activePlayerStatus;
    private boolean firstHighlightTriggered;
    private boolean videoPrepared;
    private int firstHighlightStartMs = -1;
    private String firstHighlightTitle = "";
    private String firstHighlightDescription = "";
    private String firstHighlightType = "";
    private String firstHighlightEmotion = "";
    private JSONArray firstHighlightOptions = new JSONArray();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        if (loadToken().isEmpty()) {
            showLoginScreen("");
        } else {
            showHomeScreen("已恢复登录，正在拉取短剧列表。");
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.rgb(246, 249, 254));
    }

    private void showLoginScreen(String message) {
        stopActiveVideo();
        ScrollView scrollView = newPage();
        LinearLayout root = pageRoot(scrollView);

        LinearLayout card = card();
        root.addView(card, matchWrap());

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.banju_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(82), dp(82));
        logoParams.bottomMargin = dp(18);
        card.addView(logo, logoParams);

        TextView title = text("半句", 38, Color.rgb(18, 20, 26), Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title, matchWrap());

        TextView subtitle = text("短剧陪伴 · 原生 Android", 16, Color.rgb(78, 86, 105), Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(6);
        card.addView(subtitle, subtitleParams);

        TextView intro = text("先完成真实登录和选剧首页。服务端地址可切换，便于 USB 联调或公网演示。", 14, Color.rgb(96, 105, 124), Typeface.NORMAL);
        intro.setGravity(Gravity.CENTER);
        intro.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams introParams = matchWrap();
        introParams.topMargin = dp(18);
        card.addView(intro, introParams);

        baseUrlInput = input(loadBaseUrl(), "服务端地址");
        LinearLayout.LayoutParams inputParams = matchHeight(dp(52));
        inputParams.topMargin = dp(24);
        card.addView(baseUrlInput, inputParams);

        usernameInput = input("user_demo", "用户名");
        LinearLayout.LayoutParams usernameParams = matchHeight(dp(52));
        usernameParams.topMargin = dp(12);
        card.addView(usernameInput, usernameParams);

        passwordInput = input("User12345!", "密码");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams passwordParams = matchHeight(dp(52));
        passwordParams.topMargin = dp(12);
        card.addView(passwordInput, passwordParams);

        loginButton = primaryButton("登录并进入");
        loginButton.setOnClickListener(v -> runLogin());
        LinearLayout.LayoutParams buttonParams = matchHeight(dp(52));
        buttonParams.topMargin = dp(16);
        card.addView(loginButton, buttonParams);

        messageText = text(message.isEmpty() ? "测试账号已预填，可直接登录。" : message, 13, Color.rgb(104, 112, 130), Typeface.NORMAL);
        messageText.setGravity(Gravity.CENTER);
        messageText.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams messageParams = matchWrap();
        messageParams.topMargin = dp(16);
        card.addView(messageText, messageParams);

        setContentView(scrollView);
    }

    private void runLogin() {
        String baseUrl = normalizeBaseUrl(baseUrlInput.getText().toString());
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (baseUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
            setMessage("服务端地址、用户名和密码都需要填写。", false);
            return;
        }

        saveBaseUrl(baseUrl);
        loginButton.setEnabled(false);
        setMessage("正在登录...", true);

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("username", username);
                payload.put("password", password);

                String body = httpPost(baseUrl + "/api/auth/login", payload.toString(), "");
                JSONObject json = new JSONObject(body);
                String token = json.optString("token", "");
                JSONObject user = json.optJSONObject("user");
                String displayName = user == null ? username : user.optString("display_name", username);
                if (token.isEmpty()) {
                    throw new IllegalStateException("服务端没有返回 token");
                }
                saveSession(token, displayName);
                runOnUiThread(() -> showHomeScreen("登录成功，正在拉取短剧列表。"));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    loginButton.setEnabled(true);
                    setMessage("登录失败：" + error.getMessage(), false);
                });
            }
        }).start();
    }

    private void showHomeScreen(String message) {
        stopActiveVideo();
        ScrollView scrollView = newPage();
        LinearLayout root = pageRoot(scrollView);
        root.setGravity(Gravity.NO_GRAVITY);

        LinearLayout header = card();
        header.setGravity(Gravity.NO_GRAVITY);
        root.addView(header, matchWrap());

        TextView eyebrow = text("Banju Native Stage 2", 12, Color.rgb(83, 103, 160), Typeface.BOLD);
        header.addView(eyebrow, matchWrap());

        TextView title = text("短剧首页", 30, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(4);
        header.addView(title, titleParams);

        TextView user = text("当前用户：" + loadDisplayName(), 14, Color.rgb(88, 98, 118), Typeface.NORMAL);
        LinearLayout.LayoutParams userParams = matchWrap();
        userParams.topMargin = dp(8);
        header.addView(user, userParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(16);
        header.addView(actions, actionsParams);

        Button refreshButton = secondaryButton("刷新列表");
        refreshButton.setOnClickListener(v -> fetchDramas());
        actions.addView(refreshButton, weightHeight(1, dp(46)));

        Button logoutButton = secondaryButton("退出登录");
        logoutButton.setOnClickListener(v -> {
            clearSession();
            showLoginScreen("已退出，可重新登录。");
        });
        LinearLayout.LayoutParams logoutParams = weightHeight(1, dp(46));
        logoutParams.leftMargin = dp(10);
        actions.addView(logoutButton, logoutParams);

        messageText = text(message, 13, Color.rgb(104, 112, 130), Typeface.NORMAL);
        LinearLayout.LayoutParams messageParams = matchWrap();
        messageParams.topMargin = dp(18);
        root.addView(messageText, messageParams);

        dramaList = new LinearLayout(this);
        dramaList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = matchWrap();
        listParams.topMargin = dp(12);
        root.addView(dramaList, listParams);

        setContentView(scrollView);
        fetchDramas();
    }

    private void fetchDramas() {
        if (dramaList == null) {
            return;
        }
        dramaList.removeAllViews();
        setMessage("正在加载真实短剧列表...", true);

        new Thread(() -> {
            try {
                String body = httpGet(loadBaseUrl() + "/api/dramas", loadToken());
                JSONArray dramas = new JSONArray(body);
                runOnUiThread(() -> renderDramas(dramas));
            } catch (Exception error) {
                runOnUiThread(() -> setMessage("短剧列表加载失败：" + error.getMessage(), false));
            }
        }).start();
    }

    private void renderDramas(JSONArray dramas) {
        dramaList.removeAllViews();
        setMessage("已加载 " + dramas.length() + " 部短剧。点击“北往”进入播放页。", true);
        for (int i = 0; i < dramas.length(); i++) {
            JSONObject drama = dramas.optJSONObject(i);
            if (drama != null) {
                addDramaCard(drama);
            }
        }
    }

    private void addDramaCard(JSONObject drama) {
        String title = drama.optString("title", "未命名短剧");
        String genre = drama.optString("genre", "未分类");
        String description = drama.optString("description", "");
        int episodeCount = drama.optInt("episode_count", 0);
        int firstEpisodeId = drama.optInt("first_episode_id", 0);

        LinearLayout card = card();
        card.setGravity(Gravity.NO_GRAVITY);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.bottomMargin = dp(12);
        dramaList.addView(card, cardParams);

        TextView titleView = text(title, 21, Color.rgb(18, 20, 26), Typeface.BOLD);
        card.addView(titleView, matchWrap());

        TextView meta = text(genre + " · " + episodeCount + " 集", 13, Color.rgb(83, 103, 160), Typeface.BOLD);
        LinearLayout.LayoutParams metaParams = matchWrap();
        metaParams.topMargin = dp(6);
        card.addView(meta, metaParams);

        TextView desc = text(description.isEmpty() ? "真实服务端返回的短剧条目。" : description, 13, Color.rgb(104, 112, 130), Typeface.NORMAL);
        desc.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams descParams = matchWrap();
        descParams.topMargin = dp(8);
        card.addView(desc, descParams);

        Button enterButton = primaryButton(title.contains("北往") ? "播放北往第一集" : "播放第一集");
        enterButton.setOnClickListener(v -> showNativePlayer(title, firstEpisodeId));
        LinearLayout.LayoutParams buttonParams = matchHeight(dp(46));
        buttonParams.topMargin = dp(14);
        card.addView(enterButton, buttonParams);
    }

    private void showNativePlayer(String dramaTitle, int firstEpisodeId) {
        stopActiveVideo();
        resetHighlightState();
        ScrollView scrollView = newPage();
        LinearLayout root = pageRoot(scrollView);

        String videoUrl = loadBaseUrl() + "/media/episodes/" + firstEpisodeId;

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setBackground(darkCardBackground());
        header.setPadding(dp(18), dp(26), dp(18), dp(22));
        root.addView(header, matchWrap());

        View topSpacer = new View(this);
        header.addView(topSpacer, matchHeight(dp(8)));

        TextView title = text(dramaTitle + " · 第1集", 23, Color.WHITE, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(10);
        header.addView(title, titleParams);

        TextView status = text("正在准备视频...", 14, Color.rgb(214, 222, 238), Typeface.NORMAL);
        activePlayerStatus = status;
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(10);
        header.addView(status, statusParams);

        Button backButton = secondaryButton("返回选剧首页");
        backButton.setOnClickListener(v -> showHomeScreen("已返回短剧首页。"));
        LinearLayout.LayoutParams backParams = matchHeight(dp(46));
        backParams.topMargin = dp(14);
        header.addView(backButton, backParams);

        VideoView videoView = new VideoView(this);
        videoView.setBackgroundColor(Color.BLACK);
        activeVideoView = videoView;
        MediaController controller = new MediaController(this);
        controller.setAnchorView(videoView);
        videoView.setMediaController(controller);
        videoView.setVideoURI(Uri.parse(videoUrl));
        videoView.setOnPreparedListener(mediaPlayer -> {
            videoPrepared = true;
            mediaPlayer.setLooping(false);
            updatePlayerStatus();
            videoView.setBackgroundColor(Color.TRANSPARENT);
            videoView.start();
            scheduleFirstHighlight();
        });
        videoView.setOnErrorListener((mediaPlayer, what, extra) -> {
            status.setText("视频播放失败，请确认服务端和 adb reverse 已连接。");
            return true;
        });
        LinearLayout.LayoutParams videoParams = matchHeight(videoHeight());
        videoParams.topMargin = dp(16);
        root.addView(videoView, videoParams);

        activeHighlightPanel = buildHighlightPanel();
        LinearLayout.LayoutParams highlightParams = matchWrap();
        highlightParams.topMargin = dp(14);
        header.addView(activeHighlightPanel, highlightParams);

        setContentView(scrollView);
        videoView.requestFocus();
        fetchFirstHighlight(firstEpisodeId, status);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (activeVideoView != null && activeVideoView.isPlaying()) {
            activeVideoView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        stopActiveVideo();
        super.onDestroy();
    }

    private void stopActiveVideo() {
        stopProgressWatcher();
        if (activeVideoView != null) {
            activeVideoView.stopPlayback();
            activeVideoView = null;
        }
    }

    private void fetchFirstHighlight(int episodeId, TextView status) {
        new Thread(() -> {
            try {
                String body = httpGet(loadBaseUrl() + "/api/episodes/" + episodeId, loadToken());
                JSONObject episode = new JSONObject(body);
                JSONArray highlights = episode.optJSONArray("highlights");
                if (highlights == null || highlights.length() == 0) {
                    runOnUiThread(() -> status.setText("视频已准备，暂无高光配置。"));
                    return;
                }

                JSONObject first = highlights.optJSONObject(0);
                if (first == null) {
                    runOnUiThread(() -> status.setText("视频已准备，高光数据为空。"));
                    return;
                }

                int startMs = (int) Math.round(first.optDouble("start_time_sec", -1) * 1000);
                JSONArray options = first.optJSONArray("options");
                runOnUiThread(() -> {
                    firstHighlightStartMs = startMs;
                    firstHighlightTitle = first.optString("title", "剧情高光");
                    firstHighlightDescription = first.optString("description", "");
                    firstHighlightType = first.optString("highlight_type", "高光");
                    firstHighlightEmotion = first.optString("emotion", "情绪");
                    firstHighlightOptions = options == null ? new JSONArray() : options;
                    updatePlayerStatus();
                    scheduleFirstHighlight();
                });
            } catch (Exception error) {
                runOnUiThread(() -> status.setText("视频已准备，高光加载失败：" + error.getMessage()));
            }
        }).start();
    }

    private void scheduleFirstHighlight() {
        if (!videoPrepared || activeVideoView == null || firstHighlightStartMs < 0 || firstHighlightTriggered) {
            return;
        }
        stopProgressWatcher();
        int positionMs = Math.max(0, activeVideoView.getCurrentPosition());
        long delayMs = Math.max(300, firstHighlightStartMs - positionMs);
        progressRunnable = () -> {
            if (activeVideoView != null && !firstHighlightTriggered) {
                firstHighlightTriggered = true;
                if (activePlayerStatus != null) {
                    activePlayerStatus.setText("高光已触发，选择你的反应。");
                }
                showFirstHighlight();
            }
        };
        progressHandler.postDelayed(progressRunnable, delayMs);
        if (activeHighlightPanel != null) {
            activeHighlightPanel.postDelayed(progressRunnable, delayMs);
        }
        if (activePlayerStatus != null) {
            activePlayerStatus.postDelayed(progressRunnable, delayMs);
        }
    }

    private void stopProgressWatcher() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
            if (activeHighlightPanel != null) {
                activeHighlightPanel.removeCallbacks(progressRunnable);
            }
            if (activePlayerStatus != null) {
                activePlayerStatus.removeCallbacks(progressRunnable);
            }
            progressRunnable = null;
        }
    }

    private void resetHighlightState() {
        stopProgressWatcher();
        firstHighlightTriggered = false;
        videoPrepared = false;
        firstHighlightStartMs = -1;
        firstHighlightTitle = "";
        firstHighlightDescription = "";
        firstHighlightType = "";
        firstHighlightEmotion = "";
        firstHighlightOptions = new JSONArray();
        activeHighlightPanel = null;
        activePlayerStatus = null;
    }

    private void updatePlayerStatus() {
        if (activePlayerStatus == null) {
            return;
        }
        if (firstHighlightStartMs >= 0) {
            activePlayerStatus.setText("视频已准备，首个高光将在 " + Math.max(0, firstHighlightStartMs / 1000) + "s 触发。");
        } else {
            activePlayerStatus.setText("视频已准备，正在播放。");
        }
    }

    private LinearLayout buildHighlightPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackground(highlightBackground());
        panel.setVisibility(View.GONE);
        return panel;
    }

    private void showFirstHighlight() {
        if (activeHighlightPanel == null) {
            return;
        }
        activeHighlightPanel.removeAllViews();

        TextView badge = text(firstHighlightType + " · " + firstHighlightEmotion, 12, Color.rgb(83, 103, 160), Typeface.BOLD);
        activeHighlightPanel.addView(badge, matchWrap());

        TextView title = text(firstHighlightTitle, 20, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(6);
        activeHighlightPanel.addView(title, titleParams);

        TextView description = text(firstHighlightDescription, 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
        description.setLineSpacing(dp(2), 1.0f);
        description.setMaxLines(3);
        LinearLayout.LayoutParams descParams = matchWrap();
        descParams.topMargin = dp(8);
        activeHighlightPanel.addView(description, descParams);

        LinearLayout optionsRow = new LinearLayout(this);
        optionsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = matchWrap();
        rowParams.topMargin = dp(12);
        activeHighlightPanel.addView(optionsRow, rowParams);

        int count = Math.max(1, firstHighlightOptions.length());
        for (int i = 0; i < count; i++) {
            JSONObject option = firstHighlightOptions.optJSONObject(i);
            String label = option == null ? "我有话说" : option.optString("label", "我有话说");
            Button optionButton = primaryButton(label);
            optionButton.setTextSize(13);
            optionButton.setOnClickListener(v -> {
                activeHighlightPanel.removeAllViews();
                TextView feedback = text("已选择：" + label, 18, Color.rgb(10, 132, 80), Typeface.BOLD);
                feedback.setGravity(Gravity.CENTER);
                activeHighlightPanel.addView(feedback, matchWrap());
                progressHandler.postDelayed(() -> {
                    if (activeHighlightPanel != null) {
                        activeHighlightPanel.setVisibility(View.GONE);
                    }
                }, 1500);
            });
            LinearLayout.LayoutParams optionParams = weightHeight(1, dp(42));
            if (i > 0) {
                optionParams.leftMargin = dp(8);
            }
            optionsRow.addView(optionButton, optionParams);
        }

        Button dismissButton = secondaryButton("暂不互动");
        dismissButton.setOnClickListener(v -> activeHighlightPanel.setVisibility(View.GONE));
        LinearLayout.LayoutParams dismissParams = matchHeight(dp(40));
        dismissParams.topMargin = dp(10);
        activeHighlightPanel.addView(dismissButton, dismissParams);

        activeHighlightPanel.setVisibility(View.VISIBLE);
        activeHighlightPanel.bringToFront();
        activeHighlightPanel.requestLayout();
        if (activeHighlightPanel.getParent() instanceof View) {
            ((View) activeHighlightPanel.getParent()).requestLayout();
        }
    }

    private String httpGet(String urlString, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(6000);
        connection.setRequestProperty("Accept", "application/json");
        if (!token.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        return readResponse(connection);
    }

    private String httpPost(String urlString, String jsonBody, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (!token.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        byte[] data = jsonBody.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(data.length);
        OutputStream output = connection.getOutputStream();
        output.write(data);
        output.close();
        return readResponse(connection);
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder body = new StringBuilder();
        if (stream != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            reader.close();
        }
        connection.disconnect();
        if (status < 200 || status >= 400) {
            throw new IllegalStateException("HTTP " + status + " " + body);
        }
        return body.toString();
    }

    private void setMessage(String message, boolean good) {
        if (messageText == null) {
            return;
        }
        messageText.setText(message);
        messageText.setTextColor(good ? Color.rgb(10, 132, 80) : Color.rgb(210, 54, 70));
    }

    private String loadBaseUrl() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        return normalizeBaseUrl(prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL));
    }

    private void saveBaseUrl(String baseUrl) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_BASE_URL, normalizeBaseUrl(baseUrl))
                .apply();
    }

    private String loadToken() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_TOKEN, "");
    }

    private String loadDisplayName() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_DISPLAY_NAME, "普通用户");
    }

    private void saveSession(String token, String displayName) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_DISPLAY_NAME, displayName)
                .apply();
    }

    private void clearSession() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .remove(KEY_TOKEN)
                .remove(KEY_DISPLAY_NAME)
                .apply();
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private ScrollView newPage() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        return scrollView;
    }

    private LinearLayout pageRoot(ScrollView scrollView) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(34), dp(22), dp(28));
        root.setBackground(pageBackground());
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        return root;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(24), dp(26), dp(24), dp(24));
        card.setBackground(cardBackground());
        return card;
    }

    private TextView text(String value, int sizeSp, int color, int style) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        text.setTypeface(Typeface.DEFAULT, style);
        return text;
    }

    private EditText input(String value, String hint) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(value);
        input.setHint(hint);
        input.setTextColor(Color.rgb(18, 20, 26));
        input.setHintTextColor(Color.rgb(130, 138, 154));
        input.setTextSize(14);
        input.setPadding(dp(16), 0, dp(16), 0);
        input.setBackground(inputBackground());
        return input;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setBackground(buttonBackground());
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.rgb(28, 45, 76));
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackground(secondaryButtonBackground());
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams matchHeight(int height) {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height
        );
    }

    private LinearLayout.LayoutParams weightHeight(float weight, int height) {
        return new LinearLayout.LayoutParams(0, height, weight);
    }

    private int videoHeight() {
        int width = getResources().getDisplayMetrics().widthPixels - dp(44);
        int height = width * 16 / 9;
        return Math.min(height, dp(640));
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
        drawable.setColor(Color.argb(228, 255, 255, 255));
        drawable.setCornerRadius(dp(28));
        drawable.setStroke(dp(1), Color.argb(190, 255, 255, 255));
        return drawable;
    }

    private GradientDrawable darkCardBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(10, 16, 28),
                        Color.rgb(28, 45, 76),
                        Color.rgb(18, 20, 26)
                }
        );
        drawable.setCornerRadius(dp(18));
        return drawable;
    }

    private GradientDrawable inputBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(220, 255, 255, 255));
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), Color.argb(46, 20, 26, 38));
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

    private GradientDrawable secondaryButtonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(200, 255, 255, 255));
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), Color.argb(54, 20, 26, 38));
        return drawable;
    }

    private GradientDrawable highlightBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(255, 255, 255),
                        Color.rgb(242, 248, 255),
                        Color.rgb(255, 247, 238)
                }
        );
        drawable.setCornerRadius(dp(22));
        drawable.setStroke(dp(1), Color.argb(70, 20, 26, 38));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
