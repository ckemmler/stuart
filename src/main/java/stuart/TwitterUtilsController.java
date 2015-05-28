package stuart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yammer.dropwizard.hibernate.UnitOfWork;
import com.yammer.metrics.annotation.Timed;
import de.l3s.boilerpipe.BoilerpipeProcessingException;
import de.l3s.boilerpipe.extractors.ArticleExtractor;
import net.htmlparser.jericho.*;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import stuart.domain.HtmlDocument;
import stuart.domain.Person;
import stuart.domain.ShortUrl;
import stuart.domain.TweetedBy;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathExpressionException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.*;

import static stuart.Utils.fetch;
import static stuart.Utils.hash;

/**
 * User: candide
 * Date: 22/08/13
 * Time: 23:38
 */
@Path("/stuart/twitter")
@Produces(MediaType.APPLICATION_ATOM_XML)
public class TwitterUtilsController {

    private final StuartUtilsConfiguration configuration;
    private final SessionFactory sessionFactory;
    DateTimeFormatter isoDate = ISODateTimeFormat.dateTime();
    private static final DateTimeFormatter fullDateFormat = DateTimeFormat
            .fullDateTime();
    DateTimeFormatter twitterDateFormat =
            DateTimeFormat.forPattern("EEE MMM dd HH:mm:ss Z yyyy").withLocale(Locale.ENGLISH);

    public TwitterUtilsController(StuartUtilsConfiguration configuration, SessionFactory sessionFactory) {
        this.configuration = configuration;
        this.sessionFactory = sessionFactory;
    }

    @GET
    @Timed
    @Path("/already_tweeted")
    @Produces(MediaType.APPLICATION_JSON)
    @UnitOfWork
    public String isAlreadyTweeted(@QueryParam("url") String longUrl)  {
        try {
            final List<TweetedBy> sameURL = getTweetedByWithURL(longUrl);
            boolean score = sameURL.size()==0;
            Map<String,Object> resultObject = new HashMap<String,Object>();
            Boolean noArticle = false;
            List<TweetedBy> sameContents = null;
            if (score) {
                final String htmlContents = URLConnectionReader.getText(longUrl);
                final StringReader reader = new StringReader(htmlContents);
                try {
                    String article = ArticleExtractor.INSTANCE.getText(reader);
                    if (StringUtils.isEmpty(article))
                        throw new BoilerpipeProcessingException("Couldn't detect an article here...");
                    String articleHash = hash(article);
                    sameContents = getTweetedByWithArticleHash(articleHash);
                } catch (BoilerpipeProcessingException e) {
                    noArticle = true;
                }
            }
            if (sameContents!=null)
                score &= sameContents.size()==0;
            String result = score
                    ? String.format("Score! %s has not been tweeted yet.", longUrl)
                    : "Old news, man! Too bad.";

            resultObject.put("result", result);
            resultObject.put("sameURL", sameURL);
            if (sameContents!=null)
                resultObject.put("sameContents", sameContents);
            if (noArticle)
                resultObject.put("noArticle", true);
            final String resultJson = new ObjectMapper().writeValueAsString(resultObject);
            return resultJson;
        } catch(Exception e) {
            final String trace = ExceptionUtils.getStackTrace(e);
            return trace;
        }
    }

    public static class URLConnectionReader {
        public static String getText(String url) throws Exception {
            URL website = new URL(url);
            URLConnection connection = website.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                            connection.getInputStream()));

            StringBuilder response = new StringBuilder();
            String inputLine;

            while ((inputLine = in.readLine()) != null)
                response.append(inputLine);

            in.close();

