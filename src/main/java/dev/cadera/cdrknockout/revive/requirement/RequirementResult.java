package dev.cadera.cdrknockout.revive.requirement;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record RequirementResult(boolean passed, Set<RequirementType> selected, String failureMessage) {

    public RequirementResult {
        selected = Collections.unmodifiableSet(new LinkedHashSet<>(selected));
        failureMessage = failureMessage == null ? "" : failureMessage;
    }

    public static RequirementResult success(Set<RequirementType> selected) {
        return new RequirementResult(true, selected, "");
    }

    public static RequirementResult failure(String message) {
        return new RequirementResult(false, Set.of(), message);
    }
}
