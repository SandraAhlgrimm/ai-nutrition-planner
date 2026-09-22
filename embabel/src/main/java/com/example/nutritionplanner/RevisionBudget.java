package com.example.nutritionplanner;

record RevisionBudget(int revisionsUsed) {

    static final int MAX_REVISIONS = 3;

    RevisionBudget {
        if (revisionsUsed < 0 || revisionsUsed > MAX_REVISIONS) {
            throw new IllegalArgumentException("Revision count must be between 0 and " + MAX_REVISIONS);
        }
    }

    int auditNumber() {
        return revisionsUsed + 1;
    }

    boolean exhausted() {
        return revisionsUsed == MAX_REVISIONS;
    }

    RevisionBudget nextRevision() {
        if (exhausted()) {
            throw new IllegalStateException("The nutrition plan revision budget is exhausted");
        }
        return new RevisionBudget(revisionsUsed + 1);
    }
}
