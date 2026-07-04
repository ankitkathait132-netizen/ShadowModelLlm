package com.example.demo.common.llm;

import java.util.List;

/**
 * OpenAI-compatible chat completion request body, built entirely server-side.
 *
 * The caller only ever supplies plain text; the {@code model} is always the value
 * configured for the endpoint being called (see {@code ShadowProxyProperties.LlmEndpoint}),
 * never something a client can influence.
 */
public final class ChatCompletionRequest {

    private final String model;
    private final List<Message> messages;

    private ChatCompletionRequest(String model, List<Message> messages) {
        this.model = model;
        this.messages = messages;
    }

    public static ChatCompletionRequest of(String model, String userText) {
        return new ChatCompletionRequest(model, List.of(new Message("user", userText)));
    }

    public String getModel() {
        return model;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public static final class Message {
        private final String role;
        private final String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }
}
