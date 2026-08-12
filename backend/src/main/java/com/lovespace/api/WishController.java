package com.lovespace.api;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.service.WishService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/wishes")
public class WishController {
    private final WishService wishes;
    public WishController(WishService wishes) { this.wishes = wishes; }

    @GetMapping
    public List<WishView> list(Authentication auth) { return wishes.list(auth); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public WishView create(Authentication auth, @Valid @RequestBody WishRequest input) {
        return wishes.create(auth, input);
    }

    @PutMapping("/{id}")
    public WishView update(Authentication auth, @PathVariable @Positive Long id,
                           @Valid @RequestBody WishUpdateRequest input) {
        return wishes.update(auth, id, input);
    }

    @PatchMapping("/{id}/complete")
    public WishView complete(Authentication auth, @PathVariable @Positive Long id) {
        return wishes.complete(auth, id);
    }

    @PatchMapping("/{id}/reopen")
    public WishView reopen(Authentication auth, @PathVariable @Positive Long id) {
        return wishes.reopen(auth, id);
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable @Positive Long id) { wishes.delete(auth, id); }
}
