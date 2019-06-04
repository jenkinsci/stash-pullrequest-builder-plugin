package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class StashPostBuildComment extends Notifier {
  private String buildSuccessfulComment;
  private String buildFailedComment;

  @DataBoundConstructor
  public StashPostBuildComment(String buildSuccessfulComment, String buildFailedComment) {
    this.buildSuccessfulComment = buildSuccessfulComment;
    this.buildFailedComment = buildFailedComment;
  }

  @Override
  public BuildStepMonitor getRequiredMonitorService() {
    return BuildStepMonitor.NONE;
  }

  public String getBuildSuccessfulComment() {
    return buildSuccessfulComment;
  }

  public void setBuildSuccessfulComment(String buildSuccessfulComment) {
    this.buildSuccessfulComment = buildSuccessfulComment;
  }

  public String getBuildFailedComment() {
    return buildFailedComment;
  }

  public void setBuildFailedComment(String buildFailedComment) {
    this.buildFailedComment = buildFailedComment;
  }

  @Override
  public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
    return true;
  }

  @Override
  public BuildStepDescriptor<Publisher> getDescriptor() {
    return DESCRIPTOR;
  }

  @Extension public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

  public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

    public DescriptorImpl() {
      super(StashPostBuildComment.class);
    }

    @Override
    public StashPostBuildComment newInstance(StaplerRequest req, JSONObject formData)
        throws FormException {
      return req.bindJSON(StashPostBuildComment.class, formData);
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      // Requires that the Build Trigger is set or else nothing is posted.
      // We would like that the Post Build Comment option should not be visible when
      // the build trigger is not present.
      return true;
    }

    @Override
    public String getDisplayName() {
      return "Stash pull request builder - post build comment";
    }
  }
}
