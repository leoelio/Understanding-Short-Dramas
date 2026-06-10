package com.banju.nativeapp;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String PREFS = "banju_native_prefs";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_SESSION_ID = "session_id";
    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:8000";
    private static final int REQUEST_VOICE_SAMPLE = 4301;
    private static final int REQUEST_RECORD_AUDIO = 4302;
    private static final int REQUEST_AVATAR_IMAGE = 4303;
    private static final String VOICE_CONSENT_TEXT = "同意利用录入声音生成音频";
    private static final String VOICE_PREVIEW_TEXT = "片尾拓展已开启，我会用你的声音陪你猜下一段剧情。";

    private EditText baseUrlInput;
    private EditText usernameInput;
    private EditText passwordInput;
    private TextView messageText;
    private Button loginButton;
    private LinearLayout dramaList;
    private LinearLayout profileContent;
    private TextView avatarActionStatus;
    private EditText profileDisplayNameInput;
    private JSONArray avatarPool = new JSONArray();
    private int avatarPoolPage;
    private TextView voiceActionStatus;
    private Button voiceRecordButton;
    private MediaRecorder voiceRecorder;
    private File voiceRecordFile;
    private boolean voiceRecording;
    private LinearLayout chatContent;
    private LinearLayout chatMessagesContent;
    private EditText chatMessageInput;
    private LinearLayout socialFeedContent;
    private String activeSocialScope = "all";
    private String socialPublishVisibility = "public";
    private EditText socialTitleInput;
    private EditText socialTextInput;
    private EditText socialTopicInput;
    private LinearLayout watchRoomContent;
    private LinearLayout watchRoomEventsContent;
    private EditText watchRoomEventInput;
    private int activeEpisodeId;
    private int lastWatchHistoryEpisodeId = -1;
    private int lastWatchHistoryProgressSec = -1;
    private long lastWatchHistoryPostAtMs;
    private String activeRoomCode = "";
    private String activePlayerRoomCode = "";
    private VideoView activeVideoView;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;
    private Runnable danmakuRunnable;
    private Runnable remixRunnable;
    private Runnable roomSyncRunnable;
    private Runnable roomEventsRunnable;
    private Runnable highlightTapRunnable;
    private Runnable stickerRunnable;
    private LinearLayout activeHighlightPanel;
    private LinearLayout activeDanmakuOverlay;
    private FrameLayout activeHighlightEffectLayer;
    private LinearLayout activeRemixPanel;
    private LinearLayout activeWatchRoomStrip;
    private LinearLayout activeWatchRoomAvatars;
    private LinearLayout activePlayerControls;
    private TextView activePlayerStatus;
    private TextView activeDanmakuStatus;
    private TextView activeWatchRoomStatus;
    private TextView activeWatchRoomBoardStatus;
    private Button lightDanmakuButton;
    private Button carnivalDanmakuButton;
    private Button immersiveDanmakuButton;
    private Button activeRemixEntryButton;
    private Button activeRemixOriginalVoiceButton;
    private Button activeRemixUserVoiceButton;
    private Button activeRemixVoicePublishButton;
    private TextView activeRemixVoiceStatus;
    private MediaPlayer remixAudioPlayer;
    private boolean videoPrepared;
    private JSONArray highlightTimeline = new JSONArray();
    private JSONArray danmakuTimeline = new JSONArray();
    private JSONArray stickerTimeline = new JSONArray();
    private JSONObject remixOptionsPayload;
    private int nextHighlightIndex;
    private int nextDanmakuIndex;
    private int lastRoomEventId;
    private int activeHighlightId = -1;
    private int highlightEffectSeed;
    private int activeHighlightTapCount;
    private boolean activeHighlightTapSubmitted;
    private long[] stickerSlotLastShownAtMs = new long[0];
    private boolean remixEntryShown;
    private int remixVoiceRequestSeq;
    private String activeDanmakuMode = "light";
    private final Map<String, Integer> activeRoomChoiceCounts = new HashMap<>();
    private String activeRoomLatestAction = "";

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VOICE_SAMPLE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            uploadVoiceSample(data.getData());
        }
        if (requestCode == REQUEST_AVATAR_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            uploadAvatarImage(data.getData());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecording();
            } else {
                setMessage("未授予麦克风权限，无法录音。", false);
                if (voiceActionStatus != null) {
                    voiceActionStatus.setText("未授予麦克风权限。");
                }
            }
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.rgb(246, 249, 254));
    }

    private void enterPlayerImmersiveMode() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.BLACK);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void exitPlayerImmersiveMode() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        configureWindow();
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

        TextView eyebrow = text("半句 · 短剧陪伴", 12, Color.rgb(83, 103, 160), Typeface.BOLD);
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

        Button chatButton = primaryButton("聊聊");
        chatButton.setOnClickListener(v -> showChatScreen("正在加载好友动态。"));
        LinearLayout.LayoutParams chatParams = weightHeight(1, dp(46));
        chatParams.leftMargin = dp(10);
        actions.addView(chatButton, chatParams);

        Button socialButton = primaryButton("逛逛");
        socialButton.setOnClickListener(v -> showSocialFeedScreen("正在加载动态流。", "all"));
        LinearLayout.LayoutParams socialParams = weightHeight(1, dp(46));
        socialParams.leftMargin = dp(10);
        actions.addView(socialButton, socialParams);

        Button profileButton = primaryButton("我的");
        profileButton.setOnClickListener(v -> showProfileScreen("正在加载账号状态。"));
        LinearLayout.LayoutParams profileParams = weightHeight(1, dp(46));
        profileParams.leftMargin = dp(10);
        actions.addView(profileButton, profileParams);

        Button logoutButton = secondaryButton("退出登录");
        logoutButton.setOnClickListener(v -> {
            clearSession();
            showLoginScreen("已退出，可重新登录。");
        });
        LinearLayout.LayoutParams logoutParams = matchHeight(dp(44));
        logoutParams.topMargin = dp(10);
        header.addView(logoutButton, logoutParams);

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

    private void showSocialFeedScreen(String message, String scope) {
        stopActiveVideo();
        ScrollView scrollView = newPage();
        LinearLayout root = pageRoot(scrollView);
        root.setGravity(Gravity.NO_GRAVITY);

        LinearLayout header = card();
        header.setGravity(Gravity.NO_GRAVITY);
        root.addView(header, matchWrap());

        header.addView(text("Discover", 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());
        TextView title = text("逛逛", 30, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(4);
        header.addView(title, titleParams);

        TextView subtitle = text("AI 声音、剧情卡和朋友动态", 14, Color.rgb(88, 98, 118), Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(8);
        header.addView(subtitle, subtitleParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(16);
        header.addView(actions, actionsParams);

        Button homeButton = secondaryButton("选剧");
        homeButton.setOnClickListener(v -> showHomeScreen("已返回短剧首页。"));
        actions.addView(homeButton, weightHeight(1, dp(46)));

        Button chatButton = primaryButton("聊聊");
        chatButton.setOnClickListener(v -> showChatScreen("正在加载好友动态。"));
        LinearLayout.LayoutParams chatParams = weightHeight(1, dp(46));
        chatParams.leftMargin = dp(10);
        actions.addView(chatButton, chatParams);

        Button profileButton = primaryButton("我的");
        profileButton.setOnClickListener(v -> showProfileScreen("正在加载账号状态。"));
        LinearLayout.LayoutParams profileParams = weightHeight(1, dp(46));
        profileParams.leftMargin = dp(10);
        actions.addView(profileButton, profileParams);

        LinearLayout scopeRow = new LinearLayout(this);
        scopeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams scopeParams = matchWrap();
        scopeParams.topMargin = dp(12);
        header.addView(scopeRow, scopeParams);

        Button allButton = secondaryButton("全部");
        allButton.setOnClickListener(v -> fetchSocialFeed("all"));
        scopeRow.addView(allButton, weightHeight(1, dp(42)));

        Button friendsButton = secondaryButton("好友");
        friendsButton.setOnClickListener(v -> fetchSocialFeed("friends"));
        LinearLayout.LayoutParams friendsParams = weightHeight(1, dp(42));
        friendsParams.leftMargin = dp(8);
        scopeRow.addView(friendsButton, friendsParams);

        Button mineButton = secondaryButton("我的");
        mineButton.setOnClickListener(v -> fetchSocialFeed("mine"));
        LinearLayout.LayoutParams mineParams = weightHeight(1, dp(42));
        mineParams.leftMargin = dp(8);
        scopeRow.addView(mineButton, mineParams);

        addSocialPublishComposer(header);

        messageText = text(message, 13, Color.rgb(104, 112, 130), Typeface.NORMAL);
        LinearLayout.LayoutParams messageParams = matchWrap();
        messageParams.topMargin = dp(18);
        root.addView(messageText, messageParams);

        socialFeedContent = new LinearLayout(this);
        socialFeedContent.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentParams = matchWrap();
        contentParams.topMargin = dp(12);
        root.addView(socialFeedContent, contentParams);

        setContentView(scrollView);
        fetchSocialFeed(scope);
    }

    private void fetchSocialFeed(String scope) {
        if (socialFeedContent == null) {
            return;
        }
        String safeScope = (scope == null || scope.trim().isEmpty()) ? "all" : scope.trim();
        activeSocialScope = safeScope;
        socialFeedContent.removeAllViews();
        setMessage("正在加载逛逛动态...", true);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject(httpGet(loadBaseUrl() + "/api/social/feed?scope=" + safeScope, loadToken()));
                runOnUiThread(() -> renderSocialFeed(payload));
            } catch (Exception error) {
                runOnUiThread(() -> setMessage("逛逛加载失败：" + error.getMessage(), false));
            }
        }).start();
    }

    private void renderSocialFeed(JSONObject payload) {
        if (socialFeedContent == null) {
            return;
        }
        socialFeedContent.removeAllViews();
        JSONArray topics = payload.optJSONArray("topics");
        JSONArray posts = payload.optJSONArray("posts");
        setMessage("已加载 " + arrayLength(posts) + " 条动态。", true);

        LinearLayout topicCard = card();
        topicCard.setGravity(Gravity.NO_GRAVITY);
        topicCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams topicParams = matchWrap();
        topicParams.bottomMargin = dp(12);
        socialFeedContent.addView(topicCard, topicParams);
        topicCard.addView(text("今日专题", 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());
        if (topics == null || topics.length() == 0) {
            TextView empty = text("暂无专题。", 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
            LinearLayout.LayoutParams emptyParams = matchWrap();
            emptyParams.topMargin = dp(8);
            topicCard.addView(empty, emptyParams);
        } else {
            for (int i = 0; i < topics.length() && i < 3; i++) {
                JSONObject topic = topics.optJSONObject(i);
                if (topic != null) {
                    addSocialTopicRow(topicCard, topic);
                }
            }
        }

        if (posts == null || posts.length() == 0) {
            LinearLayout emptyCard = card();
            emptyCard.setGravity(Gravity.NO_GRAVITY);
            emptyCard.setPadding(dp(18), dp(18), dp(18), dp(18));
            socialFeedContent.addView(emptyCard, matchWrap());
            emptyCard.addView(text("暂无动态", 18, Color.rgb(18, 20, 26), Typeface.BOLD), matchWrap());
            TextView empty = text("后续会接入发布入口，先展示 Web 主线已有动态。", 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
            LinearLayout.LayoutParams emptyParams = matchWrap();
            emptyParams.topMargin = dp(8);
            emptyCard.addView(empty, emptyParams);
            return;
        }

        for (int i = 0; i < posts.length() && i < 20; i++) {
            JSONObject post = posts.optJSONObject(i);
            if (post != null) {
                addSocialPostCard(post);
            }
        }
    }

    private void addSocialTopicRow(LinearLayout parent, JSONObject topic) {
        String title = topic.optString("title", topic.optString("name", "专题"));
        String desc = topic.optString("description", topic.optString("subtitle", ""));
        TextView row = text(title + (desc.isEmpty() ? "" : " · " + desc), 13, Color.rgb(28, 45, 76), Typeface.BOLD);
        row.setSingleLine(true);
        LinearLayout.LayoutParams rowParams = matchWrap();
        rowParams.topMargin = dp(8);
        parent.addView(row, rowParams);
    }

    private void addSocialPostCard(JSONObject post) {
        LinearLayout postCard = card();
        postCard.setGravity(Gravity.NO_GRAVITY);
        postCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams postParams = matchWrap();
        postParams.bottomMargin = dp(12);
        socialFeedContent.addView(postCard, postParams);

        JSONObject user = post.optJSONObject("user");
        String sourceType = post.optString("source_type", "thought");
        String assetKind = post.optString("asset_kind", "text");
        String title = post.optString("title", "动态");
        String body = post.optString("text", "");
        int postId = post.optInt("id", -1);
        postCard.addView(text(sourceLabel(sourceType) + " · " + userName(user), 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());
        addSocialAssetPreview(postCard, post, assetKind);
        TextView titleView = text(title, 18, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(6);
        postCard.addView(titleView, titleParams);
        if (!body.isEmpty()) {
            TextView bodyView = text(body, 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
            bodyView.setMaxLines(4);
            LinearLayout.LayoutParams bodyParams = matchWrap();
            bodyParams.topMargin = dp(8);
            postCard.addView(bodyView, bodyParams);
        }
        TextView meta = text(post.optInt("like_count", 0) + " 赞 · " + post.optInt("comment_count", 0) + " 评论 · " + post.optString("visibility", "public"), 12, Color.rgb(104, 112, 130), Typeface.NORMAL);
        LinearLayout.LayoutParams metaParams = matchWrap();
        metaParams.topMargin = dp(10);
        postCard.addView(meta, metaParams);

        JSONArray comments = post.optJSONArray("comments");
        if (comments != null && comments.length() > 0) {
            for (int i = 0; i < comments.length() && i < 2; i++) {
                JSONObject comment = comments.optJSONObject(i);
                if (comment != null) {
                    JSONObject commentUser = comment.optJSONObject("user");
                    TextView commentView = text(userName(commentUser) + "：" + comment.optString("text", ""), 12, Color.rgb(88, 98, 118), Typeface.NORMAL);
                    commentView.setSingleLine(true);
                    LinearLayout.LayoutParams commentParams = matchWrap();
                    commentParams.topMargin = dp(6);
                    postCard.addView(commentView, commentParams);
                }
            }
        }

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams = matchWrap();
        actionParams.topMargin = dp(12);
        postCard.addView(actionRow, actionParams);

        Button likeButton = post.optBoolean("liked_by_me", false) ? primaryButton("已赞") : secondaryButton("点赞");
        likeButton.setOnClickListener(v -> toggleSocialLike(postId));
        actionRow.addView(likeButton, weightHeight(1, dp(42)));

        EditText commentInput = input("", "评论一句");
        LinearLayout.LayoutParams inputParams = weightHeight(2, dp(42));
        inputParams.leftMargin = dp(8);
        actionRow.addView(commentInput, inputParams);

        Button commentButton = primaryButton("发送");
        commentButton.setOnClickListener(v -> submitSocialComment(postId, commentInput));
        LinearLayout.LayoutParams commentParams = weightHeight(1, dp(42));
        commentParams.leftMargin = dp(8);
        actionRow.addView(commentButton, commentParams);
    }

    private void addSocialAssetPreview(LinearLayout parent, JSONObject post, String assetKind) {
        if ("text".equals(assetKind)) {
            return;
        }
        JSONObject assetPayload = post.optJSONObject("asset_payload");
        String detail = assetPayload == null ? "" : assetPayload.optString("shot_subtitle", assetPayload.optString("audio_text", assetPayload.optString("source_hint", "")));
        TextView assetView = text(assetKindLabel(assetKind) + (detail.isEmpty() ? "" : " · " + shortText(detail, 28)), 12, Color.rgb(28, 45, 76), Typeface.BOLD);
        assetView.setPadding(dp(12), dp(10), dp(12), dp(10));
        assetView.setBackground(inputBackground());
        LinearLayout.LayoutParams assetParams = matchWrap();
        assetParams.topMargin = dp(8);
        parent.addView(assetView, assetParams);

        String assetUrl = "";
        if ("image".equals(assetKind) || "story".equals(assetKind)) {
            assetUrl = absoluteUrl(post.optString("asset_url", ""));
        } else if ("voice".equals(assetKind) && assetPayload != null) {
            assetUrl = absoluteUrl(assetPayload.optString("cover_image", ""));
        }
        if (!assetUrl.isEmpty()) {
            ImageView preview = new ImageView(this);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setBackground(imagePlaceholderBackground());
            LinearLayout.LayoutParams previewParams = matchHeight(dp(150));
            previewParams.topMargin = dp(8);
            parent.addView(preview, previewParams);
            loadImageInto(preview, assetUrl);
        }
    }

    private String assetKindLabel(String assetKind) {
        if ("voice".equals(assetKind)) {
            return "VOICE · AI 声音";
        }
        if ("image".equals(assetKind)) {
            return "IMAGE · AI 图片";
        }
        if ("story".equals(assetKind)) {
            return "STORY · AI 剧情卡";
        }
        return "TEXT · 文字";
    }

    private String shortText(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private void toggleSocialLike(int postId) {
        if (postId <= 0) {
            setMessage("动态 ID 缺失，无法点赞。", false);
            return;
        }
        setMessage("正在处理点赞...", true);
        new Thread(() -> {
            try {
                httpPost(loadBaseUrl() + "/api/social/posts/" + postId + "/like", "{}", loadToken());
                runOnUiThread(() -> fetchSocialFeed(activeSocialScope));
            } catch (Exception error) {
                runOnUiThread(() -> setMessage("点赞失败：" + error.getMessage(), false));
            }
        }).start();
    }

    private void submitSocialComment(int postId, EditText commentInput) {
        if (postId <= 0) {
            setMessage("动态 ID 缺失，无法评论。", false);
            return;
        }
        String commentText = commentInput.getText().toString().trim();
        if (commentText.isEmpty()) {
            setMessage("评论不能为空。", false);
            return;
        }
        setMessage("正在发送评论...", true);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("text", commentText);
                httpPost(loadBaseUrl() + "/api/social/posts/" + postId + "/comments", payload.toString(), loadToken());
                runOnUiThread(() -> {
                    commentInput.setText("");
                    fetchSocialFeed(activeSocialScope);
                });
            } catch (Exception error) {
                runOnUiThread(() -> setMessage("评论失败：" + error.getMessage(), false));
            }
        }).start();
    }

    private void addSocialPublishComposer(LinearLayout parent) {
        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setGravity(Gravity.NO_GRAVITY);
        composer.setPadding(dp(16), dp(16), dp(16), dp(16));
        composer.setBackground(inputBackground());
        LinearLayout.LayoutParams composerParams = matchWrap();
        composerParams.topMargin = dp(14);
        parent.addView(composer, composerParams);

        composer.addView(text("发布一条感受", 14, Color.rgb(28, 45, 76), Typeface.BOLD), matchWrap());

        socialTitleInput = input("", "标题");
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(10);
        composer.addView(socialTitleInput, titleParams);

        socialTextInput = input("", "写下这一刻想分享的话");
        LinearLayout.LayoutParams textParams = matchWrap();
        textParams.topMargin = dp(8);
        composer.addView(socialTextInput, textParams);

        socialTopicInput = input("", "话题，可不填");
        LinearLayout.LayoutParams topicParams = matchWrap();
        topicParams.topMargin = dp(8);
        composer.addView(socialTopicInput, topicParams);

        LinearLayout visibilityRow = new LinearLayout(this);
        visibilityRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams visibilityParams = matchWrap();
        visibilityParams.topMargin = dp(10);
        composer.addView(visibilityRow, visibilityParams);

        Button publicButton = secondaryButton("公开");
        Button friendsButton = secondaryButton("好友");
        Button privateButton = secondaryButton("仅自己");
        publicButton.setOnClickListener(v -> setSocialPublishVisibility("public", publicButton, friendsButton, privateButton));
        friendsButton.setOnClickListener(v -> setSocialPublishVisibility("friends", publicButton, friendsButton, privateButton));
        privateButton.setOnClickListener(v -> setSocialPublishVisibility("private", publicButton, friendsButton, privateButton));
        visibilityRow.addView(publicButton, weightHeight(1, dp(40)));
        LinearLayout.LayoutParams friendsParams = weightHeight(1, dp(40));
        friendsParams.leftMargin = dp(8);
        visibilityRow.addView(friendsButton, friendsParams);
        LinearLayout.LayoutParams privateParams = weightHeight(1, dp(40));
        privateParams.leftMargin = dp(8);
        visibilityRow.addView(privateButton, privateParams);
        setSocialPublishVisibility(socialPublishVisibility, publicButton, friendsButton, privateButton);

        Button publishButton = primaryButton("发布动态");
        publishButton.setOnClickListener(v -> publishSocialPost());
        LinearLayout.LayoutParams publishParams = matchWrap();
        publishParams.topMargin = dp(10);
        composer.addView(publishButton, publishParams);
    }

    private void setSocialPublishVisibility(String visibility, Button publicButton, Button friendsButton, Button privateButton) {
        socialPublishVisibility = visibility;
        applyToggleButton(publicButton, "public".equals(visibility));
        applyToggleButton(friendsButton, "friends".equals(visibility));
        applyToggleButton(privateButton, "private".equals(visibility));
    }

    private void applyToggleButton(Button button, boolean selected) {
        button.setTextColor(selected ? Color.WHITE : Color.rgb(28, 45, 76));
        button.setBackground(selected ? buttonBackground() : secondaryButtonBackground());
    }

    private void publishSocialPost() {
        String title = socialTitleInput == null ? "" : socialTitleInput.getText().toString().trim();
        String body = socialTextInput == null ? "" : socialTextInput.getText().toString().trim();
        String topic = socialTopicInput == null ? "" : socialTopicInput.getText().toString().trim();
        if (title.isEmpty()) {
            setMessage("标题不能为空。", false);
            return;
        }
        setMessage("正在发布动态...", true);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("visibility", socialPublishVisibility);
                payload.put("source_type", "thought");
                payload.put("title", title);
                payload.put("text", body);
                payload.put("asset_kind", "text");
                payload.put("asset_url", "");
                payload.put("asset_payload", new JSONObject());
                payload.put("topic", topic);
                httpPost(loadBaseUrl() + "/api/social/posts", payload.toString(), loadToken());
                runOnUiThread(() -> {
                    socialTitleInput.setText("");
                    socialTextInput.setText("");
                    socialTopicInput.setText("");
                    fetchSocialFeed("mine");
                });
            } catch (Exception error) {
                runOnUiThread(() -> setMessage("发布失败：" + error.getMessage(), false));
            }
        }).start();
    }

    private String sourceLabel(String sourceType) {
        if ("voice".equals(sourceType)) {
            return "AI 声音";
        }
        if ("image".equals(sourceType)) {
            return "AI 图片";
        }
        if ("story".equals(sourceType)) {
            return "AI 剧情";
        }
        return "文字感受";
    }

    private void showProfileScreen(String message) {
        stopActiveVideo();
        ScrollView scrollView = newPage();
        LinearLayout root = pageRoot(scrollView);
        root.setGravity(Gravity.NO_GRAVITY);

        LinearLayout header = card();
        header.setGravity(Gravity.NO_GRAVITY);
        root.addView(header, matchWrap());

        TextView eyebrow = text("Account", 12, Color.rgb(83, 103, 160), Typeface.BOLD);
        header.addView(eyebrow, matchWrap());

        TextView title = text("我的", 30, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(4);
        header.addView(title, titleParams);

        TextView user = text(loadDisplayName(), 16, Color.rgb(88, 98, 118), Typeface.BOLD);
        LinearLayout.LayoutParams userParams = matchWrap();
        userParams.topMargin = dp(8);
        header.addView(user, userParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(16);
        header.addView(actions, actionsParams);

        Button backButton = secondaryButton("选剧");
        backButton.setOnClickListener(v -> showHomeScreen("已返回短剧首页。"));
        actions.addView(backButton, weightHeight(1, dp(46)));

        Button refreshButton = primaryButton("刷新");
        refreshButton.setOnClickListener(v -> fetchProfileSummary());
        LinearLayout.LayoutParams refreshParams = weightHeight(1, dp(46));
        refreshParams.leftMargin = dp(10);
        actions.addView(refreshButton, refreshParams);

        Button logoutButton = secondaryButton("退出");
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

        profileContent = new LinearLayout(this);
        profileContent.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentParams = matchWrap();
        contentParams.topMargin = dp(12);
        root.addView(profileContent, contentParams);

        setContentView(scrollView);
        fetchProfileSummary();
    }

    private void fetchProfileSummary() {
        if (profileContent == null) {
            return;
        }
        profileContent.removeAllViews();
        setMessage("正在同步账号成长和声音资产...", true);
        new Thread(() -> {
            try {
                JSONObject me = new JSONObject(httpGet(loadBaseUrl() + "/api/auth/me", loadToken()));
                JSONObject rewards = new JSONObject(httpGet(loadBaseUrl() + "/api/users/me/rewards", loadToken()));
                JSONObject voice = new JSONObject(httpGet(loadBaseUrl() + "/api/users/me/voice-profile", loadToken()));
                JSONObject avatars = new JSONObject(httpGet(loadBaseUrl() + "/api/avatar-pool", loadToken()));
                runOnUiThread(() -> renderProfileSummary(me.optJSONObject("user"), rewards, voice, avatars.optJSONArray("avatars")));
            } catch (Exception error) {
                runOnUiThread(() -> setMessage("账号状态加载失败：" + error.getMessage(), false));
            }
        }).start();
    }

    private void renderProfileSummary(JSONObject userProfile, JSONObject rewards, JSONObject voice, JSONArray avatars) {
        if (profileContent == null) {
            return;
        }
        avatarPool = avatars == null ? new JSONArray() : avatars;
        profileContent.removeAllViews();
        setMessage("账号状态已同步。", true);

        int points = rewards.optInt("points", 0);
        String title = rewards.optString("title", "剧情新人");
        int unlocked = rewards.optInt("collection_unlocked", 0);
        int total = rewards.optInt("collection_total", 0);
        double completion = rewards.optDouble("completion_percent", 0);
        JSONArray badges = rewards.optJSONArray("badges");

        addAvatarProfileCard(userProfile);

        LinearLayout growthCard = card();
        growthCard.setGravity(Gravity.NO_GRAVITY);
        growthCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams growthParams = matchWrap();
        growthParams.bottomMargin = dp(12);
        profileContent.addView(growthCard, growthParams);

        growthCard.addView(text("成长展馆", 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());
        TextView titleView = text(title, 24, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams titleViewParams = matchWrap();
        titleViewParams.topMargin = dp(6);
        growthCard.addView(titleView, titleViewParams);
        TextView pointsView = text(points + " 积分 · " + unlocked + "/" + total + " 徽章 · " + completion + "%", 14, Color.rgb(88, 98, 118), Typeface.NORMAL);
        LinearLayout.LayoutParams pointsParams = matchWrap();
        pointsParams.topMargin = dp(8);
        growthCard.addView(pointsView, pointsParams);

        if (badges != null && badges.length() > 0) {
            TextView latest = text("最近徽章：" + badgeLabels(badges), 13, Color.rgb(28, 45, 76), Typeface.BOLD);
            LinearLayout.LayoutParams latestParams = matchWrap();
            latestParams.topMargin = dp(10);
            growthCard.addView(latest, latestParams);
        }

        JSONObject profile = voice.optJSONObject("profile");
        JSONArray clips = voice.optJSONArray("clips");
        boolean generationReady = voice.optBoolean("generation_ready", false);
        LinearLayout voiceCard = card();
        voiceCard.setGravity(Gravity.NO_GRAVITY);
        voiceCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams voiceParams = matchWrap();
        voiceParams.bottomMargin = dp(12);
        profileContent.addView(voiceCard, voiceParams);

        voiceCard.addView(text("声音资产", 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());
        String voiceTitle = profile == null ? "未录入声音样本" : "声音样本已启用";
        TextView voiceTitleView = text(voiceTitle, 22, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams voiceTitleParams = matchWrap();
        voiceTitleParams.topMargin = dp(6);
        voiceCard.addView(voiceTitleView, voiceTitleParams);
        String filename = profile == null ? "后续可在 Web 我的页录入授权语音。" : profile.optString("prompt_audio_filename", "已保存声音样本");
        int clipCount = clips == null ? 0 : clips.length();
        TextView voiceDesc = text(filename + " · 已缓存 " + clipCount + " 条声音 · 服务" + (generationReady ? "可用" : "未启用"), 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
        voiceDesc.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams voiceDescParams = matchWrap();
        voiceDescParams.topMargin = dp(8);
        voiceCard.addView(voiceDesc, voiceDescParams);

        TextView consent = text("授权文本：" + voice.optString("consent_text", VOICE_CONSENT_TEXT), 12, Color.rgb(28, 45, 76), Typeface.BOLD);
        LinearLayout.LayoutParams consentParams = matchWrap();
        consentParams.topMargin = dp(8);
        voiceCard.addView(consent, consentParams);

        LinearLayout voiceActions = new LinearLayout(this);
        voiceActions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams = matchWrap();
        actionParams.topMargin = dp(12);
        voiceCard.addView(voiceActions, actionParams);

        Button uploadButton = primaryButton("上传样本");
        uploadButton.setOnClickListener(v -> chooseVoiceSampleFile());
        voiceActions.addView(uploadButton, weightHeight(1, dp(44)));

        voiceRecordButton = secondaryButton(voiceRecording ? "停止上传" : "麦克风直录");
        voiceRecordButton.setOnClickListener(v -> toggleVoiceRecording());
        LinearLayout.LayoutParams recordParams = weightHeight(1, dp(44));
        recordParams.leftMargin = dp(8);
        voiceActions.addView(voiceRecordButton, recordParams);

        Button previewButton = secondaryButton(generationReady ? "生成试听" : "服务未启用");
        previewButton.setEnabled(profile != null && generationReady);
        previewButton.setOnClickListener(v -> createVoicePreviewClip());
        LinearLayout.LayoutParams previewParams = matchHeight(dp(44));
        previewParams.topMargin = dp(8);
        voiceCard.addView(previewButton, previewParams);

        voiceActionStatus = text("请上传朗读授权文本的 3-8 秒音频；试听会缓存到声音资产。", 12, Color.rgb(104, 112, 130), Typeface.NORMAL);
        LinearLayout.LayoutParams voiceStatusParams = matchWrap();
        voiceStatusParams.topMargin = dp(10);
        voiceCard.addView(voiceActionStatus, voiceStatusParams);

        addVoiceClipRows(voiceCard, clips);

        LinearLayout nextCard = card();
        nextCard.setGravity(Gravity.NO_GRAVITY);
        nextCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        profileContent.addView(nextCard, matchWrap());
        nextCard.addView(text("迁移状态", 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());
        TextView next = text("Android 当前继续消费 Web 稳定接口。声音样本上传、试听生成、好友聊天、同看和逛逛已逐步接入；头像裁剪和麦克风直录后续继续迁移。", 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
        next.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams nextParams = matchWrap();
        nextParams.topMargin = dp(8);
        nextCard.addView(next, nextParams);
    }

    private void addAvatarProfileCard(JSONObject userProfile) {
        LinearLayout avatarCard = card();
        avatarCard.setGravity(Gravity.NO_GRAVITY);
        avatarCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams avatarParams = matchWrap();
        avatarParams.bottomMargin = dp(12);
        profileContent.addView(avatarCard, avatarParams);

        avatarCard.addView(text("头像管理", 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());
        String displayName = userProfile == null ? loadDisplayName() : userProfile.optString("display_name", loadDisplayName());
        TextView name = text(displayName, 22, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = matchWrap();
        nameParams.topMargin = dp(6);
        avatarCard.addView(name, nameParams);

        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams nameRowParams = matchWrap();
        nameRowParams.topMargin = dp(10);
        avatarCard.addView(nameRow, nameRowParams);

        profileDisplayNameInput = input(displayName, "昵称");
        nameRow.addView(profileDisplayNameInput, weightHeight(2, dp(44)));

        Button saveNameButton = primaryButton("保存昵称");
        saveNameButton.setOnClickListener(v -> updateProfileDisplayName());
        LinearLayout.LayoutParams saveNameParams = weightHeight(1, dp(44));
        saveNameParams.leftMargin = dp(8);
        nameRow.addView(saveNameButton, saveNameParams);

        String avatarUrl = userProfile == null ? "" : userProfile.optString("avatar_url", "");
        if (avatarUrl.startsWith("/media/")) {
            ImageView avatarImage = new ImageView(this);
            avatarImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatarImage.setBackground(imagePlaceholderBackground());
            LinearLayout.LayoutParams imageParams = matchHeight(dp(132));
            imageParams.topMargin = dp(12);
            avatarCard.addView(avatarImage, imageParams);
            loadImageInto(avatarImage, absoluteUrl(avatarUrl));
        } else {
            TextView avatarHint = text("当前使用系统预设头像：" + (avatarUrl.isEmpty() ? "默认" : avatarUrl), 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
            LinearLayout.LayoutParams hintParams = matchWrap();
            hintParams.topMargin = dp(10);
            avatarCard.addView(avatarHint, hintParams);
        }

        Button chooseButton = primaryButton("选择图片并裁切上传");
        chooseButton.setOnClickListener(v -> chooseAvatarImage());
        LinearLayout.LayoutParams chooseParams = matchHeight(dp(44));
        chooseParams.topMargin = dp(12);
        avatarCard.addView(chooseButton, chooseParams);

        addAvatarPresetChoices(avatarCard);
        addAvatarPoolChoices(avatarCard);

        avatarActionStatus = text("图片会自动中心裁切为正方形头像，上传后同步到好友、同看和逛逛。", 12, Color.rgb(104, 112, 130), Typeface.NORMAL);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(10);
        avatarCard.addView(avatarActionStatus, statusParams);
    }

    private void addAvatarPresetChoices(LinearLayout parent) {
        TextView title = text("预设风格", 12, Color.rgb(83, 103, 160), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(14);
        parent.addView(title, titleParams);

        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams firstParams = matchWrap();
        firstParams.topMargin = dp(8);
        parent.addView(firstRow, firstParams);
        addPresetButton(firstRow, "返乡", "preset:road", true);
        addPresetButton(firstRow, "寻宝", "preset:treasure", false);

        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams secondParams = matchWrap();
        secondParams.topMargin = dp(8);
        parent.addView(secondRow, secondParams);
        addPresetButton(secondRow, "冬至", "preset:winter", true);
        addPresetButton(secondRow, "高光", "preset:stage", false);
    }

    private void addPresetButton(LinearLayout row, String label, String avatarUrl, boolean first) {
        Button button = secondaryButton(label);
        button.setOnClickListener(v -> updateProfileAvatar(avatarUrl));
        LinearLayout.LayoutParams params = weightHeight(1, dp(38));
        if (!first) {
            params.leftMargin = dp(8);
        }
        row.addView(button, params);
    }

    private void addAvatarPoolChoices(LinearLayout parent) {
        if (avatarPool.length() == 0) {
            return;
        }
        TextView title = text("头像池推荐", 12, Color.rgb(83, 103, 160), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(14);
        parent.addView(title, titleParams);

        int visible = Math.min(6, avatarPool.length());
        int start = avatarPool.length() == 0 ? 0 : (avatarPoolPage * visible) % avatarPool.length();
        for (int rowIndex = 0; rowIndex < 3 && rowIndex * 2 < visible; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = matchWrap();
            rowParams.topMargin = dp(8);
            parent.addView(row, rowParams);
            for (int column = 0; column < 2; column++) {
                int offset = rowIndex * 2 + column;
                if (offset >= visible) {
                    break;
                }
                JSONObject avatar = avatarPool.optJSONObject((start + offset) % avatarPool.length());
                if (avatar != null) {
                    addAvatarPoolButton(row, avatar, column == 0);
                }
            }
        }

        Button nextButton = secondaryButton("换一批推荐头像");
        nextButton.setOnClickListener(v -> {
            avatarPoolPage += 1;
            fetchProfileSummary();
        });
        LinearLayout.LayoutParams nextParams = matchHeight(dp(38));
        nextParams.topMargin = dp(8);
        parent.addView(nextButton, nextParams);
    }

    private void addAvatarPoolButton(LinearLayout row, JSONObject avatar, boolean first) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(8), dp(8), dp(8), dp(8));
        item.setBackground(inputBackground());
        LinearLayout.LayoutParams itemParams = weightHeight(1, dp(112));
        if (!first) {
            itemParams.leftMargin = dp(8);
        }
        row.addView(item, itemParams);

        String avatarUrl = avatar.optString("url", "");
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(imagePlaceholderBackground());
        item.addView(image, matchHeight(dp(62)));
        loadImageInto(image, absoluteUrl(avatarUrl));

        Button useButton = secondaryButton("使用");
        useButton.setTextSize(12);
        useButton.setOnClickListener(v -> updateProfileAvatar(avatarUrl));
        LinearLayout.LayoutParams useParams = matchHeight(dp(34));
        useParams.topMargin = dp(6);
        item.addView(useButton, useParams);
    }

    private void updateProfileAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            return;
        }
        if (avatarActionStatus != null) {
            avatarActionStatus.setText("正在切换头像...");
        }
        setMessage("正在切换头像...", true);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("avatar_url", avatarUrl);
                httpPatch(loadBaseUrl() + "/api/users/me/profile", payload.toString(), loadToken());
                runOnUiThread(() -> {
                    setMessage("头像已切换。", true);
                    fetchProfileSummary();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setMessage("头像切换失败：" + error.getMessage(), false);
                    if (avatarActionStatus != null) {
                        avatarActionStatus.setText("头像切换失败：" + error.getMessage());
                    }
                });
            }
        }).start();
    }

    private void updateProfileDisplayName() {
        String displayName = profileDisplayNameInput == null ? "" : profileDisplayNameInput.getText().toString().trim();
        if (displayName.isEmpty()) {
            setMessage("昵称不能为空。", false);
            if (avatarActionStatus != null) {
                avatarActionStatus.setText("昵称不能为空。");
            }
            return;
        }
        if (avatarActionStatus != null) {
            avatarActionStatus.setText("正在保存昵称...");
        }
        setMessage("正在保存昵称...", true);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("display_name", displayName);
                JSONObject response = new JSONObject(httpPatch(loadBaseUrl() + "/api/users/me/profile", payload.toString(), loadToken()));
                JSONObject user = response.optJSONObject("user");
                String savedName = user == null ? displayName : user.optString("display_name", displayName);
                runOnUiThread(() -> {
                    saveDisplayName(savedName);
                    setMessage("昵称已保存。", true);
                    fetchProfileSummary();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setMessage("昵称保存失败：" + error.getMessage(), false);
                    if (avatarActionStatus != null) {
                        avatarActionStatus.setText("昵称保存失败：" + error.getMessage());
                    }
                });
            }
        }).start();
    }

    private void chooseAvatarImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_AVATAR_IMAGE);
    }

    private void uploadAvatarImage(Uri uri) {
        if (uri == null) {
            return;
        }
        if (avatarActionStatus != null) {
            avatarActionStatus.setText("正在处理头像...");
        }
        setMessage("正在上传头像...", true);
        new Thread(() -> {
            try {
                byte[] avatarBytes = cropAvatarBytes(uri);
                httpMultipartAvatar(loadBaseUrl() + "/api/users/me/avatar", loadToken(), avatarBytes);
                runOnUiThread(() -> {
                    setMessage("头像已更新。", true);
                    fetchProfileSummary();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setMessage("头像上传失败：" + error.getMessage(), false);
                    if (avatarActionStatus != null) {
                        avatarActionStatus.setText("头像上传失败：" + error.getMessage());
                    }
                });
            }
        }).start();
    }

    private byte[] cropAvatarBytes(Uri uri) throws Exception {
        InputStream stream = getContentResolver().openInputStream(uri);
        if (stream == null) {
            throw new IllegalStateException("无法读取头像图片");
        }
        Bitmap source = BitmapFactory.decodeStream(stream);
        stream.close();
        if (source == null) {
            throw new IllegalStateException("图片格式无法识别");
        }
        int size = Math.min(source.getWidth(), source.getHeight());
        int left = Math.max(0, (source.getWidth() - size) / 2);
        int top = Math.max(0, (source.getHeight() - size) / 2);
        Bitmap square = Bitmap.createBitmap(source, left, top, size, size);
        Bitmap scaled = Bitmap.createScaledBitmap(square, 512, 512, true);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 90, output);
        if (square != source) {
            square.recycle();
        }
        scaled.recycle();
        source.recycle();
        return output.toByteArray();
    }

    private void addVoiceClipRows(LinearLayout parent, JSONArray clips) {
        if (clips == null || clips.length() == 0) {
            return;
        }
        TextView title = text("最近缓存声音", 12, Color.rgb(83, 103, 160), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(14);
        parent.addView(title, titleParams);

        for (int i = 0; i < clips.length() && i < 3; i++) {
            JSONObject clip = clips.optJSONObject(i);
            if (clip == null) {
                continue;
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(inputBackground());
            LinearLayout.LayoutParams rowParams = matchWrap();
            rowParams.topMargin = dp(8);
            parent.addView(row, rowParams);

            TextView label = text(shortText(clip.optString("scene_key", "voice"), 18) + " · " + shortText(clip.optString("text", ""), 28), 12, Color.rgb(28, 45, 76), Typeface.BOLD);
            label.setSingleLine(true);
            row.addView(label, weightHeight(2, dp(38)));

            Button playButton = secondaryButton("播放");
            String audioUrl = absoluteUrl(clip.optString("audio_url", ""));
            playButton.setOnClickListener(v -> playProfileVoiceAudio(audioUrl));
            LinearLayout.LayoutParams playParams = weightHeight(1, dp(38));
            playParams.leftMargin = dp(8);
            row.addView(playButton, playParams);
        }
    }

    private void chooseVoiceSampleFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        startActivityForResult(intent, REQUEST_VOICE_SAMPLE);
    }

    private void toggleVoiceRecording() {
        if (voiceRecording) {
            stopVoiceRecordingAndUpload();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            if (voiceActionStatus != null) {
                voiceActionStatus.setText("正在请求麦克风权限...");
            }
            return;
        }
        startVoiceRecording();
    }

    private void startVoiceRecording() {
        try {
            stopVoiceRecorderOnly();
            voiceRecordFile = new File(getCacheDir(), "banju_voice_record_" + System.currentTimeMillis() + ".m4a");
            voiceRecorder = new MediaRecorder();
            voiceRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            voiceRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            voiceRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            voiceRecorder.setAudioEncodingBitRate(128000);
            voiceRecorder.setAudioSamplingRate(44100);
            voiceRecorder.setOutputFile(voiceRecordFile.getAbsolutePath());
            voiceRecorder.prepare();
            voiceRecorder.start();
            voiceRecording = true;
            updateVoiceRecordButton();
            setMessage("正在录音，请朗读授权文本。", true);
            if (voiceActionStatus != null) {
                voiceActionStatus.setText("正在录音：请朗读“" + VOICE_CONSENT_TEXT + "”，完成后点停止上传。");
            }
        } catch (Exception error) {
            voiceRecording = false;
            stopVoiceRecorderOnly();
            updateVoiceRecordButton();
            setMessage("录音启动失败：" + error.getMessage(), false);
            if (voiceActionStatus != null) {
                voiceActionStatus.setText("录音启动失败：" + error.getMessage());
            }
        }
    }

    private void stopVoiceRecordingAndUpload() {
        File recorded = voiceRecordFile;
        try {
            if (voiceRecorder != null) {
                voiceRecorder.stop();
            }
        } catch (RuntimeException error) {
            if (recorded != null) {
                recorded.delete();
            }
            setMessage("录音太短或失败，请重新录制。", false);
            if (voiceActionStatus != null) {
                voiceActionStatus.setText("录音太短或失败，请重新录制。");
            }
            return;
        } finally {
            stopVoiceRecorderOnly();
            voiceRecording = false;
            updateVoiceRecordButton();
        }
        if (recorded == null || !recorded.exists() || recorded.length() == 0) {
            setMessage("录音文件为空，请重新录制。", false);
            return;
        }
        uploadVoiceSampleFile(recorded, "voice_record.m4a", "audio/mp4", true);
    }

    private void updateVoiceRecordButton() {
        if (voiceRecordButton != null) {
            voiceRecordButton.setText(voiceRecording ? "停止上传" : "麦克风直录");
        }
    }

    private void stopVoiceRecorderOnly() {
        if (voiceRecorder != null) {
            try {
                voiceRecorder.release();
            } catch (Exception ignored) {
                // 忽略释放失败，下一次录音会重新创建 recorder。
            }
            voiceRecorder = null;
        }
    }

    private void uploadVoiceSample(Uri uri) {
        if (uri == null) {
            return;
        }
        if (voiceActionStatus != null) {
            voiceActionStatus.setText("正在上传声音样本...");
        }
        setMessage("正在上传声音样本...", true);
        new Thread(() -> {
            try {
                byte[] fileData = readUriBytes(uri);
                String fileName = displayNameForUri(uri);
                String contentType = getContentResolver().getType(uri);
                if (contentType == null || contentType.isEmpty()) {
                    contentType = "audio/wav";
                }
                uploadVoiceSampleBytes(fileData, fileName, contentType, false);
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setMessage("声音样本上传失败：" + error.getMessage(), false);
                    if (voiceActionStatus != null) {
                        voiceActionStatus.setText("上传失败：" + error.getMessage());
                    }
                });
            }
        }).start();
    }

    private void uploadVoiceSampleFile(File file, String fileName, String contentType, boolean deleteAfterUpload) {
        if (voiceActionStatus != null) {
            voiceActionStatus.setText("正在上传录音样本...");
        }
        setMessage("正在上传录音样本...", true);
        new Thread(() -> {
            try {
                byte[] data = readFileBytes(file);
                uploadVoiceSampleBytes(data, fileName, contentType, deleteAfterUpload);
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setMessage("录音样本上传失败：" + error.getMessage(), false);
                    if (voiceActionStatus != null) {
                        voiceActionStatus.setText("录音上传失败：" + error.getMessage());
                    }
                });
            } finally {
                if (deleteAfterUpload && file != null) {
                    file.delete();
                }
            }
        }).start();
    }

    private void uploadVoiceSampleBytes(byte[] fileData, String fileName, String contentType, boolean recorded) throws Exception {
        httpMultipartVoiceProfile(loadBaseUrl() + "/api/users/me/voice-profile", loadToken(), fileName, contentType, fileData);
        runOnUiThread(() -> {
            setMessage(recorded ? "录音样本已上传。" : "声音样本已上传。", true);
            fetchProfileSummary();
        });
    }

    private void createVoicePreviewClip() {
        if (voiceActionStatus != null) {
            voiceActionStatus.setText("正在生成试听...");
        }
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("text", VOICE_PREVIEW_TEXT);
                payload.put("scene_key", "native_profile_preview");
                String body = httpPost(loadBaseUrl() + "/api/users/me/voice-clips", payload.toString(), loadToken());
                JSONObject result = new JSONObject(body);
                String audioUrl = absoluteUrl(result.optString("audio_url", ""));
                runOnUiThread(() -> {
                    playProfileVoiceAudio(audioUrl);
                    fetchProfileSummary();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setMessage("试听生成失败：" + error.getMessage(), false);
                    if (voiceActionStatus != null) {
                        voiceActionStatus.setText("试听生成失败：" + error.getMessage());
                    }
                });
            }
        }).start();
    }

    private void playProfileVoiceAudio(String audioUrl) {
        if (voiceActionStatus == null) {
            return;
        }
        playRemixAudio(audioUrl, voiceActionStatus);
    }

    private String badgeLabels(JSONArray badges) {
        StringBuilder builder = new StringBuilder();
        int count = Math.min(3, badges.length());
        for (int i = 0; i < count; i++) {
            JSONObject badge = badges.optJSONObject(i);
            if (badge == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("、");
            }
            builder.append(badge.optString("title", "徽章"));
        }
        return builder.length() == 0 ? "暂无" : builder.toString();
    }

    private void showChatScreen(String message) {
        stopActiveVideo();
        ScrollView scrollView = newPage();
        LinearLayout root = pageRoot(scrollView);
        root.setGravity(Gravity.NO_GRAVITY);

        LinearLayout header = card();
        header.setGravity(Gravity.NO_GRAVITY);
        root.addView(header, matchWrap());

        header.addView(text("Social", 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());
        TextView title = text("聊聊", 30, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(4);
        header.addView(title, titleParams);

        TextView subtitle = text("好友、申请和会话概览", 14, Color.rgb(88, 98, 118), Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(8);
        header.addView(subtitle, subtitleParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(16);
        header.addView(actions, actionsParams);

        Button backButton = secondaryButton("选剧");
        backButton.setOnClickListener(v -> showHomeScreen("已返回短剧首页。"));
        actions.addView(backButton, weightHeight(1, dp(46)));

        Button refreshButton = primaryButton("刷新");
        refreshButton.setOnClickListener(v -> fetchChatSummary());
        LinearLayout.LayoutParams refreshParams = weightHeight(1, dp(46));
        refreshParams.leftMargin = dp(10);
        actions.addView(refreshButton, refreshParams);

        messageText = text(message, 13, Color.rgb(104, 112, 130), Typeface.NORMAL);
        LinearLayout.LayoutParams messageParams = matchWrap();
        messageParams.topMargin = dp(18);
        root.addView(messageText, messageParams);

        chatContent = new LinearLayout(this);
        chatContent.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentParams = matchWrap();
        contentParams.topMargin = dp(12);
        root.addView(chatContent, contentParams);

        setContentView(scrollView);
        fetchChatSummary();
    }

    private void fetchChatSummary() {
        if (chatContent == null) {
            return;
        }
        chatContent.removeAllViews();
        setMessage("正在同步好友和会话...", true);
        new Thread(() -> {
            try {
                JSONObject friends = new JSONObject(httpGet(loadBaseUrl() + "/api/users/me/friends", loadToken()));
                JSONObject conversations = new JSONObject(httpGet(loadBaseUrl() + "/api/chat/conversations", loadToken()));
                JSONObject invitations = new JSONObject(httpGet(loadBaseUrl() + "/api/watch-rooms/invitations", loadToken()));
                runOnUiThread(() -> renderChatSummary(friends, conversations, invitations));
            } catch (Exception error) {
                runOnUiThread(() -> setMessage("聊聊加载失败：" + error.getMessage(), false));
            }
        }).start();
    }

    private void renderChatSummary(JSONObject friends, JSONObject conversations, JSONObject invitations) {
        if (chatContent == null) {
            return;
        }
        chatContent.removeAllViews();
        setMessage("聊聊已同步。", true);

        JSONArray friendRows = friends.optJSONArray("friends");
        JSONArray incoming = friends.optJSONArray("incoming_requests");
        JSONArray outgoing = friends.optJSONArray("outgoing_requests");
        JSONArray candidates = friends.optJSONArray("candidates");
        JSONArray conversationRows = conversations.optJSONArray("conversations");
        JSONArray receivedInvitations = invitations.optJSONArray("received");
        JSONArray sentInvitations = invitations.optJSONArray("sent");
        int unread = conversations.optInt("unread_count", 0);

        LinearLayout summaryCard = card();
        summaryCard.setGravity(Gravity.NO_GRAVITY);
        summaryCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams summaryParams = matchWrap();
        summaryParams.bottomMargin = dp(12);
        chatContent.addView(summaryCard, summaryParams);

        summaryCard.addView(text("社交状态", 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());
        TextView countView = text(arrayLength(friendRows) + " 好友 · " + unread + " 未读", 24, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams countParams = matchWrap();
        countParams.topMargin = dp(6);
        summaryCard.addView(countView, countParams);
        TextView requestView = text(arrayLength(incoming) + " 个待处理申请 · " + arrayLength(receivedInvitations) + " 个同看邀请 · " + arrayLength(candidates) + " 个可认识的人", 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
        LinearLayout.LayoutParams requestParams = matchWrap();
        requestParams.topMargin = dp(8);
        summaryCard.addView(requestView, requestParams);

        LinearLayout conversationCard = card();
        conversationCard.setGravity(Gravity.NO_GRAVITY);
        conversationCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams conversationParams = matchWrap();
        conversationParams.bottomMargin = dp(12);
        chatContent.addView(conversationCard, conversationParams);

        conversationCard.addView(text("最近会话", 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());
        if (conversationRows == null || conversationRows.length() == 0) {
            TextView empty = text("暂无会话。后续会接入聊天详情和同看邀请发送。", 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
            LinearLayout.LayoutParams emptyParams = matchWrap();
            emptyParams.topMargin = dp(8);
            conversationCard.addView(empty, emptyParams);
        } else {
            for (int i = 0; i < conversationRows.length() && i < 5; i++) {
                JSONObject conversation = conversationRows.optJSONObject(i);
                if (conversation != null) {
                    addConversationRow(conversationCard, conversation);
                }
            }
        }

        LinearLayout watchInviteCard = card();
        watchInviteCard.setGravity(Gravity.NO_GRAVITY);
        watchInviteCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams watchInviteParams = matchWrap();
        watchInviteParams.bottomMargin = dp(12);
        chatContent.addView(watchInviteCard, watchInviteParams);
        watchInviteCard.addView(text("同看邀请", 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());
        if (receivedInvitations == null || receivedInvitations.length() == 0) {
            TextView empty = text("暂无待处理同看邀请。", 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
            LinearLayout.LayoutParams emptyParams = matchWrap();
            emptyParams.topMargin = dp(8);
            watchInviteCard.addView(empty, emptyParams);
        } else {
            for (int i = 0; i < receivedInvitations.length() && i < 3; i++) {
                JSONObject invitation = receivedInvitations.optJSONObject(i);
                if (invitation != null) {
                    addWatchInvitationRow(watchInviteCard, invitation, true);
                }
            }
        }
        if (sentInvitations != null && sentInvitations.length() > 0) {
            TextView sentTitle = text("已发出", 12, Color.rgb(83, 103, 160), Typeface.BOLD);
            LinearLayout.LayoutParams sentTitleParams = matchWrap();
            sentTitleParams.topMargin = dp(16);
            watchInviteCard.addView(sentTitle, sentTitleParams);
            for (int i = 0; i < sentInvitations.length() && i < 3; i++) {
                JSONObject invitation = sentInvitations.optJSONObject(i);
                if (invitation != null) {
                    addWatchInvitationRow(watchInviteCard, invitation, false);
                }
            }
        }

        LinearLayout requestCard = card();
        requestCard.setGravity(Gravity.NO_GRAVITY);
        requestCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        chatContent.addView(requestCard, matchWrap());
        requestCard.addView(text("好友申请", 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());
        if (incoming == null || incoming.length() == 0) {
            TextView empty = text("暂无新的好友申请。", 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
            LinearLayout.LayoutParams emptyParams = matchWrap();
            emptyParams.topMargin = dp(8);
            requestCard.addView(empty, emptyParams);
        } else {
            for (int i = 0; i < incoming.length() && i < 3; i++) {
                JSONObject request = incoming.optJSONObject(i);
                if (request != null) {
                    addFriendRequestRow(requestCard, request, true);
                }
            }
        }
        if (outgoing != null && outgoing.length() > 0) {
            TextView outgoingTitle = text("已发出", 12, Color.rgb(83, 103, 160), Typeface.BOLD);
            LinearLayout.LayoutParams outgoingTitleParams = matchWrap();
            outgoingTitleParams.topMargin = dp(16);
            requestCard.addView(outgoingTitle, outgoingTitleParams);
            for (int i = 0; i < outgoing.length() && i < 3; i++) {
                JSONObject request = outgoing.optJSONObject(i);
                if (request != null) {
                    addFriendRequestRow(requestCard, request, false);
                }
            }
        }

        LinearLayout candidateCard = card();
        candidateCard.setGravity(Gravity.NO_GRAVITY);
        candidateCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams candidateParams = matchWrap();
        candidateParams.topMargin = dp(12);
        chatContent.addView(candidateCard, candidateParams);
        candidateCard.addView(text("可认识的人", 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());
        if (candidates == null || candidates.length() == 0) {
            TextView empty = text("暂无新的推荐好友。", 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
            LinearLayout.LayoutParams emptyParams = matchWrap();
            emptyParams.topMargin = dp(8);
            candidateCard.addView(empty, emptyParams);
        } else {
            for (int i = 0; i < candidates.length() && i < 4; i++) {
                JSONObject candidate = candidates.optJSONObject(i);
                if (candidate != null) {
                    addCandidateRow(candidateCard, candidate);
                }
            }
        }
    }

    private void addConversationRow(LinearLayout parent, JSONObject conversation) {
        JSONObject user = conversation.optJSONObject("user");
        JSONObject lastMessage = conversation.optJSONObject("last_message");
        int unread = conversation.optInt("unread_count", 0);
        String message = lastMessage == null ? "还没有聊天记录" : lastMessage.optString("text", "新消息");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(inputBackground());
        if (user != null) {
            row.setOnClickListener(v -> showChatDetail(user));
        }
        LinearLayout.LayoutParams rowParams = matchWrap();
        rowParams.topMargin = dp(10);
        parent.addView(row, rowParams);

        TextView title = text(userName(user) + (unread > 0 ? " · " + unread + " 未读" : ""), 15, Color.rgb(18, 20, 26), Typeface.BOLD);
        row.addView(title, matchWrap());
        TextView desc = text(message, 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
        desc.setSingleLine(true);
        LinearLayout.LayoutParams descParams = matchWrap();
        descParams.topMargin = dp(3);
        row.addView(desc, descParams);
    }

    private void addWatchInvitationRow(LinearLayout parent, JSONObject invitation, boolean incoming) {
        JSONObject user = incoming ? invitation.optJSONObject("from_user") : invitation.optJSONObject("to_user");
        JSONObject room = invitation.optJSONObject("room");
        int invitationId = invitation.optInt("id", 0);
        String roomCode = room == null ? "" : room.optString("code", "");
        int episodeId = room == null ? 0 : room.optInt("episode_id", 0);
        String status = invitation.optString("status", "pending");

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(inputBackground());
        LinearLayout.LayoutParams rowParams = matchWrap();
        rowParams.topMargin = dp(10);
        parent.addView(row, rowParams);

        String title = incoming ? userName(user) + " 邀请你同看" : "邀请 " + userName(user);
        row.addView(text(title, 14, Color.rgb(18, 20, 26), Typeface.BOLD), matchWrap());
        String detail = "房间 " + (roomCode.isEmpty() ? "未知" : roomCode) + " · 剧集 " + episodeId + " · " + status;
        TextView desc = text(detail, 12, Color.rgb(88, 98, 118), Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = matchWrap();
        descParams.topMargin = dp(3);
        row.addView(desc, descParams);

        if (!incoming) {
            Button openButton = secondaryButton("查看房间");
            openButton.setOnClickListener(v -> showWatchRoomScreen(roomCode, "正在打开同看房间。"));
            LinearLayout.LayoutParams openParams = matchHeight(dp(42));
            openParams.topMargin = dp(10);
            row.addView(openButton, openParams);
            return;
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(10);
        row.addView(actions, actionsParams);

        Button acceptButton = primaryButton("接受");
        acceptButton.setOnClickListener(v -> postWatchInvitationAction(
                "/api/watch-rooms/invitations/" + invitationId + "/accept",
                "已接受同看邀请。",
                true
        ));
        actions.addView(acceptButton, weightHeight(1, dp(42)));

        Button declineButton = secondaryButton("拒绝");
        declineButton.setOnClickListener(v -> postWatchInvitationAction(
                "/api/watch-rooms/invitations/" + invitationId + "/decline",
                "已拒绝同看邀请。",
                false
        ));
        LinearLayout.LayoutParams declineParams = weightHeight(1, dp(42));
        declineParams.leftMargin = dp(10);
        actions.addView(declineButton, declineParams);
    }

    private void postWatchInvitationAction(String path, String successMessage, boolean openRoomOnSuccess) {
        setMessage("正在处理同看邀请...", true);
        new Thread(() -> {
            try {
                JSONObject response = new JSONObject(httpPost(loadBaseUrl() + path, "{}", loadToken()));
                JSONObject room = response.optJSONObject("room");
                if (room == null) {
                    JSONObject invitation = response.optJSONObject("invitation");
                    room = invitation == null ? null : invitation.optJSONObject("room");
                }
                String roomCode = room == null ? "" : room.optString("code", "");
                if (room != null && room.optInt("episode_id", 0) > 0) {
                    activeEpisodeId = room.optInt("episode_id", activeEpisodeId);
                }
                runOnUiThread(() -> {
                    if (openRoomOnSuccess && !roomCode.isEmpty()) {
                        showWatchRoomScreen(roomCode, successMessage);
                    } else {
                        setMessage(successMessage, true);
                        fetchChatSummary();
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> setMessage("同看邀请处理失败：" + error.getMessage(), false));
            }
        }).start();
    }

    private void showWatchRoomScreen(String roomCode, String message) {
        if (roomCode == null || roomCode.trim().isEmpty()) {
            setMessage("同看房间码缺失。", false);
            return;
        }
        stopActiveVideo();
        activeRoomCode = roomCode.trim().toUpperCase();

        ScrollView scrollView = newPage();
        LinearLayout root = pageRoot(scrollView);
        root.setGravity(Gravity.NO_GRAVITY);

        LinearLayout header = card();
        header.setGravity(Gravity.NO_GRAVITY);
        root.addView(header, matchWrap());

        header.addView(text("Co-watch", 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());
        TextView title = text("同看房间 " + activeRoomCode, 26, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(4);
        header.addView(title, titleParams);

        TextView subtitle = text("成员、进度和房间动态", 14, Color.rgb(88, 98, 118), Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(8);
        header.addView(subtitle, subtitleParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(16);
        header.addView(actions, actionsParams);

        Button backButton = secondaryButton("聊聊");
        backButton.setOnClickListener(v -> showChatScreen("已返回聊聊。"));
        actions.addView(backButton, weightHeight(1, dp(46)));

        Button refreshButton = primaryButton("刷新");
        refreshButton.setOnClickListener(v -> fetchWatchRoom(activeRoomCode));
        LinearLayout.LayoutParams refreshParams = weightHeight(1, dp(46));
        refreshParams.leftMargin = dp(10);
        actions.addView(refreshButton, refreshParams);

        messageText = text(message, 13, Color.rgb(104, 112, 130), Typeface.NORMAL);
        LinearLayout.LayoutParams messageParams = matchWrap();
        messageParams.topMargin = dp(18);
        root.addView(messageText, messageParams);

        watchRoomContent = new LinearLayout(this);
        watchRoomContent.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentParams = matchWrap();
        contentParams.topMargin = dp(12);
        root.addView(watchRoomContent, contentParams);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams composerParams = matchWrap();
        composerParams.topMargin = dp(14);
        root.addView(composer, composerParams);

        watchRoomEventInput = input("", "发一条房间动态");
        composer.addView(watchRoomEventInput, weightHeight(1, dp(48)));

        Button sendButton = primaryButton("发送");
        sendButton.setOnClickListener(v -> sendWatchRoomEvent());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(86), dp(48));
        sendParams.leftMargin = dp(10);
        composer.addView(sendButton, sendParams);

        setContentView(scrollView);
        fetchWatchRoom(activeRoomCode);
    }

    private void fetchWatchRoom(String roomCode) {
        if (watchRoomContent == null || roomCode == null || roomCode.trim().isEmpty()) {
            return;
        }
        watchRoomContent.removeAllViews();
        setMessage("正在同步同看房间...", true);
        new Thread(() -> {
            try {
                String safeCode = roomCode.trim().toUpperCase();
                JSONObject room = new JSONObject(httpGet(loadBaseUrl() + "/api/watch-rooms/" + safeCode, loadToken()));
                JSONArray events = new JSONArray(httpGet(loadBaseUrl() + "/api/watch-rooms/" + safeCode + "/events", loadToken()));
                runOnUiThread(() -> renderWatchRoom(room, events));
            } catch (Exception error) {
                runOnUiThread(() -> setMessage("同看房间加载失败：" + error.getMessage(), false));
            }
        }).start();
    }

    private void renderWatchRoom(JSONObject room, JSONArray events) {
        if (watchRoomContent == null) {
            return;
        }
        watchRoomContent.removeAllViews();
        String roomCode = room.optString("code", activeRoomCode);
        int episodeId = room.optInt("episode_id", 0);
        if (!roomCode.isEmpty()) {
            activeRoomCode = roomCode;
        }
        if (episodeId > 0) {
            activeEpisodeId = episodeId;
        }
        setMessage("房间已同步。", true);

        LinearLayout stateCard = card();
        stateCard.setGravity(Gravity.NO_GRAVITY);
        stateCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams stateParams = matchWrap();
        stateParams.bottomMargin = dp(12);
        watchRoomContent.addView(stateCard, stateParams);

        JSONObject host = room.optJSONObject("host");
        JSONObject guest = room.optJSONObject("guest");
        stateCard.addView(text("房间状态", 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());
        TextView title = text("成员 " + room.optInt("member_count", 1) + " 人 · " + room.optString("playback_state", "paused"), 22, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(6);
        stateCard.addView(title, titleParams);
        TextView members = text("房主：" + userName(host) + " · 成员：" + (guest == null ? "等待加入" : userName(guest)), 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
        LinearLayout.LayoutParams membersParams = matchWrap();
        membersParams.topMargin = dp(8);
        stateCard.addView(members, membersParams);
        TextView progress = text("剧集 " + episodeId + " · 进度 " + (int) Math.round(room.optDouble("progress_sec", 0)) + " 秒", 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
        LinearLayout.LayoutParams progressParams = matchWrap();
        progressParams.topMargin = dp(4);
        stateCard.addView(progress, progressParams);

        if (episodeId > 0) {
            Button playButton = primaryButton("进入本集播放");
            playButton.setOnClickListener(v -> showNativePlayer("同看房间 " + roomCode, episodeId, roomCode));
            LinearLayout.LayoutParams playParams = matchHeight(dp(46));
            playParams.topMargin = dp(14);
            stateCard.addView(playButton, playParams);
        }

        LinearLayout eventsCard = card();
        eventsCard.setGravity(Gravity.NO_GRAVITY);
        eventsCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        watchRoomContent.addView(eventsCard, matchWrap());
        eventsCard.addView(text("房间动态", 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());
        watchRoomEventsContent = eventsCard;
        if (events == null || events.length() == 0) {
            TextView empty = text("暂无动态，可以先发一句。", 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
            LinearLayout.LayoutParams emptyParams = matchWrap();
            emptyParams.topMargin = dp(8);
            eventsCard.addView(empty, emptyParams);
            return;
        }
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event != null) {
                addWatchRoomEventRow(eventsCard, event);
            }
        }
    }

    private void addWatchRoomEventRow(LinearLayout parent, JSONObject event) {
        JSONObject user = event.optJSONObject("user");
        String content = roomEventText(event);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(11), dp(12), dp(11));
        row.setBackground(inputBackground());
        LinearLayout.LayoutParams rowParams = matchWrap();
        rowParams.topMargin = dp(10);
        parent.addView(row, rowParams);

        addRoomEventAvatar(row, user);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        copyParams.leftMargin = dp(10);
        row.addView(copy, copyParams);

        TextView name = text(userName(user) + " · " + roomEventTypeLabel(event), 14, Color.rgb(18, 20, 26), Typeface.BOLD);
        name.setSingleLine(true);
        copy.addView(name, matchWrap());

        TextView subtitle = text(userIdentityTitle(user, "房间成员"), 11, Color.rgb(83, 103, 160), Typeface.BOLD);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(2);
        copy.addView(subtitle, subtitleParams);

        TextView body = text(shortText(content, 42), 12, Color.rgb(88, 98, 118), Typeface.NORMAL);
        body.setSingleLine(true);
        LinearLayout.LayoutParams bodyParams = matchWrap();
        bodyParams.topMargin = dp(3);
        copy.addView(body, bodyParams);
    }

    private void addRoomEventAvatar(LinearLayout parent, JSONObject user) {
        FrameLayout avatar = new FrameLayout(this);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(230, 255, 255, 255), Color.argb(210, 232, 241, 255)}
        );
        background.setShape(GradientDrawable.OVAL);
        background.setStroke(dp(1), Color.argb(80, 20, 26, 38));
        avatar.setBackground(background);
        parent.addView(avatar, new LinearLayout.LayoutParams(dp(42), dp(42)));

        String avatarUrl = user == null ? "" : absoluteUrl(user.optString("avatar_url", ""));
        if (!avatarUrl.isEmpty()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.addView(image, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            loadImageInto(image, avatarUrl);
            return;
        }
        String displayName = userName(user);
        String initial = displayName.isEmpty() ? "房" : displayName.substring(0, Math.min(1, displayName.length()));
        TextView fallback = text(initial, 16, Color.rgb(28, 45, 76), Typeface.BOLD);
        fallback.setGravity(Gravity.CENTER);
        avatar.addView(fallback, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private String roomEventTypeLabel(JSONObject event) {
        String type = event.optString("event_type", "danmaku");
        if ("interaction".equals(type)) {
            JSONObject payload = event.optJSONObject("payload");
            if (payload != null && "native_remix_share".equals(payload.optString("source_hint", ""))) {
                return "AI 二创";
            }
            return "高光选择";
        }
        if ("danmaku_like".equals(type)) {
            return "点赞弹幕";
        }
        if ("danmaku_reply".equals(type)) {
            return "回复弹幕";
        }
        return "房间发言";
    }

    private void sendWatchRoomEvent() {
        if (watchRoomEventInput == null || activeRoomCode.isEmpty()) {
            return;
        }
        String content = watchRoomEventInput.getText().toString().trim();
        if (content.isEmpty()) {
            setMessage("房间动态不能为空。", false);
            return;
        }
        setMessage("正在发送房间动态...", true);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("text", content);
                payload.put("source", "native_android");
                JSONObject body = new JSONObject();
                body.put("event_type", "danmaku");
                body.put("payload", payload);
                httpPost(loadBaseUrl() + "/api/watch-rooms/" + activeRoomCode + "/events", body.toString(), loadToken());
                runOnUiThread(() -> {
                    watchRoomEventInput.setText("");
                    fetchWatchRoom(activeRoomCode);
                });
            } catch (Exception error) {
                runOnUiThread(() -> setMessage("房间动态发送失败：" + error.getMessage(), false));
            }
        }).start();
    }

    private void addFriendRequestRow(LinearLayout parent, JSONObject request, boolean incoming) {
        JSONObject user = incoming ? request.optJSONObject("from_user") : request.optJSONObject("to_user");
        int requestId = request.optInt("id", 0);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(inputBackground());
        LinearLayout.LayoutParams rowParams = matchWrap();
        rowParams.topMargin = dp(10);
        parent.addView(row, rowParams);

        String label = incoming ? " 请求添加好友" : " 等待对方通过";
        row.addView(text(userName(user) + label, 14, Color.rgb(18, 20, 26), Typeface.BOLD), matchWrap());
        TextView desc = text(user == null ? "" : user.optString("growth_title", "短剧同好"), 12, Color.rgb(88, 98, 118), Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = matchWrap();
        descParams.topMargin = dp(3);
        row.addView(desc, descParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(10);
        row.addView(actions, actionsParams);

        if (incoming) {
            Button acceptButton = primaryButton("接受");
            acceptButton.setOnClickListener(v -> postFriendRequestAction(
                    "/api/users/me/friend-requests/" + requestId + "/accept",
                    "{}",
                    "已接受好友申请。"
            ));
            actions.addView(acceptButton, weightHeight(1, dp(42)));

            Button declineButton = secondaryButton("拒绝");
            declineButton.setOnClickListener(v -> postFriendRequestAction(
                    "/api/users/me/friend-requests/" + requestId + "/decline",
                    "{}",
                    "已拒绝好友申请。"
            ));
            LinearLayout.LayoutParams declineParams = weightHeight(1, dp(42));
            declineParams.leftMargin = dp(10);
            actions.addView(declineButton, declineParams);
        } else {
            Button withdrawButton = secondaryButton("撤回申请");
            withdrawButton.setOnClickListener(v -> postFriendRequestAction(
                    "/api/users/me/friend-requests/" + requestId + "/withdraw",
                    "{}",
                    "已撤回好友申请。"
            ));
            actions.addView(withdrawButton, matchHeight(dp(42)));
        }
    }

    private void addCandidateRow(LinearLayout parent, JSONObject candidate) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(inputBackground());
        LinearLayout.LayoutParams rowParams = matchWrap();
        rowParams.topMargin = dp(10);
        parent.addView(row, rowParams);

        row.addView(text(userName(candidate), 14, Color.rgb(18, 20, 26), Typeface.BOLD), matchWrap());
        TextView desc = text(candidate.optString("growth_title", "短剧同好") + " · " + candidate.optInt("points", 0) + " 积分", 12, Color.rgb(88, 98, 118), Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = matchWrap();
        descParams.topMargin = dp(3);
        row.addView(desc, descParams);

        Button addButton = primaryButton("申请好友");
        addButton.setOnClickListener(v -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("user_id", candidate.optInt("id", 0));
                postFriendRequestAction("/api/users/me/friends", payload.toString(), "好友申请已发送。");
            } catch (Exception error) {
                setMessage("好友申请发送失败：" + error.getMessage(), false);
            }
        });
        LinearLayout.LayoutParams addParams = matchHeight(dp(42));
        addParams.topMargin = dp(10);
        row.addView(addButton, addParams);
    }

    private void postFriendRequestAction(String path, String body, String successMessage) {
        setMessage("正在处理好友申请...", true);
        new Thread(() -> {
            try {
                httpPost(loadBaseUrl() + path, body, loadToken());
                runOnUiThread(() -> {
                    setMessage(successMessage, true);
                    fetchChatSummary();
                });
            } catch (Exception error) {
                runOnUiThread(() -> setMessage("好友申请处理失败：" + error.getMessage(), false));
            }
        }).start();
    }

    private void showChatDetail(JSONObject friend) {
        if (friend == null) {
            return;
        }
        stopActiveVideo();
        ScrollView scrollView = newPage();
        LinearLayout root = pageRoot(scrollView);
        root.setGravity(Gravity.NO_GRAVITY);

        LinearLayout header = card();
        header.setGravity(Gravity.NO_GRAVITY);
        root.addView(header, matchWrap());

        header.addView(text("Chat", 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());
        TextView title = text(userName(friend), 28, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(4);
        header.addView(title, titleParams);

        String titleLine = friend.optString("growth_title", "短剧同好");
        TextView subtitle = text(titleLine + " · " + friend.optInt("points", 0) + " 积分", 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(8);
        header.addView(subtitle, subtitleParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(16);
        header.addView(actions, actionsParams);

        Button backButton = secondaryButton("返回");
        backButton.setOnClickListener(v -> showChatScreen("已返回聊聊。"));
        actions.addView(backButton, weightHeight(1, dp(46)));

        Button refreshButton = primaryButton("刷新");
        refreshButton.setOnClickListener(v -> fetchChatMessages(friend));
        LinearLayout.LayoutParams refreshParams = weightHeight(1, dp(46));
        refreshParams.leftMargin = dp(10);
        actions.addView(refreshButton, refreshParams);

        Button inviteButton = primaryButton("邀请同看");
        inviteButton.setOnClickListener(v -> sendWatchRoomInvite(friend));
        LinearLayout.LayoutParams inviteParams = matchHeight(dp(46));
        inviteParams.topMargin = dp(10);
        header.addView(inviteButton, inviteParams);

        messageText = text("正在加载会话。", 13, Color.rgb(104, 112, 130), Typeface.NORMAL);
        LinearLayout.LayoutParams messageParams = matchWrap();
        messageParams.topMargin = dp(18);
        root.addView(messageText, messageParams);

        chatMessagesContent = new LinearLayout(this);
        chatMessagesContent.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentParams = matchWrap();
        contentParams.topMargin = dp(12);
        root.addView(chatMessagesContent, contentParams);

        addChatEmojiRow(root, friend);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams composerParams = matchWrap();
        composerParams.topMargin = dp(14);
        root.addView(composer, composerParams);

        chatMessageInput = input("", "发一条消息");
        composer.addView(chatMessageInput, weightHeight(1, dp(48)));

        Button sendButton = primaryButton("发送");
        sendButton.setOnClickListener(v -> sendChatMessage(friend));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(86), dp(48));
        sendParams.leftMargin = dp(10);
        composer.addView(sendButton, sendParams);

        setContentView(scrollView);
        fetchChatMessages(friend);
    }

    private void fetchChatMessages(JSONObject friend) {
        if (chatMessagesContent == null || friend == null) {
            return;
        }
        int friendId = friend.optInt("id", 0);
        if (friendId <= 0) {
            setMessage("好友信息缺少 ID，无法打开会话。", false);
            return;
        }
        chatMessagesContent.removeAllViews();
        setMessage("正在同步消息...", true);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject(httpGet(loadBaseUrl() + "/api/chat/messages/" + friendId, loadToken()));
                runOnUiThread(() -> renderChatMessages(friend, payload));
            } catch (Exception error) {
                runOnUiThread(() -> setMessage("消息加载失败：" + error.getMessage(), false));
            }
        }).start();
    }

    private void renderChatMessages(JSONObject fallbackFriend, JSONObject payload) {
        if (chatMessagesContent == null) {
            return;
        }
        chatMessagesContent.removeAllViews();
        JSONObject friend = payload.optJSONObject("friend");
        if (friend == null) {
            friend = fallbackFriend;
        }
        JSONArray messages = payload.optJSONArray("messages");
        setMessage("已打开与 " + userName(friend) + " 的会话。", true);
        if (messages == null || messages.length() == 0) {
            TextView empty = text("还没有聊天记录，可以先发一句。", 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
            chatMessagesContent.addView(empty, matchWrap());
            return;
        }
        int start = Math.max(0, messages.length() - 40);
        for (int i = start; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message != null) {
                addMessageBubble(message);
            }
        }
    }

    private void addMessageBubble(JSONObject message) {
        if (isWatchLinkMessage(message) && addWatchLinkMessageCard(message)) {
            return;
        }
        if (isEmojiMessage(message)) {
            addEmojiMessageBubble(message);
            return;
        }
        boolean outgoing = "outgoing".equals(message.optString("direction", ""));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(outgoing ? Gravity.RIGHT : Gravity.LEFT);
        LinearLayout.LayoutParams rowParams = matchWrap();
        rowParams.topMargin = dp(8);
        chatMessagesContent.addView(row, rowParams);

        TextView bubble = text(message.optString("text", ""), 14, outgoing ? Color.WHITE : Color.rgb(18, 20, 26), Typeface.NORMAL);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setBackground(chatBubbleBackground(outgoing));
        row.addView(bubble, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void addChatEmojiRow(LinearLayout root, JSONObject friend) {
        LinearLayout emojiRow = new LinearLayout(this);
        emojiRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams emojiParams = matchWrap();
        emojiParams.topMargin = dp(14);
        root.addView(emojiRow, emojiParams);

        String[] emojis = {"哈哈哈", "磕到了", "救命", "太上头"};
        for (int i = 0; i < emojis.length; i++) {
            final String emoji = emojis[i];
            Button button = secondaryButton(emoji);
            button.setTextSize(13);
            button.setOnClickListener(v -> sendChatEmoji(friend, emoji));
            LinearLayout.LayoutParams buttonParams = weightHeight(1, dp(40));
            if (i > 0) {
                buttonParams.leftMargin = dp(8);
            }
            emojiRow.addView(button, buttonParams);
        }
    }

    private boolean isEmojiMessage(JSONObject message) {
        String type = message.optString("message_type", message.optString("type", ""));
        return "emoji".equals(type);
    }

    private void addEmojiMessageBubble(JSONObject message) {
        boolean outgoing = "outgoing".equals(message.optString("direction", ""));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(outgoing ? Gravity.RIGHT : Gravity.LEFT);
        LinearLayout.LayoutParams rowParams = matchWrap();
        rowParams.topMargin = dp(8);
        chatMessagesContent.addView(row, rowParams);

        TextView bubble = text(message.optString("text", "👍"), 20, outgoing ? Color.WHITE : Color.rgb(28, 45, 76), Typeface.BOLD);
        bubble.setPadding(dp(18), dp(12), dp(18), dp(12));
        bubble.setBackground(emojiBubbleBackground(outgoing));
        row.addView(bubble, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private boolean isWatchLinkMessage(JSONObject message) {
        String type = message.optString("message_type", message.optString("type", ""));
        return "watch_link".equals(type);
    }

    private boolean addWatchLinkMessageCard(JSONObject message) {
        JSONObject payload = message.optJSONObject("payload");
        if (payload == null) {
            String rawPayload = message.optString("payload", "");
            if (!rawPayload.isEmpty()) {
                try {
                    payload = new JSONObject(rawPayload);
                } catch (Exception ignored) {
                    payload = null;
                }
            }
        }
        if (payload == null) {
            return false;
        }
        final String roomCode = payload.optString("room_code", "").trim().toUpperCase();
        final int episodeId = payload.optInt("episode_id", 0);
        if (roomCode.isEmpty()) {
            return false;
        }

        boolean outgoing = "outgoing".equals(message.optString("direction", ""));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(outgoing ? Gravity.RIGHT : Gravity.LEFT);
        LinearLayout.LayoutParams rowParams = matchWrap();
        rowParams.topMargin = dp(10);
        chatMessagesContent.addView(row, rowParams);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(highlightBackground());
        row.addView(card, matchWrap());

        String kicker = outgoing ? "已发送同看邀请" : "同看邀请";
        card.addView(text(kicker, 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());

        TextView title = text(message.optString("text", "邀请你加入同看房间 " + roomCode), 17, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(5);
        card.addView(title, titleParams);

        String detail = "房间 " + roomCode + (episodeId > 0 ? " · 剧集 " + episodeId : "");
        TextView desc = text(detail, 12, Color.rgb(88, 98, 118), Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = matchWrap();
        descParams.topMargin = dp(5);
        card.addView(desc, descParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(12);
        card.addView(actions, actionsParams);

        Button roomButton = secondaryButton("进房间");
        roomButton.setOnClickListener(v -> showWatchRoomScreen(roomCode, "已打开同看房间。"));
        actions.addView(roomButton, weightHeight(1, dp(42)));

        if (episodeId > 0) {
            Button playButton = primaryButton("直接同看");
            playButton.setOnClickListener(v -> showNativePlayer("同看房间 " + roomCode, episodeId, roomCode));
            LinearLayout.LayoutParams playParams = weightHeight(1, dp(42));
            playParams.leftMargin = dp(10);
            actions.addView(playButton, playParams);
        }
        return true;
    }

    private void sendChatMessage(JSONObject friend) {
        if (chatMessageInput == null || friend == null) {
            return;
        }
        String content = chatMessageInput.getText().toString().trim();
        if (content.isEmpty()) {
            setMessage("消息内容不能为空。", false);
            return;
        }
        int friendId = friend.optInt("id", 0);
        if (friendId <= 0) {
            setMessage("好友信息缺少 ID，无法发送。", false);
            return;
        }
        setMessage("正在发送...", true);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("to_user_id", friendId);
                payload.put("message_type", "text");
                payload.put("text", content);
                httpPost(loadBaseUrl() + "/api/chat/messages", payload.toString(), loadToken());
                runOnUiThread(() -> {
                    chatMessageInput.setText("");
                    fetchChatMessages(friend);
                });
            } catch (Exception error) {
                runOnUiThread(() -> setMessage("消息发送失败：" + error.getMessage(), false));
            }
        }).start();
    }

    private void sendChatEmoji(JSONObject friend, String emoji) {
        if (friend == null) {
            return;
        }
        int friendId = friend.optInt("id", 0);
        if (friendId <= 0) {
            setMessage("好友信息缺少 ID，无法发送表情。", false);
            return;
        }
        setMessage("正在发送表情...", true);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("to_user_id", friendId);
                payload.put("message_type", "emoji");
                payload.put("text", emoji);
                payload.put("payload", new JSONObject());
                httpPost(loadBaseUrl() + "/api/chat/messages", payload.toString(), loadToken());
                runOnUiThread(() -> fetchChatMessages(friend));
            } catch (Exception error) {
                runOnUiThread(() -> setMessage("表情发送失败：" + error.getMessage(), false));
            }
        }).start();
    }

    private void sendWatchRoomInvite(JSONObject friend) {
        if (friend == null) {
            return;
        }
        int friendId = friend.optInt("id", 0);
        if (friendId <= 0) {
            setMessage("好友信息缺少 ID，无法邀请。", false);
            return;
        }
        if (activeEpisodeId <= 0) {
            setMessage("请先进入一集短剧播放页，再回来发起同看邀请。", false);
            return;
        }
        setMessage("正在创建同看房间...", true);
        new Thread(() -> {
            try {
                JSONObject roomPayload = new JSONObject();
                roomPayload.put("episode_id", activeEpisodeId);
                roomPayload.put("progress_sec", 0);
                roomPayload.put("playback_state", "paused");
                JSONObject room = new JSONObject(httpPost(loadBaseUrl() + "/api/watch-rooms", roomPayload.toString(), loadToken()));
                String roomCode = room.optString("code", "");
                if (roomCode.isEmpty()) {
                    throw new IllegalStateException("同看房间缺少房间码");
                }

                JSONObject invitePayload = new JSONObject();
                invitePayload.put("user_id", friendId);
                httpPost(loadBaseUrl() + "/api/watch-rooms/" + roomCode + "/invite", invitePayload.toString(), loadToken());

                JSONObject messagePayload = new JSONObject();
                messagePayload.put("to_user_id", friendId);
                messagePayload.put("message_type", "watch_link");
                messagePayload.put("text", "邀请你加入同看房间 " + roomCode);
                JSONObject linkPayload = new JSONObject();
                linkPayload.put("room_code", roomCode);
                linkPayload.put("episode_id", activeEpisodeId);
                messagePayload.put("payload", linkPayload);
                httpPost(loadBaseUrl() + "/api/chat/messages", messagePayload.toString(), loadToken());

                runOnUiThread(() -> {
                    setMessage("同看邀请已发送：" + roomCode, true);
                    fetchChatMessages(friend);
                });
            } catch (Exception error) {
                runOnUiThread(() -> setMessage("同看邀请发送失败：" + error.getMessage(), false));
            }
        }).start();
    }

    private String userName(JSONObject user) {
        if (user == null) {
            return "好友";
        }
        return user.optString("display_name", user.optString("nickname", "好友"));
    }

    private int arrayLength(JSONArray rows) {
        return rows == null ? 0 : rows.length();
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
                JSONArray watchHistory;
                try {
                    watchHistory = new JSONArray(httpGet(loadBaseUrl() + "/api/users/me/watch-history", loadToken()));
                } catch (Exception ignored) {
                    watchHistory = new JSONArray();
                }
                JSONArray finalWatchHistory = watchHistory;
                runOnUiThread(() -> renderHomeContent(dramas, finalWatchHistory));
            } catch (Exception error) {
                runOnUiThread(() -> setMessage("短剧列表加载失败：" + error.getMessage(), false));
            }
        }).start();
    }

    private void renderDramas(JSONArray dramas) {
        renderHomeContent(dramas, new JSONArray());
    }

    private void renderHomeContent(JSONArray dramas, JSONArray watchHistory) {
        dramaList.removeAllViews();
        setMessage("已加载 " + dramas.length() + " 部短剧，最近观看 " + watchHistory.length() + " 条。", true);
        addWatchHistorySection(watchHistory);
        for (int i = 0; i < dramas.length(); i++) {
            JSONObject drama = dramas.optJSONObject(i);
            if (drama != null) {
                addDramaCard(drama);
            }
        }
    }

    private void addWatchHistorySection(JSONArray rows) {
        if (rows == null || rows.length() == 0) {
            return;
        }
        LinearLayout section = card();
        section.setGravity(Gravity.NO_GRAVITY);
        section.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams sectionParams = matchWrap();
        sectionParams.bottomMargin = dp(12);
        dramaList.addView(section, sectionParams);

        section.addView(text("最近观看", 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());
        TextView title = text("继续刚才的剧情", 21, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(4);
        section.addView(title, titleParams);

        int count = Math.min(3, rows.length());
        for (int i = 0; i < count; i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row != null) {
                addWatchHistoryCard(section, row, i == 0);
            }
        }
    }

    private void addWatchHistoryCard(LinearLayout parent, JSONObject row, boolean first) {
        JSONObject drama = row.optJSONObject("drama");
        String dramaTitle = drama == null ? "短剧" : drama.optString("title", "短剧");
        String episodeTitle = row.optString("episode_title", "第 " + row.optInt("episode_no", 1) + " 集");
        int episodeId = row.optInt("episode_id", 0);
        int progressSec = (int) Math.round(row.optDouble("progress_sec", 0));
        int durationSec = (int) Math.round(row.optDouble("duration_sec", 0));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(inputBackground());
        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.topMargin = first ? dp(14) : dp(10);
        parent.addView(card, cardParams);

        String historyPosterUrl = dramaPosterUrl(dramaTitle, true);
        if (!historyPosterUrl.isEmpty()) {
            ImageView poster = new ImageView(this);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setBackground(imagePlaceholderBackground());
            card.addView(poster, new LinearLayout.LayoutParams(dp(76), dp(76)));
            loadImageInto(poster, historyPosterUrl);
        }

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        infoParams.leftMargin = historyPosterUrl.isEmpty() ? 0 : dp(12);
        card.addView(info, infoParams);

        TextView name = text(dramaTitle + " · " + episodeTitle, 16, Color.rgb(18, 20, 26), Typeface.BOLD);
        name.setSingleLine(true);
        info.addView(name, matchWrap());

        TextView progress = text("已看到 " + formatDuration(progressSec) + progressSuffix(progressSec, durationSec), 12, Color.rgb(88, 98, 118), Typeface.NORMAL);
        LinearLayout.LayoutParams progressParams = matchWrap();
        progressParams.topMargin = dp(5);
        info.addView(progress, progressParams);

        Button continueButton = primaryButton("继续观看");
        continueButton.setEnabled(episodeId > 0);
        continueButton.setOnClickListener(v -> showNativePlayer(dramaTitle, episodeId, "", progressSec));
        LinearLayout.LayoutParams buttonParams = matchHeight(dp(42));
        buttonParams.topMargin = dp(10);
        info.addView(continueButton, buttonParams);
    }

    private String progressSuffix(int progressSec, int durationSec) {
        if (durationSec <= 0) {
            return "";
        }
        int percent = Math.max(0, Math.min(100, Math.round(progressSec * 100f / durationSec)));
        return " · " + percent + "%";
    }

    private String formatDuration(int sec) {
        int safe = Math.max(0, sec);
        return (safe / 60) + ":" + String.format("%02d", safe % 60);
    }

    private String dramaPosterUrl(String title, boolean history) {
        String slug = dramaPosterSlug(title);
        if (slug.isEmpty()) {
            return "";
        }
        return absoluteUrl("/assets/drama_posters/generated/" + slug + (history ? "_history.jpg" : "_card.jpg"));
    }

    private String dramaPosterSlug(String title) {
        String value = title == null ? "" : title;
        if (value.contains("北派寻宝")) return "beipai_xunbao";
        if (value.contains("云渺")) return "yunmiao";
        if (value.contains("那年冬至") || value.contains("冬至")) return "winter_solstice";
        if (value.contains("北往")) return "beiwang";
        if (value.contains("天下第一纨绔") || value.contains("纨绔")) return "diyi_wanku";
        if (value.contains("十八岁太奶") || value.contains("太奶")) return "eighteen_grandma";
        if (value.contains("幸得相遇离婚时") || value.contains("幸福相遇离婚时") || value.contains("离婚时")) return "lucky_divorce";
        if (value.contains("荒年全村") || value.contains("满仓肉")) return "famine_village";
        if (value.contains("家里家外")) return "home_inside_out";
        if (value.contains("撕夜")) return "siye";
        return "";
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

        String posterUrl = dramaPosterUrl(title, false);
        if (!posterUrl.isEmpty()) {
            ImageView poster = new ImageView(this);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setBackground(imagePlaceholderBackground());
            LinearLayout.LayoutParams posterParams = matchHeight(dp(188));
            posterParams.bottomMargin = dp(14);
            card.addView(poster, posterParams);
            loadImageInto(poster, posterUrl);
        }

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
        showNativePlayer(dramaTitle, firstEpisodeId, "");
    }

    private void showNativePlayer(String dramaTitle, int firstEpisodeId, String roomCode) {
        showNativePlayer(dramaTitle, firstEpisodeId, roomCode, 0);
    }

    private void showNativePlayer(String dramaTitle, int firstEpisodeId, String roomCode, int resumeSec) {
        stopActiveVideo();
        enterPlayerImmersiveMode();
        resetHighlightState();
        activeEpisodeId = firstEpisodeId;
        activePlayerRoomCode = roomCode == null ? "" : roomCode.trim().toUpperCase();
        if (!activePlayerRoomCode.isEmpty()) {
            activeRoomCode = activePlayerRoomCode;
        }
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
            if (resumeSec > 1) {
                videoView.seekTo(resumeSec * 1000);
                resetHighlightIndexForPosition(resumeSec * 1000);
            }
            updatePlayerStatus();
            videoView.setBackgroundColor(Color.TRANSPARENT);
            videoView.start();
            scheduleNextHighlight();
            scheduleDanmakuTrack();
            scheduleStickerTimeline();
            scheduleRemixEntry();
            scheduleWatchRoomSync();
            scheduleWatchRoomEvents();
        });
        videoView.setOnErrorListener((mediaPlayer, what, extra) -> {
            status.setText("视频播放失败，请确认服务端和 adb reverse 已连接。");
            return true;
        });
        videoView.setOnCompletionListener(mediaPlayer -> {
            postWatchHistoryProgress(firstEpisodeId, Math.max(videoView.getCurrentPosition(), videoView.getDuration()), true);
            if (!remixEntryShown && hasRemixOptions()) {
                showRemixEntry(true);
            }
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

        if (!activePlayerRoomCode.isEmpty()) {
            activeWatchRoomStrip = new LinearLayout(this);
            activeWatchRoomStrip.setOrientation(LinearLayout.HORIZONTAL);
            activeWatchRoomStrip.setGravity(Gravity.CENTER_VERTICAL);
            activeWatchRoomStrip.setPadding(dp(12), dp(8), dp(10), dp(8));
            activeWatchRoomStrip.setBackground(roomEventBubbleBackground());
            FrameLayout.LayoutParams roomStripParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP
            );
            roomStripParams.leftMargin = dp(16);
            roomStripParams.rightMargin = dp(16);
            roomStripParams.topMargin = dp(96);
            playerFrame.addView(activeWatchRoomStrip, roomStripParams);

            activeWatchRoomAvatars = new LinearLayout(this);
            activeWatchRoomAvatars.setOrientation(LinearLayout.HORIZONTAL);
            activeWatchRoomAvatars.setGravity(Gravity.CENTER_VERTICAL);
            activeWatchRoomStrip.addView(activeWatchRoomAvatars, new LinearLayout.LayoutParams(dp(78), dp(40)));
            addPlayerRoomAvatar(activeWatchRoomAvatars, null, true);
            addPlayerRoomAvatar(activeWatchRoomAvatars, null, false);

            activeWatchRoomStatus = text("同看房间 " + activePlayerRoomCode + " · 正在同步", 12, Color.WHITE, Typeface.BOLD);
            activeWatchRoomStatus.setSingleLine(true);
            LinearLayout.LayoutParams statusInlineParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            statusInlineParams.leftMargin = dp(8);
            activeWatchRoomStrip.addView(activeWatchRoomStatus, statusInlineParams);

            Button roomButton = glassButton("房间");
            roomButton.setTextSize(12);
            roomButton.setOnClickListener(v -> showWatchRoomScreen(activePlayerRoomCode, "已打开同看房间。"));
            LinearLayout.LayoutParams roomButtonParams = new LinearLayout.LayoutParams(dp(68), dp(34));
            roomButtonParams.leftMargin = dp(8);
            activeWatchRoomStrip.addView(roomButton, roomButtonParams);

            activeWatchRoomBoardStatus = text("互动榜 · 等待同伴选择、点赞或发言", 12, Color.WHITE, Typeface.BOLD);
            activeWatchRoomBoardStatus.setSingleLine(true);
            activeWatchRoomBoardStatus.setPadding(dp(12), dp(7), dp(12), dp(7));
            activeWatchRoomBoardStatus.setBackground(danmakuBubbleBackground());
            FrameLayout.LayoutParams boardParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP
            );
            boardParams.leftMargin = dp(16);
            boardParams.rightMargin = dp(16);
            boardParams.topMargin = dp(146);
            playerFrame.addView(activeWatchRoomBoardStatus, boardParams);
        }

        activeDanmakuOverlay = new LinearLayout(this);
        activeDanmakuOverlay.setOrientation(LinearLayout.VERTICAL);
        activeDanmakuOverlay.setPadding(dp(12), activePlayerRoomCode.isEmpty() ? dp(92) : dp(188), dp(12), 0);
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        );
        playerFrame.addView(activeDanmakuOverlay, overlayParams);

        activeHighlightEffectLayer = new FrameLayout(this);
        activeHighlightEffectLayer.setClipChildren(false);
        activeHighlightEffectLayer.setClipToPadding(false);
        playerFrame.addView(activeHighlightEffectLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout bottomControls = new LinearLayout(this);
        bottomControls.setOrientation(LinearLayout.VERTICAL);
        bottomControls.setPadding(dp(14), dp(14), dp(14), dp(18));
        bottomControls.setBackground(controlBarBackground());
        activePlayerControls = bottomControls;
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

        activeRemixEntryButton = pillButton("片尾拓展");
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
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        );
        remixParams.leftMargin = dp(14);
        remixParams.rightMargin = dp(14);
        remixParams.topMargin = dp(18);
        remixParams.bottomMargin = dp(18);
        playerFrame.addView(activeRemixPanel, remixParams);

        setContentView(root);
        videoView.requestFocus();
        fetchEpisodeHighlights(firstEpisodeId, status);
        fetchEpisodeDanmaku(firstEpisodeId);
        fetchEpisodeExperience(firstEpisodeId);
        fetchRemixOptions(firstEpisodeId);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (activeVideoView != null) {
            postCurrentWatchHistoryProgress(false);
            if (activeVideoView.isPlaying()) {
                activeVideoView.pause();
            }
        }
    }

    @Override
    protected void onDestroy() {
        stopActiveVideo();
        super.onDestroy();
    }

    private void stopActiveVideo() {
        exitPlayerImmersiveMode();
        stopProgressWatcher();
        stopDanmakuWatcher();
        stopRemixWatcher();
        stopWatchRoomSync();
        stopWatchRoomEvents();
        stopHighlightTapWatcher();
        stopStickerWatcher();
        stopRemixAudio();
        postCurrentWatchHistoryProgress(false);
        if (voiceRecording && voiceRecordFile != null) {
            voiceRecordFile.delete();
        }
        voiceRecording = false;
        stopVoiceRecorderOnly();
        updateVoiceRecordButton();
        if (activeVideoView != null) {
            activeVideoView.stopPlayback();
            activeVideoView = null;
        }
    }

    private void postCurrentWatchHistoryProgress(boolean force) {
        if (activeVideoView == null || !videoPrepared || activeEpisodeId <= 0) {
            return;
        }
        postWatchHistoryProgress(activeEpisodeId, activeVideoView.getCurrentPosition(), force);
    }

    private void postWatchHistoryProgress(int episodeId, int positionMs, boolean force) {
        if (episodeId <= 0 || positionMs <= 0 || loadToken().isEmpty()) {
            return;
        }
        int progressSec = Math.max(0, Math.round(positionMs / 1000f));
        long now = System.currentTimeMillis();
        if (!force
                && lastWatchHistoryEpisodeId == episodeId
                && Math.abs(progressSec - lastWatchHistoryProgressSec) < 3
                && now - lastWatchHistoryPostAtMs < 10000) {
            return;
        }
        lastWatchHistoryEpisodeId = episodeId;
        lastWatchHistoryProgressSec = progressSec;
        lastWatchHistoryPostAtMs = now;
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("episode_id", episodeId);
                payload.put("progress_sec", progressSec);
                httpPost(loadBaseUrl() + "/api/users/me/watch-history", payload.toString(), loadToken());
            } catch (Exception ignored) {
            }
        }).start();
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
                    resetHighlightIndexToCurrent();
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
                    if (isRemixOverlayVisible()) {
                        progressHandler.postDelayed(progressRunnable, 1000);
                        return;
                    }
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

    private void stopStickerWatcher() {
        if (stickerRunnable != null) {
            progressHandler.removeCallbacks(stickerRunnable);
            stickerRunnable = null;
        }
    }

    private void stopRemixWatcher() {
        if (remixRunnable != null) {
            progressHandler.removeCallbacks(remixRunnable);
            remixRunnable = null;
        }
    }

    private void stopWatchRoomSync() {
        if (roomSyncRunnable != null) {
            progressHandler.removeCallbacks(roomSyncRunnable);
            roomSyncRunnable = null;
        }
    }

    private void stopWatchRoomEvents() {
        if (roomEventsRunnable != null) {
            progressHandler.removeCallbacks(roomEventsRunnable);
            roomEventsRunnable = null;
        }
    }

    private void stopHighlightTapWatcher() {
        if (highlightTapRunnable != null) {
            progressHandler.removeCallbacks(highlightTapRunnable);
            highlightTapRunnable = null;
        }
    }

    private void scheduleWatchRoomSync() {
        stopWatchRoomSync();
        if (activePlayerRoomCode.isEmpty()) {
            return;
        }
        roomSyncRunnable = () -> {
            if (!videoPrepared || activeVideoView == null || activePlayerRoomCode.isEmpty()) {
                return;
            }
            int episodeId = activeEpisodeId;
            int positionMs = Math.max(0, activeVideoView.getCurrentPosition());
            boolean playing = activeVideoView.isPlaying();
            String roomCode = activePlayerRoomCode;
            postWatchRoomSync(roomCode, episodeId, positionMs / 1000.0, playing ? "playing" : "paused");
            progressHandler.postDelayed(roomSyncRunnable, 5000);
        };
        progressHandler.postDelayed(roomSyncRunnable, 1000);
    }

    private void postWatchRoomSync(String roomCode, int episodeId, double progressSec, String playbackState) {
        if (roomCode == null || roomCode.isEmpty() || episodeId <= 0) {
            return;
        }
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("episode_id", episodeId);
                payload.put("progress_sec", progressSec);
                payload.put("playback_state", playbackState);
                String body = httpPost(loadBaseUrl() + "/api/watch-rooms/" + roomCode + "/sync", payload.toString(), loadToken());
                JSONObject room = new JSONObject(body);
                runOnUiThread(() -> updatePlayerWatchRoomStatus(room));
            } catch (Exception ignored) {
                // 同看同步是辅助能力，失败不打断播放。
            }
        }).start();
    }

    private void updatePlayerWatchRoomStatus(JSONObject room) {
        if (activeWatchRoomStatus == null || room == null) {
            return;
        }
        String roomCode = room.optString("code", activePlayerRoomCode);
        if (!roomCode.equals(activePlayerRoomCode)) {
            return;
        }
        JSONObject host = room.optJSONObject("host");
        JSONObject guest = room.optJSONObject("guest");
        JSONObject updatedBy = room.optJSONObject("updated_by");
        int memberCount = room.optInt("member_count", guest == null ? 1 : 2);
        String state = room.optString("playback_state", "paused");
        int progressSec = (int) Math.round(room.optDouble("progress_sec", 0));
        String memberText = memberCount >= 2
                ? userName(host) + " / " + userName(guest)
                : userName(host) + " · 等待好友";
        String stateText = "playing".equals(state) ? "播放中" : "已暂停";
        String updater = updatedBy == null ? "" : " · " + userName(updatedBy) + "同步";
        String titleText = roomUserTitle(host);
        if (guest != null) {
            titleText = titleText + " / " + roomUserTitle(guest);
        }
        activeWatchRoomStatus.setText("同看 " + roomCode + " · " + memberText + "\n" + titleText + " · " + stateText + " " + formatDuration(progressSec) + updater);
        activeWatchRoomStatus.setSingleLine(false);
        updatePlayerRoomAvatars(host, guest);
    }

    private String roomUserTitle(JSONObject user) {
        if (user == null) {
            return "等待加入";
        }
        String title = user.optString("growth_title", "剧情新人");
        int badgeCount = user.optInt("badge_count", 0);
        return badgeCount > 0 ? title + " · " + badgeCount + "枚" : title;
    }

    private void updatePlayerRoomAvatars(JSONObject host, JSONObject guest) {
        if (activeWatchRoomAvatars == null) {
            return;
        }
        activeWatchRoomAvatars.removeAllViews();
        addPlayerRoomAvatar(activeWatchRoomAvatars, host, true);
        addPlayerRoomAvatar(activeWatchRoomAvatars, guest, false);
    }

    private void addPlayerRoomAvatar(LinearLayout parent, JSONObject user, boolean first) {
        FrameLayout avatarShell = new FrameLayout(this);
        GradientDrawable shellBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(220, 255, 255, 255), Color.argb(190, 228, 238, 255)}
        );
        shellBackground.setShape(GradientDrawable.OVAL);
        shellBackground.setStroke(dp(1), Color.argb(135, 255, 255, 255));
        avatarShell.setBackground(shellBackground);
        avatarShell.setClipToOutline(true);
        LinearLayout.LayoutParams shellParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        shellParams.leftMargin = first ? 0 : -dp(8);
        parent.addView(avatarShell, shellParams);

        String avatarUrl = user == null ? "" : absoluteUrl(user.optString("avatar_url", ""));
        if (!avatarUrl.isEmpty()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatarShell.addView(image, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            loadImageInto(image, avatarUrl);
        } else {
            String initial = user == null ? "+" : userName(user).substring(0, Math.min(1, userName(user).length()));
            TextView fallback = text(initial, 15, Color.rgb(28, 45, 76), Typeface.BOLD);
            fallback.setGravity(Gravity.CENTER);
            avatarShell.addView(fallback, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
    }

    private void scheduleWatchRoomEvents() {
        stopWatchRoomEvents();
        if (activePlayerRoomCode.isEmpty()) {
            return;
        }
        roomEventsRunnable = () -> {
            if (!videoPrepared || activeVideoView == null || activePlayerRoomCode.isEmpty()) {
                return;
            }
            String roomCode = activePlayerRoomCode;
            int afterId = lastRoomEventId;
            new Thread(() -> {
                try {
                    JSONArray events = new JSONArray(httpGet(loadBaseUrl() + "/api/watch-rooms/" + roomCode + "/events?after_id=" + afterId, loadToken()));
                    runOnUiThread(() -> renderWatchRoomEventsOverlay(events));
                } catch (Exception ignored) {
                    // 房间动态是辅助展示，失败不影响观看。
                }
            }).start();
            progressHandler.postDelayed(roomEventsRunnable, 3500);
        };
        progressHandler.postDelayed(roomEventsRunnable, 1200);
    }

    private void renderWatchRoomEventsOverlay(JSONArray events) {
        if (events == null || events.length() == 0 || activeDanmakuOverlay == null) {
            return;
        }
        int start = Math.max(0, events.length() - 3);
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) {
                continue;
            }
            lastRoomEventId = Math.max(lastRoomEventId, event.optInt("id", lastRoomEventId));
            updateWatchRoomBoard(event);
            if (i >= start) {
                showWatchRoomEventBubble(event);
            }
        }
    }

    private void updateWatchRoomBoard(JSONObject event) {
        if (activeWatchRoomBoardStatus == null || event == null) {
            return;
        }
        String type = event.optString("event_type", "");
        JSONObject user = event.optJSONObject("user");
        JSONObject payload = event.optJSONObject("payload");
        if (payload == null) {
            return;
        }
        String label = "";
        if ("interaction".equals(type)) {
            label = payload.optString("label", payload.optString("option_label", payload.optString("option_key", "")));
            if (!label.isEmpty()) {
                if ("native_remix_share".equals(payload.optString("source_hint", ""))) {
                    activeRoomLatestAction = userName(user) + "分享了「" + label + "」";
                } else {
                    activeRoomChoiceCounts.put(label, activeRoomChoiceCounts.containsKey(label) ? activeRoomChoiceCounts.get(label) + 1 : 1);
                    activeRoomLatestAction = userName(user) + "选了「" + label + "」";
                }
            }
        } else if ("danmaku_like".equals(type)) {
            activeRoomLatestAction = userName(user) + "赞了一条弹幕";
        } else if ("danmaku_reply".equals(type)) {
            activeRoomLatestAction = userName(user) + "回弹幕：" + payload.optString("text", "同感");
        } else if ("danmaku".equals(type)) {
            activeRoomLatestAction = userName(user) + "说：" + payload.optString("text", "发了一条动态");
        }
        String hotLabel = "";
        int hotCount = 0;
        for (Map.Entry<String, Integer> entry : activeRoomChoiceCounts.entrySet()) {
            if (entry.getValue() > hotCount) {
                hotLabel = entry.getKey();
                hotCount = entry.getValue();
            }
        }
        String hotText = hotLabel.isEmpty() ? "热门选择待产生" : "热门选择「" + hotLabel + "」x" + hotCount;
        String latestText = activeRoomLatestAction.isEmpty() ? "等待同伴互动" : activeRoomLatestAction;
        activeWatchRoomBoardStatus.setText("互动榜 · " + hotText + " · " + latestText);
    }

    private void showWatchRoomEventBubble(JSONObject event) {
        if (activeDanmakuOverlay == null || "immersive".equals(activeDanmakuMode)) {
            return;
        }
        JSONObject user = event.optJSONObject("user");
        String content = roomEventText(event);
        while (activeDanmakuOverlay.getChildCount() >= ("carnival".equals(activeDanmakuMode) ? 4 : 2)) {
            activeDanmakuOverlay.removeViewAt(0);
        }
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.HORIZONTAL);
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setPadding(dp(10), dp(8), dp(12), dp(8));
        bubble.setBackground(roomEventBubbleBackground());
        bubble.setAlpha(0f);
        bubble.setTranslationX(dp(24));
        addRoomEventOverlayAvatar(bubble, user);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        copyParams.leftMargin = dp(8);
        bubble.addView(copy, copyParams);

        TextView title = text("同看 · " + userName(user), 11, Color.argb(230, 255, 255, 255), Typeface.BOLD);
        title.setSingleLine(true);
        copy.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView body = text(shortText(content, 24), 12, Color.WHITE, Typeface.BOLD);
        body.setSingleLine(true);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(1);
        copy.addView(body, bodyParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.RIGHT;
        params.bottomMargin = dp(6);
        params.rightMargin = dp(12);
        activeDanmakuOverlay.addView(bubble, params);
        bubble.animate().alpha(1f).translationX(0f).setDuration(220).start();
        progressHandler.postDelayed(() -> {
            if (activeDanmakuOverlay != null) {
                bubble.animate().alpha(0f).translationX(dp(18)).setDuration(180).withEndAction(() -> {
                    if (activeDanmakuOverlay != null) {
                        activeDanmakuOverlay.removeView(bubble);
                    }
                }).start();
            }
        }, 3600);
    }

    private void addRoomEventOverlayAvatar(LinearLayout parent, JSONObject user) {
        FrameLayout avatar = new FrameLayout(this);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(235, 255, 255, 255), Color.argb(190, 228, 238, 255)}
        );
        background.setShape(GradientDrawable.OVAL);
        background.setStroke(dp(1), Color.argb(125, 255, 255, 255));
        avatar.setBackground(background);
        parent.addView(avatar, new LinearLayout.LayoutParams(dp(30), dp(30)));

        String avatarUrl = user == null ? "" : absoluteUrl(user.optString("avatar_url", ""));
        if (!avatarUrl.isEmpty()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.addView(image, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            loadImageInto(image, avatarUrl);
            return;
        }
        String displayName = userName(user);
        String initial = displayName.isEmpty() ? "同" : displayName.substring(0, Math.min(1, displayName.length()));
        TextView fallback = text(initial, 13, Color.rgb(28, 45, 76), Typeface.BOLD);
        fallback.setGravity(Gravity.CENTER);
        avatar.addView(fallback, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private String roomEventText(JSONObject event) {
        String type = event.optString("event_type", "danmaku");
        JSONObject payload = event.optJSONObject("payload");
        if (payload == null) {
            return "更新了房间动态";
        }
        if ("interaction".equals(type)) {
            String label = payload.optString("label", payload.optString("option_label", ""));
            if ("native_remix_share".equals(payload.optString("source_hint", ""))) {
                return label.isEmpty() ? "分享了一条片尾 AI 二创" : "分享了 " + label;
            }
            return label.isEmpty() ? "完成了一次高光互动" : "选择了 " + label;
        }
        if ("danmaku_like".equals(type)) {
            return "点赞了一条弹幕";
        }
        if ("danmaku_reply".equals(type)) {
            return payload.optString("text", "回复了一条弹幕");
        }
        return payload.optString("text", "发了一条弹幕");
    }

    private void resetHighlightState() {
        stopProgressWatcher();
        stopDanmakuWatcher();
        stopRemixWatcher();
        stopWatchRoomSync();
        stopWatchRoomEvents();
        stopHighlightTapWatcher();
        stopStickerWatcher();
        videoPrepared = false;
        highlightTimeline = new JSONArray();
        danmakuTimeline = new JSONArray();
        stickerTimeline = new JSONArray();
        stickerSlotLastShownAtMs = new long[0];
        remixOptionsPayload = null;
        nextHighlightIndex = 0;
        nextDanmakuIndex = 0;
        lastRoomEventId = 0;
        activeHighlightId = -1;
        highlightEffectSeed = 0;
        activeHighlightTapCount = 0;
        activeHighlightTapSubmitted = false;
        remixEntryShown = false;
        activeHighlightPanel = null;
        activeDanmakuOverlay = null;
        activeHighlightEffectLayer = null;
        activeRemixPanel = null;
        activeWatchRoomStrip = null;
        activeWatchRoomAvatars = null;
        activePlayerStatus = null;
        activeDanmakuStatus = null;
        activeWatchRoomStatus = null;
        activeWatchRoomBoardStatus = null;
        activePlayerControls = null;
        lightDanmakuButton = null;
        carnivalDanmakuButton = null;
        immersiveDanmakuButton = null;
        activeRemixEntryButton = null;
        activePlayerRoomCode = "";
        activeRoomChoiceCounts.clear();
        activeRoomLatestAction = "";
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

    private void resetHighlightIndexToCurrent() {
        int positionMs = activeVideoView == null ? 0 : Math.max(0, activeVideoView.getCurrentPosition());
        resetHighlightIndexForPosition(positionMs);
    }

    private void resetHighlightIndexForPosition(int positionMs) {
        int startIndex = highlightTimeline.length();
        for (int i = 0; i < highlightTimeline.length(); i++) {
            JSONObject highlight = highlightTimeline.optJSONObject(i);
            if (highlight == null) {
                continue;
            }
            int startMs = (int) Math.round(highlight.optDouble("start_time_sec", 0) * 1000);
            if (startMs >= positionMs - 500) {
                startIndex = i;
                break;
            }
        }
        nextHighlightIndex = startIndex;
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
        bubble.setOnClickListener(v -> showDanmakuActionPanel(comment));
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
        }, "carnival".equals(activeDanmakuMode) ? 3600 : 6200);
    }

    private void showDanmakuActionPanel(JSONObject comment) {
        if (activeHighlightPanel == null) {
            return;
        }
        JSONObject user = comment.optJSONObject("user");
        String nickname = user == null ? "观众" : user.optString("nickname", "观众");
        String text = comment.optString("text", "");
        prepareInteractionPanel();

        addPanelHeader(
                activeHighlightPanel,
                "弹幕互动",
                nickname + " 的弹幕",
                text,
                v -> activeHighlightPanel.setVisibility(View.GONE)
        );

        addDanmakuUserStrip(activeHighlightPanel, user, text);
        addPanelSectionTitle(activeHighlightPanel, "快捷回复");

        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams firstRowParams = matchWrap();
        firstRowParams.topMargin = dp(12);
        activeHighlightPanel.addView(firstRow, firstRowParams);

        Button likeButton = primaryButton("点赞这条");
        likeButton.setOnClickListener(v -> submitDanmakuAction("danmaku_like", comment, ""));
        firstRow.addView(likeButton, weightHeight(1, dp(42)));

        Button agreeButton = primaryButton("回复同感");
        agreeButton.setOnClickListener(v -> submitDanmakuAction("danmaku_reply", comment, "同感"));
        LinearLayout.LayoutParams agreeParams = weightHeight(1, dp(42));
        agreeParams.leftMargin = dp(8);
        firstRow.addView(agreeButton, agreeParams);

        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams secondRowParams = matchWrap();
        secondRowParams.topMargin = dp(8);
        activeHighlightPanel.addView(secondRow, secondRowParams);

        Button laughButton = secondaryButton("回复哈哈");
        laughButton.setOnClickListener(v -> submitDanmakuAction("danmaku_reply", comment, "哈哈哈"));
        secondRow.addView(laughButton, weightHeight(1, dp(40)));

        Button closeButton = secondaryButton("关闭");
        closeButton.setOnClickListener(v -> activeHighlightPanel.setVisibility(View.GONE));
        LinearLayout.LayoutParams closeParams = weightHeight(1, dp(40));
        closeParams.leftMargin = dp(8);
        secondRow.addView(closeButton, closeParams);

        addPanelSectionTitle(activeHighlightPanel, "自定义回复");
        LinearLayout customRow = new LinearLayout(this);
        customRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams customRowParams = matchWrap();
        customRowParams.topMargin = dp(8);
        activeHighlightPanel.addView(customRow, customRowParams);

        EditText customInput = input("", "写一句不剧透的回复");
        customRow.addView(customInput, weightHeight(1, dp(42)));

        Button sendCustomButton = primaryButton("发送");
        LinearLayout.LayoutParams sendCustomParams = new LinearLayout.LayoutParams(dp(78), dp(42));
        sendCustomParams.leftMargin = dp(8);
        customRow.addView(sendCustomButton, sendCustomParams);

        TextView customStatus = text("回复会在同看房间同步；普通观看先记为本地互动。", 11, Color.rgb(104, 112, 130), Typeface.NORMAL);
        LinearLayout.LayoutParams customStatusParams = matchWrap();
        customStatusParams.topMargin = dp(6);
        activeHighlightPanel.addView(customStatus, customStatusParams);

        sendCustomButton.setOnClickListener(v -> submitDanmakuCustomReply(comment, customInput, customStatus));

        activeHighlightPanel.setVisibility(View.VISIBLE);
        activeHighlightPanel.bringToFront();
        animatePanel(activeHighlightPanel);
    }

    private void addDanmakuUserStrip(LinearLayout panel, JSONObject user, String content) {
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(12), dp(10), dp(12), dp(10));
        strip.setBackground(inputBackground());
        LinearLayout.LayoutParams stripParams = matchWrap();
        stripParams.topMargin = dp(12);
        panel.addView(strip, stripParams);

        FrameLayout avatar = new FrameLayout(this);
        GradientDrawable avatarBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(230, 255, 255, 255), Color.argb(210, 232, 241, 255)}
        );
        avatarBackground.setShape(GradientDrawable.OVAL);
        avatarBackground.setStroke(dp(1), Color.argb(80, 20, 26, 38));
        avatar.setBackground(avatarBackground);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        strip.addView(avatar, avatarParams);

        String avatarUrl = user == null ? "" : absoluteUrl(user.optString("avatar_url", ""));
        if (!avatarUrl.isEmpty()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.addView(image, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            loadImageInto(image, avatarUrl);
        } else {
            String displayName = userName(user);
            String initial = displayName.isEmpty() ? "观" : displayName.substring(0, Math.min(1, displayName.length()));
            TextView fallback = text(initial, 16, Color.rgb(28, 45, 76), Typeface.BOLD);
            fallback.setGravity(Gravity.CENTER);
            avatar.addView(fallback, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        copyParams.leftMargin = dp(10);
        strip.addView(copy, copyParams);

        TextView name = text(userName(user), 14, Color.rgb(18, 20, 26), Typeface.BOLD);
        name.setSingleLine(true);
        copy.addView(name, matchWrap());

        String title = danmakuUserTitle(user);
        TextView meta = text(title + " · " + shortText(content, 24), 12, Color.rgb(88, 98, 118), Typeface.NORMAL);
        meta.setSingleLine(true);
        LinearLayout.LayoutParams metaParams = matchWrap();
        metaParams.topMargin = dp(2);
        copy.addView(meta, metaParams);
    }

    private String danmakuUserTitle(JSONObject user) {
        return userIdentityTitle(user, "匿名弹幕");
    }

    private String userIdentityTitle(JSONObject user, String fallback) {
        if (user == null) {
            return fallback;
        }
        String growthTitle = user.optString("growth_title", "");
        if (!growthTitle.isEmpty()) {
            return growthTitle;
        }
        if (user.optBoolean("relation_ready", false)) {
            return "可互动观众";
        }
        String role = user.optString("role", "");
        return role.isEmpty() ? fallback : role;
    }

    private void submitDanmakuAction(String eventType, JSONObject comment, String replyText) {
        if (!activePlayerRoomCode.isEmpty()) {
            submitDanmakuRoomEvent(eventType, comment, replyText);
            return;
        }
        String message = "danmaku_like".equals(eventType) ? "已点赞弹幕。" : "已回复：" + shortText(replyText, 20);
        showInteractionFeedback(message, "已记录本次互动；进入同看房间后可同步给好友。");
    }

    private void submitDanmakuCustomReply(JSONObject comment, EditText input, TextView status) {
        String replyText = input == null ? "" : input.getText().toString().trim();
        if (replyText.isEmpty()) {
            if (status != null) {
                status.setText("先写一句回复。");
                status.setTextColor(Color.rgb(210, 54, 70));
            }
            return;
        }
        if (replyText.length() > 48) {
            if (status != null) {
                status.setText("回复控制在 48 字以内，避免遮挡观看。");
                status.setTextColor(Color.rgb(210, 54, 70));
            }
            return;
        }
        if (replyText.contains("http") || replyText.contains("微信") || replyText.contains("QQ")) {
            if (status != null) {
                status.setText("回复里不要带广告或联系方式。");
                status.setTextColor(Color.rgb(210, 54, 70));
            }
            return;
        }
        submitDanmakuAction("danmaku_reply", comment, replyText);
    }

    private void submitDanmakuRoomEvent(String eventType, JSONObject comment, String replyText) {
        if (activePlayerRoomCode.isEmpty()) {
            return;
        }
        String originalText = comment.optString("text", "");
        int commentId = comment.optInt("id", 0);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("danmaku_id", commentId);
                payload.put("danmaku_text", originalText);
                payload.put("episode_id", activeEpisodeId);
                payload.put("source", "native_android");
                if (!replyText.isEmpty()) {
                    payload.put("text", replyText);
                }
                JSONObject body = new JSONObject();
                body.put("event_type", eventType);
                body.put("payload", payload);
                httpPost(loadBaseUrl() + "/api/watch-rooms/" + activePlayerRoomCode + "/events", body.toString(), loadToken());
                String message = "danmaku_like".equals(eventType) ? "已点赞弹幕。" : "已回复：" + shortText(replyText, 20);
                runOnUiThread(() -> showInteractionFeedback(message));
            } catch (Exception error) {
                runOnUiThread(() -> showInteractionFeedback("弹幕互动同步失败。"));
            }
        }).start();
    }

    private void fetchEpisodeExperience(int episodeId) {
        new Thread(() -> {
            try {
                String body = httpGet(loadBaseUrl() + "/api/episodes/" + episodeId + "/experience", loadToken());
                JSONObject payload = new JSONObject(body);
                JSONObject config = payload.optJSONObject("config");
                JSONArray timeline = config == null ? null : config.optJSONArray("sticker_timeline");
                if (timeline == null) {
                    timeline = new JSONArray();
                }
                JSONArray finalTimeline = timeline;
                runOnUiThread(() -> {
                    stickerTimeline = finalTimeline;
                    stickerSlotLastShownAtMs = new long[stickerTimeline.length()];
                    for (int i = 0; i < stickerSlotLastShownAtMs.length; i++) {
                        stickerSlotLastShownAtMs[i] = -100000L;
                    }
                    scheduleStickerTimeline();
                });
            } catch (Exception ignored) {
                runOnUiThread(() -> {
                    stickerTimeline = new JSONArray();
                    stickerSlotLastShownAtMs = new long[0];
                });
            }
        }).start();
    }

    private void scheduleStickerTimeline() {
        if (!videoPrepared || activeVideoView == null || activeHighlightEffectLayer == null || stickerTimeline.length() == 0) {
            return;
        }
        if (stickerSlotLastShownAtMs.length != stickerTimeline.length()) {
            stickerSlotLastShownAtMs = new long[stickerTimeline.length()];
            for (int i = 0; i < stickerSlotLastShownAtMs.length; i++) {
                stickerSlotLastShownAtMs[i] = -100000L;
            }
        }
        stopStickerWatcher();
        stickerRunnable = () -> {
            if (!videoPrepared || activeVideoView == null || activeHighlightEffectLayer == null) {
                return;
            }
            if (!isRemixOverlayVisible()) {
                int positionMs = Math.max(0, activeVideoView.getCurrentPosition());
                for (int i = 0; i < stickerTimeline.length(); i++) {
                    JSONObject slot = stickerTimeline.optJSONObject(i);
                    if (slot == null) {
                        continue;
                    }
                    int startMs = (int) Math.round(slot.optDouble("start_time_sec", -1) * 1000);
                    int endMs = (int) Math.round(slot.optDouble("end_time_sec", -1) * 1000);
                    if (startMs < 0 || endMs <= startMs || positionMs < startMs || positionMs > endMs) {
                        continue;
                    }
                    int cadenceMs = Math.max(2200, (int) Math.round(slot.optDouble("cadence_sec", 6) * 1000));
                    if (positionMs - stickerSlotLastShownAtMs[i] >= cadenceMs) {
                        stickerSlotLastShownAtMs[i] = positionMs;
                        showExperienceStickerSlot(slot, i);
                    }
                }
            }
            progressHandler.postDelayed(stickerRunnable, 650);
        };
        progressHandler.post(stickerRunnable);
    }

    private void showExperienceStickerSlot(JSONObject slot, int slotIndex) {
        JSONArray assetIds = slot.optJSONArray("asset_ids");
        if (assetIds == null || assetIds.length() == 0) {
            return;
        }
        int burstCount = Math.max(1, Math.min(3, slot.optInt("burst_count", assetIds.length())));
        String meaning = slot.optString("meaning", "");
        for (int i = 0; i < burstCount; i++) {
            String assetId = assetIds.optString((i + highlightEffectSeed) % assetIds.length(), "");
            addExperienceSticker(assetId, meaning, slotIndex, i);
        }
        highlightEffectSeed++;
    }

    private void addExperienceSticker(String assetId, String meaning, int slotIndex, int index) {
        if (activeHighlightEffectLayer == null || assetId == null || assetId.trim().isEmpty()) {
            return;
        }
        LinearLayout sticker = new LinearLayout(this);
        sticker.setOrientation(LinearLayout.HORIZONTAL);
        sticker.setGravity(Gravity.CENTER_VERTICAL);
        sticker.setPadding(dp(10), 0, dp(12), 0);
        sticker.setBackground(experienceStickerBackground(assetId, index));
        sticker.setClickable(true);
        sticker.setRotation(experienceStickerRotation(assetId, index));
        sticker.setAlpha(0f);
        sticker.setScaleX(0.72f);
        sticker.setScaleY(0.72f);

        TextView glyph = text(experienceStickerGlyph(assetId), 23, Color.WHITE, Typeface.BOLD);
        glyph.setGravity(Gravity.CENTER);
        sticker.addView(glyph, new LinearLayout.LayoutParams(dp(36), dp(44)));

        TextView label = text(experienceStickerLabel(assetId, meaning), 12, Color.WHITE, Typeface.BOLD);
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = dp(4);
        sticker.addView(label, labelParams);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(48)
        );
        params.leftMargin = experienceStickerLeft(assetId, slotIndex, index);
        params.topMargin = experienceStickerTop(assetId, slotIndex, index);
        activeHighlightEffectLayer.addView(sticker, params);

        sticker.setOnClickListener(v -> {
            animateTap(sticker);
            addExperienceStickerPulse(assetId, slotIndex, index);
        });

        float driveX = experienceStickerDriveX(assetId, index);
        sticker.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationXBy(driveX)
                .translationYBy(-dp(12 + index * 7))
                .setStartDelay(index * 120L)
                .setDuration(220)
                .withEndAction(() -> sticker.animate()
                        .alpha(0f)
                        .translationYBy(-dp(42))
                        .setStartDelay(1300 + index * 160L)
                        .setDuration(460)
                        .withEndAction(() -> {
                            if (activeHighlightEffectLayer != null) {
                                activeHighlightEffectLayer.removeView(sticker);
                            }
                        })
                        .start())
                .start();
    }

    private void addExperienceStickerPulse(String assetId, int slotIndex, int index) {
        if (activeHighlightEffectLayer == null) {
            return;
        }
        TextView pulse = text("+1 " + experienceStickerGlyph(assetId), 16, Color.WHITE, Typeface.BOLD);
        pulse.setSingleLine(true);
        pulse.setGravity(Gravity.CENTER);
        pulse.setPadding(dp(12), 0, dp(12), 0);
        pulse.setBackground(experienceStickerBackground(assetId, index + 4));
        pulse.setAlpha(0f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(38));
        params.leftMargin = experienceStickerLeft(assetId, slotIndex, index);
        params.topMargin = Math.max(dp(150), experienceStickerTop(assetId, slotIndex, index) - dp(44));
        activeHighlightEffectLayer.addView(pulse, params);
        pulse.animate()
                .alpha(1f)
                .translationYBy(-dp(24))
                .setDuration(120)
                .withEndAction(() -> pulse.animate()
                        .alpha(0f)
                        .translationYBy(-dp(46))
                        .setStartDelay(260)
                        .setDuration(320)
                        .withEndAction(() -> {
                            if (activeHighlightEffectLayer != null) {
                                activeHighlightEffectLayer.removeView(pulse);
                            }
                        })
                        .start())
                .start();
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
                        activeRemixEntryButton.setText(options != null && options.length() > 0 ? "片尾拓展" : "暂无片尾拓展");
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

    private boolean hasRemixOptions() {
        JSONArray options = remixOptionsPayload == null ? null : remixOptionsPayload.optJSONArray("options");
        return options != null && options.length() > 0;
    }

    private void enterRemixOverlay() {
        if (activeRemixPanel == null) {
            return;
        }
        if (activeVideoView != null && activeVideoView.isPlaying()) {
            activeVideoView.pause();
        }
        if (activePlayerStatus != null) {
            activePlayerStatus.setText("片尾拓展已打开，视频已暂停。");
        }
        if (activePlayerControls != null) {
            activePlayerControls.setVisibility(View.GONE);
        }
        if (activeHighlightPanel != null) {
            activeHighlightPanel.setVisibility(View.GONE);
        }
        activeRemixPanel.setBackground(remixOverlayBackground());
        activeRemixPanel.setVisibility(View.VISIBLE);
        activeRemixPanel.bringToFront();
    }

    private void dismissRemixPanel() {
        stopRemixAudio();
        if (activeRemixPanel != null) {
            activeRemixPanel.setVisibility(View.GONE);
        }
        if (activePlayerControls != null) {
            activePlayerControls.setVisibility(View.VISIBLE);
        }
        resetHighlightIndexToCurrent();
        updatePlayerStatus();
        scheduleNextHighlight();
    }

    private boolean isRemixOverlayVisible() {
        return activeRemixPanel != null && activeRemixPanel.getVisibility() == View.VISIBLE;
    }

    private void addRemixChoiceCard(LinearLayout parent, String kicker, String titleText, String description, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(13), dp(12), dp(13));
        card.setBackground(remixChoiceBackground());
        card.setClickable(true);
        card.setOnClickListener(listener);
        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.topMargin = dp(10);
        parent.addView(card, cardParams);

        TextView badge = text(kicker, 11, Color.WHITE, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setSingleLine(true);
        badge.setPadding(dp(8), 0, dp(8), 0);
        badge.setBackground(remixBadgeBackground());
        card.addView(badge, new LinearLayout.LayoutParams(dp(78), dp(36)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        copyParams.leftMargin = dp(12);
        card.addView(copy, copyParams);

        TextView title = text(titleText, 17, Color.rgb(18, 20, 26), Typeface.BOLD);
        title.setMaxLines(2);
        copy.addView(title, matchWrap());

        if (description != null && !description.trim().isEmpty()) {
            TextView desc = text(description, 12, Color.rgb(88, 98, 118), Typeface.NORMAL);
            desc.setMaxLines(2);
            LinearLayout.LayoutParams descParams = matchWrap();
            descParams.topMargin = dp(4);
            copy.addView(desc, descParams);
        }

        TextView arrow = text(">", 22, Color.rgb(83, 103, 160), Typeface.BOLD);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(42)));
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
        enterRemixOverlay();
        if (autoTriggered && activeVideoView != null && activeVideoView.isPlaying()) {
            activeVideoView.pause();
        }
        activeRemixPanel.removeAllViews();
        activeRemixPanel.setVisibility(View.VISIBLE);

        JSONArray featured = remixOptionsPayload == null ? null : remixOptionsPayload.optJSONArray("featured_remixes");
        int featuredCount = featured == null ? 0 : featured.length();
        addPanelHeader(
                activeRemixPanel,
                autoTriggered ? "片尾拓展" : "AI 二创",
                "要不要看看另一种后续？",
                featuredCount > 0 ? "已有 " + featuredCount + " 条精选二创。选择一个方向，进入 3 张分镜番外。" : "不影响正片观看，只是一个可选番外体验。",
                v -> dismissRemixPanel()
        );

        if (featuredCount > 0) {
            addFeaturedRemixCards(featured);
        }

        JSONArray options = remixOptionsPayload == null ? null : remixOptionsPayload.optJSONArray("options");
        if (options == null || options.length() == 0) {
            if (featuredCount == 0) {
                TextView empty = text("本集暂未配置片尾二创。", 14, Color.rgb(88, 98, 118), Typeface.NORMAL);
                LinearLayout.LayoutParams emptyParams = matchWrap();
                emptyParams.topMargin = dp(12);
                activeRemixPanel.addView(empty, emptyParams);
            }
            animatePanel(activeRemixPanel);
            return;
        }
        for (int i = 0; i < options.length(); i++) {
            JSONObject option = options.optJSONObject(i);
            if (option == null) {
                continue;
            }
            String label = option.optString("label", "二创方向");
            String desc = option.optString("description", "");
            addRemixChoiceCard(
                    activeRemixPanel,
                    "方向 " + (i + 1),
                    label,
                    desc,
                    v -> showRemixVariants(option)
            );
        }
        animatePanel(activeRemixPanel);
    }

    private void addFeaturedRemixCards(JSONArray featured) {
        if (activeRemixPanel == null || featured == null || featured.length() == 0) {
            return;
        }
        TextView title = text("精选二创", 12, Color.rgb(83, 103, 160), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(14);
        activeRemixPanel.addView(title, titleParams);

        for (int i = 0; i < featured.length(); i++) {
            JSONObject item = featured.optJSONObject(i);
            if (item == null) {
                continue;
            }
            final JSONObject remix = item;
            JSONObject choice = remix.optJSONObject("choice");
            String choiceLabel = remix.optString("choice_label", choice == null ? "AI 二创" : choice.optString("label", "AI 二创"));
            String titleText = remix.optString("title", choiceLabel);
            String detail = remix.optString("logline", remix.optString("share_copy", remix.optString("story_text", "")));
            addRemixChoiceCard(
                    activeRemixPanel,
                    "精选 · " + choiceLabel,
                    titleText,
                    shortText(detail, 64),
                    v -> renderRemixResult(remix)
            );
        }
    }

    private void showRemixVariants(JSONObject option) {
        if (activeRemixPanel == null) {
            return;
        }
        enterRemixOverlay();
        activeRemixPanel.removeAllViews();
        String choiceLabel = option.optString("label", "二创方向");
        String choiceDescription = option.optString("description", "");
        addPanelHeader(
                activeRemixPanel,
                "选择版本",
                choiceLabel,
                choiceDescription,
                v -> showRemixEntry(false)
        );

        String choiceKey = option.optString("key", "");
        JSONArray variants = option.optJSONArray("variants");
        if (shouldUseRemixSceneChoice(choiceKey) && variants != null && variants.length() > 0) {
            JSONObject defaultVariant = variants.optJSONObject(0);
            String defaultVariantKey = defaultVariant == null ? "" : defaultVariant.optString("variant_key", "");
            createRemix(choiceKey, defaultVariantKey, choiceLabel, 0, false);
            return;
        }
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
                String desc = variant.optString("description", variant.optString("value", ""));
                addRemixChoiceCard(
                        activeRemixPanel,
                        "版本 " + (i + 1),
                        label,
                        desc,
                        v -> createRemix(choiceKey, variantKey, label)
                );
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
        createRemix(choiceKey, variantKey, label, 0, true);
    }

    private void createRemix(String choiceKey, String variantKey, String label, int startIndex, boolean sceneChoiceResolved) {
        if (activeRemixPanel == null || choiceKey.isEmpty()) {
            return;
        }
        enterRemixOverlay();
        activeRemixPanel.removeAllViews();
        TextView loading = text("正在穿梭进入剧集", 22, Color.rgb(18, 20, 26), Typeface.BOLD);
        loading.setGravity(Gravity.CENTER);
        activeRemixPanel.addView(loading, matchWrap());
        TextView detail = text("准备「" + label + "」的片尾番外。图片和声音会优先读取缓存。", 13, Color.rgb(88, 98, 118), Typeface.BOLD);
        detail.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailParams = matchWrap();
        detailParams.topMargin = dp(10);
        activeRemixPanel.addView(detail, detailParams);
        TextView orbit = text("•   •   •", 30, Color.rgb(10, 102, 255), Typeface.BOLD);
        orbit.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams orbitParams = matchWrap();
        orbitParams.topMargin = dp(22);
        activeRemixPanel.addView(orbit, orbitParams);
        orbit.animate().translationX(dp(18)).alpha(0.42f).setDuration(520)
                .withEndAction(() -> orbit.animate().translationX(-dp(18)).alpha(1f).setDuration(520).start())
                .start();
        animatePanel(activeRemixPanel);

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
                runOnUiThread(() -> renderRemixResult(result, startIndex, sceneChoiceResolved));
            } catch (Exception error) {
                runOnUiThread(() -> renderRemixError(error.getMessage()));
            }
        }).start();
    }

    private void renderRemixResult(JSONObject result) {
        renderRemixResult(result, 0, true);
    }

    private void renderRemixResult(JSONObject result, int startIndex, boolean sceneChoiceResolved) {
        if (activeRemixPanel == null) {
            return;
        }
        enterRemixOverlay();
        JSONObject imagePlan = result.optJSONObject("image_plan");
        JSONArray shots = imagePlan == null ? null : imagePlan.optJSONArray("shots");
        if (shots != null && shots.length() > 0) {
            renderRemixShot(result, imagePlan, shots, startIndex, sceneChoiceResolved);
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
        renderRemixShot(result, imagePlan, shots, shotIndex, true);
    }

    private void renderRemixShot(JSONObject result, JSONObject imagePlan, JSONArray shots, int shotIndex, boolean sceneChoiceResolved) {
        if (activeRemixPanel == null) {
            return;
        }
        enterRemixOverlay();
        int safeIndex = Math.max(0, Math.min(shotIndex, shots.length() - 1));
        String choiceKey = imagePlan == null ? "" : imagePlan.optString("choice_key", "");
        if (!sceneChoiceResolved && isImmediateRemixSceneChoice(choiceKey)) {
            showRemixSceneChoice(result, imagePlan, shots, safeIndex);
            return;
        }
        JSONObject shot = shots.optJSONObject(safeIndex);
        if (shot == null) {
            renderRemixError("二创图片数据异常");
            return;
        }

        remixVoiceRequestSeq++;
        stopRemixAudio();
        activeRemixPanel.removeAllViews();
        JSONObject choice = result.optJSONObject("choice");
        String titleText = choice == null ? "AI 二创分镜" : choice.optString("label", "AI 二创分镜");
        String subtitle = shot.optString("subtitle", shot.optString("caption", ""));
        addPanelHeader(
                activeRemixPanel,
                "镜头 " + (safeIndex + 1) + " / " + shots.length(),
                titleText,
                subtitle.isEmpty() ? "点击图片或下一张，继续浏览这一段剧情。" : subtitle,
                v -> dismissRemixPanel()
        );

        FrameLayout stage = new FrameLayout(this);
        stage.setBackground(remixStageBackground());
        stage.setClipToPadding(true);
        stage.setClickable(true);
        stage.setOnClickListener(v -> goNextRemixShot(result, imagePlan, shots, safeIndex, sceneChoiceResolved));
        LinearLayout.LayoutParams imageParams = matchHeight(remixImageHeight());
        imageParams.topMargin = dp(12);
        activeRemixPanel.addView(stage, imageParams);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        stage.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        loadImageInto(image, shotImageUrl(shot));

        TextView shotBadge = text("镜头 " + (safeIndex + 1) + " / " + shots.length(), 12, Color.WHITE, Typeface.BOLD);
        shotBadge.setGravity(Gravity.CENTER);
        shotBadge.setPadding(dp(10), 0, dp(10), 0);
        shotBadge.setBackground(remixFloatingBadgeBackground());
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(32),
                Gravity.TOP | Gravity.LEFT
        );
        badgeParams.leftMargin = dp(12);
        badgeParams.topMargin = dp(12);
        stage.addView(shotBadge, badgeParams);

        TextView caption = text(subtitle.isEmpty() ? "轻点图片切换下一镜头" : subtitle, 15, Color.WHITE, Typeface.BOLD);
        caption.setMaxLines(3);
        caption.setPadding(dp(14), dp(10), dp(14), dp(10));
        caption.setBackground(remixCaptionBackground());
        FrameLayout.LayoutParams captionParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        captionParams.leftMargin = dp(12);
        captionParams.rightMargin = dp(12);
        captionParams.bottomMargin = dp(12);
        stage.addView(caption, captionParams);

        TextView pageHint = text(remixShotDots(safeIndex, shots.length()), 16, Color.rgb(83, 103, 160), Typeface.BOLD);
        pageHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pageHintParams = matchWrap();
        pageHintParams.topMargin = dp(8);
        activeRemixPanel.addView(pageHint, pageHintParams);

        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams navParams = matchWrap();
        navParams.topMargin = dp(10);
        activeRemixPanel.addView(navRow, navParams);

        Button previousButton = secondaryButton("上一张");
        previousButton.setOnClickListener(v -> renderRemixShot(result, imagePlan, shots, safeIndex == 0 ? shots.length() - 1 : safeIndex - 1, sceneChoiceResolved));
        navRow.addView(previousButton, weightHeight(1, dp(40)));

        Button nextButton = primaryButton(safeIndex >= shots.length() - 1 ? "回到第一张" : "下一张");
        nextButton.setOnClickListener(v -> goNextRemixShot(result, imagePlan, shots, safeIndex, sceneChoiceResolved));
        LinearLayout.LayoutParams nextParams = weightHeight(1, dp(40));
        nextParams.leftMargin = dp(8);
        navRow.addView(nextButton, nextParams);

        TextView voiceTitle = text("声音带入", 12, Color.rgb(83, 103, 160), Typeface.BOLD);
        LinearLayout.LayoutParams voiceTitleParams = matchWrap();
        voiceTitleParams.topMargin = dp(10);
        activeRemixPanel.addView(voiceTitle, voiceTitleParams);

        LinearLayout voiceRow = new LinearLayout(this);
        voiceRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams voiceParams = matchWrap();
        voiceParams.topMargin = dp(6);
        activeRemixPanel.addView(voiceRow, voiceParams);

        activeRemixOriginalVoiceButton = secondaryButton("原声讲述");
        activeRemixOriginalVoiceButton.setOnClickListener(v -> requestRemixVoice(imagePlan, shot, "original"));
        voiceRow.addView(activeRemixOriginalVoiceButton, weightHeight(1, dp(40)));

        activeRemixUserVoiceButton = secondaryButton("我的声音");
        activeRemixUserVoiceButton.setOnClickListener(v -> requestRemixVoice(imagePlan, shot, "user"));
        LinearLayout.LayoutParams userVoiceParams = weightHeight(1, dp(40));
        userVoiceParams.leftMargin = dp(8);
        voiceRow.addView(activeRemixUserVoiceButton, userVoiceParams);

        activeRemixVoiceStatus = text("选择一种声音播放当前分镜。", 12, Color.rgb(104, 112, 130), Typeface.BOLD);
        LinearLayout.LayoutParams voiceStatusParams = matchWrap();
        voiceStatusParams.topMargin = dp(7);
        activeRemixPanel.addView(activeRemixVoiceStatus, voiceStatusParams);

        activeRemixVoicePublishButton = secondaryButton("发布当前声音");
        activeRemixVoicePublishButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams voicePublishParams = matchHeight(dp(38));
        voicePublishParams.topMargin = dp(8);
        activeRemixPanel.addView(activeRemixVoicePublishButton, voicePublishParams);

        TextView shareTitle = text("分享到逛逛", 12, Color.rgb(83, 103, 160), Typeface.BOLD);
        LinearLayout.LayoutParams shareTitleParams = matchWrap();
        shareTitleParams.topMargin = dp(10);
        activeRemixPanel.addView(shareTitle, shareTitleParams);

        LinearLayout publishRow = new LinearLayout(this);
        publishRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams publishRowParams = matchWrap();
        publishRowParams.topMargin = dp(6);
        activeRemixPanel.addView(publishRow, publishRowParams);

        Button publishStoryButton = primaryButton("发剧情卡");
        publishStoryButton.setOnClickListener(v -> publishRemixAssetPost(result, imagePlan, shot, "story"));
        publishRow.addView(publishStoryButton, weightHeight(1, dp(40)));

        Button publishImageButton = secondaryButton("发图片");
        publishImageButton.setOnClickListener(v -> publishRemixAssetPost(result, imagePlan, shot, "image"));
        LinearLayout.LayoutParams imagePublishParams = weightHeight(1, dp(40));
        imagePublishParams.leftMargin = dp(8);
        publishRow.addView(publishImageButton, imagePublishParams);

        Button againButton = secondaryButton("重新选择剧情");
        againButton.setOnClickListener(v -> showRemixEntry(false));
        LinearLayout.LayoutParams againParams = matchHeight(dp(38));
        againParams.topMargin = dp(8);
        activeRemixPanel.addView(againButton, againParams);
        animatePanel(activeRemixPanel);
    }

    private void goNextRemixShot(JSONObject result, JSONObject imagePlan, JSONArray shots, int safeIndex, boolean sceneChoiceResolved) {
        if (shouldPauseForRemixSceneChoice(imagePlan, safeIndex, sceneChoiceResolved)) {
            showRemixSceneChoice(result, imagePlan, shots, safeIndex);
            return;
        }
        renderRemixShot(result, imagePlan, shots, (safeIndex + 1) % shots.length(), sceneChoiceResolved);
    }

    private boolean shouldPauseForRemixSceneChoice(JSONObject imagePlan, int safeIndex, boolean sceneChoiceResolved) {
        if (sceneChoiceResolved || imagePlan == null || safeIndex != 0) {
            return false;
        }
        String choiceKey = imagePlan.optString("choice_key", "");
        return shouldUseRemixSceneChoice(choiceKey) && !isImmediateRemixSceneChoice(choiceKey);
    }

    private void showRemixSceneChoice(JSONObject result, JSONObject imagePlan, JSONArray shots, int currentIndex) {
        if (activeRemixPanel == null || imagePlan == null || shots == null || shots.length() == 0) {
            return;
        }
        enterRemixOverlay();
        activeRemixPanel.removeAllViews();
        String choiceKey = imagePlan.optString("choice_key", "");
        addPanelHeader(
                activeRemixPanel,
                remixSceneEyebrow(choiceKey),
                remixSceneTitle(choiceKey),
                remixSceneSubtitle(choiceKey),
                v -> dismissRemixPanel()
        );

        JSONArray variants = remixVariantsForChoice(choiceKey);
        if (variants != null) {
            for (int i = 0; i < variants.length(); i++) {
                JSONObject variant = variants.optJSONObject(i);
                if (variant == null) {
                    continue;
                }
                String variantKey = variant.optString("variant_key", "");
                String label = variant.optString("label", "个性版本");
                String variable = variant.optString("variable_label", "选择 " + (i + 1));
                String summary = variant.optString("summary", "");
                addRemixChoiceCard(
                        activeRemixPanel,
                        variable,
                        label,
                        summary,
                        v -> createRemix(choiceKey, variantKey, label, remixSceneChoiceStartIndexAfterChoice(choiceKey), true)
                );
            }
        }

        int continueIndex = isImmediateRemixSceneChoice(choiceKey)
                ? currentIndex
                : Math.min(shots.length() - 1, currentIndex + 1);
        Button keepButton = secondaryButton("继续当前版本");
        keepButton.setOnClickListener(v -> renderRemixShot(result, imagePlan, shots, continueIndex, true));
        LinearLayout.LayoutParams keepParams = matchHeight(dp(40));
        keepParams.topMargin = dp(12);
        activeRemixPanel.addView(keepButton, keepParams);
        animatePanel(activeRemixPanel);
    }

    private boolean shouldUseRemixSceneChoice(String choiceKey) {
        return "road_breakdown".equals(choiceKey)
                || "ticket_home".equals(choiceKey)
                || "kindness_ride".equals(choiceKey);
    }

    private boolean isImmediateRemixSceneChoice(String choiceKey) {
        return "kindness_ride".equals(choiceKey);
    }

    private int remixSceneChoiceStartIndexAfterChoice(String choiceKey) {
        return isImmediateRemixSceneChoice(choiceKey) ? 0 : 1;
    }

    private JSONArray remixVariantsForChoice(String choiceKey) {
        JSONArray options = remixOptionsPayload == null ? null : remixOptionsPayload.optJSONArray("options");
        if (options == null) {
            return null;
        }
        for (int i = 0; i < options.length(); i++) {
            JSONObject option = options.optJSONObject(i);
            if (option != null && choiceKey.equals(option.optString("key", ""))) {
                return option.optJSONArray("variants");
            }
        }
        return null;
    }

    private String remixSceneEyebrow(String choiceKey) {
        if ("road_breakdown".equals(choiceKey)) {
            return "年三十返乡补给站";
        }
        if ("ticket_home".equals(choiceKey)) {
            return "返乡售票口";
        }
        if ("kindness_ride".equals(choiceKey)) {
            return "雪夜顺风车";
        }
        return "片尾选择";
    }

    private String remixSceneTitle(String choiceKey) {
        if ("road_breakdown".equals(choiceKey)) {
            return "他们买什么撑过这一段？";
        }
        if ("ticket_home".equals(choiceKey)) {
            return "他买哪张票继续回家？";
        }
        if ("kindness_ride".equals(choiceKey)) {
            return "哪辆车载他们继续往北？";
        }
        return "选择一个细节";
    }

    private String remixSceneSubtitle(String choiceKey) {
        if ("road_breakdown".equals(choiceKey)) {
            return "轮胎刚补上，下一口补给会决定他们怎么继续往北走。";
        }
        if ("ticket_home".equals(choiceKey)) {
            return "风雪和体力把摩托路逼到尽头，换一种交通方式也要赶回年夜饭。";
        }
        if ("kindness_ride".equals(choiceKey)) {
            return "帮人脱困后摩托不见了，这次善意会把他们送上另一条回家路。";
        }
        return "这个选择会改变后续分镜和声音。";
    }

    private void publishRemixAssetPost(JSONObject result, JSONObject imagePlan, JSONObject shot, String sourceType) {
        if (result == null || imagePlan == null || shot == null) {
            return;
        }
        boolean imagePost = "image".equals(sourceType);
        TextView status = text(imagePost ? "正在发布 AI 图片..." : "正在发布剧情卡...", 12, Color.rgb(83, 103, 160), Typeface.BOLD);
        activeRemixPanel.addView(status, matchWrap());

        new Thread(() -> {
            try {
                JSONObject choice = result.optJSONObject("choice");
                String choiceLabel = choice == null ? result.optString("title", "AI 二创") : choice.optString("label", "AI 二创");
                String subtitle = shot.optString("subtitle", shot.optString("caption", ""));
                String imageUrl = shotImageUrl(shot);
                JSONObject assetPayload = new JSONObject();
                assetPayload.put("source_hint", "native_remix");
                assetPayload.put("episode_id", activeEpisodeId);
                assetPayload.put("remix_id", result.optInt("id", 0));
                assetPayload.put("choice_key", imagePlan.optString("choice_key", ""));
                assetPayload.put("variant_key", imagePlan.optString("variant_key", ""));
                assetPayload.put("shot_index", shot.optInt("index", 1));
                assetPayload.put("shot_subtitle", subtitle);
                assetPayload.put("image_url", imageUrl);

                JSONObject payload = new JSONObject();
                payload.put("visibility", "public");
                payload.put("source_type", imagePost ? "image" : "story");
                payload.put("title", shortText((imagePost ? "AI 图片：" : "AI 剧情卡：") + choiceLabel, 80));
                payload.put("text", shortText(subtitle.isEmpty() ? result.optString("share_copy", result.optString("story_text", "分享了一条片尾 AI 二创。")) : subtitle, 220));
                payload.put("asset_kind", imagePost ? "image" : "story");
                payload.put("asset_url", imageUrl);
                payload.put("asset_payload", assetPayload);
                payload.put("topic", "片尾AI二创");
                String body = httpPost(loadBaseUrl() + "/api/social/posts", payload.toString(), loadToken());
                JSONObject post = new JSONObject(body).optJSONObject("post");
                String shareLabel = "AI二创：" + choiceLabel;
                runOnUiThread(() -> {
                    status.setText("已发布到逛逛");
                    addRemixPostActions(status, post, shareLabel, imagePost ? "image" : "story", imageUrl, assetPayload);
                });
            } catch (Exception error) {
                runOnUiThread(() -> status.setText("发布失败：" + error.getMessage()));
            }
        }).start();
    }

    private void addRemixPostActions(TextView status, JSONObject post, String label, String assetKind, String assetUrl, JSONObject assetPayload) {
        if (activeRemixPanel == null || status == null) {
            return;
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(8);
        activeRemixPanel.addView(actions, actionsParams);

        Button viewButton = primaryButton("查看逛逛");
        viewButton.setOnClickListener(v -> showSocialFeedScreen("已打开逛逛，刚发布的内容会出现在动态流。", "all"));
        actions.addView(viewButton, weightHeight(1, dp(38)));

        if (!activePlayerRoomCode.isEmpty()) {
            Button roomButton = secondaryButton("同步房间");
            roomButton.setOnClickListener(v -> syncRemixPostToWatchRoom(label, post, assetKind, assetUrl, assetPayload, status));
            LinearLayout.LayoutParams roomParams = weightHeight(1, dp(38));
            roomParams.leftMargin = dp(8);
            actions.addView(roomButton, roomParams);
        }
    }

    private void syncRemixPostToWatchRoom(String label, JSONObject post, String assetKind, String assetUrl, JSONObject assetPayload, TextView status) {
        if (activePlayerRoomCode.isEmpty()) {
            if (status != null) {
                status.setText("当前不在同看房间，无法同步。");
            }
            return;
        }
        if (status != null) {
            status.setText("正在同步到同看房间...");
        }
        String roomCode = activePlayerRoomCode;
        new Thread(() -> {
            try {
                JSONObject roomPayload = new JSONObject();
                roomPayload.put("label", shortText(label, 42));
                roomPayload.put("option_label", shortText(label, 42));
                roomPayload.put("option_key", assetKind);
                roomPayload.put("episode_id", activeEpisodeId);
                roomPayload.put("source", "native_android");
                roomPayload.put("source_hint", "native_remix_share");
                roomPayload.put("asset_kind", assetKind);
                roomPayload.put("asset_url", assetUrl);
                roomPayload.put("asset_payload", assetPayload == null ? new JSONObject() : assetPayload);
                roomPayload.put("reward_message", "已同步片尾二创");
                if (post != null) {
                    roomPayload.put("post_id", post.optInt("id", 0));
                }
                JSONObject body = new JSONObject();
                body.put("event_type", "interaction");
                body.put("payload", roomPayload);
                httpPost(loadBaseUrl() + "/api/watch-rooms/" + roomCode + "/events", body.toString(), loadToken());
                runOnUiThread(() -> {
                    if (status != null) {
                        status.setText("已同步到同看房间");
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (status != null) {
                        status.setText("房间同步失败：" + error.getMessage());
                    }
                });
            }
        }).start();
    }

    private int remixImageHeight() {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        return Math.max(dp(250), Math.min(dp(420), screenHeight - dp(560)));
    }

    private String remixShotDots(int activeIndex, int total) {
        StringBuilder builder = new StringBuilder();
        int safeTotal = Math.max(1, total);
        for (int i = 0; i < safeTotal; i++) {
            if (i > 0) {
                builder.append(" ");
            }
            builder.append(i == activeIndex ? "●" : "○");
        }
        return builder.toString();
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
        int requestSeq = ++remixVoiceRequestSeq;
        stopRemixAudio();
        setRemixVoiceControlsLoading(voiceMode);
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
                String audioText = result.optString("text", shot.optString("audio_text", shot.optString("subtitle", "")));
                runOnUiThread(() -> {
                    if (requestSeq != remixVoiceRequestSeq) {
                        return;
                    }
                    restoreRemixVoiceButtons();
                    TextView status = activeRemixVoiceStatus == null ? text("", 12, Color.rgb(104, 112, 130), Typeface.BOLD) : activeRemixVoiceStatus;
                    playRemixAudio(audioUrl, status);
                    addRemixVoicePublishButton(imagePlan, shot, voiceMode, audioUrl, audioText, status);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (requestSeq != remixVoiceRequestSeq) {
                        return;
                    }
                    restoreRemixVoiceButtons();
                    if (activeRemixVoiceStatus != null) {
                        activeRemixVoiceStatus.setText("声音暂不可用：" + error.getMessage());
                    }
                });
            }
        }).start();
    }

    private void setRemixVoiceControlsLoading(String voiceMode) {
        if (activeRemixVoiceStatus != null) {
            activeRemixVoiceStatus.setText(("user".equals(voiceMode) ? "我的声音" : "原声讲述") + "正在准备...");
        }
        if (activeRemixVoicePublishButton != null) {
            activeRemixVoicePublishButton.setVisibility(View.GONE);
            activeRemixVoicePublishButton.setOnClickListener(null);
        }
        if (activeRemixOriginalVoiceButton != null) {
            activeRemixOriginalVoiceButton.setEnabled(false);
            activeRemixOriginalVoiceButton.setText("original".equals(voiceMode) ? "准备中" : "原声讲述");
        }
        if (activeRemixUserVoiceButton != null) {
            activeRemixUserVoiceButton.setEnabled(false);
            activeRemixUserVoiceButton.setText("user".equals(voiceMode) ? "准备中" : "我的声音");
        }
    }

    private void restoreRemixVoiceButtons() {
        if (activeRemixOriginalVoiceButton != null) {
            activeRemixOriginalVoiceButton.setEnabled(true);
            activeRemixOriginalVoiceButton.setText("原声讲述");
        }
        if (activeRemixUserVoiceButton != null) {
            activeRemixUserVoiceButton.setEnabled(true);
            activeRemixUserVoiceButton.setText("我的声音");
        }
    }

    private void addRemixVoicePublishButton(JSONObject imagePlan, JSONObject shot, String voiceMode, String audioUrl, String audioText, TextView status) {
        if (activeRemixPanel == null || audioUrl.isEmpty()) {
            return;
        }
        if (activeRemixVoicePublishButton == null) {
            return;
        }
        activeRemixVoicePublishButton.setText("发" + ("user".equals(voiceMode) ? "我的声音" : "原声音频"));
        activeRemixVoicePublishButton.setVisibility(View.VISIBLE);
        activeRemixVoicePublishButton.setOnClickListener(v -> publishRemixVoicePost(imagePlan, shot, voiceMode, audioUrl, audioText, status));
    }

    private void publishRemixVoicePost(JSONObject imagePlan, JSONObject shot, String voiceMode, String audioUrl, String audioText, TextView status) {
        if (imagePlan == null || shot == null || audioUrl.isEmpty()) {
            return;
        }
        status.setText("正在发布声音动态...");
        new Thread(() -> {
            try {
                String label = "user".equals(voiceMode) ? "我的声音" : "原声讲述";
                JSONObject assetPayload = new JSONObject();
                assetPayload.put("source_hint", "native_remix_voice");
                assetPayload.put("episode_id", activeEpisodeId);
                assetPayload.put("choice_key", imagePlan.optString("choice_key", ""));
                assetPayload.put("variant_key", imagePlan.optString("variant_key", ""));
                assetPayload.put("shot_index", shot.optInt("index", 1));
                assetPayload.put("voice_mode", voiceMode);
                assetPayload.put("audio_text", audioText);
                assetPayload.put("cover_image", shotImageUrl(shot));

                JSONObject payload = new JSONObject();
                payload.put("visibility", "public");
                payload.put("source_type", "voice");
                payload.put("title", shortText("AI 声音：" + label, 80));
                payload.put("text", shortText(audioText.isEmpty() ? "分享了一段片尾 AI 二创声音。" : audioText, 220));
                payload.put("asset_kind", "voice");
                payload.put("asset_url", audioUrl);
                payload.put("asset_payload", assetPayload);
                payload.put("topic", "片尾AI二创");
                String body = httpPost(loadBaseUrl() + "/api/social/posts", payload.toString(), loadToken());
                JSONObject post = new JSONObject(body).optJSONObject("post");
                String shareLabel = "AI声音：" + label;
                runOnUiThread(() -> {
                    status.setText("声音已发布到逛逛");
                    addRemixPostActions(status, post, shareLabel, "voice", audioUrl, assetPayload);
                });
            } catch (Exception error) {
                runOnUiThread(() -> status.setText("声音发布失败：" + error.getMessage()));
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
        enterRemixOverlay();
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

    private void prepareInteractionPanel() {
        if (activeHighlightPanel == null) {
            return;
        }
        stopHighlightTapWatcher();
        activeHighlightTapCount = 0;
        activeHighlightTapSubmitted = false;
        if (activeRemixPanel != null) {
            activeRemixPanel.setVisibility(View.GONE);
        }
        if (activePlayerControls != null) {
            activePlayerControls.setVisibility(View.VISIBLE);
        }
        activeHighlightPanel.setBackground(highlightBackground());
        activeHighlightPanel.removeAllViews();
    }

    private void addHighlightHeader(LinearLayout panel, String highlightType, String emotion, String titleText, String subtitle, View.OnClickListener closeListener) {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(hero, matchWrap());

        TextView glyph = text(highlightGlyph(highlightType), 18, Color.WHITE, Typeface.BOLD);
        glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(highlightGlyphBackground(highlightType));
        hero.addView(glyph, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        copyParams.leftMargin = dp(12);
        hero.addView(copy, copyParams);

        TextView kicker = text(highlightType + " · " + emotion, 12, Color.rgb(83, 103, 160), Typeface.BOLD);
        kicker.setSingleLine(true);
        copy.addView(kicker, matchWrap());

        TextView title = text(titleText, 21, Color.rgb(18, 20, 26), Typeface.BOLD);
        title.setMaxLines(2);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(3);
        copy.addView(title, titleParams);

        Button closeButton = secondaryButton("收起");
        closeButton.setTextSize(12);
        closeButton.setOnClickListener(closeListener);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(66), dp(34));
        closeParams.leftMargin = dp(8);
        hero.addView(closeButton, closeParams);

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView desc = text(subtitle, 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
            desc.setLineSpacing(dp(2), 1.0f);
            desc.setMaxLines(3);
            LinearLayout.LayoutParams descParams = matchWrap();
            descParams.topMargin = dp(10);
            panel.addView(desc, descParams);
        }
    }

    private void addPanelHeader(LinearLayout panel, String kicker, String titleText, String subtitle, View.OnClickListener closeListener) {
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(topRow, matchWrap());

        TextView badge = text(kicker, 12, Color.rgb(28, 45, 76), Typeface.BOLD);
        badge.setSingleLine(true);
        badge.setGravity(Gravity.CENTER_VERTICAL);
        badge.setPadding(dp(12), 0, dp(12), 0);
        badge.setBackground(panelBadgeBackground());
        topRow.addView(badge, new LinearLayout.LayoutParams(0, dp(30), 1));

        Button closeButton = secondaryButton("收起");
        closeButton.setTextSize(12);
        closeButton.setOnClickListener(closeListener);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(72), dp(32));
        closeParams.leftMargin = dp(10);
        topRow.addView(closeButton, closeParams);

        TextView title = text(titleText, 21, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(10);
        panel.addView(title, titleParams);

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView desc = text(subtitle, 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
            desc.setLineSpacing(dp(2), 1.0f);
            desc.setMaxLines(3);
            LinearLayout.LayoutParams descParams = matchWrap();
            descParams.topMargin = dp(7);
            panel.addView(desc, descParams);
        }
    }

    private void addPanelSectionTitle(LinearLayout panel, String value) {
        TextView section = text(value, 12, Color.rgb(83, 103, 160), Typeface.BOLD);
        LinearLayout.LayoutParams sectionParams = matchWrap();
        sectionParams.topMargin = dp(12);
        panel.addView(section, sectionParams);
    }

    private void animatePanel(View panel) {
        panel.setAlpha(0f);
        panel.setTranslationY(dp(18));
        panel.animate().alpha(1f).translationY(0f).setDuration(240).start();
    }

    private void animateTap(View view) {
        view.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(70)
                .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                .start();
    }

    private void showHighlightStickers(String highlightType, String mainText, String emotion, boolean burst) {
        if (activeHighlightEffectLayer == null) {
            return;
        }
        String[] stickers = highlightStickerTexts(highlightType, mainText, emotion, burst);
        int count = burst ? Math.min(5, stickers.length) : Math.min(3, stickers.length);
        for (int i = 0; i < count; i++) {
            addHighlightSticker(stickers[i], highlightType, i, burst);
        }
        highlightEffectSeed++;
    }

    private void addHighlightSticker(String value, String highlightType, int index, boolean burst) {
        if (activeHighlightEffectLayer == null || value == null || value.trim().isEmpty()) {
            return;
        }
        TextView sticker = text(value, burst ? 17 : 15, Color.WHITE, Typeface.BOLD);
        sticker.setSingleLine(true);
        sticker.setGravity(Gravity.CENTER);
        sticker.setPadding(dp(13), 0, dp(13), 0);
        sticker.setBackground(highlightStickerBackground(highlightType, burst, index));
        sticker.setRotation(highlightStickerRotation(index));
        sticker.setAlpha(0f);
        sticker.setScaleX(0.82f);
        sticker.setScaleY(0.82f);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(burst ? 42 : 38)
        );
        params.leftMargin = highlightStickerLeft(index);
        params.topMargin = highlightStickerTop(index, burst);
        activeHighlightEffectLayer.addView(sticker, params);

        sticker.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationYBy(-dp(burst ? 18 : 12))
                .setDuration(180)
                .withEndAction(() -> sticker.animate()
                        .alpha(0f)
                        .translationYBy(-dp(burst ? 62 : 44))
                        .setStartDelay(burst ? 650 : 1150)
                        .setDuration(420)
                        .withEndAction(() -> {
                            if (activeHighlightEffectLayer != null) {
                                activeHighlightEffectLayer.removeView(sticker);
                            }
                        })
                        .start())
                .start();
    }

    private String[] highlightStickerTexts(String highlightType, String mainText, String emotion, boolean burst) {
        String glyph = highlightGlyph(highlightType);
        String compactMain = shortText(mainText == null ? "" : mainText.trim(), 8);
        String compactEmotion = shortText(emotion == null ? "" : emotion.trim(), 6);
        if (burst) {
            return new String[]{"+1", compactMain, glyph + "感", "已同步", "热度上升"};
        }
        if (highlightType != null && (highlightType.contains("甜") || highlightType.contains("爱情") || highlightType.contains("心动"))) {
            return new String[]{"心动", compactMain, "甜度上升", compactEmotion, "名场面"};
        }
        if (highlightType != null && (highlightType.contains("悲") || highlightType.contains("虐") || highlightType.contains("感动"))) {
            return new String[]{"破防", compactMain, "抱抱", compactEmotion, "情绪到了"};
        }
        if (highlightType != null && (highlightType.contains("反转") || highlightType.contains("悬疑") || highlightType.contains("悬念"))) {
            return new String[]{"?", compactMain, "等等", compactEmotion, "反转预警"};
        }
        if (highlightType != null && (highlightType.contains("搞笑") || highlightType.contains("好笑"))) {
            return new String[]{"笑点", compactMain, "绷不住", compactEmotion, "再来"};
        }
        if (highlightType != null && (highlightType.contains("冲突") || highlightType.contains("高能") || highlightType.contains("爽"))) {
            return new String[]{"冲", compactMain, "燃起来", compactEmotion, "高能"};
        }
        return new String[]{glyph, compactMain, compactEmotion, "高光", "上头"};
    }

    private int highlightStickerLeft(int index) {
        int width = Math.max(dp(320), getResources().getDisplayMetrics().widthPixels);
        int laneWidth = Math.max(dp(56), (width - dp(108)) / 4);
        int lane = Math.abs(highlightEffectSeed + index * 2) % 4;
        return dp(18) + lane * laneWidth;
    }

    private int highlightStickerTop(int index, boolean burst) {
        int height = Math.max(dp(620), getResources().getDisplayMetrics().heightPixels);
        int topStart = burst ? dp(190) : dp(132);
        int usable = Math.max(dp(180), height - dp(burst ? 430 : 460));
        int lane = Math.abs(highlightEffectSeed * 2 + index * 3) % 5;
        return topStart + lane * Math.max(dp(38), usable / 5);
    }

    private float highlightStickerRotation(int index) {
        int value = (highlightEffectSeed + index) % 5;
        return new float[]{-7f, 5f, -3f, 8f, -5f}[value];
    }

    private String experienceStickerGlyph(String assetId) {
        String key = assetId == null ? "" : assetId.toLowerCase();
        if (key.contains("meal")) return "烟";
        if (key.contains("bill")) return "账";
        if (key.contains("go") || key.contains("charge")) return "冲";
        if (key.contains("cash") || key.contains("wage")) return "薪";
        if (key.contains("phone")) return "家";
        if (key.contains("lantern")) return "年";
        if (key.contains("ticket") || key.contains("train")) return "票";
        if (key.contains("question")) return "?";
        if (key.contains("rock")) return "摇";
        if (key.contains("moto") || key.contains("vehicle") || key.contains("car")) return "车";
        if (key.contains("north")) return "北";
        if (key.contains("rain") || key.contains("snow")) return "落";
        if (key.contains("seal")) return "印";
        if (key.contains("spirit")) return "灵";
        if (key.contains("treasure") || key.contains("map")) return "宝";
        if (key.contains("compass")) return "针";
        if (key.contains("trap")) return "警";
        if (key.contains("heart") || key.contains("kiss") || key.contains("blush")) return "心";
        if (key.contains("crow")) return "冷";
        if (key.contains("wow") || key.contains("laugh")) return "哇";
        if (key.contains("tear")) return "泪";
        return "高";
    }

    private String experienceStickerLabel(String assetId, String meaning) {
        String key = assetId == null ? "" : assetId;
        String lower = key.toLowerCase();
        if (lower.contains("meal")) return "打工人日常";
        if (lower.contains("nopay") || lower.contains("bill")) return "一分没结";
        if (lower.contains("gosign") || lower.contains("charge")) return "冲起来";
        if (lower.contains("debtcash")) return "欠薪结清";
        if (lower.contains("wage")) return "欠薪得还";
        if (lower.contains("homephone")) return "想家了";
        if (lower.contains("lantern")) return "年三十";
        if (lower.contains("ticket")) return "回家票";
        if (lower.contains("smoke")) return "悬着心";
        if (lower.contains("road")) return "回得去吗";
        if (lower.contains("rockword")) return "贼摇滚";
        if (lower.contains("rockmoto")) return "摩托回家";
        if (lower.contains("north")) return "一路北往";
        if (lower.contains("rain")) return "灵雨压场";
        if (lower.contains("seal")) return "法阵亮起";
        if (lower.contains("spirit")) return "灵气现形";
        if (lower.contains("map")) return "藏宝图";
        if (lower.contains("compass")) return "罗盘指针";
        if (lower.contains("trap")) return "机关警报";
        if (lower.contains("snow")) return "冬至雪";
        if (lower.contains("kiss")) return "突然亲吻";
        if (lower.contains("heartbeat")) return "心跳加速";
        if (lower.contains("broken")) return "心碎";
        if (lower.contains("blush")) return "脸红";
        if (lower.contains("choice")) return "选哪边";
        if (lower.contains("questionlove")) return "爱还是现实";
        if (lower.contains("hug")) return "抱抱";
        if (lower.contains("wow")) return "哇塞";
        if (lower.contains("laugh")) return "笑点";
        if (lower.contains("tear")) return "心疼";
        if (meaning != null && !meaning.trim().isEmpty()) {
            return shortText(meaning, 8);
        }
        return shortText(key, 8);
    }

    private int experienceStickerAccent(String assetId, boolean strong) {
        String key = assetId == null ? "" : assetId.toLowerCase();
        if (key.contains("heart") || key.contains("kiss") || key.contains("blush") || key.contains("hug")) {
            return strong ? Color.rgb(255, 86, 128) : Color.rgb(255, 151, 174);
        }
        if (key.contains("winter") || key.contains("snow")) {
            return strong ? Color.rgb(88, 142, 255) : Color.rgb(165, 210, 255);
        }
        if (key.contains("xianxia") || key.contains("rain") || key.contains("seal") || key.contains("spirit")) {
            return strong ? Color.rgb(98, 101, 238) : Color.rgb(144, 205, 255);
        }
        if (key.contains("treasure") || key.contains("map") || key.contains("compass") || key.contains("trap")) {
            return strong ? Color.rgb(178, 125, 42) : Color.rgb(235, 176, 72);
        }
        if (key.contains("question") || key.contains("smoke") || key.contains("road")) {
            return strong ? Color.rgb(94, 71, 214) : Color.rgb(144, 121, 238);
        }
        if (key.contains("ticket") || key.contains("phone") || key.contains("lantern") || key.contains("north")) {
            return strong ? Color.rgb(44, 113, 210) : Color.rgb(105, 162, 233);
        }
        if (key.contains("moto") || key.contains("vehicle") || key.contains("car") || key.contains("rock")) {
            return strong ? Color.rgb(255, 116, 58) : Color.rgb(255, 161, 83);
        }
        return strong ? Color.rgb(18, 20, 26) : Color.rgb(255, 140, 72);
    }

    private GradientDrawable experienceStickerBackground(String assetId, int index) {
        int first = experienceStickerAccent(assetId, true);
        int second = experienceStickerAccent(assetId, false);
        if (index % 3 == 1) {
            first = Color.rgb(24, 32, 54);
        }
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(236, Color.red(first), Color.green(first), Color.blue(first)),
                        Color.argb(224, Color.red(second), Color.green(second), Color.blue(second))
                }
        );
        drawable.setCornerRadius(dp(21));
        drawable.setStroke(dp(1), Color.argb(118, 255, 255, 255));
        return drawable;
    }

    private int experienceStickerLeft(String assetId, int slotIndex, int index) {
        int width = Math.max(dp(320), getResources().getDisplayMetrics().widthPixels);
        int usable = Math.max(dp(160), width - dp(190));
        int lane = Math.abs((assetId == null ? 0 : assetId.hashCode()) + highlightEffectSeed * 3 + slotIndex * 5 + index * 7) % 5;
        return Math.max(dp(12), Math.min(width - dp(170), dp(16) + lane * Math.max(dp(46), usable / 5)));
    }

    private int experienceStickerTop(String assetId, int slotIndex, int index) {
        int height = Math.max(dp(620), getResources().getDisplayMetrics().heightPixels);
        int usable = Math.max(dp(260), height - dp(850));
        int lane = Math.abs((assetId == null ? 0 : assetId.hashCode()) + highlightEffectSeed + slotIndex * 3 + index * 11) % 6;
        return dp(170) + lane * Math.max(dp(52), usable / 6);
    }

    private float experienceStickerRotation(String assetId, int index) {
        int value = Math.abs((assetId == null ? 0 : assetId.hashCode()) + index) % 5;
        return new float[]{-8f, 4f, -3f, 7f, -5f}[value];
    }

    private float experienceStickerDriveX(String assetId, int index) {
        String key = assetId == null ? "" : assetId.toLowerCase();
        if (key.contains("moto") || key.contains("vehicle") || key.contains("car") || key.contains("rock")) {
            return dp(38 + index * 16);
        }
        if (key.contains("smoke") || key.contains("rain") || key.contains("snow")) {
            return dp(index % 2 == 0 ? 16 : -16);
        }
        return 0f;
    }

    private void showHighlight(JSONObject highlight) {
        if (activeHighlightPanel == null) {
            return;
        }
        int highlightId = highlight.optInt("id", -1);
        activeHighlightId = highlightId;
        String highlightType = highlight.optString("highlight_type", "高光");
        String emotion = highlight.optString("emotion", "情绪");
        String titleText = highlight.optString("title", "剧情高光");
        String descriptionText = highlight.optString("description", "");
        JSONArray options = highlight.optJSONArray("options");
        prepareInteractionPanel();
        activeHighlightPanel.setBackground(highlightBackground(highlightType));
        showHighlightStickers(highlightType, titleText, emotion, false);

        addHighlightHeader(
                activeHighlightPanel,
                highlightType,
                emotion,
                titleText,
                descriptionText,
                v -> hideHighlightAndScheduleNext()
        );

        addPanelSectionTitle(activeHighlightPanel, "选择你的反应，同步到互动榜");

        boolean tapMode = isHighlightTapMode(highlight);
        if (tapMode) {
            addHighlightTapPad(highlightId, highlightType, emotion, options);
        } else {
            LinearLayout optionsList = new LinearLayout(this);
            optionsList.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams listParams = matchWrap();
            listParams.topMargin = dp(8);
            activeHighlightPanel.addView(optionsList, listParams);

            int count = Math.max(1, options == null ? 0 : options.length());
            for (int i = 0; i < count; i++) {
                JSONObject option = options == null ? null : options.optJSONObject(i);
                String label = option == null ? "我有话说" : option.optString("label", "我有话说");
                String optionKey = option == null ? "default" : option.optString("key", "default");
                Button optionButton = highlightOptionButton(label, highlightType, i);
                optionButton.setOnClickListener(v -> {
                    animateTap(v);
                    showHighlightStickers(highlightType, label, emotion, true);
                    submitInteraction(highlightId, optionKey, label);
                });
                LinearLayout.LayoutParams optionParams = matchHeight(dp(48));
                if (i > 0) {
                    optionParams.topMargin = dp(8);
                }
                optionsList.addView(optionButton, optionParams);
            }
        }

        Button dismissButton = secondaryButton("继续看正片");
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
        if (!tapMode) {
            progressHandler.postDelayed(() -> {
                if (activeHighlightPanel != null
                        && activeHighlightPanel.getVisibility() == View.VISIBLE
                        && activeHighlightId == highlightId) {
                    hideHighlightAndScheduleNext();
                }
            }, 10000);
        }
    }

    private boolean isHighlightTapMode(JSONObject highlight) {
        if (highlight == null) {
            return false;
        }
        JSONArray options = highlight.optJSONArray("options");
        if (options == null || options.length() == 0 || highlight.optJSONObject("reward_hint") != null) {
            return false;
        }
        for (int i = 0; i < options.length(); i++) {
            JSONObject option = options.optJSONObject(i);
            String key = option == null ? "" : option.optString("key", "");
            if (key.contains("vehicle") || key.contains("train") || key.contains("car") || key.contains("motor")) {
                return false;
            }
        }
        String source = highlight.optString("highlight_type", "")
                + highlight.optString("title", "")
                + highlight.optString("description", "")
                + highlight.optString("emotion", "");
        return source.contains("爽")
                || source.contains("高能")
                || source.contains("搞笑")
                || source.contains("好笑")
                || source.contains("反转")
                || source.contains("冲突")
                || source.contains("甜")
                || source.contains("爱情")
                || source.contains("心动")
                || source.contains("亲");
    }

    private void addHighlightTapPad(int highlightId, String highlightType, String emotion, JSONArray options) {
        JSONObject option = options == null ? null : options.optJSONObject(0);
        String optionKey = option == null ? "default" : option.optString("key", "default");
        String optionLabel = option == null ? highlightTapPadText(highlightType) : option.optString("label", highlightTapPadText(highlightType));

        TextView helper = text("连续点击表达情绪，停手后自动同步。超过 99 次显示 MAX。", 12, Color.rgb(88, 98, 118), Typeface.BOLD);
        LinearLayout.LayoutParams helperParams = matchWrap();
        helperParams.topMargin = dp(4);
        activeHighlightPanel.addView(helper, helperParams);

        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.HORIZONTAL);
        pad.setGravity(Gravity.CENTER_VERTICAL);
        pad.setPadding(dp(18), 0, dp(18), 0);
        pad.setClickable(true);
        pad.setBackground(highlightImpactPadBackground(highlightType, false));
        LinearLayout.LayoutParams padParams = matchHeight(dp(88));
        padParams.topMargin = dp(10);
        activeHighlightPanel.addView(pad, padParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        pad.addView(copy, copyParams);

        TextView title = text(highlightTapPadText(highlightType), 18, Color.WHITE, Typeface.BOLD);
        title.setSingleLine(true);
        copy.addView(title, matchWrap());

        TextView subtitle = text(optionLabel + " · 停手后自动提交", 12, Color.argb(220, 255, 255, 255), Typeface.BOLD);
        subtitle.setSingleLine(true);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(3);
        copy.addView(subtitle, subtitleParams);

        TextView counter = text("0", 34, Color.WHITE, Typeface.BOLD);
        counter.setGravity(Gravity.CENTER);
        counter.setBackground(highlightTapCounterBackground());
        pad.addView(counter, new LinearLayout.LayoutParams(dp(86), dp(58)));

        pad.setOnClickListener(v -> handleHighlightTap(pad, counter, highlightId, optionKey, optionLabel, highlightType, emotion));
        scheduleHighlightTapNoClickHide(highlightId);
    }

    private String highlightTapPadText(String highlightType) {
        String type = highlightType == null ? "" : highlightType;
        if (type.contains("甜") || type.contains("爱情") || type.contains("心动")) {
            return "疯狂送心";
        }
        if (type.contains("搞笑") || type.contains("好笑")) {
            return "笑点连击";
        }
        if (type.contains("反转")) {
            return "反转刷屏";
        }
        if (type.contains("爽") || type.contains("高能")) {
            return "爽值连击";
        }
        return "情绪连击";
    }

    private void handleHighlightTap(View pad, TextView counter, int highlightId, String optionKey, String optionLabel, String highlightType, String emotion) {
        if (activeHighlightId != highlightId || activeHighlightTapSubmitted) {
            return;
        }
        activeHighlightTapCount++;
        counter.setText(activeHighlightTapCount > 99 ? "MAX" : String.valueOf(activeHighlightTapCount));
        pad.setBackground(highlightImpactPadBackground(highlightType, activeHighlightTapCount >= 5));
        animateTap(pad);
        showHighlightTapBurst(pad, highlightType, optionLabel, emotion, activeHighlightTapCount);
        scheduleHighlightTapSubmit(highlightId, optionKey, optionLabel);
    }

    private void scheduleHighlightTapNoClickHide(int highlightId) {
        stopHighlightTapWatcher();
        highlightTapRunnable = () -> {
            if (activeHighlightId == highlightId && activeHighlightTapCount == 0) {
                hideHighlightAndScheduleNext();
            }
        };
        progressHandler.postDelayed(highlightTapRunnable, 2200);
    }

    private void scheduleHighlightTapSubmit(int highlightId, String optionKey, String optionLabel) {
        stopHighlightTapWatcher();
        highlightTapRunnable = () -> {
            if (activeHighlightId != highlightId || activeHighlightTapSubmitted || activeHighlightTapCount <= 0) {
                return;
            }
            activeHighlightTapSubmitted = true;
            String countText = activeHighlightTapCount > 99 ? "MAX" : String.valueOf(activeHighlightTapCount);
            submitInteraction(highlightId, optionKey, optionLabel + " x" + countText);
        };
        progressHandler.postDelayed(highlightTapRunnable, 1100);
    }

    private void showHighlightTapBurst(View pad, String highlightType, String optionLabel, String emotion, int count) {
        showHighlightStickers(highlightType, optionLabel, emotion, true);
        String countText = count > 99 ? "MAX" : String.valueOf(count);
        addHighlightTapWord("+1", highlightType, 0, true);
        addHighlightTapWord("总 " + countText, highlightType, 1, true);
        if (count >= 5) {
            boolean heart = isHeartHighlight(highlightType);
            addHighlightTapWord(heart ? (count >= 16 ? "心动MAX" : "爱心变大") : (count >= 16 ? "燃到MAX" : "开始冒火"), highlightType, 2, true);
        }
    }

    private void addHighlightTapWord(String value, String highlightType, int index, boolean burst) {
        if (activeHighlightEffectLayer == null || value == null || value.trim().isEmpty()) {
            return;
        }
        TextView word = text(value, index == 1 ? 15 : 18, Color.WHITE, Typeface.BOLD);
        word.setSingleLine(true);
        word.setGravity(Gravity.CENTER);
        word.setPadding(dp(12), 0, dp(12), 0);
        word.setBackground(highlightStickerBackground(highlightType, burst, index + 5));
        word.setAlpha(0f);
        word.setScaleX(0.78f);
        word.setScaleY(0.78f);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(40)
        );
        int width = Math.max(dp(320), getResources().getDisplayMetrics().widthPixels);
        params.leftMargin = Math.max(dp(20), Math.min(width - dp(130), dp(120 + index * 88 + (highlightEffectSeed % 3) * 26)));
        params.topMargin = dp(520 + index * 48);
        activeHighlightEffectLayer.addView(word, params);
        word.animate()
                .alpha(1f)
                .scaleX(index == 2 ? 1.18f : 1f)
                .scaleY(index == 2 ? 1.18f : 1f)
                .translationYBy(-dp(34 + index * 10))
                .setDuration(170)
                .withEndAction(() -> word.animate()
                        .alpha(0f)
                        .translationYBy(-dp(46))
                        .setStartDelay(420)
                        .setDuration(360)
                        .withEndAction(() -> {
                            if (activeHighlightEffectLayer != null) {
                                activeHighlightEffectLayer.removeView(word);
                            }
                        })
                        .start())
                .start();
    }

    private boolean isHeartHighlight(String highlightType) {
        String type = highlightType == null ? "" : highlightType;
        return type.contains("甜") || type.contains("爱情") || type.contains("心动");
    }

    private void submitInteraction(int highlightId, String optionKey, String label) {
        if (activeHighlightPanel == null) {
            return;
        }
        activeHighlightPanel.removeAllViews();
        renderHighlightFeedback("已选择 " + label, "正在同步到互动榜", true);

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("highlight_id", highlightId);
                payload.put("option_key", optionKey);
                payload.put("session_id", loadSessionId());
                httpPost(loadBaseUrl() + "/api/interactions", payload.toString(), loadToken());
                postWatchRoomInteractionEvent(highlightId, optionKey, label);
                runOnUiThread(() -> showInteractionFeedback("已选择：" + label + "，已上报。"));
            } catch (Exception error) {
                runOnUiThread(() -> showInteractionFeedback("已选择：" + label + "，上报失败。"));
            }
        }).start();
    }

    private void postWatchRoomInteractionEvent(int highlightId, String optionKey, String label) {
        if (activePlayerRoomCode.isEmpty()) {
            return;
        }
        try {
            JSONObject payload = new JSONObject();
            payload.put("highlight_id", highlightId);
            payload.put("option_key", optionKey);
            payload.put("label", label);
            payload.put("episode_id", activeEpisodeId);
            payload.put("source", "native_android");
            JSONObject body = new JSONObject();
            body.put("event_type", "interaction");
            body.put("payload", payload);
            httpPost(loadBaseUrl() + "/api/watch-rooms/" + activePlayerRoomCode + "/events", body.toString(), loadToken());
        } catch (Exception ignored) {
            // 房间事件失败不影响主互动上报。
        }
    }

    private void showInteractionFeedback(String message) {
        showInteractionFeedback(message, "同看房间会同步这次互动");
    }

    private void showInteractionFeedback(String message, String detailText) {
        if (activeHighlightPanel == null) {
            return;
        }
        activeHighlightPanel.removeAllViews();
        renderHighlightFeedback(message, detailText, true);
        progressHandler.postDelayed(this::hideHighlightAndScheduleNext, 2400);
    }

    private void renderHighlightFeedback(String titleText, String detailText, boolean success) {
        activeHighlightPanel.setBackground(highlightFeedbackBackground(success));
        TextView burst = text(success ? "+1" : "!", 28, success ? Color.rgb(10, 132, 80) : Color.rgb(210, 54, 70), Typeface.BOLD);
        burst.setGravity(Gravity.CENTER);
        activeHighlightPanel.addView(burst, matchWrap());

        TextView title = text(titleText, 18, Color.rgb(18, 20, 26), Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(4);
        activeHighlightPanel.addView(title, titleParams);

        TextView detail = text(detailText, 12, Color.rgb(88, 98, 118), Typeface.NORMAL);
        detail.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailParams = matchWrap();
        detailParams.topMargin = dp(4);
        activeHighlightPanel.addView(detail, detailParams);
        animatePanel(activeHighlightPanel);
    }

    private void hideHighlightAndScheduleNext() {
        stopHighlightTapWatcher();
        if (activeHighlightPanel != null) {
            activeHighlightPanel.setVisibility(View.GONE);
        }
        activeHighlightId = -1;
        activeHighlightTapCount = 0;
        activeHighlightTapSubmitted = false;
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

    private String httpPatch(String urlString, String jsonBody, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("PATCH");
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

    private String httpMultipartVoiceProfile(String urlString, String token, String fileName, String contentType, byte[] fileData) throws Exception {
        String boundary = "----banju-native-" + UUID.randomUUID();
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (!token.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }

        OutputStream output = connection.getOutputStream();
        writeUtf8(output, "--" + boundary + "\r\n");
        writeUtf8(output, "Content-Disposition: form-data; name=\"consent_text\"\r\n\r\n");
        writeUtf8(output, VOICE_CONSENT_TEXT + "\r\n");

        writeUtf8(output, "--" + boundary + "\r\n");
        writeUtf8(output, "Content-Disposition: form-data; name=\"voice_sample\"; filename=\"" + safeMultipartFileName(fileName) + "\"\r\n");
        writeUtf8(output, "Content-Type: " + contentType + "\r\n\r\n");
        output.write(fileData);
        writeUtf8(output, "\r\n--" + boundary + "--\r\n");
        output.close();
        return readResponse(connection);
    }

    private String httpMultipartAvatar(String urlString, String token, byte[] avatarBytes) throws Exception {
        String boundary = "----banju-avatar-" + UUID.randomUUID();
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (!token.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        OutputStream output = connection.getOutputStream();
        writeUtf8(output, "--" + boundary + "\r\n");
        writeUtf8(output, "Content-Disposition: form-data; name=\"avatar\"; filename=\"avatar_crop.jpg\"\r\n");
        writeUtf8(output, "Content-Type: image/jpeg\r\n\r\n");
        output.write(avatarBytes);
        writeUtf8(output, "\r\n--" + boundary + "--\r\n");
        output.close();
        return readResponse(connection);
    }

    private void writeUtf8(OutputStream output, String value) throws Exception {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] readUriBytes(Uri uri) throws Exception {
        InputStream stream = getContentResolver().openInputStream(uri);
        if (stream == null) {
            throw new IllegalStateException("无法读取音频文件");
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        stream.close();
        return buffer.toByteArray();
    }

    private byte[] readFileBytes(File file) throws Exception {
        InputStream stream = new FileInputStream(file);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        stream.close();
        return buffer.toByteArray();
    }

    private String displayNameForUri(Uri uri) {
        String fallback = uri.getLastPathSegment();
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.trim().isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
            // 文件名只影响展示和后端扩展名判断，失败时使用 fallback。
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return fallback == null || fallback.trim().isEmpty() ? "voice_sample.wav" : fallback;
    }

    private String safeMultipartFileName(String fileName) {
        return fileName.replace("\\", "_").replace("/", "_").replace("\"", "_");
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

    private void saveDisplayName(String displayName) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
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

    private Button highlightOptionButton(String label, String highlightType, int index) {
        Button button = new Button(this);
        button.setText((index + 1) + "  " + label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setBackground(highlightOptionBackground(highlightType, index));
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
        return highlightBackground("");
    }

    private GradientDrawable highlightBackground(String highlightType) {
        int accent = highlightAccentColor(highlightType, false);
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(255, 255, 255),
                        Color.argb(255, 242, 248, 255),
                        Color.argb(255, Color.red(accent), Color.green(accent), Color.blue(accent))
                }
        );
        drawable.setCornerRadius(dp(22));
        drawable.setStroke(dp(1), Color.argb(78, Color.red(accent), Color.green(accent), Color.blue(accent)));
        return drawable;
    }

    private GradientDrawable highlightGlyphBackground(String highlightType) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        highlightAccentColor(highlightType, true),
                        highlightAccentColor(highlightType, false)
                }
        );
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), Color.argb(118, 255, 255, 255));
        return drawable;
    }

    private GradientDrawable highlightOptionBackground(String highlightType, int index) {
        int first = highlightAccentColor(highlightType, true);
        int second = highlightAccentColor(highlightType, false);
        if (index % 2 == 1) {
            first = Color.rgb(28, 45, 76);
            second = highlightAccentColor(highlightType, true);
        }
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{first, second}
        );
        drawable.setCornerRadius(dp(17));
        drawable.setStroke(dp(1), Color.argb(80, 255, 255, 255));
        return drawable;
    }

    private GradientDrawable highlightImpactPadBackground(String highlightType, boolean hot) {
        int first = hot ? highlightAccentColor(highlightType, true) : Color.rgb(20, 24, 36);
        int second = hot ? Color.rgb(255, 216, 106) : highlightAccentColor(highlightType, true);
        int third = highlightAccentColor(highlightType, false);
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(246, Color.red(first), Color.green(first), Color.blue(first)),
                        Color.argb(238, Color.red(second), Color.green(second), Color.blue(second)),
                        Color.argb(226, Color.red(third), Color.green(third), Color.blue(third))
                }
        );
        drawable.setCornerRadius(dp(22));
        drawable.setStroke(dp(1), Color.argb(hot ? 150 : 96, 255, 255, 255));
        return drawable;
    }

    private GradientDrawable highlightTapCounterBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(232, 12, 16, 28), Color.argb(218, 45, 58, 92)}
        );
        drawable.setCornerRadius(dp(19));
        drawable.setStroke(dp(1), Color.argb(96, 255, 255, 255));
        return drawable;
    }

    private GradientDrawable highlightFeedbackBackground(boolean success) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                success
                        ? new int[]{Color.rgb(255, 255, 255), Color.rgb(235, 252, 243), Color.rgb(239, 247, 255)}
                        : new int[]{Color.rgb(255, 255, 255), Color.rgb(255, 240, 242), Color.rgb(245, 248, 255)}
        );
        drawable.setCornerRadius(dp(22));
        drawable.setStroke(dp(1), success ? Color.argb(80, 10, 132, 80) : Color.argb(90, 210, 54, 70));
        return drawable;
    }

    private GradientDrawable highlightStickerBackground(String highlightType, boolean burst, int index) {
        int first = burst ? Color.rgb(18, 20, 26) : highlightAccentColor(highlightType, true);
        int second = highlightAccentColor(highlightType, false);
        if (!burst && index % 2 == 1) {
            first = Color.rgb(30, 38, 58);
        }
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.argb(burst ? 235 : 225, Color.red(first), Color.green(first), Color.blue(first)),
                        Color.argb(218, Color.red(second), Color.green(second), Color.blue(second))}
        );
        drawable.setCornerRadius(dp(19));
        drawable.setStroke(dp(1), Color.argb(112, 255, 255, 255));
        return drawable;
    }

    private int highlightAccentColor(String highlightType, boolean strong) {
        String type = highlightType == null ? "" : highlightType;
        if (type.contains("甜") || type.contains("爱情") || type.contains("心动")) {
            return strong ? Color.rgb(255, 86, 128) : Color.rgb(255, 151, 174);
        }
        if (type.contains("悲") || type.contains("虐") || type.contains("感动")) {
            return strong ? Color.rgb(44, 113, 210) : Color.rgb(105, 162, 233);
        }
        if (type.contains("反转") || type.contains("悬疑") || type.contains("悬念")) {
            return strong ? Color.rgb(94, 71, 214) : Color.rgb(144, 121, 238);
        }
        if (type.contains("冲突") || type.contains("高能") || type.contains("爽")) {
            return strong ? Color.rgb(255, 116, 58) : Color.rgb(255, 161, 83);
        }
        if (type.contains("搞笑") || type.contains("好笑")) {
            return strong ? Color.rgb(247, 178, 34) : Color.rgb(255, 204, 86);
        }
        return strong ? Color.rgb(10, 102, 255) : Color.rgb(88, 142, 255);
    }

    private String highlightGlyph(String highlightType) {
        String type = highlightType == null ? "" : highlightType;
        if (type.contains("甜") || type.contains("爱情") || type.contains("心动")) {
            return "甜";
        }
        if (type.contains("悲") || type.contains("虐") || type.contains("感动")) {
            return "泪";
        }
        if (type.contains("反转")) {
            return "反";
        }
        if (type.contains("悬疑") || type.contains("悬念")) {
            return "?";
        }
        if (type.contains("冲突")) {
            return "冲";
        }
        if (type.contains("搞笑") || type.contains("好笑")) {
            return "笑";
        }
        if (type.contains("高能") || type.contains("爽")) {
            return "燃";
        }
        return "高";
    }

    private GradientDrawable remixOverlayBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.argb(246, 255, 255, 255),
                        Color.argb(240, 241, 247, 255),
                        Color.argb(242, 255, 246, 232)
                }
        );
        drawable.setCornerRadius(dp(30));
        drawable.setStroke(dp(1), Color.argb(120, 255, 255, 255));
        return drawable;
    }

    private GradientDrawable remixChoiceBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(235, 255, 255, 255),
                        Color.argb(220, 236, 244, 255)
                }
        );
        drawable.setCornerRadius(dp(20));
        drawable.setStroke(dp(1), Color.argb(54, 20, 26, 38));
        return drawable;
    }

    private GradientDrawable remixBadgeBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.rgb(10, 102, 255),
                        Color.rgb(255, 126, 67)
                }
        );
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), Color.argb(92, 255, 255, 255));
        return drawable;
    }

    private GradientDrawable remixStageBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(9, 13, 24),
                        Color.rgb(28, 45, 76),
                        Color.rgb(255, 244, 232)
                }
        );
        drawable.setCornerRadius(dp(28));
        drawable.setStroke(dp(1), Color.argb(120, 255, 255, 255));
        return drawable;
    }

    private GradientDrawable remixFloatingBadgeBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(225, 8, 15, 30),
                        Color.argb(210, 40, 61, 98)
                }
        );
        drawable.setCornerRadius(dp(16));
        drawable.setStroke(dp(1), Color.argb(96, 255, 255, 255));
        return drawable;
    }

    private GradientDrawable remixCaptionBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(218, 8, 15, 30),
                        Color.argb(198, 28, 45, 76)
                }
        );
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), Color.argb(80, 255, 255, 255));
        return drawable;
    }

    private GradientDrawable panelBadgeBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(230, 238, 246, 255),
                        Color.argb(220, 255, 244, 232)
                }
        );
        drawable.setCornerRadius(dp(15));
        drawable.setStroke(dp(1), Color.argb(58, 20, 26, 38));
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

    private GradientDrawable chatBubbleBackground(boolean outgoing) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                outgoing
                        ? new int[]{Color.rgb(10, 102, 255), Color.rgb(0, 71, 198)}
                        : new int[]{Color.argb(230, 255, 255, 255), Color.argb(215, 246, 249, 254)}
        );
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), outgoing ? Color.argb(60, 255, 255, 255) : Color.argb(46, 20, 26, 38));
        return drawable;
    }

    private GradientDrawable emojiBubbleBackground(boolean outgoing) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                outgoing
                        ? new int[]{Color.rgb(255, 122, 74), Color.rgb(255, 72, 118)}
                        : new int[]{Color.rgb(255, 247, 220), Color.rgb(236, 246, 255)}
        );
        drawable.setCornerRadius(dp(22));
        drawable.setStroke(dp(1), outgoing ? Color.argb(70, 255, 255, 255) : Color.argb(56, 20, 26, 38));
        return drawable;
    }

    private GradientDrawable roomEventBubbleBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(220, 0, 122, 255),
                        Color.argb(214, 88, 86, 214)
                }
        );
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), Color.argb(96, 255, 255, 255));
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
