package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.JenkinsLocationConfiguration;

/** Created by Nathan McCarthy */
public class StashBuilds {
  private static final Logger logger = Logger.getLogger(StashBuilds.class.getName());
  private StashBuildTrigger trigger;
  private StashRepository repository;

  private static final String APPROVED = "APPROVED";
  private static final String NEEDS_WORK = "NEEDS_WORK";

  public StashBuilds(StashBuildTrigger trigger, StashRepository repository) {
    this.trigger = trigger;
    this.repository = repository;
  }

  public StashCause getCause(AbstractBuild build) {
    Cause cause = build.getCause(StashCause.class);
    if (cause == null || !(cause instanceof StashCause)) {
      return null;
    }
    return (StashCause) cause;
  }

  public void onStarted(AbstractBuild build) {
    StashCause cause = this.getCause(build);
    if (cause == null) {
      return;
    }
    try {
      build.setDescription(cause.getShortDescription());
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Can't update build description", e);
    }
  }

  public void onCompleted(AbstractBuild build, TaskListener listener) {
    StashCause cause = this.getCause(build);
    if (cause == null) {
      return;
    }
    Result result = build.getResult();
    // Note: current code should no longer use "new JenkinsLocationConfiguration()"
    // as only one instance per runtime is really supported by the current core.
    JenkinsLocationConfiguration globalConfig = JenkinsLocationConfiguration.get();
    String rootUrl = globalConfig == null ? null : globalConfig.getUrl();
    String buildUrl = "";
    if (rootUrl == null) {
      buildUrl = " PLEASE SET JENKINS ROOT URL FROM GLOBAL CONFIGURATION " + build.getUrl();
    } else {
      buildUrl = rootUrl + build.getUrl();
    }
    repository.deletePullRequestComment(cause.getPullRequestId(), cause.getBuildStartCommentId());

    String additionalComment = "";

    StashPostBuildCommentAction comments = build.getAction(StashPostBuildCommentAction.class);
    if (comments != null) {
      String buildComment =
          result == Result.SUCCESS
              ? comments.getBuildSuccessfulComment()
              : comments.getBuildFailedComment();

      if (buildComment != null && !buildComment.isEmpty()) {
        additionalComment = "\n\n" + buildComment;
      }
    }
    String duration = build.getDurationString();
    repository.postFinishedComment(
        cause.getPullRequestId(),
        cause.getSourceCommitHash(),
        cause.getDestinationCommitHash(),
        result,
        buildUrl,
        build.getNumber(),
        additionalComment,
        duration);

    // Mark PR as Approved or Needs Work
    if (trigger.isApproveOnBuildSuccessful() && build.getResult() == Result.SUCCESS) {
      repository.markStatus(cause.getPullRequestId(), StashBuilds.APPROVED);
    }

    if (trigger.isNeedsWorkOnBuildFailure() && result == Result.FAILURE) {
      repository.markStatus(cause.getPullRequestId(), StashBuilds.NEEDS_WORK);
    }

    // Merge PR
    StashBuildTrigger trig = StashBuildTrigger.getTrigger(build.getProject());
    if (trig.getMergeOnSuccess() && build.getResult() == Result.SUCCESS) {
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
