package net.lintim.timetabling.util;

import net.lintim.util.Config;

public class WalkingEvaluatorParameters extends WalkingParameters {
    private final boolean addChanges;
    public WalkingEvaluatorParameters(Config config) {
        super(config);
        addChanges = config.getBooleanValue("tim_eval_walking_add_changes");
    }

    public boolean addChanges() {
        return addChanges;
    }
}
