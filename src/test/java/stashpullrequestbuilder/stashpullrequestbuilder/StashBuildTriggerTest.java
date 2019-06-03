package stashpullrequestbuilder.stashpullrequestbuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.model.BooleanParameterDefinition;
import hudson.model.FileParameterDefinition;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

@RunWith(MockitoJUnitRunner.class)
public class StashBuildTriggerTest {

  @Rule public JenkinsRule jenkinsRule = new JenkinsRule();
  @Rule public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  private StashBuildTrigger trigger;

  private StashCause cause;
  private FreeStyleProject project;

  @Mock private ParametersDefinitionProperty parametersDefinitionProperty;

  @Before
  public void before() throws Exception {
    trigger =
        new StashBuildTrigger(
            "ProjectPath",
            "* * * * *",
            "StashHost",
            "CredentialsId",
            "ProjectCode",
            "RepositoryName",
            "CiSkipPhrases",
            false,
            false,
            false,
            false,
            false,
            false,
            "CiBuildPhrases",
            false,
            "TargetBranchesToBuild",
            false);

    project = spy(jenkinsRule.createFreeStyleProject());
    trigger.start(project, true);
  }

  private StashCause makeCause(Map<String, String> additionalParameters) {
    return new StashCause(
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
        additionalParameters);
  }

  private void jobSetup(ParameterDefinition... parameterDefinitions) {
    when(project.getProperty(ParametersDefinitionProperty.class))
        .thenReturn(parametersDefinitionProperty);
    when(parametersDefinitionProperty.getParameterDefinitions())
        .thenReturn(Arrays.asList(parameterDefinitions));
  }

  private List<ParameterValue> captureBuildParameters() {
    ArgumentCaptor<ParametersAction> captor = ArgumentCaptor.forClass(ParametersAction.class);
    assertThat(trigger.startJob(cause), is(notNullValue()));
    verify(project, times(1)).scheduleBuild2(anyInt(), eq(cause), captor.capture());
    ParametersAction parametersAction = captor.getValue();
    return parametersAction.getAllParameters();
  }

  @Test
  public void startJobPassesParameterWithDefaultValue() {
    cause = makeCause(null);
    ParameterDefinition parameterDefinition =
        new StringParameterDefinition("param1", "param1_default");
    jobSetup(parameterDefinition);

    List<ParameterValue> parameters = captureBuildParameters();

    assertThat(parameters, hasSize(1));
    assertThat(parameters.get(0).getName(), is("param1"));
    assertThat(parameters.get(0).getValue(), is("param1_default"));
  }

  @Test
  public void startJobPassesParameterWithValueFromStashCause() {
    Map<String, String> prParameters = new TreeMap<>();
    prParameters.put("param1", "param1_value");
    cause = makeCause(prParameters);

    ParameterDefinition parameterDefinition =
        new StringParameterDefinition("param1", "param1_default");
    jobSetup(parameterDefinition);

    List<ParameterValue> parameters = captureBuildParameters();

    assertThat(parameters, hasSize(1));
    assertThat(parameters.get(0).getName(), is("param1"));
    assertThat(parameters.get(0).getValue(), is("param1_value"));
  }

  @Test
  public void startJobIgnoresParameterWithMismatchingName() {
    Map<String, String> prParameters = new TreeMap<>();
    prParameters.put("param2", "param2_value");
    cause = makeCause(prParameters);

    ParameterDefinition parameterDefinition =
        new StringParameterDefinition("param1", "param1_default");
    jobSetup(parameterDefinition);

    List<ParameterValue> parameters = captureBuildParameters();

    assertThat(parameters, hasSize(1));
    assertThat(parameters.get(0).getName(), is("param1"));
    assertThat(parameters.get(0).getValue(), is("param1_default"));
  }

  @Test
  public void startJobReplacesValueOfNonStringParameter() {
    Map<String, String> prParameters = new TreeMap<>();
    prParameters.put("param1", "param1_value");
    cause = makeCause(prParameters);

    ParameterDefinition parameterDefinition =
        new BooleanParameterDefinition("param1", false, "parameter 1");
    jobSetup(parameterDefinition);

    List<ParameterValue> parameters = captureBuildParameters();

    assertThat(parameters, hasSize(1));
    assertThat(parameters.get(0), instanceOf(StringParameterValue.class));
    assertThat(parameters.get(0).getName(), is("param1"));
    assertThat(parameters.get(0).getValue(), is("param1_value"));
  }

  @Test
  public void startJobSkipsNullParameters() {
    cause = makeCause(null);

    ParameterDefinition parameterDefinition = new FileParameterDefinition("param1", "parameter 1");
    jobSetup(parameterDefinition);

    List<ParameterValue> parameters = captureBuildParameters();

    assertThat(parameters, is(empty()));
  }
}
