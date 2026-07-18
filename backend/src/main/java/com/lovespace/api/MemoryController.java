package com.lovespace.api;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.service.MemoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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
    @GetMapping
    public PageResponse<MemoryView> list(Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") @jakarta.validation.constraints.Size(max = 200) String q,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "") String type) {
        return memories.list(auth, page, size, q, year, type);
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
    @PutMapping("/{id}")
    public MemoryView update(Authentication auth, @PathVariable @Positive Long id,
                             @Valid @RequestBody MemoryRequest data) {
        return memories.update(auth, id, data);
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable @Positive Long id) { memories.delete(auth, id); }
}
