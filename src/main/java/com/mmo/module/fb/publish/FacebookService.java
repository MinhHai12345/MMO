package com.mmo.module.fb.publish;

import com.mmo.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacebookService {

    private final AppProperties appProperties;

    private static final String GRAPH_API_URL = "https://graph.facebook.com/v18.0";

    public void postMessage(String message) {
        try {
            String url = GRAPH_API_URL + "/" + pageId + "/feed";
            
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("message", message);
            params.add("access_token", pageAccessToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Facebook post sent successfully");
            } else {
                log.error("❌ Failed to send Facebook post: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("❌ Failed to send Facebook post: {}", e.getMessage());
        }
    }

    public void postWithImage(String message, byte[] imageBytes) {
        try {
            String url = GRAPH_API_URL + "/" + pageId + "/photos";
            
            MultiValueMap<String, Object> params = new LinkedMultiValueMap<>();
            params.add("caption", message);
            params.add("access_token", pageAccessToken);
            params.add("source", new org.springframework.core.io.ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return "image.png";
                }
            });

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(params, headers);
            
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Facebook post with image sent successfully");
            } else {
                log.error("❌ Failed to send Facebook post with image: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("❌ Failed to send Facebook post with image: {}", e.getMessage());
        }
    }

    public void schedulePost(String message, String scheduledTime) {
        try {
            String url = GRAPH_API_URL + "/" + pageId + "/feed";
            
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("message", message);
            params.add("access_token", pageAccessToken);
            params.add("published", "false");
            params.add("scheduled_publish_time", scheduledTime);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Facebook post scheduled successfully for: {}", scheduledTime);
            } else {
                log.error("❌ Failed to schedule Facebook post: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("❌ Failed to schedule Facebook post: {}", e.getMessage());
        }
    }

    public void deletePost(String postId) {
        try {
            String url = GRAPH_API_URL + "/" + postId;
            
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("access_token", pageAccessToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(
                    url, 
                    HttpMethod.DELETE, 
                    request, 
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Facebook post deleted successfully: {}", postId);
            } else {
                log.error("❌ Failed to delete Facebook post: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("❌ Failed to delete Facebook post: {}", e.getMessage());
        }
    }

    public Map<String, Object> getPageInsights() {
        try {
            String url = GRAPH_API_URL + "/" + pageId + "/insights";
            
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("metric", "page_post_engagements,page_impressions,page_reach");
            params.add("access_token", pageAccessToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, 
                    HttpMethod.GET, 
                    request, 
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Facebook page insights retrieved successfully");
                return response.getBody();
            } else {
                log.error("❌ Failed to retrieve Facebook page insights: {}", response.getStatusCode());
                return new HashMap<>();
            }
        } catch (Exception e) {
            log.error("❌ Failed to retrieve Facebook page insights: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    public void commentOnPost(String postId, String comment) {
        try {
            String url = GRAPH_API_URL + "/" + postId + "/comments";
            
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("message", comment);
            params.add("access_token", pageAccessToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Facebook comment posted successfully");
            } else {
                log.error("❌ Failed to post Facebook comment: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("❌ Failed to post Facebook comment: {}", e.getMessage());
        }
    }
}
