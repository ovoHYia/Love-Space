package com.lovespace.repository;

import com.lovespace.domain.Media;
import java.util.List;

public interface MediaAlbumRepository {
    List<Media> findAlbumMedia(Long coupleId, String keywordPattern, String tagKey, int offset, int size);

    long countAlbumMedia(Long coupleId, String keywordPattern, String tagKey);
}
