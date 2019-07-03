package stashpullrequestbuilder.stashpullrequestbuilder;

import static java.lang.String.format;

import antlr.ANTLRException;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.ListBoxModel;
import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.ParameterizedJobMixIn.ParameterizedJob;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/** Created by Nathan McCarthy */
public class StashBuildTrigger extends Trigger<Job<?, ?>> {
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
  public String getCredentialsId() {
    return this.credentialsId;
  }

  private StandardUsernamePasswordCredentials getCredentials() {
    // Cast is safe due to isApplicable() check
    ParameterizedJob parameterizedJob = (ParameterizedJob) job;

    return CredentialsMatchers.firstOrNull(
        CredentialsProvider.lookupCredentials(
            StandardUsernamePasswordCredentials.class,
            this.job,
            Tasks.getDefaultAuthenticationOf(parameterizedJob),
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

  public boolean getIgnoreSsl() {
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

  public boolean getCancelOutdatedJobsEnabled() {
    return cancelOutdatedJobsEnabled;
  }

  @Override
  public void start(Job<?, ?> job, boolean newInstance) {
    super.start(job, newInstance);
    try {
      Objects.requireNonNull(job, "job is null");
      this.stashPullRequestsBuilder = new StashPullRequestsBuilder(job, this);
    } catch (NullPointerException e) {
      logger.log(Level.SEVERE, "Can't start trigger", e);
      return;
    }
  }

  public StashPullRequestsBuilder getBuilder() {
    return this.stashPullRequestsBuilder;
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

  public boolean getCheckMergeable() {
    return checkMergeable;
  }

  public boolean getCheckNotConflicted() {
    return checkNotConflicted;
  }

  public boolean getCheckProbeMergeStatus() {
    return checkProbeMergeStatus;
  }

  public boolean getOnlyBuildOnComment() {
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
