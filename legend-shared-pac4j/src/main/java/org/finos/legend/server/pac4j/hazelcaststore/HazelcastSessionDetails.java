package org.finos.legend.server.pac4j.hazelcaststore;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class HazelcastSessionDetails implements Serializable
{

    private final Date createdDate;

    private Map<String, Object> sessionData = new HashMap<>();

    public HazelcastSessionDetails(Date createdDate)
    {
        this.createdDate = createdDate;
    }

    public Date getCreatedDate()
    {
        return createdDate;
    }

    public Map<String, Object> getSessionData()
    {
        return sessionData;
    }

    public void setSessionData(Map<String, Object> sessionData)
    {
        this.sessionData = sessionData;
    }
}
