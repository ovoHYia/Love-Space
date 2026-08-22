package com.lovespace.service;

import com.lovespace.api.dto.ApiDtos.MediaView;
import com.lovespace.api.error.ApiException;
import com.lovespace.domain.*;
import com.lovespace.repository.*;
import com.lovespace.security.CurrentUserService;
import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
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
    private final long minFreeBytes;
    private final MediaRepository media;
    private final CoupleRepository couples;
    private final UserRepository users;
    private final CurrentUserService current;
    private final ViewMapper views;
    private final MediaFileSystem fileSystem;
    // 近期通过全文哈希校验的媒体；大小校验仍是每次执行的快速路径，
    // 命中缓存时跳过昂贵的全文 SHA-256 重算（缓存带 TTL，兼顾性能与完整性底线）
    private final Map<String, HashVerification> verifiedHashes = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long HASH_VERIFICATION_TTL_MILLIS = java.util.concurrent.TimeUnit.MINUTES.toMillis(15);
    private static final int MAX_CACHED_HASH_VERIFICATIONS = 10_000;

    public MediaStorageService(@Value("${app.upload-dir:./data/uploads}") String uploadDir,
                               @Value("${app.media-max-bytes:52428800}") long maxBytes,
                               @Value("${app.media-total-max-bytes:2147483648}") long maxTotalBytes,
                               @Value("${app.media-min-free-bytes:67108864}") long minFreeBytes,
                               MediaRepository media, CoupleRepository couples, UserRepository users,
                               CurrentUserService current, ViewMapper views, MediaFileSystem fileSystem) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes; this.maxTotalBytes = maxTotalBytes; this.minFreeBytes = minFreeBytes;
        this.media = media; this.couples = couples; this.users = users;
        this.current = current; this.views = views; this.fileSystem = fileSystem;
    }

    @PostConstruct
    void initializeDirectory() throws IOException { fileSystem.createDirectories(root); }

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

    @Transactional(propagation = Propagation.MANDATORY)
    public Media store(User owner, Long memoryId, MultipartFile file) {
        String detectedContentType = validate(file);
        Long coupleId = owner.getCouple().getId();
        couples.findByIdForUpdate(coupleId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT, "STORAGE_OWNER_MISSING", "情侣空间不存在"));
        long usedBytes = media.totalBytesByCoupleId(coupleId);
        if (usedBytes > maxTotalBytes || file.getSize() > maxTotalBytes - usedBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "STORAGE_QUOTA_EXCEEDED",
                    "情侣空间的媒体存储额度已满");
        }
        requireDiskCapacity(file.getSize());
        String storedName = UUID.randomUUID().toString();
        Path target = safeResolve(storedName);
        try (InputStream raw = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(raw, digest)) {
            fileSystem.copy(input, target);
            Media value = new Media();
            value.setCoupleId(coupleId);
            value.setOwnerId(owner.getId());
            value.setMemoryId(memoryId);
            value.setStoredName(storedName);
            value.setOriginalName(safeOriginalName(file.getOriginalFilename()));
            value.setContentType(detectedContentType);
            value.setMediaType(ALLOWED_TYPES.get(value.getContentType()));
            value.setByteSize(Files.size(target));
            value.setSha256(HexFormat.of().formatHex(digest.digest()));
            Media saved = media.save(value);
            deleteOnRollback(target);
            return saved;
            }
        } catch (IOException | RuntimeException | NoSuchAlgorithmException ex) {
            try { fileSystem.deleteIfExists(target); } catch (IOException ignored) { }
            if (ex instanceof ApiException api) throw api;
            if (ex instanceof IOException && hasInsufficientDiskSpace(file.getSize())) {
                throw insufficientStorage();
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_ERROR", "保存上传文件失败");
        }
    }

    /**
     * 在任何媒体写入前完成批量校验，避免文字已经改变后才发现数量、配额或磁盘不足。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<MultipartFile> validateMemoryMediaBatch(User owner, Long memoryId,
                                                         List<MultipartFile> files) {
        List<MultipartFile> incoming = files == null ? List.of() : files.stream()
                .filter(Objects::nonNull).filter(file -> !file.isEmpty()).toList();
        int existingCount = memoryId == null ? 0 : media.findByMemoryId(memoryId).size();
        if (existingCount + incoming.size() > 20) {
            throw ApiException.badRequest("每段回忆最多保存 20 个媒体文件");
        }
        long incomingBytes = 0;
        for (MultipartFile file : incoming) {
            validate(file);
            try {
                incomingBytes = Math.addExact(incomingBytes, file.getSize());
            } catch (ArithmeticException ex) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "STORAGE_QUOTA_EXCEEDED",
                        "情侣空间的媒体存储额度已满");
            }
        }
        if (incoming.isEmpty()) return incoming;
        Long coupleId = owner.getCouple().getId();
        couples.findByIdForUpdate(coupleId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT, "STORAGE_OWNER_MISSING", "情侣空间不存在"));
        long usedBytes = media.totalBytesByCoupleId(coupleId);
        if (usedBytes > maxTotalBytes || incomingBytes > maxTotalBytes - usedBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "STORAGE_QUOTA_EXCEEDED",
                    "情侣空间的媒体存储额度已满");
        }
        requireDiskCapacity(incomingBytes);
        return incoming;
    }

    @Transactional
    public void delete(User user, Memory memory, Long mediaId) {
        if (!memory.getAuthorId().equals(user.getId())) throw ApiException.forbidden("只能管理自己回忆中的媒体");
        Media value = media.findByIdAndCoupleId(mediaId, user.getCouple().getId())
                .filter(item -> Objects.equals(item.getMemoryId(), memory.getId()))
                .orElseThrow(() -> ApiException.notFound("媒体不存在"));
        media.delete(value);
        deletePhysicalAfterCommit(value);
    }

    @Transactional(readOnly = true)
    public MediaDownload load(Authentication auth, Long id) {
        User user = current.user(auth);
        Media value = media.findAccessibleByIdAndCoupleId(id, user.getCouple().getId())
                .orElseThrow(() -> ApiException.notFound("媒体不存在"));
        Path path = safeResolve(value.getStoredName());
        try {
            verifyReadable(value, path);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "MEDIA_FILE_UNREADABLE",
                    "媒体文件无法读取，请先检查存储完整性。媒体 ID：" + value.getId());
        }
        return new MediaDownload(value, new FileSystemResource(path.toFile()));
    }

    public void deletePhysical(Media value) {
        deletePath(safeResolve(value.getStoredName()));
    }

    public InputStream openForExport(Media value) throws IOException {
        Path path = safeResolve(value.getStoredName());
        verifyReadable(value, path);
        return Files.newInputStream(path, StandardOpenOption.READ);
    }

    public long verifiedFileSize(Media value) throws IOException {
        Path path = safeResolve(value.getStoredName());
        verifyReadable(value, path);
        return Files.size(path);
    }

    Path physicalPath(String storedName) { return safeResolve(storedName); }

    Path storageRoot() { return root; }

    static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
                input.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 unavailable", ex);
        }
    }

    private void verifyReadable(Media value, Path path) throws IOException {
        if (!fileSystem.isRegularFile(path)) {
            throw new ApiException(HttpStatus.CONFLICT, "MEDIA_FILE_MISSING",
                    "媒体原文件缺失，无法继续读取。媒体 ID：" + value.getId());
        }
        long actualSize = Files.size(path);
        if (actualSize != value.getByteSize()) {
            throw new ApiException(HttpStatus.CONFLICT, "MEDIA_SIZE_MISMATCH",
                    "媒体文件大小与记录不一致，无法继续读取。媒体 ID：" + value.getId());
        }
        if (value.getSha256() != null && !value.getSha256().isBlank()) {
            if (!isRecentlyVerified(value.getStoredName(), value.getSha256())) {
                String actualHash = sha256(path);
                if (!MessageDigest.isEqual(value.getSha256().getBytes(StandardCharsets.US_ASCII),
                        actualHash.getBytes(StandardCharsets.US_ASCII))) {
                    throw new ApiException(HttpStatus.CONFLICT, "MEDIA_HASH_MISMATCH",
                            "媒体文件完整性校验失败，无法继续读取。媒体 ID：" + value.getId());
                }
                rememberVerified(value.getStoredName(), value.getSha256());
            }
        }
    }

    private boolean isRecentlyVerified(String storedName, String expectedSha256) {
        HashVerification cached = verifiedHashes.get(storedName);
        return cached != null && cached.sha256().equals(expectedSha256)
                && cached.verifiedAt() + HASH_VERIFICATION_TTL_MILLIS > System.currentTimeMillis();
    }

    private void rememberVerified(String storedName, String sha256) {
        if (verifiedHashes.size() >= MAX_CACHED_HASH_VERIFICATIONS) {
            long now = System.currentTimeMillis();
            verifiedHashes.entrySet().removeIf(entry ->
                    entry.getValue().verifiedAt() + HASH_VERIFICATION_TTL_MILLIS <= now);
        }
        verifiedHashes.put(storedName, new HashVerification(sha256, System.currentTimeMillis()));
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
            fileSystem.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Could not delete media file {}. It can be removed during maintenance.", path.getFileName(), ex);
        }
    }

    private void requireDiskCapacity(long incomingBytes) {
        try {
            long usableBytes = fileSystem.usableSpace(root);
            if (incomingBytes > usableBytes || minFreeBytes > usableBytes - incomingBytes) {
                throw insufficientStorage();
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_CHECK_FAILED",
                    "无法检查服务器存储空间");
        }
    }

    private boolean hasInsufficientDiskSpace(long incomingBytes) {
        try {
            long usableBytes = fileSystem.usableSpace(root);
            return incomingBytes > usableBytes || minFreeBytes > usableBytes - incomingBytes;
        } catch (IOException ignored) {
            return false;
        }
    }

    private ApiException insufficientStorage() {
        return new ApiException(HttpStatus.INSUFFICIENT_STORAGE, "INSUFFICIENT_STORAGE",
                "服务器存储空间不足，请联系管理员");
    }

    private String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("上传文件不能为空");
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "单个文件超过大小限制");
        }
        String declaredContentType = normalizeContentType(file.getContentType());
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(512);
            String detectedContentType = detectContentType(header, declaredContentType);
            if (detectedContentType == null && !ALLOWED_TYPES.containsKey(declaredContentType)) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                        "仅支持图片、视频和音频文件");
            }
            if (detectedContentType == null) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_FILE_CONTENT", "文件内容与声明的类型不符");
            }
            return detectedContentType;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_READ_ERROR", "无法读取上传文件");
        }
    }

    private void requireImage(MultipartFile file) {
        String detectedContentType = validate(file);
        if (!"image".equals(ALLOWED_TYPES.get(detectedContentType))) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "AVATAR_MUST_BE_IMAGE", "头像必须是图片");
        }
    }

    private String normalizeContentType(String value) {
        if (value == null) return "";
        int semicolon = value.indexOf(';');
        return (semicolon >= 0 ? value.substring(0, semicolon) : value).trim().toLowerCase(Locale.ROOT);
    }

    private String detectContentType(byte[] header, String declaredContentType) {
        if (startsWith(header, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return "image/png";
        if (asciiAt(header, 0, "GIF87a") || asciiAt(header, 0, "GIF89a")) return "image/gif";
        if (asciiAt(header, 0, "RIFF") && asciiAt(header, 8, "WEBP")) return "image/webp";
        if (asciiAt(header, 0, "RIFF") && asciiAt(header, 8, "WAVE")) return "audio/wav";
        if (asciiAt(header, 0, "OggS")) return "audio/ogg";
        if (header.length >= 2 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xF6) == 0xF0) return "audio/aac";
        if (asciiAt(header, 0, "ID3") || (header.length >= 2 && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xE0) == 0xE0)) return "audio/mpeg";
        if (startsWith(header, 0x1A, 0x45, 0xDF, 0xA3)) {
            return switch (declaredContentType) {
                case "audio/webm" -> "audio/webm";
                case "video/x-matroska" -> "video/x-matroska";
                default -> "video/webm";
            };
        }
        if (!isoBaseMedia(header)) return null;
        if (containsBrand(header, "avif") || containsBrand(header, "avis")) return "image/avif";
        if (containsAnyBrand(header, "heic", "heix", "hevc", "hevx", "heim", "heis", "heif", "mif1", "msf1")) {
            return "image/heic";
        }
        if (ALLOWED_TYPES.containsKey(declaredContentType)
                && Set.of("video/mp4", "video/quicktime", "audio/mp4", "audio/x-m4a").contains(declaredContentType)) {
            return declaredContentType;
        }
        if (containsBrand(header, "qt  ")) return "video/quicktime";
        if (containsAnyBrand(header, "M4A ", "M4B ", "M4P ")) return "audio/mp4";
        if (containsAnyBrand(header, "isom", "iso2", "mp41", "mp42", "avc1", "dash", "M4V ")) return "video/mp4";
        return null;
    }

    private boolean isoBaseMedia(byte[] header) { return asciiAt(header, 4, "ftyp"); }

    private boolean containsAnyBrand(byte[] header, String... brands) {
        for (String brand : brands) if (containsBrand(header, brand)) return true;
        return false;
    }

    private boolean containsBrand(byte[] header, String brand) {
        for (int offset = 8; offset + brand.length() <= header.length; offset += 4) {
            if (asciiAt(header, offset, brand)) return true;
        }
        return false;
    }

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

    private record HashVerification(String sha256, long verifiedAt) {}

    public record MediaDownload(Media media, Resource resource) {}
}