            return response.toString();
        }

        public static void main(String[] args) throws Exception {
            String content = URLConnectionReader.getText(args[0]);
            System.out.println(content);
        }
    }

    @GET
    @Timed
    @Path("/home_timeline")
    @Produces("text/xml")
    public String getHomeTimelineRssFeed() {
        try {
            OAuthConsumer consumer = new DefaultOAuthConsumer(
                    configuration.getTwitterConsumerKey(),
                    configuration.getTwitterConsumerSecret());

            consumer.setTokenWithSecret(configuration.getTwitterAccessToken(), configuration.getTwitterAccessTokenSecret());

            // create a request that requires authentication
            String spec = "https://api.twitter.com/1.1/statuses/home_timeline.json";
            spec += "?count=200";
            URL url = new URL(spec);
            HttpURLConnection request = (HttpURLConnection) url.openConnection();

            // sign the request
            consumer.sign(request);

            // send the request
            request.connect();

            // response status should be 200 OK
            int statusCode = request.getResponseCode();
            if (statusCode==200) {
                final String json = IOUtils.toString(request.getInputStream());
                JSONArray tweets = JSONArray.fromObject(json);
                try {
                    final String feed = rssFromTweets(tweets, configuration.getTwitterName(), false);
                    return feed;
                }  catch (Throwable t) {
                    throw t;
                }
            } else
                throw new Exception("Didn't work out this time: " + request.getResponseCode() + " (" + request.getResponseMessage() + ")");
        } catch (Throwable e) {
            final String stackTrace = ExceptionUtils.getStackTrace(e);
            return stackTrace;
        }
    }

    @GET
    @Timed
    @Path("/user_timeline")
    @Produces("text/xml")
    public String getUserTimelineRssFeed(@QueryParam("twitterName") String twitterName) throws IOException, SAXException, XPathExpressionException, ParserConfigurationException, TransformerException, OAuthCommunicationException, OAuthExpectationFailedException, OAuthMessageSignerException {
        try {
            OAuthConsumer consumer = new DefaultOAuthConsumer(
                    configuration.getTwitterConsumerKey(),
                    configuration.getTwitterConsumerSecret());

            consumer.setTokenWithSecret(configuration.getTwitterAccessToken(), configuration.getTwitterAccessTokenSecret());

            // create a request that requires authentication
            String urlString = String.format("https://api.twitter.com/1.1/statuses/user_timeline.json?screen_name=%s", twitterName);
            urlString += "&count=200";
            URL url = new URL(urlString);
            HttpURLConnection request = (HttpURLConnection) url.openConnection();

            // sign the request
            consumer.sign(request);

            // send the request
            request.connect();

            // response status should be 200 OK
            int statusCode = request.getResponseCode();
            if (statusCode==200) {
                final String json = IOUtils.toString(request.getInputStream());
                JSONArray tweets = JSONArray.fromObject(json);
                try {
                    final String feed = rssFromTweets(tweets, twitterName, true);
                    return feed;
                }  catch (Throwable t) {
                    throw t;
                }
            } else
                throw new Exception("Didn't work out this time: " + request.getResponseCode() + " (" + request.getResponseMessage() + ")");
        } catch (Throwable t) {
            final String stackTrace = ExceptionUtils.getStackTrace(t);
            return stackTrace;
        }
    }

    public static void main(final String[] args) {
        System.out.println(hash("http://www.youtube.com/watch?v=P0ukYf_xvgc&feature=youtu.be"));
    }

    private String rssFromTweets(JSONArray tweets, String twitterName, boolean setImage) throws ParserConfigurationException, TransformerException, IOException, ExecutionException, InterruptedException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        // root elements
        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("rss");
        rootElement.setAttribute("version", "2.0");
        Element channelElement = doc.createElement("channel");
        populateChannelElement(doc, channelElement);
        rootElement.appendChild(channelElement);
        doc.appendChild(rootElement);

        addTweetUrls(doc, tweets, twitterName, channelElement, setImage);

        final String feed = getStringFromDoc(doc);
        return feed;
    }


    private void addTweetUrls(final Document doc, JSONArray tweets, final String twitterName, Element channelElement, boolean setImage) throws IOException, ExecutionException, InterruptedException {
        final ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Future<List<Element>>> futures = new ArrayList<Future<List<Element>>>();
        boolean imageIsSet = false;
        for (int i=0; i<tweets.size(); i++) {
            final JSONObject tweet = tweets.getJSONObject(i);
            if (setImage&&!imageIsSet) {
                setImage(doc, channelElement, tweet);
                imageIsSet = true;
            }
            Callable<List<Element>> callable = new Callable<List<Element>>() {

                @Override
                public List<Element> call() throws Exception {
                    System.out.println("spawning a thread");
                    List<String> urls = getUrls(tweet);
                    List<Element> feedItems = createRssFeedItems(doc, tweet, twitterName, urls);
                    return feedItems;
                }

            };
            final Future<List<Element>> future = executorService.submit(callable);
            futures.add(future);
        }
        for (Future<List<Element>> future : futures) {
            try {
                final List<Element> feedItems = future.get();
                for (Element feedItem : feedItems) {
                    channelElement.appendChild(feedItem);
                }
            } catch (Throwable t) {
                continue;
            }
        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void setImage(Document doc, Element channelElement, JSONObject tweet) {
        final JSONObject user = tweet.getJSONObject("user");
        if (user==null) return;
        String imageUrl = user.getString("profile_image_url");
        if (imageUrl==null) return;
        final String imageLink = user.getString("url");
        final String imageDescription = user.getString("description");
        if (imageDescription==null) return;
        final Element image = doc.createElement("image");
        final Element title = createTextElement(doc, "title", imageDescription);
        final Element link = createTextElement(doc, "link", imageLink);
        final Element url = createTextElement(doc, "url", imageUrl);
        image.appendChild(title);
        image.appendChild(link);
        image.appendChild(url);
        channelElement.appendChild(image);
    }

    public List<Element> createRssFeedItems(Document doc, JSONObject tweet, String twitterName, List<String> urls) throws IOException {
        List<Element> elements = new ArrayList<Element>();
        for (String url : urls) {
            final String longUrl = getLongUrl(url);
            final HtmlDocument htmlDocument = getHtmlDocument(url);
            final Element item = doc.createElement("item");
            item.appendChild(createTextElement(doc, "link", longUrl));
            String articleHash = null;
            if (htmlDocument != null) {
                item.appendChild(createTextElement(doc, "title", htmlDocument.title));
                item.appendChild(createTextElement(doc, "description", htmlDocument.description));
                // this assumes that a document with the exact same URL will have identical contents
                articleHash = htmlDocument.article_hash;
            } else {
                String htmlContents = "";
                try {
                    htmlContents = URLConnectionReader.getText(longUrl);
                } catch (Throwable t) {
                    System.out.println("not taking into account documents whose article we're not able to extract");
                }
                try {
                    String article = ArticleExtractor.INSTANCE.getText(new StringReader(htmlContents));
                    if (StringUtils.isEmpty(article))
                        throw new BoilerpipeProcessingException("No article was detected here...");
                    articleHash = hash(article);
                } catch (BoilerpipeProcessingException e) {
                    System.out.println("Could not extract article:" + longUrl);
                }
                final Source source = getSource(new StringReader(htmlContents));
                String title = longUrl, description = longUrl;
                if (source != null) {
                    title = getTitle(source);
                    description = getMetaValue(source, "description");
                    if (description == null && title!=null) description = title;
                }
                addHtmlDocument(url, title, description, articleHash);
                item.appendChild(createTextElement(doc, "title", title));
                item.appendChild(createTextElement(doc, "description", description));
            }
            final String isoDateString = getIsoDateString(tweet, "created_at");
            JSONObject tweetAuthor = tweet.getJSONObject("user");
            item.appendChild(createDCElement(doc, "date", isoDateString));
            item.appendChild(createTextElement(doc, "pubDate", tweet.getString("created_at")));
            item.appendChild(createDCElement(doc, "creator", tweetAuthor.getString("name")));
            addTweetedBy(tweet, url, articleHash);
            elements.add(item);
        }
        saveLastTweetId(twitterName, tweet.getString("id_str"));
        return elements;
    }

    private Source getSource(StringReader reader) {
        try {
            return parseHtmlDoc(reader);
        } catch (Exception e) {
            return null;
        }
    }

    private String getLongUrl(String url) {
        final List<String> shorteners = Arrays.asList("bit.do", "t.co", "go2.do",
                "adf.ly", "goo.gl", "bitly.com", "tinyurl.com", "ow.ly",
                "bit.ly", "adcrun.ch", "zpag.es", "ity.im", "q.gs", "lnk.co",
                "viralurl.com", "is.gd", "vur.me", "bc.vc", "yu2.it", "twitthis.com",
                "u.to", "j.pm", "bee4.biz", "adflav.com", "buzurl.com", "xlinkz.info",
                "cutt.us", "u.bb", "yourls.org", "fun.ly", "hit.my", "nov.io",
                "crisco.com", "x.co", "shortquik.com", "prettylinkpro.com",
                "viralurl.biz", "longurl.org", "tota2.com", "adcraft.co",
                "virl.ws", "scrnch.me", "filoops.info", "linkto.im", "vurl.bz",
                "fzy.co", "vzturl.com", "picz.us", "lemde.fr", "golinks.co",
                "xtu.me", "qr.net", "1url.com", "tweez.me", "sk.gy",
                "gog.li", "cektkp.com", "v.gd", "p6l.org", "id.tl",
                "dft.ba", "aka.gr");
        boolean needsExpanding = false;
        for (String shortener : shorteners) {
            if (url.indexOf(shortener)!=0)
                needsExpanding = true;
        }
        if (!needsExpanding) return url;
        try {
            final ShortUrl shortUrl = getShortUrl(url);
            if (shortUrl!=null)
                return shortUrl.longUrl;
            return longUrl(url);
        } catch (Exception e) {
            return url;
        }
    }

    private static String getTitle(Source source) {
        net.htmlparser.jericho.Element titleElement=source.getFirstElement(HTMLElementName.TITLE);
        if (titleElement==null) return null;
        // TITLE element never contains other tags so just decode it collapsing whitespace:
        return CharacterReference.decodeCollapseWhiteSpace(titleElement.getContent());
    }

    private static String getMetaValue(Source source, String key) {
        for (int pos=0; pos<source.length();) {
            StartTag startTag=source.getNextStartTag(pos,"name",key,false);
            if (startTag==null) return null;
            if (startTag.getName()==HTMLElementName.META)
                return startTag.getAttributeValue("content"); // Attribute values are automatically decoded
            pos=startTag.getEnd();
        }
        return null;
    }

    private String getIsoDateString(JSONObject tweet, String fieldName) {
        final String created_at = tweet.getString(fieldName);
        final DateTime time = twitterDateFormat.parseDateTime(created_at);
        final String isDateString = isoDate.print(time.getMillis());
        return isDateString;
    }
    private List<String> getUrls(JSONObject tweet) throws IOException {
        List<String> urls = new ArrayList<String>();
        final JSONObject entities = tweet.getJSONObject("entities");
        if (entities==null) return urls;
        final JSONArray urlArray = entities.getJSONArray("urls");
        if (urlArray==null) return urls;
        for (int i=0; i<urlArray.size(); i++) {
            JSONObject url = urlArray.getJSONObject(i);
            if (url==null) continue;
            String expandedUrl = url.getString("expanded_url");
            if (filteredOut(expandedUrl))
                continue;
            urls.add(expandedUrl);
        }
        return urls;
    }

    private Source parseHtmlDoc(StringReader reader) throws IOException {
        MicrosoftConditionalCommentTagTypes.register();
        PHPTagTypes.register();
        PHPTagTypes.PHP_SHORT.deregister(); // remove PHP short tags for this example otherwise they override processing instructions
        MasonTagTypes.register();
        Source source=new Source(reader);

        // Call fullSequentialParse manually as most of the source will be parsed.
        source.fullSequentialParse();

        return source;
    }

    private String longUrl(String expandedUrl) throws IOException {
        final String url = URLEncoder.encode(expandedUrl, "UTF-8");
        String longUrlResponse = fetch("http://api.longurl.org/v2/expand?format=json&url=" + url);
        JSONObject json = JSONObject.fromObject(longUrlResponse);
        if (json.has("long-url")) {
            final String longUrl = json.getString("long-url");
            addShortUrl(expandedUrl, longUrl);
            return longUrl;
        }
        return expandedUrl;
    }

    private boolean filteredOut(String expandedUrl) {
        if (expandedUrl.indexOf("4sq")!=-1)
            return true;
        return false;
    }

    private void populateChannelElement(Document doc, Element channelElement) {
        channelElement.appendChild(createTextElement(doc, "title", "Home Timeline"));
        channelElement.appendChild(createTextElement(doc, "description", "Home Timeline"));
        channelElement.appendChild(createTextElement(doc, "PubDate", fullDateFormat.print(System.currentTimeMillis())));
        channelElement.appendChild(createDCElement(doc, "creator", "ckemmler"));
    }

    private Element createTextElement(Document doc, String elementName, String text) {
        final Element element = doc.createElement(elementName);
        element.setTextContent(text);
        return element;
    }

    public String getStringFromDoc(org.w3c.dom.Document doc) throws TransformerException {
        StringWriter output = new StringWriter();

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new DOMSource(doc), new StreamResult(output));

        String xml = output.toString();
        return xml;
    }

    private Element createDCElement(Document document, String elementName, String text) {
        final Element elementNS = document.createElementNS("http://purl.org/dc/elements/1.1/", "dc:" + elementName);
        elementNS.setTextContent(text);
        return elementNS;
    }


    public synchronized void addShortUrl(String shortUrl, String longUrl) {
        final Session session = sessionFactory.openSession();
        ShortUrl sUrl = new ShortUrl();
        sUrl.longUrl = longUrl;
        sUrl.shortUrl = shortUrl;
        final Transaction transaction = session.beginTransaction();
        session.persist(sUrl);
        session.flush();
        transaction.commit();
        session.close();
    }

    public synchronized ShortUrl getShortUrl(String shortUrl) {
        final Session session = sessionFactory.openSession();
        final Query query = session.createQuery("SELECT url FROM ShortUrl url WHERE url.shortUrl=:url");
        query.setParameter("url", shortUrl);
        final List list = query.list();
        if (list.size()>0) {
            final ShortUrl sUrl = (ShortUrl) list.get(0);
            session.close();
            return sUrl;
        }
        session.close();
        return null;
    }

    public synchronized void addHtmlDocument(String url, String title, String description, String article_hash) {
        HtmlDocument htmlDoc = new HtmlDocument();
        htmlDoc.description = description;
        htmlDoc.title = title;
        htmlDoc.URL = url;
        htmlDoc.URL_hash = hash(url);
        htmlDoc.article_hash = article_hash;
        final Session session = sessionFactory.openSession();
        final Transaction transaction = session.beginTransaction();
        session.persist(htmlDoc);
        session.flush();
        transaction.commit();
        session.close();
    }

    public synchronized void saveLastTweetId(String twitterName, String lastTweetId) {
        final Session session = sessionFactory.openSession();
        Person p = getPerson(twitterName, session);
        final Transaction transaction = session.beginTransaction();
        if (p==null) {
            p = new Person();
            p.twitterName = twitterName;
            p.lastTweetId = lastTweetId;
            p.lastUpdated = new java.sql.Date(System.currentTimeMillis());
            session.persist(p);
        } else {
            p.lastTweetId = lastTweetId;
            p.lastUpdated = new java.sql.Date(System.currentTimeMillis());
            session.persist(p);
        }
        session.flush();
        transaction.commit();
        session.close();
    }

    private Date getTimestamp(JSONObject tweet) {
        final String created_at = tweet.getString("created_at");
        final DateTime time = twitterDateFormat.parseDateTime(created_at);
        final Date timestamp = time.toDate();
        return timestamp;
    }

    public synchronized HtmlDocument getHtmlDocument(String url) {
        url = hash(url);
        final Session session = sessionFactory.openSession();
        final Query query = session.createQuery("SELECT doc FROM HtmlDocument doc WHERE doc.URL_hash=:hash");
        query.setParameter("hash", url);
        final List list = query.list();
        if (list.size()>0) {
            final HtmlDocument htmlDocument = (HtmlDocument) list.get(0);
            session.close();
            return htmlDocument;
        }
        session.close();
        return null;
    }

    private synchronized void addTweetedBy(JSONObject tweet, String url, String articleHash) {
        // first check if we already have this information
        final Session session = sessionFactory.openSession();
        final Date created = getTimestamp(tweet);
        final JSONObject user = tweet.getJSONObject("user");
        final String twitterName = user.getString("screen_name");
        if (isDupeTweet(articleHash, twitterName, created, session)) {
            session.close();
            return;
        }

        final Transaction transaction = session.beginTransaction();
        TweetedBy tweetedBy = new TweetedBy();
        tweetedBy.tweet = tweet.getString("text");
        if (tweet.has("profile_image_url"))
            tweetedBy.twitterProfilePhotoURL = tweet.getString("profile_image_url");
        tweetedBy.created = created;
        tweetedBy.twitterName = twitterName;
        tweetedBy.twitterId = user.getLong("id");
        tweetedBy.tweetId = tweet.getString("id_str");
        tweetedBy.URL_hash = hash(url);
        tweetedBy.article_hash = articleHash;

        session.persist(tweetedBy);
        session.flush();
        transaction.commit();
        session.close();
    }

    private List<TweetedBy> getTweetedByWithURL(String url) {
        url = hash(url);
        final Session session = sessionFactory.openSession();
        final Query query = session.createQuery("SELECT tweet FROM TweetedBy tweet WHERE tweet.URL_hash=:hash");
        query.setParameter("hash", url);
        final List list = query.list();
        session.close();
        return list;
    }

    private boolean isDupeTweet(String articleHash, String twitterName, Date created, Session session) {
        final Query query = session.createQuery("SELECT tweet FROM TweetedBy tweet WHERE " +
                "tweet.article_hash=:hash AND tweet.twitterName=:twitterName AND tweet.created=:created");
        query.setParameter("hash", articleHash);
        query.setParameter("twitterName", twitterName);
        query.setParameter("created", created);
        final List list = query.list();
        return list.size()>0;
    }

    private List<TweetedBy> getTweetedByWithArticleHash(String articleHash) {
        final Session session = sessionFactory.openSession();
        final Query query = session.createQuery("SELECT tweet FROM TweetedBy tweet WHERE tweet.article_hash=:hash");
        query.setParameter("hash", articleHash);
        final List list = query.list();
        session.close();
        return list;
    }

    private synchronized Person getPerson(final String twitterName, final Session session) {
        final Query query = session.createQuery("SELECT person FROM Person person WHERE person.twitterName=:twitterName");
        query.setParameter("twitterName", twitterName);
        final List list = query.list();
        if (list.size()>0) {
            Person p = (Person) list.get(0);
            return p;
        }
        return null;
    }

}
