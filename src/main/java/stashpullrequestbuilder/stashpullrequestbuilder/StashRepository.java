package stashpullrequestbuilder.stashpullrequestbuilder;

import static java.lang.String.format;

import hudson.Util;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.model.ParameterizedJobMixIn.ParameterizedJob;
import org.apache.commons.lang.StringUtils;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashApiClient;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashApiClient.StashApiException;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestBuildTarget;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestComment;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestMergeableResponse;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValue;

/** Created by Nathan McCarthy */
public class StashRepository {
  private static final Logger logger =
      Logger.getLogger(MethodHandles.lookup().lookupClass().getName());
  private static final String BUILD_START_MESSAGE = "BuildStarted";
  private static final String BUILD_FINISH_MESSAGE = "BuildFinished";
  private static final String BUILD_CANCEL_MESSAGE = "BuildCanceled";
  private static final String[] BUILD_STATUSES = {
    BUILD_START_MESSAGE, BUILD_FINISH_MESSAGE, BUILD_CANCEL_MESSAGE
  };
  private static final String BUILD_MARKER = "[*%s* **%s**] %s into %s";

  private static final String BUILD_STATUS_REGEX =
      "\\[\\*%s\\* \\*\\*%s\\*\\*\\] ([0-9a-fA-F]+) into ([0-9a-fA-F]+)";

  private static final String BUILD_FINISH_SENTENCE =
      " %n%n **[%s](%s)** - Build *&#x0023;%d* which took *%s*";

  private static final String BUILD_SUCCESS_COMMENT = "✓ BUILD SUCCESS";
  private static final String BUILD_FAILURE_COMMENT = "✕ BUILD FAILURE";
  private static final String BUILD_UNSTABLE_COMMENT = "⁉ BUILD UNSTABLE";
  private static final String BUILD_ABORTED_COMMENT = "‼ BUILD ABORTED";
  private static final String BUILD_NOTBUILT_COMMENT = "✕ BUILD INCOMPLETE";

  private static final String ADDITIONAL_PARAMETER_REGEX = "^p:(([A-Za-z_0-9])+)=(.*)";
  private static final Pattern ADDITIONAL_PARAMETER_REGEX_PATTERN =
      Pattern.compile(ADDITIONAL_PARAMETER_REGEX);

  private static final DateTimeFormatter TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

  private Job<?, ?> job;
  private StashBuildTrigger trigger;
  private StashApiClient client;
  private StashPollingAction pollLog;

  public StashRepository(
      @Nonnull Job<?, ?> job,
      @Nonnull StashBuildTrigger trigger,
      StashApiClient client,
      StashPollingAction pollLog) {
    this.job = job;
    this.trigger = trigger;
    this.client = client;
    this.pollLog = pollLog;
  }

  public Collection<StashPullRequestResponseValue> getTargetPullRequests() {
    // Fetch "OPEN" pull requests from the server. Failure to get the list will
    // prevent builds from being scheduled. However, the call will be retried
    // during the next cycle, as determined by the cron settings.
    try {
      return client.getPullRequests();
    } catch (StashApiException e) {
      pollLog.log("Cannot fetch pull request list", e);
      logger.log(Level.INFO, format("%s: cannot fetch pull request list", job.getFullName()), e);
      return new ArrayList<>();
    }
  }

  private boolean shouldSkip(StashPullRequestResponseValue pullRequest) {
    if (!"OPEN".equals(pullRequest.getState())) {
      return true;
    }

    if (isSkipBuild(pullRequest.getTitle())) {
      pollLog.log("Not building PR #{}, its title contains the skip phrase", pullRequest.getId());
      return true;
    }

    if (!isForTargetBranch(pullRequest)) {
      pollLog.log(
          "Not building PR #{} as it targets branch {}",
          pullRequest.getId(),
          pullRequest.getToRef().getBranch().getName());
      return true;
    }

    // Check whether the pull request can be merged and whether it's in the
    // "conflicted" state. If that information cannot be retrieved, don't build
    // the pull request in this cycle.
    try {
      if (!isPullRequestMergeable(pullRequest)) {
        pollLog.log("Not building PR #{} as it cannot be merged", pullRequest.getId());
        return true;
      }
    } catch (StashApiException e) {
      pollLog.log("Cannot determine if PR #{} can be merged, not building", pullRequest.getId(), e);
      logger.log(
          Level.INFO,
          format(
              "%s: cannot determine if pull request %s can be merged, skipping",
              job.getFullName(), pullRequest.getId()),
          e);
      return true;
    }

    return false;
  }

