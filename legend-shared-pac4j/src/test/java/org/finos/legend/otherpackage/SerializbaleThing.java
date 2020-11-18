package org.finos.legend.otherpackage;

import java.io.Serializable;
import java.util.Objects;

public class SerializbaleThing implements Serializable
{
  private static final long serialVersionUID = -3579175487758281236L;

  private String str1;

  public SerializbaleThing(String str1)
  {
    this.str1 = str1;
  }

  public String getStr1()
  {
    return str1;
  }

  public void setStr1(String str1)
  {
    this.str1 = str1;
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SerializbaleThing that = (SerializbaleThing) o;
    return Objects.equals(str1, that.str1);
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(str1);
  }
}
