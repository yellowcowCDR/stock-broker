package com.hermes.broker.common.logging;

import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.RollingPolicy;
import ch.qos.logback.core.rolling.TriggeringPolicyBase;
import ch.qos.logback.core.rolling.helper.CompressionMode;
import ch.qos.logback.core.util.FileSize;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Rolls solely on file size and names every new file with its creation minute.
 */
public class TimestampedSizeRollingPolicy<E> extends TriggeringPolicyBase<E> implements RollingPolicy {

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
    private static final FileSize DEFAULT_MAX_FILE_SIZE = new FileSize(10 * 1024 * 1024);

    private final Clock clock;
    private String logPath = "logs";
    private FileSize maxFileSize = DEFAULT_MAX_FILE_SIZE;
    private String activeFileName;
    private FileAppender<?> parent;

    public TimestampedSizeRollingPolicy() {
        this(Clock.systemDefaultZone());
    }

    TimestampedSizeRollingPolicy(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void start() {
        if (parent == null) {
            addError("The parent appender must be set before starting TimestampedSizeRollingPolicy.");
            return;
        }
        if (logPath == null || logPath.isBlank()) {
            addError("The logPath property must not be blank.");
            return;
        }
        if (maxFileSize == null || maxFileSize.getSize() <= 0) {
            addError("The maxFileSize property must be greater than zero.");
            return;
        }

        try {
            Files.createDirectories(Path.of(logPath));
            activeFileName = nextAvailableFileName();
            super.start();
        } catch (IOException | RuntimeException exception) {
            addError("Unable to prepare log directory [" + logPath + "].", exception);
        }
    }

    @Override
    public boolean isTriggeringEvent(File activeFile, E event) {
        return activeFile != null && activeFile.length() >= maxFileSize.getSize();
    }

    @Override
    public void rollover() {
        activeFileName = nextAvailableFileName();
    }

    @Override
    public String getActiveFileName() {
        return activeFileName;
    }

    @Override
    public CompressionMode getCompressionMode() {
        return CompressionMode.NONE;
    }

    @Override
    public void setParent(FileAppender<?> parent) {
        this.parent = parent;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }

    public void setMaxFileSize(FileSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    private String nextAvailableFileName() {
        String timestamp = FILE_TIMESTAMP.format(LocalDateTime.now(clock));
        Path directory = Path.of(logPath);
        int sequence = 0;

        Path candidate;
        do {
            candidate = directory.resolve(timestamp + "." + sequence + ".log");
            sequence++;
        } while (Files.exists(candidate));

        return candidate.toString();
    }
}