  private boolean isStatusMessage(String content) {
    String escapedBuildName = Pattern.quote(job.getDisplayName());
    for (String buildStatus : BUILD_STATUSES) {
      String buildStatusMessage = String.format(BUILD_STATUS_REGEX, buildStatus, escapedBuildName);
      Matcher matcher =
          Pattern.compile(buildStatusMessage, Pattern.CASE_INSENSITIVE).matcher(content);
      if (matcher.find()) {
        return true;
      }
    }
    return false;
  }

  protected List<StashPullRequestBuildTarget> getBuildTargets(
      StashPullRequestResponseValue pullRequest) {
    if (shouldSkip(pullRequest)) {
      return new ArrayList<>();
    }

    String owner = pullRequest.getToRef().getRepository().getProjectName();
    String repositoryName = pullRequest.getToRef().getRepository().getRepositoryName();

    String id = pullRequest.getId();

    // Fetch all comments for the pull request. If it fails, don't build the
    // pull request in this cycle, as it cannot be determined if it should be
    // built without checking the comments.
    List<StashPullRequestComment> comments;
    try {
      comments = client.getPullRequestComments(owner, repositoryName, id);
    } catch (StashApiException e) {
      pollLog.log("Cannot read comments for PR #{}, not building", pullRequest.getId(), e);
      logger.log(Level.INFO, format("%s: cannot read pull request comments", job.getFullName()), e);
      return new ArrayList<>();
    }

    boolean isOnlyBuildOnComment = trigger.getOnlyBuildOnComment();
    if (!isOnlyBuildOnComment) {
      return getBuildTargetsWithoutOnlyBuildOnCommentLogic(pullRequest, comments);
    }
    List<StashPullRequestBuildTarget> buildTargets =
        getBuildTargetsWithOnlyBuildOnCommentLogic(pullRequest, comments);

    if (!trigger.getCancelOutdatedJobsEnabled()) {
      return buildTargets;
    }
    buildTargets.sort(
        Comparator.comparing(StashPullRequestBuildTarget::getBuildCommandCommentId).reversed());
    List<StashPullRequestBuildTarget> buildTargetsToKeep =
        buildTargets.stream().limit(1).collect(Collectors.toList());
    List<StashPullRequestBuildTarget> buildTargetsToCancel =
        buildTargets.stream().skip(1).collect(Collectors.toList());
    try {
      for (StashPullRequestBuildTarget buildTarget : buildTargetsToCancel) {
        postBuildCancelComment(pullRequest, buildTarget.getBuildCommandCommentId());
      }
    } catch (StashApiException e) {
      pollLog.log(
          "Cannot post \\\"BuildCanceled\\\" comment for PR #{}, not building",
          pullRequest.getId(),
          e);
      logger.log(
          Level.INFO,
          format(
              "%s: cannot post Build Cancel comment for pull request %s, not building",
              job.getFullName(), pullRequest.getId()),
          e);
      return new ArrayList<>();
    }
    return buildTargetsToKeep;
  }

