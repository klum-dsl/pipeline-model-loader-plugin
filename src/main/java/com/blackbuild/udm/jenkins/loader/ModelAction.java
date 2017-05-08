package com.blackbuild.udm.jenkins.loader;

import hudson.model.InvisibleAction;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by snpaux on 20.04.2017.
 */
public class ModelAction extends InvisibleAction {

    private final List<ModelSource> sources = new ArrayList<>();

    public void addSource(ModelSource source) {
        sources.add(source);
    }

    public void addSource(String jobName, int buildNumber) {
        sources.add(new ModelSource(jobName, buildNumber));
    }

    public List<ModelSource> getSources() {
        return sources;
    }
}
