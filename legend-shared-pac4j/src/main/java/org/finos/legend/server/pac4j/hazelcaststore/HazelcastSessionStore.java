package org.finos.legend.server.pac4j.hazelcaststore;

import com.hazelcast.config.FileSystemYamlConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.finos.legend.server.pac4j.internal.HttpSessionStore;
import org.finos.legend.server.pac4j.sessionutil.SessionToken;
import org.pac4j.core.context.Pac4jConstants;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.ProfileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class HazelcastSessionStore extends HttpSessionStore
{
    private static final Logger logger = LoggerFactory.getLogger(HazelcastSessionStore.class);

    private final Map<UUID, HazelcastSessionDetails> hazelcastMap;
    private final int maxSessionLength;

    public HazelcastSessionStore(String hazelcastConfigFilePath,
                                 Map<Class<? extends WebContext>, SessionStore<? extends WebContext>> underlyingStores)
    {
        super(underlyingStores);

        try
        {
            FileSystemYamlConfig fileConfig = new FileSystemYamlConfig(hazelcastConfigFilePath);
            HazelcastInstance hazelcastInstance = Hazelcast.getOrCreateHazelcastInstance(fileConfig);

            String hazelcastMapName = hazelcastInstance.getConfig().getMapConfigs().keySet().stream().findFirst().get();
            this.hazelcastMap = hazelcastInstance.getMap(hazelcastMapName);
            this.maxSessionLength = hazelcastInstance.getConfig().getMapConfig(hazelcastMapName).getTimeToLiveSeconds();
        }
        catch (FileNotFoundException e)
        {
            logger.error("Failed to find Hazelcast config file in specified path: {}", hazelcastConfigFilePath);
            throw new RuntimeException();
        }
    }

    private SessionToken getOrCreateSsoKey(WebContext context)
    {
        SessionToken token = SessionToken.fromContext(context);
        if (token == null)
        {
            token = createSsoKey(context);
        }
        return token;
    }

    private SessionToken createSsoKey(WebContext context)
    {
        SessionToken token = SessionToken.generate();
        token.saveInContext(context, maxSessionLength);
        HazelcastSessionDetails hazelcastSessionDetails = new HazelcastSessionDetails(new Date());
        hazelcastMap.put(token.getSessionId(), hazelcastSessionDetails);
        return token;
    }

    @Override
    public String getOrCreateSessionId(WebContext context)
    {
        getOrCreateSsoKey(context);
        return super.getOrCreateSessionId(context);
    }

    @Override
    public Object get(WebContext context, String key)
    {
        Object res = super.get(context, key);
        if (res == null)
        {
            SessionToken token = getOrCreateSsoKey(context);
            HazelcastSessionDetails hazelcastSessionDetails = hazelcastMap.get(token.getSessionId());
            if (hazelcastSessionDetails != null)
            {
                Map<String, Object> sessionData = hazelcastSessionDetails.getSessionData();
                Object data = sessionData.get(key);
                if (data != null)
                {
                    res = data;
                    //Once we have it, store it in the regular session store for later access
                    super.set(context, key, res);
                }
            }
        }
        else if (SessionToken.fromContext(context) == null)
        {
            //if res is not null ,this means we still have an active Session but an expired SSO cookie we need to recreate one and add it to the context request/response.
            createSsoKey(context);
            if (res instanceof LinkedHashMap)
            {
                ProfileHelper.flatIntoAProfileList((LinkedHashMap<String, CommonProfile>)res);
                set(context, Pac4jConstants.USER_PROFILES, ProfileHelper.flatIntoAProfileList((LinkedHashMap<String, CommonProfile>)res));
            }
        }
        return res;
    }

    @Override
    public void set(WebContext context, String key, Object value)
    {
        SessionToken token = getOrCreateSsoKey(context);
        HazelcastSessionDetails details = hazelcastMap.get(token.getSessionId());
        if (details != null)
        {
            details.getSessionData().put(key, value);

            // need to write the object back into hazelcast
            hazelcastMap.put(token.getSessionId(), details);
        }

        super.set(context, key, value);
    }

    @Override
    public boolean destroySession(WebContext context)
    {
        SessionToken token = getOrCreateSsoKey(context);
        token.saveInContext(context, 0);
        hazelcastMap.remove(token.getSessionId());
        return super.destroySession(context);
    }
}
