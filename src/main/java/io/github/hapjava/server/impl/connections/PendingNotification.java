package io.github.hapjava.server.impl.connections;

import javax.json.JsonValue;

public class PendingNotification {
  public final int aid;
  public final int iid;
  public final JsonValue changed;

  public PendingNotification(int aid, int iid, JsonValue changed) {
    this.aid = aid;
    this.iid = iid;
    this.changed = changed;
  }
}
