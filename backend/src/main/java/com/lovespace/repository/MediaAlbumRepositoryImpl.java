package com.lovespace.repository;

import com.lovespace.domain.Media;
import com.lovespace.domain.Memory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;

public class MediaAlbumRepositoryImpl implements MediaAlbumRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Media> findAlbumMedia(Long coupleId, String keywordPattern, String tagKey,
                                      int offset, int size) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Media> query = builder.createQuery(Media.class);
        Root<Media> media = query.from(Media.class);
        Root<Memory> memory = query.from(Memory.class);

        query.select(media)
                .where(albumPredicates(builder, query, media, memory, coupleId, keywordPattern, tagKey))
                .orderBy(builder.desc(memory.get("eventAt")), builder.desc(media.get("id")));

        TypedQuery<Media> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult(offset);
        typedQuery.setMaxResults(size);
        return typedQuery.getResultList();
    }

    @Override
    public long countAlbumMedia(Long coupleId, String keywordPattern, String tagKey) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<Media> media = query.from(Media.class);
        Root<Memory> memory = query.from(Memory.class);

        query.select(builder.count(media))
                .where(albumPredicates(builder, query, media, memory, coupleId, keywordPattern, tagKey));
        return entityManager.createQuery(query).getSingleResult();
    }

    private Predicate[] albumPredicates(CriteriaBuilder builder, CriteriaQuery<?> query,
                                        Root<Media> media, Root<Memory> memory,
                                        Long coupleId, String keywordPattern, String tagKey) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.equal(media.get("coupleId"), coupleId));
        predicates.add(builder.equal(memory.get("id"), media.get("memoryId")));
        predicates.add(builder.equal(memory.get("coupleId"), coupleId));
        predicates.add(builder.isNull(memory.get("deletedAt")));

        Expression<String> mediaType = builder.lower(media.get("mediaType"));
        predicates.add(builder.or(builder.equal(mediaType, "image"), builder.equal(mediaType, "video")));

        if (keywordPattern != null) {
            Expression<String> title = builder.lower(memory.get("title"));
            Expression<String> description = builder.lower(memory.get("description"));
            Expression<String> location = builder.lower(memory.get("location"));
            predicates.add(builder.or(
                    builder.like(title, keywordPattern, '\\'),
                    builder.like(description, keywordPattern, '\\'),
                    builder.like(location, keywordPattern, '\\')));
        }

        if (tagKey != null) {
            Subquery<String> tagQuery = query.subquery(String.class);
            Root<Memory> taggedMemory = tagQuery.from(Memory.class);
            Join<Memory, String> tag = taggedMemory.join("tags");
            tagQuery.select(tag).where(
                    builder.equal(taggedMemory.get("id"), memory.get("id")),
                    builder.equal(builder.lower(tag), tagKey));
            predicates.add(builder.exists(tagQuery));
        }
        return predicates.toArray(Predicate[]::new);
    }
}
