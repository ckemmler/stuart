# Stuart

A [dropwizard](http://dropwizard.codahale.com/) utility webapp to rss-ify your twitter roster. It uses hibernate for persistence, [boilerpipe](https://code.google.com/p/boilerpipe/) to parse link targets and extract their title and contents, and [signpost](https://code.google.com/p/oauth-signpost/) (oauth) to access your twitter rooster.

It will let you create an rss of all the article (links) that are posted by your twitter friends (/stuart/home_timeline). It will also let you get an rss of all the articles posted by a specific twitter user (/stuart/user_timeline).

You will have to provide stuart with a valid oauth access token for your twitter account. 

It also includes a utility endpoint that will check if a specific url has already been tweeted. To do this, it will fetch the url's content, parse the article (with boilerpipe), hash its contents and query the database to check if the same article hash is already in store.

Lastly, the /stuart/scoopit endpoint changes the rss provided by scoopit to replace the link by the source (or vice-versa, I can't remember) so that clicking on the rss's link in an aggregator will get you to the actual article, not the scoop it website.


    GET     /stuart/scoopit (stuart.ScoopItUtilsController)
    GET     /stuart/twitter/already_tweeted (stuart.TwitterUtilsController)
    GET     /stuart/twitter/home_timeline (stuart.TwitterUtilsController)
    GET     /stuart/twitter/user_timeline (stuart.TwitterUtilsController)
    
The project is built with maven, so just do `maven install` to create a runnable jar. There is an example run bash script and yml configuration file included in the project.

Also, you need to setup a mysql database and run the DDL inside `db/init.sql` before running the app. 