package org.finos.legend.server.pac4j.session.store;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import org.apache.commons.lang.StringUtils;
import org.bson.Document;

import org.finos.legend.server.pac4j.session.config.SessionStoreConfiguration.MongodbConfiguration;
import org.finos.legend.server.pac4j.session.utils.SessionToken;
import org.finos.legend.server.pac4j.session.utils.UuidUtils;

import java.util.Date;
import java.util.concurrent.TimeUnit;


public class MongoDbSessionStore implements SessionStore
{
    private MongoDatabase mongoDatabase;
    private MongoCollection<Document> mongoCollection;

    public MongoDbSessionStore(MongodbConfiguration mongodbConfiguration)
    {
        validateConfiguration(mongodbConfiguration);

        this.mongoDatabase = new MongoClient(new MongoClientURI(mongodbConfiguration.getDatabaseURI()))
                .getDatabase(mongodbConfiguration.getDatabaseName());

        this.mongoCollection = mongoDatabase.getCollection(mongodbConfiguration.getCollection());
    }

    public MongoDbSessionStore(MongoDatabase mongoDatabase, String collectionName)
    {
        this.mongoDatabase = mongoDatabase;
        this.mongoCollection = mongoDatabase.getCollection(collectionName);
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

    public Object getDatabaseClient()
    {
        return mongoDatabase;
    }

    public Document getSession(SessionToken token)
    {
        return mongoCollection.find(convertTokenToSessionDocument(token)).first();
    }

    public String getSessionAttribute(Object document, String attributeKey)
    {
        return ((Document) document).getString(attributeKey);
    }

    public Object updateSession(SessionToken token, String key, Object value)
    {
        return mongoCollection.updateOne(convertTokenToSessionDocument(token), new Document("$set", new Document(key, value)));
    }

    private void validateConfiguration(MongodbConfiguration mongodbConfiguration)
    {
        if (StringUtils.isEmpty(mongodbConfiguration.getDatabaseURI()))
        {
            throw new RuntimeException("MongoDB session store requires 'databaseURI' attribute to be configured if enabled");
        }

        if (StringUtils.isEmpty(mongodbConfiguration.getDatabaseName()))
        {
            throw new RuntimeException("MongoDB session store requires 'databaseName' attribute to be configured if enabled");
        }

        if (StringUtils.isEmpty(mongodbConfiguration.getCollection()))
        {
            throw new RuntimeException("MongoDB session store requires 'collectionName' attribute to be configured if enabled");
        }
    }

}