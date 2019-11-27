package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hudson.Util;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;

/** Created by Nathan McCarthy */
public class StashApiClient {

  // Connection timeout: maximum time for connecting to the HTTP server.
  private static final int HTTP_CONNECTION_TIMEOUT_SECONDS = 15;

  // Socket timeout: maximum period of inactivity between two data packets
  // arriving to the client once the connection is established.
  private static final int HTTP_SOCKET_TIMEOUT_SECONDS = 30;

  private static final Logger logger =
      Logger.getLogger(MethodHandles.lookup().lookupClass().getName());
  private static final ObjectMapper mapper = new ObjectMapper();

  private String apiBaseUrl;

  private String project;
  private String repositoryName;
  private Credentials credentials;
  private boolean ignoreSsl;

  public StashApiClient(
      String stashHost,
      String username,
      String password,
      String project,
      String repositoryName,
      boolean ignoreSsl) {
    this.credentials = new UsernamePasswordCredentials(username, password);
    this.project = project;
    this.repositoryName = repositoryName;
    this.apiBaseUrl = stashHost.replaceAll("/$", "") + "/rest/api/1.0/projects/";
    this.ignoreSsl = ignoreSsl;
  }

  @Nonnull
  public List<StashPullRequestResponseValue> getPullRequests() throws StashApiException {
    List<StashPullRequestResponseValue> pullRequestResponseValues = new ArrayList<>();
    try {
      boolean isLastPage = false;
      int start = 0;
      while (!isLastPage) {
        String response = getRequest(pullRequestsPath(start));
        StashPullRequestResponse parsedResponse =
            mapper.readValue(response, StashPullRequestResponse.class);
        isLastPage = parsedResponse.getIsLastPage();
        if (!isLastPage) {
          start = parsedResponse.getNextPageStart();
        }
        pullRequestResponseValues.addAll(parsedResponse.getPrValues());
      }
      return pullRequestResponseValues;
    } catch (IOException e) {
      throw new StashApiException("Cannot read list of pull requests", e);
    }
  }

  @Nonnull
  public List<StashPullRequestComment> getPullRequestComments(
      String projectCode, String commentRepositoryName, String pullRequestId)
      throws StashApiException {

    try {
      boolean isLastPage = false;
      int start = 0;
      List<StashPullRequestComment> comments = new ArrayList<>();
      while (!isLastPage) {
        String response =
            getRequest(
                apiBaseUrl
                    + projectCode
                    + "/repos/"
                    + commentRepositoryName
                    + "/pull-requests/"
                    + pullRequestId
                    + "/activities?start="
                    + start);
        StashPullRequestActivityResponse parsedResponse =
            mapper.readValue(response, StashPullRequestActivityResponse.class);

        List<StashPullRequestActivity> prValues = parsedResponse.getPrValues();
        if (prValues != null) {
          for (StashPullRequestActivity activity : prValues) {
            if (activity != null && activity.getComment() != null) {
              comments.add(activity.getComment());
            }
          }
        }

        isLastPage = parsedResponse.getIsLastPage();
        if (!isLastPage) {
          start = parsedResponse.getNextPageStart();
        }
      }

      return comments;
    } catch (IOException e) {
      throw new StashApiException(
          format(
              "%s/%s: cannot read comments for pull request %s",
              projectCode, commentRepositoryName, pullRequestId),
          e);
    }
  }

  public void deletePullRequestComment(String pullRequestId, String commentId)
      throws StashApiException {
    String path = pullRequestPath(pullRequestId) + "/comments/" + commentId + "?version=0";
    deleteRequest(path);
  }

  @Nullable
  public StashPullRequestComment postPullRequestComment(String pullRequestId, String comment)
      throws StashApiException {
    String path = pullRequestPath(pullRequestId) + "/comments";
    String response = postRequest(path, comment);
    try {
      return mapper.readValue(response, StashPullRequestComment.class);
    } catch (IOException e) {
      throw new StashApiException("Cannot parse reply after comment posting", e);
    }
  }

