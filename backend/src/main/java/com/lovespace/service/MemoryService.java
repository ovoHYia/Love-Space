package com.lovespace.service;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.api.error.ApiException;
import com.lovespace.domain.*;
import com.lovespace.repository.*;
import com.lovespace.security.CurrentUserService;
import com.lovespace.time.BeijingTime;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MemoryService {
    private final MemoryRepository memories;
    private final MediaRepository media;
    private final CurrentUserService current;
    private final MediaStorageService storage;
    private final ViewMapper views;
    private final OptimisticUpdateGuard versions;
    public MemoryService(MemoryRepository memories, MediaRepository media, CurrentUserService current,
                         MediaStorageService storage, ViewMapper views,
                         OptimisticUpdateGuard versions) {
        this.memories = memories; this.media = media; this.current = current;
        this.storage = storage; this.views = views; this.versions = versions;
    }

    @Transactional(readOnly = true)
    public PageResponse<MemoryView> list(Authentication auth, int page, int size, String search,
                                         LocalDate date, String tag) {
        User user = current.user(auth);
        if (page < 0 || size < 1 || size > 100) throw ApiException.badRequest("分页参数无效");
        String selectedTag = normalizeTagForQuery(tag);
        Specification<Memory> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("coupleId"), user.getCouple().getId()));
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\'),
                        cb.like(cb.lower(root.get("location")), pattern, '\\')));
            }
            if (date != null) {
                LocalDateTime start = date.atStartOfDay();
                predicates.add(cb.greaterThanOrEqualTo(root.get("eventAt"), start));
                predicates.add(cb.lessThan(root.get("eventAt"), start.plusDays(1)));
            }
            if (selectedTag != null) {
                predicates.add(cb.equal(cb.lower(root.join("tags")), selectedTag));
                query.distinct(true);
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        if (!offsetFitsJpa(page, size)) {
            return emptyPage(page, size, memories.count(spec));
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "eventAt", "id"));
        Page<Memory> result = memories.findAll(spec, pageable);
        List<MemoryView> content = views.memories(result.getContent());
        return pageResponse(page, size, result.getTotalElements(), content);
    }

    @Transactional(readOnly = true)
    public MemoryView get(Authentication auth, Long id) {
        User user = current.user(auth);
        Memory memory = memories.findByIdAndCoupleIdAndDeletedAtIsNull(id, user.getCouple().getId())
                .orElseThrow(() -> ApiException.notFound("回忆不存在"));
        return views.memory(memory);
    }

    @Transactional(readOnly = true)
    public List<MemoryTagView> tags(Authentication auth) {
        User user = current.user(auth);
        return memories.aggregateActiveTags(user.getCouple().getId()).stream()
                .map(item -> new MemoryTagView(item.getName(), item.getMemoryCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AlbumItemView> album(Authentication auth, int page, int size, String search, String tag) {
        User user = current.user(auth);
        if (page < 0 || size < 1 || size > 100) throw ApiException.badRequest("分页参数无效");
        String keyword = AccountService.trimToNull(search);
        String keywordPattern = keyword == null ? null : "%" + escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
        String selectedTag = normalizeTagForQuery(tag);
        Long coupleId = user.getCouple().getId();
        if (!offsetFitsJpa(page, size)) {
            return emptyPage(page, size, media.countAlbumMedia(coupleId, keywordPattern, selectedTag));
        }
        int offset = (int) ((long) page * size);
        List<Media> visualMedia = media.findAlbumMedia(coupleId, keywordPattern, selectedTag, offset, size);
        long total = media.countAlbumMedia(coupleId, keywordPattern, selectedTag);
        Map<Long, Memory> memoryById = memories.findAllById(visualMedia.stream()
                        .map(Media::getMemoryId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Memory::getId, value -> value));
        List<AlbumItemView> content = visualMedia.stream().map(item -> {
            Memory memory = memoryById.get(item.getMemoryId());
            if (memory == null) return null;
            return new AlbumItemView(views.media(item), memory.getId(), memory.getTitle(),
                    BeijingTime.toOffset(memory.getEventAt()), memory.getLocation(), List.copyOf(memory.getTags()));
        }).filter(Objects::nonNull).toList();
        return pageResponse(page, size, total, content);
    }

    @Transactional
    public MemoryView create(Authentication auth, MemoryRequest request, List<MultipartFile> files) {
        User user = current.user(auth);
        List<MultipartFile> incoming = storage.validateMemoryMediaBatch(user, null, files);
        Memory memory = new Memory();
        memory.setCoupleId(user.getCouple().getId()); memory.setAuthorId(user.getId());
        apply(memory, request);
        memories.saveAndFlush(memory);
        List<Media> stored = new ArrayList<>();
        try {
            for (MultipartFile file : incoming) stored.add(storage.store(user, memory.getId(), file));
            return views.memory(memory);
        } catch (RuntimeException ex) {
            stored.forEach(storage::deletePhysical);
            throw ex;
        }
    }

    @Transactional
    public MemoryView update(Authentication auth, Long id, MemoryUpdateRequest request) {
        return update(auth, id, request, List.of());
    }

    @Transactional
    public MemoryView update(Authentication auth, Long id, MemoryUpdateRequest request,
                             List<MultipartFile> files) {
        User user = current.user(auth);
        Memory memory = memories.findByIdAndCoupleIdAndDeletedAtIsNull(id, user.getCouple().getId())
                .orElseThrow(() -> ApiException.notFound("回忆不存在"));
        if (!memory.getAuthorId().equals(user.getId())) throw ApiException.forbidden("只能编辑自己的回忆");
        versions.requireFresh(request.version(), memory.getVersion());
        List<MultipartFile> incoming = storage.validateMemoryMediaBatch(user, memory.getId(), files);
        apply(memory, request);
        List<Media> stored = new ArrayList<>();
        try {
            for (MultipartFile file : incoming) stored.add(storage.store(user, memory.getId(), file));
            Memory saved = memories.saveAndFlush(memory);
            return views.memory(saved);
        } catch (RuntimeException ex) {
            stored.forEach(storage::deletePhysical);
            throw ex;
        }
    }

    @Transactional
    public MemoryView addMedia(Authentication auth, Long id, List<MultipartFile> files) {
        User user = current.user(auth);
        Memory memory = ownedMemory(user, id);
        List<MultipartFile> incoming = files == null ? List.of() : files.stream()
                .filter(Objects::nonNull).filter(file -> !file.isEmpty()).toList();
        if (incoming.isEmpty()) throw ApiException.badRequest("请选择要上传的媒体文件");
        incoming = storage.validateMemoryMediaBatch(user, memory.getId(), incoming);
        List<Media> stored = new ArrayList<>();
        try {
            incoming.forEach(file -> stored.add(storage.store(user, memory.getId(), file)));
            return views.memory(memory);
        } catch (RuntimeException ex) {
            stored.forEach(storage::deletePhysical);
            throw ex;
        }
    }

    @Transactional
    public MemoryView deleteMedia(Authentication auth, Long id, Long mediaId) {
        User user = current.user(auth);
        Memory memory = ownedMemory(user, id);
        storage.delete(user, memory, mediaId);
        return views.memory(memory);
    }

    @Transactional(readOnly = true)
    public MemoryView random(Authentication auth, Long excludeId) {
        User user = current.user(auth);
        Specification<Memory> scope = (root, query, cb) -> {
            Predicate couple = cb.equal(root.get("coupleId"), user.getCouple().getId());
            Predicate active = cb.isNull(root.get("deletedAt"));
            return excludeId == null ? cb.and(couple, active)
                    : cb.and(couple, active, cb.notEqual(root.get("id"), excludeId));
        };
        long total = memories.count(scope);
        if (total == 0) {
            if (excludeId != null) {
                throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "NO_ALTERNATIVE_MEMORY", "暂时没有其他回忆可抽取");
            }
            throw ApiException.notFound("还没有可抽取的回忆");
        }
        return memories.findRandomActive(user.getCouple().getId(), excludeId)
                .map(views::memory).orElseThrow(() -> ApiException.notFound("还没有可抽取的回忆"));
    }

    @Transactional
    public void delete(Authentication auth, Long id) {
        User user = current.user(auth);
        Memory memory = memories.findByIdAndCoupleIdAndDeletedAtIsNull(id, user.getCouple().getId())
                .orElseThrow(() -> ApiException.notFound("回忆不存在"));
        if (!memory.getAuthorId().equals(user.getId())) throw ApiException.forbidden("只能删除自己的回忆");
        memory.moveToTrash(user.getId(), BeijingTime.now());
        memories.save(memory);
    }

    private void apply(Memory value, MemoryRequest input) {
        apply(value, input.title(), input.description(), input.eventAt(), input.eventTimeKnown(),
                input.location(), input.tags());
    }

    private void apply(Memory value, MemoryUpdateRequest input) {
        apply(value, input.title(), input.description(), input.eventAt(), input.eventTimeKnown(),
                input.location(), input.tags());
    }

    private void apply(Memory value, String title, String description,
                       java.time.OffsetDateTime eventAtInput, Boolean eventTimeKnownInput,
                       String location, List<String> tags) {
        value.setTitle(title.trim());
        value.setDescription(AccountService.trimToNull(description));
        LocalDateTime eventAt = BeijingTime.toLocal(eventAtInput);
        boolean eventTimeKnown = eventTimeKnownInput == null || eventTimeKnownInput;
        value.setEventTimeKnown(eventTimeKnown);
        value.setEventAt(eventTimeKnown ? eventAt : eventAt.toLocalDate().atStartOfDay());
        value.setLocation(AccountService.trimToNull(location));
        LinkedHashSet<String> normalizedTags = new LinkedHashSet<>();
        Map<String, String> displayTagsByKey = new LinkedHashMap<>();
        if (tags != null) {
            for (String tag : tags) {
                String normalized = tag == null ? null : tag.trim().replaceAll("\\s+", " ");
                if (normalized != null && !normalized.isBlank()) {
                    // MySQL utf8mb4_unicode_ci is case/accent insensitive. Keep the
                    // first request spelling for display, but deduplicate by the same
                    // folded key before Hibernate writes the composite PK.
                    displayTagsByKey.putIfAbsent(tagKey(normalized), normalized);
                }
            }
        }
        normalizedTags.addAll(displayTagsByKey.values());
        if (normalizedTags.size() > 12) throw ApiException.badRequest("每段回忆最多添加 12 个标签");
        value.setTags(normalizedTags);
    }
    private Memory ownedMemory(User user, Long id) {
        Memory memory = memories.findByIdAndCoupleIdAndDeletedAtIsNull(id, user.getCouple().getId())
                .orElseThrow(() -> ApiException.notFound("回忆不存在"));
        if (!memory.getAuthorId().equals(user.getId())) throw ApiException.forbidden("只能编辑自己的回忆");
        return memory;
    }
    private String normalizeTagForQuery(String value) {
        String normalized = AccountService.trimToNull(value);
        return normalized == null ? null : normalized.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
    private boolean offsetFitsJpa(int page, int size) { return (long) page * size <= Integer.MAX_VALUE; }
    private <T> PageResponse<T> emptyPage(int page, int size, long total) {
        return pageResponse(page, size, total, List.of());
    }
    private <T> PageResponse<T> pageResponse(int page, int size, long total, List<T> content) {
        long pageCount = total / size + (total % size == 0 ? 0 : 1);
        int totalPages = pageCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pageCount;
        boolean last = pageCount == 0 || (long) page >= pageCount - 1;
        return new PageResponse<>(content, page, size, total, totalPages, page == 0, last);
    }
    private String tagKey(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .toLowerCase(Locale.ROOT);
    }
    private String escapeLike(String text) { return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }
}
