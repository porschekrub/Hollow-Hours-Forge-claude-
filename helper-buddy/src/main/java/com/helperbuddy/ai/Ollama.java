package com.helperbuddy.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.helperbuddy.HelperBuddy;
import com.helperbuddy.config.HBConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * A local AI on the computer that runs the world: finds Ollama, or downloads the official release from
 * github.com/ollama (no installer, no admin rights) into a folder of its own, starts and stops it, downloads and deletes
 * models, and picks one for the helpers. Everything runs on a worker thread; the menu shows what it is doing.
 */
public final class Ollama {
    private Ollama() {}

    public static final String BASE = "http://127.0.0.1:11434";
    static final String RELEASE = "https://github.com/ollama/ollama/releases/latest/download/";

    /** {model, what it is like} */
    public static final String[][] RECOMMENDED = {
            {"qwen3.5:4b", "A good start: quick, follows orders well, understands Thai. Graphics card with 6 GB or more"},
            {"qwen3:4b-instruct", "Answers fastest (no thinking step). 4 GB or more"},
            {"llama3.2:3b", "Very light: laptops, older graphics cards, or none (slow then). English mostly"},
            {"granite4:micro", "Tiny, made for following orders. English mostly"},
            {"qwen3:8b", "Smarter, a bit slower. 8 GB or more"},
            {"qwen3.5:9b", "The smartest here. 10 GB or more next to Minecraft"},
    };

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.ALWAYS).build();

    // ------------------------------------------------------------------ what the menu shows
    static volatile boolean running;
    static volatile String version = "";
    static volatile List<String[]> models = new ArrayList<>();
    static volatile String job = "", error = "", done = "";
    static volatile float progress = -1;
    static volatile boolean busy, cancel;
    static volatile long checkedAt;
    @Nullable
    static Process process;

    public static void fill(CompoundTag t) {
        CompoundTag o = new CompoundTag();
        o.putBoolean("running", running);
        o.putString("version", version);
        Path exe = executable();
        o.putString("exe", exe == null ? "" : exe.toString());
        o.putBoolean("ours", exe != null && exe.startsWith(home()));
        o.putBoolean("canInstall", asset() != null);
        o.putString("os", os());
        o.putString("job", job);
        o.putFloat("progress", progress);
        o.putBoolean("busy", busy);
        o.putString("error", error);
        o.putString("done", done);
        o.putString("using", AiUse.isOllama() ? HBConfig.AI_MODEL.get().trim() : "");
        ListTag l = new ListTag();
        for (String[] m : models) {
            CompoundTag c = new CompoundTag();
            c.putString("name", m[0]);
            c.putString("size", m[1]);
            l.add(c);
        }
        o.put("models", l);
        t.put("ollama", o);
    }

    /** Looks again (at most once a second): is it running, which models it has. */
    public static void check() {
        long now = System.currentTimeMillis();
        if (now - checkedAt < 1000) return;
        checkedAt = now;
        try {
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(BASE + "/api/version")).timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
            running = r.statusCode() == 200;
            if (running) version = JsonParser.parseString(r.body()).getAsJsonObject().get("version").getAsString();
        } catch (Exception e) {
            running = false;
            version = "";
        }
        if (!running) {
            models = new ArrayList<>();
            return;
        }
        try {
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(BASE + "/api/tags")).timeout(Duration.ofSeconds(4)).GET().build(), HttpResponse.BodyHandlers.ofString());
            List<String[]> out = new ArrayList<>();
            for (JsonElement e : JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonArray("models")) {
                JsonObject m = e.getAsJsonObject();
                long size = m.has("size") ? m.get("size").getAsLong() : 0;
                out.add(new String[] {m.get("name").getAsString(), String.format(Locale.ROOT, "%.1f GB", size / 1e9)});
            }
            models = out;
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ where things are
    static String os() {
        String n = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return n.contains("win") ? "windows" : n.contains("mac") || n.contains("darwin") ? "mac" : "linux";
    }

    static boolean arm() {
        String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return a.contains("aarch64") || a.contains("arm64");
    }

    /** The release file for this computer (null: install it yourself; Linux releases need zstd, which Java can't unpack). */
    @Nullable
    static String asset() {
        return switch (os()) {
            case "windows" -> arm() ? "ollama-windows-arm64.zip" : "ollama-windows-amd64.zip";
            case "mac" -> "ollama-darwin.tgz";
            default -> null;
        };
    }

    /** The folder for the copy of Ollama the mod installs (shared by every Minecraft instance of this user). */
    static Path home() {
        String h = System.getProperty("user.home", ".");
        return switch (os()) {
            case "windows" -> Path.of(System.getenv().getOrDefault("LOCALAPPDATA", h + "\\AppData\\Local"), "HelperBuddy", "Ollama");
            case "mac" -> Path.of(h, "Library", "Application Support", "HelperBuddy", "Ollama");
            default -> Path.of(System.getenv().getOrDefault("XDG_DATA_HOME", h + "/.local/share"), "helperbuddy", "ollama");
        };
    }

    static String exeName() {
        return os().equals("windows") ? "ollama.exe" : "ollama";
    }

    /** Ours first, then one the player installed. */
    @Nullable
    static Path executable() {
        Path ours = find(home().resolve("current"));
        if (ours != null) return ours;
        String h = System.getProperty("user.home", ".");
        List<Path> places = new ArrayList<>();
        for (String d : System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator)) if (!d.isBlank()) places.add(Path.of(d.trim(), exeName()));
        switch (os()) {
            case "windows" -> places.add(Path.of(System.getenv().getOrDefault("LOCALAPPDATA", h + "\\AppData\\Local"), "Programs", "Ollama", "ollama.exe"));
            case "mac" -> {
                places.add(Path.of("/Applications/Ollama.app/Contents/Resources/ollama"));
                places.add(Path.of("/opt/homebrew/bin/ollama"));
                places.add(Path.of("/usr/local/bin/ollama"));
            }
            default -> {
                places.add(Path.of("/usr/local/bin/ollama"));
                places.add(Path.of("/usr/bin/ollama"));
            }
        }
        for (Path p : places) if (Files.isRegularFile(p)) return p;
        return null;
    }

    @Nullable
    static Path find(Path dir) {
        if (!Files.isDirectory(dir)) return null;
        try (Stream<Path> s = Files.walk(dir, 4)) {
            return s.filter(p -> p.getFileName().toString().equals(exeName()) && Files.isRegularFile(p)).findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ jobs
    interface Job {
        void run() throws Exception;
    }

    static boolean start(String what, Job j) {
        if (busy) return false;
        busy = true;
        cancel = false;
        error = "";
        done = "";
        job = what;
        progress = -1;
        Thread t = new Thread(() -> {
            try {
                j.run();
            } catch (Exception e) {
                error = cancel ? "Cancelled" : String.valueOf(e.getMessage() == null ? e : e.getMessage());
                HelperBuddy.LOG.warn("[Helper Buddy] Ollama: {} failed: {}", what, e.toString());
            } finally {
                busy = false;
                job = "";
                progress = -1;
                checkedAt = 0;
                check();
            }
        }, "HelperBuddy-Ollama");
        t.setDaemon(true);
        t.start();
        return true;
    }

    public static void cancel() {
        cancel = true;
    }

    /** Downloads the official release, checks it, unpacks it into its own folder and starts it. */
    public static boolean install() {
        return start("Installing Ollama", () -> {
            String a = asset();
            if (a == null) throw new IOException("On Linux please install Ollama yourself (ollama.com/download), then press Refresh");
            Path dir = home(), dl = dir.resolve("download"), file = dl.resolve(a);
            Files.createDirectories(dl);
            job = "Checking the download size";
            HttpResponse<Void> head = HTTP.send(HttpRequest.newBuilder(URI.create(RELEASE + a)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
            long size = head.headers().firstValueAsLong("content-length").orElse(-1);
            long free = Files.getFileStore(dir.getParent() == null ? dir : Files.createDirectories(dir)).getUsableSpace();
            if (size > 0 && free < size * 3) throw new IOException(String.format(Locale.ROOT, "Not enough disk space: Ollama needs about %.1f GB free, there is %.1f GB", size * 3 / 1e9, free / 1e9));
            job = "Downloading Ollama" + (size > 0 ? String.format(Locale.ROOT, " (%.1f GB)", size / 1e9) : "");
            HttpResponse<InputStream> r = HTTP.send(HttpRequest.newBuilder(URI.create(RELEASE + a)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            if (r.statusCode() / 100 != 2) throw new IOException("Download failed (HTTP " + r.statusCode() + ")");
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (InputStream in = r.body(); OutputStream out = Files.newOutputStream(file)) {
                byte[] buf = new byte[1 << 16];
                long got = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (cancel) throw new IOException("Cancelled");
                    out.write(buf, 0, n);
                    sha.update(buf, 0, n);
                    got += n;
                    if (size > 0) progress = got / (float) size;
                }
            }
            job = "Checking the download";
            progress = -1;
            String want = null;
            try {
                String sums = HTTP.send(HttpRequest.newBuilder(URI.create(RELEASE + "sha256sum.txt")).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
                for (String line : sums.split("\\R")) {
                    String[] p = line.trim().split("\\s+");
                    if (p.length == 2 && p[1].replaceFirst("^\\./", "").replaceFirst("^\\*", "").equals(a)) want = p[0].toLowerCase(Locale.ROOT);
                }
            } catch (Exception e) {
                HelperBuddy.LOG.warn("[Helper Buddy] Ollama checksums could not be read: {}", e.toString());
            }
            String got = HexFormat.of().formatHex(sha.digest());
            if (want != null && !want.equals(got)) throw new IOException("The download was damaged (checksum mismatch). Please try again");
            job = "Unpacking Ollama";
            Path staging = dir.resolve("staging");
            deleteTree(staging);
            Files.createDirectories(staging);
            if (a.endsWith(".zip")) unzip(file, staging);
            else untgz(file, staging);
            Path cur = dir.resolve("current");
            deleteTree(cur);
            Files.move(staging, cur);
            Files.deleteIfExists(file);
            Path exe = find(cur);
            if (exe == null) throw new IOException("Ollama was unpacked, but its program is missing");
            exe.toFile().setExecutable(true);
            try (Stream<Path> s = Files.walk(cur)) {
                s.filter(Files::isRegularFile).forEach(p -> {
                    String n = p.getFileName().toString();
                    if (!n.contains(".") || n.endsWith(".dylib") || n.endsWith(".so")) p.toFile().setExecutable(true);
                });
            }
            Files.writeString(dir.resolve("installed-by.txt"), "Helper Buddy (Minecraft mod). Delete this folder to remove this copy of Ollama.\n");
            job = "Starting Ollama";
            launch(exe);
            done = "Ollama is installed and running";
        });
    }

    static void unzip(Path zip, Path to) throws IOException {
        try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(new java.io.BufferedInputStream(Files.newInputStream(zip)))) {
            java.util.zip.ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (cancel) throw new IOException("Cancelled");
                Path p = to.resolve(e.getName()).normalize();
                if (!p.startsWith(to)) continue;
                if (e.isDirectory()) Files.createDirectories(p);
                else {
                    Files.createDirectories(p.getParent());
                    Files.copy(z, p, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    static void untgz(Path tgz, Path to) throws IOException {
        try (InputStream raw = new java.io.BufferedInputStream(Files.newInputStream(tgz));
             java.util.zip.GZIPInputStream gz = new java.util.zip.GZIPInputStream(raw);
             org.apache.commons.compress.archivers.tar.TarArchiveInputStream tar = new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(gz)) {
            org.apache.commons.compress.archivers.tar.TarArchiveEntry e;
            while ((e = tar.getNextTarEntry()) != null) {
                if (cancel) throw new IOException("Cancelled");
                Path p = to.resolve(e.getName()).normalize();
                if (!p.startsWith(to) || e.isSymbolicLink() || e.isLink()) continue;
                if (e.isDirectory()) Files.createDirectories(p);
                else {
                    Files.createDirectories(p.getParent());
                    Files.copy(tar, p, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    if ((e.getMode() & 0100) != 0) p.toFile().setExecutable(true);
                }
            }
        }
    }

    static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            for (Path x : s.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(x);
        }
    }

    /** Starts "ollama serve" (with room for the helper's long instructions) and waits until it answers. */
    static void launch(Path exe) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(exe.toString(), "serve");
        pb.environment().put("OLLAMA_HOST", "127.0.0.1:11434");
        pb.environment().putIfAbsent("OLLAMA_CONTEXT_LENGTH", "8192");
        Files.createDirectories(home());
        pb.redirectErrorStream(true).redirectOutput(home().resolve("server.log").toFile());
        process = pb.start();
        for (int i = 0; i < 60; i++) {
            Thread.sleep(500);
            checkedAt = 0;
            check();
            if (running) return;
            if (!process.isAlive()) throw new IOException("Ollama stopped right after starting (see " + home().resolve("server.log") + ")");
        }
        throw new IOException("Ollama did not answer after 30 seconds");
    }

    public static boolean startServer() {
        return start("Starting Ollama", () -> {
            Path exe = executable();
            if (exe == null) throw new IOException("Ollama is not installed");
            checkedAt = 0;
            check();
            if (!running) launch(exe);
            done = "Ollama is running";
        });
    }

    /** Stops the Ollama this mod started (one started some other way is left alone). */
    public static void stopServer() {
        Process p = process;
        process = null;
        if (p == null || !p.isAlive()) return;
        p.destroy();
        try {
            if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly();
        } catch (InterruptedException ignored) {
        }
        running = false;
    }

    /** When the world stops. */
    public static void shutdown() {
        stopServer();
    }

    /** The helpers use Ollama: make sure it runs (started quietly when the world starts). */
    public static void ensureRunning() {
        if (!AiUse.isOllama() || busy) return;
        checkedAt = 0;
        check();
        if (!running && executable() != null) startServer();
    }

    /** Removes the copy this mod installed (the models stay, Ollama keeps them in its own folder). */
    public static boolean remove() {
        return start("Removing Ollama", () -> {
            Path exe = executable();
            if (exe == null || !exe.startsWith(home())) throw new IOException("This Ollama was not installed by Helper Buddy: remove it with its own uninstaller");
            stopServer();
            Thread.sleep(1000);
            deleteTree(home().resolve("current"));
            done = "Ollama removed (your downloaded models were kept)";
        });
    }

    /** Downloads a model, with progress. */
    public static boolean pull(String name) {
        String model = name.trim();
        if (model.isEmpty() || !model.matches("[A-Za-z0-9._:/-]{1,80}")) return false;
        return start("Downloading " + model, () -> {
            if (!running) {
                Path exe = executable();
                if (exe == null) throw new IOException("Install Ollama first");
                launch(exe);
            }
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("stream", true);
            HttpResponse<InputStream> r = HTTP.send(HttpRequest.newBuilder(URI.create(BASE + "/api/pull")).header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofInputStream());
            boolean ok = false;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(r.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    if (cancel) throw new IOException("Cancelled");
                    if (line.isBlank()) continue;
                    JsonObject o = JsonParser.parseString(line).getAsJsonObject();
                    if (o.has("error")) throw new IOException(o.get("error").getAsString());
                    String st = o.has("status") ? o.get("status").getAsString() : "";
                    if (st.equals("success")) ok = true;
                    if (o.has("total") && o.has("completed")) {
                        long tot = o.get("total").getAsLong(), got = o.get("completed").getAsLong();
                        progress = tot > 0 ? got / (float) tot : -1;
                        job = String.format(Locale.ROOT, "Downloading %s (%.1f / %.1f GB)", model, got / 1e9, tot / 1e9);
                    } else if (!st.isEmpty()) job = "Downloading " + model + ": " + st;
                }
            }
            if (!ok) throw new IOException("The download of " + model + " did not finish");
            checkedAt = 0;
            check();
            AiUse.ollama(model);
            done = model + " is ready, and the helpers use it now";
        });
    }

    public static boolean delete(String name) {
        return start("Deleting " + name, () -> {
            JsonObject body = new JsonObject();
            body.addProperty("model", name);
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(BASE + "/api/delete")).header("content-type", "application/json")
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) throw new IOException("Ollama said: " + r.body());
            done = name + " deleted";
        });
    }

    /** (the smoke test) */
    public static String describe() {
        JsonArray a = new JsonArray();
        for (String[] r : RECOMMENDED) a.add(r[0]);
        return "ollama " + os() + " asset " + asset() + " home " + home() + " recommended " + a;
    }
}
