package org.finos.legend.server.pac4j.session.config;

import com.google.common.base.Strings;

import java.util.HashMap;
import java.util.Map;

public class SessionStoreConfiguration
{

    private static final String DEFAULT_CRYPTO_ALGORITHM = "AES";
    private static final int DEFAULT_MAX_SESSION_LENGTH = 7200;

    private String type;
    private boolean enabled;
    private String databaseURI;

    private String cryptoAlgorithm = DEFAULT_CRYPTO_ALGORITHM;
    private int maxSessionLength = DEFAULT_MAX_SESSION_LENGTH;

    private Map<String, String> customConfigurations;


    public void defaults(SessionStoreConfiguration other)
    {
        this.defaultCryptoAlgorithm(other.getCryptoAlgorithm());
        this.defaultEnabled(other.isEnabled());
        this.defaultMaxSessionLength(other.getMaxSessionLength());
        this.defaultDatabaseURI(other.getDatabaseURI());
    }

    public void defaultCryptoAlgorithm(String cryptoAlgorithm)
    {
        if (this.cryptoAlgorithm.equals(DEFAULT_CRYPTO_ALGORITHM))
        {
            this.cryptoAlgorithm = cryptoAlgorithm;
        }
    }

    private void defaultDatabaseURI(String databaseURI)
    {
        if (Strings.isNullOrEmpty(this.databaseURI))
        {
            this.databaseURI = databaseURI;
        }
    }

    public void defaultEnabled(boolean enabled)
    {
        this.enabled = this.enabled || enabled;
    }

    public void defaultMaxSessionLength(int maxSessionLength)
    {
        if (this.maxSessionLength == DEFAULT_MAX_SESSION_LENGTH)
        {
            this.maxSessionLength = maxSessionLength;
        }
    }

    public Map<String, String> getCustomConfigurations()
    {
        if (customConfigurations == null)
        {
            customConfigurations = new HashMap<>();
        }
        return customConfigurations;
    }

    public String getDatabaseURI()
    {
        return databaseURI;
    }

    public void setDatabaseURI(String databaseURI)
    {
        this.databaseURI = databaseURI;
    }

    public String getType()
    {
        return type;
    }

    public String getCryptoAlgorithm()
    {
        return cryptoAlgorithm;
    }

    public int getMaxSessionLength()
    {
        return maxSessionLength;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setCryptoAlgorithm(String cryptoAlgorithm)
    {
        this.cryptoAlgorithm = cryptoAlgorithm;
    }

    public void setCustomConfigurations(Map<String, String> customConfigurations)
    {
        this.customConfigurations = customConfigurations;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public void setMaxSessionLength(int maxSessionLength)
    {
        this.maxSessionLength = maxSessionLength;
    }

    public void setType(String type)
    {
        this.type = type;
    }

}
