package com.lightdeploy.backend.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 部署日志文件工具：每个部署记录独立一个文件，便于手工清理。
 * <p>
 * 路径规则：{@code {app.log-dir}/deploy/{recordId}.log}，
 * 本地默认 {@code ./logs/deploy}，Docker 为 {@code /data/logs/deploy}（已有 logs_data volume）。
 */
public final class DeployLogFiles {

    private DeployLogFiles() {
    }

    public static File resolve(String logDir, long recordId) {
        String base = PathUtils.resolve(logDir);
        return new File(base + File.separator + "deploy", recordId + ".log");
    }

    public static BufferedWriter openWriter(File logFile) throws IOException {
        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        Path path = logFile.toPath();
        return Files.newBufferedWriter(path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    public static void appendLine(String logDir, long recordId, String message) {
        try {
            File logFile = resolve(logDir, recordId);
            File parent = logFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            String line = message == null ? "" : message;
            if (!line.endsWith("\n")) {
                line = line + "\n";
            }
            Files.write(logFile.toPath(), line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // 文件追加失败不影响主流程（DB 尾部已有记录）
        }
    }

    /**
     * 读取全量日志，超过 maxLines 时只返回最后 maxLines 行并附截断提示。
     *
     * @return 文件不存在返回 null；存在但为空返回空字符串
     */
    public static String readFull(String logDir, long recordId, int maxLines) {
        File logFile = resolve(logDir, recordId);
        if (!logFile.exists() || !logFile.isFile()) {
            return null;
        }
        try {
            List<String> all = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8);
            if (all.size() <= maxLines) {
                return String.join("\n", all);
            }
            Deque<String> tail = new ArrayDeque<>(maxLines);
            for (String line : all) {
                tail.addLast(line);
                if (tail.size() > maxLines) {
                    tail.removeFirst();
                }
            }
            return "[... 日志过长，仅显示最后 " + maxLines + " 行，共 " + all.size() + " 行 ...]\n"
                    + String.join("\n", tail);
        } catch (IOException e) {
            return null;
        }
    }

    public static String tailText(Deque<String> tail) {
        if (tail == null || tail.isEmpty()) {
            return "";
        }
        return String.join("\n", tail);
    }
}
