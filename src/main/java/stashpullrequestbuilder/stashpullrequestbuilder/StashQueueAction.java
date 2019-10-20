package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.model.Action;
import hudson.model.InvisibleAction;
import hudson.model.Queue.QueueAction;
import java.util.List;

/**
 * QueueAction implementation used to control folding of queue items
 *
 * <p>Jenkins tries to fold multiple queue items into one to avoid building the same thing multiple
 * times. That behavior is undesired for the builds triggered by this plugin. The builds differ by
 * StashCause, but Jenkins allows a build to have many causes.
 *
 * <p>Jenkins uses QueueAction interface to decide whether to fold tasks. Tasks that use this action
 * will not be folded by Jenkins.
 */
public class StashQueueAction extends InvisibleAction implements QueueAction {

  /**
   * Returns whether the new item should be scheduled. Returns true if the other task is 'different
   * enough' to warrant a separate execution.
   */
  @Override
  public boolean shouldSchedule(List<Action> actions) {
    // Always schedule a new task
    return true;
  }
}
