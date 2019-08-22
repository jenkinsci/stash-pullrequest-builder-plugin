package stashpullrequestbuilder.stashpullrequestbuilder;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.object.HasToString.hasToString;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class StashCauseTest {

  @Test
  public void short_description_doesnt_allow_injection() {

    StashCause stashCause =
        new StashCause(
            "",
            "",
            "",
            "",
            "",
            PULL_REQUEST_ID,
            OWNER,
            DESTINATION_REPOSITORY_NAME,
            PULL_REQUEST_TITLE,
            "",
            "",
            "",
            "",
            null);

    assertThat(
        stashCause.getShortDescription(),
        is(
            "<a href='&#x2f;projects&#x2f;owner&#x25;3C&#x25;3E&amp;&#x27;&#x25;22&#x2f;repos&#x2f;name&#x25;3C&#x25;3E&amp;&#x27;&#x25;22&#x2f;pull-requests&#x2f;id&#x25;3C&#x25;3E&amp;&#x27;&#x25;22'>"
                + "PR &#x23;id&lt;&gt;&amp;&#x27;&quot; title&lt;&gt;&amp;&#x27;&quot; </a>"));
  }

  @Test
  public void will_produce_valid_PR_url() {

    StashCause stashCause =
        new StashCause(
            "https://stash.host",
            "",
            "",
            "",
            "",
            PULL_REQUEST_ID,
            OWNER,
            DESTINATION_REPOSITORY_NAME,
            PULL_REQUEST_TITLE,
            "",
            "",
            "",
            "",
            null);

    assertThat(
        stashCause.getPrUrl(),
        hasToString(
            "https://stash.host/projects/owner%3C%3E&'%22/repos/name%3C%3E&'%22/pull-requests/id%3C%3E&'%22"));
  }

  @Test
  public void will_escape_PR_url() {

    StashCause stashCause =
        new StashCause(
            "https://stash.host",
            "",
            "",
            "",
            "",
            PULL_REQUEST_ID,
            OWNER,
            DESTINATION_REPOSITORY_NAME,
            PULL_REQUEST_TITLE,
            "",
            "",
            "",
            "",
            null);

    assertThat(
        stashCause.getEscapedUrl(),
        is(
            "https&#x3a;&#x2f;&#x2f;stash.host&#x2f;projects&#x2f;owner&#x25;3C&#x25;3E&amp;&#x27;&#x25;22&#x2f;repos&#x2f;name&#x25;3C&#x25;3E&amp;&#x27;&#x25;22&#x2f;pull-requests&#x2f;id&#x25;3C&#x25;3E&amp;&#x27;&#x25;22"));
  }

  @Test
  public void will_escape_PR_description() {

    StashCause stashCause =
        new StashCause(
            "https://stash.host",
            "",
            "",
            "",
            "",
            PULL_REQUEST_ID,
            OWNER,
            DESTINATION_REPOSITORY_NAME,
            PULL_REQUEST_TITLE,
            "",
            "",
            "",
            "",
            null);

    assertThat(
        stashCause.getEscapedDescription(),
        is("PR &#x23;id&lt;&gt;&amp;&#x27;&quot; title&lt;&gt;&amp;&#x27;&quot;"));
  }

  public static final String OWNER = "owner<>&'\"";
  public static final String DESTINATION_REPOSITORY_NAME = "name<>&'\"";
  public static final String PULL_REQUEST_ID = "id<>&'\"";
  public static final String PULL_REQUEST_TITLE = "title<>&'\"";
}
