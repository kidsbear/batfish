package org.batfish.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.SortedSet;
import org.batfish.common.BatfishException;
import org.batfish.common.BfConsts;
import org.batfish.common.util.BatfishObjectMapper;
import org.batfish.common.util.CommonUtil;
import org.batfish.datamodel.Edge;
import org.batfish.datamodel.collections.NodeInterfacePair;
import org.batfish.datamodel.pojo.CreateEnvironmentRequest;
import org.batfish.datamodel.pojo.Environment;
import org.batfish.datamodel.pojo.FileObject;

public class FileStorageImpl implements Storage {

  private final Path _containersLocation;
  private final StorageUtils _utils;

  public FileStorageImpl(Path containersLocation) throws BatfishException {
    try {
      if (containersLocation != null) {
        _containersLocation = containersLocation;
        _containersLocation.toFile().mkdirs();
        _utils = new StorageUtils(_containersLocation);
      } else {
        throw new BatfishException("container location is null");
      }
    } catch (InvalidPathException e) {
      throw new BatfishException("cannot resolve containers location '" + containersLocation + "'");
    }
  }

  @Override
  public CreateEnvironmentRequest getCreateEnvironmentRequest(
      String containerName, String testrigName, String environmentName) {
    Path envDir = _utils.getEnvironmentPath(containerName, testrigName, environmentName);
    if (!Files.exists(envDir)) {
      throw new BatfishException(
          String.format(
              "Environment '%s' doesn't exist for container '%s'->testrig '%s'",
              environmentName, containerName, testrigName));
    }
    SortedSet<Path> subFileList = CommonUtil.getEntries(envDir);
    BatfishObjectMapper mapper = new BatfishObjectMapper();
    List<Edge> edgeBlackList = new ArrayList<>();
    List<String> nodeBlacklist = new ArrayList<>();
    List<NodeInterfacePair> interfaceBlacklist = new ArrayList<>();
    List<FileObject> bgpTables = new ArrayList<>();
    List<FileObject> routingTables = new ArrayList<>();
    String externalBgpAnnouncements = null;
    try {
      for (Path subdirFile : subFileList) {
        switch (subdirFile.getFileName().toString()) {
          case BfConsts.RELPATH_EDGE_BLACKLIST_FILE:
            edgeBlackList =
                mapper.readValue(
                    CommonUtil.readFile(subdirFile), new TypeReference<List<Edge>>() {});
            break;
          case BfConsts.RELPATH_NODE_BLACKLIST_FILE:
            nodeBlacklist =
                mapper.readValue(
                    CommonUtil.readFile(subdirFile), new TypeReference<List<String>>() {});
            break;
          case BfConsts.RELPATH_INTERFACE_BLACKLIST_FILE:
            interfaceBlacklist =
                mapper.readValue(
                    CommonUtil.readFile(subdirFile),
                    new TypeReference<List<NodeInterfacePair>>() {});
            break;
          case BfConsts.RELPATH_ENVIRONMENT_BGP_TABLES:
            if (Files.isDirectory(subdirFile)) {
              CommonUtil.getEntries(subdirFile)
                  .forEach(
                      path -> {
                        bgpTables.add(new FileObject(path.getFileName().toString(),
                            CommonUtil.readFile(path)));
                      });
            }
            break;
          case BfConsts.RELPATH_ENVIRONMENT_ROUTING_TABLES:
            if (Files.isDirectory(subdirFile)) {
              CommonUtil.getEntries(subdirFile)
                  .forEach(
                      path -> {
                        routingTables.add(new FileObject(path.getFileName().toString(),
                            CommonUtil.readFile(path)));
                      });
            }
            break;
          case BfConsts.RELPATH_EXTERNAL_BGP_ANNOUNCEMENTS:
            externalBgpAnnouncements = CommonUtil.readFile(subdirFile);
          default:
            continue;
        }
      }
    } catch (IOException e) {
      throw new BatfishException("Environment is not properly formatted");
    }
    return new CreateEnvironmentRequest(environmentName,
        edgeBlackList,
        interfaceBlacklist,
        nodeBlacklist,
        bgpTables,
        routingTables,
        externalBgpAnnouncements);
  }

  /**
   * Get an Environment object
   *
   * @param containerName   Parent container
   * @param testrigName     Name of testrig
   * @param environmentName Name of Environment
   * @return Environment from storage
   */
  @Override
  public Environment getEnvironment(String containerName, String testrigName,
      String environmentName) {
    CreateEnvironmentRequest request = getCreateEnvironmentRequest(
        containerName,
        testrigName,
        environmentName);
    Path environmentPath = _utils.getEnvironmentPath(containerName, testrigName, environmentName);
    Date createdAt = _utils.getCreationTime(environmentPath);
    return new Environment(environmentName,
        createdAt,
        request.getEdgeBlacklist().size(),
        request.getInterfaceBlacklist().size(),
        request.getNodeBlacklist().size(),
        request.getBgpTables().size(),
        request.getRoutingTables().size(),
        request.getExternalBgpAnnouncements());
  }

