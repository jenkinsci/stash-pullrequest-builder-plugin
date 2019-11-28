package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

public class StashPullRequestMergeableResponseTest {

  @Test
  public void testParsePullRequestMergeStatus() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    StashPullRequestMergeableResponse resp =
        mapper.readValue(
            "{\"canMerge\":false,\"conflicted\":false,\"vetoes\":[{\"summaryMessage\":\"Not approved\",\"detailedMessage\":\"Needs 2 approvals\"}]}",
            StashPullRequestMergeableResponse.class);
    assertThat(resp, is(notNullValue()));
    assertThat(resp.getCanMerge(), is(false));
    assertThat(resp.getConflicted(), is(false));
    assertThat(resp.getVetoes(), hasSize(1));
  }
}
