package com.nageoffer.ai.tinyagent.react.demo;

import com.nageoffer.ai.tinyagent.react.LlmClient;
import com.nageoffer.ai.tinyagent.react.multiagent.Agent;
import com.nageoffer.ai.tinyagent.react.multiagent.BitMallSpecialists;
import com.nageoffer.ai.tinyagent.react.multiagent.SupervisorAgent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * 主从式多智能体 Demo：LLM Supervisor 处理跨品类复合请求
 * <p>
 * 主管依次调度商品专家、IoT 专家，收齐结论后综合成面向用户的完整回复
 * 全部工具都是内存 Mock，只需在 .env 里配 TINYAGENT_API_KEY 即可运行
 */
public class MultiAgentSupervisorDemo {

    public static void main(String[] args) {
        Properties dotEnv = loadDotEnv();
        LlmClient llmClient = new LlmClient(
                setting(dotEnv, "TINYAGENT_API_URL",
                        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"),
                requiredSetting(dotEnv, "TINYAGENT_API_KEY"),
                setting(dotEnv, "TINYAGENT_MODEL", "qwen-plus")
        );

        // 组建一支专家团队（商品咨询 / 售后服务 / IoT 搭配）
        List<Agent> team = BitMallSpecialists.team(llmClient);

        // LLM Supervisor——跨品类复合请求，主管循环调多个专家再综合
        System.out.println("========== LLM Supervisor（跨品类，对比 + 搭配） ==========");
        SupervisorAgent supervisor = new SupervisorAgent(llmClient, team, 8, 8000);
        String answer = supervisor.run(
                "比特 AirX 真无线耳机 和 比特 BandPro 头戴式耳机 哪个好？我通勤用；"
                        + "另外我买了比特 Phone S1 手机，想配一套运动装备，预算 3000");
        System.out.println("\n[最终回复] " + answer);
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
