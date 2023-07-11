package org.finos.legend.server.pac4j.hazelcaststore;

import org.pac4j.core.profile.CommonProfile;

import java.util.Map;

public class HazelcastSessionDetails
{

    private final String createdDateString;

    private Map<String, CommonProfile> profileMap;

    public HazelcastSessionDetails(String createdDateString)
    {
        this.createdDateString = createdDateString;
    }

    public String getCreatedDateString()
    {
        return createdDateString;
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
