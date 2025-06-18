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
import hudson.util.DaemonThreadFactory;
import hudson.util.ExceptionCatchingThreadFactory;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.NamingThreadFactory;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.ParameterizedJobMixIn.ParameterizedJob;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashApiClient;

/** Created by Nathan McCarthy */
public class StashBuildTrigger extends Trigger<Job<?, ?>> {
  private static final Logger logger =
      Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

  @Extension public static final DescriptorImpl descriptor = new DescriptorImpl();

  // Required settings
  private final String cron;
  private final String stashHost;
  private final String credentialsId;
  private final String projectCode;
  private final String repositoryName;

  // Optional settings
  private boolean ignoreSsl;
  private String targetBranchesToBuild = "";
  private boolean checkDestinationCommit;
  private boolean checkNotConflicted;
  private boolean checkMergeable;
  private boolean checkProbeMergeStatus = true;
  private boolean mergeOnSuccess;
  private boolean deletePreviousBuildFinishComments;
  private boolean cancelOutdatedJobsEnabled;
  private String ciSkipPhrases = DescriptorImpl.DEFAULT_CI_SKIP_PHRASES;
  private boolean onlyBuildOnComment;
  private String ciBuildPhrases = DescriptorImpl.DEFAULT_CI_BUILD_PHRASES;

  private transient StashRepository stashRepository;
  private transient StashPollingAction stashPollingAction;

  private transient AtomicBoolean checkAlreadyQueued;

  @DataBoundConstructor
  public StashBuildTrigger(
      String cron,
      String stashHost,
      String credentialsId,
      String projectCode,
      String repositoryName)
      throws ANTLRException {
    super(cron);
    this.cron = cron;
    this.stashHost = stashHost;
    this.credentialsId = credentialsId;
    this.projectCode = projectCode;
    this.repositoryName = repositoryName;
  }

  public String getCron() {
    return this.cron;
  }

  public String getStashHost() {
    return stashHost;
  }

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

  public String getProjectCode() {
    return projectCode;
  }

  public String getRepositoryName() {
    return repositoryName;
  }

  public boolean getIgnoreSsl() {
    return ignoreSsl;
  }

  @DataBoundSetter
  public void setIgnoreSsl(boolean ignoreSsl) {
    this.ignoreSsl = ignoreSsl;
  }

  public String getTargetBranchesToBuild() {
    return targetBranchesToBuild;
  }

  @DataBoundSetter
  public void setTargetBranchesToBuild(String targetBranchesToBuild) {
    this.targetBranchesToBuild = targetBranchesToBuild;
  }

  public boolean getCheckDestinationCommit() {
    return checkDestinationCommit;
  }

  @DataBoundSetter
  public void setCheckDestinationCommit(boolean checkDestinationCommit) {
    this.checkDestinationCommit = checkDestinationCommit;
  }

  public boolean getCheckNotConflicted() {
    return checkNotConflicted;
  }

  @DataBoundSetter
  public void setCheckNotConflicted(boolean checkNotConflicted) {
    this.checkNotConflicted = checkNotConflicted;
  }

  public boolean getCheckMergeable() {
    return checkMergeable;
  }

  @DataBoundSetter
  public void setCheckMergeable(boolean checkMergeable) {
    this.checkMergeable = checkMergeable;
  }

  public boolean getCheckProbeMergeStatus() {
    return checkProbeMergeStatus;
  }

  @DataBoundSetter
  public void setCheckProbeMergeStatus(boolean checkProbeMergeStatus) {
    this.checkProbeMergeStatus = checkProbeMergeStatus;
  }

  public boolean getMergeOnSuccess() {
    return mergeOnSuccess;
  }

  @DataBoundSetter
  public void setMergeOnSuccess(boolean mergeOnSuccess) {
    this.mergeOnSuccess = mergeOnSuccess;
  }

  public boolean getDeletePreviousBuildFinishComments() {
    return deletePreviousBuildFinishComments;
  }

  @DataBoundSetter
  public void setDeletePreviousBuildFinishComments(boolean deletePreviousBuildFinishComments) {
    this.deletePreviousBuildFinishComments = deletePreviousBuildFinishComments;
  }

  public boolean getCancelOutdatedJobsEnabled() {
    return cancelOutdatedJobsEnabled;
  }

  @DataBoundSetter
  public void setCancelOutdatedJobsEnabled(boolean cancelOutdatedJobsEnabled) {
    this.cancelOutdatedJobsEnabled = cancelOutdatedJobsEnabled;
  }

  public String getCiSkipPhrases() {
    return ciSkipPhrases;
  }

  @DataBoundSetter
  public void setCiSkipPhrases(String ciSkipPhrases) {
    this.ciSkipPhrases = ciSkipPhrases;
  }

