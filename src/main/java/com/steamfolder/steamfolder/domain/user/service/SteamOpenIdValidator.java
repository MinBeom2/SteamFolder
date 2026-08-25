package com.steamfolder.steamfolder.domain.user.service;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SteamOpenIdValidator {

    private static final String STEAM_OPENID_URL = "https://steamcommunity.com/openid/login";
    private static final Pattern STEAM_ID_PATTERN = Pattern.compile("https://steamcommunity.com/openid/id/(\\d+)");

    private final WebClient webClient;

    public SteamOpenIdValidator(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(STEAM_OPENID_URL).build();
    }

    public String validateAndExtractSteamId(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            throw new CustomException(ErrorCode.STEAM_AUTH_FAILED, "OpenID 인증 파라미터가 비어있습니다.");
        }

        String claimedId = params.get("openid.claimed_id");
        if (claimedId == null || claimedId.isBlank()) {
            claimedId = params.get("openid.identity");
        }

        if (claimedId == null || claimedId.isBlank()) {
            throw new CustomException(ErrorCode.STEAM_AUTH_FAILED, "claimed_id 파라미터가 존재하지 않습니다.");
        }

        // 1. 스팀 서버에 유효성 검증 요청 (check_authentication)
        verifyWithSteamProvider(params);

        // 2. claimed_id URL에서 64-bit SteamID 추출
        return extractSteamIdFromClaimedId(claimedId);
    }

    private void verifyWithSteamProvider(Map<String, String> params) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        params.forEach(formData::add);
        formData.set("openid.mode", "check_authentication");

        try {
            String response = webClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null || !response.contains("is_valid:true")) {
                log.warn("스팀 OpenID 검증 실패: response={}", response);
                throw new CustomException(ErrorCode.STEAM_AUTH_FAILED, "스팀 인증 서버에서 유효성을 검증하지 못했습니다.");
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("스팀 OpenID 검증 통신 오류: ", e);
            throw new CustomException(ErrorCode.STEAM_AUTH_FAILED, "스팀 인증 서버와 통신 중 오류가 발생했습니다.");
        }
    }

    public String extractSteamIdFromClaimedId(String claimedId) {
        Matcher matcher = STEAM_ID_PATTERN.matcher(claimedId);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new CustomException(ErrorCode.STEAM_AUTH_FAILED, "유효한 64-bit SteamID 형식이 아닙니다.");
    }
}
