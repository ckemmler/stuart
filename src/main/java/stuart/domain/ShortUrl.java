package stuart.domain;

import org.hibernate.annotations.Index;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

/**
 * User: candide
 * Date: 25/08/13
 * Time: 15:54
 */
@Entity(name="ShortUrl")
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Index(name="shortUrl")
    public String shortUrl;

    @Index(name="longUrl")
    public String longUrl;

}
