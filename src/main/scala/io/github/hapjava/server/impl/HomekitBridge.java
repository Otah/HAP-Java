package io.github.hapjava.server.impl;

import io.github.hapjava.accessories.Bridge;
import io.github.hapjava.services.Service;
import java.util.Collection;
import java.util.Collections;

public class HomekitBridge implements Bridge {

  private final String label;
  private final Service info;

  public HomekitBridge(String label, Service info) {
    this.label = label;
    this.info = info;
  }

  @Override
  public String getName() {
    return label;
  }

  @Override
  public Collection<Service> getServices() {
    return Collections.singleton(info);
  }

  @Override
  public int getId() {
    return 1;
  }
}
