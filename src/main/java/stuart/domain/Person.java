package stuart.domain;

import org.hibernate.annotations.Index;

import javax.persistence.*;
import java.util.Date;

/**
 * User: candide
 * Date: 26/08/13
 * Time: 05:17
 */
@Entity(name="Person")
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Index(name="twitterName")
    public String twitterName;

    @Index(name="lastTweetId")
    public String lastTweetId;

    @Index(name="lastUpdated")
    @Temporal(TemporalType.TIMESTAMP)
    public Date lastUpdated;

}
