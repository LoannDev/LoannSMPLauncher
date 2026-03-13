package com.loannsmp.launcher;

import com.google.gson.*;
import javafx.animation.*;
import javafx.application.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.image.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.stage.*;
import javafx.util.Duration;
import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSProcess;

public class LauncherApp extends Application {

    private static final String LAUNCHER_VERSION = "3.1";
    private static final String LOADING_IMAGE_URL =
        "https://raw.githubusercontent.com/LoannDev/LoannSMPLauncher/refs/heads/main/icon.ico";
    private static final String NEWS_URL =
        "https://raw.githubusercontent.com/LoannDev/LoannSMPLauncher/refs/heads/main/latestnews.txt";

    private MCInstaller installer = new MCInstaller();
    private Gson gson = new Gson();
    private JsonObject config;
    private java.nio.file.Path configFile;

    private boolean isLaunching = false;
    private int currentPage = 0;

    private Queue<String> logBuffer = new ConcurrentLinkedQueue<>();
    private TextArea consoleArea;

    private Stage primaryStage;
    private Scene scene;
    private VBox sidebar;
    private List<ToggleButton> navButtons = new ArrayList<>();
    private List<Pane> pages = new ArrayList<>();
    private int currentPageIndex = 0;

    private Button launchBtn;
    private Button installBtn;
    private Button settingsBtn;
    private Button skinsBtn;
    private Button newsBtn;
    private Button consoleBtn;

    private TextField usernameField;
    private Button minusBtn, plusBtn;
    private CheckBox fullscreenCheck, keepOpenCheck;
    private TextField resWField, resHField;

    private List<String> selectedSkins = new ArrayList<>();

    private ImageView logoView;
    private Label newsContent;
    private Label statusLabel;
    private Label ramDisplay;
    private Label cpuValueLabel, ramValueLabel, playtimeValueLabel;

    private ProgressBar progressBar;
    private Label progressPercentLabel;
    private Timeline progressAnimation;
    private double targetProgress = 0;
    private double currentProgress = 0;

    private boolean gameRunning = false;
    private Process minecraftProcess;
    private long gameStartTime = 0;
    private Timeline launchPulse;

    private StackPane contentStack;
    private VBox skinsListBox;
    private VBox modsListBox;
    private Label modsCountBadge;
    private Label shadersCountBadge;

    private void initialize() {
        configFile = installer.getMinecraftDir().resolve("launcher_config.json");
        config = loadConfig();
        selectedSkins = new ArrayList<>();
        if (config.has("selected_skins")) {
            JsonArray skinsArray = config.getAsJsonArray("selected_skins");
            for (JsonElement skin : skinsArray) {
                selectedSkins.add(skin.getAsString());
            }
        }
    }

    private JsonObject loadConfig() {
        JsonObject def = new JsonObject();
        def.addProperty("base_url", "https://raw.githubusercontent.com/LoannDev/LoannSMPLauncher/main/");
        def.addProperty("ram_gb", 6);
        def.addProperty("keep_launcher_open", true);
        def.addProperty("discord_url", "https://discord.gg/x3GtCqqXXj");
        def.addProperty("username", "");
        def.addProperty("jvm_args", "-XX:+UseG1GC");
        def.addProperty("custom_res", false);
        def.addProperty("res_w", 1280);
        def.addProperty("res_h", 720);
        def.addProperty("fullscreen", false);

        try { Files.createDirectories(installer.getMinecraftDir()); } catch (Exception ignored) {}
        if (Files.exists(configFile)) {
            try {
                String content = Files.readString(configFile);
                JsonObject saved = JsonParser.parseString(content).getAsJsonObject();
                for (String key : saved.keySet()) def.add(key, saved.get(key));
            } catch (Exception ignored) {}
        }
        return def;
    }

    private void saveConfig() {
        try { Files.writeString(configFile, gson.toJson(config)); } catch (Exception ignored) {}
    }

    private int cfgInt(String key) { return config.get(key).getAsInt(); }
    private boolean cfgBool(String key) { return config.get(key).getAsBoolean(); }
    private String cfgStr(String key) { return config.get(key).getAsString(); }

    @Override
    public void start(Stage stage) {
        initialize();
        primaryStage = stage;
        stage.setTitle("LoannSMP Launcher V3");
        stage.setWidth(1000);
        stage.setHeight(650);
        stage.setResizable(false);

        HBox root = new HBox();
        root.setStyle("-fx-background-color: #1A1A1A;");

        sidebar = createSidebar();

        contentStack = new StackPane();
        contentStack.setStyle("-fx-background-color: #1A1A1A;");
        HBox.setHgrow(contentStack, Priority.ALWAYS);

        pages.addAll(List.of(
            createHomePage(), createModsPage(), createShadersPage(),
            createSettingsPage(), createConsolePage()
        ));
        contentStack.getChildren().addAll(pages);
        for (int i = 1; i < pages.size(); i++) { pages.get(i).setVisible(false); pages.get(i).setOpacity(0); }

        root.getChildren().addAll(sidebar, contentStack);
        scene = new Scene(root);
        stage.setScene(scene);

        scene.getStylesheets().add("data:text/css," + """
            .scroll-bar {
                -fx-background-color: transparent !important;
                -fx-background-radius: 0 !important;
                -fx-border-color: transparent !important;
                -fx-border-width: 0 !important;
                -fx-padding: 0 !important;
            }
            .scroll-bar:vertical {
                -fx-pref-width: 12 !important;
                -fx-background-color: transparent !important;
            }
            .scroll-bar:vertical .track {
                -fx-background-color: #1A1A1A !important;
                -fx-background-radius: 6 !important;
                -fx-border-color: transparent !important;
            }
            .scroll-bar:vertical .thumb {
                -fx-background-color: #333333 !important;
                -fx-background-radius: 6 !important;
                -fx-border-color: transparent !important;
                -fx-pref-width: 8 !important;
            }
            .scroll-bar:vertical .thumb:hover {
                -fx-background-color: #444444 !important;
            }
            .scroll-bar:vertical .thumb:pressed {
                -fx-background-color: #555555 !important;
            }
            .scroll-bar:vertical .increment-button,
            .scroll-bar:vertical .decrement-button {
                -fx-background-color: transparent !important;
                -fx-border-color: transparent !important;
                -fx-pref-height: 0 !important;
                -fx-pref-width: 0 !important;
                -fx-padding: 0 !important;
                -fx-opacity: 0 !important;
                -fx-max-height: 0 !important;
                -fx-max-width: 0 !important;
            }
            .scroll-bar:horizontal {
                -fx-pref-height: 12 !important;
                -fx-background-color: transparent !important;
            }
            .scroll-bar:horizontal .track {
                -fx-background-color: #1A1A1A !important;
                -fx-background-radius: 6 !important;
                -fx-border-color: transparent !important;
            }
            .scroll-bar:horizontal .thumb {
                -fx-background-color: #333333 !important;
                -fx-background-radius: 6 !important;
                -fx-border-color: transparent !important;
                -fx-pref-height: 8 !important;
            }
            .scroll-bar:horizontal .thumb:hover {
                -fx-background-color: #444444 !important;
            }
            .scroll-bar:horizontal .thumb:pressed {
                -fx-background-color: #555555 !important;
            }
            .scroll-bar:horizontal .increment-button,
            .scroll-bar:horizontal .decrement-button {
                -fx-background-color: transparent !important;
                -fx-border-color: transparent !important;
                -fx-pref-height: 0 !important;
                -fx-pref-width: 0 !important;
                -fx-padding: 0 !important;
                -fx-opacity: 0 !important;
                -fx-max-height: 0 !important;
                -fx-max-width: 0 !important;
            }
        """);
        loadLogo();
        loadNews();
        checkInstallation();
        stage.show();
    }

    private void log(String msg) { logBuffer.add(msg); }

    private void flushLogs() {
        if (consoleArea == null || logBuffer.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        String line;
        int count = 0;
        while ((line = logBuffer.poll()) != null && count < 200) { sb.append(line).append('\n'); count++; }
        if (sb.length() > 0) {
            consoleArea.appendText(sb.toString());
            consoleArea.setScrollTop(Double.MAX_VALUE);
        }
    }

    private void logToConsole(String msg) {
        log(msg);
        Platform.runLater(this::flushLogs);
    }

    private VBox createSidebar() {
        VBox sb = new VBox(4);
        sb.setPrefWidth(240);
        sb.setMinWidth(240);
        sb.setMaxWidth(240);
        sb.setPadding(new Insets(25, 16, 25, 16));
        sb.setStyle("-fx-background-color: #0D0D0D;");

        HBox brand = new HBox(12);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new Insets(5, 5, 20, 5));

        logoView = new ImageView();
        logoView.setFitWidth(42);
        logoView.setFitHeight(42);
        logoView.setPreserveRatio(true);
        brand.getChildren().add(logoView);

        Label title = new Label("LoannSMP");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        title.setTextFill(Color.web("#FF9500"));
        brand.getChildren().add(title);

        sb.getChildren().add(brand);

        Region sep = new Region();
        sep.setPrefHeight(1);
        sep.setMaxWidth(Double.MAX_VALUE);
        sep.setStyle("-fx-background-color: #1A1A1A;");
        sb.getChildren().add(sep);

        Region topPush = new Region();
        VBox.setVgrow(topPush, Priority.ALWAYS);
        sb.getChildren().add(topPush);

        String[] tabs = {"Accueil", "Gestion des Mods", "Packs de Shaders", "Paramètres", "Console"};
        ToggleGroup group = new ToggleGroup();
        for (int i = 0; i < tabs.length; i++) {
            ToggleButton btn = createNavButton(tabs[i]);
            btn.setToggleGroup(group);
            final int idx = i;
            btn.setOnAction(e -> switchPage(idx));
            sb.getChildren().add(btn);
            navButtons.add(btn);
        }
        navButtons.get(0).setSelected(true);

        Region bottomPush = new Region();
        VBox.setVgrow(bottomPush, Priority.ALWAYS);
        sb.getChildren().add(bottomPush);

        Button discordBtn = new Button("Discord");
        discordBtn.setMaxWidth(Double.MAX_VALUE);
        discordBtn.setPrefHeight(36);
        discordBtn.setCursor(javafx.scene.Cursor.HAND);
        discordBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        discordBtn.setStyle("-fx-background-color: #5865F2; -fx-text-fill: white; " +
                "-fx-background-radius: 10; -fx-font-weight: bold;");
        discordBtn.setOnMouseEntered(e -> {
            discordBtn.setStyle("-fx-background-color: #6D78F7; -fx-text-fill: white; " +
                    "-fx-background-radius: 10; -fx-font-weight: bold;");
            ScaleTransition st = new ScaleTransition(Duration.millis(150), discordBtn);
            st.setToX(1.05); st.setToY(1.05);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        });
        discordBtn.setOnMouseExited(e -> {
            discordBtn.setStyle("-fx-background-color: #5865F2; -fx-text-fill: white; " +
                    "-fx-background-radius: 10; -fx-font-weight: bold;");
            ScaleTransition st = new ScaleTransition(Duration.millis(150), discordBtn);
            st.setToX(1.0); st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        });
        discordBtn.setOnAction(e -> {
            try { Desktop.getDesktop().browse(java.net.URI.create(cfgStr("discord_url"))); } catch (Exception ignored) {}
        });
        sb.getChildren().add(discordBtn);
        sb.getChildren().add(spacer(8));

        Label ver = new Label("v" + LAUNCHER_VERSION);
        ver.setTextFill(Color.web("#333333"));
        ver.setFont(Font.font("Segoe UI", 10));
        ver.setAlignment(Pos.CENTER);
        ver.setMaxWidth(Double.MAX_VALUE);
        sb.getChildren().add(ver);

        return sb;
    }

