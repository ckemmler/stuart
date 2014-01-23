package stuart;

import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.config.Bootstrap;
import com.yammer.dropwizard.config.Environment;
import com.yammer.dropwizard.db.DatabaseConfiguration;
import com.yammer.dropwizard.hibernate.HibernateBundle;
import stuart.domain.*;

/**
 * User: candide
 * Date: 22/08/13
 * Time: 23:28
 */
public class StuartUtilsService extends Service<StuartUtilsConfiguration> {

    public static void main(String[] args) throws Exception {
        new StuartUtilsService().run(args);
    }

    @Override
    public void initialize(Bootstrap<StuartUtilsConfiguration> bootstrap) {
        bootstrap.setName("stuart");
        bootstrap.addBundle(hibernate);
    }

    private final HibernateBundle<StuartUtilsConfiguration> hibernate = new HibernateBundle<StuartUtilsConfiguration>(
            HtmlDocument.class, ShortUrl.class, Person.class, TweetedBy.class) {
        @Override
        public DatabaseConfiguration getDatabaseConfiguration(StuartUtilsConfiguration configuration) {
            return configuration.getDatabaseConfiguration();
        }
    };

    @Override
    public void run(StuartUtilsConfiguration configuration,
                    Environment environment) {
        environment.addResource(new ScoopItUtilsController());
        environment.addResource(new TwitterUtilsController(configuration, hibernate.getSessionFactory()));
    }

}
