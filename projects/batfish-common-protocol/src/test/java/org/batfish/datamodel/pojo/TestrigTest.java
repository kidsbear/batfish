package org.batfish.datamodel.pojo;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import com.google.common.testing.EqualsTester;
import java.util.Date;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link Testrig}. */
@RunWith(JUnit4.class)
public class TestrigTest {

  @Test
  public void testConstructorAndGetter() {
    Date now = new Date();
    Testrig testrig = new Testrig("testrig", now, 0, 0);
    assertThat(testrig.getName(), equalTo("testrig"));
    assertThat(testrig.getCreatedAt(), equalTo(now));
    assertThat(testrig.getEnvironmentsCount(), equalTo(0));
    assertThat(testrig.getConfigurationsCount(), equalTo(0));
  }

  @Test
  public void testToString() {
    Date now = new Date();
    Testrig testrig = new Testrig("testrig", now, 1, 2);
    String expected =
        String.format(
            "Testrig{name=testrig, createdAt=%s, environmentsCount=%s, configurationsCount=%s}",
            now, 1, 2);
    assertThat(testrig.toString(), equalTo(expected));
  }

  @Test
  public void testEquals() {
    Date now = new Date();
    Testrig testrig = new Testrig("testrig", now, 0, 0);
    Testrig tCopy = new Testrig("testrig", now, 0, 0);
    Date otherTime = new Date();
    Testrig tOtherTime = new Testrig("testrig", otherTime, 0, 0);
    Testrig tWithEnvs = new Testrig("testrig", now, 1, 0);
    Testrig tWithCondigs = new Testrig("testrig", now, 0, 1);

    new EqualsTester()
        .addEqualityGroup(testrig, tCopy)
        .addEqualityGroup(tOtherTime)
        .addEqualityGroup(tWithEnvs)
        .addEqualityGroup(tWithCondigs)
        .testEquals();
  }
}
