
CREATE TABLE `ShortUrl` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `shortUrl` varchar(255) NOT NULL,
  `longUrl` text NOT NULL,
  PRIMARY KEY (`id`),
  KEY `shortUrl` (`shortUrl`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `HtmlDocument` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `URL` longtext NOT NULL,
  `URL_hash` varchar(255) NOT NULL,
  `title` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `description` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `article_hash` varchar(255) NULL,
  PRIMARY KEY (`id`),
  KEY `URL` (`URL_hash`),
  KEY `article_hash` (`article_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `TweetedBy` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `URL_hash` varchar(255) NOT NULL,
  `article_hash` varchar(255) NULL,
  `twitterName` varchar(255) NOT NULL,
  `twitterProfilePhotoURL` varchar(255) NULL,
  `tweetId` varchar(255) NOT NULL,
  `twitterId` varchar(255) NOT NULL,
  `tweet` varchar(255) CHARACTER SET utf8mb4 COLLATE 'utf8mb4_unicode_ci' NOT NULL,
  `created` timestamp NOT NULL,
  PRIMARY KEY (`id`),
  KEY `URL` (`URL_hash`),
  KEY `twitterId` (`twitterId`),
  KEY `tweetId` (`tweetId`),
  KEY `article_hash` (`article_hash`),
  KEY `twitterName` (`twitterName`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `Person` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `twitterName` varchar(255) NOT NULL,
  `lastTweetId` varchar(255) NOT NULL,
  `lastUpdated` timestamp NOT NULL,
  PRIMARY KEY (`id`),
  KEY `URL` (`lastTweetId`),
  KEY `lastUpdated` (`lastUpdated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
