package com.orgista.openstream;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.orgista.openstream.media.HdrCapabilityProbe;
import com.orgista.openstream.media.MediaCapabilityReport;
import com.orgista.openstream.media.SelectedTrackInspector;
import com.orgista.openstream.xr.ImmersiveMediaDescriptor;
import com.orgista.openstream.xr.ImmersiveVideoBackend;
import com.orgista.openstream.xr.WebXrBackend;
import com.orgista.openstream.xr.XrRuntimeProbe;
import com.orgista.openstream.xr.XrRuntimeReport;

import java.util.Locale;

@OptIn(markerClass = UnstableApi.class)
public final class MainActivity extends Activity {
    private static final int WHITE = Color.rgb(247, 248, 247);
    private static final int MUTED = Color.rgb(191, 198, 195);
    private static final int MINT = Color.rgb(169, 244, 197);
    private static final int AMBER = Color.rgb(248, 197, 114);

    private FrameLayout root;
    private PlayerView playerView;
    private ExoPlayer player;
    private TextView playbackStatus;
    private MediaCapabilityReport mediaCapabilities;
    private XrRuntimeReport xrRuntime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mediaCapabilities = HdrCapabilityProbe.inspect(this);
        xrRuntime = XrRuntimeProbe.inspect(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(5, 6, 6));
        setContentView(root);
        // After setContentView: the window has no DecorView before content is
        // installed, and getInsetsController() NPEs on Android 14.
        hideSystemUi();
        showBrowseScreen();
    }

    @Override
    protected void onStop() {
        super.onStop();
        releasePlayer();
    }

    private void hideSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    private void showBrowseScreen() {
        releasePlayer();
        root.removeAllViews();

        ImageView hero = new ImageView(this);
        hero.setImageResource(R.drawable.northstar_hero);
        hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(hero, matchParent());

        View shade = new View(this);
        shade.setBackgroundColor(Color.argb(110, 0, 0, 0));
        root.addView(shade, matchParent());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(58), dp(36), dp(58), dp(38));
        root.addView(content, matchParent());

        content.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
        ));

        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout heroCopy = new LinearLayout(this);
        heroCopy.setOrientation(LinearLayout.VERTICAL);
        heroCopy.setGravity(Gravity.START);
        heroCopy.setPadding(0, 0, 0, dp(8));
        content.addView(heroCopy, new LinearLayout.LayoutParams(dp(690), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView eyebrow = text("OPENSTREAM ORIGINAL  ·  2026  ·  2H 14M", 14, AMBER, Typeface.BOLD);
        heroCopy.addView(eyebrow);

        TextView title = text("Northstar", 64, WHITE, Typeface.BOLD);
        title.setPadding(0, dp(8), 0, dp(8));
        heroCopy.addView(title);

        TextView summary = text(
                "A signal from the edge of the solar system draws a salvage crew into an impossible orbit.",
                18,
                WHITE,
                Typeface.NORMAL
        );
        summary.setMaxLines(3);
        summary.setLineSpacing(0, 1.15f);
        heroCopy.addView(summary, new LinearLayout.LayoutParams(dp(650), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout badges = new LinearLayout(this);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        badges.setPadding(0, dp(16), 0, dp(18));
        addBadge(badges, "4K");
        addBadge(badges, "HDR10+");
        addBadge(badges, "DOLBY VISION");
        addBadge(badges, "ATMOS");
        heroCopy.addView(badges);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button play = actionButton("Play", true);
        play.setOnClickListener(view -> playDemo());
        actions.addView(play, new LinearLayout.LayoutParams(dp(144), dp(52)));

        Button immersive = actionButton("Immersive 180", false);
        LinearLayout.LayoutParams immersiveParams = new LinearLayout.LayoutParams(dp(196), dp(52));
        immersiveParams.setMarginStart(dp(12));
        actions.addView(immersive, immersiveParams);
        immersive.setOnClickListener(view -> openImmersiveViewer());

        Button inspect = actionButton("Capabilities", false);
        LinearLayout.LayoutParams inspectParams = new LinearLayout.LayoutParams(dp(176), dp(52));
        inspectParams.setMarginStart(dp(12));
        actions.addView(inspect, inspectParams);
        inspect.setOnClickListener(view -> showCapabilities());
        heroCopy.addView(actions);
        play.requestFocus();

        playbackStatus = text(xrRuntime.runtimeLabel(), 14, MUTED, Typeface.NORMAL);
        playbackStatus.setPadding(0, dp(14), 0, 0);
        heroCopy.addView(playbackStatus);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.openstream_mark);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        bar.addView(logo, new LinearLayout.LayoutParams(dp(46), dp(46)));

        TextView brand = text("OpenStream", 22, WHITE, Typeface.BOLD);
        brand.setPadding(dp(10), 0, dp(44), 0);
        bar.addView(brand);

        String[] destinations = {"Discover", "Library", "Live", "Connections"};
        for (String destination : destinations) {
            Button nav = actionButton(destination, "Discover".equals(destination));
            LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(44)
            );
            navParams.setMarginEnd(dp(8));
            bar.addView(nav, navParams);
        }

        View gap = new View(this);
        bar.addView(gap, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView runtime = text(xrRuntime.openXr11() ? "OPENXR 1.1" : "UNIVERSAL", 12, MINT, Typeface.BOLD);
        runtime.setPadding(dp(14), dp(8), dp(14), dp(8));
        runtime.setBackground(panelDrawable(Color.argb(178, 17, 25, 22), MINT, dp(4)));
        bar.addView(runtime);
        return bar;
    }

    private void playDemo() {
        root.removeAllViews();
        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setUseController(true);
        root.addView(playerView, matchParent());

        ImageButton close = new ImageButton(this);
        close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        close.setContentDescription("Close player");
        close.setBackground(panelDrawable(Color.argb(205, 14, 17, 16), WHITE, dp(4)));
        close.setColorFilter(WHITE);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(52), dp(52));
        closeParams.gravity = Gravity.TOP | Gravity.END;
        closeParams.setMargins(0, dp(28), dp(28), 0);
        root.addView(close, closeParams);
        close.setOnClickListener(view -> showBrowseScreen());

        playbackStatus = text("Inspecting selected track...", 14, WHITE, Typeface.BOLD);
        playbackStatus.setPadding(dp(14), dp(9), dp(14), dp(9));
        playbackStatus.setBackground(panelDrawable(Color.argb(218, 11, 15, 14), Color.TRANSPARENT, dp(4)));
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.gravity = Gravity.TOP | Gravity.START;
        statusParams.setMargins(dp(28), dp(28), 0, 0);
        root.addView(playbackStatus, statusParams);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onTracksChanged(Tracks tracks) {
                updateSelectedTrack(tracks);
            }
        });
        Uri demo = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.openstream_demo);
        player.setMediaItem(MediaItem.fromUri(demo));
        player.prepare();
        player.play();
        close.requestFocus();
    }

    private void updateSelectedTrack(Tracks tracks) {
        for (Tracks.Group group : tracks.getGroups()) {
            for (int index = 0; index < group.length; index++) {
                if (!group.isTrackSelected(index)) {
                    continue;
                }
                Format format = group.getTrackFormat(index);
                if (format.width <= 0 && format.height <= 0) {
                    continue;
                }
                SelectedTrackInspector.TrackResult result = SelectedTrackInspector.inspect(format);
                String dimensions = result.width() > 0
                        ? String.format(Locale.US, "%dx%d", result.width(), result.height())
                        : "adaptive";
                playbackStatus.setText(getString(
                        R.string.playback_status,
                        dimensions,
                        result.dynamicRange(),
                        result.dolbyVisionProfile()
                ));
                return;
            }
        }
    }

    private void openImmersiveViewer() {
        ImmersiveVideoBackend backend = new WebXrBackend(xrRuntime);
        if (!backend.isAvailable(this)) {
            showMessage(
                    "Immersive runtime unavailable",
                    "This device is running the 2D compatibility target. OpenStream will not claim headset playback until an OpenXR runtime is present."
            );
            return;
        }
        ImmersiveMediaDescriptor descriptor = new ImmersiveMediaDescriptor(
                Uri.EMPTY,
                ImmersiveMediaDescriptor.Projection.EQUIRECTANGULAR_180,
                ImmersiveMediaDescriptor.StereoMode.SIDE_BY_SIDE,
                "runtime-check"
        );
        ImmersiveVideoBackend.LaunchResult result = backend.launch(this, descriptor);
        if (!result.launched()) {
            showMessage("Unable to open immersive viewer", result.message());
        }
    }

    private void showCapabilities() {
        StringBuilder report = new StringBuilder();
        report.append(xrRuntime.runtimeLabel()).append("\n\n");
        for (MediaCapabilityReport.DynamicRange range : MediaCapabilityReport.DynamicRange.values()) {
            report.append(range.name().replace('_', '+'))
                    .append(": ")
                    .append(mediaCapabilities.playableState(range).name().replace('_', ' '))
                    .append('\n');
        }
        report.append("\nHEVC Main10 decoder: ")
                .append(mediaCapabilities.hasHevcMain10Decoder() ? "yes" : "no");
        report.append("\nAV1 Main10 decoder: ")
                .append(mediaCapabilities.hasAv1Main10Decoder() ? "yes" : "no");
        report.append("\nDolby Vision codec profiles: ")
                .append(mediaCapabilities.dolbyVisionCodecProfiles().isEmpty()
                        ? "none reported"
                        : String.join(", ", mediaCapabilities.dolbyVisionCodecProfiles()));
        showMessage("Runtime media capabilities", report.toString());
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Done", null)
                .show();
    }

    private Button actionButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(primary ? Color.rgb(5, 8, 7) : WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setBackground(panelDrawable(
                primary ? WHITE : Color.argb(190, 18, 22, 21),
                primary ? Color.TRANSPARENT : Color.argb(120, 255, 255, 255),
                dp(4)
        ));
        button.setOnFocusChangeListener((view, focused) -> {
            Button focusedButton = (Button) view;
            focusedButton.setScaleX(focused ? 1.06f : 1f);
            focusedButton.setScaleY(focused ? 1.06f : 1f);
            focusedButton.setTranslationZ(focused ? dp(8) : 0);
            if (!primary) {
                focusedButton.setBackground(panelDrawable(
                        focused ? WHITE : Color.argb(190, 18, 22, 21),
                        focused ? Color.TRANSPARENT : Color.argb(120, 255, 255, 255),
                        dp(4)
                ));
                focusedButton.setTextColor(focused ? Color.rgb(5, 8, 7) : WHITE);
            }
        });
        return button;
    }

    private void addBadge(LinearLayout parent, String label) {
        TextView badge = text(label, 11, WHITE, Typeface.BOLD);
        badge.setPadding(dp(9), dp(5), dp(9), dp(5));
        badge.setBackground(panelDrawable(Color.argb(145, 15, 20, 18), Color.argb(105, 255, 255, 255), dp(3)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMarginEnd(dp(7));
        parent.addView(badge, params);
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setTypeface(Typeface.DEFAULT, style);
        text.setLetterSpacing(0);
        return text;
    }

    private GradientDrawable panelDrawable(int fillColor, int strokeColor, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        if (strokeColor != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
        if (playerView != null) {
            playerView.setPlayer(null);
            playerView = null;
        }
    }
}