  private List<StashPullRequestBuildTarget> getBuildTargetsWithOnlyBuildOnCommentLogic(
      StashPullRequestResponseValue pullRequest, List<StashPullRequestComment> comments) {
    List<StashPullRequestBuildTarget> buildTargets = new ArrayList<>();

    // Start with least recent comments
    comments.sort(Comparator.naturalOrder());

    for (StashPullRequestComment comment : comments) {
      String content = comment.getText();
      if (StringUtils.isEmpty(content)) {
        continue;
      }

      if (isPhrasesContain(content, this.trigger.getCiBuildPhrases())) {
        if (comment.getReplies() != null
            && !comment.getReplies().isEmpty()
            && comment.getReplies().stream().anyMatch(reply -> isStatusMessage(reply.getText()))) {
          continue;
        }

        buildTargets.add(
            new StashPullRequestBuildTarget(
                pullRequest, getAdditionalParameters(comment), comment.getCommentId()));
      }
    }
    return buildTargets;
  }

  private List<StashPullRequestBuildTarget> getBuildTargetsWithoutOnlyBuildOnCommentLogic(
      StashPullRequestResponseValue pullRequest, List<StashPullRequestComment> comments) {
    String sourceCommit = pullRequest.getFromRef().getLatestCommit();
    String destinationCommit = pullRequest.getToRef().getLatestCommit();

    // Start with most recent comments
    comments.sort(Comparator.reverseOrder());

    for (StashPullRequestComment comment : comments) {
      String content = comment.getText();
      if (StringUtils.isEmpty(content)) {
        continue;
      }

      // These will match any start or finish message -- need to check commits
      String escapedBuildName = Pattern.quote(job.getDisplayName());
      String project_build_start =
          String.format(BUILD_STATUS_REGEX, BUILD_START_MESSAGE, escapedBuildName);
      String project_build_finished =
          String.format(BUILD_STATUS_REGEX, BUILD_FINISH_MESSAGE, escapedBuildName);
      Matcher startMatcher =
          Pattern.compile(project_build_start, Pattern.CASE_INSENSITIVE).matcher(content);
      Matcher finishMatcher =
          Pattern.compile(project_build_finished, Pattern.CASE_INSENSITIVE).matcher(content);

      if (startMatcher.find() || finishMatcher.find()) {
        String sourceCommitMatch;
        String destinationCommitMatch;

        if (startMatcher.find(0)) {
          sourceCommitMatch = startMatcher.group(1);
          destinationCommitMatch = startMatcher.group(2);
        } else {
          sourceCommitMatch = finishMatcher.group(1);
          destinationCommitMatch = finishMatcher.group(2);
        }

        // first check source commit -- if it doesn't match, just move on. If it does,
        // investigate further.
        if (sourceCommitMatch.equalsIgnoreCase(sourceCommit)) {
          // if we're checking destination commits, and if this doesn't match, then move on.
          if (this.trigger.getCheckDestinationCommit()
              && (!destinationCommitMatch.equalsIgnoreCase(destinationCommit))) {
            continue;
          }
          return new ArrayList<>();
        }
      }
      if (isSkipBuild(content)) {
        return new ArrayList<>();
      }
      if (isPhrasesContain(content, this.trigger.getCiBuildPhrases())) {
        return Collections.singletonList(
            new StashPullRequestBuildTarget(pullRequest, getAdditionalParameters(comment)));
      }
    }
    return Collections.singletonList(new StashPullRequestBuildTarget(pullRequest));
  }

  /**
   * Post "BuildStarted" comment to Bitbucket Server
   *
   * @param pullRequest pull request
   * @return comment ID
   * @throws StashApiException if posting the comment fails
   */
  private String postBuildStartComment(
      StashPullRequestResponseValue pullRequest, Integer buildCommandCommentId)
      throws StashApiException {
    return postBuildStatusComment(pullRequest, buildCommandCommentId, BUILD_START_MESSAGE);
  }

  /**
   * Post "BuildCanceled" comment to Bitbucket Server
   *
   * @param pullRequest pull request
   * @return comment ID
   * @throws StashApiException if posting the comment fails
   */
  private String postBuildCancelComment(
      StashPullRequestResponseValue pullRequest, Integer buildCommandCommentId)
      throws StashApiException {
    return postBuildStatusComment(pullRequest, buildCommandCommentId, BUILD_CANCEL_MESSAGE);
  }

