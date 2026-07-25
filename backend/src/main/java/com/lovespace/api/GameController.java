package com.lovespace.api;

import static com.lovespace.api.dto.ApiDtos.*;

import com.lovespace.service.GameService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/games")
public class GameController {
    private final GameService games;
    public GameController(GameService games) { this.games = games; }

    @GetMapping
    public List<GameSessionView> list(Authentication auth) { return games.list(auth); }

    @GetMapping("/{id}")
    public GameSessionView get(Authentication auth, @PathVariable @Positive Long id) {
        return games.get(auth, id);
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public GameSessionView create(Authentication auth, @Valid @RequestBody GameCreateRequest input) {
        return games.create(auth, input);
    }

    @PostMapping("/{id}/answer")
    public GameSessionView answer(Authentication auth, @PathVariable @Positive Long id,
                                  @Valid @RequestBody GameAnswerRequest input) {
        return games.answer(auth, id, input);
    }

    @PostMapping("/{id}/strokes")
    public GameSessionView addStrokes(Authentication auth, @PathVariable @Positive Long id,
                                      @Valid @RequestBody GameStrokeBatchRequest input) {
        return games.addStrokes(auth, id, input);
    }

    @DeleteMapping("/{id}/canvas")
    public GameSessionView clearCanvas(Authentication auth, @PathVariable @Positive Long id) {
        return games.clearCanvas(auth, id);
    }

    @PostMapping("/{id}/guess")
    public GameSessionView guess(Authentication auth, @PathVariable @Positive Long id,
                                 @Valid @RequestBody GameGuessRequest input) {
        return games.guess(auth, id, input);
    }

    @PostMapping("/{id}/next")
    public GameSessionView nextRound(Authentication auth, @PathVariable @Positive Long id) {
        return games.nextRound(auth, id);
    }

    @PatchMapping("/{id}/finish")
    public GameSessionView finish(Authentication auth, @PathVariable @Positive Long id) {
        return games.finish(auth, id);
    }
}
