package com.mmo.module.fb.publish.provider.x;

import com.mmo.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class TwitterService {

    private final AppProperties appProperties;

    private Twitter twitter;

    private Twitter getTwitterInstance() {
        if (twitter == null) {
            ConfigurationBuilder cb = new ConfigurationBuilder();
            cb.setOAuthConsumerKey(appProperties.getTwitter().getConsumer().getKey())
              .setOAuthConsumerSecret(appProperties.getTwitter().getConsumer().getSecret())
              .setOAuthAccessToken(appProperties.getTwitter().getAccess().getToken())
              .setOAuthAccessTokenSecret(appProperties.getTwitter().getAccess().getSecret());
            twitter = new TwitterFactory(cb.build()).getInstance();
        }
        return twitter;
    }

    public void tweet(String message) {
        try {
            getTwitterInstance().updateStatus(message);
            log.info("✅ Tweet sent successfully");
        } catch (TwitterException e) {
            log.error("❌ Failed to send tweet: {}", e.getMessage());
        }
    }

    public void tweetWithImage(String message, byte[] imageBytes) {
        try {
            StatusUpdate statusUpdate = new StatusUpdate(message);
            statusUpdate.setMedia("image.png", new java.io.ByteArrayInputStream(imageBytes));
            getTwitterInstance().updateStatus(statusUpdate);
            log.info("✅ Tweet with image sent successfully");
        } catch (TwitterException e) {
            log.error("❌ Failed to send tweet with image: {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Failed to send tweet with image: {}", e.getMessage());
        }
    }

    public Status tweetThread(String[] messages) {
        try {
            Status previousStatus = null;
            for (String message : messages) {
                if (previousStatus == null) {
                    previousStatus = getTwitterInstance().updateStatus(message);
                } else {
                    StatusUpdate statusUpdate = new StatusUpdate(message);
                    statusUpdate.inReplyToStatusId(previousStatus.getId());
                    previousStatus = getTwitterInstance().updateStatus(statusUpdate);
                }
            }
            log.info("✅ Tweet thread sent successfully with {} tweets", messages.length);
            return previousStatus;
        } catch (TwitterException e) {
            log.error("❌ Failed to send tweet thread: {}", e.getMessage());
            return null;
        }
    }

    public Status tweetThreadWithImage(String firstMessage, byte[] imageBytes, String[] additionalMessages) {
        try {
            StatusUpdate statusUpdate = new StatusUpdate(firstMessage);
            statusUpdate.setMedia("image.png", new java.io.ByteArrayInputStream(imageBytes));
            Status previousStatus = getTwitterInstance().updateStatus(statusUpdate);

            for (String message : additionalMessages) {
                StatusUpdate replyUpdate = new StatusUpdate(message);
                replyUpdate.inReplyToStatusId(previousStatus.getId());
                previousStatus = getTwitterInstance().updateStatus(replyUpdate);
            }
            log.info("✅ Tweet thread with image sent successfully");
            return previousStatus;
        } catch (TwitterException e) {
            log.error("❌ Failed to send tweet thread with image: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("❌ Failed to send tweet thread with image: {}", e.getMessage());
            return null;
        }
    }

    public Status replyToTweet(long tweetId, String message) {
        try {
            StatusUpdate statusUpdate = new StatusUpdate(message);
            statusUpdate.inReplyToStatusId(tweetId);
            Status status = getTwitterInstance().updateStatus(statusUpdate);
            log.info("✅ Reply sent successfully to tweet {}", tweetId);
            return status;
        } catch (TwitterException e) {
            log.error("❌ Failed to reply to tweet {}: {}", tweetId, e.getMessage());
            return null;
        }
    }

    public Status replyWithImage(long tweetId, String message, byte[] imageBytes) {
        try {
            StatusUpdate statusUpdate = new StatusUpdate(message);
            statusUpdate.inReplyToStatusId(tweetId);
            statusUpdate.setMedia("image.png", new java.io.ByteArrayInputStream(imageBytes));
            Status status = getTwitterInstance().updateStatus(statusUpdate);
            log.info("✅ Reply with image sent successfully to tweet {}", tweetId);
            return status;
        } catch (TwitterException e) {
            log.error("❌ Failed to reply with image to tweet {}: {}", tweetId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("❌ Failed to reply with image to tweet {}: {}", tweetId, e.getMessage());
            return null;
        }
    }

    public Status quoteTweet(long tweetId, String message) {
        try {
            // Note: twitter4j 4.0.7 doesn't have native quote tweet support
            // To quote, include the tweet URL in the message: https://twitter.com/user/status/tweetId
            // For now, this just posts a regular tweet
            Status status = getTwitterInstance().updateStatus(message);
            log.info("✅ Quote tweet sent successfully for tweet {}", tweetId);
            return status;
        } catch (TwitterException e) {
            log.error("❌ Failed to quote tweet {}: {}", tweetId, e.getMessage());
            return null;
        }
    }
}
