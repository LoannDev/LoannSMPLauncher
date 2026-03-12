package com.loannsmp.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Handles all Minecraft / Forge installation logic:
 *  - Detecting the .minecraft directory
 *  - Checking if Forge is installed
 *  - Downloading & extracting the modpack ZIP
 *  - Running the Forge installer
 *  - Building the Minecraft launch command
 *  - Uninstalling mods & Forge versions
 */
public class MCInstaller {

    private static final String MC_VERSION = "1.20.1";
    private static final String FORGE_INSTALLER_URL_TEMPLATE =
            "https://maven.minecraftforge.net/net/minecraftforge/forge/%s/forge-%s-installer.jar";
    // Forge promotion API to find latest Forge for a given MC version
    private static final String FORGE_PROMOTIONS_URL =
            "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";

    private final OkHttpClient http;
    private final Gson gson = new Gson();

    private final Path minecraftDir;
    private final Path modsDir;
    private final Path shadersDir;
    private final Path versionsDir;
    private final Path versionFile;

    private String installedForgeVersion; // e.g. "1.20.1-47.3.0"

    public MCInstaller() {
        this.http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
        this.minecraftDir = getMinecraftDirectory();
        this.modsDir = minecraftDir.resolve("mods");
        this.shadersDir = minecraftDir.resolve("shaderpacks");
        this.versionsDir = minecraftDir.resolve("versions");
        this.versionFile = minecraftDir.resolve("loannsmp_version.json");
    }

    // ── Getters ─────────────────────────────────────────────────────────

    public Path getMinecraftDir() { return minecraftDir; }
    public Path getModsDir() { return modsDir; }
    public Path getShadersDir() { return shadersDir; }
    public String getInstalledForgeVersion() { return installedForgeVersion; }

    // ── Minecraft directory detection ───────────────────────────────────

