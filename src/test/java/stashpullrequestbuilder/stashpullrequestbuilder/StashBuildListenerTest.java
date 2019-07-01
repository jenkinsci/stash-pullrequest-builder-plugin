package stashpullrequestbuilder.stashpullrequestbuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

@RunWith(MockitoJUnitRunner.class)
public class StashBuildListenerTest {

  @Rule public JenkinsRule jenkinsRule = new JenkinsRule();
  @Rule public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  private StashBuildListener stashBuildListener;

  private StashCause stashCause;
  private TaskListener taskListener;
  private PrintStream printStream;
  private ByteArrayOutputStream buildLogBuffer;

  @Mock private FreeStyleBuild build;

  @Before
  public void before() throws Exception {
    stashBuildListener = new StashBuildListener();

    buildLogBuffer = new ByteArrayOutputStream();
    printStream = new PrintStream(buildLogBuffer, true, "UTF-8");
    taskListener = new StreamTaskListener(printStream, null);

    stashCause =
        new StashCause(
            "StashHost",
            "SourceBranch",
            "TargetBranch",
            "SourceRepositoryOwner",
            "SourceRepositoryName",
            "PullRequestId",
            "DestinationRepositoryOwner",
            "DestinationRepositoryName",
            "PullRequestTitle",
            "SourceCommitHash",
            "DestinationCommitHash",
            "BuildStartCommentId",
            "PullRequestVersion",
            null);
  }

  @Test
  public void onStarted_sets_description() throws Exception {
    when(build.getCause(eq(StashCause.class))).thenReturn(stashCause);

    stashBuildListener.onStarted(build, taskListener);

    verify(build, times(1)).setDescription(anyString());
  }

  @Test
  public void onStarted_doesnt_set_description_if_there_is_no_StashCause() throws Exception {
    when(build.getCause(eq(StashCause.class))).thenReturn(null);

    stashBuildListener.onStarted(build, taskListener);

    verify(build, never()).setDescription(anyString());
  }

  @Test
  public void onStarted_writes_to_build_log_on_exception() throws Exception {
    when(build.getCause(eq(StashCause.class))).thenReturn(stashCause);
    doThrow(new IOException("Bad Description")).when(build).setDescription(anyString());

    stashBuildListener.onStarted(build, taskListener);

    String buildLog = new String(buildLogBuffer.toByteArray(), StandardCharsets.UTF_8);
    String[] buildLogLines = buildLog.split("\\r?\\n|\\r");
    assertThat(buildLogLines.length, greaterThanOrEqualTo(3));
    assertThat(buildLogLines[0], is("Can't update build description"));
    assertThat(buildLogLines[1], is("java.io.IOException: Bad Description"));
    for (int i = 2; i < buildLogLines.length; i++) {
      assertThat(buildLogLines[i], matchesPattern("^\\tat [A-Za-z0-9_$.]+\\(.*\\)$"));
    }
  }

  @Test
  public void onCompleted_posts_finished_comment() throws Exception {
    final String duration = "2 seconds";
    final int buildNumber = 123;

    StashBuildTrigger trigger = mock(StashBuildTrigger.class);
    StashPullRequestsBuilder builder = mock(StashPullRequestsBuilder.class);
    StashRepository repository = mock(StashRepository.class);
    FreeStyleProject project = spy(jenkinsRule.createFreeStyleProject());

    Map<TriggerDescriptor, Trigger<?>> triggerMap = new HashMap<TriggerDescriptor, Trigger<?>>();
    triggerMap.put(StashBuildTrigger.descriptor, trigger);

    when(build.getCause(eq(StashCause.class))).thenReturn(stashCause);
    when(build.getParent()).thenReturn(project);
    when(project.getTriggers()).thenReturn(triggerMap);
    when(trigger.getBuilder()).thenReturn(builder);
    when(builder.getRepository()).thenReturn(repository);
    when(build.getResult()).thenReturn(Result.SUCCESS);
    when(build.getDurationString()).thenReturn(duration);
    when(build.getNumber()).thenReturn(buildNumber);

    stashBuildListener.onCompleted(build, taskListener);

    verify(repository, times(1))
        .postFinishedComment(
            eq(stashCause.getPullRequestId()),
            eq(stashCause.getSourceCommitHash()),
            eq(stashCause.getDestinationCommitHash()),
            eq(Result.SUCCESS),
            startsWith("http://localhost"),
            eq(buildNumber),
            eq(""),
            eq(duration));
  }

  @Test
  public void onCompleted_doesnt_check_build_result_if_there_is_no_StashCause() throws Exception {
    when(build.getCause(eq(StashCause.class))).thenReturn(null);

    stashBuildListener.onCompleted(build, taskListener);

    verify(build, never()).getResult();
  }
}