  @Nullable
  public StashPullRequestMergeableResponse getPullRequestMergeStatus(String pullRequestId)
      throws StashApiException {
    String path = pullRequestPath(pullRequestId) + "/merge";
    String response = getRequest(path);
    try {
      return mapper.readValue(response, StashPullRequestMergeableResponse.class);
    } catch (IOException e) {
      throw new StashApiException("Cannot parse merge status", e);
    }
  }

  @Nonnull
  public Optional<String> mergePullRequest(String pullRequestId, String version)
      throws StashApiException {
    String path = pullRequestPath(pullRequestId) + "/merge?version=" + version;
    String response = postRequest(path, null);

    try {
      StashPullRequestResponseValue parsedResponse =
          mapper.readValue(response, StashPullRequestResponseValue.class);

      if ("MERGED".equals(parsedResponse.getState())) {
        // Successful merge
        return Optional.empty();
      }
    } catch (IOException e) {
      throw new StashApiException("Cannot parse merge response", e);
    }

    // Return the whole response for the purpose of logging
    return Optional.of(response);
  }

  private CloseableHttpClient getHttpClient() throws StashApiException {
    HttpClientBuilder httpClientBuilder = HttpClientBuilder.create().useSystemProperties();

    if (this.ignoreSsl) {
      try {
        SSLContextBuilder sslContextBuilder = new SSLContextBuilder();

        sslContextBuilder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
        SSLConnectionSocketFactory sslConnectionSocketFactory =
            new SSLConnectionSocketFactory(
                sslContextBuilder.build(), NoopHostnameVerifier.INSTANCE);
        httpClientBuilder.setSSLSocketFactory(sslConnectionSocketFactory);
      } catch (KeyManagementException | KeyStoreException | NoSuchAlgorithmException e) {
        throw new StashApiException("Failed to setup the SSLConnectionFactory", e);
      }
    }

    RequestConfig.Builder requestBuilder =
        RequestConfig.custom()
            .setConnectTimeout(HTTP_CONNECTION_TIMEOUT_SECONDS * 1000)
            .setSocketTimeout(HTTP_SOCKET_TIMEOUT_SECONDS * 1000);
    httpClientBuilder.setDefaultRequestConfig(requestBuilder.build());

    return httpClientBuilder.build();
  }

  @Nonnull
  private static String entityAsString(HttpResponse httpResponse) throws StashApiException {
    HttpEntity entity = httpResponse.getEntity();
    if (entity == null) {
      throw new StashApiException("No HTTP entity found in response");
    }

    try {
      return Util.fixNull(EntityUtils.toString(entity, Consts.UTF_8));
    } catch (IOException e) {
      throw new StashApiException("Cannot decode HTTP response contents", e);
    }
  }

  @Nonnull
  private String getRequest(String path) throws StashApiException {
    logger.log(Level.FINEST, "PR-GET-REQUEST:" + path);
    CloseableHttpClient client = getHttpClient();

    HttpGet request = new HttpGet(path);
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html; section 14.10.
    // tells the server that we want it to close the connection when it has sent the response.
    // address large amount of close_wait sockets client and fin sockets server side
    request.addHeader("Connection", "close");

    try {
      request.addHeader(new BasicScheme().authenticate(credentials, request, null));
      HttpResponse httpResponse = client.execute(request);
      int responseCode = httpResponse.getStatusLine().getStatusCode();
      if (!validResponseCode(responseCode)) {
        logger.log(Level.SEVERE, "Failing to get response from Stash PR GET" + request.getURI());
        throw new StashApiException(
            "Didn't get a 200 response from Stash PR GET! Response; '"
                + responseCode
                + "' with message; "
                + httpResponse.getStatusLine().getReasonPhrase());
      }

      String response = entityAsString(httpResponse);
      logger.log(Level.FINEST, "PR-GET-RESPONSE:" + response);
      return response;
    } catch (AuthenticationException | IOException e) {
      throw new StashApiException("Exception in GET request", e);
    } finally {
      request.releaseConnection();
    }
  }