    public static Path getMinecraftDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return Path.of(System.getenv("APPDATA"), ".minecraft");
        } else if (os.contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library", "Application Support", "minecraft");
        } else {
            return Path.of(System.getProperty("user.home"), ".minecraft");
        }
    }

    // ── HTTP helper ─────────────────────────────────────────────────────

    public String fetchText(String url) throws IOException {
        Request req = new Request.Builder().url(url).build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code());
            return resp.body().string().trim();
        }
    }

    public byte[] fetchBytes(String url) throws IOException {
        Request req = new Request.Builder().url(url).build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code());
            return resp.body().bytes();
        }
    }

    /**
     * Downloads a URL to a byte array, calling progressCallback(downloaded, total)
     */
    public byte[] fetchBytesWithProgress(String url, BiConsumer<Long, Long> progressCallback) throws IOException {
        Request req = new Request.Builder().url(url).build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code());
            long total = resp.body().contentLength();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            long downloaded = 0;
            try (InputStream in = resp.body().byteStream()) {
                int n;
                while ((n = in.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                    downloaded += n;
                    if (progressCallback != null) progressCallback.accept(downloaded, total);
                }
            }
            return baos.toByteArray();
        }
    }

    // ── MD5 helper ──────────────────────────────────────────────────────

    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ── Find the latest Forge version for MC_VERSION ────────────────────

    public String findForgeVersion() throws IOException {
        String json = fetchText(FORGE_PROMOTIONS_URL);
        JsonObject promos = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("promos");
        // Try recommended first, then latest
        String key = MC_VERSION + "-recommended";
        if (promos.has(key)) {
            return MC_VERSION + "-" + promos.get(key).getAsString();
        }
        key = MC_VERSION + "-latest";
        if (promos.has(key)) {
            return MC_VERSION + "-" + promos.get(key).getAsString();
        }
        return null;
    }

    /**
     * Returns the version ID as it appears in the versions/ folder, e.g. "1.20.1-forge-47.3.0"
     */
    public String forgeToInstalledVersion(String forgeVersion) {
        // forgeVersion = "1.20.1-47.3.0"
        // installed id = "1.20.1-forge-47.3.0"
        String[] parts = forgeVersion.split("-", 2);
        if (parts.length == 2) return parts[0] + "-forge-" + parts[1];
        return forgeVersion;
    }

    // ── Check if a Forge version is installed ───────────────────────────

    public boolean isForgeInstalled(String forgeVersion) {
        String verId = forgeToInstalledVersion(forgeVersion);
        Path verDir = versionsDir.resolve(verId);
        return Files.isDirectory(verDir) && Files.exists(verDir.resolve(verId + ".json"));
    }

    // ── Full installation check (matches UpdateChecker in Python) ──────

    /**
     * Returns: "up_to_date", "needs_update", or "unavailable"
     */
    public String checkInstallation(String baseUrl, Consumer<String> log) {
        try {
            String modpackTxt = fetchText(baseUrl + "modpack.txt");
            if (modpackTxt.equalsIgnoreCase("none")) {
                if (log != null) log.accept("⚠️ Le modpack n'est pas encore disponible");
                return "unavailable";
            }

            // Check Forge
            boolean forgeOk = false;
            try {
                String forgeVer = findForgeVersion();
                if (forgeVer != null && isForgeInstalled(forgeVer)) {
                    installedForgeVersion = forgeVer;
                    forgeOk = true;
                    if (log != null) log.accept("✅ Forge " + forgeVer + " détecté");
                }
            } catch (Exception e) {
                if (log != null) log.accept("⚠️ Erreur vérification Forge: " + e.getMessage());
            }

            // Check modpack hash
            String remoteHash = md5(modpackTxt);
            String localHash = null;
            if (Files.exists(versionFile)) {
                try {
                    String content = Files.readString(versionFile);
                    JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
                    localHash = obj.has("modpack_hash") ? obj.get("modpack_hash").getAsString() : null;
                } catch (Exception ignored) {}
            }

            boolean modsExist = Files.isDirectory(modsDir) &&
                    modsDir.toFile().listFiles((d, n) -> n.endsWith(".jar")) != null &&
                    modsDir.toFile().listFiles((d, n) -> n.endsWith(".jar")).length > 0;

            if (localHash != null && localHash.equals(remoteHash) && modsExist && forgeOk) {
                if (log != null) log.accept("✅ Installation à jour !");
                return "up_to_date";
            } else {
                if (!modsExist && log != null) log.accept("⚠️ Aucun mod installé");
                else if (!remoteHash.equals(localHash) && log != null) log.accept("⚠️ Mise à jour disponible");
                else if (!forgeOk && log != null) log.accept("⚠️ Forge non installé");
                return "needs_update";
            }
        } catch (Exception e) {
            if (log != null) log.accept("⚠️ Impossible de vérifier: " + e.getMessage());
            return "needs_update";
        }
    }

    // ── Install mods + Forge ────────────────────────────────────────────

    /**
     * Full install flow. Calls progressCallback(percent, statusText) and log(message).
     * Returns true on success.
     */
    public boolean install(String baseUrl, BiConsumer<Integer, String> progress, Consumer<String> log, BooleanSupplier running) {
        try {
            log.accept("======================================================================");
            log.accept("📦 TÉLÉCHARGEMENT DES MODS");
            log.accept("======================================================================");

            progress.accept(5, "Récupération du lien...");
            log.accept("Lecture de modpack.txt...");

            String modpackUrl = fetchText(baseUrl + "modpack.txt");
            if (modpackUrl.equalsIgnoreCase("none")) {
                log.accept("❌ Le modpack n'est pas encore sorti");
                return false;
            }
            if (!modpackUrl.startsWith("http://") && !modpackUrl.startsWith("https://")) {
                log.accept("❌ URL invalide dans modpack.txt: " + modpackUrl);
                return false;
            }
            log.accept("✅ URL récupérée avec succès");

            // Download modpack ZIP
            progress.accept(10, "Téléchargement...");
            log.accept("Téléchargement du modpack...");

            byte[] zipData = fetchBytesWithProgress(modpackUrl, (downloaded, total) -> {
                // (optional: could update progress here)
            });

            if (!running.getAsBoolean()) return false;

            log.accept(String.format("✅ Téléchargement terminé: %.2f MB", zipData.length / 1_048_576.0));

            // Extract mods
            progress.accept(30, "Extraction...");
            Files.createDirectories(modsDir);

            // Delete old mods
            File[] oldMods = modsDir.toFile().listFiles((d, n) -> n.endsWith(".jar"));
            if (oldMods != null && oldMods.length > 0) {
                log.accept("Suppression de " + oldMods.length + " ancien(s) mod(s)...");
                for (File f : oldMods) f.delete();
            }

            log.accept("Extraction du ZIP...");
            int count = 0;
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (!running.getAsBoolean()) return false;
                    String name = entry.getName();
                    if (name.endsWith(".jar") && !name.startsWith("__MACOSX")) {
                        String fileName = Path.of(name).getFileName().toString();
                        if (!fileName.isEmpty()) {
                            Files.copy(zis, modsDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                            log.accept("  ✓ " + fileName);
                            count++;
                        }
                    }
                    zis.closeEntry();
                }
            }

            if (count == 0) {
                log.accept("❌ Aucun fichier .jar trouvé dans le ZIP");
                return false;
            }
            log.accept("\n✅ " + count + " mod(s) installé(s)");

            // Save version hash
            try {
                String hash = md5(modpackUrl);
                JsonObject obj = new JsonObject();
                obj.addProperty("modpack_hash", hash);
                obj.addProperty("url", modpackUrl);
                Files.writeString(versionFile, gson.toJson(obj));
            } catch (Exception ignored) {}

            // Install Forge
            progress.accept(50, "Recherche Forge...");
            log.accept("\n🔍 RECHERCHE DE FORGE");

            String forgeVer = findForgeVersion();
            if (forgeVer == null) {
                log.accept("❌ Forge introuvable pour " + MC_VERSION);
                return false;
            }
            log.accept("✅ Forge: " + forgeVer);

            if (isForgeInstalled(forgeVer)) {
                installedForgeVersion = forgeVer;
                log.accept("✅ Forge déjà installé");
                progress.accept(100, "Terminé !");
                return true;
            }

            // Download & run Forge installer
            progress.accept(60, "Installation de Minecraft + Forge (peut prendre bcp de temps)");
            log.accept("\n🔨 INSTALLATION DE FORGE (peut prendre bcp de temps)");

            String forgeInstallerUrl = String.format(FORGE_INSTALLER_URL_TEMPLATE, forgeVer, forgeVer);
            log.accept("Téléchargement du Forge installer...");
            byte[] installerBytes = fetchBytes(forgeInstallerUrl);

            Path installerPath = minecraftDir.resolve("forge-installer-temp.jar");
            Files.write(installerPath, installerBytes);
            log.accept("✅ Forge installer téléchargé");

            // Run the Forge installer in headless mode
            log.accept("Exécution de l'installeur Forge...");
            String javaExe = ProcessHandle.current().info().command().orElse("java");
            // Try to use the same java that's running us; fallback to "java"
            ProcessBuilder pb = new ProcessBuilder(
                    javaExe, "-jar", installerPath.toString(), "--installClient", minecraftDir.toString()
            );
            pb.directory(minecraftDir.toFile());
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!running.getAsBoolean()) { proc.destroyForcibly(); return false; }
                    log.accept(line);
                }
            }

            int exitCode = proc.waitFor();
            // Clean up installer
            try { Files.deleteIfExists(installerPath); } catch (Exception ignored) {}

            if (exitCode != 0) {
                log.accept("❌ Le Forge installer a échoué (code " + exitCode + ")");
                return false;
            }

            installedForgeVersion = forgeVer;
            log.accept("\n🎉 INSTALLATION TERMINÉE");
            progress.accept(100, "Terminé !");
            return true;

        } catch (Exception e) {
            log.accept("❌ ERREUR: " + e.getMessage());
            return false;
        }
    }

    // ── Uninstall ───────────────────────────────────────────────────────

    public boolean uninstall(Consumer<String> log) {
        try {
            log.accept("\n🗑️  DÉSINSTALLATION...");

            if (Files.isDirectory(modsDir)) {
                File[] jars = modsDir.toFile().listFiles((d, n) -> n.endsWith(".jar"));
                int count = jars != null ? jars.length : 0;
                // Delete all jars
                if (jars != null) for (File f : jars) f.delete();
                log.accept("✅ " + count + " mods supprimés");
            }

            if (Files.isDirectory(versionsDir)) {
                try (var dirs = Files.list(versionsDir)) {
                    dirs.filter(p -> p.getFileName().toString().toLowerCase().contains("forge"))
                        .forEach(p -> {
                            try {
                                deleteDirectory(p);
                                log.accept("✅ " + p.getFileName() + " supprimé");
                            } catch (Exception e) {
                                log.accept("⚠️ Impossible de supprimer " + p.getFileName());
                            }
                        });
                }
            }

            Files.deleteIfExists(versionFile);
            installedForgeVersion = null;
            log.accept("✅ Terminé");
            return true;
        } catch (Exception e) {
            log.accept("❌ Erreur: " + e.getMessage());
            return false;
        }
    }

    // ── Build the Minecraft launch command ──────────────────────────────

    /**
     * Merges a Forge version JSON with its parent (vanilla) version JSON,
     * mirroring minecraft_launcher_lib's inherit_json() logic.
     */
    private JsonObject inheritJson(JsonObject forgeJson) throws IOException {
        if (!forgeJson.has("inheritsFrom")) return forgeJson;

        String parentVer = forgeJson.get("inheritsFrom").getAsString();
        Path parentPath = versionsDir.resolve(parentVer).resolve(parentVer + ".json");
        if (!Files.exists(parentPath)) return forgeJson;

        JsonObject parent = JsonParser.parseString(Files.readString(parentPath)).getAsJsonObject();

        // Build set of lib names already in Forge JSON (without version) to avoid duplicates
        Set<String> forgeLibNames = new HashSet<>();
        if (forgeJson.has("libraries")) {
            for (JsonElement el : forgeJson.getAsJsonArray("libraries")) {
                String n = el.getAsJsonObject().get("name").getAsString();
                String libKey = libNameWithoutVersion(n);
                forgeLibNames.add(libKey);
            }
        }

        // Merge libraries: Forge libs first, then parent libs not already present
        JsonArray mergedLibs = forgeJson.has("libraries")
                ? forgeJson.getAsJsonArray("libraries").deepCopy()
                : new JsonArray();
        if (parent.has("libraries")) {
            for (JsonElement el : parent.getAsJsonArray("libraries")) {
                String n = el.getAsJsonObject().get("name").getAsString();
                if (!forgeLibNames.contains(libNameWithoutVersion(n))) {
                    mergedLibs.add(el);
                }
            }
        }
        parent.add("libraries", mergedLibs);

        // Merge arguments (dict): for each sub-key (jvm, game), prepend Forge's entries
        if (forgeJson.has("arguments") && parent.has("arguments")) {
            JsonObject fArgs = forgeJson.getAsJsonObject("arguments");
            JsonObject pArgs = parent.getAsJsonObject("arguments");
            for (String key : new String[]{"jvm", "game"}) {
                if (fArgs.has(key)) {
                    JsonArray merged = fArgs.getAsJsonArray(key).deepCopy();
                    if (pArgs.has(key)) {
                        for (JsonElement el : pArgs.getAsJsonArray(key)) merged.add(el);
                    }
                    pArgs.add(key, merged);
                }
            }
        } else if (forgeJson.has("arguments")) {
            parent.add("arguments", forgeJson.getAsJsonObject("arguments").deepCopy());
        }

        // Override scalar fields from Forge JSON
        for (String key : forgeJson.keySet()) {
            if (key.equals("libraries") || key.equals("arguments")) continue;
            parent.add(key, forgeJson.get(key));
        }

        return parent;
    }

    /** Returns "group:artifact" from a Maven coordinate "group:artifact:version[:classifier]" */
    private String libNameWithoutVersion(String mavenName) {
        String[] parts = mavenName.split(":");
        return parts.length >= 2 ? parts[0] + ":" + parts[1] : mavenName;
    }

    /**
     * Builds the full command to launch Minecraft with Forge.
     * Mirrors minecraft_launcher_lib.command.get_minecraft_command().
     */
    public List<String> buildLaunchCommand(String username, int ramGb, String extraJvmArgs,
                                           boolean customRes, int resW, int resH, boolean fullscreen) {
        if (installedForgeVersion == null) return Collections.emptyList();

        String verId = forgeToInstalledVersion(installedForgeVersion);
        Path versionDir = versionsDir.resolve(verId);
        Path versionJson = versionDir.resolve(verId + ".json");

        try {
            String jsonStr = Files.readString(versionJson);
            JsonObject forgeRoot = JsonParser.parseString(jsonStr).getAsJsonObject();

            // Merge with parent (vanilla) JSON — critical for Forge
            JsonObject root = inheritJson(forgeRoot);

            // Main class (Forge overrides this)
            String mainClass = root.get("mainClass").getAsString();

            // Collect libraries → classpath
            List<String> classpathEntries = new ArrayList<>();
            if (root.has("libraries")) {
                for (JsonElement el : root.getAsJsonArray("libraries")) {
                    JsonObject lib = el.getAsJsonObject();
                    // Check rules if present
                    if (lib.has("rules") && !evaluateRules(lib.getAsJsonArray("rules"))) continue;

                    if (lib.has("downloads") && lib.getAsJsonObject("downloads").has("artifact")) {
                        String path = lib.getAsJsonObject("downloads").getAsJsonObject("artifact").get("path").getAsString();
                        Path libFile = minecraftDir.resolve("libraries").resolve(path);
                        if (Files.exists(libFile)) classpathEntries.add(libFile.toString());
                    } else if (lib.has("name")) {
                        String mavenPath = mavenNameToPath(lib.get("name").getAsString());
                        Path libFile = minecraftDir.resolve("libraries").resolve(mavenPath);
                        if (Files.exists(libFile)) classpathEntries.add(libFile.toString());
                    }
                }
            }

            // Add the client jar (use "jar" field if present, else version id)
            String jarVersion = root.has("jar") ? root.get("jar").getAsString() : root.get("id").getAsString();
            Path clientJar = versionsDir.resolve(jarVersion).resolve(jarVersion + ".jar");
            if (Files.exists(clientJar)) {
                classpathEntries.add(clientJar.toString());
            }

            String sep = System.getProperty("path.separator");
            String classpath = String.join(sep, classpathEntries);
            String nativesDir = versionDir.resolve("natives").toString();

            // Asset index
            String assetIndex;
            if (root.has("assetIndex")) {
                assetIndex = root.getAsJsonObject("assetIndex").get("id").getAsString();
            } else if (root.has("assets")) {
                assetIndex = root.get("assets").getAsString();
            } else {
                assetIndex = root.get("id").getAsString();
            }

            String versionType = root.has("type") ? root.get("type").getAsString() : "release";

            // Find java
            String javaExe = findJava();

            // Build command
            List<String> cmd = new ArrayList<>();
            cmd.add(javaExe);

            // User JVM args first (RAM, custom args)
            cmd.add("-Xmx" + ramGb + "G");
            cmd.add("-Xms" + (ramGb / 2) + "G");
            if (extraJvmArgs != null && !extraJvmArgs.isBlank()) {
                for (String arg : extraJvmArgs.split("\\s+")) cmd.add(arg);
            }
            if (fullscreen) cmd.add("-fullscreen");

            // JVM args from version JSON
            // KEY: when arguments.jvm is present, it already contains -p (module-path),
            // --add-modules, --add-opens, AND ${classpath}. We must NOT add -cp separately.
            boolean hasJvmArgs = root.has("arguments")
                    && root.getAsJsonObject("arguments").has("jvm");

            if (hasJvmArgs) {
                for (JsonElement arg : root.getAsJsonObject("arguments").getAsJsonArray("jvm")) {
                    if (arg.isJsonPrimitive()) {
                        cmd.add(replacePlaceholders(arg.getAsString(), verId, classpath,
                                nativesDir, sep, assetIndex, username, versionType));
                    } else if (arg.isJsonObject()) {
                        // Handle rule-based arguments (e.g. OS-specific)
                        JsonObject ruleArg = arg.getAsJsonObject();
                        if (ruleArg.has("rules") && !evaluateRules(ruleArg.getAsJsonArray("rules"))) continue;
                        if (ruleArg.has("value")) {
                            if (ruleArg.get("value").isJsonPrimitive()) {
                                cmd.add(replacePlaceholders(ruleArg.get("value").getAsString(),
                                        verId, classpath, nativesDir, sep, assetIndex, username, versionType));
                            } else {
                                for (JsonElement v : ruleArg.getAsJsonArray("value")) {
                                    cmd.add(replacePlaceholders(v.getAsString(),
                                            verId, classpath, nativesDir, sep, assetIndex, username, versionType));
                                }
                            }
                        }
                    }
                }
            } else {
                // Older format without arguments.jvm — add classpath manually
                cmd.add("-Djava.library.path=" + nativesDir);
                cmd.add("-cp");
                cmd.add(classpath);
            }

            cmd.add(mainClass);

            // Game arguments
            List<String> gameArgs = new ArrayList<>();
            if (root.has("arguments") && root.getAsJsonObject("arguments").has("game")) {
                for (JsonElement arg : root.getAsJsonObject("arguments").getAsJsonArray("game")) {
                    if (arg.isJsonPrimitive()) {
                        gameArgs.add(arg.getAsString());
                    }
                    // Rule-based game args (e.g. demo mode, custom resolution)
                    // We handle custom res manually below, so skip those rules
                }
            } else if (root.has("minecraftArguments")) {
                gameArgs.addAll(Arrays.asList(root.get("minecraftArguments").getAsString().split("\\s+")));
            }

            // Replace placeholders in game args
            for (int i = 0; i < gameArgs.size(); i++) {
                gameArgs.set(i, replacePlaceholders(gameArgs.get(i), verId, classpath,
                        nativesDir, sep, assetIndex, username, versionType));
            }
            cmd.addAll(gameArgs);

            // Custom resolution
            if (customRes) {
                cmd.add("--width"); cmd.add(String.valueOf(resW));
                cmd.add("--height"); cmd.add(String.valueOf(resH));
            }

            return cmd;

        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /** Replace all known placeholders in a JVM/game argument string. */
    private String replacePlaceholders(String arg, String versionId, String classpath,
                                       String nativesDir, String sep, String assetIndex,
                                       String username, String versionType) {
        return arg
                .replace("${natives_directory}", nativesDir)
                .replace("${launcher_name}", "LoannSMP")
                .replace("${launcher_version}", "2.1.0")
                .replace("${classpath}", classpath)
                .replace("${classpath_separator}", sep)
                .replace("${library_directory}", minecraftDir.resolve("libraries").toString())
                .replace("${auth_player_name}", username)
                .replace("${version_name}", versionId)
                .replace("${game_directory}", minecraftDir.toString())
                .replace("${assets_root}", minecraftDir.resolve("assets").toString())
                .replace("${assets_index_name}", assetIndex)
                .replace("${auth_uuid}", "0")
                .replace("${auth_access_token}", "0")
                .replace("${user_type}", "msa")
                .replace("${version_type}", versionType)
                .replace("${user_properties}", "{}")
                .replace("${auth_session}", "0")
                .replace("${resolution_width}", "854")
                .replace("${resolution_height}", "480")
                .replace("${game_assets}", minecraftDir.resolve("assets").resolve("virtual").resolve("legacy").toString())
                .replace("${quickPlayPath}", "")
                .replace("${quickPlaySingleplayer}", "")
                .replace("${quickPlayMultiplayer}", "")
                .replace("${quickPlayRealms}", "");
    }

    /** Evaluate rules array — returns true if the current OS matches. */
    private boolean evaluateRules(JsonArray rules) {
        boolean allowed = false;
        String osName = System.getProperty("os.name").toLowerCase();
        String mcOs;
        if (osName.contains("win")) mcOs = "windows";
        else if (osName.contains("mac")) mcOs = "osx";
        else mcOs = "linux";

        for (JsonElement el : rules) {
            JsonObject rule = el.getAsJsonObject();
            String action = rule.get("action").getAsString();
            if (rule.has("os")) {
                JsonObject os = rule.getAsJsonObject("os");
                if (os.has("name")) {
                    if (os.get("name").getAsString().equals(mcOs)) {
                        allowed = action.equals("allow");
                    }
                } else {
                    allowed = action.equals("allow");
                }
            } else {
                allowed = action.equals("allow");
            }
        }
        return allowed;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String findJava() {
        // Try JAVA_HOME first
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null) {
            Path bin = Path.of(javaHome, "bin", "java" + (isWindows() ? ".exe" : ""));
            if (Files.exists(bin)) return bin.toString();
        }
        // Try the JVM we're running in
        String current = ProcessHandle.current().info().command().orElse(null);
        if (current != null) return current;
        // Fallback
        return "java";
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * Convert Maven coordinate "group:artifact:version" or "group:artifact:version:classifier"
     * to a path like "group/path/artifact/version/artifact-version[-classifier].jar"
     */
    private String mavenNameToPath(String name) {
        String[] parts = name.split(":");
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) { Files.deleteIfExists(dir); return; }
        try (var entries = Files.walk(dir)) {
            entries.sorted(Comparator.reverseOrder())
                   .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
        }
    }

    // ── Functional interface for "running" check ────────────────────────

    @FunctionalInterface
    public interface BooleanSupplier {
        boolean getAsBoolean();
    }
}
