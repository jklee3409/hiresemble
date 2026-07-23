package com.hiresemble.profile.domain.model;

import java.util.ArrayList;
import java.util.List;

public record ProfileCompletion(
        boolean completed, int completionPercent, List<ProfileCompletionItem> missingItems) {

    public ProfileCompletion {
        missingItems = List.copyOf(missingItems);
    }

    public static ProfileCompletion calculate(
            String legalName,
            List<String> desiredRoles,
            List<String> desiredIndustries,
            List<String> desiredLocations,
            boolean hasPrimaryEducation) {
        List<ProfileCompletionItem> missing = new ArrayList<>();
        if (legalName == null || legalName.isBlank()) {
            missing.add(ProfileCompletionItem.LEGAL_NAME);
        }
        if (desiredRoles.isEmpty()) {
            missing.add(ProfileCompletionItem.DESIRED_ROLE);
        }
        if (desiredIndustries.isEmpty()) {
            missing.add(ProfileCompletionItem.DESIRED_INDUSTRY);
        }
        if (desiredLocations.isEmpty()) {
            missing.add(ProfileCompletionItem.DESIRED_LOCATION);
        }
        if (!hasPrimaryEducation) {
            missing.add(ProfileCompletionItem.PRIMARY_EDUCATION);
        }
        int completionPercent = (ProfileCompletionItem.values().length - missing.size()) * 20;
        return new ProfileCompletion(missing.isEmpty(), completionPercent, missing);
    }
}
