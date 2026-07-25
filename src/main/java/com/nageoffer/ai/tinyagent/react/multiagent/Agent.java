package com.nageoffer.ai.tinyagent.react.multiagent;

/**
 * 一个智能体的最小抽象。把前面二十多篇搭起来的能力收敛成三个方法：
 * <ul>
 *     <li>{@link #name()}：唯一标识，用小驼峰，被包成工具时直接当函数名用</li>
 *     <li>{@link #description()}：agent card，一句话说清这个 Agent 擅长什么、什么时候找它</li>
 *     <li>{@link #run(String)}：接一个任务，返回结果文本</li>
 * </ul>
 * 单体 ReActAgent、带人设的专家、主管，最终都统一到这个接口上——
 * 于是主管调专家和专家调工具就长成了同一个形状
 */
public interface Agent {

    String name();

    String description();

    String run(String userMessage);
}
