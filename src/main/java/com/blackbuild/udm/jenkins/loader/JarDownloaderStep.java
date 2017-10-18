package com.blackbuild.udm.jenkins.loader;

import com.blackbuild.klum.ast.util.FactoryHelper;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;
import groovy.lang.Script;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.inject.Inject;
import java.net.URL;
import java.util.List;

/**
 * Created by snpaux on 19.04.2017.
 */
public class JarDownloaderStep extends AbstractStepImpl {

    private final String jobName;
    private String configurationClass;
    private String modelClass;

    @DataBoundConstructor
    public JarDownloaderStep(String jobName) {
        this.jobName = jobName;
    }

    @DataBoundSetter
    public void setConfigurationClass(String configurationClass) {
        this.configurationClass = configurationClass;
    }

    @DataBoundSetter
    public void setModelClass(String modelClass) {
        this.modelClass = modelClass;
    }

    public String getJobName() {
        return jobName;
    }

    public String getConfigurationClass() {
        return configurationClass;
    }

    public String getModelClass() {
        return modelClass;
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
            return "Load a Klum model";
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

            Class<GroovyObject> modelClass = null;
            Class<Script> configClass = null;

            if (step.getModelClass() != null) {
                listener.getLogger().println("Loading Model class: " + step.getModelClass());
                modelClass = (Class<GroovyObject>) loader.loadClass(step.getModelClass());
            }
            if (step.getConfigurationClass() != null) {
                listener.getLogger().println("Executing Model script: " + step.getConfigurationClass());
                configClass = (Class<Script>) loader.loadClass(step.getConfigurationClass());
            }

            if (modelClass != null && configClass == null) {
                listener.getLogger().println("create Model from classpath hint");
                return FactoryHelper.createFromClasspath(modelClass);
            }

            if (modelClass == null && configClass != null) {
                listener.getLogger().println("Create model from config script: " + configClass.getName());
                return configClass.newInstance().run();
            }

            if (configClass != null && modelClass != null) {
                listener.getLogger().println("Create model with explicit script");
                return modelClass.getMethod("createFrom", Class.class).invoke(null, configClass);
            }

            listener.getLogger().println("No configuration class defined");
            return null;
        }
    }
}
