package com.lovespace.api;

import com.lovespace.api.dto.ApiDtos.CalendarEntryView;
import com.lovespace.api.dto.ApiDtos.CalendarEventRequest;
import com.lovespace.service.CalendarService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/calendar")
public class CalendarController {
    private final CalendarService calendar;

    public CalendarController(CalendarService calendar) {
        this.calendar = calendar;
    }

    @GetMapping
    public List<CalendarEntryView> list(Authentication auth,
                                        @RequestParam LocalDate from,
                                        @RequestParam LocalDate to) {
        return calendar.list(auth, from, to);
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarEntryView create(Authentication auth, @Valid @RequestBody CalendarEventRequest input) {
        return calendar.create(auth, input);
    }

    @PutMapping("/events/{id}")
    public CalendarEntryView update(Authentication auth, @PathVariable @Positive Long id,
                                    @Valid @RequestBody CalendarEventRequest input) {
        return calendar.update(auth, id, input);
    }

    @DeleteMapping("/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable @Positive Long id) {
        calendar.delete(auth, id);
    }
}
