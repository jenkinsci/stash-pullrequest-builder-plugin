package stashpullrequestbuilder.stashpullrequestbuilder;

import static java.lang.String.format;

import antlr.ANTLRException;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Executor;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.ListBoxModel;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/** Created by Nathan McCarthy */
@SuppressFBWarnings({"NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", "NP_NULL_ON_SOME_PATH"})
public class StashBuildTrigger extends Trigger<AbstractProject<?, ?>> {
  private static final Logger logger =
      Logger.getLogger(MethodHandles.lookup().lookupClass().getName());
  private final String projectPath;
  private final String cron;
  private final String stashHost;
  private final String credentialsId;
  private final String projectCode;
  private final String repositoryName;
  private final String ciSkipPhrases;
  private final String ciBuildPhrases;
  private final String targetBranchesToBuild;
  private final boolean ignoreSsl;
  private final boolean checkDestinationCommit;
  private final boolean checkMergeable;
  private final boolean mergeOnSuccess;
  private final boolean checkNotConflicted;
  private final boolean onlyBuildOnComment;
  private final boolean deletePreviousBuildFinishComments;
  private final boolean cancelOutdatedJobsEnabled;

  private boolean checkProbeMergeStatus;

  private transient StashPullRequestsBuilder stashPullRequestsBuilder;

  @Extension
  public static final StashBuildTriggerDescriptor descriptor = new StashBuildTriggerDescriptor();

  @DataBoundConstructor
  public StashBuildTrigger(
      String projectPath,
      String cron,
      String stashHost,
      String credentialsId,
      String projectCode,
      String repositoryName,
      String ciSkipPhrases,
      boolean ignoreSsl,
      boolean checkDestinationCommit,
      boolean checkMergeable,
      boolean mergeOnSuccess,
      boolean checkNotConflicted,
      boolean onlyBuildOnComment,
      String ciBuildPhrases,
      boolean deletePreviousBuildFinishComments,
      String targetBranchesToBuild,
      boolean cancelOutdatedJobsEnabled)
      throws ANTLRException {
    super(cron);
    this.projectPath = projectPath;
    this.cron = cron;
    this.stashHost = stashHost;
    this.credentialsId = credentialsId;
    this.projectCode = projectCode;
    this.repositoryName = repositoryName;
    this.ciSkipPhrases = ciSkipPhrases;
    this.cancelOutdatedJobsEnabled = cancelOutdatedJobsEnabled;
    this.ciBuildPhrases = ciBuildPhrases == null ? "test this please" : ciBuildPhrases;
    this.ignoreSsl = ignoreSsl;
    this.checkDestinationCommit = checkDestinationCommit;
    this.checkMergeable = checkMergeable;
    this.mergeOnSuccess = mergeOnSuccess;
    this.checkNotConflicted = checkNotConflicted;
    this.onlyBuildOnComment = onlyBuildOnComment;
    this.deletePreviousBuildFinishComments = deletePreviousBuildFinishComments;
    this.targetBranchesToBuild = targetBranchesToBuild;
  }

  @DataBoundSetter
  public void setCheckProbeMergeStatus(boolean checkProbeMergeStatus) {
    this.checkProbeMergeStatus = checkProbeMergeStatus;
  }

  public String getStashHost() {
    return stashHost;
  }

  public String getProjectPath() {
    return this.projectPath;
  }

  public String getCron() {
    return this.cron;
  }

  // Needed for Jelly Config
  public String getcredentialsId() {
    return this.credentialsId;
  }

