package com.lovespace.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RealtimeSyncInterceptorTest {
    @Test
    void trashRestorePublishesTrashAndThePluralBusinessResource() {
        assertEquals(List.of("trash", "memories"),
                RealtimeSyncInterceptor.resourcesOf("/api/trash/MEMORY/7/restore"));
        assertEquals(List.of("trash", "messages"),
                RealtimeSyncInterceptor.resourcesOf("/api/trash/MESSAGE/8/restore"));
        assertEquals(List.of("trash", "calendar"),
                RealtimeSyncInterceptor.resourcesOf("/api/trash/CALENDAR_EVENT/9/restore"));
    }

    @Test
    void ordinaryMutationsKeepTheirExistingResourceName() {
        assertEquals(List.of("memories"), RealtimeSyncInterceptor.resourcesOf("/api/memories/7"));
        assertEquals(List.of("games"), RealtimeSyncInterceptor.resourcesOf("/api/games/3/strokes"));
    }
}
