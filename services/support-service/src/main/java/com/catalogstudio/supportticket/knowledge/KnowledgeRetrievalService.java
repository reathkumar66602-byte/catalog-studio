package com.catalogstudio.supportticket.knowledge;

import com.catalogstudio.supportticket.SupportedLocales;
import com.catalogstudio.supportticket.entity.SupportKnowledgeArticle;
import com.catalogstudio.supportticket.repository.SupportKnowledgeArticleRepository;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalService {

    private final SupportKnowledgeArticleRepository articleRepository;

    @Transactional(readOnly = true)
    public String buildContext(String locale, String userMessage) {
        String loc = SupportedLocales.normalize(locale);
        Set<String> tokens = tokenize(userMessage);
        if (tokens.isEmpty()) {
            return "";
        }
        LinkedHashSet<SupportKnowledgeArticle> matches = new LinkedHashSet<>();
        for (String token : tokens) {
            matches.addAll(articleRepository.searchVerified(loc, token, PageRequest.of(0, 3)));
        }
        if (matches.isEmpty() && !"en".equals(loc)) {
            for (String token : tokens) {
                matches.addAll(articleRepository.searchVerified("en", token, PageRequest.of(0, 3)));
            }
        }
        if (matches.isEmpty()) {
            return "";
        }
        return matches.stream()
                .limit(4)
                .map(a -> "### " + a.getTitle() + "\\n" + nullToEmpty(a.getSummary()) + "\\n" + a.getBody())
                .collect(Collectors.joining("\\n\\n"));
    }

    private Set<String> tokenize(String message) {
        if (!StringUtils.hasText(message)) {
            return Set.of();
        }
        return Arrays.stream(message.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(t -> t.length() >= 3)
                .filter(t -> !Set.of("the", "and", "for", "with", "from", "that", "this", "have", "what", "how").contains(t))
                .limit(8)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
