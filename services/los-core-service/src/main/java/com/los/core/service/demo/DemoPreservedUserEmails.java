package com.los.core.service.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class DemoPreservedUserEmails {

    private final List<String> preservedEmailsLower;

    public DemoPreservedUserEmails(
            @Value("${los.demo.preserved-user-emails:sahil@gmail.com,borrower@credinnov.com}") String csv) {
        this.preservedEmailsLower = parseCsv(csv);
    }

    public List<String> preservedEmailsLower() {
        return preservedEmailsLower;
    }

    private static List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.toList());
    }
}
