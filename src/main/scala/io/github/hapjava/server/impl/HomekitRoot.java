package io.github.hapjava.server.impl;

import io.github.hapjava.accessories.HomekitAccessory;
import io.github.hapjava.server.HomekitAuthInfo;
import io.github.hapjava.server.HomekitWebHandler;
import io.github.hapjava.server.impl.connections.HomekitClientConnectionFactoryImpl;
import io.github.hapjava.server.impl.connections.SubscriptionManager;
import io.github.hapjava.server.impl.jmdns.JmdnsHomekitAdvertiser;
import io.github.hapjava.services.Service;
import java.io.IOException;
import java.net.InetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides advertising and handling for HomeKit accessories. This class handles the advertising of
 * HomeKit accessories and contains one or more accessories. When implementing a bridge accessory,
 * you will interact with this class directly. Instantiate it via {@link
 * HomekitServer#createBridge(HomekitAuthInfo, String, Service)}. For single accessories, this is
 * composed by {@link HomekitStandaloneAccessoryServer}.
 *
 * @author Andy Lintner
 */
public class HomekitRoot {

  private static final Logger logger = LoggerFactory.getLogger(HomekitRoot.class);

  private final JmdnsHomekitAdvertiser advertiser;
  private final HomekitWebHandler webHandler;
  private final HomekitAuthInfo authInfo;
  private final String label;
  private final HomekitRegistry registry;
  private final SubscriptionManager subscriptions = new SubscriptionManager();
  private boolean started = false;
  private int configurationIndex = 1;

  HomekitRoot(
      String label, HomekitWebHandler webHandler, InetAddress localhost, HomekitAuthInfo authInfo)
      throws IOException {
    this(label, webHandler, authInfo, new JmdnsHomekitAdvertiser(localhost));
  }

  HomekitRoot(
      String label,
      HomekitWebHandler webHandler,
      HomekitAuthInfo authInfo,
      JmdnsHomekitAdvertiser advertiser)
      throws IOException {
    this.advertiser = advertiser;
    this.webHandler = webHandler;
    this.authInfo = authInfo;
    this.label = label;
    this.registry = new HomekitRegistry();
  }

  /**
   * Starts advertising and handling the previously added HomeKit accessories. You should try to
   * call this after you have used the {@link #addAccessory(HomekitAccessory)} method to add all the
   * initial accessories you plan on advertising, as any later additions will cause the HomeKit
   * clients to reconnect.
   */
  public void start() {
    started = true;
    webHandler
        .start(
            new HomekitClientConnectionFactoryImpl(authInfo, registry, subscriptions, advertiser))
        .thenAccept(
            port -> {
              try {
                refreshAuthInfo();
                advertiser.advertise(label, authInfo.getMac(), port, configurationIndex);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  /** Stops advertising and handling the HomeKit accessories. */
  public void stop() {
    advertiser.stop();
    webHandler.stop();
    subscriptions.removeAll();
    started = false;
  }

  /**
   * Refreshes auth info after it has been changed outside this library
   *
   * @throws IOException if there is an error in the underlying protocol, such as a TCP error
   */
  public void refreshAuthInfo() throws IOException {
    advertiser.setDiscoverable(!authInfo.hasUser());
  }

  /**
   * By default, most homekit requests require that the client be paired. Allowing unauthenticated
   * requests can be useful for debugging, but should not be used in production.
   *
   * @param allow whether to allow unauthenticated requests
   */
  public void allowUnauthenticatedRequests(boolean allow) {
    registry.setAllowUnauthenticatedRequests(allow);
  }

  /**
   * By default, the bridge advertises itself at revision 1. If you make changes to the accessories
   * you're including in the bridge after your first call to {@link start()}, you should increment
   * this number. The behavior of the client if the configuration index were to decrement is
   * undefined, so this implementation will not manage the configuration index by automatically
   * incrementing - preserving this state across invocations should be handled externally.
   *
   * @param revision an integer, greater than or equal to one, indicating the revision of the
   *     accessory information
   * @throws IOException if there is an error in the underlying protocol, such as a TCP error
   */
  public void setConfigurationIndex(int revision) throws IOException {
    if (revision < 1) {
      throw new IllegalArgumentException("revision must be greater than or equal to 1");
    }
    this.configurationIndex = revision;
    if (this.started) {
      advertiser.setConfigurationIndex(revision);
    }
  }

  HomekitRegistry getRegistry() {
    return registry;
  }
}
