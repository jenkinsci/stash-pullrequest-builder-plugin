package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.model.InvisibleAction;

public class StashPostBuildCommentAction extends InvisibleAction {
  // Recognize deprecated fields so they can be read in from XML, but mark
  // them transient so they are not saved.
  @Deprecated
  @SuppressWarnings("unused")
  private final transient String buildSuccessfulComment = null;

  @Deprecated
  @SuppressWarnings("unused")
  private final transient String buildFailedComment = null;
}
