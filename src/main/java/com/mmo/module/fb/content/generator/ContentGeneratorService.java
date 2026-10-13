package com.mmo.module.fb.content.generator;

import com.mmo.module.fb.domain.entity.ContentTemplate;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchPrediction;
import com.mmo.module.fb.domain.entity.TeamForm;
import com.mmo.module.fb.domain.repository.ContentTemplateRepository;
import com.mmo.module.fb.domain.repository.TeamFormRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentGeneratorService {

    private final ContentTemplateRepository contentTemplateRepository;
    private final TeamFormRepository teamFormRepository;

    public String generateMatchInsight(MatchPrediction prediction) {
        Match match = prediction.getMatch();
        
        Map<String, Object> context = new HashMap<>();
        context.put("match", match);
        context.put("prediction", prediction);
        context.put("homeTeam", match.getHomeTeam());
        context.put("awayTeam", match.getAwayTeam());
        
        // Add team form data
        TeamForm homeForm = teamFormRepository.findByTeamIdAndSeasonIdAndFormType(
                match.getHomeTeam().getId(), match.getSeason().getId(), "LAST_5").orElse(null);
        TeamForm awayForm = teamFormRepository.findByTeamIdAndSeasonIdAndFormType(
                match.getAwayTeam().getId(), match.getSeason().getId(), "LAST_5").orElse(null);
        
        context.put("homeForm", homeForm != null ? homeForm.getFormData() : "");
        context.put("awayForm", awayForm != null ? awayForm.getFormData() : "");
        
        // Get template
        ContentTemplate template = contentTemplateRepository
                .findByPlatformAndTemplateType("TELEGRAM", "MATCH_INSIGHT")
                .orElseThrow(() -> new RuntimeException("Template not found"));
        
        // TODO: Implement template rendering with Thymeleaf
        return renderTemplate(template.getContent(), context);
    }

    public String generateMatchDashboard(List<MatchPrediction> freeMatches, List<MatchPrediction> vipMatches) {
        Map<String, Object> context = new HashMap<>();
        context.put("freeMatches", freeMatches);
        context.put("vipMatches", vipMatches);
        context.put("totalMatches", freeMatches.size() + vipMatches.size());
        
        ContentTemplate template = contentTemplateRepository
                .findByPlatformAndTemplateType("TELEGRAM", "DAILY_DASHBOARD")
                .orElseThrow(() -> new RuntimeException("Template not found"));
        
        return renderTemplate(template.getContent(), context);
    }

    public String generateMatchRecap(List<MatchPrediction> completedMatches) {
        Map<String, Object> context = new HashMap<>();
        context.put("matches", completedMatches);
        
        long winCount = completedMatches.stream()
                .filter(m -> "WIN".equals(m.getResult()))
                .count();
        
        context.put("winCount", winCount);
        context.put("lossCount", completedMatches.size() - winCount);
        context.put("winRate", completedMatches.isEmpty() ? 0 : (winCount * 100.0 / completedMatches.size()));
        
        ContentTemplate template = contentTemplateRepository
                .findByPlatformAndTemplateType("TELEGRAM", "DAILY_RECAP")
                .orElseThrow(() -> new RuntimeException("Template not found"));
        
        return renderTemplate(template.getContent(), context);
    }

    private String renderTemplate(String templateContent, Map<String, Object> context) {
        // TODO: Implement proper Thymeleaf rendering
        // For now, simple placeholder replacement
        String result = templateContent;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}
