package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/** Created by Nathan on 20/03/2015. */
@SuppressFBWarnings("EQ_COMPARETO_USE_OBJECT_EQUALS")
@JsonIgnoreProperties(ignoreUnknown = true)
public class StashPullRequestActivity implements Comparable<StashPullRequestActivity> {
  private StashPullRequestComment comment;

  public StashPullRequestComment getComment() {
    return comment;
  }

  public void setComment(StashPullRequestComment comment) {
    this.comment = comment;
  }

  @Override
  public int compareTo(StashPullRequestActivity target) {
    if (this.comment == null || target.getComment() == null) {
      return -1;
    }
    int commentIdThis = this.comment.getCommentId();
    int commentIdOther = target.getComment().getCommentId();

    if (commentIdThis > commentIdOther) {
      return 1;
    } else if (commentIdThis == commentIdOther) {
      return 0;
    } else {
      return -1;
    }
  }
}
