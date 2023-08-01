package org.finos.legend.server.pac4j.session.store;

import org.finos.legend.server.pac4j.session.utils.SessionToken;

import java.util.concurrent.TimeUnit;

public interface SessionStore
{
    String SESSION_PROPERTY_ID = "_id";
    String SESSION_PROPERTY_CREATED = "created";

    void createIndex(long maxSessionLength, TimeUnit seconds);

    void createSession(SessionToken token);

    Object deleteSession(SessionToken token);

    Object getDatabaseClient();

    Object getSession(SessionToken token);

    String getSessionAttribute(Object document, String attributeKey);

    Object updateSession(SessionToken token, String key, Object value);

}