package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.model.ParameterizedJobMixIn;

/** Created by Nathan McCarthy */
@Extension
public class StashBuildListener extends RunListener<AbstractBuild<?, ?>> {
  private static final Logger logger =
      Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

  @Override
  public void onStarted(AbstractBuild<?, ?> abstractBuild, TaskListener listener) {
    logger.info("BuildListener onStarted called.");

    StashCause cause = abstractBuild.getCause(StashCause.class);
    if (cause == null) {
      return;
    }

    try {
      abstractBuild.setDescription(cause.getShortDescription());
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Can't update build description", e);
    }
  }

  @Override
  public void onCompleted(AbstractBuild<?, ?> abstractBuild, @Nonnull TaskListener listener) {
    StashCause cause = abstractBuild.getCause(StashCause.class);
    if (cause == null) {
      return;
    }

    StashBuildTrigger trigger =
        ParameterizedJobMixIn.getTrigger(abstractBuild.getParent(), StashBuildTrigger.class);
    if (trigger == null) {
      return;
    }

    StashRepository repository = trigger.getBuilder().getRepository();
    Result result = abstractBuild.getResult();
    // Note: current code should no longer use "new JenkinsLocationConfiguration()"
    // as only one instance per runtime is really supported by the current core.
    JenkinsLocationConfiguration globalConfig = JenkinsLocationConfiguration.get();
    String rootUrl = globalConfig == null ? null : globalConfig.getUrl();
    String buildUrl = "";
    if (rootUrl == null) {
      buildUrl = " PLEASE SET JENKINS ROOT URL FROM GLOBAL CONFIGURATION " + abstractBuild.getUrl();
    } else {
      buildUrl = rootUrl + abstractBuild.getUrl();
    }
    repository.deletePullRequestComment(cause.getPullRequestId(), cause.getBuildStartCommentId());

    String additionalComment = "";

    StashPostBuildComment comments =
        abstractBuild.getProject().getPublishersList().get(StashPostBuildComment.class);

    if (comments != null) {
      String buildComment =
          result == Result.SUCCESS
              ? comments.getBuildSuccessfulComment()
              : comments.getBuildFailedComment();

      if (buildComment != null && !buildComment.isEmpty()) {
        String expandedComment;
        try {
          expandedComment =
              Util.fixEmptyAndTrim(abstractBuild.getEnvironment(listener).expand(buildComment));
        } catch (IOException | InterruptedException e) {
          expandedComment = "Exception while expanding '" + buildComment + "': " + e;
        }

        additionalComment = "\n\n" + expandedComment;
      }
    }
    String duration = abstractBuild.getDurationString();
    repository.postFinishedComment(
        cause.getPullRequestId(),
        cause.getSourceCommitHash(),
        cause.getDestinationCommitHash(),
        result,
        buildUrl,
        abstractBuild.getNumber(),
        additionalComment,
        duration);

    // Merge PR
    if (trigger.getMergeOnSuccess() && abstractBuild.getResult() == Result.SUCCESS) {
      boolean mergeStat =
          repository.mergePullRequest(cause.getPullRequestId(), cause.getPullRequestVersion());
      if (mergeStat == true) {
        String logmsg =
            "Merged pull request "
                + cause.getPullRequestId()
                + "("
                + cause.getSourceBranch()
                + ") to branch "
                + cause.getTargetBranch();
        logger.log(Level.INFO, logmsg);
        listener.getLogger().println(logmsg);
      } else {
        String logmsg =
            "Failed to merge pull request "
                + cause.getPullRequestId()
                + "("
                + cause.getSourceBranch()
                + ") to branch "
                + cause.getTargetBranch()
                + " because it's out of date";
        logger.log(Level.INFO, logmsg);
        listener.getLogger().println(logmsg);
      }
    }
  }
}
