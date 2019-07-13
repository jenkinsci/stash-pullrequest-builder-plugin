package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/** Unit tests for StashPullRequestComment */
public class StashPullRequestCommentTest {

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void hashCode_behaves_as_expected() {
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
  public void comment_compareTo_null_throws() {
    StashPullRequestComment comment = new StashPullRequestComment();

    expectedException.expect(NullPointerException.class);
    comment.compareTo(null);
  }

  @Test
  public void comments_with_same_id_and_same_text_are_equal() {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setCommentId(1);
    comment1.setText("1");

    StashPullRequestComment comment2 = new StashPullRequestComment();
    comment2.setCommentId(1);
    comment2.setText("1");

    assertThat(comment1.equals(comment2), is(equalTo(true)));
    assertThat(comment2.equals(comment1), is(equalTo(true)));
    assertThat(comment1.compareTo(comment2), is(equalTo(0)));
    assertThat(comment2.compareTo(comment1), is(equalTo(0)));
  }

  @Test
  public void comments_with_different_id_and_same_text_are_unequal() {
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
  public void comments_with_same_id_and_different_text_are_unequal() {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setCommentId(1);
    comment1.setText("1");

    StashPullRequestComment comment2 = new StashPullRequestComment();
    comment2.setCommentId(1);
    comment2.setText("2");

    assertThat(comment1.equals(comment2), is(equalTo(false)));
    assertThat(comment2.equals(comment1), is(equalTo(false)));
    assertThat(comment1.compareTo(comment2), is(lessThan(0)));
    assertThat(comment2.compareTo(comment1), is(greaterThan(0)));
  }

  @Test
  public void id_comparison_takes_precedence_over_string_comparison() {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setCommentId(1);
    comment1.setText("1");

    StashPullRequestComment comment2 = new StashPullRequestComment();
    comment2.setCommentId(2);
    comment2.setText("2");

    assertThat(comment1.compareTo(comment2), is(lessThan(0)));
    assertThat(comment2.compareTo(comment1), is(greaterThan(0)));

    comment1.setCommentId(2);
    comment2.setCommentId(1);

    assertThat(comment1.compareTo(comment2), is(greaterThan(0)));
    assertThat(comment2.compareTo(comment1), is(lessThan(0)));
  }

  @Test
  public void comments_with_same_id_and_one_null_text_are_unequal() {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setCommentId(1);
    comment1.setText("1");

    StashPullRequestComment comment2 = new StashPullRequestComment();
    comment2.setCommentId(1);

    assertThat(comment1.equals(comment2), is(equalTo(false)));
    assertThat(comment2.equals(comment1), is(equalTo(false)));
  }

  @Test
  public void same_id_and_null_text_on_the_left_throws_in_compareTo() {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setCommentId(1);
    comment1.setText("1");

    StashPullRequestComment comment2 = new StashPullRequestComment();
    comment2.setCommentId(1);

    expectedException.expect(NullPointerException.class);
    comment2.compareTo(comment1);
  }

  @Test
  public void same_id_and_null_text_on_the_right_throws_in_compareTo() {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setCommentId(1);
    comment1.setText("1");

    StashPullRequestComment comment2 = new StashPullRequestComment();
    comment2.setCommentId(1);

    expectedException.expect(NullPointerException.class);
    comment1.compareTo(comment2);
  }

  @Test
  public void comments_with_same_id_and_null_text_on_both_sides_are_equal() {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setCommentId(1);

    StashPullRequestComment comment2 = new StashPullRequestComment();
    comment2.setCommentId(1);

    assertThat(comment1.equals(comment2), is(equalTo(true)));
    assertThat(comment2.equals(comment1), is(equalTo(true)));
    assertThat(comment1.compareTo(comment2), is(equalTo(0)));
    assertThat(comment2.compareTo(comment1), is(equalTo(0)));
  }

  @Test
  public void comments_with_different_id_and_null_text_are_unequal() {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setCommentId(1);
    comment1.setText("1");

    StashPullRequestComment comment2 = new StashPullRequestComment();
    comment2.setCommentId(2);

    assertThat(comment1.equals(comment2), is(equalTo(false)));
    assertThat(comment2.equals(comment1), is(equalTo(false)));
    assertThat(comment1.compareTo(comment2), is(lessThan(0)));
    assertThat(comment2.compareTo(comment1), is(greaterThan(0)));
  }

  @Test
  public void comments_with_null_id_not_equal_to_nonnull_id() {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setCommentId(1);

    StashPullRequestComment comment2 = new StashPullRequestComment();

    assertThat(comment1.equals(comment2), is(equalTo(false)));
    assertThat(comment2.equals(comment1), is(equalTo(false)));
  }

  @Test
  public void null_id_on_the_left_throws() {
    StashPullRequestComment comment1 = new StashPullRequestComment();

    StashPullRequestComment comment2 = new StashPullRequestComment();
    comment2.setCommentId(1);

    expectedException.expect(NullPointerException.class);
    comment1.compareTo(comment2);
  }

  @Test
  public void null_id_on_the_right_throws() {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setCommentId(1);

    StashPullRequestComment comment2 = new StashPullRequestComment();

    expectedException.expect(NullPointerException.class);
    assertThat(comment1.compareTo(comment2), is(equalTo(0)));
  }

  @Test
  public void comments_with_both_null_id_and_same_text_equal() {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    StashPullRequestComment comment2 = new StashPullRequestComment();

    assertThat(comment1.equals(comment2), is(equalTo(true)));
    assertThat(comment2.equals(comment1), is(equalTo(true)));
    assertThat(comment1.compareTo(comment2), is(equalTo(0)));
    assertThat(comment2.compareTo(comment1), is(equalTo(0)));
  }

  @Test
  public void comments_with_both_null_id_and_different_text_unequal() {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setText("1");

    StashPullRequestComment comment2 = new StashPullRequestComment();
    comment2.setText("2");

    assertThat(comment1.equals(comment2), is(equalTo(false)));
    assertThat(comment2.equals(comment1), is(equalTo(false)));
    assertThat(comment1.compareTo(comment2), is(lessThan(0)));
    assertThat(comment2.compareTo(comment1), is(greaterThan(0)));
  }
}
