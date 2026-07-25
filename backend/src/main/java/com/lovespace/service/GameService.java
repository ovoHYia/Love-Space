package com.lovespace.service;

import static com.lovespace.api.dto.ApiDtos.*;

import com.lovespace.api.error.ApiException;
import com.lovespace.domain.GameSession;
import com.lovespace.domain.User;
import com.lovespace.repository.GameSessionRepository;
import com.lovespace.security.CurrentUserService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class GameService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_STROKES = 500;
    private static final int MAX_POINTS = 20_000;
    private static final List<Question> QUESTIONS = List.of(
            new Question("周末最想和对方一起做什么？", List.of("宅家看电影", "去吃好吃的", "户外散步", "随性出发")),
            new Question("如果突然多出三天假期，最想怎么过？", List.of("去旅行", "回家休息", "逛吃逛吃", "一起学新东西")),
            new Question("对方最需要安慰时，你会怎么做？", List.of("抱抱 TA", "认真倾听", "带 TA 吃好吃的", "陪着但不打扰")),
            new Question("最适合你们的约会氛围是？", List.of("热闹有趣", "安静浪漫", "自然户外", "在家温馨")),
            new Question("一起生活最幸福的小事是？", List.of("一起吃饭", "睡前聊天", "分享日常", "计划未来")),
            new Question("下一次纪念日更想收到什么？", List.of("一封信", "一件礼物", "一次旅行", "认真陪伴"))
    );
    private static final List<String> DRAW_WORDS = List.of(
            "奶茶", "玫瑰", "摩天轮", "小猫", "旅行箱", "蛋糕",
            "雨伞", "星星", "相机", "火锅", "海边", "爱心"
    );

    private final GameSessionRepository games;
    private final CurrentUserService current;
    private final ObjectMapper objectMapper;

    public GameService(GameSessionRepository games, CurrentUserService current, ObjectMapper objectMapper) {
        this.games = games;
        this.current = current;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<GameSessionView> list(Authentication auth) {
        User user = current.user(auth);
        User partner = current.partner(user);
        return games.findTop20ByCoupleIdOrderByUpdatedAtDesc(user.getCouple().getId()).stream()
                .map(game -> view(game, user, partner))
                .toList();
    }

    @Transactional(readOnly = true)
    public GameSessionView get(Authentication auth, Long id) {
        User user = current.user(auth);
        User partner = current.partner(user);
        return view(find(user, id), user, partner);
    }

    @Transactional
    public GameSessionView create(Authentication auth, GameCreateRequest input) {
        User user = current.user(auth);
        User partner = current.partner(user);
        Optional<GameSession> active = games.findFirstByCoupleIdAndGameTypeAndStatusOrderByUpdatedAtDesc(
                user.getCouple().getId(), input.gameType(), GameSession.STATUS_ACTIVE);
        if (active.isPresent()) return view(active.get(), user, partner);

        GameSession game = new GameSession();
        game.setCoupleId(user.getCouple().getId());
        game.setGameType(input.gameType());
        game.setStatus(GameSession.STATUS_ACTIVE);
        game.setCreatedBy(user.getId());
        game.setRoundNumber(1);
        if (GameSession.TYPE_TACIT_QUIZ.equals(input.gameType())) {
            Question question = QUESTIONS.get(0);
            game.setCurrentTurnUserId(null);
            game.setStateJson(write(new StoredGameState(
                    question.prompt(), question.options(), Map.of(), 0,
                    null, List.of(), List.of(), false)));
        } else {
            game.setCurrentTurnUserId(user.getId());
            game.setStateJson(write(new StoredGameState(
                    null, List.of(), Map.of(), 0,
                    DRAW_WORDS.get(0), List.of(), List.of(), false)));
        }
        return view(games.save(game), user, partner);
    }

    @Transactional
    public GameSessionView answer(Authentication auth, Long id, GameAnswerRequest input) {
        User user = current.user(auth);
        User partner = current.partner(user);
        GameSession game = findLocked(user, id);
        requireActiveType(game, GameSession.TYPE_TACIT_QUIZ);
        StoredGameState state = read(game);
        if (state.answers().size() >= 2) throw ApiException.conflict("本轮答案已经揭晓，请进入下一题");
        String answer = input.answer().trim();
        if (!state.options().contains(answer)) throw ApiException.badRequest("请选择题目提供的答案");
        Map<Long, String> answers = new LinkedHashMap<>(state.answers());
        answers.put(user.getId(), answer);
        int score = state.score();
        if (answers.size() == 2 && Objects.equals(answers.get(user.getId()), answers.get(partner.getId()))) score++;
        saveState(game, new StoredGameState(
                state.prompt(), state.options(), answers, score,
                null, List.of(), List.of(), answers.size() == 2));
        return view(game, user, partner);
    }

    @Transactional
    public GameSessionView addStrokes(Authentication auth, Long id, GameStrokeBatchRequest input) {
        User user = current.user(auth);
        User partner = current.partner(user);
        GameSession game = findLocked(user, id);
        requireActiveType(game, GameSession.TYPE_DRAW_GUESS);
        requireDrawer(game, user);
        StoredGameState state = read(game);
        if (state.roundComplete()) throw ApiException.conflict("本轮已经猜中，请进入下一轮");
        List<GameStrokeRequest> strokes = new ArrayList<>(state.strokes());
        strokes.addAll(input.strokes());
        int points = strokes.stream().mapToInt(stroke -> stroke.points().size()).sum();
        if (strokes.size() > MAX_STROKES || points > MAX_POINTS) {
            throw ApiException.badRequest("画布内容过多，请清空后重新绘制");
        }
        saveState(game, new StoredGameState(
                null, List.of(), Map.of(), state.score(),
                state.secretWord(), strokes, state.guesses(), false));
        return view(game, user, partner);
    }

    @Transactional
    public GameSessionView clearCanvas(Authentication auth, Long id) {
        User user = current.user(auth);
        User partner = current.partner(user);
        GameSession game = findLocked(user, id);
        requireActiveType(game, GameSession.TYPE_DRAW_GUESS);
        requireDrawer(game, user);
        StoredGameState state = read(game);
        if (state.roundComplete()) throw ApiException.conflict("本轮已经结束");
        saveState(game, new StoredGameState(
                null, List.of(), Map.of(), state.score(),
                state.secretWord(), List.of(), state.guesses(), false));
        return view(game, user, partner);
    }

    @Transactional
    public GameSessionView guess(Authentication auth, Long id, GameGuessRequest input) {
        User user = current.user(auth);
        User partner = current.partner(user);
        GameSession game = findLocked(user, id);
        requireActiveType(game, GameSession.TYPE_DRAW_GUESS);
        if (Objects.equals(game.getCurrentTurnUserId(), user.getId())) {
            throw ApiException.forbidden("作画方不能参与猜题");
        }
        StoredGameState state = read(game);
        if (state.roundComplete()) throw ApiException.conflict("本轮已经猜中，请进入下一轮");
        String guess = input.guess().trim();
        boolean correct = normalized(guess).equals(normalized(state.secretWord()));
        List<StoredGuess> guesses = new ArrayList<>(state.guesses());
        guesses.add(new StoredGuess(user.getId(), guess, correct, LocalDateTime.now(ZONE)));
        if (guesses.size() > 20) guesses = new ArrayList<>(guesses.subList(guesses.size() - 20, guesses.size()));
        saveState(game, new StoredGameState(
                null, List.of(), Map.of(), state.score() + (correct ? 1 : 0),
                state.secretWord(), state.strokes(), guesses, correct));
        return view(game, user, partner);
    }

    @Transactional
    public GameSessionView nextRound(Authentication auth, Long id) {
        User user = current.user(auth);
        User partner = current.partner(user);
        GameSession game = findLocked(user, id);
        requireActive(game);
        StoredGameState state = read(game);
        int nextRound = game.getRoundNumber() + 1;
        game.setRoundNumber(nextRound);
        if (GameSession.TYPE_TACIT_QUIZ.equals(game.getGameType())) {
            if (state.answers().size() < 2) throw ApiException.conflict("双方都回答后才能进入下一题");
            Question question = QUESTIONS.get((nextRound - 1) % QUESTIONS.size());
            saveState(game, new StoredGameState(
                    question.prompt(), question.options(), Map.of(), state.score(),
                    null, List.of(), List.of(), false));
        } else {
            if (!state.roundComplete()) throw ApiException.conflict("猜中后才能进入下一轮");
            Long nextDrawer = Objects.equals(game.getCurrentTurnUserId(), user.getId())
                    ? partner.getId() : user.getId();
            game.setCurrentTurnUserId(nextDrawer);
            saveState(game, new StoredGameState(
                    null, List.of(), Map.of(), state.score(),
                    DRAW_WORDS.get((nextRound - 1) % DRAW_WORDS.size()), List.of(), List.of(), false));
        }
        return view(game, user, partner);
    }

    @Transactional
    public GameSessionView finish(Authentication auth, Long id) {
        User user = current.user(auth);
        User partner = current.partner(user);
        GameSession game = findLocked(user, id);
        if (GameSession.STATUS_ACTIVE.equals(game.getStatus())) {
            game.setStatus(GameSession.STATUS_FINISHED);
            game.setFinishedAt(LocalDateTime.now(ZONE));
            games.save(game);
        }
        return view(game, user, partner);
    }

    private GameSession find(User user, Long id) {
        return games.findByIdAndCoupleId(id, user.getCouple().getId())
                .orElseThrow(() -> ApiException.notFound("游戏不存在"));
    }

    private GameSession findLocked(User user, Long id) {
        return games.findLockedByIdAndCoupleId(id, user.getCouple().getId())
                .orElseThrow(() -> ApiException.notFound("游戏不存在"));
    }

    private void requireActive(GameSession game) {
        if (!GameSession.STATUS_ACTIVE.equals(game.getStatus())) throw ApiException.conflict("游戏已经结束");
    }

    private void requireActiveType(GameSession game, String type) {
        requireActive(game);
        if (!type.equals(game.getGameType())) throw ApiException.badRequest("当前游戏不支持此操作");
    }

    private void requireDrawer(GameSession game, User user) {
        if (!Objects.equals(game.getCurrentTurnUserId(), user.getId())) {
            throw ApiException.forbidden("现在轮到对方作画");
        }
    }

    private void saveState(GameSession game, StoredGameState state) {
        game.setStateJson(write(state));
        games.save(game);
    }

    private GameSessionView view(GameSession game, User user, User partner) {
        StoredGameState state = read(game);
        boolean revealed = state.answers().size() >= 2;
        Boolean matched = revealed
                ? Objects.equals(state.answers().get(user.getId()), state.answers().get(partner.getId()))
                : null;
        String secretWord = Objects.equals(game.getCurrentTurnUserId(), user.getId())
                || state.roundComplete() || GameSession.STATUS_FINISHED.equals(game.getStatus())
                ? state.secretWord() : null;
        Map<Long, String> nicknames = Map.of(user.getId(), user.getNickname(), partner.getId(), partner.getNickname());
        List<GameGuessView> guesses = state.guesses().stream()
                .map(guess -> new GameGuessView(
                        guess.userId(), nicknames.getOrDefault(guess.userId(), "对方"),
                        guess.text(), guess.correct(), guess.createdAt()))
                .toList();
        return new GameSessionView(
                game.getId(), game.getGameType(), game.getStatus(), game.getCreatedBy(),
                Objects.equals(game.getCreatedBy(), user.getId()) ? user.getNickname() : partner.getNickname(),
                game.getRoundNumber(), game.getCurrentTurnUserId(), state.prompt(), state.options(),
                state.answers().get(user.getId()), revealed ? state.answers().get(partner.getId()) : null,
                revealed, matched, state.score(), secretWord, state.strokes(), guesses, state.roundComplete(),
                game.getCreatedAt(), game.getUpdatedAt(), game.getFinishedAt());
    }

    private StoredGameState read(GameSession game) {
        try {
            StoredGameState state = objectMapper.readValue(game.getStateJson(), StoredGameState.class);
            return new StoredGameState(
                    state.prompt(),
                    state.options() == null ? List.of() : state.options(),
                    state.answers() == null ? Map.of() : state.answers(),
                    state.score(),
                    state.secretWord(),
                    state.strokes() == null ? List.of() : state.strokes(),
                    state.guesses() == null ? List.of() : state.guesses(),
                    state.roundComplete());
        } catch (Exception ex) {
            throw new IllegalStateException("无法读取游戏状态", ex);
        }
    }

    private String write(StoredGameState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception ex) {
            throw new IllegalStateException("无法保存游戏状态", ex);
        }
    }

    private String normalized(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private record Question(String prompt, List<String> options) {}
    public record StoredGuess(Long userId, String text, boolean correct, LocalDateTime createdAt) {}
    public record StoredGameState(
            String prompt, List<String> options, Map<Long, String> answers, int score,
            String secretWord, List<GameStrokeRequest> strokes,
            List<StoredGuess> guesses, boolean roundComplete) {}
}
