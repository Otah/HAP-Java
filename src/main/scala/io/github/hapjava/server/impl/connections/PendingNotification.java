package io.github.hapjava.server.impl.connections;

import spray.json.JsValue;

public class PendingNotification {
  public final int aid;
  public final int iid;
  public final JsValue changed;

  public PendingNotification(int aid, int iid, JsValue changed) {
    this.aid = aid;
    this.iid = iid;
    this.changed = changed;
  }
}
