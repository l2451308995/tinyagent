package com.nageoffer.ai.tinyagent.react.demo;

import com.nageoffer.ai.tinyagent.react.LlmClient;
import com.nageoffer.ai.tinyagent.react.multiagent.AgentResult;
import com.nageoffer.ai.tinyagent.react.multiagent.Aggregator;
import com.nageoffer.ai.tinyagent.react.multiagent.BitMallHandoffTeam;
import com.nageoffer.ai.tinyagent.react.multiagent.HandoffAgent;
import com.nageoffer.ai.tinyagent.react.multiagent.HandoffRunner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * 第 23 篇：跨 Agent 上下文交接 Demo
 * <p>
 * 三个真实的 ReAct 专家串成一条依赖链，售后 → 商品 → IoT
 * 日志里有三处值得盯：每一步注入的上下文分了哪几块、每一步抽出了哪些工具事实、
 * 下游有没有靠上游的 facts 少查一遍
 * <p>
 * 全部工具都是内存 Mock，只需在项目根目录 .env 配 TINYAGENT_API_KEY
 */
public class MultiAgentContextDemo {

    private static final String REQUEST =
            "订单 88231 的扫地机老掉线，帮我查保修并给出处理建议；"
                    + "如果要换新，预算 2000 元以内，"
                    + "希望能和家里的比特 SoundBox Mini 智能音箱联动。";

    public static void main(String[] args) {
        Properties dotEnv = loadDotEnv();
        LlmClient llmClient = new LlmClient(
                setting(dotEnv, "TINYAGENT_API_URL",
                        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"),
                requiredSetting(dotEnv, "TINYAGENT_API_KEY"),
                setting(dotEnv, "TINYAGENT_MODEL", "qwen-plus")
        );

        List<HandoffAgent> team = BitMallHandoffTeam.team(llmClient);

        // 固定计划：把通信机制单独讲清楚。模型自主路由是第 22 篇 Supervisor 的事
        // 每条任务都写清这一步交付什么、哪些内容留给别人。交付边界是这次分工的产物，
        // 归编排层；写进专家人设就等于焊死在身份上，同一个专家在别的请求里会被误伤
        List<HandoffRunner.Step> plan = List.of(
                new HandoffRunner.Step(
                        "afterSalesSpecialist",
                        "查询订单 88231 的商品和签收日期，核验当前保修状态，"
                                + "并给出扫地机频繁掉线的处理建议。"
                                + "只交付订单、保修和掉线处理结论，换新候选和音箱联动不在本次范围内，"
                                + "一个字都不要写"),
                new HandoffRunner.Step(
                        "productSpecialist",
                        "结合上游给出的在用型号，筛选预算内的换新候选并说明规格差异。"
                                + "只交付候选型号、价格和选择依据，不要复述保修或退货结论，"
                                + "也不要判断候选能否与音箱联动，这一项不在本次范围内",
                        Set.of("afterSalesSpecialist")),
                new HandoffRunner.Step(
                        "iotSpecialist",
                        "核对上游给出的候选商品与用户现有智能音箱的联动能力。"
                                + "只交付兼容状态、已证实的语音指令和尚未确认的边界，"
                                + "不要复述订单、保修或商品规格全文",
                        Set.of("productSpecialist"))
        );

        System.out.println("########## 用户请求：" + REQUEST);
        HandoffRunner.RunResult run = new HandoffRunner(team).run(
                REQUEST,
                constraints(),
                plan);

        Aggregator.Aggregation aggregation = new Aggregator().aggregate(
                run.scope(),
                run.resultNotifications());

        printBlackboard(run);
        System.out.println("\n========== 最终结果 ==========");
        System.out.println(aggregation.answer());
        System.out.println("\n执行顺序：" + run.executedAgents());
        if (!aggregation.intact()) {
            System.out.println("失败步骤：" + aggregation.failedAgent()
                    + "，跳过步骤：" + run.skippedAgents()
                    + "，未被消费的 TASK：" + run.undeliveredTasks() + " 条");
        }
    }

    /**
     * 把黑板上的 facts 单独打一遍。这是判断交接有没有真正生效的直接证据：
     * 商品专家的输入里应该出现售后专家抽出来的 productName，而不是让它重新猜型号
     */
    private static void printBlackboard(HandoffRunner.RunResult run) {
        System.out.println("\n========== 黑板上的结构化事实 ==========");
        for (AgentResult result : run.scope().results()) {
            System.out.println("[" + result.agentName() + "] " + result.status());
            if (result.facts().isEmpty()) {
                System.out.println("  （无 facts，下游只能拿到摘要）");
                continue;
            }
            result.facts().forEach((key, value) ->
                    System.out.println("  - " + key + "：" + value));
        }
    }

    private static Map<String, String> constraints() {
        Map<String, String> constraints = new LinkedHashMap<>();
        constraints.put("订单号", "88231");
        constraints.put("预算", "换新预算 2000 元以内");
        constraints.put("已有设备", "比特 SoundBox Mini 智能音箱");
        return Collections.unmodifiableMap(constraints);
    }

    private static Properties loadDotEnv() {
        Properties properties = new Properties();
        Path path = Path.of(".env");
        if (!Files.exists(path)) {
            return properties;
        }

        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("读取 .env 文件失败：" + path.toAbsolutePath(), e);
        }
    }

    private static String requiredSetting(Properties dotEnv, String key) {
        String value = setting(dotEnv, key, "");
        if (value.isBlank()) {
            throw new IllegalStateException("请在项目根目录 .env 文件中配置 " + key);
        }
        return value;
    }

    private static String setting(Properties dotEnv, String key, String defaultValue) {
        String dotEnvValue = dotEnv.getProperty(key);
        if (dotEnvValue != null && !dotEnvValue.isBlank()) {
            return dotEnvValue;
        }
        return defaultValue;
    }
}
