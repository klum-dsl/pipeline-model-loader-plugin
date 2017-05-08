package com.blackbuild.udm.jenkins.loader;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import hudson.Extension;
import hudson.model.Queue;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.GroovyShellDecorator;

import java.io.IOException;
import java.net.URL;

@Extension
public class ModelClassPathDecorator extends GroovyShellDecorator {

    @Override
    public void configureShell(CpsFlowExecution exec, GroovyShell shell) {

        if (exec == null)
            return;

        Queue.Executable executable;
        try {
            executable = exec.getOwner().getExecutable();
        } catch (IOException e) {
            return;
        }
        Run<?,?> run;
        if (executable instanceof Run) {
            run = (Run) executable;
        } else {
            // SCM.checkout does not make it possible to do checkouts outside the context of a Run.
            return;
        }

        GroovyClassLoader loader = shell.getClassLoader();
        ModelAction action = run.getAction(ModelAction.class);

        if (action == null) {
            return;
        }

        for (ModelSource modelSource : action.getSources()) {

            try {
                for (URL url : new JarLoader(modelSource.jobName, modelSource.buildNumber).loadJarsFromJob()) {
                    loader.addURL(url);
                }
            } catch (IOException e) {
                return;
            }
        }
    }
}
