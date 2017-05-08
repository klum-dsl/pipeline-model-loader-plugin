package com.blackbuild.udm.jenkins.loader;

import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import hudson.AbortException;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.util.FileVisitor;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.libs.ClasspathAdder;
import org.jenkinsci.plugins.workflow.libs.ClasspathAdder.Addition;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

/**
 * Created by snpaux on 05.05.2017.
 */
public class JarLoader {

    private String jobName;
    private int buildNumber;

    public JarLoader(String jobName, int buildNumber) {
        this.jobName = jobName;
        this.buildNumber = buildNumber;
    }

    public List<URL> loadJarsFromJob() throws IOException {

        Job sourceJob = Jenkins.getInstance().getItemByFullName(jobName, Job.class);

        if (sourceJob == null)
            throw new AbortException(format("No job named '%s' found.", jobName));

        Run sourceBuild;

        if (buildNumber == -1)
            sourceBuild = sourceJob.getLastSuccessfulBuild();
        else
            sourceBuild = sourceJob.getBuildByNumber(buildNumber);

        if (sourceBuild == null)
            throw new AbortException(format("Job named '%s' has no successful runs.", jobName));

        buildNumber = sourceBuild.getNumber();

        URI archiveRoot = sourceBuild.getArtifactManager().root().toURI();

        JarFileVisitor visitor = new JarFileVisitor();
        visitor.visit(new File(archiveRoot), "");

        return visitor.getUrls();
    }

    public int getBuildNumber() {
        return buildNumber;
    }

    private static class JarFileVisitor extends FileVisitor {

        private List<URL> urls = new ArrayList<>();

        @Override
        public void visit(File file, String s) throws IOException {
            if (file.isDirectory()) {
                for (File child : file.listFiles()) {
                    visit(child, null);
                }
            }

            if (!file.getName().endsWith(".jar"))
                return;

            urls.add(file.toURI().toURL());
        }

        List<URL> getUrls() {
            return urls;
        }
    }


    public static void reloadPreviousModelsFor(@Nonnull CpsFlowExecution execution) throws Exception {

        Queue.Executable executable = execution.getOwner().getExecutable();
        Run<?,?> build;
        if (executable instanceof Run) {
            build = (Run) executable;
        } else {
            // SCM.checkout does not make it possible to do checkouts outside the context of a Run.
            return;
        }

        ModelAction action = build.getAction(ModelAction.class);
        GroovyClassLoader loader = execution.getTrustedShell().getClassLoader();

        if (action != null) {
            // resumedBuild
            for (ModelSource source : action.getSources()) {
                JarLoader jarLoader = new JarLoader(source.jobName, source.buildNumber);

                for (URL url : jarLoader.loadJarsFromJob()) {
                    loader.addURL(url);
                }
            }
        }
    }


}
