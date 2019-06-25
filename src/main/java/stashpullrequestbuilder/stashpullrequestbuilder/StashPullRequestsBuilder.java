package stashpullrequestbuilder.stashpullrequestbuilder;

import static java.lang.String.format;

import hudson.model.AbstractProject;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValue;

/** Created by Nathan McCarthy */
public class StashPullRequestsBuilder {
  private static final Logger logger =
      Logger.getLogger(MethodHandles.lookup().lookupClass().getName());
  private AbstractProject<?, ?> project;
  private StashBuildTrigger trigger;
  private StashRepository repository;

  public StashPullRequestsBuilder(
      @Nonnull AbstractProject<?, ?> project, @Nonnull StashBuildTrigger trigger) {
    this.project = project;
    this.trigger = trigger;
    this.repository = new StashRepository(project, trigger);
  }

  public void run() {
    logger.info(format("Build Start (%s).", project.getName()));
    Collection<StashPullRequestResponseValue> targetPullRequests =
        this.repository.getTargetPullRequests();
    this.repository.addFutureBuildTasks(targetPullRequests);
  }

  public AbstractProject<?, ?> getProject() {
    return this.project;
  }

  public StashBuildTrigger getTrigger() {
    return this.trigger;
  }

  public StashRepository getRepository() {
    return this.repository;
  }
}
