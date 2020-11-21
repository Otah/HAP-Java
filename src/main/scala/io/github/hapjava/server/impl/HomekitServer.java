package io.github.hapjava.server.impl;

import com.github.otah.hap.api.server.HomeKitServer;
import io.github.hapjava.server.HomekitAuthInfo;
import io.github.hapjava.server.impl.http.impl.HomekitHttpServer;
import io.github.hapjava.services.Service;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.InvalidAlgorithmParameterException;

/**
 * The main entry point for hap-java. Creating an instance of this class will listen for HomeKit
 * connections on the supplied port. Only a single root accessory can be added for each unique
 * instance and port, however, that accessory may be a {@link #createBridge(HomekitAuthInfo, String,
 * Service) bridge accessory} containing child accessories.
 *
 * <p>The {@link HomekitAuthInfo HomekitAuthInfo} argument when creating accessories should be an
 * implementation supplied by your application. Several of the values needed for your implementation
 * are provided by this class, specifically {@link #generateKey() generateKey}, {@link
 * #generateMac() generateMac}, and {@link #generateSalt()}. It is important that you provide these
 * same values on each start of your application or HomeKit will fail to recognize your device as
 * being the same.
 *
 * @author Andy Lintner
 */
public class HomekitServer {

  private final HomekitHttpServer http;
  private final HomekitRoot root;

  public HomekitServer(HomeKitServer serverDef, int nThreads) throws IOException {
    String hostString = serverDef.host().getOrElse(() -> null);
    InetAddress host =
        hostString == null ? InetAddress.getLocalHost() : InetAddress.getByName(hostString);
    http = new HomekitHttpServer(host, serverDef.port(), nThreads);
    root =
        new HomekitRoot(
            serverDef.root().label(),
            http,
            host,
            new AuthConverter(serverDef.root().auth()),
            serverDef.root());
  }

  public HomekitServer(HomeKitServer serverDef) throws IOException {
    this(serverDef, Runtime.getRuntime().availableProcessors());
  }

  public void start() {
    root.start();
  }

  /** Stops the service, closing down existing connections and preventing new ones. */
  public void stop() {
    root.stop();
    http.stop();
  }

  /**
   * Generates a value to supply in {@link HomekitAuthInfo#getSalt() HomekitAuthInfo.getSalt()}.
   * This is used to salt the pin-code. You don't need to worry about that though - the salting is
   * done on the plaintext pin. (Yes, plaintext passwords are bad. Please don't secure your nuclear
   * storage facility with this implementation)
   *
   * @return the generated salt
   */
  public static BigInteger generateSalt() {
    return HomekitUtils.generateSalt();
  }

  /**
   * Generates a value to supply in {@link HomekitAuthInfo#getPrivateKey()
   * HomekitAuthInfo.getPrivKey()}. This is used as the private key during pairing and connection
   * setup.
   *
   * @return the generated key
   * @throws InvalidAlgorithmParameterException if the JVM does not contain the necessary encryption
   *     algorithms.
   */
  public static byte[] generateKey() throws InvalidAlgorithmParameterException {
    return HomekitUtils.generateKey();
  }

  /**
   * Generates a value to supply in {@link HomekitAuthInfo#getMac() HomekitAuthInfo.getMac()}. This
   * is used as the unique identifier of the accessory during mDNS advertising. It is a valid MAC
   * address generated in the locally administered range so as not to conflict with any commercial
   * devices.
   *
   * @return the generated MAC
   */
  public static String generateMac() {
    return HomekitUtils.generateMac();
  }

  /**
   * Generates a value to supply in {@link HomekitAuthInfo#getPin() HomekitAuthInfo.getPin()}. This
   * is used as the Pin a user enters into their HomeKit device in order to confirm pairing.
   *
   * @return the generated Pin
   */
  public static String generatePin() {
    return HomekitUtils.generatePin();
  }
}
