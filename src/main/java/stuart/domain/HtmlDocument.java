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
@Entity(name="HtmlDocument")
public class HtmlDocument {

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

    @Index(name="URL")
    public String URL;

    public String title;

    public String description;

    public String article_hash;

}
