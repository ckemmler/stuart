package stuart.domain;

import org.hibernate.annotations.Index;

import javax.persistence.*;
import java.util.Date;

/**
 * User: candide
 * Date: 27/08/13
 * Time: 12:31
 */
@Entity(name="TweetedBy")
public class TweetedBy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Index(name="URL_hash")
    public String URL_hash;

    @Index(name="article_hash")
    public String article_hash;

    @Index(name="twitterName")
    public String twitterName;

    public String twitterProfilePhotoURL;

    @Index(name="tweetId")
    public String tweetId;

    @Index(name="twitterId")
    public long twitterId;

    public String tweet;

    @Index(name="created")
    @Temporal(TemporalType.TIMESTAMP)
    public Date created;

}
