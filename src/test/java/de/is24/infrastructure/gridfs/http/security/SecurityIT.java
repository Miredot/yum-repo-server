package de.is24.infrastructure.gridfs.http.security;

import de.is24.infrastructure.gridfs.http.web.boot.AbstractContainerAndMongoDBStarter;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.redline_rpm.Builder;
import org.redline_rpm.header.Architecture;
import org.redline_rpm.header.Os;
import org.redline_rpm.header.RpmType;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;

import static de.is24.infrastructure.gridfs.http.web.RepoTestUtils.uploadRpm;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.apache.commons.lang.StringUtils.substringBefore;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.core.Is.is;


public class SecurityIT extends AbstractContainerAndMongoDBStarter {
  public static final String RANDOM_STRING = "foobar";
  public static final String DOMAIN_SEPERATOR = ".";
  private String repoUrl;
  private String myProtectedRPM;
  private String anotherHostsProtectedRPM;

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {
    repoUrl = deploymentURL + "/repo/protected-repo";

    myProtectedRPM = buildProtectedRPMForMyHostName();
    anotherHostsProtectedRPM = buildProtectedRPMForHost("anotherhost");

    uploadRpm(repoUrl, new File(tempFolder.getRoot(), myProtectedRPM).getPath());
    uploadRpm(repoUrl, new File(tempFolder.getRoot(), anotherHostsProtectedRPM).getPath());
  }

  @Test
  public void allowDownloadOfMyProtectedRPM() throws Exception {
    HttpGet get = new HttpGet(repoUrl + "/noarch/" + myProtectedRPM);

    HttpResponse response = httpClient.execute(get);
    assertThat(response.getStatusLine().getStatusCode(), is(SC_OK));
  }

  @Test
  public void denyUnauthenticaedDownloadOfProtectedRPM() throws Exception {
    HttpGet get = new HttpGet(repoUrl + "/noarch/" + anotherHostsProtectedRPM);

    HttpClient httpClientWithoutAuthentication = HttpClientBuilder.create().build();
    HttpResponse response = httpClientWithoutAuthentication.execute(get);
    assertThat(response.getStatusLine().getStatusCode(), anyOf(is(SC_UNAUTHORIZED), is(SC_FORBIDDEN)));
  }

  @Test
  public void allowDownloadOfProtectedRPMForAuthenticatedUser() throws Exception {
    HttpGet get = new HttpGet(repoUrl + "/noarch/" + anotherHostsProtectedRPM);

    HttpResponse response = httpClient.execute(get);
    assertThat(response.getStatusLine().getStatusCode(), is(SC_OK));
  }

  @Test
  public void allowDeletionOfProtectedRPMToAuthenticatedUsers() throws Exception {
    HttpDelete delete = new HttpDelete(repoUrl + "/noarch/" + anotherHostsProtectedRPM);

    HttpResponse response = httpClient.execute(delete);
    assertThat(response.getStatusLine().getStatusCode(), is(SC_NO_CONTENT));
  }

  private String getMyShortHostName() throws UnknownHostException {
    String myHostName;

    // local integration tests use localhost access, thus need other hostname in package
    if (deploymentURL.getHost().equals("localhost")) {
      myHostName = "localhost";
    } else {
      myHostName = InetAddress.getLocalHost().getHostName();
    }
    return substringBefore(myHostName, DOMAIN_SEPERATOR);
  }

  private String buildProtectedRPMForMyHostName() throws IOException, NoSuchAlgorithmException {
    String myHostName = getMyShortHostName();
    return buildProtectedRPMForHost(myHostName);
  }


  private String buildProtectedRPMForHost(String myHostName) throws NoSuchAlgorithmException, IOException {
    String rpmNamePrefix = "protected-" + myHostName;

    Builder builder = new Builder();
    String version = "1." + System.currentTimeMillis();

    builder.setPackage(rpmNamePrefix, version, "1");
    builder.setType(RpmType.BINARY);
    builder.setPlatform(Architecture.NOARCH, Os.LINUX);
    builder.setSummary("packaged to test protected Repo access during integration test");
    builder.setBuildHost(myHostName);
    builder.setDescription("packaged to test protected Repo access during integration test");
    builder.setLicense(RANDOM_STRING);
    builder.setGroup("integration Testing");
    builder.setDistribution(RANDOM_STRING);
    builder.setVendor(RANDOM_STRING);
    builder.setPackager(this.getClass().getName());
    builder.setUrl("https://github.com/ImmobilienScout24/yum-repo-server");
    builder.setSourceRpm("none");
    return builder.build(tempFolder.getRoot());
  }


}
