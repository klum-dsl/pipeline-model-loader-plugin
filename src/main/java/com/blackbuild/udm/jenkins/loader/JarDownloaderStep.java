package com.blackbuild.udm.jenkins.loader;

import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FileVisitor;
import jenkins.model.Jenkins;
import jenkins.util.VirtualFile;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.libs.ClasspathAdder;
import org.jenkinsci.plugins.workflow.libs.LibraryAdder;
import org.jenkinsci.plugins.workflow.libs.LibraryStep;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.springframework.ui.Model;

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

/**
 * Created by snpaux on 19.04.2017.
 */
public class JarDownloaderStep extends AbstractStepImpl {

    private final String jobName;
    private String configurationClass;

    @DataBoundConstructor
    public JarDownloaderStep(String jobName) {
        this.jobName = jobName;
    }

    @DataBoundSetter
    public void setConfigurationClass(String configurationClass) {
        this.configurationClass = configurationClass;
    }

    public String getJobName() {
        return jobName;
    }

    public String getConfigurationClass() {
        return configurationClass;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "loadModelFrom";
        }

        @Override public String getDisplayName() {
            return "Load a model";
        }
    }

    public static class Execution extends AbstractSynchronousStepExecution<Object> {

        private static final long serialVersionUID = 1L;

        @Inject private transient JarDownloaderStep step;
        @StepContextParameter private transient Run<?,?> run;
        @StepContextParameter private transient TaskListener listener;

        @Override
        protected Object run() throws Exception {

            JarLoader jarLoader = new JarLoader(step.getJobName(), -1);
            List<URL> jarUrls = jarLoader.loadJarsFromJob();

            CpsFlowExecution exec = (CpsFlowExecution) getContext().get(FlowExecution.class);
            GroovyClassLoader loader = exec.getTrustedShell().getClassLoader();

            ModelAction action = run.getAction(ModelAction.class);

            if (action == null) {
                action = new ModelAction();
                run.addAction(action);
            }

            for (URL url: jarUrls) {
                listener.getLogger().printf("Loading jar %s%n", url.getFile());
                loader.addURL(url);
            }

            action.addSource(step.getJobName(), jarLoader.getBuildNumber());

            run.save(); // persist changes to ModelAction

            if (step.getConfigurationClass() == null) {
                listener.getLogger().println("No configuration class defined");
                return null;
            }

            listener.getLogger().println("Executing Model script: " + step.getConfigurationClass());
            Class<Script> configClass = (Class<Script>) loader.loadClass(step.getConfigurationClass());

            return configClass.newInstance().run();
        }

    }

}
