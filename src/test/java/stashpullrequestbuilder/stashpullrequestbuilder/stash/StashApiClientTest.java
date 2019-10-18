package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;

import com.github.tomakehurst.wiremock.client.BasicCredentials;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.net.SocketException;
import java.util.List;
import org.apache.http.client.ClientProtocolException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashApiClient.StashApiException;

/*
 * Known issues:
 *
 * deletePullRequestComment() gives no indication whether the call has succeeded
 * There are no checks whether the HTTP code indicates an error for the specific request
 * POST requests throw on 204 No Content
 * Timeouts are not testable without very slow tests, as the timings are not configurable
 */

/** Created by nathan on 7/06/2015. */
public class StashApiClientTest {

  @Rule
  public WireMockRule wireMockRule =
      new WireMockRule(wireMockConfig().dynamicPort().dynamicHttpsPort());

  private StashApiClient client;

  private String projectName = "PROJ";
  private String repositoryName = "Repo";
  private String pullRequestId = "1337";
  private String commentId = "42";
  private String mergeVersion = "12345";

  @Before
  public void before() throws Exception {
    client =
        new StashApiClient(
            wireMockRule.baseUrl(), "Username", "Password", projectName, repositoryName, true);
  }

  private String pullRequestPath(int start) {
    return format(
        "/rest/api/1.0/projects/%s/repos/%s/pull-requests?start=%d",
        projectName, repositoryName, start);
  }

  private String pullRequestActivitiesPath(int start) {
    return format(
        "/rest/api/1.0/projects/%s/repos/%s/pull-requests/%s/activities?start=%d",
        projectName, repositoryName, pullRequestId, start);
  }

  private String pullRequestCommentPath(String commentId) {
    return format(
        "/rest/api/1.0/projects/%s/repos/%s/pull-requests/%s/comments/%s?version=0",
        projectName, repositoryName, pullRequestId, commentId);
  }

  private String pullRequestPostCommentPath() {
    return format(
        "/rest/api/1.0/projects/%s/repos/%s/pull-requests/%s/comments",
        projectName, repositoryName, pullRequestId);
  }

  private String pullRequestMergeStatusPath() {
    return format(
        "/rest/api/1.0/projects/%s/repos/%s/pull-requests/%s/merge",
        projectName, repositoryName, pullRequestId);
  }

  private String pullRequestMergePath(String version) {
    return format(
        "/rest/api/1.0/projects/%s/repos/%s/pull-requests/%s/merge?version=%s",
        projectName, repositoryName, pullRequestId, version);
  }

  private ResponseDefinitionBuilder jsonResponse(String filename) {
    return aResponse()
        .withStatus(200)
        .withBodyFile(filename)
        .withHeader("Content-Type", "application/json");
  }

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

  @Test
  public void getPullRequests_gets_empty_list() throws Exception {
    stubFor(get(pullRequestPath(0)).willReturn(jsonResponse("PullRequestListEmpty.json")));

    assertThat(client.getPullRequests(), is(empty()));
  }

  @Test
  public void getPullRequests_gets_pull_request() throws Exception {
    stubFor(get(pullRequestPath(0)).willReturn(jsonResponse("PullRequestListSingle.json")));

    List<StashPullRequestResponseValue> pullRequests = client.getPullRequests();

    assertThat(pullRequests, hasSize(1));
    StashPullRequestResponseValue pullRequest = pullRequests.get(0);

    assertThat(pullRequest.getClosed(), is(false));
    assertThat(pullRequest.getCreatedDate(), is("2017/11/20 8:20:59"));
    assertThat(pullRequest.getDescription(), is("Expand code to make it slower"));

    // If "id" is present, the branch name is set from it, "refs/" and "heads/" is removed
    assertThat(pullRequest.getFromRef().getBranch().getName(), is("abc"));
    assertThat(pullRequest.getFromRef().getId(), is("refs/heads/abc"));

    // "latestChangeset" overrides "latestCommit"
    assertThat(pullRequest.getFromRef().getLatestChangeset(), is("13"));
    assertThat(pullRequest.getFromRef().getLatestCommit(), is("13"));
    assertThat(pullRequest.getFromRef().getCommit().getHash(), is("13"));

    assertThat(pullRequest.getFromRef().getRepository().getRepository().getKey(), is("MyClone"));
    assertThat(pullRequest.getFromRef().getRepository().getSlug(), is("~Me"));
    assertThat(pullRequest.getId(), is("1001"));
    assertThat(pullRequest.getLocked(), is(false));
    assertThat(pullRequest.getState(), is("OPEN"));
    assertThat(pullRequest.getTitle(), is("Add some bloat"));

    // Branch name is read from "name" (not "Name") in absence of "id"
    assertThat(pullRequest.getToRef().getBranch().getName(), is("master"));
    assertThat(pullRequest.getToRef().getId(), is(nullValue()));

    // "latestCommit" is used in absence of "latestChangeset"
    assertThat(pullRequest.getToRef().getLatestChangeset(), is(nullValue()));
    assertThat(pullRequest.getToRef().getLatestCommit(), is("ABCD"));
    assertThat(pullRequest.getToRef().getCommit(), is(nullValue()));

    assertThat(pullRequest.getToRef().getRepository().getRepository().getKey(), is("PROJ"));
    assertThat(pullRequest.getToRef().getRepository().getSlug(), is("BigRepo"));
    assertThat(pullRequest.getUpdatedDate(), is("2018/02/03 15:33:14"));
    assertThat(pullRequest.getVersion(), is("3.11"));
  }

