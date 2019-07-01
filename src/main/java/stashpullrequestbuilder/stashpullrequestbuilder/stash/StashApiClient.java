package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import static java.lang.String.format;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpClientError;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.params.HttpParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.params.CoreConnectionPNames;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;

/** Created by Nathan McCarthy */
@SuppressFBWarnings("EQ_DOESNT_OVERRIDE_EQUALS")
public class StashApiClient {

  // Request timeout: maximum time between sending an HTTP request and receiving
  // a response to it from the server.
  private static final int HTTP_REQUEST_TIMEOUT_SECONDS = 60;

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
    if (ignoreSsl) {
      Protocol easyhttps =
          new Protocol("https", (ProtocolSocketFactory) new EasySSLProtocolSocketFactory(), 443);
      Protocol.registerProtocol("https", easyhttps);
    }
  }

  @Nonnull
  public List<StashPullRequestResponseValue> getPullRequests() throws StashApiException {
    List<StashPullRequestResponseValue> pullRequestResponseValues =
        new ArrayList<StashPullRequestResponseValue>();
    try {
      boolean isLastPage = false;
      int start = 0;
      while (!isLastPage) {
        String response = getRequest(pullRequestsPath(start));
        StashPullRequestResponse parsedResponse = parsePullRequestJson(response);
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
      List<StashPullRequestActivityResponse> commentResponses =
          new ArrayList<StashPullRequestActivityResponse>();
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
        StashPullRequestActivityResponse resp = parseCommentJson(response);
        isLastPage = resp.getIsLastPage();
        if (!isLastPage) {
          start = resp.getNextPageStart();
        }
        commentResponses.add(resp);
      }
      return extractComments(commentResponses);
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
      return parseSingleCommentJson(response);
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
      return parsePullRequestMergeStatus(response);
    } catch (IOException e) {
      throw new StashApiException("Cannot parse merge status", e);
    }
  }

  public boolean mergePullRequest(String pullRequestId, String version) throws StashApiException {
    String path = pullRequestPath(pullRequestId) + "/merge?version=" + version;
    String response = postRequest(path, null);
    return !response.equals(Integer.toString(HttpStatus.SC_CONFLICT));
  }

  private HttpClient getHttpClient() {
    HttpClient client = new HttpClient();
    HttpParams httpParams = client.getParams();
    httpParams.setParameter(
        CoreConnectionPNames.CONNECTION_TIMEOUT, HTTP_CONNECTION_TIMEOUT_SECONDS * 1000);
    httpParams.setParameter(CoreConnectionPNames.SO_TIMEOUT, HTTP_SOCKET_TIMEOUT_SECONDS * 1000);

    //        if (Jenkins.getInstance() != null) {
    //            ProxyConfiguration proxy = Jenkins.getInstance().proxy;
    //            if (proxy != null) {
    //                logger.info("Jenkins proxy: " + proxy.name + ":" + proxy.port);
    //                client.getHostConfiguration().setProxy(proxy.name, proxy.port);
    //                String username = proxy.getUserName();
    //                String password = proxy.getPassword();
    //                // Consider it to be passed if username specified. Sufficient?
    //                if (username != null && !"".equals(username.trim())) {
    //                    logger.info("Using proxy authentication (user=" + username + ")");
    //                    client.getState().setProxyCredentials(AuthScope.ANY,
    //                        new UsernamePasswordCredentials(username, password));
    //                }
    //            }
    //        }
    return client;
  }

  private String getRequest(String path) throws StashApiException {
    logger.log(Level.FINEST, "PR-GET-REQUEST:" + path);
    HttpClient client = getHttpClient();
    client.getState().setCredentials(AuthScope.ANY, credentials);

    GetMethod request = new GetMethod(path);
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html; section 14.10.
    // tells the server that we want it to close the connection when it has sent the response.
    // address large amount of close_wait sockets client and fin sockets server side
    request.setRequestHeader("Connection", "close");

    client.getParams().setAuthenticationPreemptive(true);
    String response = null;
    FutureTask<String> httpTask = null;
    Thread thread;
    try {
      // Run the HTTP request in a future task so we have the opportunity
      // to cancel it if it gets hung up; which is possible if stuck at
      // socket native layer.  see issue JENKINS-30558
      httpTask =
          new FutureTask<String>(
              new Callable<String>() {

                private HttpClient client;
                private GetMethod request;

                @Override
                public String call() throws StashApiException, IOException {
                  String response = null;
                  int responseCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;
                  responseCode = client.executeMethod(request);
                  if (!validResponseCode(responseCode)) {
                    logger.log(
                        Level.SEVERE,
                        "Failing to get response from Stash PR GET" + request.getPath());
                    throw new StashApiException(
                        "Didn't get a 200 response from Stash PR GET! Response; '"
                            + HttpStatus.getStatusText(responseCode)
                            + "' with message; "
                            + response);
                  }
                  InputStream responseBodyAsStream = request.getResponseBodyAsStream();
                  StringWriter stringWriter = new StringWriter();
                  IOUtils.copy(responseBodyAsStream, stringWriter, "UTF-8");
                  response = stringWriter.toString();

                  return response;
                }

                public Callable<String> init(HttpClient client, GetMethod request) {
                  this.client = client;
                  this.request = request;
                  return this;
                }
              }.init(client, request));
      thread = new Thread(httpTask);
      thread.start();
      response = httpTask.get(HTTP_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

    } catch (TimeoutException e) {
      request.abort();
      throw new StashApiException("Timeout in GET request", e);
    } catch (InterruptedException | ExecutionException e) {
      throw new StashApiException("Exception in GET request", e);
    } finally {
      request.releaseConnection();
    }
    logger.log(Level.FINEST, "PR-GET-RESPONSE:" + response);
    return response;
  }

  public void deleteRequest(String path) throws StashApiException {
    HttpClient client = getHttpClient();
    client.getState().setCredentials(AuthScope.ANY, credentials);

    DeleteMethod request = new DeleteMethod(path);
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html; section 14.10.
    // tells the server that we want it to close the connection when it has sent the response.
    // address large amount of close_wait sockets client and fin sockets server side
    request.setRequestHeader("Connection", "close");

    client.getParams().setAuthenticationPreemptive(true);
    int response = -1;
    FutureTask<Integer> httpTask = null;
    Thread thread;

    try {
      // Run the HTTP request in a future task so we have the opportunity
      // to cancel it if it gets hung up; which is possible if stuck at
      // socket native layer.  see issue JENKINS-30558
      httpTask =
          new FutureTask<Integer>(
              new Callable<Integer>() {

                private HttpClient client;
                private DeleteMethod request;

                @Override
                public Integer call() throws StashApiException, HttpException, IOException {
                  int response = -1;
                  response = client.executeMethod(request);
                  return response;
                }

                public Callable<Integer> init(HttpClient client, DeleteMethod request) {
                  this.client = client;
                  this.request = request;
                  return this;
                }
              }.init(client, request));
      thread = new Thread(httpTask);
      thread.start();
      response = httpTask.get(HTTP_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

    } catch (TimeoutException e) {
      request.abort();
      throw new StashApiException("Timeout in DELETE request", e);
    } catch (ExecutionException | InterruptedException e) {
      throw new StashApiException("Exception in DELETE request", e);
    } finally {
      request.releaseConnection();
    }

    logger.log(Level.FINE, "Delete comment {" + path + "} returned result code; " + response);
  }

  private String postRequest(String path, String comment) throws StashApiException {
    logger.log(Level.FINEST, "PR-POST-REQUEST:" + path + " with: " + comment);
    HttpClient client = getHttpClient();
    client.getState().setCredentials(AuthScope.ANY, credentials);

    PostMethod request = new PostMethod(path);
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html; section 14.10.
    // tells the server that we want it to close the connection when it has sent the response.
    // address large amount of close_wait sockets client and fin sockets server side
    request.setRequestHeader("Connection", "close");
    request.setRequestHeader("X-Atlassian-Token", "no-check"); // xsrf

    if (comment != null) {
      ObjectNode node = mapper.getNodeFactory().objectNode();
      node.put("text", comment);

      StringRequestEntity requestEntity = null;
      try {
        requestEntity =
            new StringRequestEntity(mapper.writeValueAsString(node), "application/json", "UTF-8");
      } catch (IOException e) {
        throw new StashApiException("Exception preparing POST request", e);
      }
      request.setRequestEntity(requestEntity);
    }

    client.getParams().setAuthenticationPreemptive(true);
    String response = "";
    FutureTask<String> httpTask = null;
    Thread thread;

    try {
      // Run the HTTP request in a future task so we have the opportunity
      // to cancel it if it gets hung up; which is possible if stuck at
      // socket native layer.  see issue JENKINS-30558
      httpTask =
          new FutureTask<String>(
              new Callable<String>() {

                private HttpClient client;
                private PostMethod request;

                @Override
                public String call() throws StashApiException, HttpException, IOException {
                  String response = "";
                  int responseCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;

                  responseCode = client.executeMethod(request);
                  if (!validResponseCode(responseCode)) {
                    logger.log(
                        Level.SEVERE,
                        "Failing to get response from Stash PR POST" + request.getPath());
                    throw new StashApiException(
                        "Didn't get a 200 response from Stash PR POST! Response; '"
                            + HttpStatus.getStatusText(responseCode)
                            + "' with message; "
                            + response);
                  }
                  InputStream responseBodyAsStream = request.getResponseBodyAsStream();
                  StringWriter stringWriter = new StringWriter();
                  IOUtils.copy(responseBodyAsStream, stringWriter, "UTF-8");
                  response = stringWriter.toString();
                  logger.log(Level.FINEST, "API Request Response: " + response);

                  return response;
                }

                public Callable<String> init(HttpClient client, PostMethod request) {
                  this.client = client;
                  this.request = request;
                  return this;
                }
              }.init(client, request));
      thread = new Thread(httpTask);
      thread.start();
      response = httpTask.get(HTTP_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

    } catch (TimeoutException e) {
      request.abort();
      throw new StashApiException("Timeout in POST request", e);
    } catch (ExecutionException | InterruptedException e) {
      throw new StashApiException("Exception in POST request", e);
    } finally {
      request.releaseConnection();
    }

    logger.log(Level.FINEST, "PR-POST-RESPONSE:" + response);

    return response;
  }

  private boolean validResponseCode(int responseCode) {
    return responseCode == HttpStatus.SC_OK
        || responseCode == HttpStatus.SC_ACCEPTED
        || responseCode == HttpStatus.SC_CREATED
        || responseCode == HttpStatus.SC_NO_CONTENT
        || responseCode == HttpStatus.SC_RESET_CONTENT;
  }

  private StashPullRequestResponse parsePullRequestJson(String response) throws IOException {
    StashPullRequestResponse parsedResponse;
    parsedResponse = mapper.readValue(response, StashPullRequestResponse.class);
    return parsedResponse;
  }

  private StashPullRequestActivityResponse parseCommentJson(String response) throws IOException {
    StashPullRequestActivityResponse parsedResponse;
    parsedResponse = mapper.readValue(response, StashPullRequestActivityResponse.class);
    return parsedResponse;
  }

  @Nonnull
  private List<StashPullRequestComment> extractComments(
      List<StashPullRequestActivityResponse> responses) {
    List<StashPullRequestComment> comments = new ArrayList<StashPullRequestComment>();
    for (StashPullRequestActivityResponse parsedResponse : responses) {
      for (StashPullRequestActivity a : parsedResponse.getPrValues()) {
        if (a != null && a.getComment() != null) {
          comments.add(a.getComment());
        }
      }
    }
    return comments;
  }

  private StashPullRequestComment parseSingleCommentJson(String response) throws IOException {
    StashPullRequestComment parsedResponse;
    parsedResponse = mapper.readValue(response, StashPullRequestComment.class);
    return parsedResponse;
  }

  protected static StashPullRequestMergeableResponse parsePullRequestMergeStatus(String response)
      throws IOException {
    StashPullRequestMergeableResponse parsedResponse;
    parsedResponse = mapper.readValue(response, StashPullRequestMergeableResponse.class);
    return parsedResponse;
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

  private static class EasySSLProtocolSocketFactory
      extends stashpullrequestbuilder.stashpullrequestbuilder.repackage.org.apache.commons
          .httpclient.contrib.ssl.EasySSLProtocolSocketFactory {
    private static final Log LOG = LogFactory.getLog(EasySSLProtocolSocketFactory.class);
    private SSLContext sslcontext = null;

    private static SSLContext createEasySSLContext() {
      try {
        TrustManager[] trustAllCerts =
            new TrustManager[] {
              new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                  return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {}

                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
              }
            };

        SSLContext context = SSLContext.getInstance("SSL");
        context.init(null, trustAllCerts, null);
        return context;
      } catch (KeyManagementException | NoSuchAlgorithmException e) {
        LOG.error(e.getMessage(), e);
        throw new HttpClientError(e.toString());
      }
    }

    private SSLContext getSSLContext() {
      if (this.sslcontext == null) {
        this.sslcontext = createEasySSLContext();
      }
      return this.sslcontext;
    }

    public Socket createSocket(String host, int port, InetAddress clientHost, int clientPort)
        throws IOException, UnknownHostException {
      return this.getSSLContext()
          .getSocketFactory()
          .createSocket(host, port, clientHost, clientPort);
    }

    public Socket createSocket(
        String host, int port, InetAddress localAddress, int localPort, HttpConnectionParams params)
        throws IOException, UnknownHostException, ConnectTimeoutException {
      if (params == null) {
        throw new IllegalArgumentException("Parameters may not be null");
      } else {
        int timeout = params.getConnectionTimeout();
        SSLSocketFactory socketfactory = this.getSSLContext().getSocketFactory();
        if (timeout == 0) {
          return socketfactory.createSocket(host, port, localAddress, localPort);
        } else {
          Socket socket = socketfactory.createSocket();
          InetSocketAddress localaddr = new InetSocketAddress(localAddress, localPort);
          InetSocketAddress remoteaddr = new InetSocketAddress(host, port);
          socket.bind(localaddr);
          socket.connect(remoteaddr, timeout);
          return socket;
        }
      }
    }

    public Socket createSocket(String host, int port) throws IOException {
      return this.getSSLContext().getSocketFactory().createSocket(host, port);
    }

    public Socket createSocket(Socket socket, String host, int port, boolean autoClose)
        throws IOException {
      return this.getSSLContext().getSocketFactory().createSocket(socket, host, port, autoClose);
    }
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
