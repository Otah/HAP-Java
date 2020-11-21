package io.github.hapjava.server.impl;

import com.github.otah.hap.api.server.HomeKitRoot;

public class HomekitRegistry {

  private final HomeKitRoot root;
  private boolean isAllowUnauthenticatedRequests = false;

  public HomekitRegistry(HomeKitRoot root) {
    this.root = root;
  }

  public HomeKitRoot getRoot() {
    return root;
  }

  public String getLabel() {
    return root.label();
  }

  public boolean isAllowUnauthenticatedRequests() {
    return isAllowUnauthenticatedRequests;
  }

  public void setAllowUnauthenticatedRequests(boolean allow) {
    this.isAllowUnauthenticatedRequests = allow;
  }
}
