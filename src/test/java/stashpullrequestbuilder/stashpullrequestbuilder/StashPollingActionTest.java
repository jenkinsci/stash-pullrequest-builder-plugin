package stashpullrequestbuilder.stashpullrequestbuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import hudson.model.FreeStyleProject;
import java.io.IOException;
import org.apache.commons.jelly.XMLOutput;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

@RunWith(MockitoJUnitRunner.class)
public class StashPollingActionTest {

  @Rule public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Mock FreeStyleProject project;
  private StashPollingAction stashPollingAction;

  @Before
  public void before() {
    stashPollingAction = new StashPollingAction(project);
  }

  @Test
  public void getIconFileName_returns_expected_value() {
    assertThat(stashPollingAction.getIconFileName(), is(equalTo("clipboard.png")));
  }

  @Test
  public void getDisplayName_returns_expected_value() {
    assertThat(stashPollingAction.getDisplayName(), is(equalTo("Polling Log")));
  }

  @Test
  public void getUrlName_returns_expected_value() {
    assertThat(stashPollingAction.getUrlName(), is(equalTo("stash-polling")));
  }

  @Test
  public void getOwner_returns_expected_value() {
    assertThat(stashPollingAction.getOwner(), is(equalTo(project)));
  }

  @Test
  public void log_records_string_with_newline() {
    stashPollingAction.log("Some message");
    assertThat(stashPollingAction.toString(), matchesPattern("^Some message(\\r?\\n|\\r)$"));
  }

  @Test
  public void log_formats_arguments() {
    stashPollingAction.log("{}: found {} items", "Finder", 5);
    assertThat(
        stashPollingAction.toString(), matchesPattern("^Finder: found 5 items(\\r?\\n|\\r)$"));
  }

  @Test
  public void log_records_string_and_exception() {
    IOException e = new IOException("Read Error");
    stashPollingAction.log("{}, {} and {}", "one", "two", "three", e);

    String[] logLines = stashPollingAction.toString().split("\\r?\\n|\\r");
    assertThat(logLines.length, is(greaterThanOrEqualTo(3)));
    assertThat(logLines[0], is(equalTo("one, two and three")));
    assertThat(logLines[1], is(equalTo("java.io.IOException: Read Error")));
    for (int i = 2; i < logLines.length; i++) {
      assertThat(logLines[i], matchesPattern("^\\tat [A-Za-z0-9_$.]+\\(.*\\)$"));
    }
  }

  @Test
  public void log_appends_messages() {
    stashPollingAction.log("First message");
    stashPollingAction.log("Second message");
    String[] logLines = stashPollingAction.toString().split("\\r?\\n|\\r");
    assertThat(logLines.length, is(equalTo(2)));
    assertThat(logLines[0], is(equalTo("First message")));
    assertThat(logLines[1], is(equalTo("Second message")));
  }

  @Test
  public void reset_clears_messages() {
    stashPollingAction.log("First message");
    stashPollingAction.resetLog();
    stashPollingAction.log("Second message");
    String[] logLines = stashPollingAction.toString().split("\\r?\\n|\\r");
    assertThat(logLines.length, is(equalTo(1)));
    assertThat(logLines[0], is(equalTo("Second message")));
  }

  @Test
  public void writeLogTo_writes_log_without_escaping() throws Exception {
    XMLOutput xmlOut = mock(XMLOutput.class);
    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);

    stashPollingAction.log("\"Fish\" & <i>chips</i>");
    stashPollingAction.writeLogTo(xmlOut);
    verify(xmlOut, times(1)).write(stringCaptor.capture());

    assertThat(stringCaptor.getValue(), matchesPattern("^\"Fish\" & <i>chips</i>(\\r?\\n|\\r)$"));
  }
}
