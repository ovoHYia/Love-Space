package com.lovespace.service;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.api.error.ApiException;
import com.lovespace.domain.*;
import com.lovespace.repository.*;
import com.lovespace.security.CurrentUserService;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonthlyReportService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final CurrentUserService current;
    private final MoodRepository moods;
    private final MemoryRepository memories;
    private final DiaryRepository diaries;
    private final LetterMessageRepository messages;
    private final WishRepository wishes;

    public MonthlyReportService(
            CurrentUserService current,
            MoodRepository moods,
            MemoryRepository memories,
            DiaryRepository diaries,
            LetterMessageRepository messages,
            WishRepository wishes) {
        this.current = current;
        this.moods = moods;
        this.memories = memories;
        this.diaries = diaries;
        this.messages = messages;
        this.wishes = wishes;
    }

    @Transactional(readOnly = true)
    public MonthlyReportResponse monthly(Authentication auth, String requestedMonth) {
        User viewer = current.user(auth);
        User partner = current.partner(viewer);
        LocalDate today = LocalDate.now(ZONE);
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth month = parseMonth(requestedMonth, currentMonth);
        if (month.isAfter(currentMonth)) {
            throw ApiException.badRequest("暂不能生成未来月份的报告");
        }

        LocalDate from = month.atDay(1);
        LocalDate to = month.equals(currentMonth) ? today : month.atEndOfMonth();
        LocalDateTime startAt = from.atStartOfDay();
        LocalDateTime endAt = to.plusDays(1).atStartOfDay();
        Long coupleId = viewer.getCouple().getId();
        List<User> pair = List.of(viewer, partner).stream()
                .sorted(Comparator.comparing(User::getId)).toList();
        Map<Long, User> users = pair.stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<Mood> moodEntries = moods.findByCoupleIdAndMoodDateBetweenOrderByMoodDateAscUserIdAsc(
                coupleId, from, to);
        List<MoodTrendPoint> trend = moodEntries.stream()
                .map(item -> new MoodTrendPoint(
                        item.getMoodDate(), item.getUserId(),
                        users.containsKey(item.getUserId()) ? users.get(item.getUserId()).getNickname() : "已注销用户",
                        item.getEmoji(), item.getLabel(), item.getNote(), score(item.getLabel())))
                .toList();

        Map<LocalDate, List<Mood>> byDate = moodEntries.stream()
                .collect(Collectors.groupingBy(Mood::getMoodDate, TreeMap::new, Collectors.toList()));
        int recordedDays = byDate.size();
        int sharedMoodDays = (int) byDate.values().stream()
                .filter(items -> items.stream().map(Mood::getUserId).distinct().count() >= 2)
                .count();
        int resonanceDays = (int) byDate.values().stream().filter(this::resonates).count();
        int resonanceRate = sharedMoodDays == 0 ? 0 : roundedPercent(resonanceDays, sharedMoodDays);
        int daysInScope = to.getDayOfMonth();
        int coverageRate = roundedPercent(moodEntries.size(), daysInScope * pair.size());

        List<MoodPersonSummary> people = pair.stream()
                .map(user -> personSummary(user, moodEntries))
                .toList();
        List<MoodDistributionView> distribution = distribution(moodEntries);

        List<Memory> monthlyMemories = memories
                .findByCoupleIdAndDeletedAtIsNullAndEventAtGreaterThanEqualAndEventAtLessThanOrderByEventAt(
                        coupleId, startAt, endAt);
        List<Diary> monthlyDiaries = diaries
                .findByCoupleIdAndDeletedAtIsNullAndDiaryDateBetweenOrderByDiaryDate(coupleId, from, to);
        List<LetterMessage> monthlyMessages = messages.findVisibleByCoupleAndUserAndDeliverAtRange(
                coupleId, viewer.getId(), LocalDateTime.now(ZONE), startAt, endAt);
        List<Wish> completedWishes = wishes
                .findByCoupleIdAndDeletedAtIsNullAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAt(
                        coupleId, startAt, endAt);

        MonthlyActivitySummary activities = new MonthlyActivitySummary(
                monthlyMemories.size(), monthlyDiaries.size(), monthlyMessages.size(), completedWishes.size());
        List<MonthlyHighlight> highlights = highlights(
                monthlyMemories, monthlyDiaries, monthlyMessages, completedWishes);

        return new MonthlyReportResponse(
                month.toString(), from, to, daysInScope,
                moodEntries.size(), recordedDays, sharedMoodDays, longestStreak(byDate.keySet()),
                resonanceRate, coverageRate, insight(moodEntries.size(), sharedMoodDays, resonanceRate),
                trend, distribution, people, activities, highlights);
    }

    private YearMonth parseMonth(String value, YearMonth fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest("月份格式应为 YYYY-MM");
        }
    }

    private MoodPersonSummary personSummary(User user, List<Mood> allEntries) {
        List<Mood> entries = allEntries.stream()
                .filter(item -> user.getId().equals(item.getUserId())).toList();
        Optional<Map.Entry<String, List<Mood>>> dominant = entries.stream()
                .collect(Collectors.groupingBy(Mood::getLabel))
                .entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, List<Mood>>>comparingInt(entry -> entry.getValue().size())
                        .reversed().thenComparing(Map.Entry::getKey))
                .findFirst();
        double average = entries.isEmpty() ? 0 : roundOneDecimal(
                entries.stream().mapToInt(item -> score(item.getLabel())).average().orElse(0));
        return new MoodPersonSummary(
                user.getId(), user.getNickname(), entries.size(), average,
                dominant.map(Map.Entry::getKey).orElse(null),
                dominant.map(entry -> entry.getValue().get(0).getEmoji()).orElse(null));
    }

    private List<MoodDistributionView> distribution(List<Mood> entries) {
        if (entries.isEmpty()) return List.of();
        return entries.stream().collect(Collectors.groupingBy(Mood::getLabel))
                .entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, List<Mood>>>comparingInt(entry -> entry.getValue().size())
                        .reversed().thenComparing(Map.Entry::getKey))
                .map(entry -> new MoodDistributionView(
                        entry.getKey(), entry.getValue().get(0).getEmoji(), entry.getValue().size(),
                        roundedPercent(entry.getValue().size(), entries.size())))
                .toList();
    }

    private boolean resonates(List<Mood> entries) {
        Map<Long, Mood> byUser = entries.stream().collect(Collectors.toMap(
                Mood::getUserId, Function.identity(), (first, ignored) -> first));
        if (byUser.size() < 2) return false;
        Iterator<Mood> iterator = byUser.values().iterator();
        return Math.abs(score(iterator.next().getLabel()) - score(iterator.next().getLabel())) <= 1;
    }

    private int longestStreak(Set<LocalDate> recordedDates) {
        int longest = 0;
        int currentLength = 0;
        LocalDate previous = null;
        for (LocalDate date : new TreeSet<>(recordedDates)) {
            currentLength = previous != null && previous.plusDays(1).equals(date) ? currentLength + 1 : 1;
            longest = Math.max(longest, currentLength);
            previous = date;
        }
        return longest;
    }

    private List<MonthlyHighlight> highlights(
            List<Memory> monthlyMemories,
            List<Diary> monthlyDiaries,
            List<LetterMessage> monthlyMessages,
            List<Wish> completedWishes) {
        List<MonthlyHighlight> items = new ArrayList<>();
        monthlyMemories.forEach(item -> items.add(new MonthlyHighlight(
                "MEMORY", item.getId(), item.getTitle(), item.getEventAt().toLocalDate())));
        monthlyDiaries.forEach(item -> items.add(new MonthlyHighlight(
                "DIARY", item.getId(), item.getTitle(), item.getDiaryDate())));
        monthlyMessages.forEach(item -> items.add(new MonthlyHighlight(
                "LETTER", item.getId(), "一封写给彼此的信", item.getDeliverAt().toLocalDate())));
        completedWishes.forEach(item -> items.add(new MonthlyHighlight(
                "WISH", item.getId(), "完成愿望 · " + item.getTitle(), item.getCompletedAt().toLocalDate())));
        return items.stream()
                .sorted(Comparator.comparing(MonthlyHighlight::date).reversed()
                        .thenComparing(MonthlyHighlight::type).thenComparing(MonthlyHighlight::id))
                .limit(8)
                .toList();
    }

    private String insight(int moodEntries, int sharedMoodDays, int resonanceRate) {
        if (moodEntries == 0) return "这个月还没有心情记录，从今天留下一枚心情符号吧。";
        if (sharedMoodDays == 0) return "这个月已经有人留下心情，下一次不妨一起记录彼此的当下。";
        if (resonanceRate >= 75) return "这个月你们常常在同一频率，也认真接住了彼此的情绪。";
        if (resonanceRate >= 40) return "这个月有默契也有不同，每一种心情都值得被好好看见。";
        return "这个月你们的心情各有起伏，回头看看，也许会更懂彼此一点。";
    }

    private int score(String label) {
        if (label == null) return 3;
        return switch (label.trim()) {
            case "开心", "甜甜的", "感动" -> 5;
            case "温柔", "平静" -> 4;
            case "想念" -> 3;
            case "有点累" -> 2;
            case "需要抱抱" -> 1;
            default -> 3;
        };
    }

    private int roundedPercent(long value, long total) {
        return total == 0 ? 0 : (int) Math.round(value * 100.0 / total);
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
