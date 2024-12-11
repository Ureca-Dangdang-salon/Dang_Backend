package com.dangdangsalon.domain.payment.service;

import com.dangdangsalon.domain.notification.service.NotificationService;
import com.dangdangsalon.domain.notification.service.RedisNotificationService;
import com.dangdangsalon.domain.orders.entity.Orders;
import com.dangdangsalon.domain.user.entity.User;
import com.dangdangsalon.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.aspectj.bridge.MessageUtil.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@DisplayName("PaymentNotificationService 테스트")
class PaymentNotificationServiceTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisNotificationService redisNotificationService;

    @InjectMocks
    private PaymentNotificationService paymentNotificationService;

    private User mockUser;
    private Orders mockOrders;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        mockUser = User.builder()
                .notificationEnabled(true)
                .build();

        mockOrders = Orders.builder()
                .user(mockUser)
                .build();

        ReflectionTestUtils.setField(mockUser, "id", 1L);
        ReflectionTestUtils.setField(mockOrders, "id", 101L);
    }

    @Test
    @DisplayName("알림 전송 - 성공")
    void sendNotificationToUser_Success() {
        // Given
        when(notificationService.getFcmTokens(1L)).thenReturn(List.of("dummyFcmToken1", "dummyFcmToken2"));
        when(notificationService.sendNotificationWithData(
                eq("dummyFcmToken1"),
                eq("결제가 완료되었습니다"),
                eq("결제 내역을 확인해보세요."),
                eq("결제"),
                eq(1L) // ID 통일
        )).thenReturn(true);

        when(notificationService.sendNotificationWithData(
                eq("dummyFcmToken2"),
                eq("결제가 완료되었습니다"),
                eq("결제 내역을 확인해보세요."),
                eq("결제"),
                eq(1L) // ID 통일
        )).thenReturn(true);

        // When
        paymentNotificationService.sendNotificationToUser(mockOrders);

        // Then
        verify(notificationService, times(2)).sendNotificationWithData(
                anyString(),
                eq("결제가 완료되었습니다"),
                eq("결제 내역을 확인해보세요."),
                eq("결제"),
                eq(1L)
        );
        verify(redisNotificationService, times(1)).saveNotificationToRedis(
                eq(1L),
                eq("결제가 완료되었습니다"),
                eq("결제 내역을 확인해보세요."),
                eq("결제"),
                eq(1L)
        );
    }

    @Test
    @DisplayName("알림 전송 - 알림 비활성화")
    void sendNotificationToUser_NotificationDisabled() {
        // Given
        mockUser.updateNotificationEnabled(false); // 알림 비활성화

        // When
        paymentNotificationService.sendNotificationToUser(mockOrders);

        // Then
        verify(notificationService, never()).sendNotificationWithData(any(), any(), any(), any(), any());
        verify(redisNotificationService, never()).saveNotificationToRedis(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("알림 전송 - 사용자 없음 예외")
    void sendNotificationToUser_UserNotFound() {
        // 토큰 목록이 비어있는 경우를 시뮬레이션
        when(notificationService.getFcmTokens(1L)).thenReturn(Collections.emptyList());

        // When & Then
        try {
            paymentNotificationService.sendNotificationToUser(mockOrders);
            fail("예외가 발생해야 합니다.");
        } catch (Exception e) {
            // 적절한 예외 처리 확인
            assertTrue(e instanceof IllegalArgumentException);
        }

        verify(notificationService, never()).sendNotificationWithData(any(), any(), any(), any(), any());
        verify(redisNotificationService, never()).saveNotificationToRedis(anyLong(), any(), any(), any(), any());
    }
    @Test
    @DisplayName("알림 전송 - FCM 전송 실패")
    void sendNotificationToUser_FcmSendFailed() {
        // Assuming mockOrders returns 1L when getId() is called
        when(notificationService.getFcmTokens(1L)).thenReturn(List.of("dummyFcmToken1"));
        when(notificationService.sendNotificationWithData(
                eq("dummyFcmToken1"),
                eq("결제가 완료되었습니다"),
                eq("결제 내역을 확인해보세요."),
                eq("결제"),
                eq(1L) // Change this to 1L to match the actual implementation
        )).thenReturn(false);

        // When
        paymentNotificationService.sendNotificationToUser(mockOrders);

        // Then
        verify(notificationService, times(1)).sendNotificationWithData(
                eq("dummyFcmToken1"),
                eq("결제가 완료되었습니다"),
                eq("결제 내역을 확인해보세요."),
                eq("결제"),
                eq(1L) // Change this to 1L
        );
        verify(redisNotificationService, never()).saveNotificationToRedis(anyLong(), any(), any(), any(), any());
    }


    @Test
    @DisplayName("알림 전송 - FCM 토큰 없음")
    void sendNotificationToUser_NoFcmToken() {
        // Given
        when(notificationService.getFcmTokens(1L)).thenReturn(List.of());

        // When
        paymentNotificationService.sendNotificationToUser(mockOrders);

        // Then
        verify(notificationService, times(1)).getFcmTokens(1L);
        verify(notificationService, never()).sendNotificationWithData(any(), any(), any(), any(), any());
        verify(redisNotificationService, never()).saveNotificationToRedis(anyLong(), any(), any(), any(), any());
    }
}