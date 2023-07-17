// Copyright 2020 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.server.pac4j.session.authorization;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.commons.lang.StringUtils;
import org.bson.Document;
import org.finos.legend.server.pac4j.session.config.SessionStoreConfiguration;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.profile.CommonProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class MongoDbSessionAuthority extends SessionAuthority
{

    public static final String NAME = "MongoDbSessionAuthority";
    private static final Logger logger = LoggerFactory.getLogger(MongoDbSessionAuthority.class);

    private MongoCollection<Document> mongoCollection;

    @JsonProperty
    private String collection;

    @Override
    protected boolean isProfileAuthorized(WebContext webContext, CommonProfile u)
    {
        String id = u.getId();
        Document doc = mongoCollection.find(new Document("_id", id)).first();
        if (doc != null)
        {
            logger.debug("Allowing user {} - found in Mongo Collection", id);
            return true;
        }
        else
        {
            logger.warn("Disallowing user {} - not found in Mongo Collection", id);
            return false;
        }
    }

    @Override
    public void configureDatabase(Object database, SessionStoreConfiguration config)
    {
        MongoDatabase mongoDB = (MongoDatabase) database;
        if (mongoDB == null || StringUtils.isEmpty(collection))
        {
            throw new RuntimeException("MongoDB database and collection name must be specified");
        }
        mongoCollection = mongoDB.getCollection(collection);
    }

}