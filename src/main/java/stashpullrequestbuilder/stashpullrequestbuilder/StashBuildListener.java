package stashpullrequestbuilder.stashpullrequestbuilder;

import static java.lang.String.format;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.model.ParameterizedJobMixIn;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashApiClient.StashApiException;

/** Created by Nathan McCarthy */
@Extension
public class StashBuildListener extends RunListener<Run<?, ?>> {
  private static final Logger logger =
      Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

  @Override
  public void onStarted(Run<?, ?> run, TaskListener listener) {
    logger.info("BuildListener onStarted called.");

    StashCause cause = run.getCause(StashCause.class);
    if (cause == null) {
      return;
    }

    try {
      run.setDescription(cause.getShortDescription());
    } catch (IOException e) {
      PrintStream buildLogger = listener.getLogger();
      buildLogger.println("Can't update build description");
      e.printStackTrace(buildLogger);
    }
  }

  @Override
  public void onCompleted(Run<?, ?> run, @Nonnull TaskListener listener) {
    PrintStream buildLogger = listener.getLogger();

    StashCause cause = run.getCause(StashCause.class);
    if (cause == null) {
      return;
    }

    StashBuildTrigger trigger =
        ParameterizedJobMixIn.getTrigger(run.getParent(), StashBuildTrigger.class);
    if (trigger == null) {
      return;
    }

    StashRepository repository = trigger.getRepository();
    Result result = run.getResult();
    // Note: current code should no longer use "new JenkinsLocationConfiguration()"
    // as only one instance per runtime is really supported by the current core.
    JenkinsLocationConfiguration globalConfig = JenkinsLocationConfiguration.get();
    String rootUrl = globalConfig == null ? null : globalConfig.getUrl();
    String buildUrl = "";
    if (rootUrl == null) {
      buildUrl = " PLEASE SET JENKINS ROOT URL FROM GLOBAL CONFIGURATION " + run.getUrl();
    } else {
      buildUrl = rootUrl + run.getUrl();
    }

    // Delete the "Build Started" comment, as it gets replaced with a comment
    // about the build result. Failure to delete the comment is not fatal, it's
    // reported to the build log.
    try {
      repository.deletePullRequestComment(cause.getPullRequestId(), cause.getBuildStartCommentId());
    } catch (StashApiException e) {
      buildLogger.println(
          format(
              "%s: cannot delete Build Start comment for pull request %s",
              run.getParent().getDisplayName(), cause.getPullRequestId()));
      e.printStackTrace(buildLogger);
    }

    String additionalComment = "";
    StashPostBuildComment comments = null;

    if (run instanceof AbstractBuild) {
      AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) run;
      comments = build.getProject().getPublishersList().get(StashPostBuildComment.class);
    }

    if (comments != null) {
      String buildComment =
          result == Result.SUCCESS
              ? comments.getBuildSuccessfulComment()
              : comments.getBuildFailedComment();

      if (buildComment != null && !buildComment.isEmpty()) {
        String expandedComment;
        try {
          expandedComment = Util.fixEmptyAndTrim(run.getEnvironment(listener).expand(buildComment));
        } catch (IOException | InterruptedException e) {
          expandedComment = "Exception while expanding '" + buildComment + "': " + e;
        }

        additionalComment = "\n\n" + expandedComment;
      }
    }
    String duration = run.getDurationString();
    repository.postFinishedComment(
        cause.getPullRequestId(),
        cause.getSourceCommitHash(),
        cause.getDestinationCommitHash(),
        result,
        buildUrl,
        run.getNumber(),
        additionalComment,
        duration);

    // Request the server to merge the pull request on success if that option is
    // enabled. Log the result to the build log. Handle exceptions here, report
    // them to the build log as well.
    if (trigger.getMergeOnSuccess() && run.getResult() == Result.SUCCESS) {
      try {
        boolean mergeStat =
            repository.mergePullRequest(cause.getPullRequestId(), cause.getPullRequestVersion());
        if (mergeStat == true) {
          buildLogger.println(
              format(
                  "Successfully merged pull request %s (%s) to branch %s",
                  cause.getPullRequestId(), cause.getSourceBranch(), cause.getTargetBranch()));
        } else {
          buildLogger.println(
              format(
                  "Failed to merge pull request %s (%s) to branch %s, it may be out of date",
                  cause.getPullRequestId(), cause.getSourceBranch(), cause.getTargetBranch()));
        }
      } catch (StashApiException e) {
        buildLogger.println(
            format(
                "Failed to merge pull request %s (%s) to branch %s",
                cause.getPullRequestId(), cause.getSourceBranch(), cause.getTargetBranch()));
        e.printStackTrace(buildLogger);
      }
    }
  }
}
