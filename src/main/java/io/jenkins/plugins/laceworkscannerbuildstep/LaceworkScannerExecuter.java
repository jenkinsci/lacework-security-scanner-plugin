package io.jenkins.plugins.laceworkscannerbuildstep;

import hudson.*;
import hudson.Launcher.ProcStarter;
import hudson.util.ArgumentListBuilder;
import java.io.File;
import java.io.PrintStream;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import jenkins.model.Jenkins;

/**
 * This class does the actual lw-scanner execution.
 *
 * @author Alan Nix
 */
public class LaceworkScannerExecuter {

    public static int execute(Run<?, ?> build, FilePath workspace, EnvVars env, Launcher launcher,
            TaskListener listener, String laceworkAccountName, Secret laceworkAccessToken, String customFlags,
            boolean fixableOnly, String imageName, String imageTag, String outputHtmlName, boolean noPull,
            boolean evaluatePolicies, boolean saveToLacework, boolean scanLibraryPackages, String tags) {

        PrintStream print_stream = null;
        try {
            ArgumentListBuilder args = new ArgumentListBuilder();

            String buildId = env.get("BUILD_ID");
            String buildName = env.get("JOB_NAME").trim();
            buildName = buildName.replaceAll("\\s+", "");

            File htmlFile = new File(build.getRootDir(), outputHtmlName);

            args.add("lw-scanner", "image", "evaluate", imageName, imageTag);

            // Add Lacework authentication
            args.add("--account-name", laceworkAccountName, "--access-token");
            args.addMasked(laceworkAccessToken);

            args.add("--build-id", buildId);
            args.add("--build-plan", buildName);

            args.add("--html");
            args.add("--html-file", htmlFile.getAbsolutePath());

            if (fixableOnly) {
                args.add("--fixable");
            }

            if (noPull) {
                args.add("--no-pull");
            }

            if (evaluatePolicies) {
                args.add("--policy");
            }

            if (saveToLacework) {
                args.add("--save");
            }

            if (scanLibraryPackages) {
                args.add("--scan-library-packages");
            }

            if (tags != null && !tags.equals("")) {
                args.add("--tags", tags);
            }

            if (customFlags != null && !customFlags.equals("")) {
                args.addTokenized(customFlags);
            }

            File outFile = new File(build.getRootDir(), "output");
            ProcStarter ps = launcher.launch();
            ps.cmds(args);
            ps.stdin(null);
            print_stream = new PrintStream(outFile, "UTF-8");
            ps.stderr(print_stream);
            ps.stdout(print_stream);
            ps.quiet(true);
            listener.getLogger().println(args.toString());
            int exitCode = ps.join(); // RUN !

            // CSS
            // File cssFile;
            // FilePath cssTarget = new FilePath(workspace, "styles.css");

            // if (Jenkins.get().getPluginManager().getWorkDir() != null)
            // cssFile = new File(Jenkins.get().getPluginManager().getWorkDir() +
            // "/lacework-security-scanner/css/",
            // "styles.css");
            // else
            // cssFile = new File(
            // Jenkins.get().getPlugin("lacework-security-scanner").getWrapper().baseResourceURL.getFile(),
            // "css/styles.css");
            // FilePath cssFilePath = new FilePath(cssFile);
            // cssFilePath.copyTo(cssTarget);

            // HTML
            FilePath htmlFilePath = new FilePath(htmlFile);
            FilePath htmlTarget = new FilePath(workspace, outputHtmlName);
            htmlFilePath.copyTo(htmlTarget);

            // String htmlOutput = htmlTarget.readToString();
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

    // Read output save HTML and print stderr
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
        String scanRegex = "(?s)<script.*?(/>|</script>)";
        scanOutput = scanOutput.replaceAll(scanRegex, "");
        scanRegex = "(?s)<style.*?(/>|</style>)";
        scanOutput = scanOutput.replaceAll(scanRegex, "");

        int headEnd = scanOutput.lastIndexOf("</head>");
        scanOutput = insert(scanOutput, "<link rel=\"stylesheet\" type=\"text/css\" href=\"styles.css\">", headEnd);
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