  public boolean getOnlyBuildOnComment() {
    return onlyBuildOnComment;
  }

  @DataBoundSetter
  public void setOnlyBuildOnComment(boolean onlyBuildOnComment) {
    this.onlyBuildOnComment = onlyBuildOnComment;
  }

  public String getCiBuildPhrases() {
    return ciBuildPhrases;
  }

  @DataBoundSetter
  public void setCiBuildPhrases(String ciBuildPhrases) {
    this.ciBuildPhrases = ciBuildPhrases;
  }

  public StashPollingAction getStashPollingAction() {
    return stashPollingAction;
  }

  @Override
  public void start(Job<?, ?> job, boolean newInstance) {
    super.start(job, newInstance);
    this.checkAlreadyQueued = new AtomicBoolean(false);

    if (job == null) {
      logger.log(Level.SEVERE, "Can't start trigger: job is null");
      return;
    }

    if (stashPollingAction == null) {
      stashPollingAction = new StashPollingAction(job);
    }

    if (StringUtils.isEmpty(credentialsId)) {
      stashPollingAction.log("Stash credentials are not set");
      return;
    }

    StandardUsernamePasswordCredentials credentials = getCredentials();
    if (credentials == null) {
      stashPollingAction.log("No such credentials: \"{}\"", credentialsId);
      return;
    }

    try {
      StashApiClient stashApiClient =
          new StashApiClient(
              getStashHost(),
              credentials.getUsername(),
              credentials.getPassword().getPlainText(),
              getProjectCode(),
              getRepositoryName(),
              getIgnoreSsl());

      this.stashRepository = new StashRepository(job, this, stashApiClient, stashPollingAction);
    } catch (Throwable e) {
      logger.log(Level.SEVERE, "Can't start trigger", e);
      stashPollingAction.log("Can't start trigger", e);
    }
  }

  public StashRepository getRepository() {
    return this.stashRepository;
  }

  @Override
  public void run() {
    if (job == null || stashRepository == null) {
      logger.info("Not ready to run.");
      return;
    }

    if (!job.isBuildable()) {
      logger.fine(format("Job is not buildable, skipping build (%s).", job.getFullName()));
      return;
    }

    if (checkAlreadyQueued.compareAndSet(false, true)) {
      logger.fine(format("Job not queued, adding %s", job.getFullName()));
      descriptor
          .getExecutorService()
          .execute(
              new Runnable() {
                @Override
                public void run() {
                  try {
                    stashRepository.pollRepository();
                  } finally {
                    checkAlreadyQueued.set(false);
                  }
                }
              });
    } else {
      logger.fine(format("Job is already queued, skipping %s", job.getFullName()));
    }

    getDescriptor().save();
  }

  @Override
  public void stop() {
    stashRepository = null;
    stashPollingAction = null;
    super.stop();
  }

  public static final class DescriptorImpl extends TriggerDescriptor {
    public static final String DEFAULT_CI_SKIP_PHRASES = "NO TEST";
    public static final String DEFAULT_CI_BUILD_PHRASES = "test this please";
    private final transient ExecutorService executorService;

    private boolean enablePipelineSupport;
    private String numOfThreads = "10";

    public DescriptorImpl() {
      load();
      int intOfThreads = Integer.parseInt(numOfThreads);
      logger.info(format("Creating pool size %s", numOfThreads));
      executorService =
          Executors.newFixedThreadPool(
              intOfThreads,
              new ExceptionCatchingThreadFactory(
                  new NamingThreadFactory(
                      new DaemonThreadFactory(), "DescriptorImpl.DescriptorImpl")));
    }

    public ExecutorService getExecutorService() {
      return executorService;
    }

    public boolean getEnablePipelineSupport() {
      return enablePipelineSupport;
    }

    public String getNumOfThreads() {
      return numOfThreads;
    }

    @DataBoundSetter
    public void setNumOfThreads(String numOfThreads) {
      this.numOfThreads = numOfThreads;
    }

    @DataBoundSetter
    public void setEnablePipelineSupport(boolean enablePipelineSupport) {
      this.enablePipelineSupport = enablePipelineSupport;
    }

    @Override
    public boolean isApplicable(Item item) {
      if (enablePipelineSupport) {
        return item instanceof Job && item instanceof ParameterizedJob;
      }

      return item instanceof AbstractProject;
    }

    @Override
    public String getDisplayName() {
      return "Stash pull request builder";
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
      enablePipelineSupport = false;

      req.bindJSON(this, json);
      save();
      logger.info(format("configuring numOfThreads to %s", numOfThreads));
      return super.configure(req, json);
    }

    public FormValidation doCheckCredentialsId(@QueryParameter String value) {
      if (StringUtils.isEmpty(value)) {
        return FormValidation.error("Credentials cannot be empty");
      } else {
        return FormValidation.ok();
      }
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
