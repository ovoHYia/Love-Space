package com.lovespace.api;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.service.AnniversaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/anniversaries")
public class AnniversaryController {
    private final AnniversaryService anniversaries;
    public AnniversaryController(AnniversaryService anniversaries) { this.anniversaries = anniversaries; }
    @GetMapping public List<AnniversaryView> list(Authentication auth) { return anniversaries.list(auth); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AnniversaryView create(Authentication auth, @Valid @RequestBody AnniversaryRequest input) {
        return anniversaries.create(auth, input);
    }
    @PutMapping("/{id}")
    public AnniversaryView update(Authentication auth, @PathVariable @Positive Long id,
                                  @Valid @RequestBody AnniversaryRequest input) {
        return anniversaries.update(auth, id, input);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable @Positive Long id) { anniversaries.delete(auth, id); }
}
