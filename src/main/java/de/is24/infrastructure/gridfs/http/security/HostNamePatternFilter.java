package de.is24.infrastructure.gridfs.http.security;

import de.is24.infrastructure.gridfs.http.gridfs.GridFsFileDescriptor;
import de.is24.infrastructure.gridfs.http.utils.HostName;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.springframework.util.StringUtils.commaDelimitedListToSet;
import static org.springframework.util.StringUtils.trimAllWhitespace;
import static org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST;


@Component
@ManagedResource
public class HostNamePatternFilter {
  private static final Logger LOGGER = LoggerFactory.getLogger(HostNamePatternFilter.class);

  private final Set<String> protectedRepos;
  private List<IpRange> whiteListedIpRanges = new ArrayList<>();

  @Autowired
  public HostNamePatternFilter(
    @Value("${security.protectedRepos:}") String protectedRepos,
    @Value("${security.protectedRepoWhiteListedIpRanges:}") String protectedRepoWhiteListedIpRanges) {
    this.protectedRepos = Collections.synchronizedSet(commaDelimitedListToSet(trimAllWhitespace(protectedRepos)));
    if (isNotBlank(protectedRepoWhiteListedIpRanges)) {
      for (String ipRange : commaDelimitedListToSet(trimAllWhitespace(protectedRepoWhiteListedIpRanges))) {
        whiteListedIpRanges.add(new IpRange(ipRange));
      }
    }
  }

  public boolean isAllowedPropagationRepo(String repo) {
    return !protectedRepos.contains(repo);
  }

  public boolean isAllowed(GridFsFileDescriptor gridFsFileDescriptor) {
    RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

    boolean isWebCall = false;
    HostName remoteHostName = null;
    if (requestAttributes != null) {
      isWebCall = true;

      remoteHostName = (HostName) requestAttributes.getAttribute(HostNameFilter.REMOTE_HOST_NAME,
        SCOPE_REQUEST);
    }

    if (isWebCall && !gridFsFileDescriptor.getArch().equals("repodata") &&
        protectedRepos.contains(gridFsFileDescriptor.getRepo())) {
      LOGGER.info("check access permission for {} to {}", remoteHostName, gridFsFileDescriptor.getPath());
      if (remoteHostName.isIp()) {
        LOGGER.debug("..is IP...");
        if (!isAllowedIp(remoteHostName.getName())) {
          LOGGER.info("... ip not in whitelist: deny");
          return false;
        }
      } else if (!gridFsFileDescriptor.getFilename().contains(remoteHostName.getShortName())) {
        LOGGER.info("... not ip, not matching: deny");
        return false;
      }
    }
    LOGGER.debug("...allowed.");

    return true;
  }

  private boolean isAllowedIp(String ip) {
    for (IpRange ipRange : whiteListedIpRanges) {
      if (ipRange.isIn(ip)) {
        return true;
      }
    }

    return false;
  }

  @ManagedOperation
  public void addProtectedRepo(String repoName) {
    protectedRepos.add(repoName);
  }

  @ManagedOperation
  public Set<String> getProtectedRepos() {
    return Collections.unmodifiableSet(protectedRepos);
  }
}
