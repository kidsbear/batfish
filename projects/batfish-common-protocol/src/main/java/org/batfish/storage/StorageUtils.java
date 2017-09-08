package org.batfish.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import org.batfish.common.BatfishException;
import org.batfish.common.BfConsts;

public class StorageUtils {

  private final Path _containerLocation;

  public StorageUtils(Path containerLocation) {
    _containerLocation = containerLocation;
  }

  public Path resolvePath(Path basePath, String... dirs) {
    if (dirs.length == 0) {
      return basePath;
    }
    return basePath.resolve(String.join("/", dirs));
  }

  public Path getContainerPath(String containerName) {
    return resolvePath(_containerLocation, containerName);
  }

  public Path getTestrigPath(String containerName, String testrigName) {
    return resolvePath(
        _containerLocation, containerName, BfConsts.RELPATH_TESTRIGS_DIR, testrigName);
  }

  public Path getEnvironmentPath(String containerName, String testrigName, String environmentName) {
    return resolvePath(
        getTestrigPath(containerName, testrigName),
        BfConsts.RELPATH_ENVIRONMENTS_DIR,
        environmentName,
        BfConsts.RELPATH_ENV_DIR);
  }

  public Date getCreationTime(Path path) {
    BasicFileAttributes view;
    try {
      view = Files.getFileAttributeView(path, BasicFileAttributeView.class).readAttributes();
    } catch (IOException e) {
      throw new BatfishException(String.format("Failed to get creation time of file %s", path), e);
    }
    return new Date(view.creationTime().toMillis());
  }

}
