package stuart;

import com.yammer.metrics.annotation.Timed;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.*;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import static stuart.Utils.fetch;

/**
 * User: candide
 * Date: 22/08/13
 * Time: 23:38
 */
@Path("/stuart/scoopit")
@Produces(MediaType.APPLICATION_ATOM_XML)
public class ScoopItUtilsController {


    @GET
    @Timed
    public String replaceLinkWithSourceUrl(@QueryParam("url") String url) throws IOException, SAXException, XPathExpressionException, ParserConfigurationException, TransformerException {
        String originalFeed = fetch(url);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(originalFeed)));
        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();
        XPathExpression expr = xpath.compile("//rss/channel/item");
        NodeList list = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
        Map<String,String> toReplace = new HashMap<String,String>();
        for (int i=0; i<list.getLength(); i++) {
            final Element item = (Element) list.item(i);
            final Element source = (Element)item.getElementsByTagName("source").item(0);
            if (source==null)
                continue;
            final String sourceUrl = source.getAttribute("url");
            final Element link = (Element)item.getElementsByTagName("link").item(0);
            final String scoopItLink = link.getTextContent();
            toReplace.put(scoopItLink, sourceUrl);
        }
        for (String scoopItLink : toReplace.keySet()) {
            final String sourceUrl = toReplace.get(scoopItLink);
            originalFeed = originalFeed.replace(scoopItLink, sourceUrl);
        }
        return originalFeed;
    }

}
