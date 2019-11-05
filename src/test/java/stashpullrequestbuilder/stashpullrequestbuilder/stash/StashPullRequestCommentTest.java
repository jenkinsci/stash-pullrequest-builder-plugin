package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

/** Unit tests for StashPullRequestComment */
public class StashPullRequestCommentTest {

  @Test
  public void accessors_work() {
    StashPullRequestComment comment = new StashPullRequestComment();
    comment.setCommentId(42);
    comment.setText("Build Started");
    assertThat(comment.getCommentId(), is(equalTo(42)));
    assertThat(comment.getText(), is(equalTo("Build Started")));
  }

  @Test
  public void hashCode_determined_by_commentId() {
    StashPullRequestComment comment = new StashPullRequestComment();
    assertThat(comment.hashCode(), is(equalTo(Integer.MIN_VALUE)));

    comment.setCommentId(Integer.MAX_VALUE);
    assertThat(comment.hashCode(), is(equalTo(Integer.MAX_VALUE)));

    comment.setCommentId(123);
    assertThat(comment.hashCode(), is(equalTo(123)));

    comment.setCommentId(-1000);
    assertThat(comment.hashCode(), is(equalTo(-1000)));

    comment.setText("42");
    assertThat(comment.hashCode(), is(equalTo(-1000)));
  }

  @Test
  public void comment_unequal_to_null() {
    StashPullRequestComment comment = new StashPullRequestComment();

    assertThat(comment.equals(null), is(equalTo(false)));
  }

  @Test
  public void comment_with_id_equal_to_itself() {
    StashPullRequestComment comment = new StashPullRequestComment();
    comment.setCommentId(1);

    assertThat(comment.equals(comment), is(equalTo(true)));
    assertThat(comment.compareTo(comment), is(equalTo(0)));
  }

  @Test
  public void comment_with_null_id_equal_to_itself() {
    StashPullRequestComment comment = new StashPullRequestComment();

    assertThat(comment.equals(comment), is(equalTo(true)));
    assertThat(comment.compareTo(comment), is(equalTo(0)));
  }

  @Test
  public void comments_with_same_id_are_equal() {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setCommentId(1);
    comment1.setText("1");

    StashPullRequestComment comment2 = new StashPullRequestComment();
    comment2.setCommentId(1);
    comment2.setText("2");

    assertThat(comment1.equals(comment2), is(equalTo(true)));
    assertThat(comment2.equals(comment1), is(equalTo(true)));
    assertThat(comment1.compareTo(comment2), is(equalTo(0)));
    assertThat(comment2.compareTo(comment1), is(equalTo(0)));
  }

  @Test
  public void comments_with_null_id_are_equal() {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setText("1");

    StashPullRequestComment comment2 = new StashPullRequestComment();
    comment2.setText("2");

    assertThat(comment1.equals(comment2), is(equalTo(true)));
    assertThat(comment2.equals(comment1), is(equalTo(true)));
    assertThat(comment1.compareTo(comment2), is(equalTo(0)));
    assertThat(comment2.compareTo(comment1), is(equalTo(0)));
  }

  @Test
  public void comments_with_different_id_are_unequal() {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setCommentId(1);
    comment1.setText("1");

    StashPullRequestComment comment2 = new StashPullRequestComment();
    comment2.setCommentId(2);
    comment2.setText("1");

    assertThat(comment1.equals(comment2), is(equalTo(false)));
    assertThat(comment2.equals(comment1), is(equalTo(false)));
    assertThat(comment1.compareTo(comment2), is(lessThan(0)));
    assertThat(comment2.compareTo(comment1), is(greaterThan(0)));
  }

  @Test
  public void comment_with_nonnull_id_unequal_to_comment_with_null_id() {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setCommentId(1);

    StashPullRequestComment comment2 = new StashPullRequestComment();

    assertThat(comment1.equals(comment2), is(equalTo(false)));
    assertThat(comment2.equals(comment1), is(equalTo(false)));
  }

  @Test
  public void comparison_throws_for_null_id_in_caller() {
    StashPullRequestComment comment1 = new StashPullRequestComment();

    StashPullRequestComment comment2 = new StashPullRequestComment();
    comment2.setCommentId(2);

    assertThrows(NullPointerException.class, () -> comment1.compareTo(comment2));
  }

  @Test
  public void comparison_throws_for_null_id_in_argument() {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setCommentId(1);

    StashPullRequestComment comment2 = new StashPullRequestComment();

    assertThrows(NullPointerException.class, () -> comment1.compareTo(comment2));
  }
}
