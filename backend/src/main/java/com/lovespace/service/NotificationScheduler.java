package com.lovespace.service;

import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {
    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final NotificationService notifications;
    public NotificationScheduler(NotificationService notifications) { this.notifications = notifications; }

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Shanghai")
    public void generateDailyReminders() {
        int created = notifications.generateAnniversaryReminders(LocalDate.now(ZONE));
        if (created > 0) log.info("Generated {} anniversary reminder notification(s)", created);
    }
}
