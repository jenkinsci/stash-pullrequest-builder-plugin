package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.Test;

/** Created by nathan on 7/06/2015. */
public class StashApiClientTest {

  @Test
  public void testParsePullRequestMergeStatus() throws Exception {
    StashPullRequestMergeableResponse resp =
        StashApiClient.parsePullRequestMergeStatus(
            "{\"canMerge\":false,\"conflicted\":false,\"vetoes\":[{\"summaryMessage\":\"You may not merge after 6pm on a Friday.\",\"detailedMessage\":\"It is likely that your Blood Alcohol Content (BAC) exceeds the threshold for making sensible decisions regarding pull requests. Please try again on Monday.\"}]}");
    assertThat(resp, is(notNullValue()));
    assertThat(resp.getCanMerge(), is(false));
    assertThat(resp.getConflicted(), is(false));
    assertThat(resp.getVetoes(), hasSize(1));
  }
}
