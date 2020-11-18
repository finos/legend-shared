package org.finos.legend.server.pac4j;

import org.finos.legend.otherpackage.SerializbaleThing;
import org.junit.Assert;
import org.junit.Test;
import org.pac4j.core.util.JavaSerializationHelper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

public class SerializationTest
{
  private final static JavaSerializationHelper helper = LegendPac4jBundle.getSerializationHelper();
  private final static SerializbaleThing serializableThing = new SerializbaleThing("A random string");

  @Test
  public void testJavaSerialization()
  {
    byte[] bytes = helper.serializeToBytes(serializableThing);
    try (final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
         final ObjectInputStream ois = new ObjectInputStream(bais)) {
      Object output = ois.readObject();
      Assert.assertEquals(serializableThing, output);
    } catch (final IOException | ClassNotFoundException e) {
      e.printStackTrace();
    }
  }

  @Test()
  public void testPac4jSerialization()
  {
    byte[] bytes = helper.serializeToBytes(serializableThing);
    Object output = helper.unserializeFromBytes(bytes);
    Assert.assertNull(output); // Pac4J will return null if the class is not whitelisted
  }
}