  private String postBuildStatusComment(
      StashPullRequestResponseValue pullRequest, Integer buildCommandCommentId, String buildMessage)
      throws StashApiException {
    String sourceCommit = pullRequest.getFromRef().getLatestCommit();
    String destinationCommit = pullRequest.getToRef().getLatestCommit();
    String comment =
        format(BUILD_MARKER, buildMessage, job.getDisplayName(), sourceCommit, destinationCommit);
    StashPullRequestComment commentResponse;
    commentResponse =
        this.client.postPullRequestComment(pullRequest.getId(), comment, buildCommandCommentId);
    return commentResponse.getCommentId().toString();
  }

  @Nullable
  public static AbstractMap.SimpleEntry<String, String> getParameter(String content) {
    if (content.isEmpty()) {
      return null;
    }
    Matcher parameterMatcher = ADDITIONAL_PARAMETER_REGEX_PATTERN.matcher(content);
    if (parameterMatcher.find(0)) {
      String parameterName = parameterMatcher.group(1);
      String parameterValue = parameterMatcher.group(3);
      return new AbstractMap.SimpleEntry<>(parameterName, parameterValue);
    }
    return null;
  }

  static Map<String, String> getParametersFromContent(String content) {
    Map<String, String> result = new TreeMap<>();
    String[] lines = content.split("\\r?\\n|\\r");
    for (String line : lines) {
      AbstractMap.SimpleEntry<String, String> parameter = getParameter(line);
      if (parameter != null) {
        result.put(parameter.getKey(), parameter.getValue());
      }
    }

    return result;
  }

  Map<String, String> getAdditionalParameters(StashPullRequestComment comment) {
    if (comment != null) {
      return getParametersFromContent(comment.getText());
    } else {
      return new TreeMap<>();
    }
  }

