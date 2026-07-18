package com.lovespace.api;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.api.error.ApiException;
import com.lovespace.service.MessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageService messages;
    public MessageController(MessageService messages) { this.messages = messages; }
    @GetMapping
    public PageResponse<MessageView> list(Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (page < 0 || size < 1 || size > 100) throw ApiException.badRequest("分页参数无效");
        return messages.list(auth, page, size);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public MessageView create(Authentication auth, @Valid @RequestBody MessageRequest input) {
        return messages.create(auth, input);
    }
    @PatchMapping("/{id}/read")
    public MessageView read(Authentication auth, @PathVariable @Positive Long id) { return messages.markRead(auth, id); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable @Positive Long id) { messages.delete(auth, id); }
}
