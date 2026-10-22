package com.resume.resumeio.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume.resumeio.concurrency.SessionLockManager;
import com.resume.resumeio.dto.*;
import com.resume.resumeio.entity.InterviewQuestionEntity;
import com.resume.resumeio.entity.InterviewSessionEntity;
import com.resume.resumeio.exception.ConcurrentOperationException;
import com.resume.resumeio.repository.InterviewQuestionRepository;
import com.resume.resumeio.repository.InterviewSessionRepository;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模拟面试业务服务。
 * <p>
 * 支持长对话：首题与后续题目均由 AI 根据简历与历史问答动态生成；
 * 单题合格后才进入下一题，用户可随时主动结束面试。
 */
@Service
public class InterviewService {

    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);

    /** 流式评价末尾元数据前缀，供前端解析是否合格 */
    private static final String EVAL_META_PREFIX = "__EVAL__:";
    /** 解析 AI 输出中的合格/不合格标记 */
    private static final Pattern QUALIFIED_TAG = Pattern.compile("\\[合格\\]");
    private static final Pattern UNQUALIFIED_TAG = Pattern.compile("\\[不合格\\]");
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\d+[.、)\\]\\s]+");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 首题 Prompt 里携带的简历最大字符数，过长的简历会截断以控制 token 成本 */
    private static final int MAX_FIRST_QUESTION_RESUME_CHARS = 3000;

    /** 首题切入点候选：每次随机取一个，避免同一份简历反复面试问同一处 */
    private static final List<String> OPENING_ANGLES = List.of(
            "最具代表性的项目及其技术难点与方案取舍",
            "某项技术的深度细节（并发、存储、性能优化、稳定性之一）",
            "简历中的量化成果，反推是怎么做到的",
            "工程化实践（测试、部署、监控、CI/CD）",
            "一次踩坑或失败经历及其复盘"
    );

    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final SessionLockManager sessionLockManager;
    private final ChatService chatService;
    private final RagKnowledgeService ragKnowledgeService;
    private final ReportStoreService reportStoreService;

    public InterviewService(
            InterviewSessionRepository sessionRepository,
            InterviewQuestionRepository questionRepository,
            SessionLockManager sessionLockManager,
            ChatService chatService,
            RagKnowledgeService ragKnowledgeService,
            ReportStoreService reportStoreService
    ) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.sessionLockManager = sessionLockManager;
        this.chatService = chatService;
        this.ragKnowledgeService = ragKnowledgeService;
        this.reportStoreService = reportStoreService;
    }

    /**
     * 开始长对话模拟面试：入库简历向量、创建会话、由 AI 生成首题。
     */
    @Transactional
    public StartInterviewResponse start(StartInterviewRequest request, long userId) {
        String resumeText = reportStoreService.getParsedTextByReportId(request.getResumeId(), userId);
        if (resumeText != null && !resumeText.isBlank()) {
            ragKnowledgeService.indexResume(userId, request.getResumeId(), resumeText);
        }

        InterviewSessionEntity session = new InterviewSessionEntity();
        session.setUserId(userId);
        session.setReportId(request.getResumeId());
        session.setStatus("in_progress");
        session.setCurrentIndex(0);
        session = sessionRepository.save(session);

        InterviewQuestionEntity firstQuestion = createAiQuestion(
                session.getId(), request.getResumeId(), userId, List.of(), true);
        firstQuestion = questionRepository.save(firstQuestion);

        InterviewSessionDto sessionDto = new InterviewSessionDto();
        sessionDto.setId(session.getId());
        sessionDto.setUserId(userId);
        sessionDto.setResumeId(request.getResumeId());
        sessionDto.setStatus(session.getStatus());

        StartInterviewResponse response = new StartInterviewResponse();
        response.setSession(sessionDto);
        response.setFirstQuestion(toQuestionDto(firstQuestion));
        return response;
    }

    @Transactional
    public SubmitAnswerResponse submit(SubmitAnswerRequest request, long userId) {
        ReentrantLock lock = sessionLockManager.lockFor(request.getSessionId());
        lock.lock();
        try {
            return doSubmit(request, userId);
        } catch (OptimisticLockException e) {
            throw new ConcurrentOperationException("面试进度已被其他请求更新，请刷新后重试");
        } finally {
            lock.unlock();
        }
    }

    /**
     * 主动结束长对话面试。
     */
    @Transactional
    public InterviewSessionDto endInterview(long sessionId, long userId) {
        InterviewSessionEntity session = loadOwnedSession(sessionId, userId);
        if ("completed".equals(session.getStatus())) {
            return toSessionDto(session);
        }
        session.setStatus("completed");
        session = sessionRepository.save(session);
        sessionLockManager.release(session.getId());
        return toSessionDto(session);
    }

    public Flux<String> streamFeedback(InterviewStreamRequest request, long userId) {
        InterviewSessionEntity session = loadOwnedSession(request.getSessionId(), userId);
        List<InterviewQuestionEntity> questions = questionRepository.findBySessionIdOrderByQuestionOrderAsc(session.getId());

        InterviewQuestionEntity question = questions.stream()
                .filter(q -> q.getId().equals(request.getQuestionId()))
                .findFirst()
                .orElse(null);

        String questionText = question != null ? question.getContent() : "未知题目";
        long reportId = session.getReportId();
        String history = buildConversationHistory(questions);

        String instruction = """
                你是一位严格但专业的技术面试官。请评价候选人对当前题目的回答是否达到面试合格线。

                【输出格式 — 必须严格遵守】
                第一行只能是以下标记之一（不要加任何前缀或后缀）：
                [合格]
                或
                [不合格]

                然后空一行，按以下结构输出（总字数 250 字以内）：
                ## 评价
                （说明回答是否切题、结构是否清晰、技术/经历描述是否具体）
                ## 改进建议
                （不合格时：给出 2～3 条可操作的改进方向，便于候选人重新作答；
                 合格时：简要肯定亮点，并提 1 条可选提升点）

                判定标准：
                - 合格：基本切题，能说明关键经历或观点，表达基本清晰
                - 不合格：偏题、过于简略（如仅一两句）、空洞无实例、明显未理解题意

                请结合候选人简历（见参考资料）与已有对话上下文进行评价。

                【已有问答记录】
                %s

                【当前题目】
                %s

                【候选人本轮回答】
                %s
                """.formatted(
                history.isBlank() ? "（暂无，这是第一题）" : history,
                questionText,
                request.getAnswer()
        );

        String ragQuery = questionText + " " + request.getAnswer() + " " + history;
        StringBuilder fullText = new StringBuilder();

        return chatService.streamChatWithResumeContext(ragQuery, userId, reportId, instruction)
                .doOnNext(fullText::append)
                .concatWith(Flux.defer(() -> {
                    boolean qualified = parseQualified(fullText.toString());
                    try {
                        String meta = OBJECT_MAPPER.writeValueAsString(Map.of("qualified", qualified));
                        return Flux.just(EVAL_META_PREFIX + meta);
                    } catch (JsonProcessingException e) {
                        return Flux.just(EVAL_META_PREFIX + "{\"qualified\":false}");
                    }
                }));
    }

    /**
     * 合格时保存本轮回答并由 AI 生成下一题；不合格时不推进。
     */
    private SubmitAnswerResponse doSubmit(SubmitAnswerRequest request, long userId) {
        InterviewSessionEntity session = loadOwnedSession(request.getSessionId(), userId);
        if ("completed".equals(session.getStatus())) {
            throw new IllegalArgumentException("面试已结束");
        }

        List<InterviewQuestionEntity> questions = questionRepository.findBySessionIdOrderByQuestionOrderAsc(session.getId());
        if (questions.isEmpty()) {
            throw new IllegalStateException("面试题目不存在");
        }

        InterviewQuestionEntity currentQuestion = questions.get(session.getCurrentIndex());
        if (!currentQuestion.getId().equals(request.getQuestionId())) {
            throw new IllegalArgumentException("请回答当前题目");
        }

        MessageDto message = new MessageDto();
        message.setSessionId(request.getSessionId());
        message.setRole("user");
        message.setContent(request.getAnswer());

        SubmitAnswerResponse response = new SubmitAnswerResponse();
        response.setMessage(message);
        response.setQualified(Boolean.TRUE.equals(request.getQualified()));
        response.setInterviewCompleted(false);

        if (!Boolean.TRUE.equals(request.getQualified())) {
            return response;
        }

        currentQuestion.setUserAnswer(request.getAnswer());
        currentQuestion.setPassed(true);
        questionRepository.save(currentQuestion);

        int nextIndex = session.getCurrentIndex() + 1;
        InterviewQuestionEntity nextQuestion = createAiQuestion(
                session.getId(), session.getReportId(), userId, questions, false);
        nextQuestion = questionRepository.save(nextQuestion);

        session.setCurrentIndex(nextIndex);
        sessionRepository.save(session);

        response.setNextQuestion(toQuestionDto(nextQuestion));
        return response;
    }

    /** 从 AI 评价全文解析是否合格：优先匹配先出现的 [合格]/[不合格] 标记。 */
    public static boolean parseQualified(String evaluationText) {
        if (evaluationText == null || evaluationText.isBlank()) {
            return false;
        }
        Matcher qualifiedMatcher = QUALIFIED_TAG.matcher(evaluationText);
        Matcher unqualifiedMatcher = UNQUALIFIED_TAG.matcher(evaluationText);
        boolean hasQualified = qualifiedMatcher.find();
        boolean hasUnqualified = unqualifiedMatcher.find();
        if (hasQualified && hasUnqualified) {
            return qualifiedMatcher.start() < unqualifiedMatcher.start();
        }
        return hasQualified;
    }

    private InterviewSessionEntity loadOwnedSession(long sessionId, long userId) {
        InterviewSessionEntity session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("面试会话不存在"));
        if (!session.getUserId().equals(userId)) {
            throw new SecurityException("无权访问该面试会话");
        }
        return session;
    }

    private InterviewQuestionEntity createAiQuestion(
            long sessionId,
            long reportId,
            long userId,
            List<InterviewQuestionEntity> existingQuestions,
            boolean firstQuestion
    ) {
        String content = firstQuestion
                ? generateFirstQuestion(reportId, userId)
                : generateNextQuestion(reportId, userId, existingQuestions);

        InterviewQuestionEntity question = new InterviewQuestionEntity();
        question.setSessionId(sessionId);
        question.setContent(content);
        question.setCategory(firstQuestion ? "开场" : "追问");
        question.setDifficulty(firstQuestion ? "easy" : "medium");
        question.setQuestionOrder(existingQuestions.size() + 1);
        return question;
    }

    /**
     * 生成第一道面试题。
     * <p>
     * 与后续题目不同，首题**不依赖 RAG 检索**，而是把简历全文直接放进 Prompt。
     * 原因：首题的检索 query 是固定语义，配合本地哈希向量会稳定命中同一批片段，
     * 导致「每次开面试都是同一道题」；简历本身只有几 KB，整段喂给模型既更准，
     * 也保证不同简历必然得到不同问题。
     * <p>
     * 同时每次随机选一个切入点（项目难点 / 技术深度 / 量化成果 / 工程化 / 踩坑复盘），
     * 让同一份简历多次面试也不会重复。
     */
    private String generateFirstQuestion(long reportId, long userId) {
        String resumeText = reportStoreService.getParsedTextByReportId(reportId, userId);
        String resumeSnippet = resumeText == null ? "" : resumeText.strip();
        if (resumeSnippet.length() > MAX_FIRST_QUESTION_RESUME_CHARS) {
            resumeSnippet = resumeSnippet.substring(0, MAX_FIRST_QUESTION_RESUME_CHARS);
        }
        if (resumeSnippet.isBlank()) {
            // 简历文本为空时退回 RAG 路径，避免 Prompt 里没有素材
            String fallbackInstruction = """
                    你是资深技术面试官。请根据候选人简历（见参考资料）提出第一道面试问题。
                    要求：结合简历中的项目、技能或经历切入，避免通用套话；只输出一个问题本身。
                    """;
            return generateQuestionText("首题 简历 项目 技能", reportId, userId, fallbackInstruction, 0);
        }

        // 随机切入点：避免同一份简历反复面试时问同一处
        String angle = OPENING_ANGLES.get(ThreadLocalRandom.current().nextInt(OPENING_ANGLES.size()));
        String instruction = """
                你是资深技术面试官。下面是候选人的简历全文：
                【简历全文】
                %s

                本次请从「%s」这个角度切入，提出第一道面试问题。
                要求：
                - 必须结合简历中的具体项目名、技术选型或细节，避免通用套话
                - 不要与常见的“请先做个自我介绍”雷同
                - 只输出一个问题本身，不要编号、不要解释、不要多个问题
                """.formatted(resumeSnippet, angle);
        return generateQuestionText("面试开场 " + angle, reportId, userId, instruction, 0);
    }

    private String generateNextQuestion(long reportId, long userId, List<InterviewQuestionEntity> questions) {
        String history = buildConversationHistory(questions);
        String instruction = """
                你是资深技术面试官。请根据候选人简历与【已有问答记录】提出下一道面试问题。
                要求：
                - 在长对话中递进深入，可追问上一答细节，或切换新的考察维度
                - 避免与已问过的问题重复或仅换表述
                - 只输出一个问题本身，不要编号、不要解释、不要多个问题

                【已有问答记录】
                """ + (history.isBlank() ? "（暂无）" : history);
        return generateQuestionText("下一题 追问 " + questions.size(), reportId, userId, instruction, questions.size());
    }

    private String generateQuestionText(
            String ragQuery,
            long reportId,
            long userId,
            String instruction,
            int fallbackIndex
    ) {
        try {
            String raw = chatService.chatWithResumeContext(ragQuery, userId, reportId, instruction);
            String cleaned = cleanQuestionText(raw);
            if (!cleaned.isBlank()) {
                return cleaned;
            }
        } catch (Exception e) {
            log.warn("AI 生成面试题失败，使用兜底题目: {}", e.getMessage());
        }
        return fallbackQuestion(fallbackIndex);
    }

    private String buildConversationHistory(List<InterviewQuestionEntity> questions) {
        StringBuilder history = new StringBuilder();
        for (InterviewQuestionEntity q : questions) {
            if (Boolean.TRUE.equals(q.getPassed()) && q.getUserAnswer() != null && !q.getUserAnswer().isBlank()) {
                history.append("面试官：").append(q.getContent()).append('\n');
                history.append("候选人：").append(q.getUserAnswer()).append("\n\n");
            }
        }
        return history.toString().trim();
    }

    private String cleanQuestionText(String raw) {
        if (raw == null) {
            return "";
        }
        String line = raw.lines()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .findFirst()
                .orElse(raw.trim());
        line = line.replaceAll("^[\"'「『]|[\"'」』]$", "");
        Matcher matcher = LEADING_NUMBER.matcher(line);
        if (matcher.find()) {
            line = line.substring(matcher.end()).trim();
        }
        return line;
    }

    private String fallbackQuestion(int index) {
        if (index == 0) {
            return "请结合你的简历，简单介绍一下你自己以及你最引以为豪的一段项目经历。";
        }
        return "请继续分享一个能体现你技术深度的项目细节，包括你的职责、关键挑战与解决思路。";
    }

    private InterviewSessionDto toSessionDto(InterviewSessionEntity session) {
        InterviewSessionDto dto = new InterviewSessionDto();
        dto.setId(session.getId());
        dto.setUserId(session.getUserId());
        dto.setResumeId(session.getReportId());
        dto.setStatus(session.getStatus());
        return dto;
    }

    private QuestionDto toQuestionDto(InterviewQuestionEntity entity) {
        QuestionDto dto = new QuestionDto();
        dto.setId(entity.getId());
        dto.setSessionId(entity.getSessionId());
        dto.setContent(entity.getContent());
        dto.setCategory(entity.getCategory());
        dto.setDifficulty(entity.getDifficulty());
        dto.setOrder(entity.getQuestionOrder());
        return dto;
    }
}
