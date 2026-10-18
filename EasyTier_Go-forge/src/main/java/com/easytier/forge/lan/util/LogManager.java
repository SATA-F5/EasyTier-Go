package com.easytier.forge.lan.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 日志管理器
 * 同时写入文件和推送到 UI 回调
 */
public class LogManager {

    public enum Level {
        INFO, WARN, ERROR
    }

    private final Path logsDir;
    private Path currentLogFile;
    private Consumer<String> uiCallback;
    private final List<String> buffer = new ArrayList<>();
    private static final int MAX_BUFFER = 500;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public LogManager(Path baseDir) {
        this.logsDir = baseDir.resolve("logs");
        try {
            Files.createDirectories(logsDir);
        } catch (IOException e) {
            System.err.println("无法创建日志目录: " + e.getMessage());
        }
    }

    public void startNewLog(String name, String version, boolean isServer, String worldName) {
        String date = DATE_FMT.format(LocalDateTime.now());
        String fileName;
        if (isServer) {
            String world = worldName != null ? worldName : "server";
            fileName = "server-" + world + "-" + name + "_" + version + "-" + date + ".log";
        } else {
            fileName = "client-" + name + "_" + version + "-" + date + ".log";
        }
        this.currentLogFile = logsDir.resolve(fileName);
        try {
            Files.createDirectories(logsDir);
            if (!Files.exists(currentLogFile)) {
                Files.createFile(currentLogFile);
            }
        } catch (IOException e) {
            System.err.println("无法创建日志文件: " + e.getMessage());
        }
    }

    public void setUiCallback(Consumer<String> callback) {
        this.uiCallback = callback;
    }

    public void info(String msg) {
        log(Level.INFO, msg);
    }

    public void warn(String msg) {
        log(Level.WARN, msg);
    }

    public void error(String msg) {
        log(Level.ERROR, msg);
    }

    public void log(Level level, String msg) {
        String time = TIME_FMT.format(LocalDateTime.now());
        String line = "[" + time + "] [" + level + "] " + msg;

        synchronized (buffer) {
            buffer.add(line);
            while (buffer.size() > MAX_BUFFER) {
                buffer.remove(0);
            }
        }

        if (currentLogFile != null) {
            try {
                Files.writeString(currentLogFile, line + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                // 静默失败
            }
        }

        if (uiCallback != null) {
            try {
                uiCallback.accept(line);
            } catch (Exception ignored) {
            }
        }
    }

    public void raw(String msg) {
        synchronized (buffer) {
            buffer.add(msg);
            while (buffer.size() > MAX_BUFFER) {
                buffer.remove(0);
            }
        }
        if (currentLogFile != null) {
            try {
                Files.writeString(currentLogFile, msg + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
            }
        }
        if (uiCallback != null) {
            try {
                uiCallback.accept(msg);
            } catch (Exception ignored) {
            }
        }
    }

    public List<String> getBuffer() {
        synchronized (buffer) {
            return new ArrayList<>(buffer);
        }
    }

    public void clearBuffer() {
        synchronized (buffer) {
            buffer.clear();
        }
    }

    public Path getCurrentLogFile() {
        return currentLogFile;
    }
}
