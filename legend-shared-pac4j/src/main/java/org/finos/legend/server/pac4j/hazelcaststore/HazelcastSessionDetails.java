package org.finos.legend.server.pac4j.hazelcaststore;

import org.pac4j.core.profile.CommonProfile;

import java.util.Date;
import java.util.Map;

public class HazelcastSessionDetails
{

    private final Date createdDate;

    private Map<String, CommonProfile> profileMap;

    public HazelcastSessionDetails(Date createdDate)
    {
        this.createdDate = createdDate;
    }

    public Date getCreatedDate()
    {
        return createdDate;
    }

    public Map<String, CommonProfile> getProfileMap()
    {
        return profileMap;
    }

    public void setProfileMap(Map<String, CommonProfile> profileMap)
    {
        this.profileMap = profileMap;
    }
}