  private StandardUsernamePasswordCredentials getCredentials() {
    return CredentialsMatchers.firstOrNull(
        CredentialsProvider.lookupCredentials(
            StandardUsernamePasswordCredentials.class,
            this.job,
            Tasks.getDefaultAuthenticationOf(this.job),
            URIRequirementBuilder.fromUri(stashHost).build()),
        CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId)));
  }

  public String getUsername() {
    return this.getCredentials().getUsername();
  }

  public String getPassword() {
    return this.getCredentials().getPassword().getPlainText();
  }

  public String getProjectCode() {
    return projectCode;
  }

  public String getRepositoryName() {
    return repositoryName;
  }

  public String getCiSkipPhrases() {
    return ciSkipPhrases;
  }

  public String getCiBuildPhrases() {
    return ciBuildPhrases == null ? "test this please" : ciBuildPhrases;
  }

  public boolean getCheckDestinationCommit() {
    return checkDestinationCommit;
  }

  public boolean isIgnoreSsl() {
    return ignoreSsl;
  }

  public boolean getDeletePreviousBuildFinishComments() {
    return deletePreviousBuildFinishComments;
  }

  public String getTargetBranchesToBuild() {
    return targetBranchesToBuild;
  }

  public boolean getMergeOnSuccess() {
    return mergeOnSuccess;
  }

  public boolean isCancelOutdatedJobsEnabled() {
    return cancelOutdatedJobsEnabled;
  }

  @Override
  public void start(AbstractProject<?, ?> project, boolean newInstance) {
    super.start(project, newInstance);
    try {
      Objects.requireNonNull(project, "project is null");
      this.stashPullRequestsBuilder = new StashPullRequestsBuilder(project, this);
    } catch (NullPointerException e) {
      logger.log(Level.SEVERE, "Can't start trigger", e);
      return;
    }
  }

  public StashPullRequestsBuilder getBuilder() {
    return this.stashPullRequestsBuilder;
  }

  public QueueTaskFuture<?> startJob(StashCause cause) {
    List<ParameterValue> values = getParameters(cause);

    if (isCancelOutdatedJobsEnabled()) {
      cancelPreviousJobsInQueueThatMatch(cause);
      abortRunningJobsThatMatch(cause);
    }

    return job.scheduleBuild2(job.getQuietPeriod(), cause, new ParametersAction(values));
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

  private List<ParameterValue> getParameters(StashCause cause) {
    List<ParameterValue> values = new ArrayList<ParameterValue>();

    Map<String, String> additionalParameters = cause.getAdditionalParameters();

    ParametersDefinitionProperty definitionProperty =
        this.job.getProperty(ParametersDefinitionProperty.class);

    if (definitionProperty == null) {
      return values;
    }

    for (ParameterDefinition definition : definitionProperty.getParameterDefinitions()) {
      String parameterName = definition.getName();
      ParameterValue parameterValue = definition.getDefaultParameterValue();

      if (additionalParameters != null) {
        String additionalParameter = additionalParameters.get(parameterName);
        if (additionalParameter != null) {
          parameterValue = new StringParameterValue(parameterName, additionalParameter);
        }
      }

      if (parameterValue != null) {
        values.add(parameterValue);
      }
    }
    return values;
  }

  @Override
  public void run() {
    if (job == null) {
      logger.info("Not ready to run.");
      return;
    }

    if (!job.isBuildable()) {
      logger.fine(format("Job is not buildable, skipping build (%s).", job.getName()));
      return;
    }

    stashPullRequestsBuilder.run();
    getDescriptor().save();
  }

  @Override
  public void stop() {
    stashPullRequestsBuilder = null;
    super.stop();
  }

  public boolean isCheckMergeable() {
    return checkMergeable;
  }

  public boolean isCheckNotConflicted() {
    return checkNotConflicted;
  }

  public boolean isCheckProbeMergeStatus() {
    return checkProbeMergeStatus;
  }

  public boolean isOnlyBuildOnComment() {
    return onlyBuildOnComment;
  }

  public static final class StashBuildTriggerDescriptor extends TriggerDescriptor {
    public StashBuildTriggerDescriptor() {
      load();
    }

    @Override
    public boolean isApplicable(Item item) {
      return item instanceof AbstractProject;
    }

    @Override
    public String getDisplayName() {
      return "Stash pull request builder";
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
      save();
      return super.configure(req, json);
    }

    public ListBoxModel doFillCredentialsIdItems(
        @AncestorInPath Item context, @QueryParameter String stashHost) {
      if (context == null || !context.hasPermission(Item.CONFIGURE)) {
        return new ListBoxModel();
      }
      return new StandardUsernameListBoxModel()
          .includeEmptyValue()
          .includeAs(
              context instanceof Queue.Task
                  ? Tasks.getDefaultAuthenticationOf((Queue.Task) context)
                  : ACL.SYSTEM,
              context,
              StandardUsernamePasswordCredentials.class,
              URIRequirementBuilder.fromUri(stashHost).build());
    }
  }
}
