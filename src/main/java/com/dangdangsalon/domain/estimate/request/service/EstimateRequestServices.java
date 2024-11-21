package com.dangdangsalon.domain.estimate.request.service;


import com.dangdangsalon.domain.dogprofile.entity.DogProfile;
import com.dangdangsalon.domain.estimate.request.dto.DogNameResponseDto;
import com.dangdangsalon.domain.estimate.request.dto.EstimateRequestDto;
import com.dangdangsalon.domain.estimate.request.dto.MyEstimateRequestResponseDto;
import com.dangdangsalon.domain.estimate.request.entity.EstimateRequest;
import com.dangdangsalon.domain.estimate.request.repository.EstimateRequestProfilesRepository;
import com.dangdangsalon.domain.estimate.request.repository.EstimateRequestRepository;
import com.dangdangsalon.domain.region.entity.District;
import com.dangdangsalon.domain.region.repository.DistrictRepository;
import com.dangdangsalon.domain.user.entity.User;
import com.dangdangsalon.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EstimateRequestServices {

    private final EstimateRequestInsertService estimateRequestInsertService;
    private final GroomerEstimateRequestService groomerEstimateRequestService;
    private final DistrictRepository districtRepository;
    private final EstimateRequestRepository estimateRequestRepository;
    private final UserRepository userRepository;

    // 견적 요청 등록
    @Transactional
    public void insertEstimateRequest(EstimateRequestDto estimateRequestDto, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저 아이디를 찾을 수 없습니다: " + userId));

        District district = districtRepository.findByName(estimateRequestDto.getDistrict());
        EstimateRequest estimateRequest = estimateRequestInsertService.insertEstimateRequest(estimateRequestDto, user, district);

        groomerEstimateRequestService.insertGroomerEstimateRequests(estimateRequest, district, estimateRequestDto);
    }

    // 내 견적 요청 조회
    @Transactional(readOnly = true)
    public List<MyEstimateRequestResponseDto> getMyEstimateRequest(Long userId) {
        // 내 견적 요청 다 가져오고
        List<EstimateRequest> estimateRequestList = estimateRequestRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("회원의 견적 요청을 찾을 수 없습니다: " + userId));

        return estimateRequestList.stream()
                .map(estimateRequest -> {
                    // 견적 요청에 연결된 강아지 정보 가져온다
                    List<DogNameResponseDto> dogList = estimateRequest.getEstimateRequestProfiles().stream()
                            .map(estimateRequestProfile -> {
                                DogProfile dogProfile = estimateRequestProfile.getDogProfile();
                                return DogNameResponseDto.builder()
                                        .dogName(dogProfile.getName())
                                        .build();
                            })
                            .toList();

                    return MyEstimateRequestResponseDto.builder()
                            .dogList(dogList)
                            .date(estimateRequest.getRequestDate().toLocalDate())
                            .status(estimateRequest.getRequestStatus())
                            .build();
                })
                .toList();
    }
}