package com.lovespace.service;

import static com.lovespace.api.dto.ApiDtos.*;

import com.lovespace.api.error.ApiException;
import com.lovespace.domain.GameSession;
import com.lovespace.domain.User;
import com.lovespace.repository.CoupleRepository;
import com.lovespace.repository.GameSessionRepository;
import com.lovespace.security.CurrentUserService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class GameService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_STROKES = 500;
    private static final int MAX_POINTS = 20_000;
    private static final int MAX_STROKE_OPERATION_IDS = 100;
    private static final List<Question> QUESTIONS = List.of(
            new Question("周末最想和对方一起做什么？", List.of("宅家看电影", "去吃好吃的", "户外散步", "随性出发")),
            new Question("如果突然多出三天假期，最想怎么过？", List.of("去旅行", "回家休息", "逛吃逛吃", "一起学新东西")),
            new Question("对方最需要安慰时，你会怎么做？", List.of("抱抱 TA", "认真倾听", "带 TA 吃好吃的", "陪着但不打扰")),
            new Question("最适合你们的约会氛围是？", List.of("热闹有趣", "安静浪漫", "自然户外", "在家温馨")),
            new Question("一起生活最幸福的小事是？", List.of("一起吃饭", "睡前聊天", "分享日常", "计划未来")),
            new Question("下一次纪念日更想收到什么？", List.of("一封信", "一件礼物", "一次旅行", "认真陪伴")),
            new Question("理想的周末早餐是什么？", List.of("中式早点", "咖啡面包", "丰盛早午餐", "睡到自然醒")),
            new Question("两个人旅行时更喜欢哪种节奏？", List.of("计划满满", "随走随停", "悠闲度假", "深度探索")),
            new Question("突然得到一笔奖金，最想先做什么？", List.of("存起来", "买礼物", "安排旅行", "吃顿大餐")),
            new Question("对方加班很晚时，你最想怎么陪伴？", List.of("准备夜宵", "安静等候", "发消息打气", "帮忙分担家务")),
            new Question("你们更喜欢哪一种电影？", List.of("轻松喜剧", "悬疑推理", "浪漫爱情", "科幻冒险")),
            new Question("下雨天最适合一起做什么？", List.of("窝着追剧", "撑伞散步", "一起做饭", "听歌聊天")),
            new Question("家里最想拥有哪一个角落？", List.of("影音区", "阅读角", "大厨房", "阳台花园")),
            new Question("如果一起养宠物，更想养什么？", List.of("猫咪", "狗狗", "小兔子", "暂时不养")),
            new Question("吵架后更希望怎样和好？", List.of("先抱一下", "认真聊清楚", "写下想法", "冷静后再谈")),
            new Question("最想和对方一起学会什么？", List.of("做一道菜", "一种乐器", "一项运动", "一门语言")),
            new Question("临时决定约会，第一站会去哪里？", List.of("餐厅", "电影院", "公园", "商场")),
            new Question("最喜欢对方怎样表达爱？", List.of("直接说出来", "准备小惊喜", "主动做事情", "留出陪伴时间")),
            new Question("忙碌的一周结束后，最想怎么放松？", List.of("好好睡觉", "吃顿好的", "出门走走", "一起玩游戏")),
            new Question("两个人点外卖时最容易选什么？", List.of("火锅烧烤", "米饭套餐", "面食小吃", "轻食甜品")),
            new Question("如果重拍一张合照，会选什么场景？", List.of("海边日落", "城市夜景", "山野草地", "温馨家里")),
            new Question("最想一起挑战哪一种体验？", List.of("高空项目", "长途自驾", "露营看星", "潜水冲浪")),
            new Question("对方心情低落时，哪句话最有用？", List.of("我一直在", "慢慢来", "你已经很棒", "我们一起解决")),
            new Question("未来的家更偏爱什么风格？", List.of("温暖原木", "简约现代", "复古浪漫", "清新自然")),
            new Question("最希望保留哪一种共同习惯？", List.of("每天拥抱", "一起吃饭", "分享见闻", "定期约会")),
            new Question("节日更喜欢怎样度过？", List.of("精心庆祝", "简单吃饭", "短途旅行", "和平常一样")),
            new Question("一起做家务时更适合哪种分工？", List.of("各做擅长的", "轮流负责", "一起完成", "谁有空谁做")),
            new Question("睡前最想和对方聊什么？", List.of("今天发生的事", "未来计划", "有趣见闻", "什么都聊")),
            new Question("收到哪一种小惊喜会最开心？", List.of("喜欢的零食", "手写便签", "一束花", "突然的拥抱")),
            new Question("如果今天不用工作，最想几点起床？", List.of("早起看日出", "八九点起", "睡到中午", "自然醒就好")),
            new Question("两个人散步时通常会聊什么？", List.of("最近心情", "生活琐事", "未来计划", "路上见闻")),
            new Question("最适合你们的合照风格是？", List.of("自然抓拍", "搞怪有趣", "浪漫氛围", "正式精致")),
            new Question("长途路上更离不开什么？", List.of("音乐歌单", "零食饮料", "聊天陪伴", "舒服睡觉")),
            new Question("最想把哪一天重复一次？", List.of("第一次见面", "确定关系", "一次旅行", "普通但幸福的一天")),
            new Question("被对方夸奖时最想听到什么？", List.of("你好可爱", "你很可靠", "你最懂我", "有你真好")),
            new Question("共同完成一件事后会怎么庆祝？", List.of("吃顿大餐", "拍照纪念", "买个礼物", "好好休息"))
    );
    private static final List<String> DRAW_WORDS = List.of(
            "奶茶", "玫瑰", "摩天轮", "小猫", "旅行箱", "蛋糕",
            "雨伞", "星星", "相机", "火锅", "海边", "爱心",
            "太阳", "月亮", "彩虹", "云朵", "雪人", "圣诞树",
            "礼物盒", "气球", "蜡烛", "冰淇淋", "汉堡", "披萨",
            "西瓜", "草莓", "咖啡", "棒棒糖", "煎蛋", "面包",
            "小狗", "兔子", "熊猫", "企鹅", "长颈鹿", "大象",
            "海豚", "蝴蝶", "乌龟", "金鱼", "鲸鱼", "螃蟹",
            "汽车", "自行车", "火车", "飞机", "轮船", "火箭",
            "红绿灯", "路灯", "帐篷", "城堡", "房子", "学校",
            "书包", "眼镜", "手表", "耳机", "吉他", "钢琴",
            "足球", "篮球", "奖杯", "皇冠", "钥匙", "手机",
            "电脑", "台灯", "沙发", "拖鞋", "牙刷", "花瓶",
            "大树", "向日葵", "椰子树", "高山", "小桥", "灯塔",
            "热气球", "望远镜", "机器人", "风筝", "滑板", "游泳圈"
    );

    private final GameSessionRepository games;
    private final CoupleRepository couples;
    private final CurrentUserService current;
    private final ObjectMapper objectMapper;

    public GameService(GameSessionRepository games, CoupleRepository couples,
                       CurrentUserService current, ObjectMapper objectMapper) {
        this.games = games;
        this.couples = couples;
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
        Long coupleId = user.getCouple().getId();
        couples.findByIdForUpdate(coupleId)
                .orElseThrow(() -> ApiException.conflict("情侣空间不存在"));
        Optional<GameSession> active = games.findFirstByCoupleIdAndGameTypeAndStatusOrderByUpdatedAtDesc(
                coupleId, input.gameType(), GameSession.STATUS_ACTIVE);
        if (active.isPresent()) return view(active.get(), user, partner);

        GameSession game = new GameSession();
        game.setCoupleId(coupleId);
        game.setGameType(input.gameType());
        game.setStatus(GameSession.STATUS_ACTIVE);
        game.setCreatedBy(user.getId());
        game.setRoundNumber(1);
        if (GameSession.TYPE_TACIT_QUIZ.equals(input.gameType())) {
            Question question = randomItem(QUESTIONS, null);
            game.setCurrentTurnUserId(null);
            game.setStateJson(write(new StoredGameState(
                    question.prompt(), question.options(), Map.of(), 0,
                    null, List.of(), List.of(), false, List.of())));
        } else {
            game.setCurrentTurnUserId(user.getId());
            game.setStateJson(write(new StoredGameState(
                    null, List.of(), Map.of(), 0,
                    randomItem(DRAW_WORDS, null), List.of(), List.of(), false, List.of())));
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
        if (state.answers().containsKey(user.getId())) {
            throw ApiException.conflict("你已经回答过本题，请等待对方");
        }
        if (state.answers().size() >= 2) throw ApiException.conflict("本轮答案已经揭晓，请进入下一题");
        String answer = input.answer().trim();
        if (!state.options().contains(answer)) throw ApiException.badRequest("请选择题目提供的答案");
        Map<Long, String> answers = new LinkedHashMap<>(state.answers());
        answers.put(user.getId(), answer);
        int score = state.score();
        if (answers.size() == 2 && Objects.equals(answers.get(user.getId()), answers.get(partner.getId()))) score++;
        saveState(game, new StoredGameState(
                state.prompt(), state.options(), answers, score,
                null, List.of(), List.of(), answers.size() == 2, state.appliedStrokeOperationIds()));
        return view(game, user, partner);
    }

    @Transactional
    public GameSessionView addStrokes(Authentication auth, Long id, GameStrokeBatchRequest input) {
        User user = current.user(auth);
        User partner = current.partner(user);
        GameSession game = findLocked(user, id);
        requireActiveType(game, GameSession.TYPE_DRAW_GUESS);
        requireDrawer(game, user);
        requireRound(game, input.roundNumber());
        StoredGameState state = read(game);
        if (state.roundComplete()) throw ApiException.conflict("本轮已经猜中，请进入下一轮");
        if (state.appliedStrokeOperationIds().contains(input.operationId())) {
            return view(game, user, partner);
        }
        List<GameStrokeRequest> strokes = new ArrayList<>(state.strokes());
        strokes.addAll(input.strokes().stream().map(this::normalizeStroke).toList());
        int points = strokes.stream().mapToInt(stroke -> stroke.points().size()).sum();
        if (strokes.size() > MAX_STROKES || points > MAX_POINTS) {
            throw ApiException.badRequest("画布内容过多，请清空后重新绘制");
        }
        List<String> operationIds = new ArrayList<>(state.appliedStrokeOperationIds());
        operationIds.add(input.operationId());
        if (operationIds.size() > MAX_STROKE_OPERATION_IDS) {
            operationIds = new ArrayList<>(operationIds.subList(
                    operationIds.size() - MAX_STROKE_OPERATION_IDS, operationIds.size()));
        }
        saveState(game, new StoredGameState(
                null, List.of(), Map.of(), state.score(),
                state.secretWord(), strokes, state.guesses(), false, operationIds));
        return view(game, user, partner);
    }

    @Transactional
    public GameSessionView clearCanvas(Authentication auth, Long id, int roundNumber) {
        User user = current.user(auth);
        User partner = current.partner(user);
        GameSession game = findLocked(user, id);
        requireActiveType(game, GameSession.TYPE_DRAW_GUESS);
        requireDrawer(game, user);
        requireRound(game, roundNumber);
        StoredGameState state = read(game);
        if (state.roundComplete()) throw ApiException.conflict("本轮已经结束");
        saveState(game, new StoredGameState(
                null, List.of(), Map.of(), state.score(),
                state.secretWord(), List.of(), state.guesses(), false, List.of()));
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
                state.secretWord(), state.strokes(), guesses, correct, state.appliedStrokeOperationIds()));
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
            Question question = randomItem(QUESTIONS, new Question(state.prompt(), state.options()));
            saveState(game, new StoredGameState(
                    question.prompt(), question.options(), Map.of(), state.score(),
                    null, List.of(), List.of(), false, List.of()));
        } else {
            if (!state.roundComplete()) throw ApiException.conflict("猜中后才能进入下一轮");
            Long nextDrawer = Objects.equals(game.getCurrentTurnUserId(), user.getId())
                    ? partner.getId() : user.getId();
            game.setCurrentTurnUserId(nextDrawer);
            saveState(game, new StoredGameState(
                    null, List.of(), Map.of(), state.score(),
                    randomItem(DRAW_WORDS, state.secretWord()), List.of(), List.of(), false, List.of()));
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

    private void requireRound(GameSession game, int roundNumber) {
        if (game.getRoundNumber() != roundNumber) {
            throw ApiException.conflict("画板局次已经变化，请刷新后重试");
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
            List<GameStrokeRequest> strokes = state.strokes() == null
                    ? List.of()
                    : state.strokes().stream().map(this::normalizeStroke).toList();
            return new StoredGameState(
                    state.prompt(),
                    state.options() == null ? List.of() : state.options(),
                    state.answers() == null ? Map.of() : state.answers(),
                    state.score(),
                    state.secretWord(),
                    strokes,
                    state.guesses() == null ? List.of() : state.guesses(),
                    state.roundComplete(),
                    state.appliedStrokeOperationIds() == null ? List.of() : state.appliedStrokeOperationIds());
        } catch (Exception ex) {
            throw new IllegalStateException("无法读取游戏状态", ex);
        }
    }

    private GameStrokeRequest normalizeStroke(GameStrokeRequest stroke) {
        String tool = "ERASE".equals(stroke.tool()) ? "ERASE" : "DRAW";
        return new GameStrokeRequest(tool, stroke.color(), stroke.width(), stroke.points());
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

    private <T> T randomItem(List<T> items, T previous) {
        int previousIndex = previous == null ? -1 : items.indexOf(previous);
        int candidateCount = items.size() - (previousIndex >= 0 ? 1 : 0);
        int index = ThreadLocalRandom.current().nextInt(candidateCount);
        if (previousIndex >= 0 && index >= previousIndex) index++;
        return items.get(index);
    }

    private record Question(String prompt, List<String> options) {}
    public record StoredGuess(Long userId, String text, boolean correct, LocalDateTime createdAt) {}
    public record StoredGameState(
            String prompt, List<String> options, Map<Long, String> answers, int score,
            String secretWord, List<GameStrokeRequest> strokes,
            List<StoredGuess> guesses, boolean roundComplete,
            List<String> appliedStrokeOperationIds) {}
}
