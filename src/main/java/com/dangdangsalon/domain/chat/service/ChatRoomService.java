package com.dangdangsalon.domain.chat.service;

import com.dangdangsalon.domain.chat.dto.ChatCustomerDto;
import com.dangdangsalon.domain.chat.dto.ChatEstimateDogProfileDto;
import com.dangdangsalon.domain.chat.dto.ChatEstimateInfo;
import com.dangdangsalon.domain.chat.dto.ChatGroomerProfileDto;
import com.dangdangsalon.domain.chat.dto.ChatMessageDto;
import com.dangdangsalon.domain.chat.dto.ChatRoomListDto;
import com.dangdangsalon.domain.chat.dto.CreateChatRoomRequestDto;
import com.dangdangsalon.domain.chat.dto.CreateChatRoomResponseDto;
import com.dangdangsalon.domain.chat.entity.ChatRoom;
import com.dangdangsalon.domain.chat.entity.SenderRole;
import com.dangdangsalon.domain.chat.repository.ChatRoomRepository;
import com.dangdangsalon.domain.estimate.entity.Estimate;
import com.dangdangsalon.domain.estimate.repository.EstimateRepository;
import com.dangdangsalon.domain.estimate.request.dto.ServicePriceResponseDto;
import com.dangdangsalon.domain.estimate.request.entity.EstimateRequestProfiles;
import com.dangdangsalon.domain.estimate.request.repository.EstimateRequestServiceRepository;
import com.dangdangsalon.domain.groomerprofile.entity.GroomerProfile;
import com.dangdangsalon.domain.groomerprofile.repository.GroomerProfileRepository;
import com.dangdangsalon.domain.orders.entity.Orders;
import com.dangdangsalon.domain.payment.dto.PaymentDogProfileResponseDto;
import com.dangdangsalon.domain.user.entity.Role;
import com.dangdangsalon.domain.user.entity.User;
import com.dangdangsalon.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final EstimateRepository estimateRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final GroomerProfileRepository groomerProfileRepository;
    private final EstimateRequestServiceRepository estimateRequestServiceRepository;

    private final ChatMessageService chatMessageService;

    @Transactional
    public CreateChatRoomResponseDto createChatRoom(CreateChatRoomRequestDto createChatRoomRequestDto) {
        chatRoomRepository.findByEstimateId(createChatRoomRequestDto.getEstimateId())
                .ifPresent(chatRoom -> {
                    throw new IllegalStateException("이미 해당 견적서에 대한 채팅방이 존재합니다. Id: "
                            + createChatRoomRequestDto.getEstimateId());
                });

        Estimate estimate = estimateRepository.findWithGroomerProfileAndCustomerById(
                        createChatRoomRequestDto.getEstimateId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "해당하는 견적서가 존재하지 않습니다. Id: " + createChatRoomRequestDto.getEstimateId()));

        GroomerProfile groomerProfile = estimate.getGroomerProfile();
        User customer = estimate.getEstimateRequest().getUser();

        ChatRoom chatRoom = ChatRoom.builder()
                .estimate(estimate)
                .groomerProfile(groomerProfile)
                .user(customer)
                .customerLeft(false)
                .groomerLeft(false)
                .build();

        ChatRoom createdChatRoom = chatRoomRepository.save(chatRoom);

        saveFirstChatToRedis(createdChatRoom, groomerProfile, estimate);

        return CreateChatRoomResponseDto.builder()
                .roomId(createdChatRoom.getId())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ChatRoomListDto> getChatRoomList(Long userId, String role) {
        Role userRole = Role.from(role);

        List<ChatRoom> chatRooms = new ArrayList<>();

        if (userRole.equals(Role.ROLE_USER)) {
            chatRooms = chatRoomRepository.findByGroomerProfileIdOrCustomerId(null, userId);
        } else if (userRole.equals(Role.ROLE_SALON)) {
            GroomerProfile groomerProfile = groomerProfileRepository.findByUserId(userId)
                    .orElseThrow(() -> new IllegalArgumentException("해당하는 미용사 프로필이 존재하지 않습니다. Id: " + userId));
            chatRooms = chatRoomRepository.findByGroomerProfileIdOrCustomerId(groomerProfile.getId(), null);
        }

        return chatRooms.stream()
                .map(this::convertToChatRoomListDto)
                .toList();
    }

    private ChatRoomListDto convertToChatRoomListDto(ChatRoom chatRoom) {
        String lastMessage = chatMessageService.getLastMessage(chatRoom.getId());
        int unreadCount = chatMessageService.getUnreadCount(chatRoom.getId());

        return ChatRoomListDto.builder()
                .roomId(chatRoom.getId())
                .groomerProfile(ChatGroomerProfileDto.create(chatRoom))
                .customer(ChatCustomerDto.create(chatRoom))
                .lastMessage(lastMessage)
                .unreadCount(unreadCount)
                .totalAmount(chatRoom.getEstimate().getTotalAmount())
                .build();
    }

    private void saveFirstChatToRedis(ChatRoom createdChatRoom, GroomerProfile groomerProfile, Estimate estimate) {
        ChatMessageDto estimateMessage = ChatMessageDto.builder()
                .roomId(createdChatRoom.getId())
                .senderId(groomerProfile.getId())
                .senderRole(SenderRole.GROOMER.name())
                .sendAt(LocalDateTime.now())
                .estimateInfo(createEstimateMessage(estimate))
                .build();

        ChatMessageDto wantSendImageMessage = ChatMessageDto.createImageMessage(createdChatRoom.getId(),
                groomerProfile.getId(), SenderRole.GROOMER.name(), estimate.getImageKey());

        ChatMessageDto wantSendDescriptionMessage = ChatMessageDto.createTextMessage(createdChatRoom.getId(),
                groomerProfile.getId(), SenderRole.GROOMER.name(), estimate.getDescription());

        ChatMessageDto firstMessage = ChatMessageDto.createTextMessage(createdChatRoom.getId(),
                groomerProfile.getId(), SenderRole.GROOMER.name(), groomerProfile.getDetails().getStartChat());

        chatMessageService.saveMessageRedis(estimateMessage);
        chatMessageService.saveMessageRedis(wantSendImageMessage);
        chatMessageService.saveMessageRedis(wantSendDescriptionMessage);
        chatMessageService.saveMessageRedis(firstMessage);
    }

    private ChatEstimateInfo createEstimateMessage(Estimate estimate) {
        return ChatEstimateInfo.builder()
                .dogProfileList(getDogProfileServices(estimate))
                .totalAmount(estimate.getTotalAmount())
                .build();
    }

    private List<ChatEstimateDogProfileDto> getDogProfileServices(Estimate estimate) {
        List<EstimateRequestProfiles> requestDogProfiles = estimate
                .getEstimateRequest()
                .getEstimateRequestProfiles();

        return requestDogProfiles.stream()
                .map(this::requestProfileToDto)
                .toList();
    }

    private ChatEstimateDogProfileDto requestProfileToDto(EstimateRequestProfiles profile) {
        List<ServicePriceResponseDto> services = getServicePrices(profile.getId());

        return ChatEstimateDogProfileDto.builder()
                .dogName(profile.getDogProfile().getName())
                .servicePriceList(services)
                .aggressionCharge(profile.getAggressionCharge())
                .healthIssueCharge(profile.getHealthIssueCharge())
                .build();
    }

    private List<ServicePriceResponseDto> getServicePrices(Long profileId) {
        return estimateRequestServiceRepository.findByEstimateRequestProfilesId(profileId)
                .stream()
                .map(ServicePriceResponseDto::create)
                .toList();
    }
}
