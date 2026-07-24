package com.lovespace.api;

import com.lovespace.api.dto.ApiDtos.TrashItemView;
import com.lovespace.service.TrashService;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/trash")
public class TrashController {
    private final TrashService trash;

    public TrashController(TrashService trash) {
        this.trash = trash;
    }

    @GetMapping
    public List<TrashItemView> list(Authentication auth) {
        return trash.list(auth);
    }

    @PostMapping("/{type}/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(Authentication auth, @PathVariable String type, @PathVariable @Positive Long id) {
        trash.restore(auth, type, id);
    }

    @DeleteMapping("/{type}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purge(Authentication auth, @PathVariable String type, @PathVariable @Positive Long id) {
        trash.purge(auth, type, id);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void empty(Authentication auth) {
        trash.empty(auth);
    }
}
