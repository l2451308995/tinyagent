package com.nageoffer.ai.tinyagent.react.multiagent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 一次用户请求内的共享黑板
 * <p>
 * 黑板只保存三类可交接状态：用户原始请求、显式共享约束、已经完成的子 Agent 结果
 * Thought、完整 ReAct 轨迹和整个聊天历史都不进入黑板
 */
public final class AgenticScope {

    /**
     * 注入给下游的摘要上限。摘要只是背景，事实走 facts，所以这里可以卡得很死
     */
    private static final int MAX_PROJECTED_SUMMARY = 300;

    /**
     * 交付范围。它属于编排层而不是各自的人设：同一位专家在不同计划里该交付的东西不一样，
     * 写进人设就等于把编排决策焊死在角色里
     * <p>
     * 后两条都是从真实日志里一条一条补出来的，每补一条都对应模型绕开上一条的一种方式
     * <p>
     * 只写“别答别人的题”，模型答不了的时候会拐个弯，用一句“当前资料不足，建议联系客服确认”
     * 兜底——那也是结论，而且会和下游真正查到证据的专家结论直接打架
     * 堵掉兜底之后它又换了一种绕法，改写“该部分由 IoT 专家另行验证”，
     * 这回内容是对的，但终稿是给用户看的，用户不该知道后台派了几个 Agent
     */
    private static final String OUTPUT_SCOPE = """
            【输出边界】
            - 你的回复只是多专家终稿里的一节，只回答上面这一条子任务。
            - 不属于本次子任务的问题由别的专家负责，直接不写：既不要用“资料不足”“建议联系客服确认”这类话兜底，那会和负责这块的专家结论冲突；也不要写“这部分由某某专家另行验证”这类分工说明，终稿直接呈现给用户，用户不需要知道内部怎么分工。
            - 不要复述上游已经答过的内容，不要在结尾给整单的总体建议、方案排序或最终推荐。""";

    /**
     * 行文统一。它和输出边界是同一类东西：单看每一段都没毛病，只有站在终稿的位置上
     * 才看得出来它们互相不一致——一段用二级标题，一段用加粗，一段干脆没标题；
     * 一段称呼“您”，另外两段称呼“你”。拼起来一眼就能看出是几台机器缝的
     * <p>
     * 所以它也只能写在编排层。专家自己不知道终稿里还有别人
     */
    private static final String SECTION_STYLE = """
            【行文统一】
            - 用一个二级标题（## 开头）作为本节标题，标题概括本节内容；正文里不要再出现其他二级标题。
            - 统一用“你”称呼用户，不要用“您”。""";

    private final String userRequest;
    private final Map<String, String> constraints = new LinkedHashMap<>();
    private final Map<String, AgentResult> results = new LinkedHashMap<>();

    public AgenticScope(String userRequest) {
        this.userRequest = userRequest == null ? "" : userRequest.strip();
    }

    public String userRequest() {
        return userRequest;
    }

    public synchronized void putConstraint(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        constraints.put(key.strip(), value.strip());
    }

    public synchronized void putConstraints(Map<String, String> values) {
        if (values != null) {
            values.forEach(this::putConstraint);
        }
    }

