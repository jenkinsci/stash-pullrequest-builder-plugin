package stashpullrequestbuilder.stashpullrequestbuilder;

import static java.lang.String.format;

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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import org.apache.commons.lang.StringUtils;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashApiClient;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashApiClient.StashApiException;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestComment;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestMergeableResponse;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValue;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValueRepository;

/** Created by Nathan McCarthy */
public class StashRepository {
  private static final Logger logger =
      Logger.getLogger(MethodHandles.lookup().lookupClass().getName());
  private static final String BUILD_START_MARKER = "[*BuildStarted* **%s**] %s into %s";
  private static final String BUILD_FINISH_MARKER = "[*BuildFinished* **%s**] %s into %s";

  private static final String BUILD_START_REGEX =
      "\\[\\*BuildStarted\\* \\*\\*%s\\*\\*\\] ([0-9a-fA-F]+) into ([0-9a-fA-F]+)";
  private static final String BUILD_FINISH_REGEX =
      "\\[\\*BuildFinished\\* \\*\\*%s\\*\\*\\] ([0-9a-fA-F]+) into ([0-9a-fA-F]+)";

  private static final String BUILD_FINISH_SENTENCE =
      BUILD_FINISH_MARKER + " %n%n **[%s](%s)** - Build *#%d* which took *%s*";

  private static final String BUILD_SUCCESS_COMMENT = "✓ BUILD SUCCESS";
  private static final String BUILD_FAILURE_COMMENT = "✕ BUILD FAILURE";
  private static final String BUILD_UNSTABLE_COMMENT = "⁉ BUILD UNSTABLE";
  private static final String BUILD_ABORTED_COMMENT = "‼ BUILD ABORTED";
  private static final String BUILD_NOTBUILT_COMMENT = "✕ BUILD INCOMPLETE";

  private static final String ADDITIONAL_PARAMETER_REGEX = "^p:(([A-Za-z_0-9])+)=(.*)";
  private static final Pattern ADDITIONAL_PARAMETER_REGEX_PATTERN =
      Pattern.compile(ADDITIONAL_PARAMETER_REGEX);

  private Job<?, ?> job;
  private StashBuildTrigger trigger;
  private StashApiClient client;

  public StashRepository(@Nonnull Job<?, ?> job, @Nonnull StashBuildTrigger trigger) {
    this(job, trigger, makeStashApiClient(trigger));
  }

  // Visible for unit tests
  StashRepository(
      @Nonnull Job<?, ?> job, @Nonnull StashBuildTrigger trigger, StashApiClient client) {
    this.job = job;
    this.trigger = trigger;
    this.client = client;
  }

  private static StashApiClient makeStashApiClient(StashBuildTrigger trigger) {
    return new StashApiClient(
        trigger.getStashHost(),
        trigger.getUsername(),
        trigger.getPassword(),
        trigger.getProjectCode(),
        trigger.getRepositoryName(),
        trigger.isIgnoreSsl());
  }

  public Collection<StashPullRequestResponseValue> getTargetPullRequests() {
    logger.info(format("Fetch PullRequests (%s).", job.getName()));
    List<StashPullRequestResponseValue> targetPullRequests =
        new ArrayList<StashPullRequestResponseValue>();

    List<StashPullRequestResponseValue> pullRequests;
    try {
      pullRequests = client.getPullRequests();
    } catch (StashApiException e) {
      logger.info(format("%s: cannot fetch pull request list: %s", job.getName(), e));
      return targetPullRequests;
    }

    for (StashPullRequestResponseValue pullRequest : pullRequests) {
      if (isBuildTarget(pullRequest)) {
        targetPullRequests.add(pullRequest);
      }
    }
    return targetPullRequests;
  }

  public String postBuildStartCommentTo(StashPullRequestResponseValue pullRequest) {
    String sourceCommit = pullRequest.getFromRef().getLatestCommit();
    String destinationCommit = pullRequest.getToRef().getLatestCommit();
    String comment =
        format(BUILD_START_MARKER, job.getDisplayName(), sourceCommit, destinationCommit);
    StashPullRequestComment commentResponse =
        this.client.postPullRequestComment(pullRequest.getId(), comment);
    return commentResponse.getCommentId().toString();
  }

  public static AbstractMap.SimpleEntry<String, String> getParameter(String content) {
    if (content.isEmpty()) {
      return null;
    }
    Matcher parameterMatcher = ADDITIONAL_PARAMETER_REGEX_PATTERN.matcher(content);
    if (parameterMatcher.find(0)) {
      String parameterName = parameterMatcher.group(1);
      String parameterValue = parameterMatcher.group(3);
      return new AbstractMap.SimpleEntry<String, String>(parameterName, parameterValue);
    }
    return null;
  }

