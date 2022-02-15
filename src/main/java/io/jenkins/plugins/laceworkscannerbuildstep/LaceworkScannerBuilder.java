package io.jenkins.plugins.laceworkscannerbuildstep;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.Secret;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class LaceworkScannerBuilder extends Builder implements SimpleBuildStep {

    public static final int OK_CODE = 0;

    private static int buildId;
    private static int count;

    private String customFlags;
    private boolean fixableOnly;
    private String imageName;
    private String imageTag;
    private boolean noPull;
    private boolean evaluatePolicies;
    private boolean saveToLacework;
    private boolean disableLibraryPackageScanning;
    private boolean showEvaluationExceptions;
    private String tags;

    public synchronized static void setBuildId(int buildId) {
        LaceworkScannerBuilder.buildId = buildId;
    }

    public synchronized static void setCount(int count) {
        LaceworkScannerBuilder.count = count;
    }

    @DataBoundConstructor
    public LaceworkScannerBuilder(String customFlags, boolean fixableOnly, String imageName, String imageTag,
            boolean noPull, boolean evaluatePolicies, boolean saveToLacework, boolean disableLibraryPackageScanning,
            boolean showEvaluationExceptions, String tags) {
        this.customFlags = customFlags;
        this.fixableOnly = fixableOnly;
        this.imageName = imageName;
        this.imageTag = imageTag;
        this.noPull = noPull;
        this.evaluatePolicies = evaluatePolicies;
        this.saveToLacework = saveToLacework;
        this.disableLibraryPackageScanning = disableLibraryPackageScanning;
        this.showEvaluationExceptions = showEvaluationExceptions;
        this.tags = tags;
    }

    public String getCustomFlags() {
        return customFlags;
    }

    public boolean getFixableOnly() {
        return fixableOnly;
    }

    public String getImageName() {
        return imageName;
    }

    public String getImageTag() {
        return imageTag;
    }

    public boolean getNoPull() {
        return noPull;
    }

    public boolean getEvaluatePolicies() {
        return evaluatePolicies;
    }

    public boolean getSaveToLacework() {
        return saveToLacework;
    }

    public boolean getDisableLibraryPackageScanning() {
        return disableLibraryPackageScanning;
    }

    public boolean getShowEvaluationExceptions() {
        return showEvaluationExceptions;
    }

    public String getTags() {
        return tags;
    }

    @Override
    public void perform(Run<?, ?> build, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener)
            throws AbortException, java.lang.InterruptedException {
        // This is where you 'build' the project.

        String laceworkAccountName = getDescriptor().getLaceworkAccountName();
        Secret laceworkAccessToken = getDescriptor().getLaceworkAccessToken();
        if (laceworkAccountName == null || laceworkAccountName.trim().equals("") || laceworkAccessToken == null
                || Secret.toString(laceworkAccessToken).trim().equals("")) {
            throw new AbortException(
                    "Missing configuration. Please set the global configuration parameters in The \"Lacework Security\" section under  \"Manage Jenkins -> Configure System\", before continuing.\n");
        }

        // Support unique names for artifacts when there are multiple steps in the same
        // build
        String artifactSuffix, artifactName;
        if (build.hashCode() != buildId) {
            // New build
            setBuildId(build.hashCode());
            setCount(1);
            artifactSuffix = null; // When there is only one step, there should be no suffix at all
            artifactName = "laceworkscan.txt";
        } else {
            setCount(count + 1);
            artifactSuffix = Integer.toString(count);
            artifactName = "laceworkscan-" + artifactSuffix + ".txt";
        }

        int exitCode = LaceworkScannerExecuter.execute(build, workspace, env, launcher, listener, laceworkAccountName,
                laceworkAccessToken, customFlags, fixableOnly, imageName, imageTag, artifactName, noPull,
                evaluatePolicies, saveToLacework, disableLibraryPackageScanning, showEvaluationExceptions, tags);
        build.addAction(new LaceworkScannerAction(build, artifactSuffix, artifactName));

        System.out.println("exitCode: " + exitCode);
        String failedMessage = "Scanning failed.";
        archiveArtifacts(build, workspace, env, launcher, listener);
        switch (exitCode) {
            case OK_CODE:
                System.out.println("Scanning success.");
                break;
            default:
                // This exception causes the message to appear in the Jenkins console
                throw new AbortException(failedMessage);
        }
    }

    // Archive scan artifact
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE") // No idea why this is needed
    private void archiveArtifacts(Run<?, ?> build, FilePath workspace, EnvVars env, Launcher launcher,
            TaskListener listener) throws java.lang.InterruptedException {
        try {
            ArtifactArchiver artifactArchiver = new ArtifactArchiver("laceworkscan*");
            artifactArchiver.perform(build, workspace, env, launcher, listener);
        } catch (Exception e) {
            throw new InterruptedException(
                    "Failed to setup build results due to an unexpected error. Please refer to above logs for more information");
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link LaceworkScannerBuilder}. Used as a singleton. The class
     * is marked as public so that it can be accessed from views.
     */
    @Symbol("lacework")
    @Extension // This indicates to Jenkins that this is an implementation of an extension
               // point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information, simply store it in a field and
         * call save().
         */
        private String laceworkAccountName;
        private Secret laceworkAccessToken;

        /**
         * In order to load the persisted global configuration, you have to call load()
         * in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Lacework Security";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            laceworkAccountName = formData.getString("laceworkAccountName");
            laceworkAccessToken = Secret.fromString(formData.getString("laceworkAccessToken"));
            save();
            return super.configure(req, formData);
        }

        public String getLaceworkAccountName() {
            return laceworkAccountName;
        }

        public Secret getLaceworkAccessToken() {
            return laceworkAccessToken;
        }
    }
}