  /**
   * Save an Environment object
   *
   * @param containerName Parent container
   * @param testrigName Name of testrig
   * @param request Create Environment request Object
   * @return Saved copy of environment from storage
   */
  @Override
  public Environment saveEnvironment(String containerName, String testrigName,
      CreateEnvironmentRequest request) {
    Path envDir = _utils.getEnvironmentPath(containerName, testrigName, request.getName());
    if (Files.exists(envDir)) {
      throw new BatfishException(
          String.format(
              "Environment '%s' already exists for container '%s' testrig '%s'",
              request.getName(),
              containerName,
              testrigName));
    }

    if (!envDir.toFile().mkdirs()) {
      throw new BatfishException(String.format("Failed to create environment directory '%s'",
          request.getName()));
    }
    BatfishObjectMapper mapper = new BatfishObjectMapper();
    try {
      if (!request.getEdgeBlacklist().isEmpty()) {
        CommonUtil.writeFile(
            _utils.resolvePath(envDir, BfConsts.RELPATH_EDGE_BLACKLIST_FILE),
            mapper.writeValueAsString(request.getEdgeBlacklist()));
      }
      if (!request.getInterfaceBlacklist().isEmpty()) {
        CommonUtil.writeFile(
            _utils.resolvePath(envDir, BfConsts.RELPATH_INTERFACE_BLACKLIST_FILE),
            mapper.writeValueAsString(request.getInterfaceBlacklist()));
      }
      if (!request.getNodeBlacklist().isEmpty()) {
        CommonUtil.writeFile(
            _utils.resolvePath(envDir, BfConsts.RELPATH_NODE_BLACKLIST_FILE),
            mapper.writeValueAsString(request.getNodeBlacklist()));
      }
      if (request.getBgpTables() != null && !request.getBgpTables().isEmpty()) {
        Path bgpDir = _utils.resolvePath(envDir, BfConsts.RELPATH_ENVIRONMENT_BGP_TABLES);
        bgpDir.toFile().mkdirs();
        request.getBgpTables()
            .forEach(tableFile -> CommonUtil.writeFile(_utils.resolvePath(
                bgpDir,
                tableFile.getName()), tableFile.getContent()));
      }
      if (request.getRoutingTables() != null && !request.getRoutingTables().isEmpty()) {
        Path rtDir = _utils.resolvePath(envDir, BfConsts.RELPATH_ENVIRONMENT_ROUTING_TABLES);
        rtDir.toFile().mkdirs();
        request.getRoutingTables()
            .forEach(tableFile -> CommonUtil.writeFile(_utils.resolvePath(
                rtDir,
                tableFile.getName()), tableFile.getContent()));
      }
      if (request.getExternalBgpAnnouncements() != null) {
        CommonUtil.writeFile(
            _utils.resolvePath(envDir, BfConsts.RELPATH_EXTERNAL_BGP_ANNOUNCEMENTS),
            request.getExternalBgpAnnouncements());
      }
    } catch (JsonProcessingException e) {
      throw new BatfishException(String.format("Error while serializing environment '%s'",
          request.getName()));
    }
    return getEnvironment(containerName, testrigName, request.getName());
  }

  /**
   * Update an Environment object
   *
   * @param containerName Parent container
   * @param testrigName Parent testrig
   * @param request The request to updated Environment
   * @return Updated environment object from storage
   */
  @Override
  public Environment updateEnvironment(
      String containerName, String testrigName, CreateEnvironmentRequest request) {
    deleteEnvironment(containerName, testrigName, request.getName(), true);
    saveEnvironment(containerName, testrigName, request);
    return getEnvironment(containerName, testrigName, request.getName());
  }

  /**
   * Delete an Environment object
   *
   * @param containerName Parent container
   * @param testrigName Parent testrig
   * @param environmentName Environment to be deleted
   * @param force Force deletion of non empty environment
   * @return true if environment deleted, false if it does not exist
   */
  @Override
  public boolean deleteEnvironment(
      String containerName, String testrigName, String environmentName, boolean force) {
    Path envDir = _utils.getEnvironmentPath(containerName, testrigName, environmentName);
    if (!Files.exists(envDir.getParent())) {
      return false;
    }
    if (envDir.toFile().list().length != 0 && !force) {
      throw new BatfishException(
          String.format("'%s' is not empty, deletion must be forced", environmentName));
    }
    CommonUtil.deleteDirectory(envDir.getParent());
    return true;
  }

  /**
   * List all Environments in a given container and testrig
   *
   * @param containerName Parent container
   * @param testrigName Parent testrig
   * @return List of environment names
   */
  @Override public List<Environment> listEnvironments(String containerName, String testrigName) {
    Path envsDir =
        _utils.resolvePath(
            _utils.getTestrigPath(containerName, testrigName), BfConsts.RELPATH_ENVIRONMENTS_DIR);
    if (!Files.exists(envsDir)) {
      return new ArrayList<>();
    }
    List<Environment> environments = new ArrayList<>();
    CommonUtil.getSubdirectories(envsDir)
        .forEach(envPath -> environments.add(getEnvironment(containerName,
            testrigName,
            envPath.getFileName().toString())));
    return environments;
  }
}
