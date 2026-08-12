package com.lovespace.api;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.service.MemoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/memories")
public class MemoryController {
    private final MemoryService memories;
    public MemoryController(MemoryService memories) { this.memories = memories; }
    @GetMapping("/{id}")
    public MemoryView get(Authentication auth, @PathVariable @Positive Long id) {
        return memories.get(auth, id);
    }
    @GetMapping
    public PageResponse<MemoryView> list(Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") @jakarta.validation.constraints.Size(max = 200) String q,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 30) String tag) {
        return memories.list(auth, page, size, q, date, tag);
    }
    @GetMapping("/tags")
    public List<MemoryTagView> tags(Authentication auth) { return memories.tags(auth); }
    @GetMapping("/album")
    public PageResponse<AlbumItemView> album(Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(defaultValue = "") @jakarta.validation.constraints.Size(max = 200) String q,
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 30) String tag) {
        return memories.album(auth, page, size, q, tag);
    }
    @GetMapping("/random")
    public MemoryView random(Authentication auth, @RequestParam(required = false) @Positive Long excludeId) {
        return memories.random(auth, excludeId);
    }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryView create(Authentication auth, @Valid @RequestPart("data") MemoryRequest data,
                             @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return memories.create(auth, data, files);
    }
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MemoryView update(Authentication auth, @PathVariable @Positive Long id,
                             @Valid @RequestBody MemoryUpdateRequest data) {
        return memories.update(auth, id, data);
    }
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MemoryView updateWithMedia(Authentication auth, @PathVariable @Positive Long id,
                                      @Valid @RequestPart("data") MemoryUpdateRequest data,
                                      @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return memories.update(auth, id, data, files);
    }
    @PostMapping(value = "/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MemoryView addMedia(Authentication auth, @PathVariable @Positive Long id,
                               @RequestPart("files") List<MultipartFile> files) {
        return memories.addMedia(auth, id, files);
    }
    @DeleteMapping("/{id}/media/{mediaId}")
    public MemoryView deleteMedia(Authentication auth, @PathVariable @Positive Long id,
                                  @PathVariable @Positive Long mediaId) {
        return memories.deleteMedia(auth, id, mediaId);
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable @Positive Long id) { memories.delete(auth, id); }
}
