package com.nageoffer.ai.tinyagent.react.multiagent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 确定性结果聚合器
 * <p>
 * 它按结果通知的顺序读取黑板并拼成终稿，不再调用一次 LLM
 * <p>
 * 这里有一条边界要守住：<b>异常只留给协议违约</b>——通知发错了收件人、同一个 Agent 通知了两次、
 * 通知指向的结果根本不在黑板上，这些都是编排层写错了，必须炸出来
 * 而某个 Agent 没跑成功是完全预期内的业务结果，它必须收敛成一段说清楚缺口的降级终稿
 * 早期版本把两者都写成 throw，结果真实链路上只要有一步没收敛，用户拿到的就是一个堆栈
 */
public final class Aggregator {

    public static final String RECIPIENT = "aggregator";

    /**
     * @param answer           面向用户的终稿，失败时是带缺口说明的降级版本
     * @param completedAgents  成功并被写进终稿的 Agent
     * @param failedAgent      失败的 Agent，没有失败时为 null
     * @param failureReason    失败原因，没有失败时为空串
     */
    public record Aggregation(
            String answer,
            List<String> completedAgents,
            String failedAgent,
            String failureReason) {

        public boolean intact() {
            return failedAgent == null;
        }
    }

    public Aggregation aggregate(
            AgenticScope scope,
            List<AgentMessage> notifications) {
        List<String> completed = new ArrayList<>();
        StringBuilder answer = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        String failedAgent = null;
        String failureReason = "";

        for (AgentMessage notification : notifications == null ? List.<AgentMessage>of() : notifications) {
            String agentName = requireProtocol(notification, seen);

            AgentResult result = scope.resultOf(agentName)
                    .orElseThrow(() -> new IllegalStateException(
                            "通知指向了不存在的结果：" + agentName));

            boolean claimsSuccess = notification.type() == AgentMessage.Type.RESULT_READY;
            if (claimsSuccess != result.completed()) {
                throw new IllegalStateException(
                        "通知类型与黑板上的结果状态不一致：" + agentName
                                + "，通知 " + notification.type()
                                + "，结果 " + result.status());
            }

            if (!result.completed()) {
                failedAgent = agentName;
                failureReason = result.summary();
                break;
            }

            completed.add(agentName);
            if (!result.summary().isBlank()) {
                // 各段之间只空一行。早期这里给每段加了 "- " 前缀，
                // 但 summary 是多行 Markdown，前缀只作用在第一行，拼出来是个坏掉的列表
                if (!answer.isEmpty()) {
                    answer.append("\n\n");
                }
                answer.append(result.summary().strip());
            }
        }

        if (failedAgent != null) {
            answer.append("\n\n以上是已经完成的部分。")
                    .append(failedAgent).append(" 这一步没有完成（")
                    .append(failureReason.isBlank() ? "原因未提供" : failureReason)
                    .append("），依赖它的后续步骤已经停止，")
                    .append("剩余问题需要人工客服跟进，请不要把上面的内容当作完整答复。");
        }

        return new Aggregation(
                answer.toString().strip(),
                List.copyOf(completed),
                failedAgent,
                failureReason);
    }

    /**
     * 协议校验。这里的每一条不通过都意味着编排层有 bug，必须抛
     */
    private String requireProtocol(AgentMessage notification, Set<String> seen) {
        if (notification == null) {
            throw new IllegalArgumentException("结果通知不能为空");
        }
        if (notification.type() != AgentMessage.Type.RESULT_READY
                && notification.type() != AgentMessage.Type.RESULT_FAILED) {
            throw new IllegalArgumentException(
                    "聚合器收到了非结果通知：" + notification);
        }
        if (!RECIPIENT.equals(notification.to())) {
            throw new IllegalArgumentException(
                    "结果通知投递给了错误的接收者：" + notification);
        }

        String agentName = notification.content();
        if (!notification.from().equals(agentName)) {
            throw new IllegalStateException(
                    "结果通知发送者与结果署名不一致：" + notification);
        }
        if (!seen.add(agentName)) {
            throw new IllegalStateException("收到重复结果通知：" + agentName);
        }
        return agentName;
    }
}
