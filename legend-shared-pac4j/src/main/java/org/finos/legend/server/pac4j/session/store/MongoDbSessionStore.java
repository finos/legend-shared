package org.finos.legend.server.pac4j.session.store;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;

import org.finos.legend.server.pac4j.session.config.SessionStoreConfiguration;
import org.finos.legend.server.pac4j.session.utils.SessionToken;
import org.finos.legend.server.pac4j.session.utils.UuidUtils;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class MongoDbSessionStore extends SessionStore
{
    public static final String CUSTOM_CONFIG_MONGODB_DATABASE_NAME = "databaseName";
    public static final String CUSTOM_CONFIG_MONGODB_COLLECTION = "collection";

    private MongoDatabase mongoDB;
    private MongoCollection<Document> mongoCollection;

    public MongoDbSessionStore(SessionStoreConfiguration config)
    {
        validateCustomConfiguration(config.getCustomConfigurations());

        mongoDB = new MongoClient(new MongoClientURI(config.getDatabaseURI())).getDatabase(
                                                     config.getCustomConfigurations().get(CUSTOM_CONFIG_MONGODB_DATABASE_NAME));

        mongoCollection = mongoDB.getCollection(config.getCustomConfigurations().get(CUSTOM_CONFIG_MONGODB_COLLECTION));
    }

    private void validateCustomConfiguration(Map<String, String> customConfigurations)
    {

        if (!customConfigurations.containsKey(CUSTOM_CONFIG_MONGODB_DATABASE_NAME))
        {
            throw new RuntimeException("MongoDB session store requires 'databaseName' custom attribute to be configured if enabled");
        }

        if (!customConfigurations.containsKey(CUSTOM_CONFIG_MONGODB_COLLECTION))
        {
            throw new RuntimeException("MongoDB session store requires 'collection' custom attribute to be configured if enabled");
        }
    }

    public void createIndex(long maxSessionLength, TimeUnit timeUnit)
    {
        mongoCollection.createIndex(new Document(SESSION_PROPERTY_CREATED, 1),
                                    new IndexOptions().name("ttl").expireAfter(maxSessionLength, timeUnit));
    }

    public void createSession(SessionToken token)
    {
        mongoCollection.insertOne(convertTokenToSessionDocument(token).append(SessionStore.SESSION_PROPERTY_CREATED, new Date()));
    }

    private Document convertTokenToSessionDocument(SessionToken token)
    {
        return new Document(SESSION_PROPERTY_ID, UuidUtils.toHexString(token.getSessionId()));
    }

    public Object deleteSession(SessionToken token)
    {
        return mongoCollection.deleteMany(convertTokenToSessionDocument(token));
    }

    public Object getDatabase()
    {
        return mongoDB;
    }

    public Document getSession(SessionToken token)
    {
        return mongoCollection.find(convertTokenToSessionDocument(token)).first();
    }

    public Object updateSession(SessionToken token, String key, Object value)
    {
        return mongoCollection.updateOne(convertTokenToSessionDocument(token), new Document("$set", new Document(key, value)));
    }

}