package org.dataportabilityproject.serviceProviders.smugmug;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import oauth.signpost.OAuth;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;
import org.dataportabilityproject.shared.IOInterface;
import org.dataportabilityproject.shared.auth.AuthData;
import org.dataportabilityproject.shared.auth.OfflineAuthDataGenerator;
import org.dataportabilityproject.shared.auth.TokenSecretAuthData;
import org.dataportabilityproject.shared.signpost.GoogleOAuthConsumer;

final class SmugMugAuth implements OfflineAuthDataGenerator {
  private final String clientId;
  private final String clientSecret;

  SmugMugAuth(String clientId, String clientSecret) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  @Override
  public AuthData generateAuthData(IOInterface ioInterface) throws IOException {
    // As per details: https://api.smugmug.com/api/v2/doc/tutorial/authorization.html
    // and example: http://stackoverflow.com/questions/15194182/examples-for-oauth1-using-google-api-java-oauth
    // Google library puts signature in header and not in request, see https://oauth.net/1/
    OAuthConsumer consumer = new GoogleOAuthConsumer(clientId, clientSecret);

    OAuthProvider provider = new DefaultOAuthProvider(
        "https://secure.smugmug.com/services/oauth/1.0a/getRequestToken",
        "https://secure.smugmug.com/services/oauth/1.0a/getAccessToken",
        "https://secure.smugmug.com/services/oauth/1.0a/authorize?Access=Full&Permissions=Add");

    String authUrl;
    try {
      authUrl = provider.retrieveRequestToken(consumer, OAuth.OUT_OF_BAND);
    } catch (OAuthMessageSignerException
        | OAuthNotAuthorizedException
        | OAuthExpectationFailedException
        | OAuthCommunicationException e) {
      throw new IOException("Couldn't generate authUrl", e);
    }

    String code = ioInterface.ask("Please visit: " + authUrl + " and enter code:");

    try {
      provider.retrieveAccessToken(consumer, code.trim());
    } catch (OAuthMessageSignerException
        | OAuthNotAuthorizedException
        | OAuthExpectationFailedException
        | OAuthCommunicationException e) {
      throw new IOException("Couldn't authorize", e);
    }

    return TokenSecretAuthData.create(consumer.getToken(), consumer.getTokenSecret());
  }

  OAuthConsumer generateConsumer(AuthData authData) {
    checkArgument(authData instanceof TokenSecretAuthData,
        "authData expected to be TokenSecretAuthData not %s",
        authData.getClass().getCanonicalName());
    TokenSecretAuthData tokenSecretAuthData = (TokenSecretAuthData) authData;
    OAuthConsumer consumer = new GoogleOAuthConsumer(clientId, clientSecret);
    consumer.setTokenWithSecret(tokenSecretAuthData.token(), tokenSecretAuthData.secret());
    return consumer;
  }
}