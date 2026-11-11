package com.smme.billing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Backups of the built-in (H2) database: one automatically every day the app runs, plus "Backup now" in Settings.
 * Each backup is a zip in the backups folder; the newest {@code backup.keep} are kept.
 * For PostgreSQL / MySQL use the database's own tools (pg_dump) – see README.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");
    /** Only file names this service creates may be downloaded. */
    public static final Pattern NAME = Pattern.compile("shop_billing_\\d{4}-\\d{2}-\\d{2}_\\d{6}\\.zip");

    private final JdbcTemplate jdbc;
    private final boolean h2;
    private final Path dir;
    private final int keep;

    public record BackupFile(String name, long sizeKb, LocalDateTime created) {}

    public BackupService(JdbcTemplate jdbc, DataSource dataSource,
                         @Value("${backup.dir:./backups}") String dir,
                         @Value("${backup.keep:30}") int keep) {
        this.jdbc = jdbc;
        this.h2 = detectH2(dataSource);
        this.dir = Paths.get(dir).toAbsolutePath().normalize();
        this.keep = Math.max(1, keep);
    }

    private static boolean detectH2(DataSource ds) {
        try (Connection c = ds.getConnection()) {
            String url = c.getMetaData().getURL();
            return url.startsWith("jdbc:h2:") && !url.startsWith("jdbc:h2:mem:");   // in-memory databases have nothing to back up
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean isSupported() { return h2; }

    public Path getDir() { return dir; }

    /** Runs a minute after start and then every 6 hours; makes at most one automatic backup per day. */
    @Scheduled(initialDelayString = "${backup.initial-delay-ms:60000}", fixedDelayString = "PT6H")
    public void dailyBackup() {
        if (!h2) return;
        String today = "shop_billing_" + LocalDate.now() + "_";
        if (list().stream().anyMatch(b -> b.name().startsWith(today))) return;
        try {
            backupNow();
        } catch (Exception e) {
            log.error("Automatic backup failed", e);
        }
    }

    public synchronized Path backupNow() throws IOException {
        if (!h2) throw new IllegalStateException("Automatic backup works only with the built-in database");
        Files.createDirectories(dir);
        Path file = dir.resolve("shop_billing_" + LocalDateTime.now().format(STAMP) + ".zip");
        jdbc.execute("BACKUP TO '" + file.toString().replace("\\", "/").replace("'", "''") + "'");
        log.info("Database backup written to {}", file);
        prune();
        return file;
    }

    public List<BackupFile> list() {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> NAME.matcher(p.getFileName().toString()).matches())
                    .map(BackupService::info)
                    .sorted(Comparator.comparing(BackupFile::name).reversed())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** The backup file with this name, or null if the name is not one of ours. */
    public Path resolve(String name) {
        if (name == null || !NAME.matcher(name).matches()) return null;
        Path p = dir.resolve(name);
        return Files.isRegularFile(p) ? p : null;
    }

    private void prune() throws IOException {
        List<BackupFile> all = list();
        for (int i = keep; i < all.size(); i++) Files.deleteIfExists(dir.resolve(all.get(i).name()));
    }

    private static BackupFile info(Path p) {
        try {
            return new BackupFile(p.getFileName().toString(), Math.max(1, Files.size(p) / 1024),
                    LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(), ZoneId.systemDefault()));
        } catch (IOException e) {
            return new BackupFile(p.getFileName().toString(), 0, null);
        }
    }
}
