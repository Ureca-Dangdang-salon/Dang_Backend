package com.dangdangsalon.domain.chat.dto;

import com.dangdangsalon.domain.chat.entity.ChatRoom;
import com.dangdangsalon.domain.groomerprofile.entity.GroomerProfile;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatGroomerProfileDto {

    private Long groomerProfileId;
    private String address;
    private String serviceName;
    private String profileImageUrl;

    public static ChatGroomerProfileDto create(GroomerProfile groomerProfile) {
        return ChatGroomerProfileDto.builder()
                .groomerProfileId(groomerProfile.getId())
                .serviceName(groomerProfile.getName())
                .profileImageUrl(groomerProfile.getImageKey())
                .build();
    }
}