  public static Map<String, String> getParametersFromContent(String content) {
    Map<String, String> result = new TreeMap<String, String>();
    String[] lines = content.split("\\r?\\n|\\r");
    for (String line : lines) {
      AbstractMap.SimpleEntry<String, String> parameter = getParameter(line);
      if (parameter != null) {
        result.put(parameter.getKey(), parameter.getValue());
      }
    }

    return result;
  }

  public Map<String, String> getAdditionalParameters(StashPullRequestResponseValue pullRequest)
      throws StashApiException {
    StashPullRequestResponseValueRepository destination = pullRequest.getToRef();
    String owner = destination.getRepository().getProjectName();
    String repositoryName = destination.getRepository().getRepositoryName();

    String id = pullRequest.getId();
    List<StashPullRequestComment> comments =
        client.getPullRequestComments(owner, repositoryName, id);

    // Process newest comments last so they can override older comments
    Collections.sort(comments);

    Map<String, String> result = new TreeMap<String, String>();

    for (StashPullRequestComment comment : comments) {
      String content = comment.getText();
      if (content == null || content.isEmpty()) {
        continue;
      }

      Map<String, String> parameters = getParametersFromContent(content);
      for (Map.Entry<String, String> parameter : parameters.entrySet()) {
        result.put(parameter.getKey(), parameter.getValue());
      }
    }

    return result;
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
    for (Queue.Item item : queue.getItems()) {
      if (hasCauseFromTheSamePullRequest(item.getCauses(), stashCause)) {
        logger.info("Canceling item in queue: " + item);
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
    List<ParameterValue> values = new ArrayList<ParameterValue>();

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

  public Queue.Item startJob(StashCause cause) {
    List<ParameterValue> values = getParameters(cause);

    if (trigger.isCancelOutdatedJobsEnabled()) {
      cancelPreviousJobsInQueueThatMatch(cause);
      abortRunningJobsThatMatch(cause);
    }

    return ParameterizedJobMixIn.scheduleBuild2(
        job, -1, new CauseAction(cause), new ParametersAction(values));
  }

  public void addFutureBuildTasks(Collection<StashPullRequestResponseValue> pullRequests) {
    for (StashPullRequestResponseValue pullRequest : pullRequests) {
      Map<String, String> additionalParameters;

      try {
        additionalParameters = getAdditionalParameters(pullRequest);
      } catch (StashApiException e) {
        logger.info(
            format(
                "%s: cannot read additional parameters for pull request %s, skipping: %s",
                job.getName(), pullRequest.getId(), e));
        continue;
      }

      if (trigger.getDeletePreviousBuildFinishComments()) {
        try {
          deletePreviousBuildFinishedComments(pullRequest);
        } catch (StashApiException e) {
          logger.info(
              format(
                  "%s: cannot delete old \"BuildFinished\" comments for pull request %s: %s",
                  job.getName(), pullRequest, e));
        }
      }

      String commentId = postBuildStartCommentTo(pullRequest);
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
              commentId,
              pullRequest.getVersion(),
              additionalParameters);
      startJob(cause);
    }
  }

  public void deletePullRequestComment(String pullRequestId, String commentId) {
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
      Result buildResult,
      String buildUrl,
      int buildNumber,
      String additionalComment,
      String duration) {
    String message = getMessageForBuildResult(buildResult);
    String comment =
        format(
            BUILD_FINISH_SENTENCE,
            job.getDisplayName(),
            sourceCommit,
            destinationCommit,
            message,
            buildUrl,
            buildNumber,
            duration);

    comment = comment.concat(additionalComment);

    this.client.postPullRequestComment(pullRequestId, comment);
  }

  public boolean mergePullRequest(String pullRequestId, String version) {
    return this.client.mergePullRequest(pullRequestId, version);
  }

  private boolean isPullRequestMergeable(StashPullRequestResponseValue pullRequest) {
    if (trigger.isCheckMergeable()
        || trigger.isCheckNotConflicted()
        || trigger.isCheckProbeMergeStatus()) {
      /* Request PR status from Stash, and consult our configuration
       * toggles on whether we care about certain verdicts in that
       * JSON answer, parsed into fields of the "response" object.
       * See example in StashApiClientTest.java.
       */
      StashPullRequestMergeableResponse mergeable =
          client.getPullRequestMergeStatus(pullRequest.getId());
      boolean res = true;
      if (trigger.isCheckMergeable()) {
        res &= mergeable.getCanMerge();
      }

      if (trigger.isCheckNotConflicted()) {
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

    StashPullRequestResponseValueRepository destination = pullRequest.getToRef();
    String owner = destination.getRepository().getProjectName();
    String repositoryName = destination.getRepository().getRepositoryName();
    String id = pullRequest.getId();

    List<StashPullRequestComment> comments =
        client.getPullRequestComments(owner, repositoryName, id);

    for (StashPullRequestComment comment : comments) {
      String content = comment.getText();
      if (content == null || content.isEmpty()) {
        continue;
      }

      String project_build_finished = format(BUILD_FINISH_REGEX, job.getDisplayName());
      Matcher finishMatcher =
          Pattern.compile(project_build_finished, Pattern.CASE_INSENSITIVE).matcher(content);

      if (finishMatcher.find()) {
        deletePullRequestComment(pullRequest.getId(), comment.getCommentId().toString());
      }
    }
  }

  private boolean isBuildTarget(StashPullRequestResponseValue pullRequest) {

    if (!"OPEN".equals(pullRequest.getState())) {
      return false;
    }

    boolean shouldBuild = true;

    if (isSkipBuild(pullRequest.getTitle())) {
      logger.info("Skipping PR: " + pullRequest.getId() + " as title contained skip phrase");
      return false;
    }

    if (!isForTargetBranch(pullRequest)) {
      logger.info(
          "Skipping PR: "
              + pullRequest.getId()
              + " as targeting branch: "
              + pullRequest.getToRef().getBranch().getName());
      return false;
    }

    if (!isPullRequestMergeable(pullRequest)) {
      logger.info("Skipping PR: " + pullRequest.getId() + " as cannot be merged");
      return false;
    }

    boolean isOnlyBuildOnComment = trigger.isOnlyBuildOnComment();

    if (isOnlyBuildOnComment) {
      shouldBuild = false;
    }

    String sourceCommit = pullRequest.getFromRef().getLatestCommit();

    StashPullRequestResponseValueRepository destination = pullRequest.getToRef();
    String owner = destination.getRepository().getProjectName();
    String repositoryName = destination.getRepository().getRepositoryName();
    String destinationCommit = destination.getLatestCommit();

    String id = pullRequest.getId();
    List<StashPullRequestComment> comments;
    try {
      comments = client.getPullRequestComments(owner, repositoryName, id);
    } catch (StashApiException e) {
      logger.info(format("%s: cannot read pull request comments: %s", job.getName(), e));
      return false;
    }

    // Start with most recent comments
    Collections.sort(comments, Collections.reverseOrder());

    for (StashPullRequestComment comment : comments) {
      String content = comment.getText();
      if (content == null || content.isEmpty()) {
        continue;
      }

      // These will match any start or finish message -- need to check commits
      String escapedBuildName = Pattern.quote(job.getDisplayName());
      String project_build_start = String.format(BUILD_START_REGEX, escapedBuildName);
      String project_build_finished = String.format(BUILD_FINISH_REGEX, escapedBuildName);
      Matcher startMatcher =
          Pattern.compile(project_build_start, Pattern.CASE_INSENSITIVE).matcher(content);
      Matcher finishMatcher =
          Pattern.compile(project_build_finished, Pattern.CASE_INSENSITIVE).matcher(content);

      if (startMatcher.find() || finishMatcher.find()) {
        // in build only on comment, we should stop parsing comments as soon as a PR builder
        // comment is found.
        if (isOnlyBuildOnComment) {
          assert !shouldBuild;
          break;
        }

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

          shouldBuild = false;
          break;
        }
      }

      if (isSkipBuild(content)) {
        shouldBuild = false;
        break;
      }
      if (isPhrasesContain(content, this.trigger.getCiBuildPhrases())) {
        shouldBuild = true;
        break;
      }
    }
    if (shouldBuild) {
      logger.info("Building PR: " + pullRequest.getId());
    }
    return shouldBuild;
  }

  private boolean isForTargetBranch(StashPullRequestResponseValue pullRequest) {
    String targetBranchesToBuild = this.trigger.getTargetBranchesToBuild();
    if (StringUtils.isNotEmpty(targetBranchesToBuild)) {
      String[] branches = targetBranchesToBuild.split(",");
      for (String branch : branches) {
        if (pullRequest.getToRef().getBranch().getName().matches(branch.trim())) {
          return true;
        }
      }
      return false;
    }
    return true;
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
    return text != null && text.toLowerCase().contains(phrase.trim().toLowerCase());
  }
}
