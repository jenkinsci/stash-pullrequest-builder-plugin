package stashpullrequestbuilder.stashpullrequestbuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.any;
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
import hudson.model.Queue;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import jenkins.model.Jenkins;
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
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashApiClient;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashApiClient.StashApiException;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestComment;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValue;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValueRepository;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValueRepositoryBranch;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValueRepositoryRepository;

@RunWith(MockitoJUnitRunner.class)
public class StashRepositoryTest {

  private StashRepository stashRepository;

  private StashCause cause;
  private FreeStyleProject project;
  private StashPullRequestResponseValue pullRequest;
  private StashPullRequestResponseValueRepository repository;
  private StashPullRequestResponseValueRepositoryBranch branch;
  private StashPullRequestResponseValueRepositoryRepository repoRepo;
  private List<StashPullRequestResponseValue> pullRequestList;

  @Mock private StashBuildTrigger trigger;
  @Mock private StashApiClient stashApiClient;
  @Mock private ParametersDefinitionProperty parametersDefinitionProperty;

  @Rule public JenkinsRule jenkinsRule = new JenkinsRule();
  @Rule public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Before
  public void before() throws Exception {
    project = spy(jenkinsRule.createFreeStyleProject());
    stashRepository = new StashRepository(project, trigger, stashApiClient);

    branch = new StashPullRequestResponseValueRepositoryBranch();
    branch.setName("feature/add-bloat");

    repoRepo = new StashPullRequestResponseValueRepositoryRepository();

    repository = new StashPullRequestResponseValueRepository();
    repository.setBranch(branch);
    repository.setRepository(repoRepo);

    pullRequest = new StashPullRequestResponseValue();
    pullRequest.setFromRef(repository);
    pullRequest.setToRef(repository);
    pullRequest.setState("OPEN");
    pullRequest.setTitle("Add some bloat");

    pullRequestList = Collections.singletonList(pullRequest);
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
    Queue.Item item = stashRepository.startJob(cause);
    assertThat(item, is(notNullValue()));

    ParametersAction parametersAction = item.getAction(ParametersAction.class);
    assertThat(parametersAction, is(notNullValue()));

    return parametersAction.getAllParameters();
  }

