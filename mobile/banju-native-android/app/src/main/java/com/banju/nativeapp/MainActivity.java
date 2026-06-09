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
    private static final String VOICE_CONSENT_TEXT = "同意利用录入声音生成音频";
    private static final String VOICE_PREVIEW_TEXT = "片尾拓展已开启，我会用你的声音陪你猜下一段剧情。";

    private EditText baseUrlInput;
    private EditText usernameInput;
    private EditText passwordInput;
    private TextView messageText;
    private Button loginButton;
    private LinearLayout dramaList;
    private LinearLayout profileContent;
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
    private String activeRoomCode = "";
    private String activePlayerRoomCode = "";
    private VideoView activeVideoView;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;
    private Runnable danmakuRunnable;
    private Runnable remixRunnable;
    private Runnable roomSyncRunnable;
    private Runnable roomEventsRunnable;
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
    private int lastRoomEventId;
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VOICE_SAMPLE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            uploadVoiceSample(data.getData());
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
                JSONObject rewards = new JSONObject(httpGet(loadBaseUrl() + "/api/users/me/rewards", loadToken()));
                JSONObject voice = new JSONObject(httpGet(loadBaseUrl() + "/api/users/me/voice-profile", loadToken()));
                runOnUiThread(() -> renderProfileSummary(rewards, voice));
            } catch (Exception error) {
                runOnUiThread(() -> setMessage("账号状态加载失败：" + error.getMessage(), false));
            }
        }).start();
    }

    private void renderProfileSummary(JSONObject rewards, JSONObject voice) {
        if (profileContent == null) {
            return;
        }
        profileContent.removeAllViews();
        setMessage("账号状态已同步。", true);

        int points = rewards.optInt("points", 0);
        String title = rewards.optString("title", "剧情新人");
        int unlocked = rewards.optInt("collection_unlocked", 0);
        int total = rewards.optInt("collection_total", 0);
        double completion = rewards.optDouble("completion_percent", 0);
        JSONArray badges = rewards.optJSONArray("badges");

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
        JSONObject payload = event.optJSONObject("payload");
        String content = payload == null ? "" : payload.optString("text", payload.toString());
        if (content.isEmpty()) {
            content = event.optString("event_type", "房间动态");
        }
        TextView item = text(userName(user) + "：" + content, 13, Color.rgb(28, 45, 76), Typeface.NORMAL);
        LinearLayout.LayoutParams itemParams = matchWrap();
        itemParams.topMargin = dp(8);
        parent.addView(item, itemParams);
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
        showNativePlayer(dramaTitle, firstEpisodeId, "");
    }

    private void showNativePlayer(String dramaTitle, int firstEpisodeId, String roomCode) {
        stopActiveVideo();
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
            updatePlayerStatus();
            videoView.setBackgroundColor(Color.TRANSPARENT);
            videoView.start();
            scheduleNextHighlight();
            scheduleDanmakuTrack();
            scheduleRemixEntry();
            scheduleWatchRoomSync();
            scheduleWatchRoomEvents();
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
        stopWatchRoomSync();
        stopWatchRoomEvents();
        stopRemixAudio();
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
                httpPost(loadBaseUrl() + "/api/watch-rooms/" + roomCode + "/sync", payload.toString(), loadToken());
            } catch (Exception ignored) {
                // 同看同步是辅助能力，失败不打断播放。
            }
        }).start();
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
            if (i >= start) {
                showWatchRoomEventBubble(event);
            }
        }
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
        TextView bubble = text("同看 · " + userName(user) + "：" + content, 12, Color.WHITE, Typeface.BOLD);
        bubble.setSingleLine(true);
        bubble.setPadding(dp(12), dp(7), dp(12), dp(7));
        bubble.setBackground(roomEventBubbleBackground());
        bubble.setAlpha(0f);
        bubble.setTranslationX(dp(24));
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

    private String roomEventText(JSONObject event) {
        String type = event.optString("event_type", "danmaku");
        JSONObject payload = event.optJSONObject("payload");
        if (payload == null) {
            return "更新了房间动态";
        }
        if ("interaction".equals(type)) {
            String label = payload.optString("label", payload.optString("option_label", ""));
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
        videoPrepared = false;
        highlightTimeline = new JSONArray();
        danmakuTimeline = new JSONArray();
        remixOptionsPayload = null;
        nextHighlightIndex = 0;
        nextDanmakuIndex = 0;
        lastRoomEventId = 0;
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
        activePlayerRoomCode = "";
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
        if (!activePlayerRoomCode.isEmpty()) {
            bubble.setOnClickListener(v -> showDanmakuActionPanel(comment));
        }
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

    private void showDanmakuActionPanel(JSONObject comment) {
        if (activeHighlightPanel == null || activePlayerRoomCode.isEmpty()) {
            return;
        }
        JSONObject user = comment.optJSONObject("user");
        String nickname = user == null ? "观众" : user.optString("nickname", "观众");
        String text = comment.optString("text", "");
        activeHighlightPanel.removeAllViews();

        activeHighlightPanel.addView(text("弹幕互动", 12, Color.rgb(83, 103, 160), Typeface.BOLD), matchWrap());
        TextView title = text(nickname + " 的弹幕", 18, Color.rgb(18, 20, 26), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(6);
        activeHighlightPanel.addView(title, titleParams);

        TextView body = text(text, 13, Color.rgb(88, 98, 118), Typeface.NORMAL);
        body.setMaxLines(2);
        LinearLayout.LayoutParams bodyParams = matchWrap();
        bodyParams.topMargin = dp(8);
        activeHighlightPanel.addView(body, bodyParams);

        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams firstRowParams = matchWrap();
        firstRowParams.topMargin = dp(12);
        activeHighlightPanel.addView(firstRow, firstRowParams);

        Button likeButton = primaryButton("点赞");
        likeButton.setOnClickListener(v -> submitDanmakuRoomEvent("danmaku_like", comment, ""));
        firstRow.addView(likeButton, weightHeight(1, dp(42)));

        Button agreeButton = primaryButton("同感");
        agreeButton.setOnClickListener(v -> submitDanmakuRoomEvent("danmaku_reply", comment, "同感"));
        LinearLayout.LayoutParams agreeParams = weightHeight(1, dp(42));
        agreeParams.leftMargin = dp(8);
        firstRow.addView(agreeButton, agreeParams);

        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams secondRowParams = matchWrap();
        secondRowParams.topMargin = dp(8);
        activeHighlightPanel.addView(secondRow, secondRowParams);

        Button laughButton = secondaryButton("哈哈哈");
        laughButton.setOnClickListener(v -> submitDanmakuRoomEvent("danmaku_reply", comment, "哈哈哈"));
        secondRow.addView(laughButton, weightHeight(1, dp(40)));

        Button closeButton = secondaryButton("关闭");
        closeButton.setOnClickListener(v -> activeHighlightPanel.setVisibility(View.GONE));
        LinearLayout.LayoutParams closeParams = weightHeight(1, dp(40));
        closeParams.leftMargin = dp(8);
        secondRow.addView(closeButton, closeParams);

        activeHighlightPanel.setVisibility(View.VISIBLE);
        activeHighlightPanel.bringToFront();
        animatePanel(activeHighlightPanel);
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
                String message = "danmaku_like".equals(eventType) ? "已点赞弹幕。" : "已回复弹幕。";
                runOnUiThread(() -> showInteractionFeedback(message));
            } catch (Exception error) {
                runOnUiThread(() -> showInteractionFeedback("弹幕互动同步失败。"));
            }
        }).start();
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

        LinearLayout publishRow = new LinearLayout(this);
        publishRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams publishRowParams = matchWrap();
        publishRowParams.topMargin = dp(8);
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
                httpPost(loadBaseUrl() + "/api/social/posts", payload.toString(), loadToken());
                runOnUiThread(() -> status.setText("已发布到逛逛"));
            } catch (Exception error) {
                runOnUiThread(() -> status.setText("发布失败：" + error.getMessage()));
            }
        }).start();
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
                String audioText = result.optString("text", shot.optString("audio_text", shot.optString("subtitle", "")));
                runOnUiThread(() -> {
                    playRemixAudio(audioUrl, status);
                    addRemixVoicePublishButton(imagePlan, shot, voiceMode, audioUrl, audioText, status);
                });
            } catch (Exception error) {
                runOnUiThread(() -> status.setText("声音暂不可用：" + error.getMessage()));
            }
        }).start();
    }

    private void addRemixVoicePublishButton(JSONObject imagePlan, JSONObject shot, String voiceMode, String audioUrl, String audioText, TextView status) {
        if (activeRemixPanel == null || audioUrl.isEmpty()) {
            return;
        }
        Button publishButton = secondaryButton("发" + ("user".equals(voiceMode) ? "我的声音" : "原声音频"));
        publishButton.setOnClickListener(v -> publishRemixVoicePost(imagePlan, shot, voiceMode, audioUrl, audioText, status));
        LinearLayout.LayoutParams publishParams = matchHeight(dp(38));
        publishParams.topMargin = dp(8);
        activeRemixPanel.addView(publishButton, publishParams);
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
                httpPost(loadBaseUrl() + "/api/social/posts", payload.toString(), loadToken());
                runOnUiThread(() -> status.setText("声音已发布到逛逛"));
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
