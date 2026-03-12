package com.loannsmp.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;

import java.awt.Desktop;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class LauncherApp extends Application {

    private static final String LAUNCHER_VERSION = "2.1.0";
    private static final String LOADING_IMAGE_URL =
            "https://raw.githubusercontent.com/LoannDev/LoannSMPLauncher/refs/heads/main/icon.ico";
    private static final String NEWS_URL =
            "https://raw.githubusercontent.com/LoannDev/LoannSMPLauncher/refs/heads/main/latestnews.txt";

    private final MCInstaller installer = new MCInstaller();
    private final Gson gson = new Gson();
    private JsonObject config;
    private final Path configFile;

    private Process minecraftProcess;
    private boolean gameRunning = false;
    private long gameStartTime = 0;

    private final ConcurrentLinkedQueue<String> logBuffer = new ConcurrentLinkedQueue<>();
    private Timeline logFlushTimer;

    private Stage primaryStage;
    private StackPane contentStack;
    private final List<ToggleButton> navButtons = new ArrayList<>();
    private final List<Pane> pages = new ArrayList<>();
    private int currentPageIndex = 0;

    private Label newsContent;
    private TextField usernameField;
    private Label progressPercentLabel;
    private ProgressBar progressBar;
    private Label statusLabel;
    private Button installBtn, launchBtn;
    private Timeline launchPulse;

    private Label ramDisplay;
    private Button minusBtn, plusBtn;
    private CheckBox customResCheck, fullscreenCheck, keepOpenCheck;
    private TextField resWField, resHField;

    private TextArea consoleArea;

    private VBox modsListBox;
    private TextField modSearchField;

    private VBox shadersListBox;
    private TextField shaderSearchField;

    private Label statsNotRunning;
    private VBox statsContainer;
    private Label cpuValueLabel, ramValueLabel, playtimeValueLabel;

    private ImageView logoView;

    public LauncherApp() {
        configFile = installer.getMinecraftDir().resolve("launcher_config.json");
        config = loadConfig();
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
        this.primaryStage = stage;
        stage.setTitle("LoannSMP Launcher V3");
        stage.setWidth(850);
        stage.setHeight(550);
        stage.setResizable(false);

        HBox root = new HBox();
        root.setStyle("-fx-background-color: #0A0A0A;");

        VBox sidebar = createSidebar();

        contentStack = new StackPane();
        contentStack.setStyle("-fx-background-color: #0A0A0A;");
        HBox.setHgrow(contentStack, Priority.ALWAYS);

        pages.addAll(List.of(
                createLauncherPage(), createModsPage(), createShadersPage(),
                createOptionsPage(), createStatsPage(), createConsolePage()
        ));
        contentStack.getChildren().addAll(pages);
        for (int i = 1; i < pages.size(); i++) { pages.get(i).setVisible(false); pages.get(i).setOpacity(0); }

        root.getChildren().addAll(sidebar, contentStack);
        Scene scene = new Scene(root);
        stage.setScene(scene);

        root.setOpacity(0);
        stage.show();
        stage.centerOnScreen();

        FadeTransition startupFade = new FadeTransition(Duration.millis(600), root);
        startupFade.setFromValue(0);
        startupFade.setToValue(1);
        startupFade.setInterpolator(Interpolator.EASE_OUT);
        startupFade.play();

        logFlushTimer = new Timeline(new KeyFrame(Duration.millis(80), e -> flushLogs()));
        logFlushTimer.setCycleCount(Animation.INDEFINITE);
        logFlushTimer.play();

        logToConsole("=== Loann SMP Launcher ===");
        logToConsole("Démarrage: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        logToConsole("Dossier: " + installer.getMinecraftDir() + "\n");

        loadHeaderIcon();
        fetchNews();
        checkInstallation();

        Timeline statsTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateStats()));
        statsTimer.setCycleCount(Animation.INDEFINITE);
        statsTimer.play();
    }

    private void logToConsole(String msg) { logBuffer.add(msg); }

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

    private VBox createSidebar() {
        VBox sidebar = new VBox(4);
        sidebar.setPrefWidth(240);
        sidebar.setMinWidth(240);
        sidebar.setMaxWidth(240);
        sidebar.setPadding(new Insets(25, 16, 25, 16));
        sidebar.setStyle("-fx-background-color: #0D0D0D;");

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

        sidebar.getChildren().add(brand);

        Region sep = new Region();
        sep.setPrefHeight(1);
        sep.setMaxWidth(Double.MAX_VALUE);
        sep.setStyle("-fx-background-color: #1A1A1A;");
        sidebar.getChildren().add(sep);

        Region topPush = new Region();
        VBox.setVgrow(topPush, Priority.ALWAYS);
        sidebar.getChildren().add(topPush);

        String[] tabs = {"Accueil", "Mods", "Shaders", "Options", "Statistiques", "Console"};
        ToggleGroup group = new ToggleGroup();
        for (int i = 0; i < tabs.length; i++) {
            ToggleButton btn = createNavButton(tabs[i]);
            btn.setToggleGroup(group);
            int idx = i;
            btn.setOnAction(e -> switchPage(idx));
            sidebar.getChildren().add(btn);
            navButtons.add(btn);
        }
        navButtons.get(0).setSelected(true);

        Region bottomPush = new Region();
        VBox.setVgrow(bottomPush, Priority.ALWAYS);
        sidebar.getChildren().add(bottomPush);

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
            animateScale(discordBtn, 1.03);
        });
        discordBtn.setOnMouseExited(e -> {
            discordBtn.setStyle("-fx-background-color: #5865F2; -fx-text-fill: white; " +
                    "-fx-background-radius: 10; -fx-font-weight: bold;");
            animateScale(discordBtn, 1.0);
        });
        discordBtn.setOnAction(e -> {
            try { Desktop.getDesktop().browse(java.net.URI.create(cfgStr("discord_url"))); } catch (Exception ignored) {}
        });
        sidebar.getChildren().add(discordBtn);
        sidebar.getChildren().add(spacer(8));

        Label ver = new Label("v" + LAUNCHER_VERSION);
        ver.setTextFill(Color.web("#333333"));
        ver.setFont(Font.font("Segoe UI", 10));
        ver.setAlignment(Pos.CENTER);
        ver.setMaxWidth(Double.MAX_VALUE);
        sidebar.getChildren().add(ver);

        return sidebar;
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
                animateScale(btn, 1.02);
            }
        });
        btn.setOnMouseExited(e -> {
            btn.setStyle(navBtnStyle(btn.isSelected()));
            animateScale(btn, 1.0);
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

        double direction = index > currentPageIndex ? 1 : -1;

        FadeTransition fadeOut = new FadeTransition(Duration.millis(150), outgoing);
        fadeOut.setToValue(0);
        TranslateTransition slideOut = new TranslateTransition(Duration.millis(150), outgoing);
        slideOut.setToX(-15 * direction);

        incoming.setVisible(true);
        incoming.setOpacity(0);
        incoming.setTranslateX(20 * direction);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), incoming);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(250), incoming);
        slideIn.setFromX(20 * direction);
        slideIn.setToX(0);
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

    private void showToast(String message) { showToast(message, "#FF9500"); }

    private void showToast(String message, String color) {
        Platform.runLater(() -> {
            Label toast = new Label(message);
            toast.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
            toast.setTextFill(Color.web(color));
            toast.setStyle("-fx-background-color: #1A1A1A; -fx-border-color: " + color + "; " +
                    "-fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 8 15 8 15;");
            toast.setMouseTransparent(true);
            toast.setEffect(new DropShadow(15, Color.web(color, 0.3)));

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

    private Pane createLauncherPage() {
        VBox page = new VBox(20);
        page.setPadding(new Insets(35, 40, 30, 40));
        page.setAlignment(Pos.TOP_CENTER);

        VBox newsCard = new VBox(10);
        newsCard.setPadding(new Insets(25));
        newsCard.setStyle("-fx-background-color: #111111; -fx-background-radius: 16; -fx-border-color: #1A1A1A; -fx-border-radius: 16;");
        VBox.setVgrow(newsCard, Priority.ALWAYS);
        addHoverLift(newsCard);

        Label newsTitle = new Label("DERNIERES INFOS");
        newsTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        newsTitle.setTextFill(Color.web("#FF9500"));

        newsContent = new Label("Récupération des dernières nouvelles de LoannSMP...");
        newsContent.setWrapText(true);
        newsContent.setTextFill(Color.web("#999999"));
        newsContent.setFont(Font.font("Segoe UI", 12));
        newsCard.getChildren().addAll(newsTitle, newsContent);

        VBox bottomBox = new VBox(14);
        bottomBox.setPadding(new Insets(22, 25, 22, 25));
        bottomBox.setAlignment(Pos.CENTER);
        bottomBox.setStyle("-fx-background-color: #0D0D0D; -fx-background-radius: 16; -fx-border-color: #1A1A1A; -fx-border-radius: 16;");

        usernameField = new TextField(cfgStr("username"));
        usernameField.setPromptText("Entre ton pseudo Minecraft...");
        usernameField.setPrefHeight(44);
        usernameField.setMaxWidth(Double.MAX_VALUE);
        usernameField.setStyle(fieldStyle());
        usernameField.textProperty().addListener((obs, o, n) -> { config.addProperty("username", n.trim()); saveConfig(); });
        usernameField.focusedProperty().addListener((obs, was, is) -> {
            if (is) usernameField.setStyle(fieldStyle() + " -fx-border-color: #FF9500;");
            else usernameField.setStyle(fieldStyle());
        });

        progressBar = new ProgressBar(0);
        progressBar.setPrefHeight(8);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setStyle("-fx-accent: #FF9500;");

        progressPercentLabel = new Label("");
        progressPercentLabel.setTextFill(Color.web("#FF9500"));
        progressPercentLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        progressPercentLabel.setAlignment(Pos.CENTER);
        progressPercentLabel.setMaxWidth(Double.MAX_VALUE);

        statusLabel = new Label("Prêt à l'aventure");
        statusLabel.setTextFill(Color.web("#666666"));
        statusLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        statusLabel.setAlignment(Pos.CENTER);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setStyle("-fx-alignment: center;");

        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER);

        installBtn = new Button("Mettre à jour");
        installBtn.setPrefHeight(46);
        installBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        installBtn.setCursor(javafx.scene.Cursor.HAND);
        installBtn.setDisable(true);
        installBtn.setOnAction(e -> install());
        installBtn.setStyle("-fx-background-color: #151515; -fx-text-fill: #FF9500; -fx-border-color: #252525; " +
                "-fx-border-radius: 12; -fx-background-radius: 12; -fx-font-weight: bold;");
        HBox.setHgrow(installBtn, Priority.ALWAYS);
        installBtn.setMaxWidth(Double.MAX_VALUE);
        addButtonHover(installBtn, "#151515", "#1E1E1E", "#FF9500");

        launchBtn = new Button("Lancer Minecraft");
        launchBtn.setPrefHeight(46);
        launchBtn.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 12));
        launchBtn.setCursor(javafx.scene.Cursor.HAND);
        launchBtn.setDisable(true);
        launchBtn.setOnAction(e -> launch());
        launchBtn.setStyle(launchBtnStyle());
        HBox.setHgrow(launchBtn, Priority.ALWAYS);
        launchBtn.setMaxWidth(Double.MAX_VALUE);
        launchBtn.setOnMouseEntered(e -> { if (!launchBtn.isDisabled()) animateScale(launchBtn, 1.03); });
        launchBtn.setOnMouseExited(e -> animateScale(launchBtn, 1.0));

        btnRow.getChildren().addAll(installBtn, launchBtn);

        bottomBox.getChildren().addAll(usernameField, progressBar, progressPercentLabel, statusLabel, btnRow);
        page.getChildren().addAll(newsCard, bottomBox);
        return page;
    }

    private String launchBtnStyle() {
        return "-fx-background-color: linear-gradient(to right, #FF9500, #FF5E00); " +
                "-fx-text-fill: white; -fx-border-radius: 12; -fx-background-radius: 12;";
    }

    private void startLaunchPulse() {
        if (launchPulse != null) launchPulse.stop();
        DropShadow glow = new DropShadow(20, Color.web("#FF9500", 0.6));
        launchBtn.setEffect(glow);
        launchPulse = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(glow.radiusProperty(), 10)),
                new KeyFrame(Duration.millis(1000), new KeyValue(glow.radiusProperty(), 25)),
                new KeyFrame(Duration.millis(2000), new KeyValue(glow.radiusProperty(), 10))
        );
        launchPulse.setCycleCount(Animation.INDEFINITE);
        launchPulse.play();
    }

    private void stopLaunchPulse() {
        if (launchPulse != null) { launchPulse.stop(); launchPulse = null; }
        launchBtn.setEffect(null);
    }

    private void setProgress(int percent) {
        Platform.runLater(() -> {
            double val = Math.max(0, Math.min(100, percent)) / 100.0;
            progressBar.setProgress(val);
            progressPercentLabel.setText(percent > 0 && percent < 100 ? percent + "%" : "");
        });
    }

    private Pane createModsPage() {
        VBox page = new VBox(16);
        page.setPadding(new Insets(35, 40, 30, 40));
        page.setAlignment(Pos.TOP_LEFT);

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Gestion des Mods");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.WHITE);

        Label countBadge = new Label("0");
        countBadge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        countBadge.setTextFill(Color.web("#FF9500"));
        countBadge.setStyle("-fx-background-color: rgba(255,149,0,0.15); -fx-background-radius: 10; -fx-padding: 2 8 2 8;");
        countBadge.setMinWidth(24);
        countBadge.setAlignment(Pos.CENTER);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Button addBtn = new Button("+ Ajouter");
        addBtn.setPrefSize(130, 36);
        addBtn.setCursor(javafx.scene.Cursor.HAND);
        addBtn.setOnAction(e -> onAddMod());
        addBtn.setStyle("-fx-background-color: rgba(56,239,125,0.08); -fx-text-fill: #38EF7D; -fx-border-color: rgba(56,239,125,0.2); " +
                "-fx-border-radius: 10; -fx-background-radius: 10; -fx-font-weight: bold; -fx-font-size: 11;");
        addBtn.setOnMouseEntered(e -> {
            addBtn.setStyle("-fx-background-color: rgba(56,239,125,0.15); -fx-text-fill: #38EF7D; -fx-border-color: rgba(56,239,125,0.4); " +
                    "-fx-border-radius: 10; -fx-background-radius: 10; -fx-font-weight: bold; -fx-font-size: 11;");
            animateScale(addBtn, 1.05);
        });
        addBtn.setOnMouseExited(e -> {
            addBtn.setStyle("-fx-background-color: rgba(56,239,125,0.08); -fx-text-fill: #38EF7D; -fx-border-color: rgba(56,239,125,0.2); " +
                    "-fx-border-radius: 10; -fx-background-radius: 10; -fx-font-weight: bold; -fx-font-size: 11;");
            animateScale(addBtn, 1.0);
        });
        header.getChildren().addAll(title, countBadge, sp, addBtn);

        modSearchField = new TextField();
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
            scanMods();
            countBadge.setText(String.valueOf(modsListBox.getChildren().size()));
        });

        modsListBox.getChildren().addListener((javafx.collections.ListChangeListener<Node>) c ->
                countBadge.setText(String.valueOf(modsListBox.getChildren().size())));

        return page;
    }

    private void scanMods() {
        modsListBox.getChildren().clear();
        Path dir = installer.getModsDir();
        if (!Files.isDirectory(dir)) { try { Files.createDirectories(dir); } catch (Exception ignored) {} return; }
        try (var files = Files.list(dir)) {
            List<Path> sorted = files.filter(p -> p.toString().endsWith(".jar")).sorted().toList();
            for (int i = 0; i < sorted.size(); i++) {
                addModToList(sorted.get(i).getFileName().toString(), i);
            }
        } catch (Exception ignored) {}
    }

    private void addModToList(String modFile, int animDelay) {
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

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Button del = trashButton();
        del.setOnAction(e -> {
            if (confirmDialog("Supprimer le mod ?", "Voulez-vous vraiment supprimer " + modFile + " ?")) {
                try { Files.deleteIfExists(installer.getModsDir().resolve(modFile)); } catch (Exception ignored) {}
                FadeTransition ft = new FadeTransition(Duration.millis(200), card);
                ft.setToValue(0);
                ScaleTransition st = new ScaleTransition(Duration.millis(200), card);
                st.setToX(0.95); st.setToY(0);
                new ParallelTransition(ft, st).setOnFinished(ev -> modsListBox.getChildren().remove(card));
                new ParallelTransition(ft, st).play();
                showToast("Mod supprimé !");
            }
        });

        card.getChildren().addAll(name, spacer, del);
        modsListBox.getChildren().add(card);

        addCardHover(card);

        PauseTransition delay = new PauseTransition(Duration.millis(animDelay * 40));
        delay.setOnFinished(e -> {
            FadeTransition fadeIn = new FadeTransition(Duration.millis(250), card);
            fadeIn.setToValue(1);
            TranslateTransition slide = new TranslateTransition(Duration.millis(250), card);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(fadeIn, slide).play();
        });
        delay.play();
    }

    private void onAddMod() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Ajouter un mod");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Mod JAR", "*.jar"));
        File file = fc.showOpenDialog(primaryStage);
        if (file != null) {
            try {
                Files.copy(file.toPath(), installer.getModsDir().resolve(file.getName()), StandardCopyOption.REPLACE_EXISTING);
                addModToList(file.getName(), 0);
                showToast("Mod ajoute : " + file.getName(), "#38EF7D");
            } catch (Exception e) { showToast("Erreur : " + e.getMessage(), "#FF3B30"); }
        }
    }

    private Pane createShadersPage() {
        VBox page = new VBox(16);
        page.setPadding(new Insets(35, 40, 30, 40));
        page.setAlignment(Pos.TOP_LEFT);

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Packs de Shaders");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.WHITE);

        Label countBadge = new Label("0");
        countBadge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        countBadge.setTextFill(Color.web("#5865F2"));
        countBadge.setStyle("-fx-background-color: rgba(88,101,242,0.15); -fx-background-radius: 10; -fx-padding: 2 8 2 8;");
        countBadge.setMinWidth(24);
        countBadge.setAlignment(Pos.CENTER);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Button addBtn = new Button("+ Importer");
        addBtn.setPrefSize(120, 36);
        addBtn.setCursor(javafx.scene.Cursor.HAND);
        addBtn.setOnAction(e -> onAddShader());
        addBtn.setStyle("-fx-background-color: rgba(56,239,125,0.08); -fx-text-fill: #38EF7D; -fx-border-color: rgba(56,239,125,0.2); " +
                "-fx-border-radius: 10; -fx-background-radius: 10; -fx-font-weight: bold; -fx-font-size: 11;");
        addBtn.setOnMouseEntered(e -> {
            addBtn.setStyle("-fx-background-color: rgba(56,239,125,0.15); -fx-text-fill: #38EF7D; -fx-border-color: rgba(56,239,125,0.4); " +
                    "-fx-border-radius: 10; -fx-background-radius: 10; -fx-font-weight: bold; -fx-font-size: 11;");
            animateScale(addBtn, 1.05);
        });
        addBtn.setOnMouseExited(e -> {
            addBtn.setStyle("-fx-background-color: rgba(56,239,125,0.08); -fx-text-fill: #38EF7D; -fx-border-color: rgba(56,239,125,0.2); " +
                    "-fx-border-radius: 10; -fx-background-radius: 10; -fx-font-weight: bold; -fx-font-size: 11;");
            animateScale(addBtn, 1.0);
        });
        header.getChildren().addAll(title, countBadge, sp, addBtn);

        shaderSearchField = new TextField();
        shaderSearchField.setPromptText("Rechercher un shader...");
        shaderSearchField.setPrefHeight(40);
        shaderSearchField.setMinHeight(40);
        shaderSearchField.setMaxHeight(40);
        shaderSearchField.setMaxWidth(Double.MAX_VALUE);
        shaderSearchField.setStyle(fieldStyle());
        shaderSearchField.textProperty().addListener((obs, o, n) -> filterList(shadersListBox, n));

        ScrollPane scroll = scrollPane();
        shadersListBox = new VBox(6);
        shadersListBox.setAlignment(Pos.TOP_LEFT);
        scroll.setContent(shadersListBox);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        page.getChildren().addAll(header, shaderSearchField, scroll);
        Platform.runLater(() -> {
            scanShaders();
            countBadge.setText(String.valueOf(shadersListBox.getChildren().size()));
        });

        shadersListBox.getChildren().addListener((javafx.collections.ListChangeListener<Node>) c ->
                countBadge.setText(String.valueOf(shadersListBox.getChildren().size())));

        return page;
    }

    private void scanShaders() {
        shadersListBox.getChildren().clear();
        Path dir = installer.getShadersDir();
        if (!Files.isDirectory(dir)) { try { Files.createDirectories(dir); } catch (Exception ignored) {} return; }
        try (var files = Files.list(dir)) {
            List<Path> sorted = files.filter(p -> { String n = p.getFileName().toString(); return n.endsWith(".zip") || n.endsWith(".rar") || Files.isDirectory(p); })
                    .sorted().toList();
            for (int i = 0; i < sorted.size(); i++) {
                addShaderToList(sorted.get(i).getFileName().toString(), i);
            }
        } catch (Exception ignored) {}
    }

    private void addShaderToList(String shaderName, int animDelay) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPrefHeight(44);
        card.setPadding(new Insets(0, 16, 0, 16));
        card.setStyle(cardStyle());
        card.setUserData(shaderName.toLowerCase());

        card.setOpacity(0);
        card.setTranslateY(10);

        Label name = new Label(shaderName);
        name.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        name.setTextFill(Color.web("#DDDDDD"));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Button del = trashButton();
        del.setOnAction(e -> {
            if (confirmDialog("Supprimer le shader ?", "Voulez-vous vraiment supprimer " + shaderName + " ?")) {
                try {
                    Path p = installer.getShadersDir().resolve(shaderName);
                    if (Files.isDirectory(p)) {
                        try (var walk = Files.walk(p)) { walk.sorted(Comparator.reverseOrder()).forEach(f -> { try { Files.delete(f); } catch (Exception ignored) {} }); }
                    } else Files.deleteIfExists(p);
                } catch (Exception ignored) {}
                FadeTransition ft = new FadeTransition(Duration.millis(200), card);
                ft.setToValue(0);
                ft.setOnFinished(ev -> shadersListBox.getChildren().remove(card));
                ft.play();
                showToast("Shader supprime");
            }
        });
        card.getChildren().addAll(name, spacer, del);
        shadersListBox.getChildren().add(card);
        addCardHover(card);

        PauseTransition delay = new PauseTransition(Duration.millis(animDelay * 40));
        delay.setOnFinished(e -> {
            FadeTransition fadeIn = new FadeTransition(Duration.millis(250), card);
            fadeIn.setToValue(1);
            TranslateTransition slide = new TranslateTransition(Duration.millis(250), card);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(fadeIn, slide).play();
        });
        delay.play();
    }

    private void onAddShader() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Importer un Shader");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Shader Pack", "*.zip", "*.rar"));
        File file = fc.showOpenDialog(primaryStage);
        if (file != null) {
            try {
                Files.copy(file.toPath(), installer.getShadersDir().resolve(file.getName()), StandardCopyOption.REPLACE_EXISTING);
                addShaderToList(file.getName(), 0);
                showToast("Shader importe : " + file.getName(), "#38EF7D");
            } catch (Exception e) { showToast("Erreur : " + e.getMessage(), "#FF3B30"); }
        }
    }

    private Pane createOptionsPage() {
        VBox page = new VBox(10);
        page.setPadding(new Insets(30, 40, 20, 40));
        page.setAlignment(Pos.TOP_LEFT);

        Label title = new Label("Paramètres du Launcher");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.WHITE);

        VBox ramCard = sectionCard();
        ramCard.setAlignment(Pos.CENTER);
        addHoverLift(ramCard);

        Label ramHeader = sectionTitle("MEMOIRE RAM");

        HBox ramCtrl = new HBox(20);
        ramCtrl.setAlignment(Pos.CENTER);
        ramCtrl.setPadding(new Insets(5, 0, 0, 0));

        minusBtn = new Button("−");
        minusBtn.setPrefSize(40, 40);
        minusBtn.setStyle(roundBtnStyle());
        minusBtn.setCursor(javafx.scene.Cursor.HAND);
        minusBtn.setOnAction(e -> changeRam(-1));
        minusBtn.setOnMouseEntered(ev -> animateScale(minusBtn, 1.1));
        minusBtn.setOnMouseExited(ev -> animateScale(minusBtn, 1.0));

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
        plusBtn.setOnMouseEntered(ev -> animateScale(plusBtn, 1.1));
        plusBtn.setOnMouseExited(ev -> animateScale(plusBtn, 1.0));

        ramCtrl.getChildren().addAll(minusBtn, ramDisplay, plusBtn);
        ramCard.getChildren().addAll(ramHeader, ramCtrl);

        VBox dispCard = sectionCard();
        addHoverLift(dispCard);

        Label dispHeader = sectionTitle("AFFICHAGE");

        HBox resRow = new HBox(8);
        resRow.setAlignment(Pos.CENTER_LEFT);
        customResCheck = styledCheckBox("Résolution personnalisée", cfgBool("custom_res"));
        customResCheck.setOnAction(e -> { config.addProperty("custom_res", customResCheck.isSelected()); saveConfig(); });
        Region rSp = new Region(); HBox.setHgrow(rSp, Priority.ALWAYS);

        resWField = smallField(String.valueOf(cfgInt("res_w")));
        resWField.textProperty().addListener((obs, o, n) -> { try { config.addProperty("res_w", Integer.parseInt(n)); saveConfig(); } catch (Exception ignored) {} });
        Label xLabel = new Label("×"); xLabel.setTextFill(Color.web("#555555"));
        resHField = smallField(String.valueOf(cfgInt("res_h")));
        resHField.textProperty().addListener((obs, o, n) -> { try { config.addProperty("res_h", Integer.parseInt(n)); saveConfig(); } catch (Exception ignored) {} });
        resRow.getChildren().addAll(customResCheck, rSp, resWField, xLabel, resHField);

        fullscreenCheck = styledCheckBox("Plein écran", cfgBool("fullscreen"));
        fullscreenCheck.setOnAction(e -> { config.addProperty("fullscreen", fullscreenCheck.isSelected()); saveConfig(); });

        dispCard.getChildren().addAll(dispHeader, resRow, fullscreenCheck);

        VBox prefCard = sectionCard();
        addHoverLift(prefCard);
        keepOpenCheck = styledCheckBox("Rester ouvert après lancement", cfgBool("keep_launcher_open"));
        keepOpenCheck.setOnAction(e -> { config.addProperty("keep_launcher_open", keepOpenCheck.isSelected()); saveConfig(); });
        prefCard.getChildren().add(keepOpenCheck);

        HBox toolsCard = new HBox(10);
        toolsCard.setPadding(new Insets(12, 16, 12, 16));
        toolsCard.setAlignment(Pos.CENTER_LEFT);
        toolsCard.setStyle("-fx-background-color: #121212; -fx-background-radius: 10;");
        Label toolsLabel = sectionTitle("OUTILS");
        Region tSp = new Region(); HBox.setHgrow(tSp, Priority.ALWAYS);
        Button folderBtn = actionButton("Dossier", () -> { try { Desktop.getDesktop().open(installer.getMinecraftDir().toFile()); } catch (Exception ignored) {} });
        Button logsBtn = actionButton("Logs", this::copyLogs);
        toolsCard.getChildren().addAll(toolsLabel, tSp, folderBtn, logsBtn);

        Region bottomSpacer = new Region(); VBox.setVgrow(bottomSpacer, Priority.ALWAYS);

        Button uninstallBtn = new Button("Désinstaller complètement");
        uninstallBtn.setPrefHeight(34);
        uninstallBtn.setMaxWidth(Double.MAX_VALUE);
        uninstallBtn.setCursor(javafx.scene.Cursor.HAND);
        uninstallBtn.setStyle("-fx-text-fill: #FF3B30; -fx-border-color: rgba(255,59,48,0.2); " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-background-color: rgba(255,59,48,0.05); " +
                "-fx-font-size: 11; -fx-font-weight: bold;");
        uninstallBtn.setOnMouseEntered(e -> {
            uninstallBtn.setStyle("-fx-text-fill: #FF3B30; -fx-border-color: rgba(255,59,48,0.5); " +
                    "-fx-border-radius: 8; -fx-background-radius: 8; -fx-background-color: rgba(255,59,48,0.12); " +
                    "-fx-font-size: 11; -fx-font-weight: bold;");
        });
        uninstallBtn.setOnMouseExited(e -> {
            uninstallBtn.setStyle("-fx-text-fill: #FF3B30; -fx-border-color: rgba(255,59,48,0.2); " +
                    "-fx-border-radius: 8; -fx-background-radius: 8; -fx-background-color: rgba(255,59,48,0.05); " +
                    "-fx-font-size: 11; -fx-font-weight: bold;");
        });
        uninstallBtn.setOnAction(e -> uninstall(uninstallBtn));

        page.getChildren().addAll(title, ramCard, dispCard, prefCard, toolsCard, bottomSpacer, uninstallBtn);
        updateRamButtons();
        return page;
    }

    private Pane createStatsPage() {
        HBox page = new HBox(25);
        page.setPadding(new Insets(35, 40, 30, 40));
        page.setAlignment(Pos.TOP_CENTER);

        VBox left = new VBox(12);
        HBox.setHgrow(left, Priority.ALWAYS);

        statsNotRunning = new Label("Minecraft n'est pas lancé.\nLancez le jeu pour voir les stats en temps réel.");
        statsNotRunning.setWrapText(true);
        statsNotRunning.setTextFill(Color.web("#444444"));
        statsNotRunning.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        statsNotRunning.setAlignment(Pos.CENTER);
        statsNotRunning.setMaxWidth(Double.MAX_VALUE);
        statsNotRunning.setStyle("-fx-alignment: center;");

        statsContainer = new VBox(10);
        statsContainer.setVisible(false);

        VBox cpuCard = statCard("UTILISATION CPU", "0%", "#FF9500");
        cpuValueLabel = (Label) cpuCard.getUserData();
        VBox ramStatCard = statCard("RAM CONSOMMEE", "0 MB", "#38EF7D");
        ramValueLabel = (Label) ramStatCard.getUserData();
        VBox playCard = statCard("TEMPS DE SESSION", "00:00:00", "#5865F2");
        playtimeValueLabel = (Label) playCard.getUserData();

        statsContainer.getChildren().addAll(cpuCard, ramStatCard, playCard);
        left.getChildren().addAll(statsNotRunning, statsContainer);

        VBox rightPanel = new VBox(12);
        rightPanel.setPrefWidth(250);
        rightPanel.setPadding(new Insets(20));
        rightPanel.setStyle("-fx-background-color: #111111; -fx-background-radius: 16;");
        addHoverLift(rightPanel);

        Label rpLabel = new Label("Stats MC");
        rpLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        rpLabel.setTextFill(Color.WHITE);
        Label rpDesc = new Label("Suivez les performances de votre Minecraft en temps réel.");
        rpDesc.setWrapText(true);
        rpDesc.setTextFill(Color.web("#555555"));
        rpDesc.setFont(Font.font("Segoe UI", 11));
        Region rpSp = new Region(); VBox.setVgrow(rpSp, Priority.ALWAYS);
        rightPanel.getChildren().addAll(rpLabel, rpDesc, rpSp);

        page.getChildren().addAll(left, rightPanel);
        return page;
    }

    private VBox statCard(String titleText, String valueText, String color) {
        VBox card = new VBox(4);
        card.setPrefHeight(75);
        card.setPadding(new Insets(14, 20, 14, 20));
        card.setStyle("-fx-background-color: #121212; -fx-background-radius: 12; -fx-border-color: #1A1A1A; -fx-border-radius: 12;");
        addHoverLift(card);
        Label t = new Label(titleText);
        t.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        t.setTextFill(Color.web("#666666"));
        Label v = new Label(valueText);
        v.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        v.setTextFill(Color.web(color));
        card.getChildren().addAll(t, v);
        card.setUserData(v);
        return card;
    }

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
                "-fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        VBox.setVgrow(consoleArea, Priority.ALWAYS);

        page.getChildren().addAll(title, consoleArea);
        return page;
    }

    private void loadHeaderIcon() {
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

    private void fetchNews() {
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
                        startLaunchPulse();
                    }
                    default -> {
                        statusLabel.setText("Mise à jour requise");
                        statusLabel.setTextFill(Color.web("#FF9500"));
                        installBtn.setDisable(false);
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
                    startLaunchPulse();
                } else {
                    statusLabel.setText("Erreur lors de l'installation");
                    statusLabel.setTextFill(Color.web("#DC3545"));
                    installBtn.setDisable(false);
                    setProgress(0);
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

    private void launch() {
        String user = usernameField.getText().trim();
        if (user.isEmpty()) { statusLabel.setText("Pseudo requis"); statusLabel.setTextFill(Color.web("#FF9500")); return; }
        if (installer.getInstalledForgeVersion() == null) return;

        stopLaunchPulse();
        launchBtn.setDisable(true);
        int ram = cfgInt("ram_gb");
        logToConsole("Utilisateur: " + user);
        logToConsole("RAM: " + ram + " Go\n");

        List<String> cmd = installer.buildLaunchCommand(user, ram, cfgStr("jvm_args"),
                cfgBool("custom_res"), cfgInt("res_w"), cfgInt("res_h"), cfgBool("fullscreen"));
        if (cmd.isEmpty()) { logToConsole("Impossible de construire la commande"); launchBtn.setDisable(false); return; }

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(installer.getMinecraftDir().toFile());
            pb.redirectErrorStream(true);
            minecraftProcess = pb.start();
            gameRunning = true;
            gameStartTime = System.currentTimeMillis();
            statusLabel.setText("En cours...");
            statusLabel.setTextFill(Color.web("#38EF7D"));

            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(minecraftProcess.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) logToConsole(line);
                } catch (Exception ignored) {}
                try { minecraftProcess.waitFor(); } catch (Exception ignored) {}
                Platform.runLater(() -> {
                    logToConsole("\nMinecraft ferme");
                    statusLabel.setText("Prêt"); statusLabel.setTextFill(Color.web("#666666"));
                    launchBtn.setDisable(false);
                    gameRunning = false; gameStartTime = 0;
                    startLaunchPulse();
                    if (!primaryStage.isShowing()) primaryStage.show();
                });
            });
            reader.setDaemon(true);
            reader.start();

            if (!cfgBool("keep_launcher_open")) {
                new Timeline(new KeyFrame(Duration.seconds(3), e -> primaryStage.hide())).play();
            }
        } catch (Exception e) { logToConsole("Erreur lancement: " + e.getMessage()); launchBtn.setDisable(false); }
    }

    private void uninstall(Button btn) {
        if (!confirmDialog("Désinstaller le Modpack + Forge", "Es-tu sûr de vouloir supprimer tous les mods et les versions Forge ? Cela ne va pas désinstaller l'application, fin t'as juste à supprimer le .exe ou le .jar quoi..")) return;
        btn.setDisable(true);
        Thread t = new Thread(() -> {
            boolean ok = installer.uninstall(this::logToConsole);
            Platform.runLater(() -> {
                btn.setDisable(false);
                if (ok) {
                    stopLaunchPulse();
                    launchBtn.setDisable(true); installBtn.setDisable(false);
                    installBtn.setText("Installer les mods");
                    statusLabel.setText("Installation requise"); statusLabel.setTextFill(Color.web("#FF9500"));
                    scanMods();
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

    private void copyLogs() {
        Path logsFile = installer.getMinecraftDir().resolve("logs").resolve("latest.log");
        if (Files.exists(logsFile)) {
            try {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(Files.readString(logsFile));
                Clipboard.getSystemClipboard().setContent(cc);
                logToConsole("Logs copies dans le presse-papier !");
            } catch (Exception e) { logToConsole("Erreur copie logs: " + e.getMessage()); }
        } else { logToConsole("Aucun fichier de logs trouve"); }
    }

    private void updateStats() {
        if (!gameRunning || minecraftProcess == null || !minecraftProcess.isAlive()) {
            statsNotRunning.setVisible(true); statsContainer.setVisible(false); return;
        }
        statsNotRunning.setVisible(false); statsContainer.setVisible(true);
        try {
            long pid = minecraftProcess.pid();
            SystemInfo si = new SystemInfo();
            OSProcess proc = si.getOperatingSystem().getProcess((int) pid);
            if (proc != null) {
                cpuValueLabel.setText(String.format("%.1f%%", proc.getProcessCpuLoadCumulative() * 100));
                ramValueLabel.setText(String.format("%.0f MB", proc.getResidentSetSize() / 1_048_576.0));
            }
            if (gameStartTime > 0) {
                long s = (System.currentTimeMillis() - gameStartTime) / 1000;
                playtimeValueLabel.setText(String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60));
            }
        } catch (Exception ignored) {}
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
        saveConfig(); updateRamButtons();
    }

    private void updateRamButtons() {
        minusBtn.setDisable(cfgInt("ram_gb") <= 2);
        plusBtn.setDisable(cfgInt("ram_gb") >= 16);
    }

    private void animateScale(Node node, double target) {
        ScaleTransition st = new ScaleTransition(Duration.millis(150), node);
        st.setToX(target);
        st.setToY(target);
        st.setInterpolator(Interpolator.EASE_BOTH);
        st.play();
    }

    private void addHoverLift(Region node) {
        node.setOnMouseEntered(e -> {
            TranslateTransition tt = new TranslateTransition(Duration.millis(200), node);
            tt.setToY(-2);
            tt.setInterpolator(Interpolator.EASE_OUT);
            tt.play();
        });
        node.setOnMouseExited(e -> {
            TranslateTransition tt = new TranslateTransition(Duration.millis(200), node);
            tt.setToY(0);
            tt.setInterpolator(Interpolator.EASE_OUT);
            tt.play();
        });
    }

    private void addCardHover(HBox card) {
        String base = cardStyle();
        String hover = "-fx-background-color: #161616; -fx-background-radius: 10; -fx-border-color: #252525; -fx-border-radius: 10;";
        card.setOnMouseEntered(e -> {
            card.setStyle(hover);
            TranslateTransition tt = new TranslateTransition(Duration.millis(150), card);
            tt.setToX(3);
            tt.setInterpolator(Interpolator.EASE_OUT);
            tt.play();
        });
        card.setOnMouseExited(e -> {
            card.setStyle(base);
            TranslateTransition tt = new TranslateTransition(Duration.millis(150), card);
            tt.setToX(0);
            tt.setInterpolator(Interpolator.EASE_OUT);
            tt.play();
        });
    }

    private void addButtonHover(Button btn, String baseBg, String hoverBg, String textColor) {
        String base = "-fx-background-color: " + baseBg + "; -fx-text-fill: " + textColor + "; -fx-border-color: #252525; " +
                "-fx-border-radius: 12; -fx-background-radius: 12; -fx-font-weight: bold;";
        String hover = "-fx-background-color: " + hoverBg + "; -fx-text-fill: " + textColor + "; -fx-border-color: #333333; " +
                "-fx-border-radius: 12; -fx-background-radius: 12; -fx-font-weight: bold;";
        btn.setOnMouseEntered(e -> { btn.setStyle(hover); animateScale(btn, 1.02); });
        btn.setOnMouseExited(e -> { btn.setStyle(base); animateScale(btn, 1.0); });
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
        VBox card = new VBox(8);
        card.setPadding(new Insets(14, 18, 14, 18));
        card.setStyle("-fx-background-color: #121212; -fx-background-radius: 10;");
        return card;
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
        btn.setOnMouseEntered(e -> {
            btn.setStyle("-fx-background-color: #252525; -fx-text-fill: #FFFFFF; -fx-background-radius: 8; " +
                    "-fx-padding: 6 14 6 14; -fx-font-size: 10;");
        });
        btn.setOnMouseExited(e -> {
            btn.setStyle("-fx-background-color: #1A1A1A; -fx-text-fill: #CCCCCC; -fx-background-radius: 8; " +
                    "-fx-padding: 6 14 6 14; -fx-font-size: 10;");
        });
        btn.setOnAction(e -> action.run());
        return btn;
    }

    private Button trashButton() {
        SVGPath trashIcon = new SVGPath();
        trashIcon.setContent("M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z");
        trashIcon.setFill(Color.web("#555555"));
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
            trashIcon.setFill(Color.web("#FF3B30"));
            animateScale(del, 1.15);
        });
        del.setOnMouseExited(e -> {
            del.setStyle("-fx-background-color: transparent; -fx-background-radius: 17;");
            trashIcon.setFill(Color.web("#555555"));
            animateScale(del, 1.0);
        });
        return del;
    }

    private ScrollPane scrollPane() {
        ScrollPane sp = new ScrollPane();
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        return sp;
    }

    private Region spacer(double h) {
        Region r = new Region(); r.setPrefHeight(h); r.setMinHeight(h); return r;
    }

    private void filterList(VBox container, String query) {
        String q = query.toLowerCase();
        for (Node node : container.getChildren()) {
            if (node.getUserData() instanceof String name) {
                boolean match = name.contains(q);
                node.setVisible(match);
                node.setManaged(match);
            }
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