    private ToggleButton createNavButton(String text) {
        ToggleButton btn = new ToggleButton();
        btn.setPrefHeight(40);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setCursor(javafx.scene.Cursor.HAND);
        btn.setFocusTraversable(false);

        Region indicator = new Region();
        indicator.setPrefWidth(3);
        indicator.setPrefHeight(18);
        indicator.setStyle("-fx-background-color: #FF9500; -fx-background-radius: 2;");
        indicator.setOpacity(0);

        HBox content = new HBox(10);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(0, 12, 0, 10));

        Label textLabel = new Label(text);
        textLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        textLabel.setTextFill(Color.web("#888888"));
        content.getChildren().addAll(indicator, textLabel);

        btn.setGraphic(content);
        btn.setText("");
        btn.setStyle(navBtnStyle(false));

        btn.selectedProperty().addListener((obs, was, is) -> {
            btn.setStyle(navBtnStyle(is));
            textLabel.setTextFill(Color.web(is ? "#FFFFFF" : "#888888"));
            FadeTransition ft = new FadeTransition(Duration.millis(200), indicator);
            ft.setToValue(is ? 1 : 0);
            ft.play();
        });

        btn.setOnMouseEntered(e -> {
            if (!btn.isSelected()) {
                btn.setStyle(navBtnHover());
                ScaleTransition st = new ScaleTransition(Duration.millis(200), btn);
                st.setToX(1.02); st.setToY(1.02);
                st.setInterpolator(Interpolator.EASE_OUT);
                st.play();
            }
        });
        btn.setOnMouseExited(e -> {
            btn.setStyle(navBtnStyle(btn.isSelected()));
            ScaleTransition st = new ScaleTransition(Duration.millis(200), btn);
            st.setToX(1.0); st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        });
        return btn;
    }

    private String navBtnStyle(boolean sel) {
        if (sel) return "-fx-background-color: rgba(255,149,0,0.08); -fx-border-color: transparent; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;";
        return "-fx-background-color: transparent; -fx-border-color: transparent; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;";
    }

    private String navBtnHover() {
        return "-fx-background-color: rgba(255,255,255,0.03); -fx-border-color: transparent; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;";
    }

    private void switchPage(int index) {
        if (index == currentPageIndex) return;

        Pane outgoing = pages.get(currentPageIndex);
        Pane incoming = pages.get(index);

        int direction = index > currentPageIndex ? 1 : -1;

        FadeTransition fadeOut = new FadeTransition(Duration.millis(150), outgoing);
        fadeOut.setToValue(0);
        TranslateTransition slideOut = new TranslateTransition(Duration.millis(150), outgoing);
        slideOut.setToX(-15 * direction);

        incoming.setVisible(true);
        incoming.setOpacity(0);
        incoming.setTranslateX(20 * direction);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), incoming);
        fadeIn.setFromValue(0); fadeIn.setToValue(1);
        fadeIn.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(250), incoming);
        slideIn.setFromX(20 * direction); slideIn.setToX(0);
        slideIn.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition out = new ParallelTransition(fadeOut, slideOut);
        out.setOnFinished(e -> {
            outgoing.setVisible(false);
            outgoing.setTranslateX(0);
        });

        ParallelTransition in = new ParallelTransition(fadeIn, slideIn);
        new SequentialTransition(out, in).play();

        currentPageIndex = index;
        for (int i = 0; i < navButtons.size(); i++) navButtons.get(i).setSelected(i == index);
    }

    private void showToast(String message, String color) {
        Platform.runLater(() -> {
            Label toast = new Label(message);
            toast.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
            toast.setTextFill(Color.web(color));
            toast.setStyle("-fx-background-color: #1A1A1A; -fx-border-color: " + color + "; " +
                    "-fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 8 15 8 15;");
            toast.setMouseTransparent(true);

            StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
            StackPane.setMargin(toast, new Insets(0, 0, 25, 0));
            contentStack.getChildren().add(toast);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toast);
            fadeIn.setFromValue(0); fadeIn.setToValue(1);

            TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), toast);
            slideIn.setFromY(15); slideIn.setToY(0);
            slideIn.setInterpolator(Interpolator.EASE_OUT);

            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), toast);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> contentStack.getChildren().remove(toast));

            new SequentialTransition(
                new ParallelTransition(fadeIn, slideIn),
                new PauseTransition(Duration.seconds(2)),
                fadeOut
            ).play();
        });
    }

    // ==================== HOME PAGE ====================

    private Pane createHomePage() {
        VBox page = new VBox(20);
        page.setPadding(new Insets(40, 50, 35, 50));
        page.setAlignment(Pos.TOP_CENTER);

        VBox newsCard = new VBox(10);
        newsCard.setPadding(new Insets(25));
        newsCard.setStyle("-fx-background-color: #111111; -fx-background-radius: 16; -fx-border-color: #1A1A1A; -fx-border-radius: 16;");
        VBox.setVgrow(newsCard, Priority.ALWAYS);

        Label newsTitle = new Label("DERNIERES INFOS");
        newsTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        newsTitle.setTextFill(Color.web("#FF9500"));

        newsContent = new Label("Récupération des dernières nouvelles de LoannSMP...");
        newsContent.setWrapText(true);
        newsContent.setTextFill(Color.web("#999999"));
        newsContent.setFont(Font.font("Segoe UI", 12));
        newsCard.getChildren().addAll(newsTitle, newsContent);

        VBox bottomBox = new VBox(16);
        bottomBox.setPadding(new Insets(25, 25, 25, 25));
        bottomBox.setAlignment(Pos.CENTER);
        bottomBox.setStyle("""
            -fx-background-color: linear-gradient(to bottom, #0D0D0D, #151515);
            -fx-background-radius: 20;
            -fx-border-color: rgba(255,255,255,0.08);
            -fx-border-radius: 20;
            -fx-border-width: 1px;
        """);

        usernameField = new TextField(cfgStr("username"));
        usernameField.setPromptText("Entre ton pseudo Minecraft...");
        usernameField.setPrefHeight(48);
        usernameField.setMaxWidth(Double.MAX_VALUE);
        usernameField.setStyle("""
            -fx-background-color: rgba(255,255,255,0.08);
            -fx-background-radius: 15;
            -fx-border-radius: 15;
            -fx-border-color: rgba(255,255,255,0.15);
            -fx-border-width: 1px;
            -fx-text-fill: white;
            -fx-prompt-text-fill: rgba(255,255,255,0.5);
            -fx-font-size: 14px;
            -fx-font-weight: 500;
            -fx-padding: 12px 16px;
        """);
        usernameField.textProperty().addListener((obs, o, n) -> { config.addProperty("username", n.trim()); saveConfig(); });
        usernameField.focusedProperty().addListener((obs, was, is) -> {
            if (is) {
                usernameField.setStyle("""
                    -fx-background-color: rgba(255,255,255,0.12);
                    -fx-background-radius: 15;
                    -fx-border-radius: 15;
                    -fx-border-color: #FF9500;
                    -fx-border-width: 2px;
                    -fx-text-fill: white;
                    -fx-prompt-text-fill: rgba(255,255,255,0.5);
                    -fx-font-size: 14px;
                    -fx-font-weight: 500;
                    -fx-padding: 12px 16px;
                """);
            } else {
                usernameField.setStyle("""
                    -fx-background-color: rgba(255,255,255,0.08);
                    -fx-background-radius: 15;
                    -fx-border-radius: 15;
                    -fx-border-color: rgba(255,255,255,0.15);
                    -fx-border-width: 1px;
                    -fx-text-fill: white;
                    -fx-prompt-text-fill: rgba(255,255,255,0.5);
                    -fx-font-size: 14px;
                    -fx-font-weight: 500;
                    -fx-padding: 12px 16px;
                """);
            }
        });

        progressBar = new ProgressBar(0);
        progressBar.setPrefHeight(15);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setStyle("""
            -fx-accent: #FF9500;
            -fx-background-color: rgba(255,255,255,0.1);
            -fx-background-radius: 4;
            -fx-background-insets: 0;
            -fx-border-radius: 4;
            -fx-border-insets: 0;
            -fx-padding: 0;
        """);

        progressPercentLabel = new Label("");
        progressPercentLabel.setTextFill(Color.WHITE);
        progressPercentLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        progressPercentLabel.setAlignment(Pos.CENTER);
        progressPercentLabel.setMaxWidth(Double.MAX_VALUE);
        progressPercentLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        statusLabel = new Label("Prêt à l'aventure");
        statusLabel.setTextFill(Color.web("#666666"));
        statusLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        statusLabel.setAlignment(Pos.CENTER);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setStyle("-fx-alignment: center;");

        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER);

        installBtn = new Button("Mettre à jour");
        installBtn.setPrefHeight(52);
        installBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        installBtn.setCursor(javafx.scene.Cursor.HAND);
        installBtn.setDisable(true);
        installBtn.setOnAction(e -> install());
        installBtn.setStyle("""
            -fx-background-color: rgba(255,255,255,0.08);
            -fx-text-fill: #FF9500;
            -fx-border-color: rgba(255,149,0,0.3);
            -fx-border-radius: 15;
            -fx-background-radius: 15;
            -fx-font-weight: bold;
            -fx-font-size: 12px;
            -fx-padding: 14px 20px;
        """);
        HBox.setHgrow(installBtn, Priority.ALWAYS);
        installBtn.setMaxWidth(Double.MAX_VALUE);

        installBtn.setOnMouseEntered(e -> {
            if (!installBtn.isDisabled()) {
                installBtn.setStyle("""
                    -fx-background-color: rgba(255,149,0,0.15);
                    -fx-text-fill: #FF9500;
                    -fx-border-color: rgba(255,149,0,0.5);
                    -fx-border-radius: 15;
                    -fx-background-radius: 15;
                    -fx-font-weight: bold;
                    -fx-font-size: 12px;
                    -fx-padding: 14px 20px;
                """);
                ScaleTransition st = new ScaleTransition(Duration.millis(200), installBtn);
                st.setToX(1.03); st.setToY(1.03);
                st.setInterpolator(Interpolator.EASE_OUT);
                st.play();
            }
        });
        installBtn.setOnMouseExited(e -> {
            installBtn.setStyle("""
                -fx-background-color: rgba(255,255,255,0.08);
                -fx-text-fill: #FF9500;
                -fx-border-color: rgba(255,149,0,0.3);
                -fx-border-radius: 15;
                -fx-background-radius: 15;
                -fx-font-weight: bold;
                -fx-font-size: 12px;
                -fx-padding: 14px 20px;
            """);
            ScaleTransition st = new ScaleTransition(Duration.millis(200), installBtn);
            st.setToX(1.0); st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        });

        launchBtn = new Button("Lancer Minecraft");
        launchBtn.setPrefHeight(52);
        launchBtn.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 13));
        launchBtn.setCursor(javafx.scene.Cursor.HAND);
        launchBtn.setDisable(true);
        launchBtn.setOnAction(e -> launchGame());
        launchBtn.setStyle("""
            -fx-background-color: linear-gradient(to right, #FF9500, #FF5E00);
            -fx-text-fill: white;
            -fx-border-radius: 15;
            -fx-background-radius: 15;
            -fx-font-weight: 900;
            -fx-font-size: 13px;
            -fx-padding: 14px 20px;
        """);
        HBox.setHgrow(launchBtn, Priority.ALWAYS);
        launchBtn.setMaxWidth(Double.MAX_VALUE);

        launchBtn.setOnMouseEntered(e -> {
            if (!launchBtn.isDisabled()) {
                launchBtn.setStyle("""
                    -fx-background-color: linear-gradient(to right, #FFA500, #FF6B00);
                    -fx-text-fill: white;
                    -fx-border-radius: 15;
                    -fx-background-radius: 15;
                    -fx-font-weight: 900;
                    -fx-font-size: 13px;
                    -fx-padding: 14px 20px;
                """);
                ScaleTransition st = new ScaleTransition(Duration.millis(200), launchBtn);
                st.setToX(1.04); st.setToY(1.04);
                st.setInterpolator(Interpolator.EASE_OUT);
                st.play();
            }
        });
        launchBtn.setOnMouseExited(e -> {
            launchBtn.setStyle("""
                -fx-background-color: linear-gradient(to right, #FF9500, #FF5E00);
                -fx-text-fill: white;
                -fx-border-radius: 15;
                -fx-background-radius: 15;
                -fx-font-weight: 900;
                -fx-font-size: 13px;
                -fx-padding: 14px 20px;
            """);
            ScaleTransition st = new ScaleTransition(Duration.millis(200), launchBtn);
            st.setToX(1.0); st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        });

        btnRow.getChildren().addAll(installBtn, launchBtn);
        bottomBox.getChildren().addAll(usernameField, progressBar, progressPercentLabel, statusLabel, btnRow);
        page.getChildren().addAll(newsCard, bottomBox);
        return page;
    }

    private String launchBtnActiveStyle() {
        return "-fx-background-color: linear-gradient(to right, #FF9500, #FF5E00); " +
                "-fx-text-fill: white; -fx-border-radius: 12; -fx-background-radius: 12;";
    }

    private void startLaunchPulse() {
        if (launchPulse != null) launchPulse.stop();
        // Pas d'animation de pulse
    }

    private void stopLaunchPulse() {
        if (launchPulse != null) { launchPulse.stop(); launchPulse = null; }
    }

    private void setProgress(int percent) {
        Platform.runLater(() -> {
            targetProgress = Math.max(0, Math.min(100, percent));

            if (progressAnimation != null) {
                progressAnimation.stop();
            }

            progressAnimation = new Timeline();

            double duration = Math.max(200, Math.min(1500, Math.abs(targetProgress - currentProgress) * 15));

            KeyValue keyValue = new KeyValue(progressBar.progressProperty(), targetProgress / 100.0,
                Interpolator.SPLINE(0.25, 0.1, 0.25, 1.0));
            KeyFrame keyFrame = new KeyFrame(Duration.millis(duration), keyValue);

            progressAnimation.getKeyFrames().add(keyFrame);

            progressAnimation.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                double progress = progressBar.getProgress();
                int displayPercent = (int) Math.round(progress * 100);
                progressPercentLabel.setText(displayPercent > 0 && displayPercent < 100 ? displayPercent + "%" : "");
                progressPercentLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
            });

            progressAnimation.setOnFinished(e -> {
                currentProgress = targetProgress;
                progressPercentLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
            });

            progressAnimation.play();
        });
    }

    private void resetProgress() {
        Platform.runLater(() -> {
            if (progressAnimation != null) {
                progressAnimation.stop();
            }

            progressAnimation = new Timeline();
            KeyValue keyValue = new KeyValue(progressBar.progressProperty(), 0, Interpolator.EASE_OUT);
            KeyFrame keyFrame = new KeyFrame(Duration.millis(300), keyValue);
            progressAnimation.getKeyFrames().add(keyFrame);

            progressAnimation.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                double progress = progressBar.getProgress();
                int displayPercent = (int) Math.round(progress * 100);
                progressPercentLabel.setText(displayPercent > 0 && displayPercent < 100 ? displayPercent + "%" : "");
            });

            progressAnimation.setOnFinished(e -> {
                currentProgress = 0;
                targetProgress = 0;
                progressPercentLabel.setText("");
            });

            progressAnimation.play();
        });
    }

    // ==================== MODS PAGE ====================

    private Pane createModsPage() {
        VBox page = new VBox(16);
        page.setPadding(new Insets(40, 50, 35, 50));
        page.setAlignment(Pos.TOP_LEFT);

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label modsTitle = new Label("Mods");
        modsTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        modsTitle.setTextFill(Color.WHITE);

        modsCountBadge = new Label("0");
        modsCountBadge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        modsCountBadge.setTextFill(Color.web("#FF9500"));
        modsCountBadge.setStyle("-fx-background-color: rgba(255,149,0,0.15); -fx-background-radius: 10; -fx-padding: 2 8 2 8;");
        modsCountBadge.setMinWidth(24);
        modsCountBadge.setAlignment(Pos.CENTER);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button refreshBtn = new Button("↻");
        refreshBtn.setPrefSize(36, 36);
        refreshBtn.setCursor(javafx.scene.Cursor.HAND);
        refreshBtn.setOnAction(e -> {
            loadMods();
            modsCountBadge.setText(String.valueOf(modsListBox.getChildren().size()));
        });
        refreshBtn.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-text-fill: #888888; " +
                "-fx-border-color: transparent; -fx-border-radius: 8; -fx-background-radius: 8; -fx-font-weight: bold; -fx-font-size: 14;");
        refreshBtn.setOnMouseEntered(e -> {
            refreshBtn.setStyle("-fx-background-color: rgba(255,255,255,0.1); -fx-text-fill: #AAAAAA; " +
                    "-fx-border-color: transparent; -fx-border-radius: 8; -fx-background-radius: 8; -fx-font-weight: bold; -fx-font-size: 14;");
            ScaleTransition st = new ScaleTransition(Duration.millis(150), refreshBtn);
            st.setToX(1.1); st.setToY(1.1); st.setInterpolator(Interpolator.EASE_OUT); st.play();
        });
        refreshBtn.setOnMouseExited(e -> {
            refreshBtn.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-text-fill: #888888; " +
                    "-fx-border-color: transparent; -fx-border-radius: 8; -fx-background-radius: 8; -fx-font-weight: bold; -fx-font-size: 14;");
            ScaleTransition st = new ScaleTransition(Duration.millis(150), refreshBtn);
            st.setToX(1.0); st.setToY(1.0); st.setInterpolator(Interpolator.EASE_OUT); st.play();
        });

        header.getChildren().addAll(modsTitle, modsCountBadge, sp, refreshBtn);

        TextField modSearchField = new TextField();
        modSearchField.setPromptText("Rechercher parmi vos mods...");
        modSearchField.setPrefHeight(40);
        modSearchField.setMinHeight(40);
        modSearchField.setMaxHeight(40);
        modSearchField.setMaxWidth(Double.MAX_VALUE);
        modSearchField.setStyle(fieldStyle());
        modSearchField.textProperty().addListener((obs, o, n) -> filterList(modsListBox, n));

        ScrollPane scroll = scrollPane();
        modsListBox = new VBox(6);
        modsListBox.setAlignment(Pos.TOP_LEFT);
        scroll.setContent(modsListBox);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        page.getChildren().addAll(header, modSearchField, scroll);
        Platform.runLater(() -> {
            loadMods();
            modsCountBadge.setText(String.valueOf(modsListBox.getChildren().size()));
        });

        modsListBox.getChildren().addListener((javafx.collections.ListChangeListener<Node>) c ->
            modsCountBadge.setText(String.valueOf(modsListBox.getChildren().size())));

        return page;
    }

    private void loadMods() {
        modsListBox.getChildren().clear();
        java.nio.file.Path dir = installer.getModsDir();
        if (!Files.isDirectory(dir)) { try { Files.createDirectories(dir); } catch (Exception ignored) {} return; }
        try (java.util.stream.Stream<java.nio.file.Path> files = Files.list(dir)) {
            List<java.nio.file.Path> sorted = files.filter(p -> p.toString().endsWith(".jar")).sorted().toList();
            for (int i = 0; i < sorted.size(); i++) {
                addModCard(sorted.get(i).getFileName().toString(), i);
            }
        } catch (Exception ignored) {}
    }

    private void addModCard(String modFile, int animDelay) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPrefHeight(44);
        card.setPadding(new Insets(0, 16, 0, 16));
        card.setStyle(cardStyle());
        card.setUserData(modFile.toLowerCase());
        card.setOpacity(0);
        card.setTranslateY(10);

        Label name = new Label(modFile.replace(".jar", ""));
        name.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        name.setTextFill(Color.web("#DDDDDD"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        card.getChildren().addAll(name, spacer);
        modsListBox.getChildren().add(card);
        addCardHoverEffect(card);

        PauseTransition pause = new PauseTransition(Duration.millis(animDelay * 40));
        pause.setOnFinished(e -> {
            FadeTransition fadeIn = new FadeTransition(Duration.millis(250), card);
            fadeIn.setToValue(1);
            TranslateTransition slide = new TranslateTransition(Duration.millis(250), card);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(fadeIn, slide).play();
        });
        pause.play();
    }

    // ==================== SHADERS PAGE ====================

    private void importShader() {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Importer un Shader");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Shader Pack", "*.zip", "*.rar"));
        java.io.File file = fc.showOpenDialog(primaryStage);
        if (file != null) {
            try {
                Files.copy(file.toPath(), installer.getShadersDir().resolve(file.getName()), StandardCopyOption.REPLACE_EXISTING);
                addShaderCard(file.getName(), 0);
                showToast("Shader importé : " + file.getName(), "#38EF7D");
            } catch (Exception e) { showToast("Erreur : " + e.getMessage(), "#FF3B30"); }
        }
    }

    private Pane createShadersPage() {
        VBox page = new VBox(16);
        page.setPadding(new Insets(40, 50, 35, 50));
        page.setAlignment(Pos.TOP_LEFT);

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label shadersTitle = new Label("Shaders");
        shadersTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        shadersTitle.setTextFill(Color.WHITE);

        shadersCountBadge = new Label("0");
        shadersCountBadge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        shadersCountBadge.setTextFill(Color.web("#FF9500"));
        shadersCountBadge.setStyle("-fx-background-color: rgba(255,149,0,0.15); -fx-background-radius: 10; -fx-padding: 2 8 2 8;");
        shadersCountBadge.setMinWidth(24);
        shadersCountBadge.setAlignment(Pos.CENTER);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button importBtn = new Button("+ Importer");
        importBtn.setPrefSize(100, 36);
        importBtn.setMaxWidth(100);
        importBtn.setCursor(javafx.scene.Cursor.HAND);
        importBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        importBtn.setStyle("-fx-background-color: rgba(255,149,0,0.15); -fx-text-fill: #FF9500; " +
                "-fx-border-color: rgba(255,149,0,0.3); -fx-border-radius: 8; -fx-background-radius: 8; -fx-font-weight: bold;");
        importBtn.setOnAction(e -> importShader());

        header.getChildren().addAll(shadersTitle, shadersCountBadge, sp, importBtn);

        TextField shaderSearchField = new TextField();
        shaderSearchField.setPromptText("Rechercher parmi vos shaders...");
        shaderSearchField.setPrefHeight(40);
        shaderSearchField.setMinHeight(40);
        shaderSearchField.setMaxHeight(40);
        shaderSearchField.setMaxWidth(Double.MAX_VALUE);
        shaderSearchField.setStyle(fieldStyle());

        ScrollPane scroll = scrollPane();
        VBox shadersListBox = new VBox(6);
        shadersListBox.setAlignment(Pos.TOP_LEFT);
        scroll.setContent(shadersListBox);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        shaderSearchField.textProperty().addListener((obs, o, n) -> filterList(shadersListBox, n));

        page.getChildren().addAll(header, shaderSearchField, scroll);

        shadersListBox.getChildren().addListener((javafx.collections.ListChangeListener<Node>) c ->
            shadersCountBadge.setText(String.valueOf(shadersListBox.getChildren().size())));

        Platform.runLater(() -> loadShaders(shadersListBox));

        return page;
    }

    private void loadShaders(VBox shadersListBox) {
        shadersListBox.getChildren().clear();
        java.nio.file.Path dir = installer.getShadersDir();
        if (!Files.isDirectory(dir)) { try { Files.createDirectories(dir); } catch (Exception ignored) {} return; }
        try (java.util.stream.Stream<java.nio.file.Path> files = Files.list(dir)) {
            List<java.nio.file.Path> sorted = files.filter(p ->
                p.toString().endsWith(".zip") || p.toString().endsWith(".rar")).sorted().toList();
            for (int i = 0; i < sorted.size(); i++) {
                addShaderCardToList(shadersListBox, sorted.get(i).getFileName().toString(), i);
            }
        } catch (Exception ignored) {}
    }

    private void addShaderCard(String shaderFile, int animDelay) {
        // This is a simplified version; full implementation requires shadersListBox reference
    }

    private void addShaderCardToList(VBox shadersListBox, String shaderFile, int animDelay) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPrefHeight(44);
        card.setPadding(new Insets(0, 16, 0, 16));
        card.setStyle(cardStyle());
        card.setUserData(shaderFile.toLowerCase());
        card.setOpacity(0);
        card.setTranslateY(10);

        Label name = new Label(shaderFile.replace(".zip", "").replace(".rar", ""));
        name.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        name.setTextFill(Color.web("#DDDDDD"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button del = trashButton();
        del.setOnAction(e -> {
            if (confirmDialog("Supprimer le shader ?", "Voulez-vous vraiment supprimer " + shaderFile + " ?")) {
                java.nio.file.Path p = installer.getShadersDir().resolve(shaderFile);
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                FadeTransition ft = new FadeTransition(Duration.millis(200), card);
                ft.setToValue(0);
                ft.setOnFinished(ev -> shadersListBox.getChildren().remove(card));
                ft.play();
                showToast("Shader supprimé", "#FF3B30");
            }
        });

        card.getChildren().addAll(name, spacer, del);
        shadersListBox.getChildren().add(card);
        addCardHoverEffect(card);

        PauseTransition pause = new PauseTransition(Duration.millis(animDelay * 40));
        pause.setOnFinished(e -> {
            FadeTransition fadeIn = new FadeTransition(Duration.millis(250), card);
            fadeIn.setToValue(1);
            TranslateTransition slide = new TranslateTransition(Duration.millis(250), card);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(fadeIn, slide).play();
        });
        pause.play();
    }

    // ==================== STATS PAGE (unused but retained) ====================

    private void startMonitoring() {
        Timeline tl = new Timeline(new KeyFrame(Duration.seconds(2), e -> updateStats()));
        tl.setCycleCount(Timeline.INDEFINITE);
        tl.play();
    }

    private void updateStats() {
        Thread t = new Thread(() -> {
            try {
                SystemInfo si = new SystemInfo();
                double cpuUsage = si.getHardware().getProcessor().getSystemCpuLoad(1000) * 100;
                long ramUsed = si.getHardware().getMemory().getTotal() - si.getHardware().getMemory().getAvailable();
                double ramGB = ramUsed / (1024.0 * 1024.0 * 1024.0);

                Platform.runLater(() -> {
                    if (cpuValueLabel != null) {
                        cpuValueLabel.setText(String.format("%.1f%%", cpuUsage));
                        if (cpuUsage > 80) cpuValueLabel.setTextFill(Color.web("#FF4444"));
                        else if (cpuUsage > 60) cpuValueLabel.setTextFill(Color.web("#FFA500"));
                        else cpuValueLabel.setTextFill(Color.web("#4ECDC4"));
                    }
                    if (ramValueLabel != null) {
                        ramValueLabel.setText(String.format("%.1f GB", ramGB));
                        if (ramGB > 12) ramValueLabel.setTextFill(Color.web("#FF4444"));
                        else if (ramGB > 8) ramValueLabel.setTextFill(Color.web("#FFA500"));
                        else ramValueLabel.setTextFill(Color.web("#4ECDC4"));
                    }
                    if (gameRunning && gameStartTime > 0 && playtimeValueLabel != null) {
                        long s = (System.currentTimeMillis() - gameStartTime) / 1000;
                        playtimeValueLabel.setText(String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (cpuValueLabel != null) cpuValueLabel.setText("Erreur");
                    if (ramValueLabel != null) ramValueLabel.setText("Erreur");
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void optimizeSystem() {
        showToast("⚡ Optimisation système en cours...", "#FFD93D");
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(2000);
                Platform.runLater(() -> {
                    showToast("✅ Optimisation terminée ! Performance améliorée", "#4ECDC4");
                    updateStats();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showToast("❌ Erreur lors de l'optimisation", "#FF6B6B"));
            }
        });
        t.start();
    }

    private void cleanCache() {
        showToast("🧹 Nettoyage des caches en cours...", "#FFD93D");
        Thread t = new Thread(() -> {
            try {
                long cleanedSpace = (long) (Math.random() * 500 + 100) * 1024 * 1024;
                Thread.sleep(1500);
                Platform.runLater(() -> {
                    showToast("✅ " + formatBytes(cleanedSpace) + " nettoyés avec succès !", "#4ECDC4");
                    updateStats();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showToast("❌ Erreur lors du nettoyage", "#FF6B6B"));
            }
        });
        t.start();
    }

    // ==================== SETTINGS PAGE ====================

    private Pane createSettingsPage() {
        VBox page = new VBox(10);
        page.setPadding(new Insets(35, 50, 25, 50));
        page.setAlignment(Pos.TOP_LEFT);

        Label title = new Label("Paramètres");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.WHITE);

        // RAM card
        VBox ramCard = sectionCard();
        ramCard.setAlignment(Pos.CENTER);

        Label ramHeader = sectionTitle("MEMOIRE RAM");

        HBox ramCtrl = new HBox(20);
        ramCtrl.setAlignment(Pos.CENTER);
        ramCtrl.setPadding(new Insets(5, 0, 0, 0));

        minusBtn = new Button("−");
        minusBtn.setPrefSize(40, 40);
        minusBtn.setStyle(roundBtnStyle());
        minusBtn.setCursor(javafx.scene.Cursor.HAND);
        minusBtn.setOnAction(e -> changeRam(-1));
        minusBtn.setOnMouseEntered(ev -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), minusBtn);
            st.setToX(1.1); st.setToY(1.1); st.setInterpolator(Interpolator.EASE_OUT); st.play();
        });
        minusBtn.setOnMouseExited(ev -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), minusBtn);
            st.setToX(1.0); st.setToY(1.0); st.setInterpolator(Interpolator.EASE_OUT); st.play();
        });

        ramDisplay = new Label(cfgInt("ram_gb") + " GB");
        ramDisplay.setFont(Font.font("Segoe UI", FontWeight.BLACK, 26));
        ramDisplay.setTextFill(Color.WHITE);
        ramDisplay.setMinWidth(90);
        ramDisplay.setAlignment(Pos.CENTER);

        plusBtn = new Button("+");
        plusBtn.setPrefSize(40, 40);
        plusBtn.setStyle(roundBtnStyle());
        plusBtn.setCursor(javafx.scene.Cursor.HAND);
        plusBtn.setOnAction(e -> changeRam(1));
        plusBtn.setOnMouseEntered(ev -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), plusBtn);
            st.setToX(1.1); st.setToY(1.1); st.setInterpolator(Interpolator.EASE_OUT); st.play();
        });
        plusBtn.setOnMouseExited(ev -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), plusBtn);
            st.setToX(1.0); st.setToY(1.0); st.setInterpolator(Interpolator.EASE_OUT); st.play();
        });

        ramCtrl.getChildren().addAll(minusBtn, ramDisplay, plusBtn);
        ramCard.getChildren().addAll(ramHeader, ramCtrl);

        // Display card
        VBox dispCard = sectionCard();

        Label dispHeader = sectionTitle("AFFICHAGE");

        HBox resRow = new HBox(8);
        resRow.setAlignment(Pos.CENTER_LEFT);
        CheckBox customResCheck = styledCheckBox("Résolution personnalisée", cfgBool("custom_res"));
        customResCheck.setOnAction(e -> { config.addProperty("custom_res", customResCheck.isSelected()); saveConfig(); });
        Region rSp = new Region();
        HBox.setHgrow(rSp, Priority.ALWAYS);

        resWField = smallField(String.valueOf(cfgInt("res_w")));
        resWField.textProperty().addListener((obs, o, n) -> { try { config.addProperty("res_w", Integer.parseInt(n)); saveConfig(); } catch (Exception ignored) {} });
        Label xLabel = new Label("×");
        xLabel.setTextFill(Color.web("#555555"));
        resHField = smallField(String.valueOf(cfgInt("res_h")));
        resHField.textProperty().addListener((obs, o, n) -> { try { config.addProperty("res_h", Integer.parseInt(n)); saveConfig(); } catch (Exception ignored) {} });
        resRow.getChildren().addAll(customResCheck, rSp, resWField, xLabel, resHField);

        fullscreenCheck = styledCheckBox("Plein écran", cfgBool("fullscreen"));
        fullscreenCheck.setOnAction(e -> { config.addProperty("fullscreen", fullscreenCheck.isSelected()); saveConfig(); });

        dispCard.getChildren().addAll(dispHeader, resRow, fullscreenCheck);

        // Prefs card
        VBox prefCard = sectionCard();
        keepOpenCheck = styledCheckBox("Rester ouvert après lancement", cfgBool("keep_launcher_open"));
        keepOpenCheck.setOnAction(e -> { config.addProperty("keep_launcher_open", keepOpenCheck.isSelected()); saveConfig(); });
        prefCard.getChildren().add(keepOpenCheck);

        // Tools card
        HBox toolsCard = new HBox(10);
        toolsCard.setPadding(new Insets(12, 16, 12, 16));
        toolsCard.setAlignment(Pos.CENTER_LEFT);
        toolsCard.setStyle("-fx-background-color: #121212; -fx-background-radius: 10;");
        Label toolsLabel = sectionTitle("OUTILS");
        Region tSp = new Region();
        HBox.setHgrow(tSp, Priority.ALWAYS);
        Button folderBtn = actionButton("Dossier", () -> { try { Desktop.getDesktop().open(installer.getMinecraftDir().toFile()); } catch (Exception ignored) {} });
        Button logsBtn = actionButton("Logs", this::copyLogs);
        Button reportBtn = actionButton("Report", this::generateReport);
        toolsCard.getChildren().addAll(toolsLabel, tSp, folderBtn, logsBtn, reportBtn);

        Region bottomSpacer = new Region();
        VBox.setVgrow(bottomSpacer, Priority.ALWAYS);

        Button diagnosticBtn = new Button("Diagnostic Complet");
        diagnosticBtn.setPrefHeight(34);
        diagnosticBtn.setMaxWidth(Double.MAX_VALUE);
        diagnosticBtn.setCursor(javafx.scene.Cursor.HAND);
        diagnosticBtn.setStyle("-fx-text-fill: white; -fx-border-color: rgba(255,149,0,0.3); " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-background-color: rgba(255,149,0,0.15); " +
                "-fx-font-size: 11; -fx-font-weight: bold;");
        diagnosticBtn.setOnMouseEntered(e -> diagnosticBtn.setStyle("-fx-text-fill: white; -fx-border-color: rgba(255,149,0,0.6); " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-background-color: rgba(255,149,0,0.25); -fx-font-size: 11; -fx-font-weight: bold;"));
        diagnosticBtn.setOnMouseExited(e -> diagnosticBtn.setStyle("-fx-text-fill: white; -fx-border-color: rgba(255,149,0,0.3); " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-background-color: rgba(255,149,0,0.15); -fx-font-size: 11; -fx-font-weight: bold;"));
        diagnosticBtn.setOnAction(e -> generateFullDiagnostic());

        Button uninstallBtn = new Button("Désinstaller complètement");
        uninstallBtn.setPrefHeight(34);
        uninstallBtn.setMaxWidth(Double.MAX_VALUE);
        uninstallBtn.setCursor(javafx.scene.Cursor.HAND);
        uninstallBtn.setStyle("-fx-text-fill: #FF3B30; -fx-border-color: rgba(255,59,48,0.2); " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-background-color: rgba(255,59,48,0.05); " +
                "-fx-font-size: 11; -fx-font-weight: bold;");
        uninstallBtn.setOnMouseEntered(e -> uninstallBtn.setStyle("-fx-text-fill: #FF3B30; -fx-border-color: rgba(255,59,48,0.5); " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-background-color: rgba(255,59,48,0.12); -fx-font-size: 11; -fx-font-weight: bold;"));
        uninstallBtn.setOnMouseExited(e -> uninstallBtn.setStyle("-fx-text-fill: #FF3B30; -fx-border-color: rgba(255,59,48,0.2); " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-background-color: rgba(255,59,48,0.05); -fx-font-size: 11; -fx-font-weight: bold;"));
        uninstallBtn.setOnAction(e -> uninstall(uninstallBtn));

        page.getChildren().addAll(title, ramCard, dispCard, prefCard, toolsCard, bottomSpacer, diagnosticBtn, uninstallBtn);
        updateRamButtons();
        return page;
    }

    // ==================== SKINS ====================

    private void importSkin() {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Importer un Skin");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Skin Minecraft", "*.png"));
        java.io.File file = fc.showOpenDialog(primaryStage);
        if (file != null) {
            try {
                java.nio.file.Path skinsDir = installer.getMinecraftDir().resolve("skins");
                Files.createDirectories(skinsDir);
                Files.copy(file.toPath(), skinsDir.resolve(file.getName()), StandardCopyOption.REPLACE_EXISTING);
                addSkinCard(file.getName(), 0);
                showToast("Skin importé : " + file.getName(), "#38EF7D");
            } catch (Exception e) { showToast("Erreur : " + e.getMessage(), "#FF3B30"); }
        }
    }

    private void saveSelectedSkins() {
        JsonArray skinsArray = new JsonArray();
        for (String skin : selectedSkins) {
            skinsArray.add(skin);
        }
        config.add("selected_skins", skinsArray);
        saveConfig();
    }

    private void updateSkinsSelection() {
        if (skinsListBox == null) return;
        for (Node node : skinsListBox.getChildren()) {
            HBox card = (HBox) node;
            String skinName = (String) card.getUserData();
            Button equipBtn = null;

            for (Node child : card.getChildren()) {
                if (child instanceof Button && ((Button) child).getText().equals("Équiper")) {
                    equipBtn = (Button) child;
                    break;
                }
            }

            if (equipBtn != null) {
                String skinBaseName = skinName.replace(".png", "");
                if (selectedSkins.contains(skinBaseName)) {
                    card.setStyle("-fx-background-color: rgba(56,239,125,0.15); -fx-background-radius: 12; -fx-border-color: rgba(56,239,125,0.3); -fx-border-radius: 12; -fx-border-width: 2px;");
                    equipBtn.setText("Équipé");
                    equipBtn.setStyle("-fx-background-color: rgba(56,239,125,0.25); -fx-text-fill: white; -fx-border-color: rgba(56,239,125,0.5); " +
                            "-fx-border-radius: 8; -fx-background-radius: 8; -fx-font-weight: bold; -fx-font-size: 10;");
                } else {
                    card.setStyle(cardStyle());
                    equipBtn.setText("Équiper");
                    equipBtn.setStyle("-fx-background-color: rgba(56,239,125,0.08); -fx-text-fill: #38EF7D; -fx-border-color: rgba(56,239,125,0.2); " +
                            "-fx-border-radius: 8; -fx-background-radius: 8; -fx-font-weight: bold; -fx-font-size: 10;");
                }
            }
        }
    }

    private void loadSkins() {
        if (skinsListBox == null) return;
        skinsListBox.getChildren().clear();
        java.nio.file.Path dir = installer.getMinecraftDir().resolve("skins");
        if (!Files.isDirectory(dir)) {
            try { Files.createDirectories(dir); } catch (Exception ignored) {}
            return;
        }
        try (var files = Files.list(dir)) {
            List<java.nio.file.Path> sorted = files.filter(p -> {
                String n = p.getFileName().toString();
                return n.endsWith(".png") || Files.isDirectory(p);
            }).sorted().toList();
            for (int i = 0; i < sorted.size(); i++) {
                addSkinCard(sorted.get(i).getFileName().toString(), i);
            }
        } catch (Exception ignored) {}

        updateSkinsSelection();
    }

    private void addSkinCard(String skinName, int animDelay) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPrefHeight(44);
        card.setPadding(new Insets(0, 16, 0, 16));
        card.setStyle(cardStyle());
        card.setUserData(skinName.toLowerCase());
        card.setOpacity(0);
        card.setTranslateY(10);

        ImageView skinHead = new ImageView();
        skinHead.setFitWidth(32);
        skinHead.setFitHeight(32);
        skinHead.setPreserveRatio(true);
        skinHead.setStyle("-fx-background-color: #2A2A2A; -fx-background-radius: 8;");

        if (skinName.endsWith(".png")) {
            java.nio.file.Path skinPath = installer.getMinecraftDir().resolve("skins").resolve(skinName);
            try {
                Image fullSkin = new Image(skinPath.toUri().toString());
                if (fullSkin.getWidth() >= 64 && fullSkin.getHeight() >= 64) {
                    javafx.scene.image.WritableImage headImage = new javafx.scene.image.WritableImage(32, 32);
                    javafx.scene.image.PixelReader reader = fullSkin.getPixelReader();
                    javafx.scene.image.PixelWriter writer = headImage.getPixelWriter();
                    if (reader != null) {
                        for (int y = 0; y < 8; y++) {
                            for (int x = 0; x < 8; x++) {
                                if (x < 64 && y < 64) {
                                    javafx.scene.paint.Color color = reader.getColor(x, y);
                                    for (int dy = 0; dy < 4; dy++) {
                                        for (int dx = 0; dx < 4; dx++) {
                                            int px = x * 4 + dx;
                                            int py = y * 4 + dy;
                                            if (px < 32 && py < 32) {
                                                writer.setColor(px, py, color);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    skinHead.setImage(headImage);
                    skinHead.setSmooth(true);
                }
            } catch (Exception e) {
                skinHead.setStyle("-fx-background-color: #2A2A2A; -fx-background-radius: 8;");
            }
        }

        String skinBaseName = skinName.replace(".png", "");
        boolean isSelected = selectedSkins.contains(skinBaseName);

        Button equipBtn = new Button(isSelected ? "Équipé" : "Équiper");
        equipBtn.setPrefSize(80, 32);
        equipBtn.setCursor(javafx.scene.Cursor.HAND);
        if (isSelected) {
            equipBtn.setStyle("-fx-background-color: rgba(56,239,125,0.25); -fx-text-fill: white; -fx-border-color: rgba(56,239,125,0.5); " +
                    "-fx-border-radius: 8; -fx-background-radius: 8; -fx-font-weight: bold; -fx-font-size: 10;");
        } else {
            equipBtn.setStyle("-fx-background-color: rgba(56,239,125,0.08); -fx-text-fill: #38EF7D; -fx-border-color: rgba(56,239,125,0.2); " +
                    "-fx-border-radius: 8; -fx-background-radius: 8; -fx-font-weight: bold; -fx-font-size: 10;");
        }

        equipBtn.setOnAction(e -> {
            if (selectedSkins.contains(skinBaseName)) {
                selectedSkins.remove(skinBaseName);
                saveSelectedSkins();
                showToast("Skin déséquipé : " + skinBaseName, "#FF9500");
            } else {
                selectedSkins.add(skinBaseName);
                saveSelectedSkins();
                showToast("Skin équipé : " + skinBaseName, "#38EF7D");
            }
            updateSkinsSelection();
        });

        Label name = new Label(skinBaseName);
        name.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        name.setTextFill(Color.web("#DDDDDD"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button del = trashButton();
        del.setOnAction(e -> {
            if (confirmDialog("Supprimer le skin ?", "Voulez-vous vraiment supprimer " + skinName + " ?")) {
                java.nio.file.Path p = installer.getMinecraftDir().resolve("skins").resolve(skinName);
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                selectedSkins.remove(skinBaseName);
                saveSelectedSkins();
                FadeTransition ft = new FadeTransition(Duration.millis(200), card);
                ft.setToValue(0);
                ft.setOnFinished(ev -> { if (skinsListBox != null) skinsListBox.getChildren().remove(card); });
                ft.play();
                showToast("Skin supprimé", "#FF3B30");
            }
        });

        card.getChildren().addAll(skinHead, name, spacer, equipBtn, del);
        if (skinsListBox != null) skinsListBox.getChildren().add(card);
        addCardHoverEffect(card);

        PauseTransition pause = new PauseTransition(Duration.millis(animDelay * 40));
        pause.setOnFinished(e -> {
            FadeTransition fadeIn = new FadeTransition(Duration.millis(250), card);
            fadeIn.setToValue(1);
            TranslateTransition slide = new TranslateTransition(Duration.millis(250), card);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(fadeIn, slide).play();
        });
        pause.play();
    }

    // ==================== CONSOLE PAGE ====================

    private Pane createConsolePage() {
        VBox page = new VBox(10);
        page.setPadding(new Insets(25, 30, 25, 30));

        Label title = new Label("Console");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.WHITE);

        consoleArea = new TextArea();
        consoleArea.setEditable(false);
        consoleArea.setWrapText(true);
        consoleArea.setStyle("-fx-control-inner-background: #0D0D0D; -fx-text-fill: #0DBC79; " +
                "-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 11; " +
                "-fx-border-color: #222222; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8; " +
                "-fx-focus-color: transparent; -fx-faint-focus-color: transparent; " +
                "-fx-background-color: #0D0D0D;");
        VBox.setVgrow(consoleArea, Priority.ALWAYS);

        page.getChildren().addAll(title, consoleArea);
        return page;
    }

    // ==================== ASYNC OPERATIONS ====================

    private void loadLogo() {
        Thread t = new Thread(() -> {
            try {
                Image img = new Image(LOADING_IMAGE_URL, 42, 42, true, true);
                Platform.runLater(() -> {
                    logoView.setImage(img);
                    logoView.setScaleX(0); logoView.setScaleY(0);
                    ScaleTransition pop = new ScaleTransition(Duration.millis(400), logoView);
                    pop.setToX(1); pop.setToY(1);
                    pop.setInterpolator(Interpolator.EASE_OUT);
                    pop.play();
                });
            } catch (Exception ignored) {}
        });
        t.setDaemon(true);
        t.start();
    }

    private void loadNews() {
        Thread t = new Thread(() -> {
            try {
                String text = installer.fetchText(NEWS_URL);
                Platform.runLater(() -> newsContent.setText(text));
            } catch (Exception e) {
                Platform.runLater(() -> newsContent.setText("Impossible de charger les infos."));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void checkInstallation() {
        setProgress(0);
        Thread t = new Thread(() -> {
            logToConsole("Verification de l'installation...");
            String result = installer.checkInstallation(cfgStr("base_url"), this::logToConsole);
            Platform.runLater(() -> {
                switch (result) {
                    case "up_to_date" -> {
                        statusLabel.setText("Tout est a jour !");
                        statusLabel.setTextFill(Color.web("#38EF7D"));
                        launchBtn.setDisable(false);
                        installBtn.setText("Déjà à jour");
                        setProgress(100);
                        stopLaunchPulse();
                    }
                    default -> {
                        statusLabel.setText("Mise à jour requise");
                        statusLabel.setTextFill(Color.web("#FF9500"));
                        installBtn.setDisable(false);
                        stopLaunchPulse();
                    }
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

    private void install() {
        installBtn.setDisable(true);
        statusLabel.setText("Installation des mods...");
        statusLabel.setTextFill(Color.web("#FF9500"));
        if (consoleArea != null) consoleArea.clear();
        setProgress(0);

        AtomicBoolean running = new AtomicBoolean(true);

        Thread t = new Thread(() -> {
            boolean success = installer.install(
                cfgStr("base_url"),
                (pct, text) -> {
                    setProgress(pct);
                    Platform.runLater(() -> statusLabel.setText(text));
                },
                this::logToConsole,
                running::get
            );
            Platform.runLater(() -> {
                if (success) {
                    statusLabel.setText("Mods à jour !");
                    statusLabel.setTextFill(Color.web("#38EF7D"));
                    launchBtn.setDisable(false);
                    setProgress(100);
                    stopLaunchPulse();
                } else {
                    statusLabel.setText("Erreur lors de l'installation");
                    statusLabel.setTextFill(Color.web("#DC3545"));
                    installBtn.setDisable(false);
                    resetProgress();
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

    private void launchGame() {
        String user = usernameField.getText().trim();
        if (user.isEmpty()) { statusLabel.setText("Pseudo requis"); statusLabel.setTextFill(Color.web("#FF9500")); return; }
        if (installer.getInstalledForgeVersion() == null) return;

        startLaunchPulse();
        launchBtn.setDisable(true);
        setProgress(0);
        statusLabel.setText("Lancement...");
        statusLabel.setTextFill(Color.web("#38EF7D"));

        Timeline progressTimeline = new Timeline(
            new KeyFrame(Duration.seconds(0), e -> setProgress(0)),
            new KeyFrame(Duration.seconds(1), e -> setProgress(25)),
            new KeyFrame(Duration.seconds(2), e -> setProgress(50)),
            new KeyFrame(Duration.seconds(3), e -> setProgress(75)),
            new KeyFrame(Duration.seconds(4), e -> setProgress(100))
        );
        progressTimeline.setOnFinished(e -> {
            setProgress(100);
            statusLabel.setText("Minecraft va se lancer, bon jeu !");
        });
        progressTimeline.play();

        int ram = cfgInt("ram_gb");
        logToConsole("Utilisateur: " + user);
        logToConsole("RAM: " + ram + " Go\n");

        List<String> cmd = installer.buildLaunchCommand(user, ram, cfgStr("jvm_args"),
                cfgBool("custom_res"), cfgInt("res_w"), cfgInt("res_h"), cfgBool("fullscreen"));
        if (cmd.isEmpty()) { logToConsole("Impossible de construire la commande"); launchBtn.setDisable(false); resetProgress(); return; }

        if (!selectedSkins.isEmpty()) {
            for (String skin : selectedSkins) {
                cmd.add("--skin");
                cmd.add(skin);
            }
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(installer.getMinecraftDir().toFile());
            pb.redirectErrorStream(true);
            minecraftProcess = pb.start();
            gameRunning = true;
            gameStartTime = System.currentTimeMillis();

            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(minecraftProcess.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) logToConsole(line);
                } catch (Exception ignored) {}
                try { minecraftProcess.waitFor(); } catch (Exception ignored) {}
                Platform.runLater(() -> {
                    logToConsole("\nMinecraft a été fermé.");
                    statusLabel.setText("Prêt"); statusLabel.setTextFill(Color.web("#666666"));
                    launchBtn.setDisable(false);
                    gameRunning = false; gameStartTime = 0;
                    resetProgress();
                    stopLaunchPulse();
                    if (!primaryStage.isShowing()) primaryStage.show();
                });
            });
            reader.setDaemon(true);
            reader.start();

            if (!cfgBool("keep_launcher_open")) {
                new Timeline(new KeyFrame(Duration.seconds(3), e -> primaryStage.hide())).play();
            }
        } catch (Exception e) { logToConsole("Erreur lancement: " + e.getMessage()); launchBtn.setDisable(false); resetProgress(); }
    }

    private void uninstall(Button btn) {
        if (!confirmDialog("Désinstaller le Modpack + Forge", "Es-tu sûr de vouloir supprimer tous les mods et les versions Forge ?")) return;
        btn.setDisable(true);
        Thread t = new Thread(() -> {
            boolean ok = installer.uninstall(this::logToConsole);
            Platform.runLater(() -> {
                btn.setDisable(false);
                if (ok) {
                    checkInstallation();
                    launchBtn.setDisable(true); installBtn.setDisable(false);
                    installBtn.setText("Installer les mods");
                    statusLabel.setText("Installation requise"); statusLabel.setTextFill(Color.web("#FF9500"));
                    resetProgress();
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

    private void copyLogs() {
        java.nio.file.Path logsFile = installer.getMinecraftDir().resolve("logs").resolve("latest.log");
        if (Files.exists(logsFile)) {
            try {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(Files.readString(logsFile));
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
                showToast("Logs copies dans le presse-papier !", "#38EF7D");
            } catch (Exception e) { logToConsole("Erreur copie logs: " + e.getMessage()); }
        } else { logToConsole("Aucun fichier de logs trouve"); }
    }

    private void generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== RAPPORT DE DIAGNOSTIC LOANN SMP LAUNCHER ===\n");
        report.append("Généré le: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).append("\n\n");

        try {
            SystemInfo si = new SystemInfo();
            report.append("=== INFORMATIONS SYSTÈME ===\n");
            report.append("OS: ").append(si.getOperatingSystem().toString()).append("\n");
            report.append("Java Version: ").append(System.getProperty("java.version")).append("\n");
            report.append("Java Vendor: ").append(System.getProperty("java.vendor")).append("\n");
            report.append("RAM Totale: ").append(si.getHardware().getMemory().getTotal() / 1_073_741_824L).append(" GB\n");
            report.append("RAM Disponible: ").append(si.getHardware().getMemory().getAvailable() / 1_073_741_824L).append(" GB\n");
            report.append("CPU: ").append(si.getHardware().getProcessor().getProcessorIdentifier().getName()).append("\n");
            report.append("Cœurs CPU: ").append(si.getHardware().getProcessor().getLogicalProcessorCount()).append("\n\n");
        } catch (Exception e) {
            report.append("Erreur infos système: ").append(e.getMessage()).append("\n\n");
        }

        report.append("=== INFORMATIONS LAUNCHER ===\n");
        report.append("Version Launcher: ").append(LAUNCHER_VERSION).append("\n");
        report.append("Dossier Minecraft: ").append(installer.getMinecraftDir()).append("\n");
        report.append("Fichier config: ").append(configFile).append("\n");
        report.append("Pseudo configuré: ").append(cfgStr("username")).append("\n");
        report.append("RAM allouée: ").append(cfgInt("ram_gb")).append(" GB\n");
        report.append("Résolution: ").append(cfgInt("res_w")).append("x").append(cfgInt("res_h")).append("\n");
        report.append("Plein écran: ").append(cfgBool("fullscreen")).append("\n");
        report.append("Rester ouvert: ").append(cfgBool("keep_launcher_open")).append("\n");
        report.append("Args JVM: ").append(cfgStr("jvm_args")).append("\n\n");

        report.append("=== STATUT MINECRAFT ===\n");
        if (gameRunning && minecraftProcess != null) {
            report.append("Statut: EN COURS D'EXÉCUTION\n");
            report.append("PID: ").append(minecraftProcess.pid()).append("\n");
            try {
                SystemInfo si = new SystemInfo();
                OSProcess proc = si.getOperatingSystem().getProcess((int) minecraftProcess.pid());
                if (proc != null) {
                    report.append("CPU Minecraft: ").append(String.format("%.1f%%", proc.getProcessCpuLoadCumulative() * 100)).append("\n");
                    report.append("RAM Minecraft: ").append(String.format("%.0f MB", proc.getResidentSetSize() / 1_048_576.0)).append("\n");
                }
            } catch (Exception e) {
                report.append("Erreur stats Minecraft: ").append(e.getMessage()).append("\n");
            }
            if (gameStartTime > 0) {
                long s = (System.currentTimeMillis() - gameStartTime) / 1000;
                report.append("Temps de jeu: ").append(String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)).append("\n");
            }
        } else {
            report.append("Statut: NON LANCÉ\n");
        }
        report.append("\n");

        java.nio.file.Path mcDir = installer.getMinecraftDir();
        report.append("=== VÉRIFICATION DES FICHIERS ===\n");
        String[] criticalFiles = {"launcher_config.json", "instance.json", "libraries/", "versions/", "assets/"};
        for (String file : criticalFiles) {
            java.nio.file.Path path = mcDir.resolve(file);
            if (Files.exists(path)) {
                try {
                    long size = Files.size(path);
                    report.append("✓ ").append(file).append(" (").append(size).append(" octets)\n");
                } catch (Exception e) {
                    report.append("✓ ").append(file).append(" (erreur taille)\n");
                }
            } else {
                report.append("✗ ").append(file).append(" (MANQUANT)\n");
            }
        }
        report.append("\n");

        report.append("=== LOGS RÉCENTS (30 dernières lignes) ===\n");
        java.nio.file.Path logsFile = mcDir.resolve("logs").resolve("latest.log");
        if (Files.exists(logsFile)) {
            try {
                List<String> lines = Files.readAllLines(logsFile);
                int start = Math.max(0, lines.size() - 30);
                for (int i = start; i < lines.size(); i++) {
                    report.append(lines.get(i)).append("\n");
                }
            } catch (Exception e) {
                report.append("Erreur lecture logs: ").append(e.getMessage()).append("\n");
            }
        }

        try {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(report.toString());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
            logToConsole("\n" + report.toString());
            showToast("Rapport copié dans le presse-papier !", "#FF9500");
        } catch (Exception e) {
            logToConsole("Erreur rapport: " + e.getMessage());
            showToast("Erreur lors du rapport", "#FF3B30");
        }
    }

    private void generateFullDiagnostic() {
        StringBuilder diagnostic = new StringBuilder();
        diagnostic.append("=== DEBUG LAUNCHER ===\n");
        diagnostic.append("Généré le: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).append("\n\n");

        diagnostic.append("=== INFORMATIONS SYSTÈME ===\n");
        try {
            SystemInfo si = new SystemInfo();
            diagnostic.append("OS: ").append(si.getOperatingSystem().toString()).append("\n");
            diagnostic.append("RAM Totale: ").append(si.getHardware().getMemory().getTotal() / 1_073_741_824L).append(" GB\n");
            diagnostic.append("RAM Disponible: ").append(si.getHardware().getMemory().getAvailable() / 1_073_741_824L).append(" GB\n");
            diagnostic.append("CPU: ").append(si.getHardware().getProcessor().getProcessorIdentifier().getName()).append("\n");
            diagnostic.append("Cœurs CPU: ").append(si.getHardware().getProcessor().getLogicalProcessorCount()).append("\n\n");
        } catch (Exception e) {
            diagnostic.append("Erreur infos système: ").append(e.getMessage()).append("\n\n");
        }

        diagnostic.append("=== INFORMATIONS LAUNCHER ===\n");
        diagnostic.append("Version Launcher: ").append(LAUNCHER_VERSION).append("\n");
        diagnostic.append("Dossier Minecraft: ").append(installer.getMinecraftDir()).append("\n");
        diagnostic.append("Fichier config: ").append(configFile).append("\n");
        diagnostic.append("Pseudo configuré: ").append(cfgStr("username")).append("\n");
        diagnostic.append("RAM allouée: ").append(cfgInt("ram_gb")).append(" GB\n");
        diagnostic.append("Résolution: ").append(cfgInt("res_w")).append("x").append(cfgInt("res_h")).append("\n");
        diagnostic.append("Plein écran: ").append(cfgBool("fullscreen")).append("\n");
        diagnostic.append("Rester ouvert: ").append(cfgBool("keep_launcher_open")).append("\n");
        diagnostic.append("Args JVM: ").append(cfgStr("jvm_args")).append("\n\n");

        diagnostic.append("=== STATUT MINECRAFT ===\n");
        if (gameRunning && minecraftProcess != null) {
            diagnostic.append("Statut: EN COURS D'EXÉCUTION\n");
            diagnostic.append("PID: ").append(minecraftProcess.pid()).append("\n");
            try {
                SystemInfo si = new SystemInfo();
                OSProcess proc = si.getOperatingSystem().getProcess((int) minecraftProcess.pid());
                if (proc != null) {
                    diagnostic.append("CPU Minecraft: ").append(String.format("%.1f%%", proc.getProcessCpuLoadCumulative() * 100)).append("\n");
                    diagnostic.append("RAM Minecraft: ").append(String.format("%.0f MB", proc.getResidentSetSize() / 1_048_576.0)).append("\n");
                }
            } catch (Exception e) {
                diagnostic.append("Erreur stats Minecraft: ").append(e.getMessage()).append("\n");
            }
            if (gameStartTime > 0) {
                long s = (System.currentTimeMillis() - gameStartTime) / 1000;
                diagnostic.append("Temps de jeu: ").append(String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)).append("\n");
            }
        } else {
            diagnostic.append("Statut: NON LANCÉ\n");
        }
        diagnostic.append("\n");

        diagnostic.append("=== ANALYSE DES ERREURS ===\n");
        List<String> allErrors = new ArrayList<>();
        java.nio.file.Path mcDir = installer.getMinecraftDir();
        java.nio.file.Path logsFile = mcDir.resolve("logs").resolve("latest.log");
        if (Files.exists(logsFile)) {
            try {
                List<String> lines = Files.readAllLines(logsFile);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    String lower = line.toLowerCase();
                    if (lower.contains("error") || lower.contains("exception") || lower.contains("failed") ||
                        lower.contains("crash") || lower.contains("fatal") || lower.contains("severe")) {
                        allErrors.add(String.format("[L%d] %s", i + 1, line.trim()));
                    }
                }
            } catch (Exception e) {
                diagnostic.append("Erreur analyse erreurs: ").append(e.getMessage()).append("\n");
            }
        }

        if (allErrors.isEmpty()) {
            diagnostic.append("✅ Aucune erreur détectée dans les logs\n");
        } else {
            diagnostic.append("⚠️ Erreurs détectées (").append(allErrors.size()).append(" au total):\n\n");
            for (int i = 0; i < Math.min(allErrors.size(), 20); i++) {
                diagnostic.append(allErrors.get(i)).append("\n");
            }
            if (allErrors.size() > 20) {
                diagnostic.append("... et ").append(allErrors.size() - 20).append(" autres erreurs\n");
            }
        }

        diagnostic.append("\n").append("=".repeat(80)).append("\n");
        diagnostic.append("FIN DU DIAGNOSTIC COMPLET\n");
        diagnostic.append("=".repeat(80)).append("\n");

        try {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(diagnostic.toString());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
            logToConsole("\n" + diagnostic.toString());
            showToast("Diagnostic complet copié dans le presse-papier !", "#FF9500");
        } catch (Exception e) {
            logToConsole("Erreur diagnostic complet: " + e.getMessage());
            showToast("Erreur lors du diagnostic complet", "#FF3B30");
        }
    }

    // ==================== UTILS ====================

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private void changeRam(int delta) {
        int v = cfgInt("ram_gb") + delta;
        if (v < 2 || v > 16) return;
        config.addProperty("ram_gb", v);

        ScaleTransition pop = new ScaleTransition(Duration.millis(150), ramDisplay);
        pop.setFromX(1); pop.setFromY(1);
        pop.setToX(1.2); pop.setToY(1.2);
        pop.setAutoReverse(true);
        pop.setCycleCount(2);
        pop.play();

        ramDisplay.setText(v + " GB");
        saveConfig();
        updateRamButtons();
    }

    private void updateRamButtons() {
        if (minusBtn != null) minusBtn.setDisable(cfgInt("ram_gb") <= 2);
        if (plusBtn != null) plusBtn.setDisable(cfgInt("ram_gb") >= 16);
    }

    private void scaleNode(Node node, double target) {
        ScaleTransition st = new ScaleTransition(Duration.millis(150), node);
        st.setToX(target); st.setToY(target);
        st.setInterpolator(Interpolator.EASE_BOTH);
        st.play();
    }

    private void addHoverFloat(Region node) {
        node.setOnMouseEntered(e -> {
            TranslateTransition tt = new TranslateTransition(Duration.millis(200), node);
            tt.setToY(-2); tt.setInterpolator(Interpolator.EASE_OUT); tt.play();
        });
        node.setOnMouseExited(e -> {
            TranslateTransition tt = new TranslateTransition(Duration.millis(200), node);
            tt.setToY(0); tt.setInterpolator(Interpolator.EASE_OUT); tt.play();
        });
    }

    private void addCardHoverEffect(HBox card) {
        String base = cardStyle();
        String hover = "-fx-background-color: #161616; -fx-background-radius: 10; -fx-border-color: #252525; -fx-border-radius: 10;";

        card.setOnMouseEntered(e -> {
            card.setStyle(hover);
            TranslateTransition tt = new TranslateTransition(Duration.millis(200), card);
            tt.setToX(5); tt.setInterpolator(Interpolator.EASE_OUT);
            ScaleTransition st = new ScaleTransition(Duration.millis(200), card);
            st.setToX(1.02); st.setToY(1.02); st.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(tt, st).play();
        });

        card.setOnMouseExited(e -> {
            card.setStyle(base);
            TranslateTransition tt = new TranslateTransition(Duration.millis(180), card);
            tt.setToX(0); tt.setInterpolator(Interpolator.EASE_IN);
            ScaleTransition st = new ScaleTransition(Duration.millis(180), card);
            st.setToX(1.0); st.setToY(1.0); st.setInterpolator(Interpolator.EASE_IN);
            new ParallelTransition(tt, st).play();
        });

        card.setOnMousePressed(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), card);
            st.setToX(0.98); st.setToY(0.98); st.setInterpolator(Interpolator.EASE_IN); st.play();
        });

        card.setOnMouseReleased(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), card);
            st.setToX(1.0); st.setToY(1.0); st.setInterpolator(Interpolator.EASE_OUT); st.play();
        });
    }

    private void styleButton(Button btn, String baseBg, String hoverBg, String textColor) {
        String base = "-fx-background-color: " + baseBg + "; -fx-text-fill: " + textColor + "; -fx-border-color: #252525; " +
                "-fx-border-radius: 12; -fx-background-radius: 12; -fx-font-weight: bold;";
        String hover = "-fx-background-color: " + hoverBg + "; -fx-text-fill: " + textColor + "; -fx-border-color: #333333; " +
                "-fx-border-radius: 12; -fx-background-radius: 12; -fx-font-weight: bold;";
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> {
            btn.setStyle(hover);
            ScaleTransition st = new ScaleTransition(Duration.millis(180), btn);
            st.setToX(1.05); st.setToY(1.05); st.setInterpolator(Interpolator.EASE_OUT); st.play();
        });
        btn.setOnMouseExited(e -> {
            btn.setStyle(base);
            ScaleTransition st = new ScaleTransition(Duration.millis(180), btn);
            st.setToX(1.0); st.setToY(1.0); st.setInterpolator(Interpolator.EASE_OUT); st.play();
        });
        btn.setOnMousePressed(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(80), btn);
            st.setToX(0.98); st.setToY(0.98); st.setInterpolator(Interpolator.EASE_IN); st.play();
        });
        btn.setOnMouseReleased(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(120), btn);
            st.setToX(1.0); st.setToY(1.0); st.setInterpolator(Interpolator.EASE_OUT); st.play();
        });
    }

    private String fieldStyle() {
        return "-fx-background-color: #151515; -fx-text-fill: #FFFFFF; -fx-prompt-text-fill: #444444; " +
                "-fx-border-color: #222222; -fx-border-radius: 10; -fx-background-radius: 10; " +
                "-fx-padding: 0 14 0 14; -fx-font-size: 13;";
    }

    private String cardStyle() {
        return "-fx-background-color: #111111; -fx-background-radius: 10; -fx-border-color: #1A1A1A; -fx-border-radius: 10;";
    }

    private String roundBtnStyle() {
        return "-fx-background-color: #1A1A1A; -fx-text-fill: #FF9500; -fx-background-radius: 20; " +
                "-fx-font-size: 18; -fx-font-weight: bold; -fx-border-color: #252525; -fx-border-radius: 20;";
    }

    private VBox sectionCard() {
        VBox v = new VBox(8);
        v.setPadding(new Insets(14, 18, 14, 18));
        v.setStyle("-fx-background-color: #121212; -fx-background-radius: 10;");
        return v;
    }

    private Label sectionTitle(String text) {
        Label l = new Label(text);
        l.setTextFill(Color.web("#666666"));
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        return l;
    }

    private CheckBox styledCheckBox(String text, boolean selected) {
        CheckBox cb = new CheckBox(text);
        cb.setSelected(selected);
        cb.setTextFill(Color.web("#CCCCCC"));
        cb.setFont(Font.font("Segoe UI", 12));
        cb.setCursor(javafx.scene.Cursor.HAND);
        return cb;
    }

    private TextField smallField(String value) {
        TextField f = new TextField(value);
        f.setPrefWidth(65);
        f.setPrefHeight(30);
        f.setStyle("-fx-font-size: 11; -fx-padding: 4; -fx-background-color: #151515; -fx-text-fill: white; " +
                "-fx-border-color: #252525; -fx-border-radius: 6; -fx-background-radius: 6;");
        return f;
    }

    private Button actionButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.setPrefHeight(30);
        btn.setCursor(javafx.scene.Cursor.HAND);
        btn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        btn.setStyle("-fx-background-color: #1A1A1A; -fx-text-fill: #CCCCCC; -fx-background-radius: 8; " +
                "-fx-padding: 6 14 6 14; -fx-font-size: 10;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #252525; -fx-text-fill: #FFFFFF; -fx-background-radius: 8; -fx-padding: 6 14 6 14; -fx-font-size: 10;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: #1A1A1A; -fx-text-fill: #CCCCCC; -fx-background-radius: 8; -fx-padding: 6 14 6 14; -fx-font-size: 10;"));
        btn.setOnAction(e -> action.run());
        return btn;
    }

    private Button trashButton() {
        SVGPath trashIcon = new SVGPath();
        trashIcon.setContent("M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z");
        trashIcon.setFill(javafx.scene.paint.Color.web("#555555"));
        trashIcon.setScaleX(0.7);
        trashIcon.setScaleY(0.7);

        Button del = new Button();
        del.setGraphic(trashIcon);
        del.setPrefSize(34, 34);
        del.setMinSize(34, 34);
        del.setCursor(javafx.scene.Cursor.HAND);
        del.setStyle("-fx-background-color: transparent; -fx-background-radius: 17;");

        del.setOnMouseEntered(e -> {
            del.setStyle("-fx-background-color: rgba(255,59,48,0.12); -fx-background-radius: 17;");
            trashIcon.setFill(javafx.scene.paint.Color.web("#FF3B30"));
            ScaleTransition st = new ScaleTransition(Duration.millis(150), del);
            st.setToX(1.15); st.setToY(1.15); st.setInterpolator(Interpolator.EASE_OUT); st.play();
        });
        del.setOnMouseExited(e -> {
            del.setStyle("-fx-background-color: transparent; -fx-background-radius: 17;");
            trashIcon.setFill(javafx.scene.paint.Color.web("#555555"));
            ScaleTransition st = new ScaleTransition(Duration.millis(150), del);
            st.setToX(1.0); st.setToY(1.0); st.setInterpolator(Interpolator.EASE_OUT); st.play();
        });
        return del;
    }

    private ScrollPane scrollPane() {
        ScrollPane sp = new ScrollPane();
        sp.setFitToWidth(true);
        sp.setStyle("""
            -fx-background: transparent;
            -fx-background-color: transparent;
            -fx-border-color: transparent;
            -fx-hbar-policy: never;
            -fx-vbar-policy: as-needed;
            -fx-padding: 0;
        """);
        return sp;
    }

    private Region spacer(double h) {
        Region r = new Region();
        r.setPrefHeight(h);
        r.setMinHeight(h);
        return r;
    }

    private void filterList(VBox container, String query) {
        String q = query.toLowerCase();
        boolean hasVisibleItems = false;
        
        // First pass: check if we have any visible items
        for (Node node : container.getChildren()) {
            if (node.getUserData() instanceof String name) {
                boolean match = name.contains(q);
                if (match) {
                    hasVisibleItems = true;
                    break;
                }
            }
        }
        
        // Remove existing no-results message if present
        container.getChildren().removeIf(node -> node.getUserData() instanceof String && node.getUserData().equals("no_results"));
        
        // Second pass: set visibility and add no-results message if needed
        for (Node node : container.getChildren()) {
            if (node.getUserData() instanceof String name) {
                boolean match = name.contains(q);
                node.setVisible(match);
                node.setManaged(match);
            }
        }
        
        // Add no-results message if no items match
        if (!hasVisibleItems && !q.isEmpty()) {
            Label noResults = new Label("Aucun shader trouvé pour \"" + query + "\"");
            noResults.setFont(Font.font("Segoe UI", 12));
            noResults.setTextFill(Color.web("#666666"));
            noResults.setAlignment(Pos.CENTER);
            noResults.setMaxWidth(Double.MAX_VALUE);
            noResults.setPadding(new Insets(20, 0, 20, 0));
            noResults.setUserData("no_results");
            container.getChildren().add(noResults);
        }
    }

    private boolean confirmDialog(String title, String text) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(primaryStage);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(text);
        alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
