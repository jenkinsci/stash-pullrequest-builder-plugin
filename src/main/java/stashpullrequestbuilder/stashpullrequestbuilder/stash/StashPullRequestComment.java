package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import java.util.Objects;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

/** Created by Nathan McCarthy */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StashPullRequestComment implements Comparable<StashPullRequestComment> {

  private Integer commentId;
  private String text;

  @JsonProperty("id")
  public Integer getCommentId() {
    return commentId;
  }

  @JsonProperty("id")
  public void setCommentId(Integer commentId) {
    this.commentId = commentId;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  @Override
  public int hashCode() {
    return (commentId == null) ? Integer.MIN_VALUE : commentId;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof StashPullRequestComment)) {
      return false;
    }

    StashPullRequestComment other = (StashPullRequestComment) o;

    return Objects.equals(this.commentId, other.commentId);
  }

  @Override
  public int compareTo(StashPullRequestComment other) {
    return Objects.compare(
        this.commentId, other.commentId, (Integer a, Integer b) -> a.compareTo(b));
  }
}