    public synchronized Map<String, String> constraints() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(constraints));
    }

    public synchronized void publish(AgentResult result) {
        if (result == null) {
            throw new IllegalArgumentException("Agent 结果不能为空");
        }
        if (results.putIfAbsent(result.agentName(), result) != null) {
            throw new IllegalStateException(
                    "同一次请求不能重复发布同名 Agent 结果：" + result.agentName());
        }
    }

    public synchronized Optional<AgentResult> resultOf(String agentName) {
        return Optional.ofNullable(results.get(agentName));
    }

    public synchronized List<AgentResult> results() {
        return List.copyOf(results.values());
    }

    /**
     * 给当前 Agent 只注入用户请求、共享约束和显式声明的依赖结果，不给上游摘要
     *
     * @see #briefingFor(String, Set, boolean)
     */
    public synchronized String briefingFor(
            String task,
            Set<String> dependencies) {
        return briefingFor(task, dependencies, false);
    }

    /**
     * 给当前 Agent 只注入用户请求、共享约束和显式声明的依赖结果
     * <p>
     * 四个刻意的编排选择：
     * <ol>
     *     <li>已确认事实和领域摘要分成两块，并明确告诉模型只有前者是证据
     *         混在一起写，模型会把上游那段客服话术也当成可引用的结论。</li>
     *     <li><b>上游摘要默认不投</b>。摘要是一份写完整的、面向用户的答复，
     *         下游看见它就会照着同样的口径和篇幅重写一遍整单结论，
     *         连自己没有工具证据的部分也一起写——事实交接精心防住的散文转述，
     *         会原样从这个口子漏回去。一句“不是证据，不要复述”挡不住它，
     *         唯一可靠的办法是不放进上下文。真需要背景时才显式打开。</li>
     *     <li>摘要按 {@link #MAX_PROJECTED_SUMMARY} 截断。即便打开也只是背景，
     *         不截断的话每多一级依赖，下游上下文就多一整段长文。</li>
     *     <li>本次子任务、输出边界和行文统一一起压在最后。模型对末尾内容更敏感，
     *         把当前要干的事、交付范围和排版口径放在背景材料后面，能减少跑题。</li>
     * </ol>
     *
     * @param projectUpstreamSummary 是否把上游的自然语言结论也投给下游
     */
    public synchronized String briefingFor(
            String task,
            Set<String> dependencies,
            boolean projectUpstreamSummary) {
        StringBuilder briefing = new StringBuilder();
        briefing.append("【用户原始请求】\n").append(userRequest);

        if (!constraints.isEmpty()) {
            briefing.append("\n\n【共享约束】\n");
            constraints.forEach((key, value) ->
                    briefing.append("- ").append(key).append("：")
                            .append(value).append('\n'));
        }

        if (dependencies != null && !dependencies.isEmpty()) {
            List<AgentResult> upstream = new ArrayList<>();
            for (String dependency : dependencies) {
                AgentResult result = results.get(dependency);
                if (result == null) {
                    throw new IllegalStateException(
                            "缺少依赖结果：" + dependency);
                }
                if (!result.completed()) {
                    throw new IllegalStateException(
                            "依赖结果未完成：" + dependency);
                }
                upstream.add(result);
            }
            appendFacts(briefing, upstream, projectUpstreamSummary);
            if (projectUpstreamSummary) {
                appendSummaries(briefing, upstream);
            }
        }

        return briefing.toString().stripTrailing()
                + "\n\n【本次子任务】\n"
                + (task == null ? "" : task.strip())
                + "\n\n" + OUTPUT_SCOPE
                + "\n\n" + SECTION_STYLE;
    }

    private void appendFacts(
            StringBuilder briefing,
            List<AgentResult> upstream,
            boolean projectUpstreamSummary) {
        boolean hasFacts = upstream.stream().anyMatch(it -> !it.facts().isEmpty());
        if (!hasFacts) {
            briefing.append("\n【上游已确认事实】\n")
                    .append("- 无。上游本轮没有产出可交接的工具字段，")
                    .append(projectUpstreamSummary
                            ? "你需要自己调用工具取证，不要照搬下面的摘要。\n"
                            : "你需要自己调用工具取证。\n");
            return;
        }

        briefing.append("\n【上游已确认事实】")
                .append("（来自上游工具返回，可直接引用，不要重复查询）\n");
        for (AgentResult result : upstream) {
            result.facts().forEach((key, value) ->
                    briefing.append("- ").append(result.agentName()).append('.')
                            .append(key).append("：").append(value).append('\n'));
        }
    }

    private void appendSummaries(StringBuilder briefing, List<AgentResult> upstream) {
        briefing.append("\n【上游领域摘要】")
                .append("（仅供理解背景，不是事实证据，不要直接复述给用户）\n");
        for (AgentResult result : upstream) {
            briefing.append("- ").append(result.agentName()).append("：")
                    .append(clip(result.summary())).append('\n');
        }
    }

    private String clip(String summary) {
        String text = summary.replaceAll("\\s+", " ").strip();
        if (text.length() <= MAX_PROJECTED_SUMMARY) {
            return text;
        }
        return text.substring(0, MAX_PROJECTED_SUMMARY) + "……（已截断，完整结论只用于终稿）";
    }
}
