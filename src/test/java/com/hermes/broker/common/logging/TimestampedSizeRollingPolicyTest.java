package com.hermes.broker.common.logging;

import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.util.FileSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class TimestampedSizeRollingPolicyTest {

    @TempDir
    Path logDirectory;

    @Test
    void namesEachNewFileWithItsCreationMinuteAndAUniqueSequence() throws Exception {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-25T05:32:45Z"),
                ZoneId.of("Asia/Seoul")
        );
        TimestampedSizeRollingPolicy<Object> policy = new TimestampedSizeRollingPolicy<>(clock);
        policy.setParent(new FileAppender<>());
        policy.setLogPath(logDirectory.toString());

        policy.start();

        Path firstFile = Path.of(policy.getActiveFileName());
        assertThat(firstFile.getFileName().toString()).isEqualTo("2026-07-25_14-32.0.log");

        Files.createFile(firstFile);
        policy.rollover();

        assertThat(Path.of(policy.getActiveFileName()).getFileName().toString())
                .isEqualTo("2026-07-25_14-32.1.log");
    }

    @Test
    void triggersOnlyWhenTheConfiguredFileSizeIsReached() throws Exception {
        TimestampedSizeRollingPolicy<Object> policy = new TimestampedSizeRollingPolicy<>();
        policy.setParent(new FileAppender<>());
        policy.setLogPath(logDirectory.toString());
        policy.setMaxFileSize(new FileSize(10));
        policy.start();

        Path activeFile = Path.of(policy.getActiveFileName());
        Files.write(activeFile, new byte[9]);
        assertThat(policy.isTriggeringEvent(activeFile.toFile(), new Object())).isFalse();

        Files.write(activeFile, new byte[10]);
        assertThat(policy.isTriggeringEvent(activeFile.toFile(), new Object())).isTrue();
    }
}
