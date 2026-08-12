package com.lovespace.service;

import com.lovespace.api.error.ApiException;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OptimisticUpdateGuard {
    public static final String STALE_UPDATE_CODE = "STALE_UPDATE";
    public static final String STALE_UPDATE_MESSAGE = "对方或另一台设备已修改此内容，请加载最新版本后重新确认。";

    public void requireFresh(Long expectedVersion, Long currentVersion) {
        if (!Objects.equals(expectedVersion, currentVersion)) {
            throw new ApiException(HttpStatus.CONFLICT, STALE_UPDATE_CODE, STALE_UPDATE_MESSAGE);
        }
    }
}
