package com.billim.domain.chat.controller;

import com.billim.domain.chat.service.ChatRoomViewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ChatRoomControllerAvailabilityTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(ChatRoomController.class)
        .withBean(ChatRoomViewService.class, () -> mock(ChatRoomViewService.class));

    @Test
    @DisplayName("채팅방 API를 명시적으로 비활성화하면 컨트롤러를 등록하지 않는다")
    void roomControllerCanBeDisabled() {
        contextRunner.withPropertyValues("billim.chat.rooms.enabled=false")
            .run(context ->
                assertThat(context).doesNotHaveBean(ChatRoomController.class));
    }

    @Test
    @DisplayName("채팅방 API를 활성화하면 생성·목록·상세 컨트롤러를 등록한다")
    void roomControllerEnabledExplicitly() {
        contextRunner.withPropertyValues("billim.chat.rooms.enabled=true")
            .run(context -> assertThat(context).hasSingleBean(ChatRoomController.class));
    }
}