  private void deleteRequest(String path) throws StashApiException {
    CloseableHttpClient client = getHttpClient();

    HttpDelete request = new HttpDelete(path);
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html; section 14.10.
    // tells the server that we want it to close the connection when it has sent the response.
    // address large amount of close_wait sockets client and fin sockets server side
    request.setHeader("Connection", "close");

    try {
      request.addHeader(new BasicScheme().authenticate(credentials, request, null));
      int response = client.execute(request).getStatusLine().getStatusCode();
      logger.log(Level.FINE, "Delete comment {" + path + "} returned result code; " + response);
    } catch (AuthenticationException | IOException e) {
      throw new StashApiException("Exception in DELETE request", e);
    } finally {
      request.releaseConnection();
    }
  }

  @Nonnull
  private String postRequest(String path, String comment) throws StashApiException {
    logger.log(Level.FINEST, "PR-POST-REQUEST:" + path + " with: " + comment);
    CloseableHttpClient client = getHttpClient();

    HttpPost request = new HttpPost(path);
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html; section 14.10.
    // tells the server that we want it to close the connection when it has sent the response.
    // address large amount of close_wait sockets client and fin sockets server side
    request.setHeader("Connection", "close");
    request.setHeader("X-Atlassian-Token", "no-check"); // xsrf

    if (comment != null) {
      ObjectNode node = mapper.getNodeFactory().objectNode();
      node.put("text", comment);
      try {
        StringEntity requestEntity =
            new StringEntity(mapper.writeValueAsString(node), ContentType.APPLICATION_JSON);
        request.setEntity(requestEntity);
      } catch (IOException e) {
        throw new StashApiException("Exception preparing POST request", e);
      }
    }

    try {
      request.addHeader(new BasicScheme().authenticate(credentials, request, null));
      HttpResponse httpResponse = client.execute(request);
      int responseCode = httpResponse.getStatusLine().getStatusCode();
      if (!validResponseCode(responseCode)) {
        logger.log(Level.SEVERE, "Failing to get response from Stash PR POST" + request.getURI());
        throw new StashApiException(
            "Didn't get a 200 response from Stash PR POST! Response; '"
                + responseCode
                + "' with message; "
                + httpResponse.getStatusLine().getReasonPhrase());
      }

      String response = entityAsString(httpResponse);
      logger.log(Level.FINEST, "PR-POST-RESPONSE:" + response);
      return response;
    } catch (AuthenticationException | IOException e) {
      throw new StashApiException("Exception in POST request", e);
    } finally {
      request.releaseConnection();
    }
  }

  private boolean validResponseCode(int responseCode) {
    return responseCode == HttpStatus.SC_OK
        || responseCode == HttpStatus.SC_ACCEPTED
        || responseCode == HttpStatus.SC_CREATED
        || responseCode == HttpStatus.SC_NO_CONTENT
        || responseCode == HttpStatus.SC_RESET_CONTENT
        || responseCode == HttpStatus.SC_CONFLICT;
  }

  private String pullRequestsPath() {
    return apiBaseUrl + this.project + "/repos/" + this.repositoryName + "/pull-requests/";
  }

  private String pullRequestPath(String pullRequestId) {
    return pullRequestsPath() + pullRequestId;
  }

  private String pullRequestsPath(int start) {
    String basePath = pullRequestsPath();
    return basePath.substring(0, basePath.length() - 1) + "?start=" + start;
  }

  /**
   * Indicates an error during interaction with the Bitbucket Server
   *
   * <p>This exception must be caught inside the plugin. Typical handling would be to retry the
   * operation during the next polling cycle in the hope that the server or the network would
   * recover by then.
   */
  public static class StashApiException extends Exception {
    private static final long serialVersionUID = 1L;

    public StashApiException(String message) {
      super(message);
    }

    public StashApiException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
