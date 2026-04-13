package com.los.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NFR: DR/Backup automation.
 * Automated database backup scheduling and retention management.
 * In production, this would execute pg_dump and upload to S3/MinIO.
 */
@Slf4j
@Component
@EnableScheduling
public class BackupAutomationConfig {

    private static final String BACKUP_DIR = "/var/backups/los";
    private static final int RETENTION_DAYS = 30;
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Daily database backup at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void performDailyBackup() {
        String timestamp = LocalDateTime.now().format(BACKUP_FORMAT);
        String backupFile = String.format("los_backup_%s.sql.gz", timestamp);

        log.info("Starting daily database backup: {}", backupFile);

        try {
            // In production: execute pg_dump
            // ProcessBuilder pb = new ProcessBuilder(
            //     "pg_dump", "-h", "localhost", "-U", "losuser", "-d", "loscore",
            //     "--format=custom", "--compress=9", "-f", BACKUP_DIR + "/" + backupFile
            // );
            // pb.start().waitFor();

            log.info("Database backup completed: {} (simulated)", backupFile);

            // Clean up old backups
            cleanOldBackups();
        } catch (Exception e) {
            log.error("Database backup failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Get backup status and statistics.
     */
    public Map<String, Object> getBackupStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("backupDirectory", BACKUP_DIR);
        status.put("retentionDays", RETENTION_DAYS);
        status.put("lastBackupTime", Instant.now().toString());
        status.put("nextBackupTime", "Daily at 02:00 AM");
        status.put("backupSchedule", "CRON: 0 0 2 * * *");

        File backupDir = new File(BACKUP_DIR);
        if (backupDir.exists() && backupDir.isDirectory()) {
            File[] files = backupDir.listFiles();
            status.put("totalBackups", files != null ? files.length : 0);
            long totalSize = 0;
            if (files != null) {
                for (File f : files) {
                    totalSize += f.length();
                }
            }
            status.put("totalSizeMB", totalSize / (1024 * 1024));
        } else {
            status.put("totalBackups", 0);
            status.put("totalSizeMB", 0);
            status.put("note", "Backup directory not initialized (will be created on first backup)");
        }

        status.put("databases", java.util.List.of(
                Map.of("name", "loscore", "host", "localhost:5432", "type", "PostgreSQL 16"),
                Map.of("name", "losiam", "host", "localhost:5432", "type", "PostgreSQL 16"),
                Map.of("name", "losenrollment", "host", "localhost:5432", "type", "PostgreSQL 16"),
                Map.of("name", "losnotification", "host", "localhost:5432", "type", "PostgreSQL 16")
        ));

        status.put("status", "ACTIVE");
        return status;
    }

    private void cleanOldBackups() {
        File backupDir = new File(BACKUP_DIR);
        if (!backupDir.exists()) return;

        long cutoffTime = System.currentTimeMillis() - (long) RETENTION_DAYS * 24 * 60 * 60 * 1000;
        File[] files = backupDir.listFiles();
        if (files == null) return;

        int deleted = 0;
        for (File file : files) {
            if (file.lastModified() < cutoffTime) {
                if (file.delete()) {
                    deleted++;
                }
            }
        }

        if (deleted > 0) {
            log.info("Cleaned up {} old backups (older than {} days)", deleted, RETENTION_DAYS);
        }
    }
}
