package com.example.demo.common.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatCompletionRequestTest {

    @Test
    void ofBuildsSingleUserMessageWithConfiguredModel() {
        ChatCompletionRequest request = ChatCompletionRequest.of("llama3.3-70b-instruct", "What is the capital of France?");

        assertThat(request.getModel()).isEqualTo("llama3.3-70b-instruct");
        assertThat(request.getMessages()).hasSize(1);

        ChatCompletionRequest.Message message = request.getMessages().get(0);
        assertThat(message.getRole()).isEqualTo("user");
        assertThat(message.getContent()).isEqualTo("What is the capital of France?");
    }

    @Test
    void ofPreservesExactUserTextWithoutMutation() {
        String text = "  text with   whitespace  ";

        ChatCompletionRequest request = ChatCompletionRequest.of("model-x", text);

        assertThat(request.getMessages().get(0).getContent()).isEqualTo(text);
    }
}
