package com.lovespace.api;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.service.DiaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/diaries")
public class DiaryController {
    private final DiaryService diaries;
    public DiaryController(DiaryService diaries) { this.diaries = diaries; }
    @GetMapping public List<DiaryView> list(Authentication auth,
            @RequestParam(required = false) @Positive Long authorId) { return diaries.list(auth, authorId); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public DiaryView create(Authentication auth, @Valid @RequestBody DiaryRequest input) {
        return diaries.create(auth, input);
    }
    @PutMapping("/{id}")
    public DiaryView update(Authentication auth, @PathVariable @Positive Long id,
                            @Valid @RequestBody DiaryUpdateRequest input) { return diaries.update(auth, id, input); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable @Positive Long id) { diaries.delete(auth, id); }
}