  private boolean hasCauseFromTheSamePullRequest(
      @Nullable List<Cause> causes, @Nullable StashCause pullRequestCause) {
    if (causes != null && pullRequestCause != null) {
      for (Cause cause : causes) {
        if (cause instanceof StashCause) {
          StashCause sc = (StashCause) cause;
          if (StringUtils.equals(sc.getPullRequestId(), pullRequestCause.getPullRequestId())
              && StringUtils.equals(
                  sc.getSourceRepositoryName(), pullRequestCause.getSourceRepositoryName())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private void cancelPreviousJobsInQueueThatMatch(@Nonnull StashCause stashCause) {
    logger.fine("Looking for queued jobs that match PR ID: " + stashCause.getPullRequestId());
    Queue queue = Jenkins.getInstance().getQueue();

    // Cast is safe due to StashBuildTrigger#isApplicable() check
    for (Queue.Item item : queue.getItems((ParameterizedJob) job)) {
      if (hasCauseFromTheSamePullRequest(item.getCauses(), stashCause)) {
        logger.info(format("%s: canceling item in queue: %s", job.getFullName(), item));
        queue.cancel(item);
      }
    }
  }

  private void abortRunningJobsThatMatch(@Nonnull StashCause stashCause) {
    logger.fine("Looking for running jobs that match PR ID: " + stashCause.getPullRequestId());
    for (Run<?, ?> run : job.getBuilds()) {
      if (run.isBuilding() && hasCauseFromTheSamePullRequest(run.getCauses(), stashCause)) {
        logger.info("Aborting build: " + run.getId() + " since PR is outdated");
        Executor executor = run.getExecutor();
        if (executor != null) {
          executor.interrupt(Result.ABORTED);
        }
      }
    }
  }

  private List<ParameterValue> getParameters(StashCause cause) {
    List<ParameterValue> values = new ArrayList<>();

    ParametersDefinitionProperty definitionProperty =
        this.job.getProperty(ParametersDefinitionProperty.class);

    if (definitionProperty == null) {
      return values;
    }

    Map<String, String> additionalParameters = cause.getAdditionalParameters();
    Map<String, String> environmentVariables = cause.getEnvironmentVariables();

    for (ParameterDefinition definition : definitionProperty.getParameterDefinitions()) {
      String parameterName = definition.getName();
      ParameterValue parameterValue = definition.getDefaultParameterValue();

      if (additionalParameters != null) {
        String additionalParameter = additionalParameters.get(parameterName);
        if (additionalParameter != null) {
          parameterValue = new StringParameterValue(parameterName, additionalParameter);
        }
      }

      String environmentValue = environmentVariables.get(parameterName);
      if (environmentValue != null) {
        parameterValue = new StringParameterValue(parameterName, environmentValue);
      }

      if (parameterValue != null) {
        values.add(parameterValue);
      }
    }
    return values;
  }

  @Nullable
  public Queue.Item startJob(StashCause cause) {
    List<ParameterValue> values = getParameters(cause);

    if (trigger.getCancelOutdatedJobsEnabled()) {
      cancelPreviousJobsInQueueThatMatch(cause);
      abortRunningJobsThatMatch(cause);
    }

    return ParameterizedJobMixIn.scheduleBuild2(
        job, -1, new CauseAction(cause), new StashQueueAction(), new ParametersAction(values));
  }

  void addFutureBuildTask(StashPullRequestBuildTarget buildTarget) {
    StashPullRequestResponseValue pullRequest = buildTarget.getPullRequest();
    Map<String, String> additionalParameters = buildTarget.getAdditionalParameters();
    Integer buildCommandCommentId = buildTarget.getBuildCommandCommentId();

    // Delete comments about previous build results, if that option is
    // enabled. Run the build even if those comments cannot be deleted.
    if (trigger.getDeletePreviousBuildFinishComments()) {
      try {
        deletePreviousBuildFinishedComments(pullRequest);
      } catch (StashApiException e) {
        pollLog.log(
            "Cannot delete old \"BuildFinished\" comments for PR #{}", pullRequest.getId(), e);
        logger.log(
            Level.INFO,
            format(
                "%s: cannot delete old \"BuildFinished\" comments for pull request %s",
                job.getFullName(), pullRequest),
            e);
      }
    }

    // Post a comment indicating the build start. Strictly speaking, we are
    // just adding the build to the queue, it will start after the quiet time
    // expires and there are executors available. Failure to post the comment
    // prevents the build for safety reasons. If the plugin cannot post this
    // comment, chances are it won't be able to post the build results, which
    // would trigger the build again and again, wasting Jenkins resources.
    String buildStartCommentId;
    try {
      buildStartCommentId = postBuildStartComment(pullRequest, buildCommandCommentId);
    } catch (StashApiException e) {
      pollLog.log(
          "Cannot post \"BuildStarted\" comment for PR #{}, not building", pullRequest.getId(), e);
      logger.log(
          Level.INFO,
          format(
              "%s: cannot post Build Start comment for pull request %s, not building",
              job.getFullName(), pullRequest.getId()),
          e);
      return;
    }

    StashCause cause =
        new StashCause(
            trigger.getStashHost(),
            pullRequest.getFromRef().getBranch().getName(),
            pullRequest.getToRef().getBranch().getName(),
            pullRequest.getFromRef().getRepository().getProjectName(),
            pullRequest.getFromRef().getRepository().getRepositoryName(),
            pullRequest.getId(),
            pullRequest.getToRef().getRepository().getProjectName(),
            pullRequest.getToRef().getRepository().getRepositoryName(),
            pullRequest.getTitle(),
            pullRequest.getFromRef().getLatestCommit(),
            pullRequest.getToRef().getLatestCommit(),
            buildStartCommentId,
            buildCommandCommentId,
            pullRequest.getVersion(),
            additionalParameters);

    Queue.Item item = startJob(cause);
    if (item != null) {
      pollLog.log("Queued job for PR #{}", pullRequest.getId());
      logger.info(format("%s: queued job for PR #%s", job.getFullName(), pullRequest.getId()));
    } else {
      pollLog.log("Failed to queue job for PR #{}", pullRequest.getId());
      logger.warning(
          format("%s: failed to queue job for PR #%s", job.getFullName(), pullRequest.getId()));
    }
  }

  /**
   * Deletes pull request comment from Bitbucket Server
   *
   * @param pullRequestId pull request ID
   * @param commentId comment to be deleted
   * @throws StashApiException if deleting the comment fails
   */
  public void deletePullRequestComment(String pullRequestId, String commentId)
      throws StashApiException {
    this.client.deletePullRequestComment(pullRequestId, commentId);
  }

  private String getMessageForBuildResult(Result result) {
    String message = BUILD_FAILURE_COMMENT;
    if (result == Result.SUCCESS) {
      message = BUILD_SUCCESS_COMMENT;
    }
    if (result == Result.UNSTABLE) {
      message = BUILD_UNSTABLE_COMMENT;
    }
    if (result == Result.ABORTED) {
      message = BUILD_ABORTED_COMMENT;
    }
    if (result == Result.NOT_BUILT) {
      message = BUILD_NOTBUILT_COMMENT;
    }
    return message;
  }

  public void postFinishedComment(
      String pullRequestId,
      String sourceCommit,
      String destinationCommit,
      Integer buildCommandCommentId,
      Result buildResult,
      String buildUrl,
      int buildNumber,
      String additionalComment,
      String duration) {
    String message = getMessageForBuildResult(buildResult);
    String comment =
        format(
                BUILD_MARKER,
                BUILD_FINISH_MESSAGE,
                job.getDisplayName(),
                sourceCommit,
                destinationCommit)
            + format(BUILD_FINISH_SENTENCE, message, buildUrl, buildNumber, duration);

    comment = comment.concat(additionalComment);

    // Post the "Build Finished" comment. Failure to post it can lead to
    // scheduling another build for the pull request unnecessarily.
    try {
      this.client.postPullRequestComment(pullRequestId, comment, buildCommandCommentId);
    } catch (StashApiException e) {
      logger.log(
          Level.WARNING,
          format(
              "%s: cannot post Build Finished comment for pull request %s",
              job.getFullName(), pullRequestId),
          e);
    }
  }

  /**
   * Instructs Bitbucket Server to merge pull request
   *
   * @param pullRequestId pull request ID
   * @param version pull request version
   * @return empty optional if the merge succeeds, server response otherwise
   * @throws StashApiException if cannot communicate to the server
   */
  @Nonnull
  public Optional<String> mergePullRequest(String pullRequestId, String version)
      throws StashApiException {
    return this.client.mergePullRequest(pullRequestId, version);
  }

  /**
   * Inquiries Bitbucket Server whether the pull request can be merged
   *
   * @param pullRequest pull request
   * @return true if the merge is allowed, false otherwise
   * @throws StashApiException if cannot communicate to the server
   */
  private boolean isPullRequestMergeable(StashPullRequestResponseValue pullRequest)
      throws StashApiException {
    if (trigger.getCheckMergeable()
        || trigger.getCheckNotConflicted()
        || trigger.getCheckProbeMergeStatus()) {
      /* Request PR status from Stash, and consult our configuration
       * toggles on whether we care about certain verdicts in that
       * JSON answer, parsed into fields of the "response" object.
       * See example in StashApiClientTest.java.
       */
      StashPullRequestMergeableResponse mergeable =
          client.getPullRequestMergeStatus(pullRequest.getId());
      boolean res = true;
      if (trigger.getCheckMergeable()) {
        res &= mergeable.getCanMerge();
      }

      if (trigger.getCheckNotConflicted()) {
        res &= !mergeable.getConflicted();
      }

      /* The trigger.isCheckProbeMergeStatus() consulted above
       * is for when a user wants to just probe the Stash REST API
       * call, so Stash updates the refspecs (by design it does
       * so "lazily" to reduce server load, that is until someone
       * requests a refresh).
       *
       * This is a workaround for a case of broken git references
       * that appear due to pull requests, even popping up in other
       * jobs trying to use e.g. just the master branch.
       *
       * See https://issues.jenkins-ci.org/browse/JENKINS-35219 and
       * https://community.atlassian.com/t5/Bitbucket-questions/Change-pull-request-refs-after-Commit-instead-of-after-Approval/qaq-p/194702
       */

      return res;
    }
    return true;
  }

  private void deletePreviousBuildFinishedComments(StashPullRequestResponseValue pullRequest)
      throws StashApiException {

    String owner = pullRequest.getToRef().getRepository().getProjectName();
    String repositoryName = pullRequest.getToRef().getRepository().getRepositoryName();
    String id = pullRequest.getId();

    List<StashPullRequestComment> comments =
        client.getPullRequestComments(owner, repositoryName, id);

    for (StashPullRequestComment comment : comments) {
      String content = comment.getText();
      if (StringUtils.isEmpty(content)) {
        continue;
      }

      String project_build_finished =
          format(BUILD_STATUS_REGEX, BUILD_FINISH_MESSAGE, job.getDisplayName());
      Matcher finishMatcher =
          Pattern.compile(project_build_finished, Pattern.CASE_INSENSITIVE).matcher(content);

      if (finishMatcher.find()) {
        deletePullRequestComment(pullRequest.getId(), comment.getCommentId().toString());
      }
    }
  }

  private boolean isForTargetBranch(StashPullRequestResponseValue pullRequest) {
    String targetBranchesToBuild = this.trigger.getTargetBranchesToBuild();
    if (StringUtils.isEmpty(targetBranchesToBuild)) {
      return true;
    }

    String[] branches = targetBranchesToBuild.split(",");
    for (String branch : branches) {
      if (pullRequest.getToRef().getBranch().getName().matches(branch.trim())) {
        return true;
      }
    }
    return false;
  }

  private boolean isSkipBuild(String pullRequestContentString) {
    String skipPhrases = this.trigger.getCiSkipPhrases();
    if (StringUtils.isNotEmpty(skipPhrases)) {
      String[] phrases = skipPhrases.split(",");
      for (String phrase : phrases) {
        if (isPhrasesContain(pullRequestContentString, phrase)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isPhrasesContain(String text, String phrase) {
    if (StringUtils.isEmpty(text)) {
      return false;
    }

    return StringUtils.containsIgnoreCase(text, phrase.trim());
  }

  public void pollRepository() {
    long pollStartTime = System.currentTimeMillis();
    pollLog.resetLog();
    pollLog.log("{}: poll started", ZonedDateTime.now().format(TIMESTAMP_FORMATTER));
    logger.finest(format("poll started for %s", job.getFullName()));

    Collection<StashPullRequestResponseValue> targetPullRequests = getTargetPullRequests();
    pollLog.log("Number of open pull requests: {}", targetPullRequests.size());

    List<StashPullRequestBuildTarget> buildTargets = new ArrayList<>();
    for (StashPullRequestResponseValue pullRequest : targetPullRequests) {
      buildTargets.addAll(getBuildTargets(pullRequest));
    }

    pollLog.log("Number of build targets to be built: {}", buildTargets.size());
    for (StashPullRequestBuildTarget buildTarget : buildTargets) {
      addFutureBuildTask(buildTarget);
    }

    pollLog.log(
        "{}: poll completed in {}",
        ZonedDateTime.now().format(TIMESTAMP_FORMATTER),
        Util.getTimeSpanString(System.currentTimeMillis() - pollStartTime));
    logger.fine(
        format(
            "poll completed in %s for %s",
            Util.getTimeSpanString(System.currentTimeMillis() - pollStartTime), job.getFullName()));
  }
}
