package org.batfish.storage;

import java.util.List;
import org.batfish.datamodel.pojo.CreateEnvironmentRequest;
import org.batfish.datamodel.pojo.Environment;

/** Common storage APIs */
public interface Storage {

  CreateEnvironmentRequest getCreateEnvironmentRequest(String containerName, String testrigName,
      String environmentName);
  Environment getEnvironment(String containerName, String testrigName, String environmentName);

  Environment saveEnvironment(String containerName, String testrigName,
      CreateEnvironmentRequest request);

  Environment updateEnvironment(String containerName, String testrigName,
      CreateEnvironmentRequest request);

  boolean deleteEnvironment(
      String containerName, String testrigName, String environmentName, boolean force);

  List<Environment> listEnvironments(String containerName, String testrigName);
}