  @Test
  public void getTargetPullRequestsReturnsEmptyListForNoPullRequests() throws Exception {
    when(stashApiClient.getPullRequests()).thenReturn(Collections.emptyList());

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsAcceptsOpenPullRequests() throws Exception {
    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getTargetPullRequestsSkipsMergedPullRequests() throws Exception {
    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    pullRequest.setState("MERGED");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsSkipsNullStatePullRequests() throws Exception {
    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    pullRequest.setState(null);

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsAcceptsMatchingBranches() throws Exception {
    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(trigger.getTargetBranchesToBuild()).thenReturn("release/.*,feature/.*,testing/.*");

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getTargetPullRequestsAcceptsMatchingBranchesWithPadding() throws Exception {
    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(trigger.getTargetBranchesToBuild())
        .thenReturn("\trelease/.*, \n\tfeature/.* \r\n, testing/.*\r");

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getTargetPullRequestsSkipsMismatchingBranches() throws Exception {
    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(trigger.getTargetBranchesToBuild()).thenReturn("release/.*,testing/.*");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsAcceptsAnyBranchIfBranchesToBuildIsEmpty() throws Exception {
    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(trigger.getTargetBranchesToBuild()).thenReturn("");

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getTargetPullRequestsAcceptsAnyBranchIfBranchesToBuildIsNull() throws Exception {
    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(trigger.getTargetBranchesToBuild()).thenReturn(null);

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getTargetPullRequestsSkipsOnSkipPhraseInTitle() throws Exception {
    pullRequest.setTitle("NO TEST");

    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsSkipsOnSkipPhraseInComments() throws Exception {
    StashPullRequestComment comment = new StashPullRequestComment();
    comment.setText("NO TEST");
    List<StashPullRequestComment> comments = Collections.singletonList(comment);

    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(stashApiClient.getPullRequestComments(any(), any(), any())).thenReturn(comments);
    when(project.getDisplayName()).thenReturn("Pull Request Builder Project");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsPrioritizesLatestComments() throws Exception {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setCommentId(1);
    comment1.setText("NO TEST");

    StashPullRequestComment comment2 = new StashPullRequestComment();
    comment2.setCommentId(2);
    comment2.setText("DO TEST");

    List<StashPullRequestComment> comments = Arrays.asList(comment1, comment2);

    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(trigger.getCiBuildPhrases()).thenReturn("DO TEST");
    when(stashApiClient.getPullRequestComments(any(), any(), any())).thenReturn(comments);
    when(project.getDisplayName()).thenReturn("Pull Request Builder Project");

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getTargetPullRequestsSkipPhraseIsCaseInsensitive() throws Exception {
    pullRequest.setTitle("Disable any testing");

    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("disable ANY Testing");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsSkipPhraseMatchedAsSubstring() throws Exception {
    pullRequest.setTitle("This will get no testing whatsoever");

    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsSupportsMultipleSkipPhrasesAndPadding() throws Exception {
    pullRequest.setTitle("This will get no testing whatsoever");

    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("\tuntestable , \n NO TEST\t, \r\ndon't worry!");

    assertThat(stashRepository.getTargetPullRequests(), empty());
  }

  @Test
  public void getTargetPullRequestsBuildsIfSkipPhraseIsEmpty() throws Exception {
    pullRequest.setTitle("NO TEST");

    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("");

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getTargetPullRequestsBuildsIfSkipPhraseIsNull() throws Exception {
    pullRequest.setTitle("NO TEST");

    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn(null);

    assertThat(stashRepository.getTargetPullRequests(), contains(pullRequest));
  }

  @Test
  public void getAdditionalParametersUsesNewestParameterDefinition() throws Exception {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setCommentId(1);
    comment1.setText("p:key=value1");

    StashPullRequestComment comment2 = new StashPullRequestComment();
    comment2.setCommentId(2);
    comment2.setText("p:key=value2");

    List<StashPullRequestComment> comments = Arrays.asList(comment1, comment2);
    when(stashApiClient.getPullRequestComments(any(), any(), any())).thenReturn(comments);

    Map<String, String> parameters = stashRepository.getAdditionalParameters(pullRequest);

    assertThat(parameters, is(aMapWithSize(1)));
    assertThat(parameters, hasEntry("key", "value2"));
  }

  @Test
  public void getAdditionalParametersUsesNewestParameterDefinitionRegardlessOfListOrder()
      throws Exception {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setCommentId(1);
    comment1.setText("p:key=value1");

    StashPullRequestComment comment2 = new StashPullRequestComment();
    comment2.setCommentId(2);
    comment2.setText("p:key=value2");

    List<StashPullRequestComment> comments = Arrays.asList(comment2, comment1);
    when(stashApiClient.getPullRequestComments(any(), any(), any())).thenReturn(comments);

    Map<String, String> parameters = stashRepository.getAdditionalParameters(pullRequest);

    assertThat(parameters, is(aMapWithSize(1)));
    assertThat(parameters, hasEntry("key", "value2"));
  }

  @Test
  public void addFutureBuildTasksRemovesOldBuildFinishedCommentsIfEnabled() throws Exception {
    StashPullRequestComment comment1 = new StashPullRequestComment();
    comment1.setCommentId(1);
    comment1.setText("[*BuildFinished* **MyProject**] DEADBEEF into 1BADFACE");

    StashPullRequestComment comment2 = new StashPullRequestComment();
    comment2.setCommentId(2);
    comment2.setText("User comment");

    StashPullRequestComment response = new StashPullRequestComment();
    response.setCommentId(3);

    List<StashPullRequestComment> comments = Arrays.asList(comment2, comment1);
    when(stashApiClient.getPullRequestComments(any(), any(), any())).thenReturn(comments);
    when(stashApiClient.postPullRequestComment(any(), any())).thenReturn(response);

    when(trigger.getDeletePreviousBuildFinishComments()).thenReturn(true);
    when(trigger.getStashHost()).thenReturn("StashHost");
    when(project.getDisplayName()).thenReturn("MyProject");

    pullRequest.setId("123");
    stashRepository.addFutureBuildTasks(pullRequestList);

    verify(stashApiClient, times(1)).deletePullRequestComment(eq("123"), eq("1"));
    verify(stashApiClient, times(0)).deletePullRequestComment(eq("123"), eq("2"));
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

  @Test
  public void parameters_populated_from_StashCause() {
    cause = makeCause(null);

    ParameterDefinition parameterDefinition =
        new StringParameterDefinition("sourceBranch", "DefaultBranch");
    jobSetup(parameterDefinition);

    List<ParameterValue> parameters = captureBuildParameters();

    assertThat(
        parameters, contains(new StringParameterValue("sourceBranch", cause.getSourceBranch())));
  }

  @Test
  public void parameters_from_pull_request_comments_overridden_by_StashCause() {
    Map<String, String> prParameters = new TreeMap<>();
    prParameters.put("sourceBranch", "BranchFromPrComment");
    cause = makeCause(prParameters);

    ParameterDefinition parameterDefinition =
        new StringParameterDefinition("sourceBranch", "DefaultBranch");
    jobSetup(parameterDefinition);

    List<ParameterValue> parameters = captureBuildParameters();

    assertThat(
        parameters, contains(new StringParameterValue("sourceBranch", cause.getSourceBranch())));
  }

  @Test
  public void getTargetPullRequests_returns_empty_if_getPullRequests_throws() throws Exception {
    when(stashApiClient.getPullRequests()).thenThrow(new StashApiException("cannot read PR list"));

    assertThat(stashRepository.getTargetPullRequests(), is(empty()));
  }

  @Test
  public void getTargetPullRequests_skips_pull_request_if_getPullRequestComments_throws()
      throws Exception {
    when(stashApiClient.getPullRequests()).thenReturn(pullRequestList);
    when(trigger.getCiSkipPhrases()).thenReturn("NO TEST");
    when(stashApiClient.getPullRequestComments(any(), any(), any()))
        .thenThrow(new StashApiException("cannot read PR comments"));

    assertThat(stashRepository.getTargetPullRequests(), is(empty()));
  }

  @Test
  public void addFutureBuildTasks_skips_scheduling_build_if_getPullRequestComments_throws()
      throws Exception {
    when(stashApiClient.getPullRequestComments(any(), any(), any()))
        .thenThrow(new StashApiException("cannot read PR comments"));

    stashRepository.addFutureBuildTasks(pullRequestList);

    assertThat(Jenkins.getInstance().getQueue().getItems(), is(emptyArray()));
  }
}
