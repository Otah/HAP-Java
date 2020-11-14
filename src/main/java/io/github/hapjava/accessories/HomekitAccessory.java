package io.github.hapjava.accessories;

import io.github.hapjava.services.Service;
import java.util.Collection;

/**
 * Base interface for all HomeKit Accessories. You can implement this interface directly, but most
 * users will prefer to use the more full featured interfaces in {@link
 * io.github.hapjava.accessories} which include a default implementation of {@link #getServices()}.
 *
 * @author Andy Lintner
 */
public interface HomekitAccessory {

  /**
   * A unique identifier that must remain static across invocations to prevent errors with paired
   * iOS devices. When used as a child of a Bridge, this value must be greater than 1, as that ID is
   * reserved for the Bridge itself.
   *
   * @return the unique identifier.
   */
  int getId();

  /**
   * Returns a name to display in iOS.
   *
   * @return the label.
   */
  String getName();

  /**
   * The collection of Services this accessory supports. Services are the primary way to interact
   * with the accessory via HomeKit. Besides the Services offered here, the accessory will
   * automatically include the required information service.
   *
   * <p>This method will only be useful if you're implementing your own accessory type. For the
   * standard accessories, use the default implementation provided by the interfaces in {@link
   * io.github.hapjava.accessories}.
   *
   * @return the collection of services.
   */
  Collection<Service> getServices();
}