  @Test
  public void getPullRequests_gets_pull_requests_from_multiple_pages() throws Exception {
    stubFor(get(pullRequestPath(0)).willReturn(jsonResponse("PullRequestListPage1.json")));
    stubFor(get(pullRequestPath(4)).willReturn(jsonResponse("PullRequestListPage2.json")));
    stubFor(get(pullRequestPath(8)).willReturn(jsonResponse("PullRequestListPage3.json")));

    List<StashPullRequestResponseValue> pullRequests = client.getPullRequests();
    assertThat(pullRequests, hasSize(11)); // 4 + 4 + 3
    assertThat(pullRequests.get(0).getTitle(), is("First PR"));
    assertThat(pullRequests.get(10).getTitle(), is("Last PR"));
  }

  @Test
  public void getPullRequests_throws_on_not_found() throws Exception {
    stubFor(any(anyUrl()).willReturn(notFound()));

    assertThat(
        assertThrows(StashApiException.class, () -> client.getPullRequests()),
        allOf(
            hasProperty("cause", is(nullValue())),
            hasProperty(
                "message",
                containsString(
                    "Didn't get a 200 response from Stash PR GET! Response; '404' with message; Not Found"))));
  }

  @Test
  public void getPullRequests_throws_on_malformed_response() throws Exception {
    stubFor(any(anyUrl()).willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

    assertThat(
        assertThrows(StashApiException.class, () -> client.getPullRequests()),
        allOf(
            hasProperty("cause", is(instanceOf(ClientProtocolException.class))),
            hasProperty("message", containsString("Exception in GET request"))));
  }

  @Test
  public void getPullRequests_throws_on_connection_reset() throws Exception {
    stubFor(any(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    assertThat(
        assertThrows(StashApiException.class, () -> client.getPullRequests()),
        allOf(
            hasProperty("cause", is(instanceOf(SocketException.class))),
            hasProperty("message", containsString("Exception in GET request"))));
  }

  @Test
  public void getPullRequestComments_gets_empty_list() throws Exception {
    stubFor(
        get(pullRequestActivitiesPath(0))
            .willReturn(jsonResponse("PullRequestCommentsEmpty.json")));

    assertThat(
        client.getPullRequestComments(projectName, repositoryName, pullRequestId), is(empty()));
  }

  @Test
  public void getPullRequestComments_gets_pull_requests_from_multiple_pages() throws Exception {
    stubFor(
        get(pullRequestActivitiesPath(0))
            .willReturn(jsonResponse("PullRequestCommentsPage1.json")));
    stubFor(
        get(pullRequestActivitiesPath(4))
            .willReturn(jsonResponse("PullRequestCommentsPage2.json")));
    stubFor(
        get(pullRequestActivitiesPath(8))
            .willReturn(jsonResponse("PullRequestCommentsPage3.json")));

    List<StashPullRequestComment> comments =
        client.getPullRequestComments(projectName, repositoryName, pullRequestId);
    assertThat(comments, hasSize(10)); // 4 + 4 + 2, not counting a broken comment
    assertThat(comments.get(0).getText(), is("First comment"));
    assertThat(comments.get(9).getText(), is("Last comment"));
  }

  @Test
  public void getPullRequestComments_throws_on_not_found() throws Exception {
    stubFor(any(anyUrl()).willReturn(notFound()));

    assertThat(
        assertThrows(
            StashApiException.class,
            () -> client.getPullRequestComments(projectName, repositoryName, pullRequestId)),
        allOf(
            hasProperty("cause", is(nullValue())),
            hasProperty(
                "message",
                containsString(
                    "Didn't get a 200 response from Stash PR GET! Response; '404' with message; Not Found"))));
  }

  @Test
  public void getPullRequestComments_throws_on_malformed_response() throws Exception {
    stubFor(any(anyUrl()).willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

    assertThat(
        assertThrows(
            StashApiException.class,
            () -> client.getPullRequestComments(projectName, repositoryName, pullRequestId)),
        allOf(
            hasProperty("cause", is(instanceOf(ClientProtocolException.class))),
            hasProperty("message", containsString("Exception in GET request"))));
  }

  @Test
  public void getPullRequestComments_throws_on_connection_reset() throws Exception {
    stubFor(any(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    assertThat(
        assertThrows(
            StashApiException.class,
            () -> client.getPullRequestComments(projectName, repositoryName, pullRequestId)),
        allOf(
            hasProperty("cause", is(instanceOf(SocketException.class))),
            hasProperty("message", containsString("Exception in GET request"))));
  }

  @Test
  public void deletePullRequestComment_deletes_comment() throws Exception {
    stubFor(delete(pullRequestCommentPath(commentId)).willReturn(noContent()));

    client.deletePullRequestComment(pullRequestId, commentId);
  }

  @Test
  public void deletePullRequestComment_doesnt_throw_on_not_found() throws Exception {
    stubFor(any(anyUrl()).willReturn(notFound()));

    client.deletePullRequestComment(pullRequestId, commentId);
  }

  @Test
  public void deletePullRequestComment_throws_on_malformed_response() throws Exception {
    stubFor(any(anyUrl()).willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

    assertThat(
        assertThrows(
            StashApiException.class,
            () -> client.deletePullRequestComment(pullRequestId, commentId)),
        allOf(
            hasProperty("cause", is(instanceOf(ClientProtocolException.class))),
            hasProperty("message", containsString("Exception in DELETE request"))));
  }

  @Test
  public void deletePullRequestComment_throws_on_connection_reset() throws Exception {
    stubFor(any(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    assertThat(
        assertThrows(
            StashApiException.class,
            () -> client.deletePullRequestComment(pullRequestId, commentId)),
        allOf(
            hasProperty("cause", is(instanceOf(SocketException.class))),
            hasProperty("message", containsString("Exception in DELETE request"))));
  }

  @Test
  public void postPullRequestComment_posts_comment() throws Exception {
    stubFor(
        post(pullRequestPostCommentPath())
            .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
            .willReturn(jsonResponse("PostPullRequestComment.json")));

    StashPullRequestComment comment = client.postPullRequestComment(pullRequestId, "Some comment");
    assertThat(comment.getCommentId(), is(234));
    assertThat(comment.getText(), is("Build started"));

    verify(
        postRequestedFor(anyUrl())
            .withBasicAuth(new BasicCredentials("Username", "Password"))
            .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
            .withHeader("Connection", equalTo("close"))
            .withHeader("X-Atlassian-Token", equalTo("no-check"))
            .withRequestBody(equalToJson("{\"text\":\"Some comment\"}")));
  }

  @Test
  public void postPullRequestComment_throws_on_no_content() throws Exception {
    stubFor(post(pullRequestPostCommentPath()).willReturn(noContent()));

    assertThat(
        assertThrows(
            StashApiException.class,
            () -> client.postPullRequestComment(pullRequestId, "Some comment")),
        allOf(
            hasProperty("cause", is(nullValue())),
            hasProperty("message", containsString("No HTTP entity found in response"))));
  }

  @Test
  public void postPullRequestComment_throws_on_not_found() throws Exception {
    stubFor(any(anyUrl()).willReturn(notFound()));

    assertThat(
        assertThrows(
            StashApiException.class,
            () -> client.postPullRequestComment(pullRequestId, "Some comment")),
        allOf(
            hasProperty("cause", is(nullValue())),
            hasProperty(
                "message",
                containsString(
                    "Didn't get a 200 response from Stash PR POST! Response; '404' with message; Not Found"))));
  }

  @Test
  public void postPullRequestComment_throws_on_malformed_response() throws Exception {
    stubFor(any(anyUrl()).willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

    assertThat(
        assertThrows(
            StashApiException.class,
            () -> client.postPullRequestComment(pullRequestId, "Some comment")),
        allOf(
            hasProperty("cause", is(instanceOf(Exception.class))),
            hasProperty("message", containsString("Exception in POST request"))));
  }

  @Test
  public void postPullRequestComment_throws_on_connection_reset() throws Exception {
    stubFor(any(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    assertThat(
        assertThrows(
            StashApiException.class,
            () -> client.postPullRequestComment(pullRequestId, "Some comment")),
        allOf(
            hasProperty("cause", is(instanceOf(SocketException.class))),
            hasProperty("message", containsString("Exception in POST request"))));
  }

  @Test
  public void getPullRequestMergeStatus_gets_merge_status() throws Exception {
    stubFor(
        get(pullRequestMergeStatusPath()).willReturn(jsonResponse("PullRequestMergeStatus.json")));

    StashPullRequestMergeableResponse resp = client.getPullRequestMergeStatus(pullRequestId);
    assertThat(resp, is(notNullValue()));
    assertThat(resp.getCanMerge(), is(true));
    assertThat(resp.getConflicted(), is(true));
    assertThat(resp.getVetoes(), hasSize(2));
  }

  @Test
  public void getPullRequestMergeStatus_throws_on_not_found() throws Exception {
    stubFor(any(anyUrl()).willReturn(notFound()));

    assertThat(
        assertThrows(
            StashApiException.class, () -> client.getPullRequestMergeStatus(pullRequestId)),
        allOf(
            hasProperty("cause", is(nullValue())),
            hasProperty(
                "message",
                containsString(
                    "Didn't get a 200 response from Stash PR GET! Response; '404' with message; Not Found"))));
  }

  @Test
  public void getPullRequestMergeStatus_throws_on_malformed_response() throws Exception {
    stubFor(any(anyUrl()).willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

    assertThat(
        assertThrows(
            StashApiException.class, () -> client.getPullRequestMergeStatus(pullRequestId)),
        allOf(
            hasProperty("cause", is(instanceOf(ClientProtocolException.class))),
            hasProperty("message", containsString("Exception in GET request"))));
  }

  @Test
  public void getPullRequestMergeStatus_throws_on_connection_reset() throws Exception {
    stubFor(any(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    assertThat(
        assertThrows(
            StashApiException.class, () -> client.getPullRequestMergeStatus(pullRequestId)),
        allOf(
            hasProperty("cause", is(instanceOf(SocketException.class))),
            hasProperty("message", containsString("Exception in GET request"))));
  }

  @Test
  public void mergePullRequest_sends_merge_request() throws Exception {
    stubFor(
        post(pullRequestMergePath(mergeVersion))
            .willReturn(jsonResponse("PullRequestMerged.json")));

    assertThat(client.mergePullRequest(pullRequestId, mergeVersion).isPresent(), is(false));

    verify(
        postRequestedFor(anyUrl())
            .withBasicAuth(new BasicCredentials("Username", "Password"))
            .withHeader("Content-Type", absent())
            .withHeader("Connection", equalTo("close"))
            .withHeader("X-Atlassian-Token", equalTo("no-check"))
            .withRequestBody(absent()));
  }

  @Test
  public void mergePullRequest_throws_on_empty_response() throws Exception {
    stubFor(post(pullRequestMergePath(mergeVersion)).willReturn(noContent()));

    assertThat(
        assertThrows(
            StashApiException.class, () -> client.mergePullRequest(pullRequestId, mergeVersion)),
        allOf(
            hasProperty("cause", is(nullValue())),
            hasProperty("message", containsString("No HTTP entity found in response"))));
  }

  @Test
  public void mergePullRequest_returns_error_on_conflict() throws Exception {
    stubFor(any(anyUrl()).willReturn(jsonResponse("PullRequestMergeFailed.json").withStatus(409)));

    assertThat(
        client.mergePullRequest(pullRequestId, mergeVersion).orElse(""),
        containsString("A detailed error message."));
  }

  @Test
  public void mergePullRequest_throws_on_not_found() throws Exception {
    stubFor(any(anyUrl()).willReturn(notFound()));

    assertThat(
        assertThrows(
            StashApiException.class, () -> client.mergePullRequest(pullRequestId, mergeVersion)),
        allOf(
            hasProperty("cause", is(nullValue())),
            hasProperty(
                "message",
                containsString(
                    "Didn't get a 200 response from Stash PR POST! Response; '404' with message; Not Found"))));
  }

  @Test
  public void mergePullRequest_throws_on_malformed_response() throws Exception {
    stubFor(any(anyUrl()).willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

    assertThat(
        assertThrows(
            StashApiException.class, () -> client.mergePullRequest(pullRequestId, mergeVersion)),
        allOf(
            hasProperty("cause", is(instanceOf(ClientProtocolException.class))),
            hasProperty("message", containsString("Exception in POST request"))));
  }

  @Test
  public void mergePullRequest_throws_on_connection_reset() throws Exception {
    stubFor(any(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    assertThat(
        assertThrows(
            StashApiException.class, () -> client.mergePullRequest(pullRequestId, mergeVersion)),
        allOf(
            hasProperty("cause", is(instanceOf(SocketException.class))),
            hasProperty("message", containsString("Exception in POST request"))));
  }
}
