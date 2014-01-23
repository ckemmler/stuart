package stuart;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yammer.dropwizard.config.Configuration;
import com.yammer.dropwizard.db.DatabaseConfiguration;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * User: candide
 * Date: 25/08/13
 * Time: 11:11
 */
public class StuartUtilsConfiguration extends Configuration {

    private String twitterName;

    public String getTwitterConsumerKey() {
        return twitterConsumerKey;
    }

    public String getTwitterConsumerSecret() {
        return twitterConsumerSecret;
    }

    public String getTwitterAccessToken() {
        return twitterAccessToken;
    }

    public String getTwitterAccessTokenSecret() {
        return twitterAccessTokenSecret;
    }

    public String getTwitterName() {
        return twitterName;
    }

    public DatabaseConfiguration getDatabaseConfiguration() {
        return database;
    }

    @Valid
    @NotNull
    @JsonProperty
    private DatabaseConfiguration database = new DatabaseConfiguration();

    @NotEmpty
    @JsonProperty
    private String twitterConsumerKey;

    @NotEmpty
    @JsonProperty
    private String twitterConsumerSecret;

    @NotEmpty
    @JsonProperty
    private String twitterAccessToken;

    @NotEmpty
    @JsonProperty
    private String twitterAccessTokenSecret;


}
