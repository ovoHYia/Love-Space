package com.lovespace.service;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.api.error.ApiException;
import com.lovespace.domain.*;
import com.lovespace.repository.*;
import com.lovespace.security.CurrentUserService;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MemoryService {
    private final MemoryRepository memories;
    private final MediaRepository media;
    private final CurrentUserService current;
    private final MediaStorageService storage;
    private final ViewMapper views;
    public MemoryService(MemoryRepository memories, MediaRepository media, CurrentUserService current,
                         MediaStorageService storage, ViewMapper views) {
        this.memories = memories; this.media = media; this.current = current;
        this.storage = storage; this.views = views;
    }

    @Transactional(readOnly = true)
    public PageResponse<MemoryView> list(Authentication auth, int page, int size, String search,
                                         LocalDate date) {
        User user = current.user(auth);
        if (page < 0 || size < 1 || size > 100) throw ApiException.badRequest("分页参数无效");
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "eventAt", "id"));
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
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<Memory> result = memories.findAll(spec, pageable);
        List<MemoryView> content = views.memories(result.getContent());
        return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public MemoryView create(Authentication auth, MemoryRequest request, List<MultipartFile> files) {
        User user = current.user(auth);
        if (files != null && files.stream().filter(Objects::nonNull).filter(file -> !file.isEmpty()).count() > 20) {
            throw ApiException.badRequest("一次最多上传 20 个媒体文件");
        }
        Memory memory = new Memory();
        memory.setCoupleId(user.getCouple().getId()); memory.setAuthorId(user.getId());
        apply(memory, request);
        memories.saveAndFlush(memory);
        List<Media> stored = new ArrayList<>();
        try {
            if (files != null) {
                for (MultipartFile file : files) if (file != null && !file.isEmpty()) stored.add(storage.store(user, memory.getId(), file));
            }
            return views.memory(memory);
        } catch (RuntimeException ex) {
            stored.forEach(storage::deletePhysical);
            throw ex;
        }
    }

    @Transactional
    public MemoryView update(Authentication auth, Long id, MemoryRequest request) {
        User user = current.user(auth);
        Memory memory = memories.findByIdAndCoupleIdAndDeletedAtIsNull(id, user.getCouple().getId())
                .orElseThrow(() -> ApiException.notFound("回忆不存在"));
        if (!memory.getAuthorId().equals(user.getId())) throw ApiException.forbidden("只能编辑自己的回忆");
        apply(memory, request);
        return views.memory(memories.save(memory));
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
        int index = java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.toIntExact(total));
        return memories.findAll(scope, PageRequest.of(index, 1, Sort.by("id"))).stream().findFirst()
                .map(views::memory).orElseThrow(() -> ApiException.notFound("还没有可抽取的回忆"));
    }

    @Transactional
    public void delete(Authentication auth, Long id) {
        User user = current.user(auth);
        Memory memory = memories.findByIdAndCoupleIdAndDeletedAtIsNull(id, user.getCouple().getId())
                .orElseThrow(() -> ApiException.notFound("回忆不存在"));
        if (!memory.getAuthorId().equals(user.getId())) throw ApiException.forbidden("只能删除自己的回忆");
        memory.moveToTrash(user.getId(), LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        memories.save(memory);
    }

    private void apply(Memory value, MemoryRequest input) {
        value.setTitle(input.title().trim());
        value.setDescription(AccountService.trimToNull(input.description()));
        value.setEventAt(input.eventAt());
        value.setLocation(AccountService.trimToNull(input.location()));
    }
    private String escapeLike(String text) { return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }
}
