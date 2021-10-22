package io.jenkins.plugins.laceworkscannerbuildstep;

import hudson.model.Action;
import hudson.model.Run;

public class LaceworkScannerAction implements Action {

    private String resultsUrl;
    private Run<?, ?> build;
    private String artifactSuffix;

    public LaceworkScannerAction(Run<?, ?> build, String artifactSuffix, String artifactName) {
        this.build = build;
        this.artifactSuffix = artifactSuffix;
        this.resultsUrl = "../artifact/" + artifactName;
    }

    @Override
    public String getIconFileName() {
        // return the path to the icon file
        return "/plugin/lacework-security-scanner/images/lacework.png";
    }

    @Override
    public String getDisplayName() {
        // return the label for your link
        if (artifactSuffix == null) {
            return "Lacework Security Scanner";
        } else {
            return "Lacework Security Scanner " + artifactSuffix;
        }
    }

    @Override
    public String getUrlName() {
        // defines the suburl, which is appended to ...jenkins/job/jobname
        if (artifactSuffix == null) {
            return "lacework-results";
        } else {
            return "lacework-results-" + artifactSuffix;
        }
    }

    public Run<?, ?> getBuild() {
        return this.build;
    }

    public String getResultsUrl() {
        return this.resultsUrl;
    }

}
