package io.github.hapjava.server.impl.connections;

import io.github.hapjava.accessories.HomekitAccessory;
import io.github.hapjava.server.HomekitAuthInfo;
import io.github.hapjava.server.impl.HomekitRegistry;
import io.github.hapjava.server.impl.http.HomekitClientConnection;
import io.github.hapjava.server.impl.http.HttpRequest;
import io.github.hapjava.server.impl.http.HttpResponse;
import io.github.hapjava.server.impl.jmdns.JmdnsHomekitAdvertiser;
import io.github.hapjava.server.impl.json.AccessoryDatabase;
import io.github.hapjava.server.impl.pairing.PairVerificationManager;
import io.github.hapjava.server.impl.pairing.PairingManager;
import io.github.hapjava.server.impl.pairing.PairingUpdateController;
import io.github.hapjava.server.impl.responses.InternalServerErrorResponse;
import io.github.hapjava.server.impl.responses.NotFoundResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Await;
import scala.concurrent.ExecutionContext;
import scala.concurrent.duration.FiniteDuration;
import spray.json.JsValue;

class HttpSession {

  private volatile PairingManager pairingManager;
  private volatile PairVerificationManager pairVerificationManager;

  private final HomekitAuthInfo authInfo;
  private final HomekitRegistry registry;
  private final AccessoryDatabase database;
  private final HomekitClientConnection connection;
  private final JmdnsHomekitAdvertiser advertiser;

  private static final Logger logger = LoggerFactory.getLogger(HttpSession.class);

  public HttpSession(
      HomekitAuthInfo authInfo,
      HomekitRegistry registry,
      SubscriptionManager subscriptions,
      HomekitClientConnection connection,
      JmdnsHomekitAdvertiser advertiser) {
    this.authInfo = authInfo;
    this.registry = registry;
    this.connection = connection;
    this.advertiser = advertiser;
    this.database =
        new AccessoryDatabase(
            registry.getRoot(), subscriptions, ExecutionContext.global(), JsValue::prettyPrint);
  }

  public HttpResponse handleRequest(HttpRequest request) throws IOException {
    switch (request.getUri()) {
      case "/pair-setup":
        return handlePairSetup(request);

      case "/pair-verify":
        return handlePairVerify(request);

      default:
        if (registry.isAllowUnauthenticatedRequests()) {
          return handleAuthenticatedRequest(request);
        } else {
          logger.warn("Unrecognized request for " + request.getUri());
          return new NotFoundResponse();
        }
    }
  }

  private <T> T waitFor(scala.concurrent.Future<T> future) throws IOException {
    try {
      return Await.result(future, FiniteDuration.apply(30, TimeUnit.SECONDS));
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  public HttpResponse handleAuthenticatedRequest(HttpRequest request) throws IOException {
    advertiser.setDiscoverable(
        false); // brigde is already bound and should not be discoverable anymore
    try {
      switch (request.getUri()) {
        case "/accessories":
          return waitFor(database.listAllAccessories());

        case "/characteristics":
          switch (request.getMethod()) {
            case PUT:
              return database.putCharacteristics(request.getBody(), connection);

            default:
              logger.warn("Unrecognized method for " + request.getUri());
              return new NotFoundResponse();
          }

        case "/pairings":
          return new PairingUpdateController(authInfo, advertiser).handle(request);

        default:
          if (request.getUri().startsWith("/characteristics?")) {
            return waitFor(
                database.getCharacteristicsValues(
                    request.getUri().substring("/characteristics?id=".length())));
          }
          logger.warn("Unrecognized request for " + request.getUri());
          return new NotFoundResponse();
      }
    } catch (Exception e) {
      logger.warn("Could not handle request", e);
      return new InternalServerErrorResponse(e);
    }
  }

  private HttpResponse handlePairSetup(HttpRequest request) {
    if (pairingManager == null) {
      synchronized (HttpSession.class) {
        if (pairingManager == null) {
          pairingManager = new PairingManager(authInfo, registry.getLabel());
        }
      }
    }
    try {
      return pairingManager.handle(request);
    } catch (Exception e) {
      logger.warn("Exception encountered during pairing", e);
      return new InternalServerErrorResponse(e);
    }
  }

  private HttpResponse handlePairVerify(HttpRequest request) {
    if (pairVerificationManager == null) {
      synchronized (HttpSession.class) {
        if (pairVerificationManager == null) {
          pairVerificationManager = new PairVerificationManager(authInfo, registry.getLabel());
        }
      }
    }
    try {
      return pairVerificationManager.handle(request);
    } catch (Exception e) {
      logger.warn("Exception encountered while verifying pairing", e);
      return new InternalServerErrorResponse(e);
    }
  }

  public static class SessionKey {
    private final InetAddress address;
    private final HomekitAccessory accessory;

    public SessionKey(InetAddress address, HomekitAccessory accessory) {
      this.address = address;
      this.accessory = accessory;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof SessionKey) {
        return address.equals(((SessionKey) obj).address)
            && accessory.equals(((SessionKey) obj).accessory);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      int hash = 1;
      hash = hash * 31 + address.hashCode();
      hash = hash * 31 + accessory.hashCode();
      return hash;
    }
  }
}
