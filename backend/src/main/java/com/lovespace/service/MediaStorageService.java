package com.lovespace.service;

import com.lovespace.api.dto.ApiDtos.MediaView;
import com.lovespace.api.error.ApiException;
import com.lovespace.domain.*;
import com.lovespace.repository.*;
import com.lovespace.security.CurrentUserService;
import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaStorageService {
    private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);
    private static final Map<String, String> ALLOWED_TYPES;
    static {
        Map<String, String> types = new HashMap<>();
        for (String value : List.of("image/jpeg", "image/png", "image/gif", "image/webp", "image/avif", "image/heic")) types.put(value, "image");
        for (String value : List.of("video/mp4", "video/webm", "video/quicktime", "video/x-matroska")) types.put(value, "video");
        for (String value : List.of("audio/mpeg", "audio/mp4", "audio/wav", "audio/x-wav", "audio/ogg", "audio/webm", "audio/aac", "audio/x-m4a")) types.put(value, "audio");
        ALLOWED_TYPES = Map.copyOf(types);
    }

    private final Path root;
    private final long maxBytes;
    private final long maxTotalBytes;
    private final MediaRepository media;
    private final UserRepository users;
    private final CurrentUserService current;
    private final ViewMapper views;

    public MediaStorageService(@Value("${app.upload-dir:./data/uploads}") String uploadDir,
                               @Value("${app.media-max-bytes:52428800}") long maxBytes,
                               @Value("${app.media-total-max-bytes:2147483648}") long maxTotalBytes,
                               MediaRepository media, UserRepository users,
                               CurrentUserService current, ViewMapper views) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes; this.maxTotalBytes = maxTotalBytes;
        this.media = media; this.users = users; this.current = current; this.views = views;
    }

    @PostConstruct
    void initializeDirectory() throws IOException { Files.createDirectories(root); }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public MediaView updateAvatar(Authentication auth, MultipartFile file) {
        User user = current.user(auth);
        requireImage(file);
        Media stored = store(user, null, file);
        Long previousId = user.getAvatarMediaId();
        user.setAvatarMediaId(stored.getId());
        users.saveAndFlush(user);
        if (previousId != null) {
            media.findByIdAndCoupleId(previousId, user.getCouple().getId()).ifPresent(old -> {
                media.delete(old);
                deletePhysicalAfterCommit(old);
            });
        }
        return views.media(stored);
    }

    public Media store(User owner, Long memoryId, MultipartFile file) {
        validate(file);
        long usedBytes = media.totalBytesByCoupleId(owner.getCouple().getId());
        if (file.getSize() > maxTotalBytes - usedBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "STORAGE_QUOTA_EXCEEDED",
                    "情侣空间的媒体存储额度已满");
        }
        String storedName = UUID.randomUUID().toString();
        Path target = safeResolve(storedName);
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target);
            Media value = new Media();
            value.setCoupleId(owner.getCouple().getId());
            value.setOwnerId(owner.getId());
            value.setMemoryId(memoryId);
            value.setStoredName(storedName);
            value.setOriginalName(safeOriginalName(file.getOriginalFilename()));
            value.setContentType(normalizeContentType(file.getContentType()));
            value.setMediaType(ALLOWED_TYPES.get(value.getContentType()));
            value.setByteSize(file.getSize());
            Media saved = media.save(value);
            deleteOnRollback(target);
            return saved;
        } catch (IOException | RuntimeException ex) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            if (ex instanceof ApiException api) throw api;
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_ERROR", "保存上传文件失败");
        }
    }

    @Transactional(readOnly = true)
    public MediaDownload load(Authentication auth, Long id) {
        User user = current.user(auth);
        Media value = media.findByIdAndCoupleId(id, user.getCouple().getId())
                .orElseThrow(() -> ApiException.notFound("媒体不存在"));
        Path path = safeResolve(value.getStoredName());
        if (!Files.isRegularFile(path)) throw ApiException.notFound("媒体文件不存在");
        return new MediaDownload(value, new FileSystemResource(path.toFile()));
    }

    public void deletePhysical(Media value) {
        deletePath(safeResolve(value.getStoredName()));
    }

    public void deletePhysicalAfterCommit(Media value) {
        Path path = safeResolve(value.getStoredName());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { deletePath(path); }
            });
        } else {
            deletePath(path);
        }
    }

    private void deleteOnRollback(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) deletePath(path);
            }
        });
    }

    private void deletePath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Could not delete media file {}. It can be removed during maintenance.", path.getFileName(), ex);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("上传文件不能为空");
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "单个文件超过大小限制");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_TYPES.containsKey(contentType)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "仅支持图片、视频和音频文件");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(64);
            if (!hasExpectedSignature(contentType, header)) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_FILE_CONTENT", "文件内容与声明的类型不符");
            }
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_READ_ERROR", "无法读取上传文件");
        }
    }

    private void requireImage(MultipartFile file) {
        validate(file);
        if (!"image".equals(ALLOWED_TYPES.get(normalizeContentType(file.getContentType())))) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "AVATAR_MUST_BE_IMAGE", "头像必须是图片");
        }
    }

    private String normalizeContentType(String value) {
        if (value == null) return "";
        int semicolon = value.indexOf(';');
        return (semicolon >= 0 ? value.substring(0, semicolon) : value).trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasExpectedSignature(String contentType, byte[] header) {
        return switch (contentType) {
            case "image/jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/gif" -> asciiAt(header, 0, "GIF87a") || asciiAt(header, 0, "GIF89a");
            case "image/webp" -> asciiAt(header, 0, "RIFF") && asciiAt(header, 8, "WEBP");
            case "image/avif" -> isoBaseMedia(header) && (asciiAt(header, 8, "avif") || asciiAt(header, 8, "avis"));
            case "image/heic" -> isoBaseMedia(header) && (asciiAt(header, 8, "heic") || asciiAt(header, 8, "heix")
                    || asciiAt(header, 8, "hevc") || asciiAt(header, 8, "hevx") || asciiAt(header, 8, "mif1"));
            case "video/mp4", "video/quicktime", "audio/mp4", "audio/x-m4a" -> isoBaseMedia(header);
            case "video/webm", "video/x-matroska", "audio/webm" -> startsWith(header, 0x1A, 0x45, 0xDF, 0xA3);
            case "audio/mpeg" -> asciiAt(header, 0, "ID3") || (header.length >= 2 && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xE0) == 0xE0);
            case "audio/wav", "audio/x-wav" -> asciiAt(header, 0, "RIFF") && asciiAt(header, 8, "WAVE");
            case "audio/ogg" -> asciiAt(header, 0, "OggS");
            case "audio/aac" -> header.length >= 2 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xF6) == 0xF0;
            default -> false;
        };
    }

    private boolean isoBaseMedia(byte[] header) { return asciiAt(header, 4, "ftyp"); }

    private boolean asciiAt(byte[] header, int offset, String value) {
        if (header.length < offset + value.length()) return false;
        for (int i = 0; i < value.length(); i++) if (header[offset + i] != (byte) value.charAt(i)) return false;
        return true;
    }

    private boolean startsWith(byte[] header, int... bytes) {
        if (header.length < bytes.length) return false;
        for (int i = 0; i < bytes.length; i++) if ((header[i] & 0xFF) != bytes[i]) return false;
        return true;
    }

    private String safeOriginalName(String value) {
        String cleaned = StringUtils.cleanPath(value == null ? "file" : value).replace('\r', '_').replace('\n', '_');
        try {
            Path name = Path.of(cleaned).getFileName();
            cleaned = name == null ? "file" : name.toString();
        } catch (InvalidPathException ex) { cleaned = "file"; }
        if (cleaned.isBlank()) cleaned = "file";
        return cleaned.length() > 255 ? cleaned.substring(cleaned.length() - 255) : cleaned;
    }

    private Path safeResolve(String storedName) {
        if (storedName == null || !storedName.matches("[0-9a-fA-F-]{36}")) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_STORAGE_PATH", "媒体存储路径无效");
        }
        Path result = root.resolve(storedName).normalize();
        if (!result.startsWith(root)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_STORAGE_PATH", "媒体存储路径无效");
        }
        return result;
    }

    public record MediaDownload(Media media, Resource resource) {}
}
