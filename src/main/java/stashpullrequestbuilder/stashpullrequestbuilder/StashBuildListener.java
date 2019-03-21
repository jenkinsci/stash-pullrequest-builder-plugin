package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.lang.invoke.MethodHandles;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/** Created by Nathan McCarthy */
@Extension
public class StashBuildListener extends RunListener<AbstractBuild> {
  private static final Logger logger =
      Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

  @Override
  public void onStarted(AbstractBuild abstractBuild, TaskListener listener) {
    logger.info("BuildListener onStarted called.");
    StashBuildTrigger trigger = StashBuildTrigger.getTrigger(abstractBuild.getProject());
    if (trigger == null) {
      return;
    }
    trigger.getBuilder().getBuilds().onStarted(abstractBuild);
  }

  @Override
  public void onCompleted(AbstractBuild abstractBuild, @Nonnull TaskListener listener) {
    StashBuildTrigger trigger = StashBuildTrigger.getTrigger(abstractBuild.getProject());
    if (trigger == null) {
      return;
    }
    trigger.getBuilder().getBuilds().onCompleted(abstractBuild, listener);
  }
}
