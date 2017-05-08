package com.blackbuild.udm.jenkins.loader;

/**
 * Created by snpaux on 19.04.2017.
 */
public class ModelSource {

    final String jobName;
    final int buildNumber;

    public ModelSource(String jobName, int buildNumber) {
        this.jobName = jobName;
        this.buildNumber = buildNumber;
    }

    @Override
    public String toString() {
        return "Jars from " + jobName + "#" + buildNumber;
    }
}
