package io.jenkins.plugins.laceworkscannerbuildstep;

import hudson.*;
import hudson.Launcher.ProcStarter;
import hudson.util.ArgumentListBuilder;
import java.io.File;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;

/**
 * This class does the actual lw-scanner execution.
 *
 * @author Alan Nix
 */
public class LaceworkScannerExecuter {

    public static int execute(Run<?, ?> build, FilePath workspace, EnvVars env, Launcher launcher,
            TaskListener listener, String laceworkAccountName, Secret laceworkAccessToken, String customFlags,
            boolean fixableOnly, String imageName, String imageTag, String outputFile, boolean noPull,
            boolean evaluatePolicies, boolean saveToLacework, boolean disableLibraryPackageScanning,
            boolean showEvaluationExceptions, String tags) {

        PrintStream print_stream = null;
        try {
            // Check to see if environment variables were provided as imageName/imageTag
            imageName = env.expand(imageName);
            imageTag = env.expand(imageTag);

            ArgumentListBuilder args = new ArgumentListBuilder();
            args.add("docker", "run");

            String inlineScannerRepo = "lacework/lacework-inline-scanner";
            String inlineScannerTag = "0.2.13";
            if (env.get("LW_INLINE_SCANNER_VERSION") != null) {
                inlineScannerTag = env.get("LW_INLINE_SCANNER_VERSION");
            }
            String inlineScannerContainer = String.format("%s:%s", inlineScannerRepo, inlineScannerTag);

            args.add("--rm", "-v", "/var/run/docker.sock:/var/run/docker.sock");
            args.add(inlineScannerContainer, "image", "evaluate", imageName, imageTag);

            // Use environment variables for Lacework auth, if they exist
            // This allows for override in a specific pipeline
            args.add("--account-name");
            if (env.get("LW_ACCOUNT_NAME") == null) {
                args.add(laceworkAccountName);
            } else {
                args.add(env.get("LW_ACCOUNT_NAME"));
            }

            args.add("--access-token");
            if (env.get("LW_ACCESS_TOKEN") == null) {
                args.addMasked(laceworkAccessToken);
            } else {
                args.addMasked(env.get("LW_ACCESS_TOKEN"));
            }

            String buildId = env.get("BUILD_ID");
            String buildName = env.get("JOB_NAME").trim();
            buildName = buildName.replaceAll("\\s+", "");

            args.add("--build-id", buildId);
            args.add("--build-plan", buildName);

            File htmlFile = new File(build.getRootDir(), outputFile);
            // args.add("--html");
            // args.add("--html-file", htmlFile.getAbsolutePath());

            if (fixableOnly) {
                args.add("--fixable");
            }

            if (noPull) {
                args.add("--no-pull");
            }

            if (evaluatePolicies) {
                args.add("--policy", "--fail-on-violation-exit-code", "1");
            }

            if (saveToLacework) {
                args.add("--save");
            }

            if (disableLibraryPackageScanning) {
                args.add("--disable-library-package-scanning");
            }

            if (showEvaluationExceptions) {
                args.add("--exceptions");
            }

            if (tags != null && !tags.equals("")) {
                args.add("--tags", tags);
            }

            if (customFlags != null && !customFlags.equals("")) {
                args.addTokenized(customFlags);
            }

            args.add("-q");

            ProcStarter ps = launcher.launch();
            ps.cmds(args);
            ps.stdin(null);
            print_stream = new PrintStream(htmlFile, "UTF-8");
            ps.stderr(print_stream);
            ps.stdout(print_stream);
            ps.quiet(true);
            listener.getLogger().println(args.toString());
            int exitCode = ps.join(); // RUN !

            // Text
            FilePath htmlFilePath = new FilePath(htmlFile);
            FilePath htmlTarget = new FilePath(workspace, outputFile);
            htmlFilePath.copyTo(htmlTarget);

            // HTML
            // FilePath htmlFilePath = new FilePath(htmlFile);
            // FilePath htmlTarget = new FilePath(workspace, outputHtmlName);
            // htmlFilePath.copyTo(htmlTarget);

            // String htmlOutput = htmlFilePath.readToString();
            // cleanHtmlOutput(htmlOutput, htmlTarget, listener);

            return exitCode;

        } catch (RuntimeException e) {
            listener.getLogger().println("RuntimeException:" + e.toString());
            return -1;
        } catch (Exception e) {
            listener.getLogger().println("Exception:" + e.toString());
            return -1;
        } finally {
            if (print_stream != null) {
                print_stream.close();
            }
        }
    }

    private static void generateCssFile(String scanOutput, FilePath target, TaskListener listener) {

        StringBuilder cssContent = new StringBuilder("");

        Pattern pattern = Pattern.compile("(?s)<style>(.*?)</style>");
        Matcher matcher = pattern.matcher(scanOutput);

        while (matcher.find()) {
            cssContent.append(matcher.group(1) + "\n");
        }

        try {
            target.write(cssContent.toString(), "UTF-8");
        } catch (Exception e) {
            listener.getLogger().println("Failed to save CSS file.");
        }
    }

    // Clean the inline CSS from HTML
    private static boolean cleanHtmlOutput(String scanOutput, FilePath target, TaskListener listener) {

        int htmlStart = scanOutput.indexOf("<!DOCTYPE html>");
        if (htmlStart == -1) {
            listener.getLogger().println(scanOutput);
            return false;
        }
        listener.getLogger().println(scanOutput.substring(0, htmlStart));
        int htmlEnd = scanOutput.lastIndexOf("</html>") + 7;

        // Remove <style> & <script> tags (Jenkins CSP doesn't allow)
        scanOutput = scanOutput.substring(htmlStart, htmlEnd);

        String styleRegex = "(?s)<style.*?(/>|</style>)";
        scanOutput = scanOutput.replaceAll(styleRegex, "");

        // String scriptRegex = "(?s)<script.*?(/>|</script>)";
        // scanOutput = scanOutput.replaceAll(scriptRegex, "");

        int headEnd = scanOutput.lastIndexOf("</head>");
        scanOutput = insert(scanOutput, "<link rel=\"stylesheet\" type=\"text/css\" href=\"laceworkstyles.css\">",
                headEnd);
        try {
            target.write(scanOutput, "UTF-8");
        } catch (Exception e) {
            listener.getLogger().println("Failed to save HTML report.");
        }

        return true;
    }

    private static String insert(String orginalStr, String insertStr, int index) {
        String orginalStrBegin = orginalStr.substring(0, index);
        String orginalStrEnd = orginalStr.substring(index);
        return orginalStrBegin + insertStr + orginalStrEnd;
    }
}