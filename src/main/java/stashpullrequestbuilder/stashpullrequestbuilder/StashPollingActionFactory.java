package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.model.TransientActionFactory;

/** Inject StashPollingAction into its owning item. */
@Extension
public class StashPollingActionFactory extends TransientActionFactory<TopLevelItem> {

  @Override
  public Class<TopLevelItem> type() {
    return TopLevelItem.class;
  }

  @Nonnull
  @Override
  public Collection<? extends Action> createFor(@Nonnull TopLevelItem item) {
    List<Action> actions = new ArrayList<>();

    if (!(item instanceof Job)) {
      return actions;
    }

    Job<?, ?> job = (Job<?, ?>) item;

    StashBuildTrigger trigger = ParameterizedJobMixIn.getTrigger(job, StashBuildTrigger.class);
    if (trigger == null) {
      return actions;
    }

    StashPollingAction stashPollingAction = trigger.getStashPollingAction();
    if (stashPollingAction != null) {
      actions.add(stashPollingAction);
    }

    return actions;
  }
}
