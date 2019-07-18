package stashpullrequestbuilder.stashpullrequestbuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.TopLevelItem;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

@RunWith(MockitoJUnitRunner.class)
public class StashPollingActionFactoryTest {

  @Rule public JenkinsRule jenkinsRule = new JenkinsRule();
  @Rule public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  private StashPollingActionFactory factory = new StashPollingActionFactory();

  @Test
  public void type_returns_TopLevelItem() {
    assertThat(factory.type(), is(equalTo(TopLevelItem.class)));
  }

  @Test
  public void createFor_returns_empty_list_for_TopLevelItem() {
    TopLevelItem item = mock(TopLevelItem.class);

    assertThat(factory.createFor(item), is(empty()));
  }

  @Test
  public void createFor_returns_empty_list_for_FreeStyleProject() {
    FreeStyleProject project = mock(FreeStyleProject.class);

    assertThat(factory.createFor(project), is(empty()));
  }

  @Test
  public void createFor_returns_empty_list_if_no_StashPollingAction() throws Exception {
    StashBuildTrigger trigger = mock(StashBuildTrigger.class);
    FreeStyleProject project = spy(jenkinsRule.createFreeStyleProject());

    Map<TriggerDescriptor, Trigger<?>> triggerMap = new HashMap<>();
    triggerMap.put(StashBuildTrigger.descriptor, trigger);

    when(project.getTriggers()).thenReturn(triggerMap);
    when(trigger.getStashPollingAction()).thenReturn(null);

    assertThat(factory.createFor(project), is(empty()));
  }

  @Test
  public void createFor_returns_action_for_project_with_StashBuildTrigger() throws Exception {
    StashBuildTrigger trigger = mock(StashBuildTrigger.class);
    FreeStyleProject project = spy(jenkinsRule.createFreeStyleProject());
    StashPollingAction stashPollingAction = new StashPollingAction(project);

    Map<TriggerDescriptor, Trigger<?>> triggerMap = new HashMap<>();
    triggerMap.put(StashBuildTrigger.descriptor, trigger);

    when(project.getTriggers()).thenReturn(triggerMap);
    when(trigger.getStashPollingAction()).thenReturn(stashPollingAction);

    Collection<? extends Action> actions = factory.createFor(project);
    assertThat(actions, contains(stashPollingAction));
  }
}
