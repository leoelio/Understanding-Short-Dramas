package com.banju.nativeapp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
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
import android.widget.FrameLayout;
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
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String PREFS = "banju_native_prefs";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_SESSION_ID = "session_id";
    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:8000";

    private EditText baseUrlInput;
    private EditText usernameInput;
    private EditText passwordInput;
    private TextView messageText;
    private Button loginButton;
    private LinearLayout dramaList;
    private int activeEpisodeId;
    private VideoView activeVideoView;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;
    private Runnable danmakuRunnable;
    private Runnable remixRunnable;
    private LinearLayout activeHighlightPanel;
    private LinearLayout activeDanmakuOverlay;
    private LinearLayout activeRemixPanel;
    private TextView activePlayerStatus;
    private TextView activeDanmakuStatus;
    private Button lightDanmakuButton;
    private Button carnivalDanmakuButton;
    private Button immersiveDanmakuButton;
    private Button activeRemixEntryButton;
    private MediaPlayer remixAudioPlayer;
    private boolean videoPrepared;
    private JSONArray highlightTimeline = new JSONArray();
    private JSONArray danmakuTimeline = new JSONArray();
    private JSONObject remixOptionsPayload;
    private int nextHighlightIndex;
    private int nextDanmakuIndex;
    private int activeHighlightId = -1;
    private boolean remixEntryShown;
    private String activeDanmakuMode = "light";

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
        activeEpisodeId = firstEpisodeId;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        String videoUrl = loadBaseUrl() + "/media/episodes/" + firstEpisodeId;
        TextView status = text("准备播放", 11, Color.argb(210, 236, 242, 255), Typeface.NORMAL);
        activePlayerStatus = status;

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
            scheduleNextHighlight();
            scheduleDanmakuTrack();
            scheduleRemixEntry();
        });
        videoView.setOnErrorListener((mediaPlayer, what, extra) -> {
            status.setText("视频播放失败，请确认服务端和 adb reverse 已连接。");
            return true;
        });
        FrameLayout playerFrame = new FrameLayout(this);
        root.addView(playerFrame, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        playerFrame.addView(videoView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        View topScrim = new View(this);
        topScrim.setBackground(topScrimBackground());
        playerFrame.addView(topScrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(132),
                Gravity.TOP
        ));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(16), dp(26), dp(16), dp(8));
        playerFrame.addView(topBar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(94),
                Gravity.TOP
        ));

        Button backButton = glassButton("<");
        backButton.setTextSize(24);
        backButton.setOnClickListener(v -> showHomeScreen("已返回短剧首页。"));
        topBar.addView(backButton, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleBlockParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleBlockParams.leftMargin = dp(12);
        topBar.addView(titleBlock, titleBlockParams);

        TextView title = text(dramaTitle + " · 第1集", 18, Color.WHITE, Typeface.BOLD);
        title.setSingleLine(true);
        titleBlock.addView(title, matchWrap());

        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(2);
        titleBlock.addView(status, statusParams);

        activeDanmakuOverlay = new LinearLayout(this);
        activeDanmakuOverlay.setOrientation(LinearLayout.VERTICAL);
        activeDanmakuOverlay.setPadding(dp(12), dp(92), dp(12), 0);
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        );
        playerFrame.addView(activeDanmakuOverlay, overlayParams);

        LinearLayout bottomControls = new LinearLayout(this);
        bottomControls.setOrientation(LinearLayout.VERTICAL);
        bottomControls.setPadding(dp(14), dp(14), dp(14), dp(18));
        bottomControls.setBackground(controlBarBackground());
        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        controlsParams.leftMargin = dp(14);
        controlsParams.rightMargin = dp(14);
        controlsParams.bottomMargin = dp(18);
        playerFrame.addView(bottomControls, controlsParams);

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomControls.addView(modeRow, matchWrap());

        lightDanmakuButton = pillButton("轻聊");
        lightDanmakuButton.setOnClickListener(v -> setDanmakuMode("light"));
        modeRow.addView(lightDanmakuButton, weightHeight(1, dp(38)));

        carnivalDanmakuButton = pillButton("狂欢");
        carnivalDanmakuButton.setOnClickListener(v -> setDanmakuMode("carnival"));
        LinearLayout.LayoutParams carnivalParams = weightHeight(1, dp(38));
        carnivalParams.leftMargin = dp(8);
        modeRow.addView(carnivalDanmakuButton, carnivalParams);

        immersiveDanmakuButton = pillButton("沉浸");
        immersiveDanmakuButton.setOnClickListener(v -> setDanmakuMode("immersive"));
        LinearLayout.LayoutParams immersiveParams = weightHeight(1, dp(38));
        immersiveParams.leftMargin = dp(8);
        modeRow.addView(immersiveDanmakuButton, immersiveParams);

        activeDanmakuStatus = text("弹幕准备中", 11, Color.argb(200, 236, 242, 255), Typeface.NORMAL);
        LinearLayout.LayoutParams danmakuStatusParams = matchWrap();
        danmakuStatusParams.topMargin = dp(8);
        bottomControls.addView(activeDanmakuStatus, danmakuStatusParams);
        updateDanmakuModeButtons();

        activeRemixEntryButton = pillButton("片尾 AI 二创");
        activeRemixEntryButton.setEnabled(false);
        activeRemixEntryButton.setOnClickListener(v -> showRemixEntry(false));
        LinearLayout.LayoutParams remixButtonParams = matchHeight(dp(42));
        remixButtonParams.topMargin = dp(10);
        bottomControls.addView(activeRemixEntryButton, remixButtonParams);

        activeHighlightPanel = buildHighlightPanel();
        FrameLayout.LayoutParams highlightParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        highlightParams.leftMargin = dp(14);
        highlightParams.rightMargin = dp(14);
        highlightParams.bottomMargin = dp(170);
        playerFrame.addView(activeHighlightPanel, highlightParams);

        activeRemixPanel = buildHighlightPanel();
        FrameLayout.LayoutParams remixParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        remixParams.leftMargin = dp(14);
        remixParams.rightMargin = dp(14);
        remixParams.bottomMargin = dp(170);
        playerFrame.addView(activeRemixPanel, remixParams);

        setContentView(root);
        videoView.requestFocus();
        fetchEpisodeHighlights(firstEpisodeId, status);
        fetchEpisodeDanmaku(firstEpisodeId);
        fetchRemixOptions(firstEpisodeId);
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
        stopDanmakuWatcher();
        stopRemixWatcher();
        stopRemixAudio();
        if (activeVideoView != null) {
            activeVideoView.stopPlayback();
            activeVideoView = null;
        }
    }

    private void stopRemixAudio() {
        if (remixAudioPlayer != null) {
            remixAudioPlayer.release();
            remixAudioPlayer = null;
        }
    }

    private void fetchEpisodeHighlights(int episodeId, TextView status) {
        new Thread(() -> {
            try {
                String body = httpGet(loadBaseUrl() + "/api/episodes/" + episodeId, loadToken());
                JSONObject episode = new JSONObject(body);
                JSONArray highlights = episode.optJSONArray("highlights");
                if (highlights == null || highlights.length() == 0) {
                    runOnUiThread(() -> status.setText("视频已准备，暂无高光配置。"));
                    return;
                }

                runOnUiThread(() -> {
                    highlightTimeline = highlights;
                    nextHighlightIndex = 0;
                    updatePlayerStatus();
                    scheduleNextHighlight();
                });
            } catch (Exception error) {
                runOnUiThread(() -> status.setText("视频已准备，高光加载失败：" + error.getMessage()));
            }
        }).start();
    }

    private void scheduleNextHighlight() {
        if (!videoPrepared || activeVideoView == null || nextHighlightIndex >= highlightTimeline.length()) {
            return;
        }
        stopProgressWatcher();
        progressRunnable = () -> {
            if (!videoPrepared || activeVideoView == null) {
                return;
            }
            while (nextHighlightIndex < highlightTimeline.length()) {
                JSONObject currentHighlight = highlightTimeline.optJSONObject(nextHighlightIndex);
                if (currentHighlight == null) {
                    nextHighlightIndex++;
                    continue;
                }
                int startMs = (int) Math.round(currentHighlight.optDouble("start_time_sec", -1) * 1000);
                if (startMs < 0) {
                    nextHighlightIndex++;
                    continue;
                }
                int positionMs = Math.max(0, activeVideoView.getCurrentPosition());
                if (positionMs >= startMs) {
                    nextHighlightIndex++;
                    if (activePlayerStatus != null) {
                        activePlayerStatus.setText("高光已触发，选择你的反应。");
                    }
                    showHighlight(currentHighlight);
                    return;
                }
                progressHandler.postDelayed(progressRunnable, 300);
                return;
            }
        };
        progressHandler.post(progressRunnable);
    }

    private void stopProgressWatcher() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
    }

    private void stopDanmakuWatcher() {
        if (danmakuRunnable != null) {
            progressHandler.removeCallbacks(danmakuRunnable);
            danmakuRunnable = null;
        }
    }

    private void stopRemixWatcher() {
        if (remixRunnable != null) {
            progressHandler.removeCallbacks(remixRunnable);
            remixRunnable = null;
        }
    }

    private void resetHighlightState() {
        stopProgressWatcher();
        stopDanmakuWatcher();
        stopRemixWatcher();
        videoPrepared = false;
        highlightTimeline = new JSONArray();
        danmakuTimeline = new JSONArray();
        remixOptionsPayload = null;
        nextHighlightIndex = 0;
        nextDanmakuIndex = 0;
        activeHighlightId = -1;
        remixEntryShown = false;
        activeHighlightPanel = null;
        activeDanmakuOverlay = null;
        activeRemixPanel = null;
        activePlayerStatus = null;
        activeDanmakuStatus = null;
        lightDanmakuButton = null;
        carnivalDanmakuButton = null;
        immersiveDanmakuButton = null;
        activeRemixEntryButton = null;
    }

    private void updatePlayerStatus() {
        if (activePlayerStatus == null) {
            return;
        }
        JSONObject nextHighlight = nextHighlight();
        if (nextHighlight != null) {
            int startSec = (int) Math.round(nextHighlight.optDouble("start_time_sec", 0));
            activePlayerStatus.setText("视频已准备，已加载 " + highlightTimeline.length() + " 个高光，下一高光 " + startSec + "s。");
        } else {
            activePlayerStatus.setText("视频已准备，正在播放。");
        }
    }

    private JSONObject nextHighlight() {
        if (nextHighlightIndex < 0 || nextHighlightIndex >= highlightTimeline.length()) {
            return null;
        }
        return highlightTimeline.optJSONObject(nextHighlightIndex);
    }

    private void fetchEpisodeDanmaku(int episodeId) {
        new Thread(() -> {
            try {
                String body = httpGet(loadBaseUrl() + "/api/episodes/" + episodeId + "/danmaku", loadToken());
                JSONArray rows = new JSONArray(body);
                runOnUiThread(() -> {
                    danmakuTimeline = rows;
                    nextDanmakuIndex = 0;
                    updateDanmakuStatus();
                    scheduleDanmakuTrack();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (activeDanmakuStatus != null) {
                        activeDanmakuStatus.setText("弹幕加载失败：" + error.getMessage());
                    }
                });
            }
        }).start();
    }

    private void setDanmakuMode(String mode) {
        activeDanmakuMode = mode;
        if (activeDanmakuOverlay != null) {
            activeDanmakuOverlay.removeAllViews();
        }
        resetDanmakuIndexToCurrent();
        updateDanmakuModeButtons();
        updateDanmakuStatus();
        scheduleDanmakuTrack();
    }

    private void resetDanmakuIndexToCurrent() {
        int positionMs = activeVideoView == null ? 0 : Math.max(0, activeVideoView.getCurrentPosition());
        int startIndex = 0;
        for (int i = 0; i < danmakuTimeline.length(); i++) {
            JSONObject comment = danmakuTimeline.optJSONObject(i);
            if (comment == null) {
                continue;
            }
            int timeMs = (int) Math.round(comment.optDouble("time_sec", 0) * 1000);
            if (timeMs >= positionMs - 2000) {
                startIndex = i;
                break;
            }
        }
        nextDanmakuIndex = startIndex;
    }

    private void updateDanmakuModeButtons() {
        if (lightDanmakuButton == null || carnivalDanmakuButton == null || immersiveDanmakuButton == null) {
            return;
        }
        lightDanmakuButton.setBackground(pillButtonBackground("light".equals(activeDanmakuMode)));
        lightDanmakuButton.setTextColor("light".equals(activeDanmakuMode) ? Color.WHITE : Color.argb(220, 255, 255, 255));
        carnivalDanmakuButton.setBackground(pillButtonBackground("carnival".equals(activeDanmakuMode)));
        carnivalDanmakuButton.setTextColor("carnival".equals(activeDanmakuMode) ? Color.WHITE : Color.argb(220, 255, 255, 255));
        immersiveDanmakuButton.setBackground(pillButtonBackground("immersive".equals(activeDanmakuMode)));
        immersiveDanmakuButton.setTextColor("immersive".equals(activeDanmakuMode) ? Color.WHITE : Color.argb(220, 255, 255, 255));
    }

    private void updateDanmakuStatus() {
        if (activeDanmakuStatus == null) {
            return;
        }
        if ("immersive".equals(activeDanmakuMode)) {
            activeDanmakuStatus.setText("沉浸模式：弹幕已隐藏");
        } else if ("carnival".equals(activeDanmakuMode)) {
            activeDanmakuStatus.setText("狂欢模式：已加载 " + danmakuTimeline.length() + " 条弹幕");
        } else {
            activeDanmakuStatus.setText("轻聊模式：筛选低打扰弹幕");
        }
    }

    private void scheduleDanmakuTrack() {
        if (!videoPrepared || activeVideoView == null || activeDanmakuOverlay == null || danmakuTimeline.length() == 0) {
            return;
        }
        stopDanmakuWatcher();
        danmakuRunnable = () -> {
            if (!videoPrepared || activeVideoView == null || activeDanmakuOverlay == null) {
                return;
            }
            if ("immersive".equals(activeDanmakuMode)) {
                activeDanmakuOverlay.removeAllViews();
                progressHandler.postDelayed(danmakuRunnable, 1000);
                return;
            }
            int positionMs = Math.max(0, activeVideoView.getCurrentPosition());
            while (nextDanmakuIndex < danmakuTimeline.length()) {
                JSONObject comment = danmakuTimeline.optJSONObject(nextDanmakuIndex);
                if (comment == null) {
                    nextDanmakuIndex++;
                    continue;
                }
                int timeMs = (int) Math.round(comment.optDouble("time_sec", 0) * 1000);
                if (timeMs < positionMs - 2500) {
                    nextDanmakuIndex++;
                    continue;
                }
                if (timeMs <= positionMs + 350) {
                    nextDanmakuIndex++;
                    if (shouldShowDanmaku(comment)) {
                        showDanmakuBubble(comment);
                        progressHandler.postDelayed(danmakuRunnable, danmakuIntervalMs());
                        return;
                    }
                    continue;
                }
                progressHandler.postDelayed(danmakuRunnable, 400);
                return;
            }
        };
        progressHandler.post(danmakuRunnable);
    }

    private boolean shouldShowDanmaku(JSONObject comment) {
        if ("immersive".equals(activeDanmakuMode)) {
            return false;
        }
        String mode = comment.optString("mode", "light");
        if ("carnival".equals(activeDanmakuMode)) {
            return true;
        }
        return "light".equals(mode);
    }

    private int danmakuIntervalMs() {
        return "carnival".equals(activeDanmakuMode) ? 520 : 1200;
    }

    private void showDanmakuBubble(JSONObject comment) {
        if (activeDanmakuOverlay == null) {
            return;
        }
        JSONObject user = comment.optJSONObject("user");
        String nickname = user == null ? "观众" : user.optString("nickname", "观众");
        String text = comment.optString("text", "");
        if (text.isEmpty()) {
            return;
        }
        if (!"carnival".equals(activeDanmakuMode)) {
            activeDanmakuOverlay.removeAllViews();
        }
        while (activeDanmakuOverlay.getChildCount() >= ("carnival".equals(activeDanmakuMode) ? 3 : 1)) {
            activeDanmakuOverlay.removeViewAt(0);
        }
        TextView bubble = text(nickname + "：" + text, "carnival".equals(activeDanmakuMode) ? 13 : 12, Color.WHITE, Typeface.BOLD);
        bubble.setSingleLine(true);
        bubble.setPadding(dp(12), dp(7), dp(12), dp(7));
        bubble.setBackground(danmakuBubbleBackground());
        bubble.setAlpha(0f);
        bubble.setTranslationX(dp(34));
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(6);
        params.rightMargin = "carnival".equals(activeDanmakuMode) ? dp(22) : dp(90);
        activeDanmakuOverlay.addView(bubble, params);
        bubble.animate().alpha(1f).translationX(0f).setDuration(220).start();
        progressHandler.postDelayed(() -> {
            if (activeDanmakuOverlay != null) {
                bubble.animate().alpha(0f).translationX(-dp(22)).setDuration(180).withEndAction(() -> {
                    if (activeDanmakuOverlay != null) {
                        activeDanmakuOverlay.removeView(bubble);
                    }
                }).start();
            }
        }, "carnival".equals(activeDanmakuMode) ? 2800 : 4200);
    }

    private void fetchRemixOptions(int episodeId) {
        new Thread(() -> {
            try {
                String body = httpGet(loadBaseUrl() + "/api/episodes/" + episodeId + "/remix-options", loadToken());
                JSONObject payload = new JSONObject(body);
                JSONArray options = payload.optJSONArray("options");
                runOnUiThread(() -> {
                    remixOptionsPayload = payload;
                    if (activeRemixEntryButton != null) {
                        activeRemixEntryButton.setEnabled(options != null && options.length() > 0);
                        activeRemixEntryButton.setText(options != null && options.length() > 0 ? "片尾 AI 二创" : "暂无片尾二创");
                    }
                    scheduleRemixEntry();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (activeRemixEntryButton != null) {
                        activeRemixEntryButton.setText("二创入口加载失败");
                        activeRemixEntryButton.setEnabled(false);
                    }
                });
            }
        }).start();
    }

    private void scheduleRemixEntry() {
        if (!videoPrepared || activeVideoView == null || remixOptionsPayload == null || remixEntryShown) {
            return;
        }
        stopRemixWatcher();
        double triggerSec = remixOptionsPayload.optDouble("trigger_time_sec", -1);
        if (triggerSec < 0) {
            return;
        }
        remixRunnable = () -> {
            if (!videoPrepared || activeVideoView == null || remixOptionsPayload == null || remixEntryShown) {
                return;
            }
            int positionMs = Math.max(0, activeVideoView.getCurrentPosition());
            if (positionMs >= (int) Math.round(triggerSec * 1000)) {
                showRemixEntry(true);
                return;
            }
            progressHandler.postDelayed(remixRunnable, 1000);
        };
        progressHandler.post(remixRunnable);
    }

    private void showRemixEntry(boolean autoTriggered) {
        if (activeRemixPanel == null) {
            return;
        }
        remixEntryShown = true;
        if (activeHighlightPanel != null) {
            activeHighlightPanel.setVisibility(View.GONE);
        }
        activeRemixPanel.removeAllViews();
        activeRemixPanel.setVisibility(View.VISIBLE);

        TextView badge = text(autoTriggered ? "片尾拓展" : "AI 二创", 12, Color.rgb(83, 103, 160), Typeface.BOLD);
        activeRemixPanel.addView(badge, matchWrap());

        TextView title = text("选一个你想看的后续", 20, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(6);
        activeRemixPanel.addView(title, titleParams);

        JSONArray options = remixOptionsPayload == null ? null : remixOptionsPayload.optJSONArray("options");
        if (options == null || options.length() == 0) {
            TextView empty = text("本集暂未配置片尾二创。", 14, Color.rgb(88, 98, 118), Typeface.NORMAL);
            LinearLayout.LayoutParams emptyParams = matchWrap();
            emptyParams.topMargin = dp(12);
            activeRemixPanel.addView(empty, emptyParams);
            return;
        }
        for (int i = 0; i < options.length(); i++) {
            JSONObject option = options.optJSONObject(i);
            if (option == null) {
                continue;
            }
            String label = option.optString("label", "二创方向");
            Button optionButton = primaryButton(label);
            optionButton.setTextSize(14);
            optionButton.setOnClickListener(v -> showRemixVariants(option));
            LinearLayout.LayoutParams optionParams = matchHeight(dp(44));
            optionParams.topMargin = dp(10);
            activeRemixPanel.addView(optionButton, optionParams);
        }
        animatePanel(activeRemixPanel);
    }

    private void showRemixVariants(JSONObject option) {
        if (activeRemixPanel == null) {
            return;
        }
        activeRemixPanel.removeAllViews();
        String choiceLabel = option.optString("label", "二创方向");
        String choiceDescription = option.optString("description", "");
        TextView title = text(choiceLabel, 20, Color.rgb(18, 20, 26), Typeface.BOLD);
        activeRemixPanel.addView(title, matchWrap());

        TextView desc = text(choiceDescription, 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
        desc.setLineSpacing(dp(2), 1.0f);
        desc.setMaxLines(2);
        LinearLayout.LayoutParams descParams = matchWrap();
        descParams.topMargin = dp(8);
        activeRemixPanel.addView(desc, descParams);

        String choiceKey = option.optString("key", "");
        JSONArray variants = option.optJSONArray("variants");
        if (variants == null || variants.length() == 0) {
            Button generateButton = primaryButton("生成分镜");
            generateButton.setOnClickListener(v -> createRemix(choiceKey, "", choiceLabel));
            LinearLayout.LayoutParams generateParams = matchHeight(dp(44));
            generateParams.topMargin = dp(12);
            activeRemixPanel.addView(generateButton, generateParams);
        } else {
            for (int i = 0; i < variants.length(); i++) {
                JSONObject variant = variants.optJSONObject(i);
                if (variant == null) {
                    continue;
                }
                String variantKey = variant.optString("variant_key", "");
                String label = variant.optString("label", "个性版本");
                Button variantButton = primaryButton(label);
                variantButton.setTextSize(14);
                variantButton.setOnClickListener(v -> createRemix(choiceKey, variantKey, label));
                LinearLayout.LayoutParams variantParams = matchHeight(dp(44));
                variantParams.topMargin = dp(10);
                activeRemixPanel.addView(variantButton, variantParams);
            }
        }

        Button backButton = secondaryButton("返回二创方向");
        backButton.setOnClickListener(v -> showRemixEntry(false));
        LinearLayout.LayoutParams backParams = matchHeight(dp(40));
        backParams.topMargin = dp(12);
        activeRemixPanel.addView(backButton, backParams);
        animatePanel(activeRemixPanel);
    }

    private void createRemix(String choiceKey, String variantKey, String label) {
        if (activeRemixPanel == null || choiceKey.isEmpty()) {
            return;
        }
        activeRemixPanel.removeAllViews();
        TextView loading = text("正在生成：" + label, 17, Color.rgb(18, 20, 26), Typeface.BOLD);
        loading.setGravity(Gravity.CENTER);
        activeRemixPanel.addView(loading, matchWrap());

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("choice_key", choiceKey);
                if (!variantKey.isEmpty()) {
                    payload.put("variant_key", variantKey);
                }
                payload.put("session_id", loadSessionId());
                String body = httpPost(loadBaseUrl() + "/api/episodes/" + activeEpisodeId + "/ai-remix", payload.toString(), loadToken());
                JSONObject result = new JSONObject(body);
                runOnUiThread(() -> renderRemixResult(result));
            } catch (Exception error) {
                runOnUiThread(() -> renderRemixError(error.getMessage()));
            }
        }).start();
    }

    private void renderRemixResult(JSONObject result) {
        if (activeRemixPanel == null) {
            return;
        }
        JSONObject imagePlan = result.optJSONObject("image_plan");
        JSONArray shots = imagePlan == null ? null : imagePlan.optJSONArray("shots");
        if (shots != null && shots.length() > 0) {
            renderRemixShot(result, imagePlan, shots, 0);
            return;
        }

        activeRemixPanel.removeAllViews();
        TextView badge = text(result.optString("source", "AI 二创"), 12, Color.rgb(83, 103, 160), Typeface.BOLD);
        activeRemixPanel.addView(badge, matchWrap());

        JSONObject choice = result.optJSONObject("choice");
        String titleText = choice == null ? "AI 分镜已生成" : choice.optString("label", "AI 分镜已生成");
        TextView title = text(titleText, 20, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(6);
        activeRemixPanel.addView(title, titleParams);

        TextView story = text(result.optString("story_text", "已生成剧情预测。"), 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
        story.setLineSpacing(dp(2), 1.0f);
        story.setMaxLines(4);
        LinearLayout.LayoutParams storyParams = matchWrap();
        storyParams.topMargin = dp(8);
        activeRemixPanel.addView(story, storyParams);

        JSONArray storyboard = result.optJSONArray("storyboard");
        if (storyboard != null) {
            for (int i = 0; i < storyboard.length() && i < 3; i++) {
                JSONObject shot = storyboard.optJSONObject(i);
                if (shot == null) {
                    continue;
                }
                TextView shotView = text("镜头 " + (i + 1) + "：" + shot.optString("subtitle", shot.optString("shot", "")), 13, Color.rgb(28, 45, 76), Typeface.BOLD);
                shotView.setSingleLine(true);
                LinearLayout.LayoutParams shotParams = matchWrap();
                shotParams.topMargin = dp(8);
                activeRemixPanel.addView(shotView, shotParams);
            }
        }

        Button againButton = secondaryButton("重新选择");
        againButton.setOnClickListener(v -> showRemixEntry(false));
        LinearLayout.LayoutParams againParams = matchHeight(dp(40));
        againParams.topMargin = dp(12);
        activeRemixPanel.addView(againButton, againParams);
        animatePanel(activeRemixPanel);
    }

    private void renderRemixShot(JSONObject result, JSONObject imagePlan, JSONArray shots, int shotIndex) {
        if (activeRemixPanel == null) {
            return;
        }
        int safeIndex = Math.max(0, Math.min(shotIndex, shots.length() - 1));
        JSONObject shot = shots.optJSONObject(safeIndex);
        if (shot == null) {
            renderRemixError("二创图片数据异常");
            return;
        }

        activeRemixPanel.removeAllViews();
        JSONObject choice = result.optJSONObject("choice");
        String titleText = choice == null ? "AI 二创分镜" : choice.optString("label", "AI 二创分镜");
        TextView badge = text("镜头 " + (safeIndex + 1) + " / " + shots.length(), 12, Color.rgb(83, 103, 160), Typeface.BOLD);
        activeRemixPanel.addView(badge, matchWrap());

        TextView title = text(titleText, 18, Color.rgb(18, 20, 26), Typeface.BOLD);
        title.setSingleLine(true);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(6);
        activeRemixPanel.addView(title, titleParams);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(imagePlaceholderBackground());
        image.setOnClickListener(v -> renderRemixShot(result, imagePlan, shots, (safeIndex + 1) % shots.length()));
        LinearLayout.LayoutParams imageParams = matchHeight(dp(238));
        imageParams.topMargin = dp(10);
        activeRemixPanel.addView(image, imageParams);
        loadImageInto(image, shotImageUrl(shot));

        String subtitle = shot.optString("subtitle", shot.optString("caption", ""));
        TextView subtitleView = text(subtitle.isEmpty() ? "点击图片切换下一镜头" : subtitle, 14, Color.rgb(28, 45, 76), Typeface.BOLD);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setSingleLine(true);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(8);
        activeRemixPanel.addView(subtitleView, subtitleParams);

        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams navParams = matchWrap();
        navParams.topMargin = dp(10);
        activeRemixPanel.addView(navRow, navParams);

        Button previousButton = secondaryButton("上一张");
        previousButton.setOnClickListener(v -> renderRemixShot(result, imagePlan, shots, safeIndex == 0 ? shots.length() - 1 : safeIndex - 1));
        navRow.addView(previousButton, weightHeight(1, dp(40)));

        Button nextButton = primaryButton(safeIndex >= shots.length() - 1 ? "回到第一张" : "下一张");
        nextButton.setOnClickListener(v -> renderRemixShot(result, imagePlan, shots, (safeIndex + 1) % shots.length()));
        LinearLayout.LayoutParams nextParams = weightHeight(1, dp(40));
        nextParams.leftMargin = dp(8);
        navRow.addView(nextButton, nextParams);

        LinearLayout voiceRow = new LinearLayout(this);
        voiceRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams voiceParams = matchWrap();
        voiceParams.topMargin = dp(8);
        activeRemixPanel.addView(voiceRow, voiceParams);

        Button originalVoiceButton = secondaryButton("原声讲述");
        originalVoiceButton.setOnClickListener(v -> requestRemixVoice(imagePlan, shot, "original"));
        voiceRow.addView(originalVoiceButton, weightHeight(1, dp(40)));

        Button userVoiceButton = secondaryButton("我的声音");
        userVoiceButton.setOnClickListener(v -> requestRemixVoice(imagePlan, shot, "user"));
        LinearLayout.LayoutParams userVoiceParams = weightHeight(1, dp(40));
        userVoiceParams.leftMargin = dp(8);
        voiceRow.addView(userVoiceButton, userVoiceParams);

        Button againButton = secondaryButton("重新选择剧情");
        againButton.setOnClickListener(v -> showRemixEntry(false));
        LinearLayout.LayoutParams againParams = matchHeight(dp(38));
        againParams.topMargin = dp(8);
        activeRemixPanel.addView(againButton, againParams);
        animatePanel(activeRemixPanel);
    }

    private String shotImageUrl(JSONObject shot) {
        String raw = shot.optString("image_url", "");
        if (raw.isEmpty()) {
            raw = shot.optString("storage_hint", "");
        }
        return absoluteUrl(raw);
    }

    private String absoluteUrl(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw;
        }
        if (raw.startsWith("/")) {
            return loadBaseUrl() + raw;
        }
        return loadBaseUrl() + "/" + raw;
    }

    private void loadImageInto(ImageView imageView, String imageUrl) {
        if (imageUrl.isEmpty()) {
            imageView.setImageDrawable(null);
            return;
        }
        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(8000);
                InputStream stream = connection.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                stream.close();
                connection.disconnect();
                runOnUiThread(() -> imageView.setImageBitmap(bitmap));
            } catch (Exception error) {
                runOnUiThread(() -> imageView.setImageDrawable(null));
            }
        }).start();
    }

    private void requestRemixVoice(JSONObject imagePlan, JSONObject shot, String voiceMode) {
        if (activeRemixPanel == null || imagePlan == null || shot == null) {
            return;
        }
        int shotIndex = shot.optInt("index", 1);
        String choiceKey = imagePlan.optString("choice_key", "");
        String variantKey = imagePlan.optString("variant_key", "");
        if (choiceKey.isEmpty() || variantKey.isEmpty()) {
            renderRemixError("当前分镜缺少声音参数");
            return;
        }
        TextView status = text("正在准备" + ("user".equals(voiceMode) ? "我的声音" : "原声") + "...", 13, Color.rgb(83, 103, 160), Typeface.BOLD);
        activeRemixPanel.addView(status, matchWrap());
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("choice_key", choiceKey);
                payload.put("variant_key", variantKey);
                payload.put("shot_index", shotIndex);
                payload.put("voice_mode", voiceMode);
                payload.put("session_id", loadSessionId());
                String body = httpPost(loadBaseUrl() + "/api/episodes/" + activeEpisodeId + "/remix-voice-clips", payload.toString(), loadToken());
                JSONObject result = new JSONObject(body);
                String audioUrl = absoluteUrl(result.optString("audio_url", ""));
                runOnUiThread(() -> playRemixAudio(audioUrl, status));
            } catch (Exception error) {
                runOnUiThread(() -> status.setText("声音暂不可用：" + error.getMessage()));
            }
        }).start();
    }

    private void playRemixAudio(String audioUrl, TextView status) {
        if (audioUrl.isEmpty()) {
            status.setText("声音暂不可用：缺少音频地址");
            return;
        }
        try {
            stopRemixAudio();
            remixAudioPlayer = new MediaPlayer();
            remixAudioPlayer.setDataSource(audioUrl);
            remixAudioPlayer.setOnPreparedListener(player -> {
                status.setText("正在播放声音");
                player.start();
            });
            remixAudioPlayer.setOnCompletionListener(player -> status.setText("声音播放完成"));
            remixAudioPlayer.setOnErrorListener((player, what, extra) -> {
                status.setText("声音播放失败");
                return true;
            });
            status.setText("正在加载声音...");
            remixAudioPlayer.prepareAsync();
        } catch (Exception error) {
            status.setText("声音播放失败：" + error.getMessage());
        }
    }

    private void renderRemixError(String message) {
        if (activeRemixPanel == null) {
            return;
        }
        activeRemixPanel.removeAllViews();
        TextView error = text("二创生成失败：" + message, 14, Color.rgb(210, 54, 70), Typeface.BOLD);
        activeRemixPanel.addView(error, matchWrap());
        Button backButton = secondaryButton("返回二创方向");
        backButton.setOnClickListener(v -> showRemixEntry(false));
        LinearLayout.LayoutParams backParams = matchHeight(dp(40));
        backParams.topMargin = dp(12);
        activeRemixPanel.addView(backButton, backParams);
        animatePanel(activeRemixPanel);
    }

    private LinearLayout buildHighlightPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackground(highlightBackground());
        panel.setVisibility(View.GONE);
        return panel;
    }

    private void animatePanel(View panel) {
        panel.setAlpha(0f);
        panel.setTranslationY(dp(18));
        panel.animate().alpha(1f).translationY(0f).setDuration(240).start();
    }

    private void showHighlight(JSONObject highlight) {
        if (activeHighlightPanel == null) {
            return;
        }
        if (activeRemixPanel != null) {
            activeRemixPanel.setVisibility(View.GONE);
        }
        activeHighlightPanel.removeAllViews();

        int highlightId = highlight.optInt("id", -1);
        activeHighlightId = highlightId;
        String highlightType = highlight.optString("highlight_type", "高光");
        String emotion = highlight.optString("emotion", "情绪");
        String titleText = highlight.optString("title", "剧情高光");
        String descriptionText = highlight.optString("description", "");
        JSONArray options = highlight.optJSONArray("options");

        TextView badge = text(highlightType + " · " + emotion, 12, Color.rgb(83, 103, 160), Typeface.BOLD);
        activeHighlightPanel.addView(badge, matchWrap());

        TextView title = text(titleText, 20, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(6);
        activeHighlightPanel.addView(title, titleParams);

        TextView description = text(descriptionText, 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
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

        int count = Math.max(1, options == null ? 0 : options.length());
        for (int i = 0; i < count; i++) {
            JSONObject option = options == null ? null : options.optJSONObject(i);
            String label = option == null ? "我有话说" : option.optString("label", "我有话说");
            String optionKey = option == null ? "default" : option.optString("key", "default");
            Button optionButton = primaryButton(label);
            optionButton.setTextSize(13);
            optionButton.setOnClickListener(v -> submitInteraction(highlightId, optionKey, label));
            LinearLayout.LayoutParams optionParams = weightHeight(1, dp(42));
            if (i > 0) {
                optionParams.leftMargin = dp(8);
            }
            optionsRow.addView(optionButton, optionParams);
        }

        Button dismissButton = secondaryButton("暂不互动");
        dismissButton.setOnClickListener(v -> hideHighlightAndScheduleNext());
        LinearLayout.LayoutParams dismissParams = matchHeight(dp(40));
        dismissParams.topMargin = dp(10);
        activeHighlightPanel.addView(dismissButton, dismissParams);

        activeHighlightPanel.setVisibility(View.VISIBLE);
        activeHighlightPanel.bringToFront();
        activeHighlightPanel.requestLayout();
        animatePanel(activeHighlightPanel);
        if (activeHighlightPanel.getParent() instanceof View) {
            ((View) activeHighlightPanel.getParent()).requestLayout();
        }
        progressHandler.postDelayed(() -> {
            if (activeHighlightPanel != null
                    && activeHighlightPanel.getVisibility() == View.VISIBLE
                    && activeHighlightId == highlightId) {
                hideHighlightAndScheduleNext();
            }
        }, 10000);
    }

    private void submitInteraction(int highlightId, String optionKey, String label) {
        if (activeHighlightPanel == null) {
            return;
        }
        activeHighlightPanel.removeAllViews();
        TextView feedback = text("已选择：" + label + "，正在上报...", 16, Color.rgb(10, 132, 80), Typeface.BOLD);
        feedback.setGravity(Gravity.CENTER);
        activeHighlightPanel.addView(feedback, matchWrap());

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("highlight_id", highlightId);
                payload.put("option_key", optionKey);
                payload.put("session_id", loadSessionId());
                httpPost(loadBaseUrl() + "/api/interactions", payload.toString(), loadToken());
                runOnUiThread(() -> showInteractionFeedback("已选择：" + label + "，已上报。"));
            } catch (Exception error) {
                runOnUiThread(() -> showInteractionFeedback("已选择：" + label + "，上报失败。"));
            }
        }).start();
    }

    private void showInteractionFeedback(String message) {
        if (activeHighlightPanel == null) {
            return;
        }
        activeHighlightPanel.removeAllViews();
        TextView feedback = text(message, 16, Color.rgb(10, 132, 80), Typeface.BOLD);
        feedback.setGravity(Gravity.CENTER);
        activeHighlightPanel.addView(feedback, matchWrap());
        progressHandler.postDelayed(this::hideHighlightAndScheduleNext, 1500);
    }

    private void hideHighlightAndScheduleNext() {
        if (activeHighlightPanel != null) {
            activeHighlightPanel.setVisibility(View.GONE);
        }
        activeHighlightId = -1;
        updatePlayerStatus();
        scheduleNextHighlight();
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

    private String loadSessionId() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String sessionId = prefs.getString(KEY_SESSION_ID, "");
        if (!sessionId.isEmpty()) {
            return sessionId;
        }
        String newSessionId = UUID.randomUUID().toString();
        prefs.edit().putString(KEY_SESSION_ID, newSessionId).apply();
        return newSessionId;
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

    private Button glassButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setBackground(glassButtonBackground());
        return button;
    }

    private Button pillButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(pillButtonBackground(false));
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

    private GradientDrawable topScrimBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.argb(230, 0, 0, 0),
                        Color.argb(116, 0, 0, 0),
                        Color.TRANSPARENT
                }
        );
    }

    private GradientDrawable controlBarBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.argb(214, 20, 24, 34),
                        Color.argb(176, 43, 54, 78)
                }
        );
        drawable.setCornerRadius(dp(24));
        drawable.setStroke(dp(1), Color.argb(70, 255, 255, 255));
        return drawable;
    }

    private GradientDrawable glassButtonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(92, 255, 255, 255));
        drawable.setCornerRadius(dp(23));
        drawable.setStroke(dp(1), Color.argb(90, 255, 255, 255));
        return drawable;
    }

    private GradientDrawable pillButtonBackground(boolean selected) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                selected
                        ? new int[]{Color.rgb(0, 122, 255), Color.rgb(86, 156, 255)}
                        : new int[]{Color.argb(84, 255, 255, 255), Color.argb(46, 255, 255, 255)}
        );
        drawable.setCornerRadius(dp(19));
        drawable.setStroke(dp(1), selected ? Color.argb(100, 255, 255, 255) : Color.argb(54, 255, 255, 255));
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

    private GradientDrawable danmakuBubbleBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(210, 8, 15, 30),
                        Color.argb(184, 28, 45, 76)
                }
        );
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), Color.argb(88, 255, 255, 255));
        return drawable;
    }

    private GradientDrawable imagePlaceholderBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(232, 238, 249),
                        Color.rgb(250, 243, 235)
                }
        );
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), Color.argb(54, 20, 26, 38));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
